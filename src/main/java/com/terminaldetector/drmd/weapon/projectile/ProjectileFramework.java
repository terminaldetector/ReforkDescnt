package com.terminaldetector.drmd.weapon.projectile;

import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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
			case ROCKET -> {
				cfg.speed = 2200;
				cfg.directDamage = 60;
				cfg.splashDamage = 80;
				cfg.splashRadius = 180;
				cfg.life = 5f;
				cfg.turnRate = 70f;
			}
			case GRAVITY_SPHERE -> {
				cfg.speed = 1800;
				cfg.directDamage = 10;
				cfg.splashDamage = 5;
				cfg.splashRadius = 140;
				cfg.life = 4f;
			}
			case ENERGY_ORB -> {
				cfg.speed = 2600;
				cfg.directDamage = 35;
				cfg.splashDamage = 15;
				cfg.splashRadius = 90;
				cfg.life = 3.5f;
			}
			case DRILL_CHARGE -> {
				cfg.speed = 2800;
				cfg.directDamage = 25;
				cfg.life = 3f;
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
					BlockPos bp = BlockPos.ofFloored(ctx.hitPos());
					for (BlockPos p : BlockPos.iterate(bp.add(-1, -1, -1), bp.add(1, 1, 1))) {
						if (sw.getBlockState(p).getHardness(sw, p) >= 0
								&& sw.getBlockState(p).getHardness(sw, p) < 20
								&& !sw.getBlockState(p).isOf(Blocks.BEDROCK)) {
							sw.breakBlock(p, true);
						}
					}
				}
			}
			case ENERGY_ORB -> {
				sw.createExplosion(ctx.projectile(), ctx.hitPos().x, ctx.hitPos().y, ctx.hitPos().z,
						1.2f, false, net.minecraft.world.World.ExplosionSourceType.NONE);
			}
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
			case "bfg", "darklance" -> ProjectileKind.ENERGY_ORB;
			case "frag", "flak" -> ProjectileKind.DRILL_CHARGE;
			default -> ProjectileKind.KINETIC;
		};
	}
}
