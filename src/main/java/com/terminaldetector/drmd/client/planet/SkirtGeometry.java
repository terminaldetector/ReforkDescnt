package com.terminaldetector.drmd.client.planet;

/**
 * Pure "does this cell need a wall toward that neighbor" rule for {@link PlanetSurfaceMesh}'s heightfield
 * skirts — zero imports at all, same idiom as {@code world.structure.StructureFaceCuller}: the caller
 * samples heights (from {@code SurfaceStore}/{@code PlanetMap}, both of which need the game's classpath),
 * this file only ever compares two already-sampled numbers.
 *
 * <p>The rule is symmetric by construction: for any two adjacent cells, exactly one of
 * {@code drawsSkirt(a, b)} / {@code drawsSkirt(b, a)} is ever true (the higher one), never both, never
 * neither (barring an exact tie, which draws nothing — flat ground needs no wall). That symmetry is what
 * makes checking all four neighbor directions correct: whichever side of a shared edge is higher draws
 * it, so every real step gets exactly one wall regardless of which cell the mesh builder visits first.
 */
public final class SkirtGeometry {
	private SkirtGeometry() {}

	/** True when {@code myTop} is strictly higher than {@code neighborTop} — this cell owns the wall down to it. */
	public static boolean drawsSkirt(double myTop, double neighborTop) {
		return myTop > neighborTop;
	}

	/** Where a skirt toward a lower neighbor should stop — the neighbor's own height, never below the water floor. */
	public static double skirtBottom(double neighborTop, double floor) {
		return Math.max(neighborTop, floor);
	}
}
