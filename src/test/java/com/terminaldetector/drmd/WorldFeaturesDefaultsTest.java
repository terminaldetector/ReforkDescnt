package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.WorldFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No existing test pinned {@code WorldFeatures}' compile-time defaults in either direction. Cheap
 * insurance against a silent revert of the Orbit-density default flip: {@code ORBIT_JUNK} content
 * (see {@code OrbitJunkWorldgen}) only actually places anything when both this flag and its second
 * gate, {@code SURFACE_DISTRICTS}, are true — a regression in either one would compile fine and just
 * quietly stop generating content.
 */
class WorldFeaturesDefaultsTest {
	@Test
	@DisplayName("ORBIT_JUNK defaults on — its old reason for being parked (the painted Spark skybox) is gone")
	void orbitJunkDefaultsTrue() {
		assertTrue(WorldFeatures.ORBIT_JUNK);
	}

	@Test
	@DisplayName("SURFACE_DISTRICTS stays on — OrbitJunkWorldgen's own second gate on the same content")
	void surfaceDistrictsDefaultsTrue() {
		assertTrue(WorldFeatures.SURFACE_DISTRICTS);
	}
}
