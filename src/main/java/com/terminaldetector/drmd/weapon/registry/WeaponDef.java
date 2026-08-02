package com.terminaldetector.drmd.weapon.registry;

import com.terminaldetector.drmd.weapon.core.DamageClass;

public class WeaponDef {
	public final String id;
	public final String displayName;
	public final String category;
	public final float energyCost;
	public final float fireRate;
	public final float damage;
	public final float splashDamage;
	public final float splashRadius;
	public final float speed;
	public final float recoil;
	public final DamageClass dmgClass;
	public final String behavior;

	public WeaponDef(String id, String displayName, String category, float energyCost, float fireRate,
					 float damage, float splashDamage, float splashRadius, float speed, float recoil,
					 DamageClass dmgClass, String behavior) {
		this.id = id;
		this.displayName = displayName;
		this.category = category;
		this.energyCost = energyCost;
		this.fireRate = fireRate;
		this.damage = damage;
		this.splashDamage = splashDamage;
		this.splashRadius = splashRadius;
		this.speed = speed;
		this.recoil = recoil;
		this.dmgClass = dmgClass;
		this.behavior = behavior;
	}
}
