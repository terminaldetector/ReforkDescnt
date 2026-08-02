package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.weapon.projectile.ProjectileFramework;
import com.terminaldetector.drmd.weapon.projectile.ProjectileKind;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Descent secondary rockets — six warhead weights.
 */
public final class DescentRocketFire {
	public enum Kind {
		/** 1 — light concussion dumbfire */
		LIGHT(1, 70f, 55f, 220f, 3000f, 1.0f, false, 18f, 8f),
		/** 2 — offensive guided */
		OFFENSE(1, 110f, 85f, 280f, 2400f, 1.15f, true, 22f, 12f),
		/** 3 — dual volley */
		DUAL(2, 95f, 80f, 300f, 2600f, 0.95f, false, 28f, 16f),
		/** 4 — triple volley */
		TRIPLE(3, 90f, 75f, 300f, 2500f, 0.85f, false, 36f, 20f),
		/** 5 — heavy dumbfire */
		HEAVY(1, 180f, 160f, 480f, 2000f, 1.45f, false, 45f, 28f),
		/** 6 — mega */
		MEGA(1, 420f, 360f, 720f, 1600f, 1.9f, false, 70f, 55f);

		public final int count;
		public final float direct;
		public final float splash;
		public final float splashR;
		public final float speed;
		public final float scale;
		public final boolean homing;
		public final float energy;
		public final float recoil;

		Kind(int count, float direct, float splash, float splashR, float speed,
			 float scale, boolean homing, float energy, float recoil) {
			this.count = count;
			this.direct = direct;
			this.splash = splash;
			this.splashR = splashR;
			this.speed = speed;
			this.scale = scale;
			this.homing = homing;
			this.energy = energy;
			this.recoil = recoil;
		}

		public static Kind fromBehavior(String behavior) {
			return switch (behavior) {
				case "rocket_offense", "homing" -> OFFENSE;
				case "rocket_dual", "rockets" -> DUAL;
				case "rocket_triple" -> TRIPLE;
				case "rocket_heavy" -> HEAVY;
				case "rocket_mega", "mega_missile" -> MEGA;
				default -> LIGHT;
			};
		}
	}

	private DescentRocketFire() {}

	public static boolean fire(PlayerEntity user, Kind kind) {
		if (user.getWorld().isClient) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		for (int i = 0; i < kind.count; i++) {
			Vec3d dir = kind.count > 1 ? spread(aim, 4.5f + i, user) : aim;
			var muzzles = WeaponCore.allMuzzles(user, "rockets");
			Vec3d pos = muzzles.isEmpty()
					? WeaponCore.muzzle(user, 0.9f, (i % 2 == 0 ? -1 : 1) * 0.2f, -0.1f)
					: muzzles.get(Math.min(i, muzzles.size() - 1));
			WeaponCore.FireConfig cfg = ProjectileFramework.config(ProjectileKind.ROCKET, user, pos, dir);
			cfg.speed = kind.speed;
			cfg.directDamage = kind.direct;
			cfg.splashDamage = kind.splash;
			cfg.splashRadius = kind.splashR;
			cfg.homing = kind.homing;
			cfg.turnRate = kind.homing ? 140f : 0f;
			cfg.life = 6.5f;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
			cfg.visualScale = kind.scale;
			cfg.worldBlast = true;
			cfg.recoil = i == 0 ? kind.recoil : 0f;
			cfg.dmgClass = DamageClass.EXPLOSIVE;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	private static Vec3d spread(Vec3d aim, float deg, LivingEntity user) {
		double yaw = Math.toRadians((user.getRandom().nextDouble() - 0.5) * 2 * deg);
		double pitch = Math.toRadians((user.getRandom().nextDouble() - 0.5) * 2 * deg);
		Vec3d f = aim.normalize();
		Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
		right = right.normalize();
		Vec3d up = right.crossProduct(f).normalize();
		return f.add(right.multiply(Math.sin(yaw))).add(up.multiply(Math.sin(pitch))).normalize();
	}
}
