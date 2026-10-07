package com.terminaldetector.drmd.world;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrmdServerConfigFeatureSettingsTest {
	private static final DrmdServerConfig.FeatureSettings ALL_ON = new DrmdServerConfig.FeatureSettings(
			true, true, true, true, true, true);

	@Test
	void parsingPreservesExistingValuesForMissingProperties() {
		Properties props = new Properties();
		props.setProperty("netherBand", "false");

		DrmdServerConfig.FeatureSettings parsed = DrmdServerConfig.parseFeatureSettings(props, ALL_ON);
		assertFalse(parsed.netherBand());
		assertTrue(parsed.endBand());
		assertTrue(parsed.klondikeIslands());
		assertTrue(parsed.orbitJunk());
		assertTrue(parsed.macroWorldgen());
		assertTrue(parsed.surfaceDistricts());
	}

	@Test
	void vanillaSuppressesRuntimeFeaturesWithoutErasingSavedChoices() {
		DrmdServerConfig.FeatureSettings effective = DrmdServerConfig.effectiveFeaturesFor(ALL_ON, false);
		assertEquals(DrmdServerConfig.FeatureSettings.disabled(), effective);

		// The configured record remains the source of truth, so Advanced restores all saved choices.
		assertEquals(ALL_ON, DrmdServerConfig.effectiveFeaturesFor(ALL_ON, true));
	}
}
