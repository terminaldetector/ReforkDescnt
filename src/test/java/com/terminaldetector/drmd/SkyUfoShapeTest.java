package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.mega.SkyUfoShape;
import com.terminaldetector.drmd.world.mega.SkyUfoShape.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SkyUfoShape#classify} directly — pure geometry, mirrors the
 * {@code AerisDensity}/{@code AerisDensityTest} idiom. Sample ranges below reuse
 * {@link SkyUfoShape#MAJOR}/{@link SkyUfoShape#MINOR} (the same bounds {@code SkyUfoHull.build}'s own
 * sweep uses) so they always track the real hull size.
 */
class SkyUfoShapeTest {
	@Test
	@DisplayName("classify is symmetric under simultaneous x -> -x, z -> -z (180-degree yaw) — the hull is not lopsided")
	void symmetricUnder180DegreeYaw() {
		// Not symmetric under a single axis flip alone: the glass parity check (x+z)%7==0 only survives a
		// *simultaneous* negation of both x and z (since -(x+z) is divisible by 7 exactly when (x+z) is),
		// so that combined transform is the invariant asserted here, not two independent per-axis flips.
		for (int x = -SkyUfoShape.MAJOR; x <= SkyUfoShape.MAJOR; x++) {
			for (int y = -SkyUfoShape.MINOR; y <= SkyUfoShape.MINOR + 1; y++) {
				for (int z = -SkyUfoShape.MAJOR; z <= SkyUfoShape.MAJOR; z++) {
					assertEquals(SkyUfoShape.classify(x, y, z), SkyUfoShape.classify(-x, y, -z),
							"classify(" + x + "," + y + "," + z + ") should equal classify("
									+ (-x) + "," + y + "," + (-z) + ")");
				}
			}
		}
	}

	@Test
	@DisplayName("across the hull's own bounding box, every cell kind actually occurs — a genuinely hollow shell, not a solid mass or an empty box")
	void producesEveryCellKindAcrossTheBoundingBox() {
		Map<Cell, Integer> counts = new EnumMap<>(Cell.class);
		for (Cell c : Cell.values()) counts.put(c, 0);
		for (int x = -SkyUfoShape.MAJOR; x <= SkyUfoShape.MAJOR; x++) {
			for (int y = -SkyUfoShape.MINOR; y <= SkyUfoShape.MINOR + 1; y++) {
				for (int z = -SkyUfoShape.MAJOR; z <= SkyUfoShape.MAJOR; z++) {
					Cell c = SkyUfoShape.classify(x, y, z);
					counts.put(c, counts.get(c) + 1);
				}
			}
		}
		assertTrue(counts.get(Cell.NONE) > 0, "expected NONE outside the shell band");
		assertTrue(counts.get(Cell.OUTER_HULL) > 0, "expected some outer-hull cells");
		assertTrue(counts.get(Cell.INNER_SHELL) > 0, "expected some inner-shell cells");
		assertTrue(counts.get(Cell.INTERIOR_AIR) > 0, "expected a hollow interior cavity");
		assertTrue(counts.get(Cell.DECK) > 0, "expected deck-plate cells");
		assertTrue(counts.get(Cell.GLASS) > 0,
				"expected the glass seam to actually fire now that it's checked before the plain-outer case");
	}

	@Test
	@DisplayName("the underside bay carve actually removes material the ellipsoid shell would otherwise place")
	void bayDoorCarveOpensAHoleInTheShell() {
		// (x=2, y=-3, z=2) sits inside the bay predicate's box (x,z in [-2,2], y<-1) *and*, absent the bay
		// carve, its e value (nx^2 + ny^2*1.7 + nz^2 ~= 0.033+0.658+0.033 = 0.724) lands inside the
		// (0.52, 0.78] shell band that would otherwise resolve to INNER_SHELL — so this specifically
		// witnesses the carve removing material, not just landing somewhere that was already empty.
		assertEquals(Cell.NONE, SkyUfoShape.classify(2, -3, 2),
				"expected the bay door to carve out a cell the shell band would otherwise fill");
	}

	@Test
	@DisplayName("the reactor pedestal column (center, +1 'core' marker, +2) is clear of hull material, so captureTemplate's unconditional overwrite there never fights the shell")
	void reactorPedestalColumnIsClearOfHullMaterial() {
		for (int y = 0; y <= 2; y++) {
			assertEquals(Cell.NONE, SkyUfoShape.classify(0, y, 0),
					"classify(0," + y + ",0) should be NONE (pedestal / core marker / lantern column)");
		}
	}

	@Test
	@DisplayName("classify is deterministic")
	void isDeterministic() {
		for (int x = -SkyUfoShape.MAJOR; x <= SkyUfoShape.MAJOR; x += 3) {
			for (int y = -SkyUfoShape.MINOR; y <= SkyUfoShape.MINOR + 1; y++) {
				for (int z = -SkyUfoShape.MAJOR; z <= SkyUfoShape.MAJOR; z += 3) {
					assertEquals(SkyUfoShape.classify(x, y, z), SkyUfoShape.classify(x, y, z));
				}
			}
		}
	}
}
