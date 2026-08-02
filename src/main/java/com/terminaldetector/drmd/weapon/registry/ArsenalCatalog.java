package com.terminaldetector.drmd.weapon.registry;

import com.terminaldetector.drmd.weapon.items.ModItems;
import net.minecraft.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Closed Descent arsenal for the strong MC release.
 *
 * <p>Everything outside this catalog stays registered for save compatibility but is
 * hidden from the creative tab and {@code /d6 weapons give_all}.
 */
public final class ArsenalCatalog {
	private ArsenalCatalog() {}

	public enum Family {
		LASER, BLASTER, ROCKET, MINE, UNIQUE, DRILL, BUILD, ORDNANCE
	}

	public record Entry(String id, Family family, String label, String hudSlot) {}

	private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

	static {
		// 1) Five laser installations — Descent travel-time bolts from modules
		put("laser", Family.LASER, "Лазер", "LASER");
		put("laser_pulse", Family.LASER, "Импульс-лазер", "PULSE");
		put("quad_laser", Family.LASER, "Quad-лазер", "QUAD");
		put("mega_laser", Family.LASER, "Мега-лазер", "MEGA-L");
		put("laser_prism", Family.LASER, "Призменный лазер", "PRISM");

		// 2) Blasters — Spreadfire / Fusion / Vulcan / Gatling / Plasma
		put("spread", Family.BLASTER, "Spreadfire", "SPREAD");
		put("fusion", Family.BLASTER, "Fusion", "FUSION");
		put("vulcan", Family.BLASTER, "Vulcan", "VULCAN");
		put("gatling", Family.BLASTER, "Гатлинг", "GATLING");
		put("plasma", Family.BLASTER, "Плазма", "PLASMA");

		// 3) Rockets — six Descent weights
		put("rocket_light", Family.ROCKET, "Ракета лёгкая", "R-LIGHT");
		put("rocket_offense", Family.ROCKET, "Ракета наступательная", "R-ATK");
		put("rocket_dual", Family.ROCKET, "Ракета сдвоенная", "R-DUAL");
		put("rocket_triple", Family.ROCKET, "Ракета строенная", "R-TRI");
		put("rocket_heavy", Family.ROCKET, "Ракета тяжёлая", "R-HVY");
		put("rocket_mega", Family.ROCKET, "Мега-ракета", "R-MEGA");

		// 4) Air mines (no gravity mines)
		put("mine_prox", Family.MINE, "Прокси-мина", "M-PROX");
		put("mine_plasma", Family.MINE, "Плазма-мина", "M-PLAS");
		put("mine_energy", Family.MINE, "Энерго-мина", "M-NRG");
		put("mine_smart", Family.MINE, "Смарт-мина", "M-SMRT");

		// 5) Unique
		put("bfg", Family.UNIQUE, "BFG", "BFG");
		put("beam_lance", Family.UNIQUE, "Лучевой снаряд", "BEAM");
		put("warp", Family.UNIQUE, "Телепорт", "WARP");

		// 6) Droppable bomb bay (cluster / rocket / guided…) — kept for release
		put("bomb_tnt", Family.ORDNANCE, "Бомба ТНТ", "BOMB");
		put("bomb_cluster", Family.ORDNANCE, "Кассетная", "CLUSTER");
		put("bomb_heavy_cluster", Family.ORDNANCE, "Тяж. кассета", "H-CLST");
		put("bomb_rocket", Family.ORDNANCE, "Ракетный отсек", "B-RKT");
		put("bomb_incendiary", Family.ORDNANCE, "Зажигательная", "INCEND");
		put("bomb_guided", Family.ORDNANCE, "Управляемая", "GUIDED");
		put("laser_designator", Family.ORDNANCE, "Целеуказатель", "DESIGN");
	}

	private static void put(String id, Family family, String label, String hud) {
		BY_ID.put(id, new Entry(id, family, label, hud));
	}

	public static boolean isOpen(String weaponId) {
		return BY_ID.containsKey(weaponId);
	}

	public static Entry get(String weaponId) {
		return BY_ID.get(weaponId);
	}

	public static List<Entry> all() {
		return List.copyOf(BY_ID.values());
	}

	public static List<Entry> family(Family family) {
		return BY_ID.values().stream().filter(e -> e.family == family).toList();
	}

	/** Items shown in the creative weapons group (combat closed set). */
	public static Item[] creativeWeapons() {
		return new Item[]{
				ModItems.LASER,
				ModItems.LASER_PULSE,
				ModItems.QUAD_LASER,
				ModItems.MEGA_LASER,
				ModItems.LASER_PRISM,
				ModItems.SPREAD,
				ModItems.FUSION,
				ModItems.VULCAN,
				ModItems.GATLING,
				ModItems.PLASMA,
				ModItems.ROCKET_LIGHT,
				ModItems.ROCKET_OFFENSE,
				ModItems.ROCKET_DUAL,
				ModItems.ROCKET_TRIPLE,
				ModItems.ROCKET_HEAVY,
				ModItems.ROCKET_MEGA,
				ModItems.MINE_PROX,
				ModItems.MINE_PLASMA,
				ModItems.MINE_ENERGY,
				ModItems.MINE_SMART,
				ModItems.BFG,
				ModItems.BEAM_LANCE,
				ModItems.WARP,
				ModItems.BOMB_TNT,
				ModItems.BOMB_CLUSTER,
				ModItems.BOMB_HEAVY_CLUSTER,
				ModItems.BOMB_ROCKET,
				ModItems.BOMB_INCENDIARY,
				ModItems.BOMB_GUIDED,
				ModItems.LASER_DESIGNATOR
		};
	}

	/** HUD primary/secondary style list order. */
	public static Item[] hudPrimaryOrder() {
		return new Item[]{
				ModItems.LASER, ModItems.VULCAN, ModItems.SPREAD, ModItems.PLASMA, ModItems.FUSION, ModItems.GATLING
		};
	}

	public static Item[] hudMissileOrder() {
		return new Item[]{
				ModItems.ROCKET_LIGHT, ModItems.ROCKET_OFFENSE, ModItems.ROCKET_DUAL,
				ModItems.ROCKET_TRIPLE, ModItems.ROCKET_HEAVY, ModItems.ROCKET_MEGA
		};
	}

	public static Item[] hudMineOrder() {
		return new Item[]{
				ModItems.MINE_PROX, ModItems.MINE_PLASMA, ModItems.MINE_ENERGY, ModItems.MINE_SMART
		};
	}

	/** Legacy placeholder ids kept registered but not in the open set. */
	public static Set<String> retiredIds() {
		return Set.of(
				"mg", "heavy", "rockets", "gravy_railgun", "flak", "homing", "concussion",
				"smart_missile", "mega_missile", "railmk2", "frag", "overdrive", "shockwave",
				"darklance", "darkfield", "energytrap", "gravmine", "plasmamine",
				"reactor", "telefrag", "whiplash"
		);
	}
}
