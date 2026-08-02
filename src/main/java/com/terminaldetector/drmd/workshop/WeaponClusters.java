package com.terminaldetector.drmd.workshop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WeaponClusters — port of SCK clusters.lua.
 * Order: Center → Upper → SideLeft → SideRight → Lower.
 * Muzzle attach points feed WeaponCore.muzzleFor (D6_SCKBridge.GetMuzzle).
 */
public final class WeaponClusters {
	public static final String[] ORDER = { "Center", "Upper", "SideLeft", "SideRight", "Lower" };

	public static final int[][] COLORS = {
			{255, 220, 40},   // Center — yellow main
			{180, 80, 255},   // Upper — purple heavy
			{80, 140, 255},   // SideLeft — blue support
			{80, 140, 255},   // SideRight — blue support
			{255, 60, 60}     // Lower — red special
	};

	public static final String[] LABELS = {
			"Center (main weapons)",
			"Upper (heavy / missiles)",
			"Side Left (support)",
			"Side Right (support)",
			"Lower (special)"
	};

	/** Local muzzle offset in camera space (fwd, rgt, up) — Source-style inches → MC via /16. */
	public record MuzzleOffset(float fwd, float rgt, float up) {
		public float mcFwd() { return fwd / 16f; }
		public float mcRgt() { return rgt / 16f; }
		public float mcUp() { return up / 16f; }
	}

	private WeaponClusters() {}

	public static int rank(String cluster) {
		for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(cluster)) return i;
		return 0;
	}

	public static void sort(List<ClusterModule> modules) {
		modules.sort(Comparator
				.comparingInt((ClusterModule m) -> rank(m.cluster))
				.thenComparingInt(m -> m.slot));
	}

	/**
	 * Build muzzle table from attach.muzzle modules (1-based idx).
	 * Matches D6_SCKBridge.Finalize.
	 */
	public static Map<Integer, MuzzleOffset> extractMuzzles(List<ClusterModule> modules) {
		Map<Integer, MuzzleOffset> map = new HashMap<>();
		for (ClusterModule m : modules) {
			if (m.muzzle) {
				map.put(m.muzzleIdx > 0 ? m.muzzleIdx : 1,
						new MuzzleOffset(m.posFwd, m.posRgt, m.posUp));
			}
		}
		return map;
	}

	public static List<ClusterModule> inCluster(List<ClusterModule> all, String cluster) {
		List<ClusterModule> out = new ArrayList<>();
		for (ClusterModule m : all) if (cluster.equals(m.cluster)) out.add(m);
		out.sort(Comparator.comparingInt(m -> m.slot));
		return out;
	}

	public static ClusterModule make(String cluster, int slot, String name, String model,
									 float fwd, float rgt, float up, float scale, boolean muzzle, int muzzleIdx) {
		ClusterModule m = new ClusterModule();
		m.cluster = cluster;
		m.slot = slot;
		m.name = name;
		m.model = model;
		m.posFwd = fwd;
		m.posRgt = rgt;
		m.posUp = up;
		m.scaleX = m.scaleY = m.scaleZ = scale;
		m.muzzle = muzzle;
		m.muzzleIdx = muzzleIdx;
		if (rgt < 0) m.yaw = -9;
		else if (rgt > 0) m.yaw = 9;
		return m;
	}
}
