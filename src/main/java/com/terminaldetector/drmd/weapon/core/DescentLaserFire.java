package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.projectile.ProjectileFramework;
import com.terminaldetector.drmd.weapon.projectile.ProjectileKind;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Descent-style combat lasers: dual (or quad) travel-time bolts from wing
 * construction modules, converging on the aim point — not hitscan beams.
 *
 * <p>Laser installations are the workshop modules ({@code SideLeft}/{@code SideRight}
 * barrels). Level 1–4 mirrors classic Descent primary laser powerups.
 */
public final class DescentLaserFire {
	/** Default converge distance when nothing is under the reticle (Source units). */
	public static final float CONVERGE_SU = 2800f;
	/** Max aim-point probe (Source units). */
	public static final float AIM_PROBE_SU = 9000f;

	private DescentLaserFire() {}

	/** Classic Descent primary laser level (1–4), stored on the item stack. */
	public static int levelOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 1;
		var custom = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
		if (custom == null) return 1;
		int lvl = custom.copyNbt().getInt("laserLevel");
		return Math.max(1, Math.min(4, lvl <= 0 ? 1 : lvl));
	}

	public static void setLevel(ItemStack stack, int level) {
		int lvl = Math.max(1, Math.min(4, level));
		net.minecraft.component.type.NbtComponent existing = stack.getOrDefault(
				net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
				net.minecraft.component.type.NbtComponent.DEFAULT);
		net.minecraft.nbt.NbtCompound nbt = existing.copyNbt();
		nbt.putInt("laserLevel", lvl);
		stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
				net.minecraft.component.type.NbtComponent.of(nbt));
	}

	/** Energy cost for one Descent primary volley (both guns). */
	public static float primaryEnergy(int level) {
		return switch (level) {
			case 1 -> 2.5f;
			case 2 -> 3.5f;
			case 3 -> 5.0f;
			default -> 7.0f;
		};
	}

	/** Damage dealt by each bolt at the given level. */
	public static float primaryBoltDamage(int level) {
		return switch (level) {
			case 1 -> 18f;
			case 2 -> 26f;
			case 3 -> 36f;
			default -> 48f;
		};
	}

	/** D1-style bolt colour by level (magenta → super cyan). */
	public static int primaryColor(int level) {
		return switch (level) {
			case 1 -> 0xFF44AA;
			case 2 -> 0xFF66DD;
			case 3 -> 0x8866FF;
			default -> 0x44EEFF;
		};
	}

	/**
	 * Fire converging bolts from combat modules.
	 *
	 * @param barrelCount 2 = classic dual laser, 4 = quad banks
	 * @return true if at least one bolt spawned
	 */
	public static boolean fireModuleBolts(PlayerEntity user, String weaponId, int barrelCount,
										 float damagePerBolt, float splashPerBolt, float splashRadiusSu,
										 float speedSu, float lifeSec, int pierce, int colorRgb,
										 float visualScale, float recoilSu, boolean ignite) {
		if (user.getWorld().isClient) return false;
		List<Vec3d> muzzles = combatMuzzles(user, weaponId, barrelCount);
		if (muzzles.isEmpty()) return false;

		Vec3d aimPoint = resolveAimPoint(user);
		boolean any = false;
		for (int i = 0; i < muzzles.size(); i++) {
			Vec3d pos = muzzles.get(i);
			Vec3d dir = aimPoint.subtract(pos);
			if (dir.lengthSquared() < 1e-8) dir = WeaponCore.aimDir(user);
			else dir = dir.normalize();

			WeaponCore.FireConfig cfg = ProjectileFramework.config(ProjectileKind.LASER, user, pos, dir);
			cfg.speed = speedSu;
			cfg.directDamage = damagePerBolt;
			cfg.splashDamage = splashPerBolt;
			cfg.splashRadius = splashRadiusSu;
			cfg.life = lifeSec;
			cfg.pierceCount = pierce;
			cfg.visualScale = visualScale;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
			cfg.igniteOnHit = ignite;
			cfg.recoil = i == 0 ? recoilSu : 0f;
			cfg.colorR = (colorRgb >> 16) & 0xFF;
			cfg.colorG = (colorRgb >> 8) & 0xFF;
			cfg.colorB = colorRgb & 0xFF;
			// detonate() already applies splash; keep onHit clear of double dips
			cfg.onHit = null;
			if (WeaponCore.fireProjectile(cfg) != null) any = true;
		}
		return any;
	}

	/** Classic dual primary laser volley from wing modules. */
	public static boolean firePrimary(PlayerEntity user, String weaponId, ItemStack stack) {
		int level = levelOf(stack);
		float dmg = primaryBoltDamage(level);
		float splash = 8f + 4f * level;
		float splashR = 70f + 15f * level;
		float speed = 6200f + 400f * level;
		int pierce = level >= 3 ? 1 : 0;
		float scale = 0.95f + 0.12f * level;
		return fireModuleBolts(user, weaponId, 2, dmg, splash, splashR, speed, 1.4f, pierce,
				primaryColor(level), scale, 12f + 4f * level, true);
	}

	/** Quad laser banks — four wing modules, fixed secondary profile. */
	public static boolean fireQuad(PlayerEntity user, String weaponId, float damage, float splash, float splashR) {
		return fireModuleBolts(user, weaponId, 4, damage, splash, splashR, 6500f, 1.3f, 1,
				0x66FFCC, 1.05f, 20f, true);
	}

	/** Mega laser — fat dual bolts from the nosegun modules. */
	public static boolean fireMega(PlayerEntity user, String weaponId, float damage, float splash, float splashR) {
		boolean ok = fireModuleBolts(user, weaponId, 2, damage, splash, splashR, 7800f, 1.6f, 2,
				0x44FFEE, 1.85f, 90f, true);
		if (ok && user.getWorld() instanceof ServerWorld) {
			// Mega keeps a punchy impact feel via larger splash already on the bolt.
		}
		return ok;
	}

	/**
	 * World positions of the first {@code count} combat module muzzles
	 * (lower/upper wing banks before the center core).
	 */
	public static List<Vec3d> combatMuzzles(PlayerEntity player, String weaponId, int count) {
		List<Vec3d> all = WeaponCore.allMuzzles(player, weaponId);
		List<Vec3d> out = new ArrayList<>(count);
		int take = Math.min(count, all.size());
		for (int i = 0; i < take; i++) out.add(all.get(i));
		// Synthesize missing wing mounts so a stripped layout still dual-fires.
		while (out.size() < count) {
			float side = out.size() % 2 == 0 ? -0.35f : 0.35f;
			float up = out.size() < 2 ? -0.12f : 0.10f;
			out.add(WeaponCore.muzzle(player, 0.85f, side, up));
		}
		return out;
	}

	/** Reticle aim point: first block under the nose, else a fixed converge distance. */
	public static Vec3d resolveAimPoint(PlayerEntity user) {
		Vec3d start = user.getEyePos();
		Vec3d dir = WeaponCore.aimDir(user);
		double probe = DescentMod.su(AIM_PROBE_SU);
		Vec3d end = start.add(dir.multiply(probe));
		if (user.getWorld() instanceof ServerWorld sw) {
			BlockHitResult hit = sw.raycast(new RaycastContext(start, end,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
			if (hit.getType() != HitResult.Type.MISS) {
				return hit.getPos();
			}
		}
		return start.add(dir.multiply(DescentMod.su(CONVERGE_SU)));
	}
}
