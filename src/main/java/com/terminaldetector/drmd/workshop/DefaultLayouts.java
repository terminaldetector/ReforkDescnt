package com.terminaldetector.drmd.workshop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default Doom-style layouts from d6_wepview.lua CFG, expressed as WeaponClusters.
 * Models: barrel (airboat), nosegun, strider, gravy (physics gun).
 */
public final class DefaultLayouts {
	private DefaultLayouts() {}

	private static final float GF = 15, GU = -12, GS = 1.20f;       // gravy center
	private static final float GFR = 15, GUR = -24;                  // gravy rail lower
	private static final float SF = 27, SU = -13, SS = 0.55f;        // strider center
	private static final float SBF = 25, SBU = -13, SBS = 0.75f;     // strider big

	/** 4 wing mounts + optional center (Layout in wepview). */
	public static List<ClusterModule> layout(String row1, float s1, String row2, float s2,
											 String center, float cf, float cu, float cs) {
		List<ClusterModule> m = new ArrayList<>();
		// Lower row → SideLeft / SideRight
		m.add(WeaponClusters.make("SideLeft", 1, "wing_l1", row1, 19, -29, -13, s1, true, 1));
		m.add(WeaponClusters.make("SideRight", 1, "wing_r1", row1, 19, 29, -13, s1, true, 2));
		// Upper row
		m.add(WeaponClusters.make("Upper", 1, "wing_l2", row2, 19, -32, 10, s2, true, 3));
		m.add(WeaponClusters.make("Upper", 2, "wing_r2", row2, 19, 32, 10, s2, true, 4));
		if (center != null) {
			boolean isGravy = "gravy".equals(center);
			m.add(WeaponClusters.make("Center", 1, isGravy ? "gravy" : "core", center,
					cf, 0, cu, cs, !isGravy, isGravy ? 0 : 5));
		}
		return m;
	}

	/** Pair-only (utility) — LayoutPair. */
	public static List<ClusterModule> layoutPair(String mdl, float scale,
												 String center, float cf, float cu, float cs) {
		List<ClusterModule> m = new ArrayList<>();
		m.add(WeaponClusters.make("SideLeft", 1, "wing_l", mdl, 19, -29, -13, scale, true, 1));
		m.add(WeaponClusters.make("SideRight", 1, "wing_r", mdl, 19, 29, -13, scale, true, 2));
		if (center != null) {
			boolean isGravy = "gravy".equals(center);
			m.add(WeaponClusters.make("Center", 1, isGravy ? "gravy" : "core", center,
					cf, 0, cu, cs, false, 0));
		}
		return m;
	}

	private static final Map<String, List<ClusterModule>> BUILTIN = new LinkedHashMap<>();

