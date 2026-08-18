package com.terminaldetector.drmd.world.portal.mirror;

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
 *
 * <p>Ships as a skeleton in this phase — real bodies land once {@link MirrorBlock} is upgraded to a
 * {@code BlockWithEntity} that needs them.
 */
public final class ImmPtlMirrorBridge {
	private ImmPtlMirrorBridge() {}

	// Phase 4 adds: attach(ServerWorld, BlockPos, Direction) -> UUID, detach(ServerWorld, UUID)
	// Phase 5 adds: linkPortals(...)
}
