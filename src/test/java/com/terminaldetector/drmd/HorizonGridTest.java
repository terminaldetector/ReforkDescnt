package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.HorizonGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link HorizonGrid} directly — pure geometry, mirrors the {@code SkirtGeometryTest} idiom. */
class HorizonGridTest {
	private static final double MAX_RADIUS = 8_192.0;

	@Test
	@DisplayName("altitude never coarsens the grid — the regression this rework exists for")
	void altitudeNeverCoarsens() {
		// The old rule sized cells by slant distance, so climbing made every cell bigger. Whatever
		// else changes, a higher eye must never produce a coarser cell than a lower one.
		int atGround = HorizonGrid.cellSizeFor(2000.0, 0.0);
		int atSky = HorizonGrid.cellSizeFor(2000.0, 1000.0);
		int atOrbit = HorizonGrid.cellSizeFor(2000.0, 5000.0);

		assertTrue(atSky <= atGround, "sky cell " + atSky + " coarser than ground cell " + atGround);
		assertTrue(atOrbit <= atSky, "orbit cell " + atOrbit + " coarser than sky cell " + atSky);
	}

	@Test
	@DisplayName("the ring under a pilot at 2000 blocks is drawn far finer than the old slant rule gave")
	void nearRingAtAltitudeIsFine() {
		// Old behaviour, reproduced exactly: cell = sqrt(horizontal^2 + drop^2) / 7, which at a ring
		// radius of 160 and a drop of 2000 gave 287 — about 8 degrees of view per column.
		double oldCell = Math.sqrt(160.0 * 160.0 + 2000.0 * 2000.0) / 7.0;
		assertTrue(oldCell > 280 && oldCell < 295, "old rule sanity: " + oldCell);

		int now = HorizonGrid.cellSizeFor(160.0, 2000.0);
		assertTrue(now <= HorizonGrid.MIN_CELL + 1, "near ring at altitude should hit the floor, was " + now);
		assertTrue(now < oldCell / 10, "expected an order of magnitude finer, old " + oldCell + " new " + now);
	}

	@Test
	@DisplayName("refinement is 1 at ground, capped at REFINE_MAX, and monotonic between")
	void refinementIsBoundedAndMonotonic() {
		assertEquals(1.0, HorizonGrid.refineForDrop(0.0), 1e-9);
		assertEquals(HorizonGrid.REFINE_MAX, HorizonGrid.refineForDrop(HorizonGrid.REFINE_FULL_DROP), 1e-9);
		// Past the full-refine altitude it stays capped rather than growing without bound.
		assertEquals(HorizonGrid.REFINE_MAX, HorizonGrid.refineForDrop(100_000.0), 1e-9);
		// Negative drop (eye below the sampled ground, e.g. inside the mantle) must not invert it.
		assertEquals(HorizonGrid.REFINE_MAX, HorizonGrid.refineForDrop(-HorizonGrid.REFINE_FULL_DROP), 1e-9);

		double prev = 0;
		for (double d = 0; d <= HorizonGrid.REFINE_FULL_DROP; d += 60) {
			double r = HorizonGrid.refineForDrop(d);
			assertTrue(r >= prev, "refinement dipped at drop " + d);
			assertTrue(r >= 1.0 && r <= HorizonGrid.REFINE_MAX, "refinement out of range at drop " + d);
			prev = r;
		}
	}

	@Test
	@DisplayName("cells never go below MIN_CELL however close the ring or high the eye")
	void cellNeverBelowFloor() {
		for (double radius : new double[] {0, 1, 16, 100, 160, 500}) {
			for (double drop : new double[] {0, 500, 2000, 20_000}) {
				assertTrue(HorizonGrid.cellSizeFor(radius, drop) >= HorizonGrid.MIN_CELL,
						"below floor at radius " + radius + " drop " + drop);
			}
		}
	}

	@Test
	@DisplayName("rings march outward, never overlap, and stop at the far radius")
	void ringsAreOrderedAndBounded() {
		HorizonGrid.Plan plan = HorizonGrid.plan(0.0, MAX_RADIUS, 1500.0);
		assertTrue(plan.rings() > 0, "planned no rings at all");
		assertTrue(plan.rings() <= HorizonGrid.MAX_RINGS);

		for (int i = 0; i < plan.rings(); i++) {
			assertTrue(plan.edges()[i + 1] > plan.edges()[i],
					"ring " + i + " does not advance: " + plan.edges()[i] + " -> " + plan.edges()[i + 1]);
			assertTrue(plan.cells()[i] >= HorizonGrid.MIN_CELL, "ring " + i + " under the cell floor");
		}
		assertTrue(plan.outer() <= MAX_RADIUS + 1e-9, "field ran past the far radius");
	}

	@Test
	@DisplayName("outer rings are coarser than inner ones — the constant-angular-size property survives")
	void cellsGrowWithDistance() {
		HorizonGrid.Plan plan = HorizonGrid.plan(0.0, MAX_RADIUS, 0.0);
		for (int i = 1; i < plan.rings(); i++) {
			assertTrue(plan.cells()[i] >= plan.cells()[i - 1],
					"ring " + i + " (" + plan.cells()[i] + ") finer than ring " + (i - 1)
							+ " (" + plan.cells()[i - 1] + ")");
		}
	}

	@Test
	@DisplayName("the field starts exactly where real chunks stop, so the join has no step")
	void fieldStartsAtInnerRadius() {
		for (double inner : new double[] {0.0, 96.0, 192.0, 768.0}) {
			HorizonGrid.Plan plan = HorizonGrid.plan(inner, MAX_RADIUS, 400.0);
			assertEquals(inner, plan.edges()[0], 1e-9, "field did not start at the chunk edge");
		}
	}

	@Test
	@DisplayName("a plan at altitude costs more cells than at ground, but stays within a bounded factor")
	void altitudeCostIsBounded() {
		long ground = totalCells(HorizonGrid.plan(0.0, MAX_RADIUS, 0.0));
		long orbit = totalCells(HorizonGrid.plan(0.0, MAX_RADIUS, 20_000.0));

		assertTrue(orbit > ground, "altitude should buy detail, ground " + ground + " orbit " + orbit);
		// Refinement is capped, so the cell count it can add is capped too — this is what keeps the
		// quad budget in PlanetSurfaceMesh a fixed number rather than something altitude can blow past.
		assertTrue(orbit <= ground * 2, "altitude cost unbounded: ground " + ground + " orbit " + orbit);
	}

	/** Rough disc-area count, the same estimate the quad budget was sized from. */
	private static long totalCells(HorizonGrid.Plan plan) {
		long total = 0;
		for (int i = 0; i < plan.rings(); i++) {
			double a = plan.edges()[i];
			double b = plan.edges()[i + 1];
			double cell = plan.cells()[i];
			total += (long) (Math.PI * (b * b - a * a) / (cell * cell));
		}
		return total;
	}

	@Test
	@DisplayName("planning is deterministic")
	void isDeterministic() {
		HorizonGrid.Plan a = HorizonGrid.plan(192.0, MAX_RADIUS, 900.0);
		HorizonGrid.Plan b = HorizonGrid.plan(192.0, MAX_RADIUS, 900.0);
		assertEquals(a.rings(), b.rings());
		for (int i = 0; i < a.rings(); i++) {
			assertEquals(a.cells()[i], b.cells()[i]);
			assertEquals(a.edges()[i], b.edges()[i], 1e-12);
		}
	}
}
