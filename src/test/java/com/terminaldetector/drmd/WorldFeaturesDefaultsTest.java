package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.WorldFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Independent defaults for the optional techno-ring satellites and surface districts. */
class WorldFeaturesDefaultsTest {
	@Test
	@DisplayName("Techno-ring satellites retain their configured default")
	void orbitJunkDefaultsTrue() {
		assertTrue(WorldFeatures.ORBIT_JUNK);
	}

	@Test
	@DisplayName("Surface districts retain their configured default")
	void surfaceDistrictsDefaultsTrue() {
		assertTrue(WorldFeatures.SURFACE_DISTRICTS);
	}
}
