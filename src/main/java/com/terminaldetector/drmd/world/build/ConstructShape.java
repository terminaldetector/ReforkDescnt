package com.terminaldetector.drmd.world.build;

/**
 * Build shapes unlocked by construction-laser tier.
 * STREAM = continuous freehand (blue+).
 */
public enum ConstructShape {
	LINE("line", "Line"),
	WALL("wall", "Wall"),
	BOX_FRAME("frame", "Box frame"),
	SOLID("solid", "Solid box"),
	CYLINDER("cylinder", "Cylinder"),
	STREAM("stream", "Stream (hold)"),
	RING("ring", "Ring preset"),
	PLATFORM("platform", "Platform preset"),
	HANGAR("hangar", "Hangar preset"),
	TORUS("torus", "Torus preset");

	public final String id;
	public final String label;

	ConstructShape(String id, String label) {
		this.id = id;
		this.label = label;
	}

	/** Shapes that use scaffold → confirm when Construction Mode is on. */
	public boolean usesScaffold() {
		return this != STREAM;
	}

	public boolean isPreset() {
		return this == RING || this == PLATFORM || this == HANGAR || this == TORUS;
	}
}
