package com.terminaldetector.drmd.world.structure;

/**
 * Pure fall-speed curve for a crashing structure — zero Minecraft dependency, directly unit-testable
 * (mirrors the {@code AerisDensity}/{@code AerisDensityTest} idiom). Deliberately kept free of
 * {@code ServerWorld}/{@code BlockPos} imports even now that Phase 2 needs a world-touching tick shell:
 * that shell lives in {@code StructureMover.tickCrashDescent} instead of here, specifically so this file
 * stays permanently plain-{@code javac}-compilable rather than only until the first impure method got
 * added to it.
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

	/** {@code wholeBlocks}: how far to actually move this tick. {@code remainder}: carry into the next tick's accumulator. */
	public record DescentStep(int wholeBlocks, double remainder) {}

	/**
	 * Blocks only move in whole-number steps, but {@link #fallSpeed} is sub-block for most of the early
	 * crash (0.05..1.6 blocks/tick) — so the caller carries a fractional accumulator across ticks
	 * (starting at 0.0) and this folds this tick's speed into it, splitting the result into the whole
	 * blocks to actually drop now and the leftover fraction to pass into the next call.
	 */
	public static DescentStep nextDrop(double fallAccumulator, int crashTicks) {
		double total = fallAccumulator + fallSpeed(crashTicks);
		int whole = (int) Math.floor(total);
		return new DescentStep(whole, total - whole);
	}
}
