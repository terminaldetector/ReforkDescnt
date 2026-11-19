package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.physics.PhysicsTarget;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import com.terminaldetector.drmd.physics.ragdoll.RagdollSimulation;
import com.terminaldetector.drmd.physics.ragdoll.RagdollPortalTransfer;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.entity.model.FlightWreckRig;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-authoritative articulated wreck. The entity is only an adapter: segment dynamics stay in
 * {@link RagdollSimulation}, while this class supplies Minecraft terrain, gravity, persistence and
 * replication.
 */
public final class RagdollEntity extends Entity implements PhysicsTarget {
	private static final TrackedData<NbtCompound> POSE = DataTracker.registerData(
			RagdollEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final int MAX_LIFETIME_TICKS = 20 * 60;
	private static final double CONTACT_EPSILON = 1.0e-5;

	public record RenderPart(Vec3 position, Quat rotation) {}
	private record Push(Vec3 normal, double depth) {}

	private RagdollRig rig = RagdollRig.scoutDrone();
    private FlightEnemyKind flightHull;
    private float hullScale=1;
    private int rigVersion=FlightWreckRig.LEGACY_VERSION;
    /** One owned optional metadata bundle; unsupported physics stays opaque and has no authority. */
    private NbtCompound opaqueRigData;
    private boolean hasRenderPose;
	private RagdollSimulation simulation = new RagdollSimulation(
			rig, new Vec3(0, 0, 0), Quat.IDENTITY, new Vec3(0, 0, 0));
	private List<RenderPart> previousRenderParts = restRenderParts();
	private List<RenderPart> currentRenderParts = restRenderParts();
	private int lifeTicks;
	// Replicated discontinuity frame only; persistent rig/identity/lifetime schemas are unchanged.
	private int portalPoseEpoch;
	/** Non-consuming ordinary metadata publication after a prepared portal commit. */
	public List<DataTracker.SerializedEntry<?>> portalTrackedPose(){return List.of(DataTracker.SerializedEntry.of(POSE,dataTracker.get(POSE)));}
	private Quat portalRenderFrame = Quat.IDENTITY;

	public RagdollEntity(EntityType<? extends RagdollEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(POSE, new NbtCompound());
	}

	/** Spawn the current five-part scout wreck at a world-space centre. */
	public static RagdollEntity spawnScout(ServerWorld world, Vec3d origin, Vec3d velocityPerTick) {
        return spawnScout(world,origin,velocityPerTick,Quat.IDENTITY);
    }

    private static RagdollEntity spawnScout(ServerWorld world,Vec3d origin,Vec3d velocityPerTick,Quat rotation) {
        return spawnHull(world,origin,velocityPerTick,rotation,null,1);
    }

    private static RagdollEntity spawnHull(ServerWorld world,Vec3d origin,Vec3d velocityPerTick,Quat rotation,FlightEnemyKind kind,float scale) {
		RagdollEntity ragdoll = ModEntities.RAGDOLL.create(world);
		if (ragdoll == null) return null;
        ragdoll.configureHull(kind,scale,FlightWreckRig.currentVersion(kind));
		Vec3 originD6 = pure(origin);
		Vec3 velocityD6 = pure(velocityPerTick).scaled(20);
		ragdoll.simulation = new RagdollSimulation(ragdoll.rig, originD6, rotation, velocityD6);
		ragdoll.syncEntityFromSimulation();
		ragdoll.dataTracker.set(POSE, ragdoll.trackedPose());
		return world.spawnEntity(ragdoll) ? ragdoll : null;
	}

	/** Replace a destroyed combat drone with physical wreckage carrying its momentum. */
	public static RagdollEntity spawnFromDrone(DroneEntity drone, DamageSource source) {
		if (!(drone.getWorld() instanceof ServerWorld world) || !com.terminaldetector.drmd.physics.PhysicsProfileState.expanded(world)) return null;
		Vec3d origin = drone.getPos().add(0, drone.getHeight() * .5, 0);
		var rotation=com.terminaldetector.drmd.entity.model.FlightHullPose.physicalRotation(drone.getYaw(),drone.getPitch(),drone.getFlightRoll());
		RagdollEntity ragdoll = spawnHull(world, origin, drone.getVelocity(),rotation,drone instanceof FlightEnemyEntity typed?typed.kind():null,drone.getScale());
		if (ragdoll == null) return null;

		Entity cause = source.getSource();
		Vec3d direction = cause == null ? drone.getVelocity() : origin.subtract(cause.getPos());
		if (direction.lengthSquared() < 1.0e-8) direction = new Vec3d(0, 1, 0);
		Vec3d impulse = direction.normalize().multiply(8.0);
		// Strike a spar rather than the centre so the death begins as a tumble, not a falling icon.
		ragdoll.applyPhysicsImpulse(origin.add(.35, .12, 0), impulse);
		return ragdoll;
	}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (getWorld().isClient) {
			updateClientBounds();
			return;
		}
        if(!rigVersionSupported())return;
		if (++lifeTicks > MAX_LIFETIME_TICKS) {
			discard();
			return;
		}

		Vec3 gravity = gravityVector();
		simulation.step(.05, gravity, RagdollSimulation.DEFAULT_SUBSTEPS,
				RagdollSimulation.DEFAULT_SOLVER_ITERATIONS, this::resolveTerrain);
		// Mild air/contact loss prevents a permanently vibrating corpse without erasing a weapon throw.
		for (RagdollSimulation.Part part : simulation.parts()) {
			part.body().withLinearVelocity(part.body().linearVelocity().scaled(.998));
			part.body().withAngularMomentum(part.body().angularMomentum().scaled(.994));
		}
		syncEntityFromSimulation();
		dataTracker.set(POSE, trackedPose());
		velocityModified = true;
	}

