package com.terminaldetector.drmd.world.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;

import java.util.Map;

/**
 * A captured structure shape, relative to a local origin — immutable once built. Carved-air cells are
 * stored as explicit {@code Blocks.AIR.getDefaultState()} entries, not simply absent from {@link #cells()}:
 * that's what lets {@link StructureMover} actively clear obstructions out of a structure's interior
 * cavity as it moves through them, rather than only ever touching cells that started out solid. A cell
 * genuinely absent from the map means "outside the structure entirely" — not this structure's business.
 */
public final class StructureTemplate {
	private final Map<StructureDelta.Cell, BlockState> cells;
	private final Map<String, StructureDelta.Cell> markers;
	// Raw bounds rather than a stored Box: net.minecraft.util.math.Box's min/max accessors aren't
	// something this sandbox can verify against a real Minecraft classpath (a same-named but unrelated
	// MicroGrid.Box record elsewhere in this codebase turned out to be a false precedent for that — see
	// StructureInstance's comment). Building a fresh Box only ever through its proven 6-double
	// constructor, and never reading fields/methods back off an existing one, sidesteps the risk
	// entirely — the same "construct from raw arithmetic, don't decompose" idiom SkyUfoHull/SkyUfoEntity
	// already use for their own interior box.
	final double interiorMinX, interiorMinY, interiorMinZ;
	final double interiorMaxX, interiorMaxY, interiorMaxZ;
	private final int lowestCellY;

	public StructureTemplate(Map<StructureDelta.Cell, BlockState> cells, Map<String, StructureDelta.Cell> markers,
			double interiorMinX, double interiorMinY, double interiorMinZ,
			double interiorMaxX, double interiorMaxY, double interiorMaxZ) {
		this.cells = Map.copyOf(cells);
		this.markers = Map.copyOf(markers);
		this.interiorMinX = interiorMinX;
		this.interiorMinY = interiorMinY;
		this.interiorMinZ = interiorMinZ;
		this.interiorMaxX = interiorMaxX;
		this.interiorMaxY = interiorMaxY;
		this.interiorMaxZ = interiorMaxZ;
		int lowest = Integer.MAX_VALUE;
		for (StructureDelta.Cell c : this.cells.keySet()) {
			if (c.y() < lowest) lowest = c.y();
		}
		this.lowestCellY = this.cells.isEmpty() ? 0 : lowest;
	}

	public Map<StructureDelta.Cell, BlockState> cells() {
		return cells;
	}

	public StructureDelta.Cell marker(String name) {
		return markers.get(name);
	}

	public Box interiorLocal() {
		return new Box(interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ);
	}

	/** Lowest occupied cell Y in the template, relative to the origin — for ground-contact checks during a crash. */
	public int lowestCellY() {
		return lowestCellY;
	}
}
