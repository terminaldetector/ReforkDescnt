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
	private final Box interiorLocal;
	private final int lowestCellY;

	public StructureTemplate(Map<StructureDelta.Cell, BlockState> cells, Map<String, StructureDelta.Cell> markers,
			Box interiorLocal) {
		this.cells = Map.copyOf(cells);
		this.markers = Map.copyOf(markers);
		this.interiorLocal = interiorLocal;
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
		return interiorLocal;
	}

	/** Lowest occupied cell Y in the template, relative to the origin — for ground-contact checks during a crash. */
	public int lowestCellY() {
		return lowestCellY;
	}
}
