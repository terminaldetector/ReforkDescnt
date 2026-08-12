package com.terminaldetector.drmd.world.mega;

/**
 * Pure geometry/material classification for the Sky UFO hull — zero Minecraft imports, extracted from
 * {@link SkyUfoHull#build}'s main sweep so it can be unit-tested directly (mirroring the
 * {@code AerisDensity}/{@code AerisDensityTest} idiom) and reused by
 * {@code StructureTemplate.captureTemplate} without a live {@code ServerWorld}.
 *
 * <p>Fixes one pre-existing dead-code bug relative to {@code build}'s current source: there, the
 * cyan-glass branch ({@code (x+z)%7==0 && outer}) sits in an {@code else if} <em>after</em> the
 * unconditional {@code if (outer)} branch has already consumed every {@code outer==true} cell, so no
 * window has ever actually rendered on this hull. Here the glass check runs before the plain-outer
 * check, so it can actually fire.
 *
 * <p>{@code MAJOR}/{@code MINOR} are independent literals here, not a reference to
 * {@link SkyUfoHull#MAJOR}/{@link SkyUfoHull#MINOR} — {@code SkyUfoHull} imports real Minecraft types
 * ({@code BlockState}, {@code ServerWorld}, ...) that aren't on the classpath this file is verified
 * against (plain {@code javac}, no Minecraft/Yarn jar in this sandbox), so any reference to it, even to
 * a plain {@code int} field, would drag that unavailable classpath in and defeat the entire point of
 * keeping this class dependency-free and directly testable. They currently duplicate
 * {@code SkyUfoHull}'s values; the planned Phase 3 rewire makes {@code SkyUfoHull} reference these
 * instead (this class becomes the geometry's source of truth, {@code SkyUfoHull} a consumer), which
 * removes the duplication instead of adding a second copy of it.
 */
public final class SkyUfoShape {
	public static final int MAJOR = 11;
	public static final int MINOR = 4;

	private SkyUfoShape() {}

	public enum Cell { OUTER_HULL, INNER_SHELL, GLASS, DECK, INTERIOR_AIR, NONE }

	public static Cell classify(int x, int y, int z) {
		double nx = x / (double) MAJOR, ny = (y + 0.2) / (double) (MINOR + 0.5), nz = z / (double) MAJOR;
		double e = nx * nx + ny * ny * 1.7 + nz * nz;
		if (e > 1.02 || e < 0.52) return Cell.NONE;
		boolean bay = z >= -2 && z <= 2 && x >= -2 && x <= 2 && y < -1;
		if (bay) return Cell.NONE;
		boolean outer = e > 0.78;
		if (outer && (x + z) % 7 == 0) return Cell.GLASS;
		if (outer) return Cell.OUTER_HULL;
		if (Math.abs(y) <= 1 && nx * nx + nz * nz < 0.55) return Cell.INTERIOR_AIR;
		if (y == -1 && nx * nx + nz * nz < 0.7) return Cell.DECK;
		return Cell.INNER_SHELL;
	}
}
