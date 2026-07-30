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

		Registry.register(Registries.ITEM_GROUP, GROUP_KEY, FabricItemGroup.builder()
				.icon(() -> new ItemStack(MG))
				.displayName(Text.translatable("itemGroup.drmd.weapons"))
				.entries((ctx, entries) -> {
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

		DescentMod.LOGGER.info("Registered {} DRMD weapons", 27);
	}
}
