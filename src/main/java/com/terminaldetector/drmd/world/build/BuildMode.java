package com.terminaldetector.drmd.world.build;

/**
 * Player building modes — place relative to look / local surface / chosen plane.
 * Block rotation uses three axes (pitch/yaw/roll of placement).
 */
public enum BuildMode {
	LOOK("Look — place along aim"),
	SURFACE("Surface — local face is floor"),
	PLANE("Plane — lock to chosen plane");

	public final String label;

	BuildMode(String label) {
		this.label = label;
	}

	public BuildMode next() {
		BuildMode[] v = values();
		return v[(ordinal() + 1) % v.length];
	}
}
