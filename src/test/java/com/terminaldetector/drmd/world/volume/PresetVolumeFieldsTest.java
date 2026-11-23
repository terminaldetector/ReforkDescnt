package com.terminaldetector.drmd.world.volume;

import com.terminaldetector.drmd.world.psychedelic.InfinityPsychedelicField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.terminaldetector.drmd.world.volume.VolumeMath.SPAWN_Y;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetVolumeFieldsTest {
	@Test
	void allPresetsHaveAnIlluminatedDeckAndSafeHeadroom() {
		for (VolumeField field : List.of(PresetVolumeFields.continuum(42), PresetVolumeFields.verticalMegacity(42),
				PresetVolumeFields.abyssComplex(42), PresetVolumeFields.orbitalRuins(42), new InfinityPsychedelicField(42))) {
			for (int x = -16; x <= 16; x += 4) {
				for (int z = -16; z <= 16; z += 4) {
					assertTrue(field.density(x, SPAWN_Y - 1, z) > 0, field.getClass().getSimpleName());
					assertTrue(field.density(x, SPAWN_Y, z) < 0, field.getClass().getSimpleName());
					assertTrue(field.density(x, SPAWN_Y + 4, z) < 0, field.getClass().getSimpleName());
				}
			}
			assertEquals(VolumeField.Material.LIGHT, field.material(17, SPAWN_Y - 1, 0));
		}
	}

	@Test
	void megacityHasGroundConnectedSupportsAboveTheLegacyCeiling() {
		VolumeField city = PresetVolumeFields.verticalMegacity(43689);
		boolean linkedSupport = false;
		for (int x = 40; x <= 69; x++) {
			for (int z = 40; z <= 69; z++) {
				if (city.density(x, -215, z) > 0 && city.density(x, -212, z) > 0
						&& city.density(x, 110, z) > 0 && city.density(x, 2188, z) > 0) {
					linkedSupport = true;
				}
			}
		}
		assertTrue(linkedSupport);
		assertTrue(city.density(0, 2188, 0) < 0, "Vertical shaft through the occupied tower");
		assertTrue(city.density(103, 2188, 103) < 0, "Flight space between districts");
	}

	@Test
	void abyssShaftJoinsSeveralSeparatedGalleryFloors() {
		VolumeField abyss = PresetVolumeFields.abyssComplex(145);
		for (int level = 1; level <= 16; level++) {
			int gallery = SPAWN_Y + 9 - level * 96;
			assertTrue(abyss.density(0, gallery, 0) < 0);
			assertTrue(abyss.density(90, gallery, 0) < 0);
			assertTrue(abyss.density(90, gallery - 21, 0) > 0);
		}
	}
}
