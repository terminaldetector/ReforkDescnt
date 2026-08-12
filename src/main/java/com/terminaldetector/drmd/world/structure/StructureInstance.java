package com.terminaldetector.drmd.world.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * A live placed instance of a {@link StructureTemplate} at a mutable world-space anchor.
 * {@link #occupiedCells()} is always derived fresh from template + anchor — there is no separate
 * mutable "currently placed" collection that can drift out of sync with reality, unlike
 * {@code SkyUfoHull}'s old {@code hullBlocks} field (which needed a defensive copy-then-clear dance to
 * dodge reentrancy during reactor destruction).
 *
 * <p>{@link #setAnchor} is package-private: only {@link StructureMover} may move an instance, since a
 * move has to diff the old and new footprints before committing to the new anchor.
 */
public final class StructureInstance {
	private final StructureTemplate template;
	private BlockPos anchor;

	public StructureInstance(StructureTemplate template, BlockPos anchor) {
		this.template = template;
		this.anchor = anchor;
	}

	public StructureTemplate template() {
		return template;
	}

	public BlockPos anchor() {
		return anchor;
	}

	void setAnchor(BlockPos anchor) {
		this.anchor = anchor;
	}

	/** World-space cell -> block state for every cell in the template, translated by the current anchor. */
	public Map<StructureDelta.Cell, BlockState> occupiedCells() {
		Map<StructureDelta.Cell, BlockState> out = new HashMap<>(template.cells().size() * 2);
		for (Map.Entry<StructureDelta.Cell, BlockState> e : template.cells().entrySet()) {
			StructureDelta.Cell local = e.getKey();
			out.put(new StructureDelta.Cell(local.x() + anchor.getX(), local.y() + anchor.getY(), local.z() + anchor.getZ()),
					e.getValue());
		}
		return out;
	}

	public BlockPos markerPos(String name) {
		StructureDelta.Cell local = template.marker(name);
		return local == null ? null : anchor.add(local.x(), local.y(), local.z());
	}

	/** The template's local interior box, translated into world space by the current anchor. */
	public Box interior() {
		Box l = template.interiorLocal();
		return new Box(
				anchor.getX() + l.minX(), anchor.getY() + l.minY(), anchor.getZ() + l.minZ(),
				anchor.getX() + l.maxX(), anchor.getY() + l.maxY(), anchor.getZ() + l.maxZ());
	}

	public int lowestWorldY() {
		return anchor.getY() + template.lowestCellY();
	}
}
