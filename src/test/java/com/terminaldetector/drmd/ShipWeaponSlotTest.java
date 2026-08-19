package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ShipWeaponSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ShipWeaponSlotTest {
	@Test
	@DisplayName("there are exactly four hardpoints, no center mount")
	void fourHardpointsNoCenter() {
		assertEquals(4, ShipWeaponSlot.values().length);
		for (ShipWeaponSlot slot : ShipWeaponSlot.values()) {
			assertFalse(slot.name().equals("CENTER"), "Pyro GX has no center hardpoint");
		}
	}

	@Test
	@DisplayName("slot names match the established WeaponClusters vocabulary")
	void namesMatchEstablishedVocabulary() {
		assertEquals(ShipWeaponSlot.UPPER, ShipWeaponSlot.valueOf("UPPER"));
		assertEquals(ShipWeaponSlot.LOWER, ShipWeaponSlot.valueOf("LOWER"));
		assertEquals(ShipWeaponSlot.SIDE_LEFT, ShipWeaponSlot.valueOf("SIDE_LEFT"));
		assertEquals(ShipWeaponSlot.SIDE_RIGHT, ShipWeaponSlot.valueOf("SIDE_RIGHT"));
	}
}
