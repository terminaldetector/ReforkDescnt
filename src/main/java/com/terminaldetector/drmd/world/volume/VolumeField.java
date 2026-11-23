package com.terminaldetector.drmd.world.volume;

/** Procedural occupancy queried at absolute block coordinates by the cube generator. */
public interface VolumeField {

	/** Positive values are solid. A value of zero is on the boundary and leaves the block empty. */
	double density(int x, int y, int z);

	/** Spatial palette hint for a solid sample. Independent of existing player edits. */
	Material material(int x, int y, int z);

	enum Material {
		BASALT, DEEPSLATE, TUFF, CALCITE, AMETHYST, ENGINEERED, LIGHT, STONE, DIRT, GRASS
	}
}
