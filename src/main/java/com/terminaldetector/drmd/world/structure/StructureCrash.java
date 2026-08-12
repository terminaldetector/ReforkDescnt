package com.terminaldetector.drmd.world.structure;

/**
 * Pure fall-speed curve for a crashing structure — zero Minecraft dependency, directly unit-testable
 * (mirrors the {@code AerisDensity}/{@code AerisDensityTest} idiom). The Minecraft-facing shell
 * ({@code tickDescent(ServerWorld, StructureInstance, int)}, driving repeated
 * {@code StructureMover.moveTo} calls with this speed and detecting heightmap contact) is Phase 2 scope,
 * not part of this file.
 */
public final class StructureCrash {
	public static final double BASE = 0.05;
	public static final double ACCEL = 0.02;
	public static final double MAX = 1.6;

	private StructureCrash() {}

	/** Downward speed (blocks/tick) at a given number of ticks into a crash. Monotonic, capped at {@link #MAX}. */
	public static double fallSpeed(int crashTicks) {
		if (crashTicks < 0) crashTicks = 0;
		return Math.min(BASE + ACCEL * crashTicks, MAX);
	}
}
