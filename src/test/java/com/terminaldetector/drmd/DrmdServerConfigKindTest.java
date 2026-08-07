package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code DrmdServerConfig} added a three-way {@code WorldKind} (STOCK / PSYCHEDELIC /
 * INFINITE_MEGACITY) on top of the one boolean it used to have, {@code psychedelicWorlds}. An
 * existing {@code config/drmd-server.properties} from before this change has that boolean and no
 * {@code worldKind} line at all — this pins that such a file still resolves to exactly what it always
 * meant, and that a hand-typo'd {@code worldKind} value degrades to the same legacy-derived default
 * rather than crashing config load.
 *
 * <p>{@code DrmdServerConfig.applyFrom} needs a real {@code Properties} plus
 * {@code DescentMod.LOGGER}, so the resolution rule is mirrored here as a plain function rather than
 * calling the real method — same reasoning as this suite's other config/formula mirrors.
 */
class DrmdServerConfigKindTest {
	private enum WorldKind { STOCK, PSYCHEDELIC, INFINITE_MEGACITY }

	/** Mirrors the worldKind-resolution half of DrmdServerConfig.applyFrom. */
	private static WorldKind resolveKind(String worldKindProp, boolean legacyPsychedelicWorlds) {
		WorldKind legacyDefault = legacyPsychedelicWorlds ? WorldKind.PSYCHEDELIC : WorldKind.STOCK;
		if (worldKindProp == null) return legacyDefault;
		try {
			return WorldKind.valueOf(worldKindProp.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return legacyDefault;
		}
	}

	@Test
	@DisplayName("a pre-upgrade file with psychedelicWorlds=true and no worldKind line still means psychedelic")
	void legacyPsychedelicTrueWithNoKindLine() {
		assertEquals(WorldKind.PSYCHEDELIC, resolveKind(null, true));
	}

	@Test
	@DisplayName("a pre-upgrade file with psychedelicWorlds=false (or absent) and no worldKind line means stock")
	void legacyPsychedelicFalseWithNoKindLine() {
		assertEquals(WorldKind.STOCK, resolveKind(null, false));
	}

	@Test
	@DisplayName("an explicit worldKind line wins outright, including the new mode the legacy boolean can't express")
	void explicitKindLineWins() {
		assertEquals(WorldKind.INFINITE_MEGACITY, resolveKind("INFINITE_MEGACITY", false));
		assertEquals(WorldKind.STOCK, resolveKind("STOCK", true));
	}

	@Test
	@DisplayName("worldKind parsing is case-insensitive, matching what the new screen writes")
	void kindParsingIsCaseInsensitive() {
		assertEquals(WorldKind.PSYCHEDELIC, resolveKind("psychedelic", false));
		assertEquals(WorldKind.INFINITE_MEGACITY, resolveKind("Infinite_Megacity", false));
	}

	@Test
	@DisplayName("a malformed worldKind value falls back to the legacy default instead of crashing config load")
	void malformedKindFallsBackSafely() {
		assertEquals(WorldKind.PSYCHEDELIC, resolveKind("not_a_real_kind", true));
		assertEquals(WorldKind.STOCK, resolveKind("", false));
	}
}
