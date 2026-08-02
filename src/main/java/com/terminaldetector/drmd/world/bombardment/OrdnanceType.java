package com.terminaldetector.drmd.world.bombardment;

/**
 * Aerial munition types — TNT becomes air-to-ground ordinance.
 */
public enum OrdnanceType {
	TNT_BOMB(1.0f, false, false, 0x333333),
	CLUSTER(0.55f, true, false, 0x554422),
	INCENDIARY(0.8f, false, true, 0xAA4400),
	LASER_GUIDED(1.25f, false, false, 0x44FF88);

	public final float blastPower;
	public final boolean cluster;
	public final boolean incendiary;
	public final int trailColor;

	OrdnanceType(float blastPower, boolean cluster, boolean incendiary, int trailColor) {
		this.blastPower = blastPower;
		this.cluster = cluster;
		this.incendiary = incendiary;
		this.trailColor = trailColor;
	}
}
