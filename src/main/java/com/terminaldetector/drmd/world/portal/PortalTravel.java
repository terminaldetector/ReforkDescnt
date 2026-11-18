package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.diag.DiagTrace;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Carries entities through a linked portal pair — the world-facing half of {@link PortalCrossing},
 * which owns the arithmetic and is tested on its own.
 *
 * <p>Shared by both linked block families rather than written twice. A charged mirror is a one-block
 * face and a portal panel is several blocks across, but nothing else about travelling through them
 * differs, so the span is a parameter and the rest is common. Copying it would have let the two drift.
 *
 * <p><b>Why this exists at all:</b> both blocks already stored their partner and did nothing with it.
 * The travel was performed by an Immersive Portals {@code Portal} entity spawned in
 * {@code ImmPtlMirrorBridge}, so with that mod absent a linked pair was decorative and said so. This
 * is that behaviour without the dependency.
 *
 * <p><b>Same dimension only, deliberately.</b> Auto-linking produces nothing else, and a
 * cross-dimension hop is a different call with its own failure modes — chunk loading at the far end,
 * the player's own dimension-change packet. A cross-dimension link carries nobody yet, exactly as
 * before.
 */
public final class PortalTravel {
	private PortalTravel() {}

	/**
	 * Ticks a traveller is ignored for after arriving.
	 *
	 * <p>Not a rate limit — a correctness guard. A teleport leaves {@code prevX/prevY/prevZ} back at the
	 * portal that was entered, so the very next tick presents a step reaching from one end of the link
	 * to the other. That segment can cross the far portal's plane on its own and throw the traveller
	 * straight back, forever. Ignoring them briefly makes the stale step harmless without depending on
	 * exactly how any one entity type refreshes its previous position.
	 */
	private static final long ARRIVAL_COOLDOWN_TICKS = 10;

	/** Traveller → world time they may travel again. Pruned as it grows; never swept on a schedule. */
	private static final Map<UUID, Long> RECENT_ARRIVALS = new HashMap<>();
    private record Face(BlockPos source,Direction facing,BlockPos partner,Direction partnerFacing,double span,long tick) {}
    /** Current owner-thread faces only. Values contain no world/entity reference. */
    private static final Map<ServerWorld,Map<BlockPos,Face>> FACES=new WeakHashMap<>();
    private static void rememberFace(ServerWorld world,BlockPos source,Direction facing,BlockPos partner,Direction partnerFacing,double span,long tick){
        var faces=FACES.computeIfAbsent(world,w->new HashMap<>());
        if(faces.size()>=4096){faces.values().removeIf(f->f.tick<tick);if(faces.size()>=4096&&!faces.containsKey(source))return;}
        faces.put(source.toImmutable(),new Face(source.toImmutable(),facing,partner.toImmutable(),partnerFacing,span,tick));
    }


