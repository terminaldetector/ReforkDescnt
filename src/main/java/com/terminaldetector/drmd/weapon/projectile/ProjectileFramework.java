package com.terminaldetector.drmd.weapon.projectile;

import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Factory + shared hit behaviours for all ProjectileKind values.
 */
public final class ProjectileFramework {
	private ProjectileFramework() {}

	public static WeaponCore.FireConfig config(ProjectileKind kind, LivingEntity owner, Vec3d pos, Vec3d dir) {
		WeaponCore.FireConfig cfg = new WeaponCore.FireConfig();
		cfg.owner = owner;
		cfg.pos = pos;
		cfg.dir = dir;
		cfg.dmgClass = kind.damageClass;
		cfg.gravity = kind.gravity;
		cfg.drag = kind.drag;
		cfg.homing = kind.defaultHoming;
		cfg.colorR = (kind.colorRgb >> 16) & 0xFF;
		cfg.colorG = (kind.colorRgb >> 8) & 0xFF;
		cfg.colorB = kind.colorRgb & 0xFF;
		cfg.onHit = ctx -> onHit(kind, ctx);
		switch (kind) {
			case LASER -> {
				cfg.speed = 8000;
				cfg.directDamage = 18;
				cfg.life = 1.2f;
				cfg.pierceCount = 2;
				// Combat laser is a burner: what it punches through catches fire.
				cfg.igniteOnHit = true;
			}
			case BURN_LANCE -> {
				cfg.speed = 4200;
				cfg.directDamage = 24;
				cfg.life = 2.2f;
				cfg.pierceCount = 1;
				cfg.igniteOnHit = true;
				cfg.visualScale = 1.15f;
			}
			case PROXIMITY_MINE -> {
				cfg.speed = 900;
				cfg.directDamage = 0;
				cfg.splashDamage = 70;
				cfg.splashRadius = 220;
				cfg.life = 45f;
				cfg.fuse = FuseType.PROXIMITY;
				cfg.armSeconds = 0.8f;
				cfg.proximityRadius = 3.5f;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_MINE;
				cfg.visualScale = 1.1f;
				cfg.worldBlast = true;
			}
			case AIRBURST -> {
				cfg.speed = 3400;
				cfg.directDamage = 12;
				cfg.splashDamage = 45;
				cfg.splashRadius = 160;
				cfg.life = 4f;
				cfg.fuse = FuseType.TIMED;
				cfg.fuseSeconds = 0.75f;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
				cfg.visualScale = 0.9f;
			}
			case PLASMA -> {
				cfg.speed = 3200;
				cfg.directDamage = 40;
				cfg.splashDamage = 20;
				cfg.splashRadius = 100;
				cfg.life = 3f;
			}
			case KINETIC -> {
				cfg.speed = 5000;
				cfg.directDamage = 28;
				cfg.life = 4f;
			}
			case DRILL_CHARGE -> {
				cfg.speed = 2800;
				cfg.directDamage = 25;
				cfg.life = 3f;
				cfg.drillCarve = true;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_DRILL;
				cfg.visualScale = 1.05f;
			}
			case ROCKET -> {
				cfg.speed = 2200;
				cfg.directDamage = 60;
				cfg.splashDamage = 80;
				cfg.splashRadius = 180;
				cfg.life = 5f;
				cfg.turnRate = 70f;
				cfg.worldBlast = true;
				// Arms clear of the launcher so a point-blank shot does not kill the shooter.
				cfg.fuse = FuseType.DELAYED_IMPACT;
				cfg.armSeconds = 0.12f;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
				cfg.visualScale = 1.2f;
			}
			case ENERGY_ORB -> {
				cfg.speed = 2600;
				cfg.directDamage = 35;
				cfg.splashDamage = 15;
				cfg.splashRadius = 90;
				cfg.life = 3.5f;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
				cfg.visualScale = 1.3f;
				cfg.worldBlast = true;
			}
			case GRAVITY_SPHERE -> {
				cfg.speed = 1800;
				cfg.directDamage = 10;
				cfg.splashDamage = 5;
				cfg.splashRadius = 140;
				cfg.life = 4f;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
				cfg.visualScale = 1.4f;
			}
		}
		return cfg;
	}

