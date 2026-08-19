package com.terminaldetector.drmd.entity;

/**
 * The four weapon hardpoints a {@code PyroShipEntity} can equip. Names reuse
 * {@code workshop.WeaponClusters.ORDER}'s existing vocabulary rather than inventing new terms —
 * that system already labels multi-barrel muzzle zones "Upper (heavy/missiles)", "Lower (special)",
 * "Side Left/Right (support)" for the same hull. {@code CENTER} is deliberately not included: the
 * Pyro GX's own established hardpoint scheme (top = cluster bombs, ventral = mining beam, wings =
 * lasers/missiles) has no center mount.
 */
public enum ShipWeaponSlot {
	UPPER,
	LOWER,
	SIDE_LEFT,
	SIDE_RIGHT
}