    /** Final phase is installed after body contacts, loose drops, flight and boarding motors. */
    public static void register(){
        // Registered after body contacts, loose drops, flight and boarding motors.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(PortalTravel::drain);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server,world)->{FACES.remove(world);COMPLETED.remove(world);});
    }
    public static void enqueue(ServerWorld world,BlockPos pos,Direction facing,BlockPos partner,Direction partnerFacing,double halfSpan){
        if(world.getServer().isOnThread())rememberFace(world,pos,facing,partner,partnerFacing,halfSpan,world.getServer().getTicks());
    }
    /** Direct synchronous API. Production block ticks enqueue and the final movement phase drains. */
    public static void carry(ServerWorld world,BlockPos pos,Direction facing,BlockPos partner,Direction partnerFacing,double halfSpan){
        if(!world.getServer().isOnThread())return;
        var face=new Face(pos.toImmutable(),facing,partner.toImmutable(),partnerFacing,halfSpan,world.getServer().getTicks());
        rememberFace(world,pos,facing,partner,partnerFacing,halfSpan,face.tick);process(world,java.util.List.of(face));
    }
    private record Completed(java.lang.ref.WeakReference<net.minecraft.entity.ItemEntity> item,Vec3d start,Vec3d end,long tick) {}
    private static final Map<ServerWorld,Map<UUID,Completed>> COMPLETED=new WeakHashMap<>();
    /** Records one actual completed deferred move; it never moves an item during another solver. */
    public static boolean afterDeferredMove(ServerWorld world,net.minecraft.entity.ItemEntity item,Vec3d actualStart){
        if(!world.getServer().isOnThread()||item.isRemoved()||item.getWorld()!=world||item.hasVehicle()
            ||!Double.isFinite(actualStart.lengthSquared()))return false;
        var map=COMPLETED.computeIfAbsent(world,w->new java.util.LinkedHashMap<>());long tick=world.getServer().getTicks();
        map.values().removeIf(p->p.tick!=tick||p.item.get()==null);
        if(map.size()>=128&&!map.containsKey(item.getUuid()))return false;
        map.put(item.getUuid(),new Completed(new java.lang.ref.WeakReference<>(item),actualStart,item.getPos(),tick));return true;
    }
    public static void drain(ServerWorld world){
        if(!world.getServer().isOnThread())return;long tick=world.getServer().getTicks();
        var map=FACES.get(world);
        if(map!=null){var faces=map.values().stream().filter(f->f.tick==tick&&validFace(world,f)).sorted(java.util.Comparator.comparing(f->f.source.toShortString())).toList();process(world,faces);map.values().removeIf(f->f.tick<=tick);}
        COMPLETED.remove(world);
    }
    private static java.util.LinkedHashSet<Entity> candidates(ServerWorld world,Face face){
        Box reach=new Box(face.source).expand(face.span+33);var result=new java.util.LinkedHashSet<Entity>();
        for(var e:world.getEntitiesByClass(Entity.class,reach,e->!e.isSpectator()))result.add(e.getRootVehicle());
        result.addAll(com.terminaldetector.drmd.world.contraption.BodySpatialIndex.query(world,reach));
        for(var p:com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.players(world))if(!p.isSpectator())result.add(p.getRootVehicle());
        var completed=COMPLETED.get(world);if(completed!=null)for(var c:completed.values()){
            var item=c.item.get();if(item!=null&&c.tick==face.tick&&item.getWorld()==world&&!item.isRemoved())result.add(item);
        }
        return result;
    }
    private static void process(ServerWorld world,java.util.List<Face> faces){
        var processed=new java.util.HashSet<UUID>();
        // All coherent physical/riding groups go first across ALL faces, then independent actors.
        for(int phase=0;phase<2;phase++)for(var face:faces){
            var sourceState=com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(face.source);
            boolean gun=sourceState.isPresent()&&sourceState.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock
                &&sourceState.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.CHANNEL)>0;
            for(var entity:candidates(world,face)){
                boolean group=entity instanceof com.terminaldetector.drmd.world.contraption.BlockBodyEntity||entity.hasPassengers();
                if((phase==0)!=group||entity.hasVehicle()||processed.contains(entity.getUuid())||onCooldown(entity.getUuid(),face.tick))continue;
                var previous=new PortalTransform.Vec3(entity.prevX,entity.prevY,entity.prevZ);boolean solverComplete=false;
                if(entity instanceof net.minecraft.server.network.ServerPlayerEntity&&entity instanceof com.terminaldetector.drmd.world.portal.gun.PortalMotionAccess motion){var start=motion.drmd$portalMovementStart(face.tick);if(start!=null)previous=toPure(start);}
                var completed=COMPLETED.get(world);var actual=completed==null?null:completed.get(entity.getUuid());
                if(actual!=null){if(actual.item.get()!=entity||!entity.getPos().equals(actual.end))continue;previous=toPure(actual.start);solverComplete=true;}
                var result=attempt(world,entity,previous,face,gun,solverComplete);
                // A refused exit must not suppress a different live face crossed in this step.
                // Arrival is marked only after the complete transaction actually commits.
                if(result!=null&&onCooldown(entity.getUuid(),face.tick))
                    for(var member:result.members())processed.add(member.getUuid());
            }
        }
    }
    private static boolean validFace(ServerWorld world,Face f){
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        var a=access.readIfLoaded(f.source);var b=access.readIfLoaded(f.partner);if(a.isEmpty()||b.isEmpty())return false;
        if(a.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock
            &&b.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock
            &&a.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.LINKED)
            &&b.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.LINKED)
            &&a.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.FACING)==f.facing
            &&b.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.FACING)==f.partnerFacing
            &&world.getBlockEntity(f.source) instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity source
            &&world.getBlockEntity(f.partner) instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity target)
            return source.isLinked()&&target.isLinked()&&f.partner.equals(source.getLinkPartnerPos())&&f.source.equals(target.getLinkPartnerPos())
                &&(source.getLinkPartnerDim()==null||source.getLinkPartnerDim().equals(world.getRegistryKey()))
                &&(target.getLinkPartnerDim()==null||target.getLinkPartnerDim().equals(world.getRegistryKey()));
        if(a.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock
            &&b.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock
            &&a.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.LINKED)
            &&b.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.LINKED)
            &&a.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.FACING)==f.facing
            &&b.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.FACING)==f.partnerFacing
            &&world.getBlockEntity(f.source) instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity source
            &&world.getBlockEntity(f.partner) instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity target)
            return source.isLinked()&&target.isLinked()&&f.partner.equals(source.getLinkPartnerPos())&&f.source.equals(target.getLinkPartnerPos())
                &&(source.getLinkPartnerDim()==null||source.getLinkPartnerDim().equals(world.getRegistryKey()))
                &&(target.getLinkPartnerDim()==null||target.getLinkPartnerDim().equals(world.getRegistryKey()));
        return false;
    }
    private static PortalTransfer.Attempt attempt(ServerWorld world,Entity entity,PortalTransform.Vec3 previous,Face f,boolean gun,boolean solverComplete){
        long now=world.getServer().getTicks();if(onCooldown(entity.getUuid(),now))return null;
        Vec3d normal=Vec3d.of(f.facing.getVector()),partnerNormal=Vec3d.of(f.partnerFacing.getVector());
        Vec3d face=Vec3d.ofCenter(f.source).add(normal.multiply(.5));
        var plane=toPure(face);var n=toPure(normal);var current=toPure(entity.getPos());
        var leading=PortalTransfer.leading(entity,previous,n);if(leading==null)return null;
        var leadingCurrent=leading.after();var leadingPrevious=leading.before();
        PortalTransform.Vec3 hit,referenceNow;
        if(PortalCrossing.crossedInward(leadingPrevious,leadingCurrent,plane,n)){hit=PortalCrossing.crossingPoint(leadingPrevious,leadingCurrent,plane,n);referenceNow=leadingCurrent;}
        else if(PortalCrossing.crossedInward(previous,current,plane,n)){hit=PortalCrossing.crossingPoint(previous,current,plane,n);referenceNow=current;}
        else return null;
        if(hit==null||!PortalCrossing.withinFace(hit,plane,n,f.span)||!faceIsOpenTo(world,entity,face,normal,hit))return null;
        var transform=com.terminaldetector.drmd.d6.D6PortalTransform.of(plane,n,toPure(Vec3d.ofCenter(f.partner).add(partnerNormal.multiply(.5))),toPure(partnerNormal));
        var residual=transform.transformVelocity(referenceNow.minus(hit));
        var attempt=PortalTransfer.prepare(world,entity,transform,residual,hit,f.span,gun,now,id->onCooldown(id,now),solverComplete);
        if(attempt.accepted()==null||!attempt.accepted().apply()){DiagTrace.count("portal.blockedGroup");return attempt;}
        for(var member:attempt.members())markArrived(member.getUuid(),now);
        DiagTrace.record("portal","carried group of "+attempt.members().size()+" through "+f.source+" to "+f.partner);
        DiagTrace.count("portal.carried");return attempt;
    }

    public static boolean exitIsClear(ServerWorld world,Entity traveller,Vec3d destination) {
        if(!Double.isFinite(destination.lengthSquared()))return false;
        Box body=traveller.getBoundingBox().offset(destination.subtract(traveller.getPos()));
        if(body.minY<world.getBottomY() || body.maxY>world.getTopY())return false;
        var min=BlockPos.ofFloored(body.minX,body.minY,body.minZ);
        var max=BlockPos.ofFloored(body.maxX-1e-7,body.maxY-1e-7,body.maxZ-1e-7);
        if(!world.getWorldBorder().contains(min) || !world.getWorldBorder().contains(max))return false;
        int x0=(min.getX()-1)>>4,x1=(max.getX()+1)>>4,z0=(min.getZ()-1)>>4,z1=(max.getZ()+1)>>4;
        int y0=(min.getY()-1)>>4,y1=(max.getY()+1)>>4;
        if((long)(x1-x0+1)*(z1-z0+1)*(y1-y0+1)>64)return false;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return world.isSpaceEmpty(traveller,body);
    }

	/**
	 * Whether the crossing point is on the open part of the portal plane, rather than through the wall
	 * beside it.
	 *
	 * <p><b>The gap this closes.</b> {@link PortalCrossing#withinFace} measures the <em>nominal</em>
	 * span — how far from the anchor a crossing may land — and nothing measured whether the plane is
	 * actually open that far. For a charged mirror the span is three quarters of a block, so the
	 * question never arose. For a portal panel it is two either side, a five-block plane of which one
	 * block is the panel and the other twenty-four are whatever happens to be there. Mounted on a wall,
	 * that plane continues straight through it, and anyone in the next room crossing it at the same
	 * depth was carried — through solid rock, from a portal they could not see.
	 *
	 * <p>One ray from the anchor's face to the crossing point settles it: if the plane is walled off
	 * between the two, they are not on the same face.
	 *
	 * <p>Both ends are nudged a little along the normal, onto the side the traveller came from. A ray
	 * running exactly along a block face grazes it unpredictably, and the approach side is by
	 * definition open — the traveller was standing in it.
	 *
	 * <p>Costs nothing in the common case: a crossing within a block of the anchor is on the anchor's
	 * own face by construction, which covers every mirror and the middle of every panel, and returns
	 * before any ray is cast.
	 */
	private static boolean faceIsOpenTo(ServerWorld world, Entity traveller, Vec3d faceCentre,
			Vec3d normal, PortalTransform.Vec3 hit) {
		Vec3d hitPoint = new Vec3d(hit.x(), hit.y(), hit.z());
		if (faceCentre.squaredDistanceTo(hitPoint) <= 1.0) return true;

		Vec3d offset = normal.multiply(APPROACH_NUDGE);
        if(!com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(world,faceCentre.add(offset),hitPoint.add(offset)))return false;
		BlockHitResult blocked = world.raycast(new RaycastContext(
				faceCentre.add(offset), hitPoint.add(offset),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, traveller));
		return blocked.getType() == HitResult.Type.MISS;
	}

	/** How far off the plane the reachability ray runs, in blocks — enough not to graze the face. */
	private static final double APPROACH_NUDGE = 0.05;

	private static boolean onCooldown(UUID id, long now) {
		Long until = RECENT_ARRIVALS.get(id);
        return until != null && now < until;
	}

	private static void markArrived(UUID id, long now) {
		// Bounded without a scheduled sweep: entries only ever expire, so clearing the stale ones
		// whenever the map grows keeps it the size of "travellers in flight" rather than of everyone
		// who has ever used a portal on this server.
		if (RECENT_ARRIVALS.size() > 64) {
			RECENT_ARRIVALS.entrySet().removeIf(e -> now >= e.getValue());
		}
		RECENT_ARRIVALS.put(id, now + ARRIVAL_COOLDOWN_TICKS);
	}

	public static void clear() { RECENT_ARRIVALS.clear();COMPLETED.clear();FACES.clear(); }

	private static Vec3d fromPure(PortalTransform.Vec3 v) { return new Vec3d(v.x(), v.y(), v.z()); }

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}
}
