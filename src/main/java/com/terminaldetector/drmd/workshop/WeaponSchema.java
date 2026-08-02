package com.terminaldetector.drmd.workshop;

import java.util.List;

/**
 * Schema layer — port of SCK schema.lua (weapon / projectile / flak / guidance).
 */
public final class WeaponSchema {
	public enum FieldType { INT, FLOAT, STRING, BOOL, ENUM }

	public record Field(String key, String label, FieldType type, Object defaultValue,
						float min, float max, List<String> choices, String section) {}

	public static final List<Field> WEAPON = List.of(
			f("PrintName", "Name", FieldType.STRING, "Custom Weapon", 0, 0, null, "Identity"),
			f("ClassName", "Class", FieldType.STRING, "weapon_d6_custom", 0, 0, null, "Identity"),
			f("Category", "Category", FieldType.ENUM, "Primary", 0, 0,
					List.of("Primary", "Secondary", "Heavy", "Utility"), "Identity"),
			f("WeaponType", "Type", FieldType.ENUM, "projectile", 0, 0,
					List.of("projectile", "hitscan", "beam", "deployable"), "Identity"),
			f("FireRate", "Fire Rate", FieldType.FLOAT, 0.5f, 0.01f, 10f, null, "Combat"),
			f("Damage", "Damage", FieldType.FLOAT, 50f, 0, 2000f, null, "Combat"),
			f("SplashRadius", "Splash Radius", FieldType.FLOAT, 0f, 0, 1000f, null, "Combat"),
			f("EnergyCost", "Energy Cost", FieldType.FLOAT, 10f, 0, 100f, null, "Combat"),
			f("MaxAmmo", "Max Ammo", FieldType.INT, 0, 0, 999, null, "Combat"),
			f("Weight", "Weight", FieldType.FLOAT, 1f, 0, 50f, null, "Misc"),
			f("AIPriority", "AI Priority", FieldType.FLOAT, 1f, 0, 10f, null, "Misc")
	);

	public static final List<Field> PROJECTILE = List.of(
			f("Model", "Model", FieldType.STRING, "default", 0, 0, null, "Appearance"),
			f("Scale", "Scale", FieldType.FLOAT, 1f, 0.1f, 10f, null, "Appearance"),
			f("Mass", "Mass", FieldType.FLOAT, 1f, 0, 100f, null, "Physics"),
			f("Gravity", "Gravity", FieldType.FLOAT, 0f, -2f, 2f, null, "Physics"),
			f("Drag", "Drag", FieldType.FLOAT, 0f, 0, 5f, null, "Physics"),
			f("InitialVelocity", "Velocity", FieldType.FLOAT, 2000f, 0, 20000f, null, "Physics"),
			f("Lifetime", "Lifetime", FieldType.FLOAT, 5f, 0.1f, 60f, null, "Physics"),
			f("Homing", "Homing", FieldType.BOOL, false, 0, 0, null, "Homing"),
			f("TurnRate", "Turn Rate", FieldType.FLOAT, 90f, 0, 720f, null, "Homing"),
			f("ExplosionRadius", "Explosion R", FieldType.FLOAT, 0f, 0, 1000f, null, "Explosion"),
			f("ExplosionDamage", "Explosion Dmg", FieldType.FLOAT, 0f, 0, 1000f, null, "Explosion"),
			f("PierceCount", "Pierce", FieldType.INT, 0, 0, 20, null, "Advanced"),
			f("Ricochet", "Ricochet", FieldType.BOOL, false, 0, 0, null, "Advanced")
	);

	public static final List<Field> FLAK = List.of(
			f("FragmentCount", "Fragments", FieldType.INT, 0, 0, 64, null, "Flak"),
			f("SpreadAngle", "Spread°", FieldType.FLOAT, 7f, 0, 180f, null, "Flak"),
			f("Randomness", "Randomness", FieldType.FLOAT, 0.2f, 0, 1f, null, "Flak"),
			f("FragmentType", "Frag Type", FieldType.ENUM, "kinetic", 0, 0,
					List.of("kinetic", "explosive", "energy", "exotic"), "Flak"),
			f("FragmentSpeed", "Frag Speed", FieldType.FLOAT, 1200f, 0, 8000f, null, "Flak"),
			f("FragmentDamage", "Frag Damage", FieldType.FLOAT, 12f, 0, 500f, null, "Flak"),
			f("DetonateTime", "Detonate Time", FieldType.FLOAT, 0f, 0, 10f, null, "Detonation"),
			f("DetonateDistance", "Detonate Dist", FieldType.FLOAT, 0f, 0, 2000f, null, "Detonation")
	);

	public static final List<Field> GUIDANCE = List.of(
			f("GuidanceType", "Type", FieldType.ENUM, "none", 0, 0,
					List.of("none", "heat", "target", "beam", "lead"), "Guidance"),
			f("LockTime", "Lock Time", FieldType.FLOAT, 0.5f, 0, 5f, null, "Guidance"),
			f("TrackingCone", "Cone°", FieldType.FLOAT, 30f, 0, 180f, null, "Guidance"),
			f("MaxAngularVelocity", "Max Turn", FieldType.FLOAT, 180f, 0, 720f, null, "Guidance"),
			f("LeadPrediction", "Lead Pred.", FieldType.BOOL, false, 0, 0, null, "Prediction"),
			f("PredictionStrength", "Pred. Strength", FieldType.FLOAT, 1f, 0, 3f, null, "Prediction"),
			f("TargetMemory", "Target Memory", FieldType.BOOL, true, 0, 0, null, "Memory"),
			f("LostTargetTimeout", "Lost Timeout", FieldType.FLOAT, 2f, 0, 10f, null, "Memory")
	);

	private static Field f(String key, String label, FieldType type, Object def,
						   float min, float max, List<String> choices, String section) {
		return new Field(key, label, type, def, min, max, choices, section);
	}

	private WeaponSchema() {}
}
