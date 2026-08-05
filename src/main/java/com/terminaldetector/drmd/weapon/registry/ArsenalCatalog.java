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
		put("mega_laser", Family.LASER, "Мега-луч (удерж.)", "MEGA-B");
		put("laser_prism", Family.LASER, "Призменный лазер", "PRISM");

		// 2) Blasters — Spreadfire / Fusion / Vulcan / Gatling / Plasma
		put("spread", Family.BLASTER, "Spreadfire", "SPREAD");
		put("fusion", Family.BLASTER, "Fusion", "FUSION");
		put("vulcan", Family.BLASTER, "Vulcan", "VULCAN");
		put("gatling", Family.BLASTER, "Гатлинг", "GATLING");
		put("plasma", Family.BLASTER, "Плазма", "PLASMA");

		// 2b) Descent 2 super primaries — the halves of the canon that were never ported
		put("gauss", Family.BLASTER, "Gauss", "GAUSS");
		put("helix", Family.BLASTER, "Helix", "HELIX");
		put("phoenix", Family.BLASTER, "Phoenix", "PHOENIX");
		put("omega", Family.BLASTER, "Omega", "OMEGA");

		// 3) Secondaries — the launched half. Canon names, because these are named weapons and not
		// weights: the generic rocket_* ids were the placeholder, not the other way round.
		put("concussion", Family.ROCKET, "Concussion", "CONC");
		put("homing", Family.ROCKET, "Homing", "HOMING");
		put("smart_missile", Family.ROCKET, "Smart", "SMART");
		put("mega_missile", Family.ROCKET, "Mega", "MEGA-M");

		// 3b) Descent 2 super secondaries — one older brother per D1 secondary, same as the primaries.
		// The fifth pair is Proximity Bomb → Smart Mine, and both of those are laid, so they sit in
		// the mine family below rather than here.
		put("flash", Family.ROCKET, "Flash", "FLASH");
		put("guided", Family.ROCKET, "Guided", "GUIDED");
		put("mercury", Family.ROCKET, "Mercury", "MERCURY");
		put("earthshaker", Family.ROCKET, "Earthshaker", "SHAKER");

		// 3c) Two weights that predate the port and answer to nothing in the canon. Kept because they
		// are somebody's loadout by now, named so they do not read as Descent weapons.
		put("rocket_dual", Family.ROCKET, "Ракета сдвоенная", "R-DUAL");
		put("rocket_heavy", Family.ROCKET, "Ракета тяжёлая", "R-HVY");

		// 4) Air mines (no gravity mines) — mine_prox and mine_smart are secondaries 3 and 8 of the
		// canon bank; the other two are extras like the rockets above.
		put("mine_prox", Family.MINE, "Proximity Bomb", "M-PROX");
		put("mine_plasma", Family.MINE, "Плазма-мина", "M-PLAS");
		put("mine_energy", Family.MINE, "Энерго-мина", "M-NRG");
		put("mine_smart", Family.MINE, "Smart Mine", "M-SMRT");

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
				ModItems.CONCUSSION,
				ModItems.HOMING,
				ModItems.SMART_MISSILE,
				ModItems.MEGA_MISSILE,
				ModItems.FLASH,
				ModItems.GUIDED,
				ModItems.MERCURY,
				ModItems.EARTHSHAKER,
				ModItems.ROCKET_DUAL,
				ModItems.ROCKET_HEAVY,
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

	/** Bank order: the four launched D1 secondaries, then each one's D2 super, then the extras. */
	public static Item[] hudMissileOrder() {
		return new Item[]{
				ModItems.CONCUSSION, ModItems.HOMING, ModItems.SMART_MISSILE, ModItems.MEGA_MISSILE,
				ModItems.FLASH, ModItems.GUIDED, ModItems.MERCURY, ModItems.EARTHSHAKER,
				ModItems.ROCKET_DUAL, ModItems.ROCKET_HEAVY
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
				"mg", "heavy", "rockets", "gravy_railgun", "flak", "railmk2", "frag",
				"overdrive", "shockwave", "darklance", "darkfield", "energytrap", "gravmine",
				"plasmamine", "reactor", "telefrag", "whiplash",
				// The generic weights the secondaries wore before their own names came back.
				"rocket_light", "rocket_offense", "rocket_triple", "rocket_mega"
		);
	}

	/**
	 * What each renamed weapon used to be called.
	 *
	 * <p>Anything that filed something away under a weapon id — workshop layouts are the live case —
	 * has to be able to find it again after the rename, so the old key stays readable. Only the
	 * lookups consult this; nothing writes under the old name any more.
	 */
	public static Map<String, String> legacyIdOf() {
		return Map.of(
				"concussion", "rocket_light",
				"homing", "rocket_offense",
				"smart_missile", "rocket_triple",
				"mega_missile", "rocket_mega"
		);
	}
}
