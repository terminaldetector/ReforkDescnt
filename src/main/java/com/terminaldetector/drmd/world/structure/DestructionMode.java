package com.terminaldetector.drmd.world.structure;

/**
 * How a destroyed structure meets its end. Kept as a plain, structure-agnostic enum so a future
 * consumer beyond the Sky UFO can pick an outcome without bespoke destruction code of its own.
 */
public enum DestructionMode {
	/** Falls to the ground under {@link StructureCrash}'s fall-speed curve, then a smaller impact shockwave. */
	CRASH,
	/** Immediate mid-air detonation via {@link StructureShockwave}. */
	SHOCKWAVE
}
