package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.weapon.projectile.FuseType;
import com.terminaldetector.drmd.weapon.projectile.ProjectileFramework;
import com.terminaldetector.drmd.weapon.projectile.ProjectileKind;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Four air-hanging proximity mines (Descent-style). No gravity wells.
 */
public final class DescentMineFire {
	public enum Kind {
		PROX(90f, 220f, 3.2f, 0xFF8866, 25f),
		PLASMA(120f, 260f, 3.6f, 0xAA66FF, 28f),
		ENERGY(70f, 200f, 4.0f, 0x44FFAA, 22f),
		SMART(140f, 300f, 4.5f, 0x66CCFF, 35f);

		public final float splash;
		public final float splashR;
		public final float proxRadius;
		public final int color;
		public final float energy;

		Kind(float splash, float splashR, float proxRadius, int color, float energy) {
			this.splash = splash;
			this.splashR = splashR;
			this.proxRadius = proxRadius;
			this.color = color;
			this.energy = energy;
		}

		public static Kind fromBehavior(String behavior) {
			return switch (behavior) {
				case "mine_plasma", "plasmamine" -> PLASMA;
				case "mine_energy", "energytrap" -> ENERGY;
				case "mine_smart", "darkfield" -> SMART;
				default -> PROX;
			};
		}
	}

	private DescentMineFire() {}

	public static boolean fire(PlayerEntity user, Kind kind) {
		if (user.getWorld().isClient) return false;
		Vec3d pos = WeaponCore.muzzle(user, 0.6f, 0f, -0.2f);
		Vec3d dir = WeaponCore.aimDir(user);
		WeaponCore.FireConfig cfg = ProjectileFramework.config(ProjectileKind.PROXIMITY_MINE, user, pos, dir);
		cfg.speed = 700;
		cfg.directDamage = 0;
		cfg.splashDamage = kind.splash;
		cfg.splashRadius = kind.splashR;
		cfg.life = 90f;
		cfg.fuse = FuseType.PROXIMITY;
		cfg.armSeconds = 0.7f;
		cfg.proximityRadius = kind.proxRadius;
		cfg.gravity = 0f; // hang in air — no drop, no gravity mine pull
		cfg.drag = 0.12f;
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_MINE;
		cfg.visualScale = 1.15f;
		cfg.worldBlast = true;
		cfg.colorR = (kind.color >> 16) & 0xFF;
		cfg.colorG = (kind.color >> 8) & 0xFF;
		cfg.colorB = kind.color & 0xFF;
		cfg.recoil = 8f;
		cfg.dmgClass = DamageClass.EXPLOSIVE;
		WeaponCore.fireProjectile(cfg);
		return true;
	}
}
