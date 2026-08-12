package com.terminaldetector.drmd.world.structure;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure geometry: translate a cell set and compute the set-difference between two placements of the
 * same shape. Zero Minecraft dependency by design — {@code StructureDeltaTest} calls this class
 * directly rather than mirroring it, the way {@code AerisDensityTest} calls {@code AerisDensity}
 * directly, since there's nothing here that needs a Minecraft classpath to exercise.
 *
 * <p>{@link Cell} is a fresh, dependency-free type rather than {@code BlockPos} on purpose: it's what
 * keeps this file (and its test) compilable with plain {@code javac}, verifiable before ever touching
 * a CI round-trip.
 *
 * <p>This is the entire efficiency/smoothness win over "delete every block, redraw every block" — for
 * a structure translated by a small step, most cells are common to both the old and new placement and
 * are never touched at all; only the leading/trailing shell that actually changes is written.
 */
public final class StructureDelta {
	public record Cell(int x, int y, int z) {}

	private StructureDelta() {}

	public static Set<Cell> translate(Set<Cell> cells, int dx, int dy, int dz) {
		Set<Cell> out = new HashSet<>(cells.size() * 2);
		for (Cell c : cells) out.add(new Cell(c.x() + dx, c.y() + dy, c.z() + dz));
		return out;
	}

	public record Diff(Set<Cell> toClear, Set<Cell> toPlace) {}

	/**
	 * {@code toClear = oldCells - newCells}, {@code toPlace = newCells - oldCells}. Cells common to
	 * both sets are left untouched. The two output sets are always disjoint by construction, so a
	 * caller never needs to reason about clear/place ordering.
	 */
	public static Diff diff(Set<Cell> oldCells, Set<Cell> newCells) {
		Set<Cell> toClear = new HashSet<>(oldCells);
		toClear.removeAll(newCells);
		Set<Cell> toPlace = new HashSet<>(newCells);
		toPlace.removeAll(oldCells);
		return new Diff(toClear, toPlace);
	}
}