	static {
		// Primary
		// Closed arsenal layouts (+ legacy keys for retired / remapped ids)
		BUILTIN.put("laser", layout("barrel", 1.00f, "barrel", 1.00f, "strider", SF, SU, SS));
		BUILTIN.put("laser_pulse", layout("nosegun", 1.50f, "nosegun", 1.40f, "strider", SF, SU, 0.70f));
		BUILTIN.put("quad_laser", layout("barrel", 0.90f, "barrel", 0.90f, "strider", SF, SU, 0.60f));
		BUILTIN.put("mega_laser", layout("nosegun", 1.85f, "nosegun", 1.75f, "strider", SF, SU, 0.85f));
		BUILTIN.put("laser_prism", layout("nosegun", 1.70f, "nosegun", 1.60f, "strider", SBF, SBU, SBS));
		BUILTIN.put("spread", layout("barrel", 1.05f, "nosegun", 1.55f, "gravy", GF, GU, GS));
		BUILTIN.put("fusion", layout("nosegun", 1.75f, "nosegun", 1.65f, "gravy", GF, GU, GS));
		BUILTIN.put("vulcan", layout("barrel", 0.92f, "barrel", 0.92f, "gravy", GF, GU, GS));
		BUILTIN.put("gatling", layout("barrel", 1.00f, "barrel", 1.00f, "gravy", GF, GU, GS));
		BUILTIN.put("plasma", layout("nosegun", 1.55f, "barrel", 1.00f, "gravy", GF, GU, GS));
		BUILTIN.put("rocket_light", layout("nosegun", 1.40f, "barrel", 0.95f, "gravy", GF, GU, GS));
		BUILTIN.put("rocket_offense", layout("nosegun", 1.40f, "barrel", 0.95f, "gravy", GF, GU, GS));
		BUILTIN.put("rocket_dual", layout("barrel", 1.00f, "barrel", 1.00f, "gravy", GF, GU, GS));
		BUILTIN.put("rocket_triple", layout("nosegun", 1.55f, "nosegun", 1.40f, "gravy", GF, GU, GS));
		BUILTIN.put("rocket_heavy", layout("nosegun", 1.70f, "barrel", 1.05f, "gravy", GF, GU, GS));
		BUILTIN.put("rocket_mega", layout("nosegun", 1.80f, "nosegun", 1.80f, "gravy", GF, GU, GS));
		BUILTIN.put("mine_prox", layoutPair("barrel", 0.85f, null, 0, 0, 0));
		BUILTIN.put("mine_plasma", layoutPair("barrel", 0.85f, null, 0, 0, 0));
		BUILTIN.put("mine_energy", layoutPair("nosegun", 1.30f, "strider", SF, SU, SS));
		BUILTIN.put("mine_smart", layoutPair("nosegun", 1.50f, "strider", SF, SU, 0.70f));
		BUILTIN.put("bfg", layout("nosegun", 1.70f, "nosegun", 1.70f, "gravy", GF, GU, GS));
		BUILTIN.put("beam_lance", layout("nosegun", 1.60f, "nosegun", 1.60f, "gravy", GF, GU, GS));
		BUILTIN.put("warp", layoutPair("nosegun", 1.20f, "strider", SF, SU, SS));
		// Legacy aliases
		BUILTIN.put("mg", BUILTIN.get("gatling"));
		BUILTIN.put("flak", BUILTIN.get("spread"));
		BUILTIN.put("rockets", BUILTIN.get("rocket_dual"));
		BUILTIN.put("concussion", BUILTIN.get("rocket_light"));
		BUILTIN.put("homing", BUILTIN.get("rocket_offense"));
		BUILTIN.put("smart_missile", BUILTIN.get("rocket_triple"));
		BUILTIN.put("mega_missile", BUILTIN.get("rocket_mega"));
		BUILTIN.put("frag", BUILTIN.get("rocket_heavy"));
		BUILTIN.put("overdrive", BUILTIN.get("laser_prism"));
		BUILTIN.put("darklance", BUILTIN.get("laser_pulse"));
		BUILTIN.put("shockwave", BUILTIN.get("beam_lance"));
		BUILTIN.put("gravmine", BUILTIN.get("mine_prox"));
		BUILTIN.put("plasmamine", BUILTIN.get("mine_plasma"));
		BUILTIN.put("energytrap", BUILTIN.get("mine_energy"));
		BUILTIN.put("darkfield", BUILTIN.get("mine_smart"));
		BUILTIN.put("heavy", layout("nosegun", 1.70f, "barrel", 1.05f, "gravy", GF, GU, GS));
		BUILTIN.put("gravy_railgun", layout("barrel", 1.00f, "barrel", 1.00f, "gravy", GFR, GUR, GS));
		BUILTIN.put("railmk2", layout("nosegun", 1.40f, "barrel", 1.00f, "strider", 25, -12, 0.65f));
		BUILTIN.put("telefrag", layoutPair("barrel", 0.80f, null, 0, 0, 0));
		BUILTIN.put("whiplash", layoutPair("barrel", 0.90f, "gravy", GF, GU, GS));
		BUILTIN.put("reactor", layout("nosegun", 1.80f, "nosegun", 1.80f, "strider", SBF, SBU, SBS));
	}

	public static List<ClusterModule> forWeapon(String weaponId) {
		List<ClusterModule> src = BUILTIN.get(weaponId);
		if (src == null) {
			// Generic fallback: dual barrels
			return layoutPair("barrel", 1.0f, "gravy", GF, GU, GS);
		}
		List<ClusterModule> copy = new ArrayList<>(src.size());
		for (ClusterModule m : src) copy.add(m.copy());
		return copy;
	}

	public static Map<String, List<ClusterModule>> all() {
		return BUILTIN;
	}
}
