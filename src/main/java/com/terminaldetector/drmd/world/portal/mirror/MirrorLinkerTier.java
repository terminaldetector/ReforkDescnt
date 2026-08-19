package com.terminaldetector.drmd.world.portal.mirror;

/**
 * Three grades of {@link MirrorLinkerItem}, mirroring {@link com.terminaldetector.drmd.world.build.ConstructLaserTier}'s
 * one-registered-item-per-tier shape rather than a mutable charge counter — this codebase has no
 * precedent for the latter (see the Citadel/mirror research this feature shipped alongside).
 */
public enum MirrorLinkerTier {
	/** Same dimension, no rotation/scale — the cheapest, always-available link. */
	SAME_DIMENSION("§b", "Resonance Key", false, false, 1.0),
	/** Same or different dimension, portal can rotate space between the two faces and rescale it.
	 *  1.25 is a placeholder scale factor, not a tuned number — a real per-link scale control is a
	 *  follow-up, not part of this pass. */
	ROTATED_SCALED("§d", "Warped Resonance Key", true, false, 1.25),
	/** Unlocks linking across dimensions outright. */
	CROSS_DIMENSION("§6", "Transdimensional Key", true, true, 1.0);

	public final String colorCode;
	public final String label;
	public final boolean allowsRotation;
	public final boolean allowsCrossDimension;
	public final double scale;

	MirrorLinkerTier(String colorCode, String label, boolean allowsRotation, boolean allowsCrossDimension, double scale) {
		this.colorCode = colorCode;
		this.label = label;
		this.allowsRotation = allowsRotation;
		this.allowsCrossDimension = allowsCrossDimension;
		this.scale = scale;
	}
}
