package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.level.NetherRelief;
import com.terminaldetector.drmd.world.level.WorldLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ties the background stream's per-tick write budget to the actual size of what it has to build.
 *
 * <p>{@code LevelBuilder.BUDGET_PER_TICK} does not derive from {@code WorldLevels}/{@code NetherRelief}
 * — it cannot, it is block writes per tick, not a Y coordinate — so nothing stops it from silently
 * falling behind when the bands it fills get taller. That is exactly what happened: the height-budget
 * rescale grew the mantle span, the floor relief and the ceiling drop by a combined ×1.5 in total row
 * count, while the budget stayed at its pre-rescale value. Chunks still finished, only slower — 1.5×
 * slower — and what a pilot flying at the old speed then flew into was a chunk still mid-build, which
 * reads as terrain that failed to load rather than terrain that is still loading. This is the report
 * that traced back to it.
 *
 * <p>{@code BUDGET_PER_TICK} is private (a tuning knob, not an API), so its value is mirrored below —
 * change one, change the other and this test.
 */
class GenerationBudgetTest {
	/** Mirrors {@code LevelBuilder.BUDGET_PER_TICK}. */
	private static final int BUDGET_PER_TICK = 4_200;
	/** What {@code BUDGET_PER_TICK} was tuned against before the height-budget rescale. */
	private static final int PRE_RESCALE_BUDGET = 2_800;
	private static final int PRE_RESCALE_MANTLE_ROWS = 176;   // old ABYSS_TOP(-64) - NETHER_CEILING(-240)
	private static final int PRE_RESCALE_FLOOR_RELIEF = 22;
	private static final int PRE_RESCALE_CEILING_DROP = 16;

	private static int mantleRowsNow() {
		return WorldLevels.ABYSS_TOP - WorldLevels.NETHER_CEILING;
	}

	@Test
	@DisplayName("the per-tick budget grew at least as much as the row count it has to write")
	void budgetKeepsPaceWithBandHeight() {
		int oldRows = PRE_RESCALE_MANTLE_ROWS + PRE_RESCALE_FLOOR_RELIEF + PRE_RESCALE_CEILING_DROP;
		int newRows = mantleRowsNow() + NetherRelief.FLOOR_RELIEF + NetherRelief.CEILING_DROP;
		double rowRatio = newRows / (double) oldRows;
		double budgetRatio = BUDGET_PER_TICK / (double) PRE_RESCALE_BUDGET;
		assertTrue(budgetRatio >= rowRatio - 1e-9,
				"rows per chunk grew ×" + rowRatio + " but the budget only grew ×" + budgetRatio
						+ " — chunks now take longer to finish than they used to, which is the bug this pins");
	}

	@Test
	@DisplayName("mantle rows dominate the per-chunk cost, so the budget must track NETHER_CEILING specifically")
	void mantleIsTheDominantCost() {
		int rows = mantleRowsNow();
		int reliefRows = NetherRelief.FLOOR_RELIEF + NetherRelief.CEILING_DROP;
		assertTrue(rows > reliefRows,
				"mantle rows (" + rows + ") should still dwarf the relief rows (" + reliefRows
						+ ") — if that ever flips, the budget reasoning above needs revisiting, not just its ratio");
	}
}
