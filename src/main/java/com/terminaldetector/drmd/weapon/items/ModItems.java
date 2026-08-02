package com.terminaldetector.drmd.weapon.items;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.registry.ArsenalCatalog;
import com.terminaldetector.drmd.weapon.registry.WeaponDef;
import com.terminaldetector.drmd.weapon.registry.WeaponRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Weapon items. Open combat set is {@link ArsenalCatalog}; retired placeholders stay
 * registered under legacy item ids for world compatibility but are hidden from creative.
 */
public final class ModItems {
	// ---- Open arsenal (canonical names) -------------------------------------------------------
	public static Item LASER;
	public static Item LASER_PULSE;
	public static Item QUAD_LASER;
	public static Item MEGA_LASER;
	public static Item LASER_PRISM;

	public static Item SPREAD;
	public static Item FUSION;
	public static Item VULCAN;
	public static Item GATLING;
	public static Item PLASMA;

	public static Item ROCKET_LIGHT;
	public static Item ROCKET_OFFENSE;
	public static Item ROCKET_DUAL;
	public static Item ROCKET_TRIPLE;
	public static Item ROCKET_HEAVY;
	public static Item ROCKET_MEGA;

	public static Item MINE_PROX;
	public static Item MINE_PLASMA;
	public static Item MINE_ENERGY;
	public static Item MINE_SMART;

	public static Item BFG;
	public static Item BEAM_LANCE;
	public static Item WARP;

	public static Item SHIELD_ORB;
	public static Item ENERGY_ORB_PICKUP;

	// ---- Legacy aliases (same instances where remapped; else retired stubs) -------------------
	public static Item MG;
	public static Item HEAVY;
	public static Item ROCKETS;
	public static Item GRAVY_RAILGUN;
	public static Item FLAK;
	public static Item HOMING;
	public static Item CONCUSSION;
	public static Item SMART_MISSILE;
	public static Item MEGA_MISSILE;
	public static Item RAILMK2;
	public static Item FRAG;
	public static Item OVERDRIVE;
	public static Item SHOCKWAVE;
	public static Item DARKLANCE;
	public static Item DARKFIELD;
	public static Item ENERGYTRAP;
	public static Item GRAVMINE;
	public static Item PLASMAMINE;
	public static Item REACTOR;
	public static Item TELEFRAG;
	public static Item WHIPLASH;

	public static Item PYRO_GX;
	public static Item EGG_ASSAULT;
	public static Item EGG_INTERCEPTOR;
	public static Item EGG_ARTILLERY;
	public static Item EGG_SUPPORT;
	public static Item EGG_HEAVY_ELITE;
	public static Item EGG_MG;
	public static Item EGG_LASER;
	public static Item EGG_RPG;
	public static Item EGG_HEAVY;
	public static Item EGG_SEEKER;

	public static Item BOMB_TNT;
	public static Item BOMB_CLUSTER;
	public static Item BOMB_HEAVY_CLUSTER;
	public static Item BOMB_ROCKET;
	public static Item BOMB_INCENDIARY;
	public static Item BOMB_GUIDED;
	public static Item LASER_DESIGNATOR;

	public static Item ALLOY_PLATE;
	public static Item ENERGY_CELL;
	public static Item TARGETING_CORE;

	public static Item EGG_TRIPOD;
	public static Item EGG_SCANNER;
	public static Item EGG_SPIDER_TURRET;

