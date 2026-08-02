package com.terminaldetector.drmd.world.bombardment;

/**
 * Aerial munition types — bomb-bay and rocket-bay ordinance.
 */
public enum OrdnanceType {
	/** Classic free-fall HE. */
	TNT_BOMB(1.45f, false, false, false, false, 0x333333),
	/** Standard cluster — mid-air submunitions with forward/down scatter. */
	CLUSTER(0.95f, true, false, false, false, 0x554422),
	/** Heavy cluster — high yield, fragments fly in every direction. */
	HEAVY_CLUSTER(1.75f, true, false, false, true, 0xCC7722),
	INCENDIARY(1.15f, false, true, false, false, 0xAA4400),
	LASER_GUIDED(1.65f, false, false, false, false, 0x44FF88),
	/** Air-to-ground rocket — powered flight, wide crater. */
	ROCKET(2.25f, false, false, true, false, 0xFF5522);

	public final float blastPower;
	public final boolean cluster;
	public final boolean incendiary;
	public final boolean rocket;
	/** True → spherical high-damage fragment spray. */
	public final boolean heavyCluster;
	public final int trailColor;

	OrdnanceType(float blastPower, boolean cluster, boolean incendiary,
				 boolean rocket, boolean heavyCluster, int trailColor) {
		this.blastPower = blastPower;
		this.cluster = cluster;
		this.incendiary = incendiary;
		this.rocket = rocket;
		this.heavyCluster = heavyCluster;
		this.trailColor = trailColor;
	}
}
