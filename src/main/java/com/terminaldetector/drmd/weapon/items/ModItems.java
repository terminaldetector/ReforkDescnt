package com.terminaldetector.drmd.weapon.items;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.core.DamageClass;
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

public final class ModItems {
	public static Item MG;
	public static Item PLASMA;
	public static Item HEAVY;
	public static Item LASER;
	public static Item ROCKETS;
	public static Item GRAVY_RAILGUN;
	public static Item VULCAN;
	public static Item FLAK;
	public static Item HOMING;
	public static Item CONCUSSION;
	public static Item SMART_MISSILE;
	public static Item MEGA_MISSILE;
	public static Item QUAD_LASER;
	public static Item RAILMK2;
	public static Item BFG;
	public static Item FRAG;
	public static Item OVERDRIVE;
	public static Item SHOCKWAVE;
	public static Item DARKLANCE;
	public static Item DARKFIELD;
	public static Item ENERGYTRAP;
	public static Item GRAVMINE;
	public static Item PLASMAMINE;
	public static Item REACTOR;
	public static Item WARP;
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
	public static Item BOMB_INCENDIARY;
	public static Item BOMB_GUIDED;
	public static Item LASER_DESIGNATOR;

	/** Survival crafting intermediates — every DRMD device is built from these three. */
	public static Item ALLOY_PLATE;
	public static Item ENERGY_CELL;
	public static Item TARGETING_CORE;

	public static Item EGG_TRIPOD;
	public static Item EGG_SCANNER;
	public static Item EGG_SPIDER_TURRET;

	public static Item EGG_MEGA_WORM;
	public static Item EGG_DRONE_SWARM;
	public static Item EGG_REACTOR_KEEPER;
	public static Item EGG_SKY_UFO;
	public static Item EGG_AIR_MINE;

