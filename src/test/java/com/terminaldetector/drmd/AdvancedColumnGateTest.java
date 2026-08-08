package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@code WorldLevels.isAdvancedColumn}'s height comparison ({@code ServerWorld}/
 * {@code MinecraftServer} need a live server, unavailable here — same mirroring approach as
 * {@code TerrainClassifierTest}). The gate re-derives Advanced-vs-Vanilla from the loaded Overworld's
 * own bottom/height rather than a stored flag, so this pins the two concrete numbers that decide it:
 * this mod's tall column ({@code -784}, height {@code 2672}) is Advanced; vanilla's own real height
 * ({@code -64}, height {@code 384}) is not — and neither is an arbitrary third height, so a
 * differently-modded world doesn't accidentally read as either.
 */
class AdvancedColumnGateTest {
	private static final int WORLD_BOTTOM = -784;
	private static final int WORLD_TOP = 1888;

	/** Mirrors WorldLevels.isAdvancedColumn's comparison, given the Overworld's own bottom/height. */
	private static boolean isAdvancedColumn(int bottomY, int height) {
		return bottomY == WORLD_BOTTOM && height == WORLD_TOP - WORLD_BOTTOM;
	}

	@Test
	@DisplayName("this mod's own tall column (-784, height 2672) is Advanced")
	void tallColumnIsAdvanced() {
		assertTrue(isAdvancedColumn(-784, 2672));
	}

	@Test
	@DisplayName("vanilla's real Overworld height (-64, height 384) is not Advanced")
	void realVanillaHeightIsNotAdvanced() {
		assertFalse(isAdvancedColumn(-64, 384));
	}

	@Test
	@DisplayName("an arbitrary third height matches neither — not just a two-way flip")
	void arbitraryThirdHeightIsNotAdvanced() {
		assertFalse(isAdvancedColumn(0, 256));
	}

	@Test
	@DisplayName("matching bottomY alone, with a different height, is not Advanced")
	void bottomMatchAloneIsNotEnough() {
		assertFalse(isAdvancedColumn(-784, 384));
	}

	@Test
	@DisplayName("matching height alone, with a different bottomY, is not Advanced")
	void heightMatchAloneIsNotEnough() {
		assertFalse(isAdvancedColumn(-64, 2672));
	}
}
