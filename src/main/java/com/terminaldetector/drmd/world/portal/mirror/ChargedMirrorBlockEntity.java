package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.entity.ModBlockEntities;
import com.terminaldetector.drmd.world.portal.PortalCrossing;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server-side link bookkeeping for {@link ChargedMirrorBlock}: which live ImmPtl entity is currently
 * attached (a plain {@code Mirror} before linking, a linked {@code Portal} after), and — once
 * linked — where the other end of the link is. The client-visible half of this ("is this block
 * currently linked") is a {@code LINKED} blockstate property instead of NBT sync, since that one bit
 * is all the client needs to render differently and ordinary blockstate updates already sync it for
 * free — no {@code toInitialChunkDataNbt}/{@code toUpdatePacket} overrides needed, unlike
 * {@code CarvedBlockEntity}'s heavier shape data.
 */
public class ChargedMirrorBlockEntity extends BlockEntity {
	/**
	 * How far either side of the block travellers are considered at all. Only a filter on who is worth
	 * testing — the step test in {@link PortalCrossing#crossedInward} is what actually catches anything
	 * moving faster than this box is wide.
	 */
	private static final double REACH = 1.0;
	/**
	 * How far from the face centre a crossing may land and still count. Half a block would be the
	 * geometric face; a little over that is forgiving at the corners without reaching the next block
	 * along, and stops someone walking through the wall beside the mirror being carried with it.
	 */
	private static final double FACE_RADIUS = 0.75;
	/**
	 * Ticks a traveller is ignored for after arriving.
	 *
	 * <p>Not a rate limit — a correctness guard. A teleport leaves {@code prevX/prevY/prevZ} back at the
	 * portal that was entered, so the very next tick presents a step reaching all the way from one end
	 * of the link to the other. That segment can cross the far portal's plane on its own and throw the
	 * traveller straight back, forever. Ignoring them briefly makes the stale step harmless without
	 * depending on exactly how any one entity type refreshes its previous position.
	 */
	private static final long ARRIVAL_COOLDOWN_TICKS = 10;

	/** Traveller → world time they may travel again. Pruned as it is read; never iterated per entity. */
	private static final java.util.Map<UUID, Long> RECENT_ARRIVALS = new java.util.HashMap<>();

	private static boolean onCooldown(UUID id, long now) {
		Long until = RECENT_ARRIVALS.get(id);
		if (until == null) return false;
		if (now >= until) {
			RECENT_ARRIVALS.remove(id);
			return false;
		}
		return true;
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

	private UUID attachedEntityId;
	private boolean linked;
	private BlockPos linkPartnerPos;
	private RegistryKey<World> linkPartnerDim;

	public ChargedMirrorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHARGED_MIRROR, pos, state);
	}

	public UUID getAttachedEntityId() { return attachedEntityId; }
	public void setAttachedEntityId(UUID id) { this.attachedEntityId = id; markDirty(); }

	public boolean isLinked() { return linked; }
	@Nullable public BlockPos getLinkPartnerPos() { return linkPartnerPos; }
	@Nullable public RegistryKey<World> getLinkPartnerDim() { return linkPartnerDim; }

	public void setLink(UUID attachedEntityId, BlockPos partnerPos, RegistryKey<World> partnerDim) {
		this.attachedEntityId = attachedEntityId;
		this.linked = true;
		this.linkPartnerPos = partnerPos;
		this.linkPartnerDim = partnerDim;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (attachedEntityId != null) nbt.putUuid("attached", attachedEntityId);
		nbt.putBoolean("linked", linked);
		if (linkPartnerPos != null) {
			nbt.putInt("partnerX", linkPartnerPos.getX());
			nbt.putInt("partnerY", linkPartnerPos.getY());
			nbt.putInt("partnerZ", linkPartnerPos.getZ());
		}
		if (linkPartnerDim != null) nbt.putString("partnerDim", linkPartnerDim.getValue().toString());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		attachedEntityId = nbt.containsUuid("attached") ? nbt.getUuid("attached") : null;
		linked = nbt.getBoolean("linked");
		linkPartnerPos = nbt.contains("partnerX")
				? new BlockPos(nbt.getInt("partnerX"), nbt.getInt("partnerY"), nbt.getInt("partnerZ"))
				: null;
		linkPartnerDim = nbt.contains("partnerDim")
				? RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString("partnerDim")))
				: null;
	}

	/**
	 * Native travel through a linked pair — no Immersive Portals needed.
	 *
	 * <p>Until this existed a linked charged mirror stored its partner and did nothing with it: the
	 * travel was performed by an ImmPtl {@code Portal} entity spawned in
	 * {@code ImmPtlMirrorBridge.linkPortals}, so without that mod the block was decorative and said so.
	 * The arithmetic lives in {@link PortalCrossing}, which is tested directly; this is only the part
	 * that needs a world.
	 *
	 * <p><b>Same dimension only, deliberately.</b> Auto-linking already produces nothing else
	 * ({@code MirrorLinkerTier.SAME_DIMENSION} is hardcoded in {@code ChargedMirrorBlock.tryAutoLink}),
	 * and a cross-dimension hop is a different call with its own failure modes — chunk loading at the
	 * far end, the player's own dimension change packet. A cross-dimension link simply does not carry
	 * anyone yet, which is what it already did before this.
	 */
	public static void tick(World world, BlockPos pos, BlockState state, ChargedMirrorBlockEntity be) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (!be.linked || be.linkPartnerPos == null) return;
		if (be.linkPartnerDim != null && !be.linkPartnerDim.equals(world.getRegistryKey())) return;
		if (!(state.getBlock() instanceof ChargedMirrorBlock)) return;

		BlockState partnerState = world.getBlockState(be.linkPartnerPos);
		if (!(partnerState.getBlock() instanceof ChargedMirrorBlock)) return;

		Direction facing = state.get(ChargedMirrorBlock.FACING);
		Direction partnerFacing = partnerState.get(ChargedMirrorBlock.FACING);
		Vec3d normal = MirrorReflection.normalFor(facing);
		Vec3d partnerNormal = MirrorReflection.normalFor(partnerFacing);
		// The face, not the block centre: that is the plane a traveller actually passes through.
		Vec3d face = Vec3d.ofCenter(pos).add(normal.multiply(0.5));
		Vec3d partnerFace = Vec3d.ofCenter(be.linkPartnerPos).add(partnerNormal.multiply(0.5));

		// One block of reach either side of the face. The step test below is what catches anything
		// faster than that; this box only decides who is worth testing at all.
		Box reach = new Box(pos).expand(REACH);
		// Passengers are skipped rather than carried: moving one out from under its vehicle desyncs the
		// pair, and the vehicle is itself in this list and travels on its own.
		long now = serverWorld.getTime();
		for (Entity entity : serverWorld.getEntitiesByClass(Entity.class, reach,
				e -> !e.isSpectator() && !e.hasVehicle())) {
			if (onCooldown(entity.getUuid(), now)) continue;
			PortalTransform.Vec3 prev =
					new PortalTransform.Vec3(entity.prevX, entity.prevY, entity.prevZ);
			PortalTransform.Vec3 now =
					new PortalTransform.Vec3(entity.getX(), entity.getY(), entity.getZ());
			PortalTransform.Vec3 plane = toPure(face);
			PortalTransform.Vec3 n = toPure(normal);

			if (!PortalCrossing.crossedInward(prev, now, plane, n)) continue;

			// Where the step met the plane, so someone passing through the wall a block to the side is
			// not carried: the crossing has to land on the mirror's own face.
			PortalTransform.Vec3 hit = PortalCrossing.crossingPoint(prev, now, plane, n);
			if (hit == null) continue;
			Vec3d hitVec = new Vec3d(hit.x(), hit.y(), hit.z());
			if (hitVec.squaredDistanceTo(face) > FACE_RADIUS * FACE_RADIUS) continue;

			PortalCrossing.Exit exit = PortalCrossing.exitFor(
					now, toPure(entity.getVelocity()),
					plane, n, toPure(partnerFace), toPure(partnerNormal));

			entity.requestTeleport(exit.position().x(), exit.position().y(), exit.position().z());
			entity.setVelocity(new Vec3d(exit.velocity().x(), exit.velocity().y(), exit.velocity().z()));
			// Without this the client keeps its own predicted velocity and fights the new one.
			entity.velocityModified = true;
			markArrived(entity.getUuid(), now);
		}
	}

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}
}
