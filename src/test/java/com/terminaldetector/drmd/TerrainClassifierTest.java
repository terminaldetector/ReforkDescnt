package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors {@code TerrainClassifier.classify}'s box-membership scan ({@code MacroEntry}/{@code MacroWorld}
 * need a live {@code ServerWorld}, unavailable here — same mirroring approach as
 * {@code TunnelCarvingCapsuleTest}). A position inside any known structure's bounds is CUBIC;
 * everything else — the default for every natural mantle/cave/corridor context — is SMOOTH.
 */
class TerrainClassifierTest {
	private enum Zone { CUBIC, SMOOTH }

	private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		boolean contains(double x, double y, double z) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}
	}

	/** Mirrors TerrainClassifier.classify — keep in sync if that formula changes. */
	private static Zone classify(double x, double y, double z, List<Box> structures) {
		for (Box b : structures) {
			if (b.contains(x, y, z)) return Zone.CUBIC;
		}
		return Zone.SMOOTH;
	}

	@Test
	@DisplayName("a point inside a structure's bounds classifies as CUBIC, including right on the boundary")
	void insideStructureIsCubic() {
		Box tower = new Box(-10, 0, -10, 10, 50, 10);
		assertEquals(Zone.CUBIC, classify(0, 20, 0, List.of(tower)));
		assertEquals(Zone.CUBIC, classify(10, 0, -10, List.of(tower)));
	}

	@Test
	@DisplayName("a point far from every known structure classifies as SMOOTH")
	void farFromEveryStructureIsSmooth() {
		Box tower = new Box(-10, 0, -10, 10, 50, 10);
		assertEquals(Zone.SMOOTH, classify(500, 20, 500, List.of(tower)));
	}

	@Test
	@DisplayName("with no structures registered at all, everywhere is SMOOTH")
	void emptyRegistryIsAlwaysSmooth() {
		assertEquals(Zone.SMOOTH, classify(0, 0, 0, List.of()));
	}

	@Test
	@DisplayName("checks every candidate structure, not just the first one in the list")
	void checksEveryStructureNotJustTheFirst() {
		Box far = new Box(1000, 0, 1000, 1010, 10, 1010);
		Box near = new Box(-5, 0, -5, 5, 10, 5);
		assertEquals(Zone.CUBIC, classify(0, 5, 0, List.of(far, near)));
	}
}