	private Vec3 gravityVector() {
		GravityFields.Sample field = GravityFields.sample(getWorld(), getPos());
		if (field == null || field.strength() < .05f || field.downDir().lengthSquared() < 1.0e-10)
			return new Vec3(0, -9.81, 0);
		Vec3d down = field.downDir().normalize();
		return new Vec3(down.x, down.y, down.z).scaled(9.81 * field.strength());
	}

	private void resolveTerrain(RagdollSimulation.Part part, Vec3 fallbackPosition) {
		Box bounds = partBox(part);
		if (!insideLoadedWorld(bounds)) {
			part.body().withPosition(fallbackPosition)
					.withLinearVelocity(new Vec3(0, 0, 0))
					.withAngularMomentum(new Vec3(0, 0, 0));
			return;
		}

		for (int pass = 0; pass < 4; pass++) {
			bounds = partBox(part);
			Push best = null;
			for (var shape : getWorld().getBlockCollisions(this, bounds.contract(1.0e-7))) {
				Box obstacle = shape.getBoundingBox();
				if (!bounds.intersects(obstacle)) continue;
				Push candidate = minimumPush(bounds, obstacle);
				if (candidate != null && (best == null || candidate.depth() < best.depth())) best = candidate;
			}
			if (best == null) break;

			Vec3 normal = best.normal();
			part.body().withPosition(part.body().position().plus(normal.scaled(best.depth() + CONTACT_EPSILON)));
			double support = projectionRadius(part, normal);
			Vec3 contactOffset = normal.scaled(-support);
			part.body().contactImpulse(contactOffset, normal, .72);
		}

		// Constraint correction can occasionally wedge a conservative OBB broadphase into a corner.
		// Do not let one bad contact inject NaNs or push the whole rig through the world.
		if (getWorld().getBlockCollisions(this, partBox(part).contract(1.0e-6)).iterator().hasNext()) {
			part.body().withPosition(fallbackPosition)
					.withLinearVelocity(new Vec3(0, 0, 0))
					.withAngularMomentum(new Vec3(0, 0, 0));
		}
	}

