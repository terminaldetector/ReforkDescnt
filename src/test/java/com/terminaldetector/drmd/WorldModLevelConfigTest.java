package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code DrmdServerConfig} added a two-way {@code WorldModLevel} (ADVANCED / VANILLA) alongside
 * {@code WorldKind}. Pins two things: {@code parseModLevel}'s default/case-insensitive/malformed-value
 * resolution (mirroring {@code DrmdServerConfigKindTest}'s style for {@code WorldKind}), and that
 * resolving VANILLA forces every {@code WorldFeatures} output false regardless of what was
 * individually saved for each one — the whole point of the mode, and the one behaviour a config-load
 * regression could silently break without any compile error.
 *
 * <p>{@code DrmdServerConfig} needs a real {@code Properties} plus {@code DescentMod.LOGGER}, so both
 * the parse rule and the force-off rule are mirrored here as plain functions rather than calling the
 * real methods — same reasoning as this suite's other config/formula mirrors.
 */
class WorldModLevelConfigTest {
	private enum WorldModLevel { ADVANCED, VANILLA }

	private record Features(boolean netherBand, boolean endBand, boolean klondikeIslands,
							 boolean orbitJunk, boolean macroWorldgen, boolean surfaceDistricts) {
		static Features allTrue() {
			return new Features(true, true, true, true, true, true);
		}
	}

	/** Mirrors DrmdServerConfig.parseModLevel. */
	private static WorldModLevel resolveModLevel(String raw, WorldModLevel fallback) {
		if (raw == null) return fallback;
		try {
			return WorldModLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	/** Mirrors DrmdServerConfig.forceFeaturesFor. */
	private static Features forceFeaturesFor(WorldModLevel level, Features saved) {
		if (level != WorldModLevel.VANILLA) return saved;
		return new Features(false, false, false, false, false, false);
	}

	@Test
	@DisplayName("no worldModLevel line at all defaults to ADVANCED")
	void missingLineDefaultsToAdvanced() {
		assertEquals(WorldModLevel.ADVANCED, resolveModLevel(null, WorldModLevel.ADVANCED));
	}

	@Test
	@DisplayName("worldModLevel parsing is case-insensitive, matching what the new screen writes")
	void parsingIsCaseInsensitive() {
		assertEquals(WorldModLevel.VANILLA, resolveModLevel("vanilla", WorldModLevel.ADVANCED));
		assertEquals(WorldModLevel.ADVANCED, resolveModLevel("Advanced", WorldModLevel.VANILLA));
	}

	@Test
	@DisplayName("a malformed worldModLevel value falls back to ADVANCED instead of crashing config load")
	void malformedValueFallsBackSafely() {
		assertEquals(WorldModLevel.ADVANCED, resolveModLevel("not_a_real_level", WorldModLevel.ADVANCED));
		assertEquals(WorldModLevel.ADVANCED, resolveModLevel("", WorldModLevel.ADVANCED));
	}

	@Test
	@DisplayName("ADVANCED leaves every individually-saved WorldFeatures flag untouched")
	void advancedLeavesFeaturesAlone() {
		Features saved = Features.allTrue();
		assertEquals(saved, forceFeaturesFor(WorldModLevel.ADVANCED, saved));
	}

	@Test
	@DisplayName("VANILLA forces every WorldFeatures flag false, even when every one was individually saved true")
	void vanillaForcesEveryFeatureOffRegardlessOfSavedValue() {
		Features forced = forceFeaturesFor(WorldModLevel.VANILLA, Features.allTrue());
		assertFalse(forced.netherBand());
		assertFalse(forced.endBand());
		assertFalse(forced.klondikeIslands());
		assertFalse(forced.orbitJunk());
		assertFalse(forced.macroWorldgen());
		assertFalse(forced.surfaceDistricts());
	}
}
