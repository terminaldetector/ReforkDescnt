package com.terminaldetector.drmd.world.build;

import org.joml.Vector3f;

/**
 * Four logical grades of construction laser — green lines → purple preset builds.
 */
public enum ConstructLaserTier {
	/** Primitive line of blocks along aim. */
	GREEN("§a", "Green", new Vector3f(0.25f, 0.95f, 0.35f), 16, 48, false),
	/** Lines + walls + box frames. */
	YELLOW("§e", "Yellow", new Vector3f(0.95f, 0.85f, 0.2f), 48, 96, false),
	/** Volumes + continuous stream laying. */
	BLUE("§b", "Blue", new Vector3f(0.25f, 0.55f, 1f), 96, 160, true),
	/** Complex presets + continuous stream. */
	PURPLE("§d", "Purple", new Vector3f(0.75f, 0.3f, 1f), 192, 256, true);

	public final String colorCode;
	public final String label;
	public final Vector3f beamRgb;
	public final int defaultLength;
	public final int maxBlocks;
	public final boolean continuous;

	ConstructLaserTier(String colorCode, String label, Vector3f beamRgb,
			int defaultLength, int maxBlocks, boolean continuous) {
		this.colorCode = colorCode;
		this.label = label;
		this.beamRgb = beamRgb;
		this.defaultLength = defaultLength;
		this.maxBlocks = maxBlocks;
		this.continuous = continuous;
	}

	public ConstructShape[] shapes() {
		return switch (this) {
			case GREEN -> new ConstructShape[]{ConstructShape.LINE};
			case YELLOW -> new ConstructShape[]{
					ConstructShape.LINE, ConstructShape.WALL, ConstructShape.BOX_FRAME};
			case BLUE -> new ConstructShape[]{
					ConstructShape.LINE, ConstructShape.WALL, ConstructShape.BOX_FRAME,
					ConstructShape.SOLID, ConstructShape.CYLINDER, ConstructShape.STREAM};
			case PURPLE -> ConstructShape.values();
		};
	}

	public boolean supports(ConstructShape shape) {
		for (ConstructShape s : shapes()) if (s == shape) return true;
		return false;
	}
}