	private boolean insideLoadedWorld(Box box) {
		if (box.minY < getWorld().getBottomY() || box.maxY > getWorld().getTopY()) return false;
		var access = WorldAccess.of(getWorld());
		for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(Math.nextDown(box.maxX)) >> 4; x++)
			for (int y = MathHelper.floor(box.minY) >> 4; y <= MathHelper.floor(Math.nextDown(box.maxY)) >> 4; y++)
				for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(Math.nextDown(box.maxZ)) >> 4; z++)
					if (!access.isVolumeAvailable(new D6ChunkPos(x, y, z))) return false;
		return getWorld().getWorldBorder().contains(box);
	}

	private static Push minimumPush(Box body, Box obstacle) {
		double[] depth = {
				obstacle.maxX - body.minX, body.maxX - obstacle.minX,
				obstacle.maxY - body.minY, body.maxY - obstacle.minY,
				obstacle.maxZ - body.minZ, body.maxZ - obstacle.minZ
		};
		Vec3[] normal = {
				new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
				new Vec3(0, 1, 0), new Vec3(0, -1, 0),
				new Vec3(0, 0, 1), new Vec3(0, 0, -1)
		};
		int best = -1;
		for (int i = 0; i < depth.length; i++)
			if (depth[i] >= 0 && (best < 0 || depth[i] < depth[best])) best = i;
		return best < 0 ? null : new Push(normal[best], depth[best]);
	}

	private static double projectionRadius(RagdollSimulation.Part part, Vec3 axis) {
		Vec3 half = part.segment().halfExtents();
		Quat rotation = part.body().rotation();
		Vec3 x = rotation.rotate(new Vec3(half.x(), 0, 0));
		Vec3 y = rotation.rotate(new Vec3(0, half.y(), 0));
		Vec3 z = rotation.rotate(new Vec3(0, 0, half.z()));
		return Math.abs(axis.dot(x)) + Math.abs(axis.dot(y)) + Math.abs(axis.dot(z));
	}

	private static Box partBox(RagdollSimulation.Part part) {
		return partBox(part.segment(), part.body().position(), part.body().rotation());
	}

	private static Box partBox(RagdollRig.Segment segment, Vec3 position, Quat rotation) {
		Vec3 half = segment.halfExtents();
		Vec3 x = rotation.rotate(new Vec3(half.x(), 0, 0));
		Vec3 y = rotation.rotate(new Vec3(0, half.y(), 0));
		Vec3 z = rotation.rotate(new Vec3(0, 0, half.z()));
		double ex = Math.abs(x.x()) + Math.abs(y.x()) + Math.abs(z.x());
		double ey = Math.abs(x.y()) + Math.abs(y.y()) + Math.abs(z.y());
		double ez = Math.abs(x.z()) + Math.abs(y.z()) + Math.abs(z.z());
		return new Box(position.x() - ex, position.y() - ey, position.z() - ez,
				position.x() + ex, position.y() + ey, position.z() + ez);
	}

	private void syncEntityFromSimulation() {
		Vec3 centre = simulation.centreOfMass();
		Vec3 velocity = simulation.centreVelocity();
		setPosition(centre.x(), centre.y(), centre.z());
		setVelocity(velocity.x() / 20.0, velocity.y() / 20.0, velocity.z() / 20.0);
		setBoundingBox(combinedBounds());
	}

	private Box combinedBounds() {
		Box result = null;
		for (RagdollSimulation.Part part : simulation.parts())
			result = union(result, partBox(part));
		return result == null ? new Box(getPos(), getPos()).expand(.5) : result;
	}

	private static Box union(Box a, Box b) {
		if (a == null) return b;
		return new Box(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
				Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
	}

	private void updateClientBounds() {
		Box bounds = null;
		for (int i = 0; i < Math.min(rig.segments().size(), currentRenderParts.size()); i++) {
			RenderPart pose = currentRenderParts.get(i);
			Vec3 world = pose.position().plus(pure(getPos()));
			bounds = union(bounds, partBox(rig.segments().get(i), world, pose.rotation()));
		}
		setBoundingBox(bounds == null ? new Box(getPos(), getPos()).expand(.5) : bounds);
	}

    /** Null identifies a legacy scout, including old saves without hull metadata. */
    public FlightEnemyKind flightHullKind(){return flightHull;}
    public float hullScale(){return hullScale;}
    public int rigVersion(){return rigVersion;}
    public boolean rigVersionSupported(){return opaqueRigData==null;}
    private boolean configureHull(FlightEnemyKind kind,float scale,int version){
        if(flightHull==kind&&Float.compare(hullScale,scale)==0&&rigVersion==version)return false;
        flightHull=kind;hullScale=scale;rigVersion=version;
        // Unsupported metadata may use recognizable geometry for display, never v1 physics.
        rig=FlightWreckRig.create(kind,scale,FlightWreckRig.supportedVersion(version)?version:FlightWreckRig.LEGACY_VERSION);
        previousRenderParts=restRenderParts();currentRenderParts=previousRenderParts;return true;
    }
    private void writeHullIdentity(NbtCompound tag){
        if(opaqueRigData!=null){copyKeys(opaqueRigData,tag,List.of("hullVersion","flightHull","hullScale","rigVersion"));return;}
        tag.putInt("hullVersion",1);tag.putString("flightHull",flightHull==null?"":flightHull.id);tag.putFloat("hullScale",hullScale);tag.putInt("rigVersion",rigVersion);
    }
    private boolean readHullIdentity(NbtCompound tag){
        int version=tag.contains("rigVersion")?tag.getInt("rigVersion"):FlightWreckRig.LEGACY_VERSION;
        boolean supported=!tag.contains("rigVersion")||tag.contains("rigVersion",NbtElement.INT_TYPE)&&FlightWreckRig.supportedVersion(version);
        opaqueRigData=null;
        if(!supported){
            opaqueRigData=new NbtCompound();
            copyKeys(tag,opaqueRigData,List.of("hullVersion","flightHull","hullScale","rigVersion","ragdoll","life"));
            version=0; // explicit refused state, not legacy authority
        }
        FlightEnemyKind kind=null;float scale=1;
        if(tag.getInt("hullVersion")==1){for(var value:FlightEnemyKind.values())if(value.id.equals(tag.getString("flightHull"))){kind=value;break;}scale=tag.contains("hullScale",NbtElement.NUMBER_TYPE)?tag.getFloat("hullScale"):1;if(!FlightWreckRig.validScale(scale))scale=1;}
        return configureHull(kind,scale,version);
    }

    private static void copyKeys(NbtCompound source,NbtCompound target,List<String> keys){
        for(String key:keys)if(source.contains(key))target.put(key,source.get(key).copy());
    }

	private NbtCompound trackedPose() {
		NbtCompound tag = new NbtCompound();
        writeHullIdentity(tag);
		if (portalPoseEpoch != 0) {
			tag.putInt("portalPoseEpoch", portalPoseEpoch);
			NbtCompound frame = new NbtCompound();
			putQuaternion(frame, portalRenderFrame);
			tag.put("portalRenderFrame", frame);
		}
		NbtList parts = new NbtList();
		Vec3 centre = simulation.centreOfMass();
		for (RagdollSimulation.Part part : simulation.parts()) {
			NbtCompound entry = new NbtCompound();
			putVector(entry, "p", part.body().position().minus(centre));
			putQuaternion(entry, part.body().rotation());
			parts.add(entry);
		}
		tag.put("parts", parts);
		return tag;
	}

	private NbtCompound persistentState() {
		NbtCompound tag = new NbtCompound();
		NbtList parts = new NbtList();
		for (RagdollSimulation.Part part : simulation.parts()) {
			NbtCompound entry = new NbtCompound();
			putVector(entry, "p", part.body().position());
			putQuaternion(entry, part.body().rotation());
			putVector(entry, "v", part.body().linearVelocity());
			putVector(entry, "l", part.body().angularMomentum());
			parts.add(entry);
		}
		tag.put("parts", parts);
		return tag;
	}

	private void loadPersistentState(NbtCompound tag) {
		NbtList parts = tag.getList("parts", NbtElement.COMPOUND_TYPE);
		if (parts.size() != simulation.parts().size()) return;
		for (int i = 0; i < parts.size(); i++) {
			NbtCompound entry = parts.getCompound(i);
			RagdollSimulation.Part part = simulation.parts().get(i);
			part.body().withPosition(readVector(entry, "p"))
					.withRotation(readQuaternion(entry))
					.withLinearVelocity(readVector(entry, "v"))
					.withAngularMomentum(readVector(entry, "l"));
		}
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (!getWorld().isClient || !POSE.equals(data) || currentRenderParts == null) return;
        var tag=dataTracker.get(POSE);boolean changed=readHullIdentity(tag);
        List<RenderPart> decoded=decodeTrackedPose(tag);
        if(decoded.size()!=rig.segments().size())return;
        int incomingEpoch=tag.getInt("portalPoseEpoch");
        Quat incomingFrame=tag.contains("portalRenderFrame",NbtElement.COMPOUND_TYPE)
                ?readQuaternion(tag.getCompound("portalRenderFrame")):Quat.IDENTITY;
        List<RenderPart> preceding=currentRenderParts;
        if(hasRenderPose&&!changed&&incomingEpoch!=portalPoseEpoch)
            preceding=rotateRenderParts(preceding,ImmPtlQuaternions.hamiltonProduct(incomingFrame,portalRenderFrame.inverse()).normalized());
        previousRenderParts=hasRenderPose&&!changed&&preceding.size()==decoded.size()?preceding:decoded;
        portalPoseEpoch=incomingEpoch;portalRenderFrame=incomingFrame;
        currentRenderParts=decoded;hasRenderPose=true;
        // Geometry-only client mirror; simulation/impulses remain server authoritative.
        if(simulation.parts().size()!=rig.segments().size()||changed)simulation=new RagdollSimulation(rig,pure(getPos()),Quat.IDENTITY,new Vec3(0,0,0));
        for(int i=0;i<decoded.size();i++)simulation.parts().get(i).body().withPosition(pure(getPos()).plus(decoded.get(i).position())).withRotation(decoded.get(i).rotation());
        updateClientBounds();
	}

	private List<RenderPart> decodeTrackedPose(NbtCompound tag) {
		NbtList parts = tag.getList("parts", NbtElement.COMPOUND_TYPE);
		if (parts.size() != rig.segments().size()) return List.of();
		List<RenderPart> decoded = new ArrayList<>(parts.size());
		for (int i = 0; i < parts.size(); i++) {
			NbtCompound entry = parts.getCompound(i);
			decoded.add(new RenderPart(readVector(entry, "p"), readQuaternion(entry)));
		}
		return List.copyOf(decoded);
	}

	private List<RenderPart> restRenderParts() {
		List<RenderPart> result = new ArrayList<>(rig.segments().size());
		for (RagdollRig.Segment segment : rig.segments())
			result.add(new RenderPart(segment.restPosition(), Quat.IDENTITY));
		return List.copyOf(result);
	}

	public RagdollRig rig() { return rig; }
	public List<RenderPart> previousRenderParts() { return previousRenderParts; }
	public List<RenderPart> currentRenderParts() { return currentRenderParts; }
	public RagdollSimulation simulation() { return simulation; }
	public List<Box> partBounds() { return simulation.parts().stream().map(RagdollEntity::partBox).toList(); }

	/**
	 * Prepare the existing articulated body for a same-world rigid portal transaction. No state or
	 * tracking data changes here. The effective transform already includes shared exit clearance;
	 * destinationRemainder is used only to validate the remaining destination-side sweep.
	 */
	public Optional<PreparedPortalTransfer> preparePortalTransfer(D6PortalTransform transform, Vec3 destinationRemainder) {
		if (getWorld().isClient || isRemoved() || !rigVersionSupported()) return Optional.empty();
		var planned = RagdollPortalTransfer.prepare(simulation, transform, destinationRemainder);
		if (planned.isEmpty()) return Optional.empty();
		var motion = planned.get();
		List<Box> destination = new ArrayList<>(), sweepStart = new ArrayList<>();
		for (int i = 0; i < motion.destinationParts().size(); i++) {
			var end = motion.destinationParts().get(i);
			var start = motion.sweepStartParts().get(i);
			Box endBox = partBox(rig.segments().get(i), end.position(), end.rotation());
			Box startBox = partBox(rig.segments().get(i), start.position(), start.rotation());
			// Static terrain needs the complete compound hull and all of its post-plane remainder,
			// not the entity centre or its untranslated old broadphase.
			if (!portalVolumeClear(union(startBox, endBox))) return Optional.empty();
			destination.add(endBox);
			sweepStart.add(startBox);
		}
		return Optional.of(new PreparedPortalTransfer(motion, destination, sweepStart));
	}

	/** No loads: UNKNOWN volumes, border/height violations and terrain all refuse preparation. */
	private boolean portalVolumeClear(Box box) {
		if (!finite(new Vec3d(box.minX,box.minY,box.minZ)) || !finite(new Vec3d(box.maxX,box.maxY,box.maxZ))
				|| box.minY < getWorld().getBottomY() || box.maxY > getWorld().getTopY()
				|| !getWorld().getWorldBorder().contains(box)) return false;
		int x0=MathHelper.floor(box.minX-1)>>4, x1=MathHelper.floor(box.maxX+1)>>4;
		int y0=MathHelper.floor(box.minY-1)>>4, y1=MathHelper.floor(box.maxY+1)>>4;
		int z0=MathHelper.floor(box.minZ-1)>>4, z1=MathHelper.floor(box.maxZ+1)>>4;
		long dx=(long)x1-x0+1,dy=(long)y1-y0+1,dz=(long)z1-z0+1;
		if(dx<=0||dy<=0||dz<=0||dx>64||dy>64||dz>64||dx*dy*dz>64)return false;
		var access=WorldAccess.of(getWorld());
		for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
			if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
		return !getWorld().getBlockCollisions(this,box.contract(1.0e-7)).iterator().hasNext();
	}

	/** Immutable planned geometry plus a guarded, one-use commit to this existing entity. */
	public final class PreparedPortalTransfer {
		private final RagdollPortalTransfer.Prepared motion;
		private final RagdollSimulation sourceSimulation=simulation;
		private final World sourceWorld=getWorld();
		private final java.util.UUID sourceIdentity=getUuid();
		private final Vec3d sourcePosition=getPos(), sourceVelocity=getVelocity();
		private final Vec3 previousPosition=new Vec3(prevX,prevY,prevZ);
		private final Vec3 previousRenderPosition=new Vec3(lastRenderX,lastRenderY,lastRenderZ);
		private final List<RenderPart> sourcePreviousParts=previousRenderParts, sourceCurrentParts=currentRenderParts;
		private final int sourceAge=age, sourceLife=lifeTicks, sourceEpoch=portalPoseEpoch;
		private final List<Box> destination, sweepStart;
		private final Box destinationBounds;
		private boolean applied;

		private PreparedPortalTransfer(RagdollPortalTransfer.Prepared motion,List<Box> destination,List<Box> sweepStart) {
			this.motion=motion;
			this.destination=List.copyOf(destination);
			this.sweepStart=List.copyOf(sweepStart);
			Box bounds=null;
			for(Box box:destination)bounds=union(bounds,box);
			this.destinationBounds=bounds;
		}

		public List<Box> destinationPartBounds(){return destination;}
		public List<Box> sweepStartPartBounds(){return sweepStart;}
		public Box destinationBounds(){return destinationBounds;}
		public Vec3 destinationCentre(){return motion.destinationCentre();}

		/** Validate every transaction member before committing any member on the server thread. */
		public boolean current(){
			return !applied && !isRemoved() && !getWorld().isClient && getWorld()==sourceWorld
					&& getUuid().equals(sourceIdentity) && rigVersionSupported() && simulation==sourceSimulation
					&& age==sourceAge && lifeTicks==sourceLife && portalPoseEpoch==sourceEpoch
					&& getPos().equals(sourcePosition) && getVelocity().equals(sourceVelocity)
					&& new Vec3(prevX,prevY,prevZ).equals(previousPosition)
					&& new Vec3(lastRenderX,lastRenderY,lastRenderZ).equals(previousRenderPosition)
					&& previousRenderParts==sourcePreviousParts && currentRenderParts==sourceCurrentParts
					&& motion.current() && destinationStillClear();
		}

		private boolean destinationStillClear(){
			for(int i=0;i<destination.size();i++)
				if(!portalVolumeClear(union(sweepStart.get(i),destination.get(i))))return false;
			return true;
		}

		/** Commit once after whole-group acceptance; never follow this with a generic entity teleport. */
		public boolean apply(){
			if(!current() || !motion.apply())return false;
			applied=true;
			Quat turn=motion.rotation();
			previousRenderParts=rotateRenderParts(sourcePreviousParts,turn);
			currentRenderParts=rotateRenderParts(sourceCurrentParts,turn);
			portalRenderFrame=ImmPtlQuaternions.hamiltonProduct(turn,portalRenderFrame).normalized();
			portalPoseEpoch++;
			syncEntityFromSimulation();
			Vec3 previous=motion.transformPoint(previousPosition), renderPrevious=motion.transformPoint(previousRenderPosition);
			prevX=previous.x();prevY=previous.y();prevZ=previous.z();
			lastRenderX=renderPrevious.x();lastRenderY=renderPrevious.y();lastRenderZ=renderPrevious.z();
			dataTracker.set(POSE,trackedPose());
			velocityModified=true;
			return true;
		}
	}

	private static List<RenderPart> rotateRenderParts(List<RenderPart> parts,Quat turn){
		return parts.stream().map(part->new RenderPart(turn.rotate(part.position()),
				ImmPtlQuaternions.hamiltonProduct(turn,part.rotation()).normalized())).toList();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
        readHullIdentity(nbt);
        if(!rigVersionSupported()&&!getWorld().isClient)
            com.terminaldetector.drmd.DescentMod.LOGGER.warn("Ragdoll {} refuses unsupported rigVersion {}; preserving optional physics metadata without simulation",getUuid(),nbt.get("rigVersion"));
		lifeTicks = Math.max(0, nbt.getInt("life"));
		simulation = new RagdollSimulation(rig, pure(getPos()), Quat.IDENTITY, new Vec3(0, 0, 0));
		if(rigVersionSupported()){
			loadPersistentState(nbt.getCompound("ragdoll"));
			syncEntityFromSimulation();
		}else setBoundingBox(combinedBounds());
		dataTracker.set(POSE, trackedPose());
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
        if(opaqueRigData!=null){copyKeys(opaqueRigData,nbt,List.copyOf(opaqueRigData.getKeys()));return;}
        writeHullIdentity(nbt);
		nbt.putInt("life", lifeTicks);
		nbt.put("ragdoll", persistentState());
	}

	@Override
	public Optional<Vec3d> physicsRaycast(Vec3d start, Vec3d end) {
		Vec3 worldStart = pure(start), worldEnd = pure(end);
		Vec3d best = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (RagdollSimulation.Part part : simulation.parts()) {
			Quat inverse = part.body().rotation().inverse();
			Vec3 a = inverse.rotate(worldStart.minus(part.body().position()));
			Vec3 b = inverse.rotate(worldEnd.minus(part.body().position()));
			Vec3 half = part.segment().halfExtents();
			Box localBox = new Box(-half.x(), -half.y(), -half.z(), half.x(), half.y(), half.z());
			Optional<Vec3d> localHit = localBox.raycast(toMinecraft(a), toMinecraft(b));
			if (localHit.isEmpty()) continue;
			Vec3 worldHit = part.body().rotation().rotate(pure(localHit.get())).plus(part.body().position());
			Vec3d minecraftHit = toMinecraft(worldHit);
			double distance = start.squaredDistanceTo(minecraftHit);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = minecraftHit;
			}
		}
		return Optional.ofNullable(best);
	}

	@Override
	public void applyPhysicsImpulse(Vec3d impact, Vec3d impulse) {
		if (getWorld().isClient || !rigVersionSupported() || !finite(impact) || !finite(impulse)) return;
		simulation.applyImpulseAt(pure(impact),pure(impulse));
		dataTracker.set(POSE, trackedPose());
		velocityModified = true;
	}

	/** Projectile-friendly conversion preserving the old damage-to-impulse tuning. */
	public void weaponImpulse(Vec3d impact, Vec3d direction, float damage) {
		if (!Float.isFinite(damage) || damage <= 0 || !finite(direction) || direction.lengthSquared() < 1.0e-12) return;
		applyPhysicsImpulse(impact, direction.normalize().multiply(Math.min(80, damage * .15)));
	}

	@Override
	public Vec3d physicsVelocity() {
		return toMinecraft(simulation.centreVelocity());
	}

	@Override
	public double physicsMass() {
		return simulation.totalMass();
	}

	@Override
	public Optional<Vec3d> physicsLongAxis() {
		if (simulation.parts().isEmpty()) return Optional.empty();
		RagdollSimulation.Part core = simulation.parts().get(0);
		Vec3 half = core.segment().halfExtents();
		Vec3 local = half.x() >= half.y() && half.x() >= half.z() ? new Vec3(1, 0, 0)
				: half.y() >= half.z() ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
		return Optional.of(toMinecraft(core.body().rotation().rotate(local)));
	}

	@Override
	public Vec3d physicsAngularVelocity() {
		if (simulation.parts().isEmpty()) return Vec3d.ZERO;
		return toMinecraft(simulation.parts().get(0).body().angularVelocity());
	}

	@Override
	public void applyPhysicsTorque(Vec3d torque) {
		if (getWorld().isClient || !rigVersionSupported() || simulation.parts().isEmpty() || !finite(torque)) return;
		simulation.parts().get(0).body().applyTorque(pure(torque));
		dataTracker.set(POSE, trackedPose());
		velocityModified = true;
	}

	@Override
	public boolean canHit() {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	private static void putVector(NbtCompound tag, String key, Vec3 value) {
		tag.putDouble(key + "x", value.x());
		tag.putDouble(key + "y", value.y());
		tag.putDouble(key + "z", value.z());
	}

	private static Vec3 readVector(NbtCompound tag, String key) {
		Vec3 value = new Vec3(tag.getDouble(key + "x"), tag.getDouble(key + "y"), tag.getDouble(key + "z"));
		if (!Double.isFinite(value.lengthSquared())) throw new IllegalArgumentException("non-finite ragdoll vector");
		return value;
	}

	private static void putQuaternion(NbtCompound tag, Quat value) {
		tag.putDouble("qx", value.x());
		tag.putDouble("qy", value.y());
		tag.putDouble("qz", value.z());
		tag.putDouble("qw", value.w());
	}

	private static Quat readQuaternion(NbtCompound tag) {
		Quat value = new Quat(tag.getDouble("qx"), tag.getDouble("qy"), tag.getDouble("qz"), tag.getDouble("qw"));
		double length = value.x() * value.x() + value.y() * value.y() + value.z() * value.z() + value.w() * value.w();
		if (!Double.isFinite(length) || length < 1.0e-12) throw new IllegalArgumentException("invalid ragdoll rotation");
		return value.normalized();
	}

	private static boolean finite(Vec3d value) {
		return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}

	private static Vec3 pure(Vec3d value) {
		return new Vec3(value.x, value.y, value.z);
	}

	private static Vec3d toMinecraft(Vec3 value) {
		return new Vec3d(value.x(), value.y(), value.z());
	}
}
