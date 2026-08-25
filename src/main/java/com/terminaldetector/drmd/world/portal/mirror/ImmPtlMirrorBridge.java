package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.UUID;

/**
 * The only file in DRMD allowed to import anything under {@code qouteall.imm_ptl.*}.
 *
 * <p>DRMD compiles against Immersive Portals (see {@code build.gradle}'s vendored
 * {@code libs/immersiveportals-*.jar}), but the mod must stay installable and crash-free without
 * ImmPtl present at runtime — {@link com.terminaldetector.drmd.world.portal.PortalComplexity
 * #hasImmersivePortals()} is the existing, reused presence check. Once compiled against it, ImmPtl's
 * classes are only actually loadable in the JVM if its mod jar is also installed; if any
 * always-loaded DRMD class referenced an ImmPtl type in a field or static initializer, the JVM would
 * throw {@code NoClassDefFoundError} the instant that class loaded — crashing every player without
 * ImmPtl installed, regardless of whether the code path that "needed" it ever ran.
 *
 * <p>So: this class must never be referenced from an eager static field or from any always-loaded
 * class's field type anywhere else in DRMD, and every call into it must be preceded, at the call
 * site, by {@code PortalComplexity.hasImmersivePortals()}. Calling one of its methods from inside a
 * method body (never a field type) is safe as long as that check runs first — the JVM resolves a
 * called method's own class lazily, at first invocation, not when the caller's class loads.
 */
public final class ImmPtlMirrorBridge {
	private ImmPtlMirrorBridge() {}

	/**
	 * Spawns a live {@link Mirror} entity sized/oriented to one block's face and returns its UUID for
	 * the caller to remember (see {@link MirrorBlockEntity}) so it can be {@link #detach}ed later.
	 *
	 * <p>{@code Mirror.ENTITY_TYPE} is used directly, unsubclassed — {@code BreakableMirror}'s own
	 * {@code createMirror(...)} convenience is hardcoded to vanilla glass via its own {@code isGlass()}
	 * predicate and will never recognize a DRMD block, so this builds the entity the same way that
	 * method does internally instead of calling it.
	 */
	public static UUID attach(ServerWorld world, BlockPos pos, Direction facing) {
		return attach(world, pos, facing, new Box(pos));
	}

	/**
	 * Same as {@link #attach(ServerWorld, BlockPos, Direction)}, but shaped from an arbitrary
	 * {@code shape} rather than the placed block's own 1-block footprint — {@code PortalAPI
	 * .setPortalOrthodoxShape} was already taking a {@link Box} for the single-block case, so a wider
	 * {@link PortalPanelBlock#panelBox} costs nothing new here, just a bigger box.
	 */
	public static UUID attach(ServerWorld world, BlockPos pos, Direction facing, Box shape) {
		Mirror mirror = Mirror.ENTITY_TYPE.create(world);
		if (mirror == null) return null;
		Vec3d center = Vec3d.ofCenter(pos);
		mirror.setOriginPos(center);
		mirror.setDestination(center); // formality — Mirror.canTeleportEntity() is always false
		mirror.setDestinationDimension(world.getRegistryKey());
		PortalAPI.setPortalOrthodoxShape(mirror, facing, shape);
		world.spawnEntity(mirror);
		return mirror.getUuid();
	}

	/** Removes a previously {@link #attach}ed mirror entity, if it still exists. */
	public static void detach(ServerWorld world, UUID mirrorId) {
		if (mirrorId == null) return;
		Entity e = world.getEntity(mirrorId);
		if (e instanceof Mirror) e.discard();
	}

	/**
	 * Removes any plain {@link Mirror} currently attached at either block, then spawns a real
	 * two-way {@link Portal} link between them and records it on both {@link ChargedMirrorBlock}s.
	 *
	 * <p>Both directions are built explicitly rather than leaning on {@code PortalAPI
	 * .createReversePortal} alone for placement: it derives the reverse portal's rotation/destination
	 * correctly from the forward one, but this call already knows exactly where and which way block B
	 * faces, so {@code setPortalOrthodoxShape} re-anchors the reverse portal to that face precisely
	 * rather than trusting a generic mirrored placement.
	 */
	public static void linkPortals(
			ServerWorld worldA, BlockPos posA, Direction facingA, UUID existingA,
			ServerWorld worldB, BlockPos posB, Direction facingB, UUID existingB,
			MirrorLinkerTier tier) {
		detach(worldA, existingA);
		detach(worldB, existingB);

		Portal forward = Portal.ENTITY_TYPE.create(worldA);
		if (forward == null) return;
		PortalAPI.setPortalOrthodoxShape(forward, facingA, new Box(posA));
		// Best-effort default: maps "the way A faces" onto "the way into B" so walking straight
		// through A continues straight through B when the tier allows rotation. Exact correctness is
		// a live-client check per the plan's own verification section, not provable here.
		DQuaternion rotation = tier.allowsRotation
				? DQuaternion.getRotationBetween(Vec3d.of(facingA.getVector()), Vec3d.of(facingB.getOpposite().getVector()))
				: null;
		PortalAPI.setPortalTransformation(forward, worldB.getRegistryKey(), Vec3d.ofCenter(posB), rotation, tier.scale);
		worldA.spawnEntity(forward);

		Portal reverse = PortalAPI.createReversePortal(forward);
		PortalAPI.setPortalOrthodoxShape(reverse, facingB, new Box(posB));
		worldB.spawnEntity(reverse);

		ChargedMirrorBlock.markLinked(worldA, posA, forward.getUuid(), posB, worldB.getRegistryKey());
		ChargedMirrorBlock.markLinked(worldB, posB, reverse.getUuid(), posA, worldA.getRegistryKey());
	}

	/**
	 * {@link PortalPanelBlock}'s own pairing call — a parallel method rather than a parameterized
	 * {@link #linkPortals} because that method's own body ends by calling {@code ChargedMirrorBlock
	 * .markLinked} directly; threading a second block type through it would mean changing an already-
	 * shipped, working link path for every existing mirror just to share a few lines with a new one.
	 * Same shape (two shaped one-way {@code Portal}s built back to back) and same restriction (auto-
	 * pairing is always same-dimension, unrotated, unscaled — {@link MirrorLinkerTier#SAME_DIMENSION}
	 * hardcoded, not threaded through, since nothing calls this with any other tier).
	 */
	public static void linkPortalPanels(
			ServerWorld worldA, BlockPos posA, Direction facingA, UUID existingA, Box shapeA,
			ServerWorld worldB, BlockPos posB, Direction facingB, UUID existingB, Box shapeB) {
		detach(worldA, existingA);
		detach(worldB, existingB);

		Portal forward = Portal.ENTITY_TYPE.create(worldA);
		if (forward == null) return;
		PortalAPI.setPortalOrthodoxShape(forward, facingA, shapeA);
		PortalAPI.setPortalTransformation(forward, worldB.getRegistryKey(), Vec3d.ofCenter(posB), null,
				MirrorLinkerTier.SAME_DIMENSION.scale);
		worldA.spawnEntity(forward);

		Portal reverse = PortalAPI.createReversePortal(forward);
		PortalAPI.setPortalOrthodoxShape(reverse, facingB, shapeB);
		worldB.spawnEntity(reverse);

		PortalPanelBlock.markLinked(worldA, posA, forward.getUuid(), posB, worldB.getRegistryKey());
		PortalPanelBlock.markLinked(worldB, posB, reverse.getUuid(), posA, worldA.getRegistryKey());
	}
}
