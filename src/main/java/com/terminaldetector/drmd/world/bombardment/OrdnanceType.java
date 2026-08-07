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
	ROCKET(2.25f, false, false, true, false, 0xFF5522),
	/** Cluster whose bomblets corrupt ground into Nether growth instead of just cratering it. */
	CLUSTER_VIRUS_NETHER(0.95f, true, false, false, false, 0xE0451A, SubmunitionPayload.VIRUS_NETHER),
	/** Cluster whose bomblets seed Sculk — a live infection, not a one-shot palette swap. */
	CLUSTER_VIRUS_SCULK(0.95f, true, false, false, false, 0x2ADFCF, SubmunitionPayload.VIRUS_SCULK),
	/** Cluster whose bomblets land and sweep rotating laser beams before their final blast. */
	CLUSTER_LASER(0.95f, true, false, false, false, 0xFF2244, SubmunitionPayload.LASER);

	/** What a cluster's bomblets do instead of a plain blast, once they land. */
	public enum SubmunitionPayload {
		NONE, VIRUS_NETHER, VIRUS_SCULK, LASER
	}

	public final float blastPower;
	public final boolean cluster;
	public final boolean incendiary;
	public final boolean rocket;
	/** True → spherical high-damage fragment spray. */
	public final boolean heavyCluster;
	public final int trailColor;
	public final SubmunitionPayload payload;

	OrdnanceType(float blastPower, boolean cluster, boolean incendiary,
				 boolean rocket, boolean heavyCluster, int trailColor) {
		this(blastPower, cluster, incendiary, rocket, heavyCluster, trailColor, SubmunitionPayload.NONE);
	}

	OrdnanceType(float blastPower, boolean cluster, boolean incendiary, boolean rocket,
				 boolean heavyCluster, int trailColor, SubmunitionPayload payload) {
		this.blastPower = blastPower;
		this.cluster = cluster;
		this.incendiary = incendiary;
		this.rocket = rocket;
		this.heavyCluster = heavyCluster;
		this.trailColor = trailColor;
		this.payload = payload;
	}
}