	public static final RegistryKey<ItemGroup> GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(DescentMod.MOD_ID, "weapons"));

	private ModItems() {}

	private static Item register(String id, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(DescentMod.MOD_ID, id), item);
	}

	private static Item wep(String itemId, WeaponDef def) {
		Item item = register(itemId, new DescentWeaponItem(def, new Item.Settings()));
		WeaponRegistry.register(def);
		return item;
	}

	public static void register() {
		// --- Lasers (5) — item paths keep legacy textures where remapped ---
		LASER = wep("weapon_d6_laser", new WeaponDef("laser", "Лазер", "laser", 0f, 0.12f, 36f, 12f, 85f, 6200f, 16f, DamageClass.ENERGY, "laser"));
		LASER_PULSE = wep("weapon_d6_darklance", new WeaponDef("laser_pulse", "Импульс-лазер", "laser", 2.5f, 0.07f, 14f, 6f, 60f, 7200f, 8f, DamageClass.ENERGY, "laser_pulse"));
		DARKLANCE = LASER_PULSE;
		QUAD_LASER = wep("weapon_d6_quad_laser", new WeaponDef("quad_laser", "Quad-лазер", "laser", 12f, 0.22f, 45f, 55f, 160f, 6500f, 20f, DamageClass.ENERGY, "quad_laser"));
		MEGA_LASER = wep("weapon_d6_mega_laser", new WeaponDef("mega_laser", "Мега-лазер", "laser", 55f, 1.1f, 210f, 140f, 520f, 7800f, 90f, DamageClass.ENERGY, "mega_laser"));
		LASER_PRISM = wep("weapon_d6_overdrive", new WeaponDef("laser_prism", "Призменный лазер", "laser", 6f, 0.16f, 22f, 10f, 80f, 6400f, 14f, DamageClass.ENERGY, "laser_prism"));
		OVERDRIVE = LASER_PRISM;

		// --- Blasters ---
		SPREAD = wep("weapon_d6_flak", new WeaponDef("spread", "Spreadfire", "blaster", 6f, 0.35f, 10f, 8f, 70f, 3800f, 25f, DamageClass.KINETIC, "spread"));
		FLAK = SPREAD;
		FUSION = register("weapon_d6_fusion", new FusionCannonItem(
				new WeaponDef("fusion", "Fusion", "blaster", 25f, 0.5f, 195f, 90f, 260f, 2400f, 320f, DamageClass.EXOTIC, "fusion"),
				new Item.Settings()));
		WeaponRegistry.register(((FusionCannonItem) FUSION).getDef());
		VULCAN = wep("weapon_d6_vulcan", new WeaponDef("vulcan", "Vulcan", "blaster", 2f, 0.065f, 15f, 0f, 0f, 4800f, 8f, DamageClass.KINETIC, "vulcan"));
		GATLING = wep("weapon_d6_mg", new WeaponDef("gatling", "Гатлинг", "blaster", 1.5f, 0.04f, 11f, 0f, 0f, 5200f, 4f, DamageClass.KINETIC, "gatling"));
		MG = GATLING;
		PLASMA = wep("weapon_d6_plasma", new WeaponDef("plasma", "Плазма", "blaster", 8f, 0.45f, 45f, 25f, 120f, 3200f, 40f, DamageClass.EXOTIC, "plasma"));

		// --- Rockets (6) ---
		ROCKET_LIGHT = wep("weapon_d6_concussion", new WeaponDef("rocket_light", "Ракета лёгкая", "rocket", 8f, 0.9f, 70f, 55f, 220f, 3000f, 18f, DamageClass.EXPLOSIVE, "rocket_light"));
		CONCUSSION = ROCKET_LIGHT;
		ROCKET_OFFENSE = wep("weapon_d6_homing", new WeaponDef("rocket_offense", "Ракета наступательная", "rocket", 12f, 1.2f, 110f, 85f, 280f, 2400f, 22f, DamageClass.EXPLOSIVE, "rocket_offense"));
		HOMING = ROCKET_OFFENSE;
		ROCKET_DUAL = wep("weapon_d6_rockets", new WeaponDef("rocket_dual", "Ракета сдвоенная", "rocket", 16f, 1.0f, 95f, 80f, 300f, 2600f, 28f, DamageClass.EXPLOSIVE, "rocket_dual"));
		ROCKETS = ROCKET_DUAL;
		ROCKET_TRIPLE = wep("weapon_d6_smart_missile", new WeaponDef("rocket_triple", "Ракета строенная", "rocket", 20f, 1.3f, 90f, 75f, 300f, 2500f, 36f, DamageClass.EXPLOSIVE, "rocket_triple"));
		SMART_MISSILE = ROCKET_TRIPLE;
		ROCKET_HEAVY = wep("weapon_d6_frag", new WeaponDef("rocket_heavy", "Ракета тяжёлая", "rocket", 28f, 1.8f, 180f, 160f, 480f, 2000f, 45f, DamageClass.EXPLOSIVE, "rocket_heavy"));
		FRAG = ROCKET_HEAVY;
		ROCKET_MEGA = wep("weapon_d6_mega_missile", new WeaponDef("rocket_mega", "Мега-ракета", "rocket", 55f, 3.5f, 420f, 360f, 720f, 1600f, 70f, DamageClass.EXPLOSIVE, "rocket_mega"));
		MEGA_MISSILE = ROCKET_MEGA;

		// --- Air mines (4, no gravity) ---
		MINE_PROX = wep("weapon_d6_gravmine", new WeaponDef("mine_prox", "Прокси-мина", "mine", 25f, 1.2f, 0f, 90f, 220f, 700f, 8f, DamageClass.EXPLOSIVE, "mine_prox"));
		GRAVMINE = MINE_PROX;
		MINE_PLASMA = wep("weapon_d6_plasmamine", new WeaponDef("mine_plasma", "Плазма-мина", "mine", 28f, 1.2f, 0f, 120f, 260f, 700f, 8f, DamageClass.EXPLOSIVE, "mine_plasma"));
		PLASMAMINE = MINE_PLASMA;
		MINE_ENERGY = wep("weapon_d6_energytrap", new WeaponDef("mine_energy", "Энерго-мина", "mine", 22f, 1.2f, 0f, 70f, 200f, 700f, 8f, DamageClass.ENERGY, "mine_energy"));
		ENERGYTRAP = MINE_ENERGY;
		MINE_SMART = wep("weapon_d6_darkfield", new WeaponDef("mine_smart", "Смарт-мина", "mine", 35f, 1.5f, 0f, 140f, 300f, 700f, 8f, DamageClass.EXPLOSIVE, "mine_smart"));
		DARKFIELD = MINE_SMART;

		// --- Unique ---
		BFG = wep("weapon_d6_bfg", new WeaponDef("bfg", "BFG", "unique", 80f, 10.0f, 300f, 150f, 600f, 400f, 200f, DamageClass.EXOTIC, "bfg"));
		BEAM_LANCE = wep("weapon_d6_shockwave", new WeaponDef("beam_lance", "Лучевой снаряд", "unique", 40f, 1.6f, 160f, 80f, 200f, 2800f, 40f, DamageClass.ENERGY, "beam_lance"));
		SHOCKWAVE = BEAM_LANCE;
		WARP = wep("weapon_d6_warp", new WeaponDef("warp", "Телепорт", "unique", 30f, 2.5f, 100f, 0f, 200f, 0f, 0f, DamageClass.EXOTIC, "warp"));

		// --- Retired placeholders (still registered, hidden from creative) ---
		HEAVY = wep("weapon_d6_heavy", new WeaponDef("heavy", "Тяжёлый (retired)", "retired", 18f, 1.1f, 80f, 60f, 220f, 1100f, 160f, DamageClass.EXPLOSIVE, "basic"));
		GRAVY_RAILGUN = wep("weapon_d6_gravy_railgun", new WeaponDef("gravy_railgun", "Грави-Рельса (retired)", "retired", 0f, 0.3f, 0f, 0f, 0f, 18000f, 0f, DamageClass.EXOTIC, "gravy"));
		RAILMK2 = wep("weapon_d6_railmk2", new WeaponDef("railmk2", "Рельса МК2 (retired)", "retired", 18f, 0.6f, 120f, 0f, 0f, 8000f, 80f, DamageClass.KINETIC, "rail"));
		REACTOR = wep("weapon_d6_reactor", new WeaponDef("reactor", "Реактор (retired)", "retired", 30f, 15.0f, 300f, 300f, 600f, 600f, 0f, DamageClass.EXPLOSIVE, "reactor"));
		TELEFRAG = wep("weapon_d6_telefrag", new WeaponDef("telefrag", "Телефраг (retired)", "retired", 50f, 5.0f, 1000f, 0f, 140f, 0f, 0f, DamageClass.EXOTIC, "telefrag"));
		WHIPLASH = wep("weapon_d6_whiplash", new WeaponDef("whiplash", "Хлыст (retired)", "retired", 20f, 3.0f, 80f, 0f, 160f, 0f, 0f, DamageClass.KINETIC, "whiplash"));

		SHIELD_ORB = register("shield_orb", new com.terminaldetector.drmd.pickup.ShieldOrbItem(new Item.Settings().maxCount(16)));
		ENERGY_ORB_PICKUP = register("energy_orb_pickup", new com.terminaldetector.drmd.pickup.EnergyOrbItem(new Item.Settings().maxCount(16)));

		PYRO_GX = register("pyro_gx", new com.terminaldetector.drmd.entity.PyroShipItem(new Item.Settings().maxCount(1)));

		EGG_ASSAULT = egg("spawn_egg_assault", com.terminaldetector.drmd.ai.AiRole.ASSAULT, 0xCC3333, 0x442222);
		EGG_INTERCEPTOR = egg("spawn_egg_interceptor", com.terminaldetector.drmd.ai.AiRole.INTERCEPTOR, 0x33AACC, 0x224455);
		EGG_ARTILLERY = egg("spawn_egg_artillery", com.terminaldetector.drmd.ai.AiRole.ARTILLERY, 0xCCAA33, 0x554422);
		EGG_SUPPORT = egg("spawn_egg_support", com.terminaldetector.drmd.ai.AiRole.SUPPORT, 0x33CC66, 0x224433);
		EGG_HEAVY_ELITE = egg("spawn_egg_heavy_elite", com.terminaldetector.drmd.ai.AiRole.HEAVY_ELITE, 0xAA33CC, 0x331144);
		EGG_MG = egg("spawn_egg_mg", com.terminaldetector.drmd.ai.AiRole.MG, 0x888888, 0x333333);
		EGG_LASER = egg("spawn_egg_laser", com.terminaldetector.drmd.ai.AiRole.LASER, 0xFF5555, 0x551111);
		EGG_RPG = egg("spawn_egg_rpg", com.terminaldetector.drmd.ai.AiRole.RPG, 0xCC7733, 0x442211);
		EGG_HEAVY = egg("spawn_egg_heavy", com.terminaldetector.drmd.ai.AiRole.HEAVY, 0x555577, 0x222233);
		EGG_SEEKER = egg("spawn_egg_seeker", com.terminaldetector.drmd.ai.AiRole.SEEKER, 0x55FFAA, 0x115533);

		BOMB_TNT = register("bomb_tnt", new com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem(
				com.terminaldetector.drmd.world.bombardment.OrdnanceType.TNT_BOMB, new Item.Settings()));
		BOMB_CLUSTER = register("bomb_cluster", new com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem(
				com.terminaldetector.drmd.world.bombardment.OrdnanceType.CLUSTER, new Item.Settings()));
		BOMB_HEAVY_CLUSTER = register("bomb_heavy_cluster", new com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem(
				com.terminaldetector.drmd.world.bombardment.OrdnanceType.HEAVY_CLUSTER, new Item.Settings()));
		BOMB_ROCKET = register("bomb_rocket", new com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem(
				com.terminaldetector.drmd.world.bombardment.OrdnanceType.ROCKET, new Item.Settings()));
		BOMB_INCENDIARY = register("bomb_incendiary", new com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem(
				com.terminaldetector.drmd.world.bombardment.OrdnanceType.INCENDIARY, new Item.Settings()));
		BOMB_GUIDED = register("bomb_guided", new com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem(
				com.terminaldetector.drmd.world.bombardment.OrdnanceType.LASER_GUIDED, new Item.Settings()));
		LASER_DESIGNATOR = register("laser_designator",
				new com.terminaldetector.drmd.world.bombardment.BombardmentItems.LaserDesignatorItem(new Item.Settings()));

		ALLOY_PLATE = register("alloy_plate", new Item(new Item.Settings()));
		ENERGY_CELL = register("energy_cell", new Item(new Item.Settings()));
		TARGETING_CORE = register("targeting_core", new Item(new Item.Settings()));

		EGG_TRIPOD = register("spawn_egg_tripod", new net.minecraft.item.SpawnEggItem(
				com.terminaldetector.drmd.entity.ModEntities.TRIPOD, 0x3A4450, 0xFF3366, new Item.Settings()));
		EGG_SCANNER = register("spawn_egg_scanner", new net.minecraft.item.SpawnEggItem(
				com.terminaldetector.drmd.entity.ModEntities.SCANNER, 0x1E2A38, 0x35E0FF, new Item.Settings()));
		EGG_SPIDER_TURRET = register("spawn_egg_spider_turret", new net.minecraft.item.SpawnEggItem(
				com.terminaldetector.drmd.entity.ModEntities.SPIDER_TURRET, 0x2A3038, 0xFFC24D, new Item.Settings()));

		Registry.register(Registries.ITEM_GROUP, GROUP_KEY, FabricItemGroup.builder()
				.icon(() -> new ItemStack(PYRO_GX))
				.displayName(Text.translatable("itemGroup.drmd.weapons"))
				.entries((ctx, entries) -> {
					entries.add(PYRO_GX);
					entries.add(ALLOY_PLATE);
					entries.add(ENERGY_CELL);
					entries.add(TARGETING_CORE);
					entries.add(SHIELD_ORB);
					entries.add(ENERGY_ORB_PICKUP);
					for (Item w : ArsenalCatalog.creativeWeapons()) entries.add(w);
					entries.add(EGG_ASSAULT);
					entries.add(EGG_INTERCEPTOR);
					entries.add(EGG_ARTILLERY);
					entries.add(EGG_SUPPORT);
					entries.add(EGG_HEAVY_ELITE);
					entries.add(EGG_MG);
					entries.add(EGG_LASER);
					entries.add(EGG_RPG);
					entries.add(EGG_HEAVY);
					entries.add(EGG_SEEKER);
					entries.add(EGG_TRIPOD);
					entries.add(EGG_SCANNER);
					entries.add(EGG_SPIDER_TURRET);
					entries.add(BOMB_TNT);
					entries.add(BOMB_CLUSTER);
					entries.add(BOMB_HEAVY_CLUSTER);
					entries.add(BOMB_ROCKET);
					entries.add(BOMB_INCENDIARY);
					entries.add(BOMB_GUIDED);
					entries.add(LASER_DESIGNATOR);
				})
				.build());

		DescentMod.LOGGER.info("Registered closed Descent arsenal ({} open weapons)", ArsenalCatalog.all().size());
	}

	private static Item egg(String id, com.terminaldetector.drmd.ai.AiRole role, int primary, int secondary) {
		return register(id, new com.terminaldetector.drmd.entity.DroneSpawnEggItem(role, primary, secondary, new Item.Settings()));
	}
}
