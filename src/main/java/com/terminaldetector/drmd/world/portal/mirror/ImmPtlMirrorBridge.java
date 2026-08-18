package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Mirror;

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
		Mirror mirror = Mirror.ENTITY_TYPE.create(world);
		if (mirror == null) return null;
		Vec3d center = Vec3d.ofCenter(pos);
		mirror.setOriginPos(center);
		mirror.setDestination(center); // formality — Mirror.canTeleportEntity() is always false
		mirror.setDestinationDimension(world.getRegistryKey());
		PortalAPI.setPortalOrthodoxShape(mirror, facing, new Box(pos));
		world.spawnEntity(mirror);
		return mirror.getUuid();
	}

	/** Removes a previously {@link #attach}ed mirror entity, if it still exists. */
	public static void detach(ServerWorld world, UUID mirrorId) {
		if (mirrorId == null) return;
		Entity e = world.getEntity(mirrorId);
		if (e instanceof Mirror) e.discard();
	}

	// Phase 5 adds: linkPortals(...)
}
