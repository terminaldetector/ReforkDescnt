package com.terminaldetector.drmd.client.portal;

/**
 * Pure "should this mirror actually recurse into a reflected render" gate — zero Minecraft imports,
 * same idiom as {@link PortalTransform}/{@code SkirtGeometry}: the caller samples the facts (distance,
 * current recursion depth), this file only ever decides yes/no from numbers already in hand.
 *
 * <p>Mirrors the shape of ImmPtl's own {@code PortalRenderer.shouldSkipRenderingPortal}/
 * {@code getRenderRange} (confirmed against the real, non-decompiled ImmersivePortalsMod-1.21 source,
 * not the decompiled jar) rather than inventing new gate logic: a hard recursion-depth cap, plus a
 * render-range cutoff that shrinks with recursion depth so a mirror facing another mirror doesn't keep
 * rendering as far out at every nested layer as the primary view does.
 */
public final class MirrorRenderGate {
	private MirrorRenderGate() {}

	/** Matches ImmPtl's own default ({@code IPGlobal.maxPortalLayer = 5}) — see the R0 research notes
	 * in {@code spicy-jumping-anchor.md} for where this number comes from. */
	public static final int DEFAULT_MAX_RECURSION_DEPTH = 5;

	/**
	 * @param currentRecursionDepth 0 for the primary view; 1 for a reflection rendered from within the
	 *                              primary view; 2 for a reflection-of-a-reflection; and so on.
	 * @param maxRecursionDepth     the hard cap — {@link #DEFAULT_MAX_RECURSION_DEPTH} unless overridden.
	 * @param distanceToMirror      straight-line distance from the current camera to the mirror.
	 * @param renderRangeAtDepthZero the ordinary (non-recursive) render distance, in blocks.
	 */
	public static boolean shouldRender(int currentRecursionDepth, int maxRecursionDepth,
			double distanceToMirror, double renderRangeAtDepthZero) {
		if (currentRecursionDepth > maxRecursionDepth) return false;
		if (distanceToMirror < 0) return false;
		return distanceToMirror <= renderRangeForDepth(renderRangeAtDepthZero, currentRecursionDepth);
	}

	/**
	 * Render range shrinks with recursion depth — matches {@code PortalRenderer.getRenderRange()}'s own
	 * shape ({@code range /= portalLayer} once layer exceeds 1): a mirror seen inside another mirror's
	 * own reflection is already a small, distant detail in the frame, so it doesn't need the primary
	 * view's full draw distance to read correctly, and shrinking it caps how much extra scene the
	 * deepest layer of a mirror-in-mirror chain has to draw.
	 */
	public static double renderRangeForDepth(double baseRange, int depth) {
		if (depth <= 1) return baseRange;
		return baseRange / depth;
	}
}
