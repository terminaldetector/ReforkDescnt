package com.terminaldetector.drmd.workshop;

import java.util.LinkedHashMap;
import java.util.Map;

/** Config registry entry — port of SCK config.lua */
public class WeaponConfig {
	public final Map<String, Object> weapon = new LinkedHashMap<>();
	public final Map<String, Object> projectile = new LinkedHashMap<>();
	public final Map<String, Object> flak = new LinkedHashMap<>();
	public final Map<String, Object> guidance = new LinkedHashMap<>();

	public WeaponConfig() {
		for (WeaponSchema.Field f : WeaponSchema.WEAPON) weapon.put(f.key(), f.defaultValue());
		for (WeaponSchema.Field f : WeaponSchema.PROJECTILE) projectile.put(f.key(), f.defaultValue());
		for (WeaponSchema.Field f : WeaponSchema.FLAK) flak.put(f.key(), f.defaultValue());
		for (WeaponSchema.Field f : WeaponSchema.GUIDANCE) guidance.put(f.key(), f.defaultValue());
	}

	public static WeaponConfig template(String name) {
		WeaponConfig c = new WeaponConfig();
		switch (name.toLowerCase()) {
			case "cannon" -> {
				c.weapon.put("PrintName", "Cannon");
				c.weapon.put("FireRate", 0.35f);
				c.weapon.put("Damage", 55f);
				c.weapon.put("EnergyCost", 6f);
				c.projectile.put("InitialVelocity", 4500f);
			}
			case "missile" -> {
				c.weapon.put("PrintName", "Missile");
				c.weapon.put("FireRate", 1.2f);
				c.weapon.put("Damage", 90f);
				c.weapon.put("EnergyCost", 18f);
				c.weapon.put("SplashRadius", 160f);
				c.projectile.put("InitialVelocity", 2200f);
				c.projectile.put("Homing", true);
				c.guidance.put("GuidanceType", "target");
			}
			case "flak" -> {
				c.weapon.put("PrintName", "Flak");
				c.weapon.put("FireRate", 0.7f);
				c.weapon.put("Damage", 12f);
				c.weapon.put("EnergyCost", 14f);
				c.flak.put("FragmentCount", 12);
				c.flak.put("SpreadAngle", 7f);
			}
			case "seeker" -> {
				c.weapon.put("PrintName", "Seeker");
				c.weapon.put("FireRate", 3.0f);
				c.weapon.put("Damage", 110f);
				c.weapon.put("EnergyCost", 0f);
				c.projectile.put("Homing", true);
				c.projectile.put("Lifetime", 15f);
				c.guidance.put("GuidanceType", "heat");
				c.guidance.put("LeadPrediction", true);
			}
			default -> c.weapon.put("PrintName", "Custom");
		}
		return c;
	}
}
