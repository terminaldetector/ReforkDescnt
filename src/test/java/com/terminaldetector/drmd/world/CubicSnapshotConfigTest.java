package com.terminaldetector.drmd.world;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubicSnapshotConfigTest {
	@Test
	void missingConfigRetainsLegacyJvmFallback() {
		assertTrue(DrmdServerConfig.parseCubicSnapshots(new Properties(), true));
		assertFalse(DrmdServerConfig.parseCubicSnapshots(new Properties(), false));
	}

	@Test
	void savedSettingOverridesLegacyJvmFallback() {
		Properties props = new Properties();
		props.setProperty("cubicSnapshots", "false");
		assertFalse(DrmdServerConfig.parseCubicSnapshots(props, true));

		props.setProperty("cubicSnapshots", "true");
		assertTrue(DrmdServerConfig.parseCubicSnapshots(props, false));
	}
}