	public static void fire(ProjectileKind kind, LivingEntity owner, Vec3d pos, Vec3d dir) {
		WeaponCore.fireProjectile(config(kind, owner, pos, dir));
	}

	private static void onHit(ProjectileKind kind, WeaponCore.HitContext ctx) {
		if (!(ctx.projectile().getWorld() instanceof ServerWorld sw)) return;
		switch (kind) {
			case GRAVITY_SPHERE -> {
				// Pull nearby entities toward impact
				Vec3d center = ctx.hitPos();
				for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class,
						new net.minecraft.util.math.Box(center, center).expand(4), LivingEntity::isAlive)) {
					Vec3d pull = center.subtract(e.getPos()).multiply(0.15);
					e.addVelocity(pull.x, pull.y, pull.z);
					e.velocityModified = true;
				}
			}
			case DRILL_CHARGE -> {
				if (ctx.isBlock()) {
					WeaponFx.drillCarve(sw, BlockPos.ofFloored(ctx.hitPos()),
							ctx.projectile().getOwnerLiving());
				}
			}
			case ENERGY_ORB -> {
				LivingEntity own = ctx.projectile().getOwnerLiving();
				if (own != null) {
					WeaponFx.explode(own, sw, ctx.hitPos(), 35f, 2.2f, kind.damageClass, false);
				}
			}
			case ROCKET -> {
				LivingEntity own = ctx.projectile().getOwnerLiving();
				if (own != null && ctx.projectile().getDamageClass() != com.terminaldetector.drmd.weapon.core.DamageClass.EXPLOSIVE) {
					WeaponFx.explode(own, sw, ctx.hitPos(), 80f, 2.5f, kind.damageClass, true);
				}
			}
			case AIRBURST -> {
				// Shrapnel cone: short kinetic slivers thrown out of the burst point.
				LivingEntity own = ctx.projectile().getOwnerLiving();
				Vec3d at = ctx.hitPos();
				if (own != null) {
					for (int i = 0; i < 8; i++) {
						Vec3d spread = new Vec3d(
								sw.getRandom().nextDouble() - 0.5,
								sw.getRandom().nextDouble() - 0.5,
								sw.getRandom().nextDouble() - 0.5);
						if (spread.lengthSquared() < 1e-6) continue;
						WeaponCore.FireConfig frag = config(ProjectileKind.KINETIC, own, at, spread.normalize());
						frag.directDamage = 9;
						frag.life = 0.7f;
						frag.speed = 2600;
						frag.visualScale = 0.6f;
						frag.onHit = null;
						WeaponCore.fireProjectile(frag);
					}
				}
				com.terminaldetector.drmd.world.smoke.SmokeSystem.emitExplosion(at, 1.4f);
			}
			case PROXIMITY_MINE -> {
				LivingEntity own = ctx.projectile().getOwnerLiving();
				if (own != null) {
					WeaponFx.explode(own, sw, ctx.hitPos(), 70f, 3.0f, kind.damageClass, true);
				}
				com.terminaldetector.drmd.world.fire.FireSystem.igniteBlast(
						sw, BlockPos.ofFloored(ctx.hitPos()), 3, 2);
			}
			case BURN_LANCE -> com.terminaldetector.drmd.world.fire.FireSystem.igniteBlast(
					sw, BlockPos.ofFloored(ctx.hitPos()), 2, 1);
			default -> {}
		}
	}

	/** Map legacy weapon behavior strings onto kinds where possible. */
	public static ProjectileKind fromBehavior(String behavior) {
		if (behavior == null) return ProjectileKind.KINETIC;
		return switch (behavior) {
			case "laser", "quad_laser", "beam", "overdrive" -> ProjectileKind.LASER;
			case "plasma" -> ProjectileKind.PLASMA;
			case "rockets", "homing", "smart_missile", "mega_missile", "concussion" -> ProjectileKind.ROCKET;
			case "gravy" -> ProjectileKind.GRAVITY_SPHERE;
			case "bfg", "darklance", "fusion" -> ProjectileKind.ENERGY_ORB;
			case "deploy" -> ProjectileKind.PROXIMITY_MINE;
			case "flak" -> ProjectileKind.AIRBURST;
			case "frag" -> ProjectileKind.DRILL_CHARGE;
			default -> ProjectileKind.KINETIC;
		};
	}
}
