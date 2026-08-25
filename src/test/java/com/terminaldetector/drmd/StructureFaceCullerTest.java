package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.structure.StructureDelta.Cell;
import com.terminaldetector.drmd.world.structure.StructureFaceCuller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calls {@link StructureFaceCuller} directly — zero Minecraft dependency, same idiom as {@code StructureDeltaTest}. */
class StructureFaceCullerTest {
	@Test
	@DisplayName("an isolated cell with no solid neighbours shows all 6 faces")
	void isolatedCellShowsAllSixFaces() {
		Cell cell = new Cell(5, 5, 5);
		Set<Cell> solid = Set.of(cell);
		assertEquals(6, StructureFaceCuller.visibleFaceOffsets(solid, cell).size());
	}

	@Test
	@DisplayName("an empty solid set still yields all 6 faces for the query cell")
	void emptySolidSetShowsAllSixFaces() {
		Cell cell = new Cell(0, 0, 0);
		assertEquals(6, StructureFaceCuller.visibleFaceOffsets(Set.of(), cell).size());
	}

	@Test
	@DisplayName("two adjacent solid cells cull exactly the one shared face pair each: 10 faces total")
	void twoAdjacentCellsCullTheSharedPair() {
		Cell a = new Cell(0, 0, 0);
		Cell b = new Cell(1, 0, 0);
		Set<Cell> solid = Set.of(a, b);
		int total = StructureFaceCuller.visibleFaceOffsets(solid, a).size()
				+ StructureFaceCuller.visibleFaceOffsets(solid, b).size();
		assertEquals(10, total, "6 + 6 minus the two faces the cells share with each other");
	}

	@Test
	@DisplayName("in a solid 3x3x3 cube, only the centre cell is fully enclosed (0 faces); every other cell shows at least one")
	void solidCubeOnlyEnclosesTheCentre() {
		Set<Cell> cube = new HashSet<>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					cube.add(new Cell(x, y, z));
				}
			}
		}
		assertEquals(27, cube.size());

		for (Cell cell : cube) {
			int faces = StructureFaceCuller.visibleFaceOffsets(cube, cell).size();
			if (cell.x() == 0 && cell.y() == 0 && cell.z() == 0) {
				assertEquals(0, faces, "the centre cell's 6 neighbours are all inside the cube");
			} else {
				assertTrue(faces >= 1, "boundary cell " + cell + " must border at least one cell outside the cube");
			}
		}
	}
}
