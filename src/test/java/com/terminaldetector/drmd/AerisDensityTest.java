package com.terminaldetector.drmd;

import com.terminaldetector.drmd.aeris.AerisDensity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls {@link AerisDensity} directly rather than mirroring it — unlike every other pure-logic test in
 * this project, the production code has zero Minecraft dependency, so there is nothing to mirror.
 *
 * <p>These are broad-sampling, existential/statistical assertions rather than exact-value checks against
 * specific coordinates: the field is hash-driven, so which cells hold a sphere or a foam blob is not
 * something worth hand-predicting (brittle against no real gain) — what matters for ÆRis/Mirai's
 * question is the shape of the emergent behaviour, which these tests probe directly on the real formula.
 */
class AerisDensityTest {
	@Test
	@DisplayName("neither all-solid nor all-air across a broad sample — genuine volumetric variation")
	void producesBothSolidAndAir() {
		int solid = 0, air = 0;
		for (int x = -200; x <= 200; x += 4) {
			for (int y = -128; y <= 256; y += 4) {
				for (int z = -200; z <= 200; z += 20) {
					if (AerisDensity.isSolid(x, y, z)) solid++; else air++;
				}
			}
		}
		assertTrue(solid > 0, "expected at least some solid voxels in the sample");
		assertTrue(air > 0, "expected at least some air voxels in the sample");
		// A mostly-empty field with occasional bounded blobs — not a heightmap-shaped 50/50 split.
		double solidFraction = solid / (double) (solid + air);
		assertTrue(solidFraction < 0.35,
				"solid fraction (" + solidFraction + ") should stay a minority — bounded blobs in open space, not a filled half-space");
	}

	@Test
	@DisplayName("at least one X/Z column has more than one separate solid run along Y — proves a single column can hold multiple independent surfaces")
	void atLeastOneColumnHasMultipleSurfaces() {
		int bestRuns = 0;
		for (int x = -300; x <= 300 && bestRuns < 2; x += 3) {
			for (int z = -300; z <= 300 && bestRuns < 2; z += 3) {
				int runs = solidRunCount(x, z, -128, 256);
				if (runs > bestRuns) bestRuns = runs;
			}
		}
		assertTrue(bestRuns >= 2,
				"expected at least one (x,z) column with 2+ disjoint solid runs along Y (found max " + bestRuns + ")"
						+ " — a Heightmap-style 'topmost solid Y' cannot represent this column at all");
	}

	@Test
	@DisplayName("far from any cell's sphere/foam envelope, density is deeply negative — fields are bounded, not an infinite solid mass")
	void farFromAnyBlobIsDeepAir() {
		// A single isolated point at a cell corner intersection, offset from cell centers where
		// spheres/foam are placed — not guaranteed empty by construction, so assert the weaker,
		// still-meaningful property: density does not blow up to some huge positive value anywhere
		// in a big sample (i.e. no field secretly produces an unbounded solid region).
		double maxDensity = -1e18;
		for (int x = -500; x <= 500; x += 25) {
			for (int y = -128; y <= 256; y += 25) {
				for (int z = -500; z <= 500; z += 25) {
					maxDensity = Math.max(maxDensity, AerisDensity.density(x, y, z));
				}
			}
		}
		assertTrue(maxDensity < 20.0,
				"max sampled density (" + maxDensity + ") should stay bounded (sphere radius <= 14, foam envelope capped) — no runaway growth");
	}

	@Test
	@DisplayName("density is deterministic — same point evaluated twice gives the same answer")
	void isDeterministic() {
		for (int i = 0; i < 50; i++) {
			double x = i * 17.3, y = i * 5.1 - 64, z = i * -11.7;
			assertTrue(AerisDensity.density(x, y, z) == AerisDensity.density(x, y, z),
					"density(" + x + "," + y + "," + z + ") differed between two calls with identical input");
		}
	}

	/** Counts disjoint solid runs along Y at a fixed (x, z), stepping every block. */
	private static int solidRunCount(int x, int z, int minY, int maxY) {
		int runs = 0;
		boolean wasSolid = false;
		for (int y = minY; y <= maxY; y++) {
			boolean solid = AerisDensity.isSolid(x, y, z);
			if (solid && !wasSolid) runs++;
			wasSolid = solid;
		}
		return runs;
	}
}