	public static final RegistryKey<ItemGroup> GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(DescentMod.MOD_ID, "weapons"));

	private ModItems() {}

	private static Item register(String id, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(DescentMod.MOD_ID, id), item);
	}

	public static void register() {

		WeaponDef def_mg = new WeaponDef("mg", "Пулемёт", "primary", 1f, 0.06f, 12f, 0f, 0f, 5000f, 5f, DamageClass.KINETIC, "mg");
		MG = register("weapon_d6_mg", new DescentWeaponItem(def_mg, new Item.Settings()));
		WeaponRegistry.register(def_mg);
		WeaponDef def_plasma = new WeaponDef("plasma", "Плазма", "primary", 8f, 0.45f, 45f, 25f, 120f, 3200f, 40f, DamageClass.EXOTIC, "plasma");
		PLASMA = register("weapon_d6_plasma", new DescentWeaponItem(def_plasma, new Item.Settings()));
		WeaponRegistry.register(def_plasma);
		WeaponDef def_heavy = new WeaponDef("heavy", "Тяжёлый", "primary", 18f, 1.1f, 80f, 60f, 220f, 1100f, 160f, DamageClass.EXPLOSIVE, "heavy");
		HEAVY = register("weapon_d6_heavy", new DescentWeaponItem(def_heavy, new Item.Settings()));
		WeaponRegistry.register(def_heavy);
		WeaponDef def_laser = new WeaponDef("laser", "Лазер", "primary", 0f, 0.4f, 150f, 0f, 0f, 0f, 30f, DamageClass.ENERGY, "laser");
		LASER = register("weapon_d6_laser", new DescentWeaponItem(def_laser, new Item.Settings()));
		WeaponRegistry.register(def_laser);
		WeaponDef def_rockets = new WeaponDef("rockets", "Ракеты", "primary", 20f, 0.8f, 80f, 50f, 200f, 2800f, 120f, DamageClass.EXPLOSIVE, "rockets");
		ROCKETS = register("weapon_d6_rockets", new DescentWeaponItem(def_rockets, new Item.Settings()));
		WeaponRegistry.register(def_rockets);
		WeaponDef def_gravy_railgun = new WeaponDef("gravy_railgun", "Грави-Рельса", "primary", 0f, 0.3f, 0f, 0f, 0f, 18000f, 0f, DamageClass.EXOTIC, "gravy");
		GRAVY_RAILGUN = register("weapon_d6_gravy_railgun", new DescentWeaponItem(def_gravy_railgun, new Item.Settings()));
		WeaponRegistry.register(def_gravy_railgun);
		WeaponDef def_vulcan = new WeaponDef("vulcan", "Вулкан", "secondary", 2f, 0.065f, 15f, 0f, 0f, 4800f, 8f, DamageClass.KINETIC, "vulcan");
		VULCAN = register("weapon_d6_vulcan", new DescentWeaponItem(def_vulcan, new Item.Settings()));
		WeaponRegistry.register(def_vulcan);
		WeaponDef def_flak = new WeaponDef("flak", "Флак-пушка", "secondary", 14f, 0.7f, 12f, 16f, 90f, 3500f, 40f, DamageClass.EXPLOSIVE, "flak");
		FLAK = register("weapon_d6_flak", new DescentWeaponItem(def_flak, new Item.Settings()));
		WeaponRegistry.register(def_flak);
		WeaponDef def_homing = new WeaponDef("homing", "ГСН-ракета", "heavy", 0f, 3.5f, 90f, 40f, 160f, 1400f, 80f, DamageClass.EXPLOSIVE, "homing");
		HOMING = register("weapon_d6_homing", new DescentWeaponItem(def_homing, new Item.Settings()));
		WeaponRegistry.register(def_homing);
		WeaponDef def_concussion = new WeaponDef("concussion", "КС-ракета", "heavy", 0f, 2.0f, 120f, 90f, 280f, 1600f, 200f, DamageClass.EXPLOSIVE, "basic");
		CONCUSSION = register("weapon_d6_concussion", new DescentWeaponItem(def_concussion, new Item.Settings()));
		WeaponRegistry.register(def_concussion);
		WeaponDef def_smart_missile = new WeaponDef("smart_missile", "Умная-ракета", "heavy", 0f, 4.0f, 110f, 60f, 200f, 1600f, 100f, DamageClass.EXPLOSIVE, "homing");
		SMART_MISSILE = register("weapon_d6_smart_missile", new DescentWeaponItem(def_smart_missile, new Item.Settings()));
		WeaponRegistry.register(def_smart_missile);
		WeaponDef def_mega_missile = new WeaponDef("mega_missile", "Мега-ракета", "heavy", 0f, 8.0f, 350f, 200f, 500f, 800f, 500f, DamageClass.EXPLOSIVE, "basic");
		MEGA_MISSILE = register("weapon_d6_mega_missile", new DescentWeaponItem(def_mega_missile, new Item.Settings()));
		WeaponRegistry.register(def_mega_missile);
		WeaponDef def_quad_laser = new WeaponDef("quad_laser", "Quad-лазер", "secondary", 12f, 0.3f, 30f, 0f, 0f, 0f, 20f, DamageClass.ENERGY, "quad_laser");
		QUAD_LASER = register("weapon_d6_quad_laser", new DescentWeaponItem(def_quad_laser, new Item.Settings()));
		WeaponRegistry.register(def_quad_laser);
		WeaponDef def_railmk2 = new WeaponDef("railmk2", "Рельса МК2", "heavy", 18f, 0.6f, 120f, 0f, 0f, 8000f, 80f, DamageClass.KINETIC, "rail");
		RAILMK2 = register("weapon_d6_railmk2", new DescentWeaponItem(def_railmk2, new Item.Settings()));
		WeaponRegistry.register(def_railmk2);
		WeaponDef def_bfg = new WeaponDef("bfg", "BFG-излучатель", "heavy", 80f, 10.0f, 300f, 150f, 600f, 400f, 200f, DamageClass.EXOTIC, "bfg");
		BFG = register("weapon_d6_bfg", new DescentWeaponItem(def_bfg, new Item.Settings()));
		WeaponRegistry.register(def_bfg);
		WeaponDef def_frag = new WeaponDef("frag", "Фраг-пускатель", "secondary", 24f, 1.5f, 60f, 50f, 180f, 2000f, 60f, DamageClass.EXPLOSIVE, "frag");
		FRAG = register("weapon_d6_frag", new DescentWeaponItem(def_frag, new Item.Settings()));
		WeaponRegistry.register(def_frag);
		WeaponDef def_overdrive = new WeaponDef("overdrive", "Овердрайв-луч", "utility", 3f, 0.08f, 25f, 0f, 0f, 0f, 5f, DamageClass.ENERGY, "beam");
		OVERDRIVE = register("weapon_d6_overdrive", new DescentWeaponItem(def_overdrive, new Item.Settings()));
		WeaponRegistry.register(def_overdrive);
		WeaponDef def_shockwave = new WeaponDef("shockwave", "Энерговолна", "utility", 40f, 3.0f, 20f, 80f, 700f, 0f, 0f, DamageClass.ENERGY, "shockwave");
		SHOCKWAVE = register("weapon_d6_shockwave", new DescentWeaponItem(def_shockwave, new Item.Settings()));
		WeaponRegistry.register(def_shockwave);
		WeaponDef def_darklance = new WeaponDef("darklance", "Копьё тьмы", "heavy", 70f, 5.0f, 250f, 0f, 0f, 0f, 50f, DamageClass.EXOTIC, "darklance");
		DARKLANCE = register("weapon_d6_darklance", new DescentWeaponItem(def_darklance, new Item.Settings()));
		WeaponRegistry.register(def_darklance);
		WeaponDef def_darkfield = new WeaponDef("darkfield", "Поле тьмы", "utility", 45f, 4.0f, 18f, 0f, 400f, 800f, 0f, DamageClass.EXOTIC, "deploy");
		DARKFIELD = register("weapon_d6_darkfield", new DescentWeaponItem(def_darkfield, new Item.Settings()));
		WeaponRegistry.register(def_darkfield);
		WeaponDef def_energytrap = new WeaponDef("energytrap", "Энерго-капкан", "utility", 22f, 1.2f, 6f, 0f, 150f, 1200f, 0f, DamageClass.ENERGY, "deploy");
		ENERGYTRAP = register("weapon_d6_energytrap", new DescentWeaponItem(def_energytrap, new Item.Settings()));
		WeaponRegistry.register(def_energytrap);
		WeaponDef def_gravmine = new WeaponDef("gravmine", "Грави-мина", "utility", 30f, 1.5f, 90f, 0f, 300f, 900f, 0f, DamageClass.EXOTIC, "deploy");
		GRAVMINE = register("weapon_d6_gravmine", new DescentWeaponItem(def_gravmine, new Item.Settings()));
		WeaponRegistry.register(def_gravmine);
		WeaponDef def_plasmamine = new WeaponDef("plasmamine", "Плазма-мина", "utility", 25f, 1.0f, 120f, 100f, 260f, 900f, 0f, DamageClass.EXOTIC, "deploy");
		PLASMAMINE = register("weapon_d6_plasmamine", new DescentWeaponItem(def_plasmamine, new Item.Settings()));
		WeaponRegistry.register(def_plasmamine);
		WeaponDef def_reactor = new WeaponDef("reactor", "Сброс реактора", "utility", 30f, 15.0f, 300f, 300f, 600f, 600f, 0f, DamageClass.EXPLOSIVE, "reactor");
		REACTOR = register("weapon_d6_reactor", new DescentWeaponItem(def_reactor, new Item.Settings()));
		WeaponRegistry.register(def_reactor);
		WeaponDef def_warp = new WeaponDef("warp", "Боевой варп", "utility", 30f, 2.5f, 100f, 0f, 200f, 0f, 0f, DamageClass.EXOTIC, "warp");
		WARP = register("weapon_d6_warp", new DescentWeaponItem(def_warp, new Item.Settings()));
		WeaponRegistry.register(def_warp);
		WeaponDef def_telefrag = new WeaponDef("telefrag", "Телефраг", "utility", 50f, 5.0f, 1000f, 0f, 140f, 0f, 0f, DamageClass.EXOTIC, "telefrag");
		TELEFRAG = register("weapon_d6_telefrag", new DescentWeaponItem(def_telefrag, new Item.Settings()));
		WeaponRegistry.register(def_telefrag);
		WeaponDef def_whiplash = new WeaponDef("whiplash", "Хлыст", "utility", 20f, 3.0f, 80f, 0f, 160f, 0f, 0f, DamageClass.KINETIC, "whiplash");
		WHIPLASH = register("weapon_d6_whiplash", new DescentWeaponItem(def_whiplash, new Item.Settings()));
		WeaponRegistry.register(def_whiplash);

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

		EGG_MEGA_WORM = register("spawn_egg_mega_worm", new net.minecraft.item.SpawnEggItem(
				com.terminaldetector.drmd.entity.ModEntities.MEGA_WORM, 0x6B3A2A, 0xE8C070, new Item.Settings()));
		EGG_DRONE_SWARM = register("spawn_egg_drone_swarm", new com.terminaldetector.drmd.entity.SimpleSpawnItem(
				com.terminaldetector.drmd.entity.ModEntities.DRONE_SWARM, 10.0, new Item.Settings()));
		EGG_REACTOR_KEEPER = register("spawn_egg_reactor_keeper", new net.minecraft.item.SpawnEggItem(
				com.terminaldetector.drmd.entity.ModEntities.REACTOR_KEEPER, 0x2A1840, 0xFF55AA, new Item.Settings()));
		EGG_SKY_UFO = register("spawn_egg_sky_ufo", new com.terminaldetector.drmd.entity.SimpleSpawnItem(
				com.terminaldetector.drmd.entity.ModEntities.SKY_UFO, 12.0, new Item.Settings()));
		EGG_AIR_MINE = register("spawn_egg_air_mine", new net.minecraft.item.SpawnEggItem(
				com.terminaldetector.drmd.entity.ModEntities.AIR_MINE, 0x442222, 0xFF4422, new Item.Settings()));

		SessionControlItems.register();

		Registry.register(Registries.ITEM_GROUP, GROUP_KEY, FabricItemGroup.builder()
				.icon(() -> new ItemStack(SessionControlItems.REACTOR_STARTER))
				.displayName(Text.translatable("itemGroup.drmd.weapons"))
				.entries((ctx, entries) -> {
					// Session — enable / start without console
					entries.add(SessionControlItems.REACTOR_STARTER);
					entries.add(SessionControlItems.SIXDOF_CORE);
					entries.add(SessionControlItems.STARTER_KIT);
					entries.add(SessionControlItems.LEVEL_LIFT);
					entries.add(SessionControlItems.CONSTRUCTION_PAD);
					entries.add(SessionControlItems.ENERGY_DIAL);
					entries.add(PYRO_GX);
					entries.add(ALLOY_PLATE);
					entries.add(ENERGY_CELL);
					entries.add(TARGETING_CORE);
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
					entries.add(EGG_MEGA_WORM);
					entries.add(EGG_DRONE_SWARM);
					entries.add(EGG_REACTOR_KEEPER);
					entries.add(EGG_SKY_UFO);
					entries.add(EGG_AIR_MINE);
					entries.add(BOMB_TNT);
					entries.add(BOMB_CLUSTER);
					entries.add(BOMB_INCENDIARY);
					entries.add(BOMB_GUIDED);
					entries.add(LASER_DESIGNATOR);
					entries.add(MG);
					entries.add(PLASMA);
					entries.add(HEAVY);
					entries.add(LASER);
					entries.add(ROCKETS);
					entries.add(GRAVY_RAILGUN);
					entries.add(VULCAN);
					entries.add(FLAK);
					entries.add(HOMING);
					entries.add(CONCUSSION);
					entries.add(SMART_MISSILE);
					entries.add(MEGA_MISSILE);
					entries.add(QUAD_LASER);
					entries.add(RAILMK2);
					entries.add(BFG);
					entries.add(FRAG);
					entries.add(OVERDRIVE);
					entries.add(SHOCKWAVE);
					entries.add(DARKLANCE);
					entries.add(DARKFIELD);
					entries.add(ENERGYTRAP);
					entries.add(GRAVMINE);
					entries.add(PLASMAMINE);
					entries.add(REACTOR);
					entries.add(WARP);
					entries.add(TELEFRAG);
					entries.add(WHIPLASH);
				})
				.build());

		DescentMod.LOGGER.info("Registered DRMD weapons + session tools + Pyro GX + drones + aerial ordnance");
	}

	private static Item egg(String id, com.terminaldetector.drmd.ai.AiRole role, int primary, int secondary) {
		return register(id, new com.terminaldetector.drmd.entity.DroneSpawnEggItem(role, primary, secondary, new Item.Settings()));
	}
}
