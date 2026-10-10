package com.terminaldetector.drmd.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrmdServerConfigCompatibilityTest {
	@Test
	void worldKindParserKeepsLegacyFallbackAndAcceptsNewKinds() {
		assertEquals(DrmdServerConfig.WorldKind.PSYCHEDELIC,
				DrmdServerConfig.parseKind("invalid", DrmdServerConfig.WorldKind.PSYCHEDELIC));
		assertEquals(DrmdServerConfig.WorldKind.INFINITE_MEGACITY,
				DrmdServerConfig.parseKind("infinite_megacity", DrmdServerConfig.WorldKind.STOCK));
	}

	@Test
	void worldHeightParserDefaultsAndHandlesCaseWithoutThrowingOnStaleValues() {
		assertEquals(DrmdServerConfig.WorldModLevel.ADVANCED,
				DrmdServerConfig.parseModLevel(null, DrmdServerConfig.WorldModLevel.ADVANCED));
		assertEquals(DrmdServerConfig.WorldModLevel.VANILLA,
				DrmdServerConfig.parseModLevel("vanilla", DrmdServerConfig.WorldModLevel.ADVANCED));
		assertEquals(DrmdServerConfig.WorldModLevel.ADVANCED,
				DrmdServerConfig.parseModLevel("invalid", DrmdServerConfig.WorldModLevel.ADVANCED));
	}
}
