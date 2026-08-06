package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A crashed-UFO landmark rebuilt itself — full saucer, trap lattice, 24-entity garrison — every time
 * its chunk reloaded, on any terrain outside a narrow height band. Both {@code ModWorldgen2.onChunkLoad}
 * and {@code CrashedUfoGenerator.generate} sample the identical {@code getTopY} at the identical X/Z,
 * but clamped it two different ways: the chunk-load side (which checks for the LODESTONE "already
 * built" marker and computes the landmark job's {@code origin}) used {@code [55,110]}; the generator
 * (which actually places that marker, via {@code resolveCrashSite}) used "floor sub-50 to a flat 70,
 * cap the top at 120" — the same only where a raw sample already happened to land inside {@code
 * [55,110]}. Outside that overlap the marker landed away from where every future chunk-load pre-check
 * would ever look for it, so the build never registered as done.
 *
 * <p>Both clamps are pure {@code int -> int} functions of the sampled top height, so they are mirrored
 * here directly rather than driving a fake {@code WorldAccess} through either real method.
 */
class CrashedUfoMarkerTest {
	/** Mirrors {@code ModWorldgen2.skyY}'s {@code CRASHED_UFO} case — unchanged by this fix. */
	private static int chunkLoadClamp(int top) {
		return Math.max(55, Math.min(110, top));
	}

	/** Mirrors the pre-fix {@code CrashedUfoGenerator.resolveCrashSite}. */
	private static int preFixGeneratorClamp(int top) {
		int y = top;
		if (y < 50) y = 70;
		return Math.min(y, 120);
	}

	/** Mirrors the fixed {@code CrashedUfoGenerator.resolveCrashSite}. */
	private static int fixedGeneratorClamp(int top) {
		return Math.max(55, Math.min(110, top));
	}

	@Test
	@DisplayName("pre-fix: low, mid-low, and high terrain all disagree with the chunk-load pre-check")
	void preFixDisagreesOutsideTheOverlapBand() {
		// Flat/low terrain (e.g. ocean floor): chunk-load clamps up to 55, the generator floored to 70.
		assertNotEquals(chunkLoadClamp(20), preFixGeneratorClamp(20));
		// Just under the chunk-load floor: chunk-load clamps to 55, pre-fix generator leaves it at 52.
		assertNotEquals(chunkLoadClamp(52), preFixGeneratorClamp(52));
		// Tall terrain (e.g. a mountain): chunk-load clamps down to 110, the generator allowed up to 120.
		assertNotEquals(chunkLoadClamp(150), preFixGeneratorClamp(150));
	}

	@Test
	@DisplayName("pre-fix: the two clamps only ever agreed inside the [55,110] overlap they shared")
	void preFixOnlyAgreesInsideTheSharedRange() {
		for (int top = 55; top <= 110; top++) {
			assertEquals(chunkLoadClamp(top), preFixGeneratorClamp(top),
					"top=" + top + " is inside both ranges and should already have agreed pre-fix");
		}
	}

	@Test
	@DisplayName("fixed: the generator's clamp now agrees with the chunk-load pre-check for any terrain height")
	void fixedAgreesForEveryTerrainHeight() {
		for (int top = -64; top <= 320; top += 3) {
			assertEquals(chunkLoadClamp(top), fixedGeneratorClamp(top),
					"top=" + top + " — a marker placed here must land exactly where the next chunk-load"
							+ " pre-check will look, or the site rebuilds itself on every reload");
		}
	}
}
