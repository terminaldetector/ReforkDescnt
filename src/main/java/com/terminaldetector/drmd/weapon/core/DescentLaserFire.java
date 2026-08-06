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
	/**
	 * Nearest the convergence point is allowed to sit, in Source units.
	 *
	 * <p>Every bolt in a volley starts from its own wing muzzle — up to about 2 blocks off the ship's
	 * centreline (a {@code SideLeft}/{@code SideRight} mount is 32 inches out, {@code /16}) — and is
	 * aimed from there at one shared point so the spread reads as a volley converging on the reticle
	 * rather than four parallel lines. That is fine at any real combat range: at 35 blocks a 2-block
	 * muzzle offset bends the shot under four degrees. It stops being fine the moment the convergence
	 * point is <em>closer than the muzzle spread itself</em> — point-blank against a wall, or a robot
	 * filling a tight Descent corridor — because then "aim from a wing gun at a point beside the nose"
	 * is asking for a direction nowhere near forward, and at the extreme, behind the muzzle entirely.
	 * That is what reads as the volley firing in every direction at once, and it is worst exactly in
	 * the close corridors this game is built around. 800 units (10 blocks) keeps the worst wing-muzzle
	 * bolt within about 13° of forward at the floor itself — a visible tight spread on a very close
	 * target, not the multiple-tens-of-degrees swing an un-floored point-blank hit produces.
	 */
	public static final float MIN_CONVERGE_SU = 800f;

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
		return fireModuleBolts(user, weaponId, 2, damage, splash, splashR, 7800f, 1.6f, 2,
				0x44FFEE, 1.85f, 90f, true);
	}

	/** Impulse laser — faster, weaker dual bolts (laser installation #2). */
	public static boolean firePulse(PlayerEntity user, String weaponId) {
		return fireModuleBolts(user, weaponId, 2, 14f, 6f, 60f, 7200f, 1.1f, 0,
				0x66FFAA, 0.85f, 8f, true);
	}

	/** Prism laser — three-way module fan converging on the reticle (laser #5). */
	public static boolean firePrism(PlayerEntity user, String weaponId) {
		if (user.getWorld().isClient) return false;
		List<Vec3d> muzzles = combatMuzzles(user, weaponId, 3);
		Vec3d aimPoint = resolveAimPoint(user);
		boolean any = false;
		for (int i = 0; i < muzzles.size(); i++) {
			Vec3d pos = muzzles.get(i);
			Vec3d dir = aimPoint.subtract(pos);
			if (dir.lengthSquared() < 1e-8) dir = WeaponCore.aimDir(user);
			else dir = dir.normalize();
			WeaponCore.FireConfig cfg = ProjectileFramework.config(ProjectileKind.LASER, user, pos, dir);
			cfg.speed = 6400;
			cfg.directDamage = 22f;
			cfg.splashDamage = 10f;
			cfg.splashRadius = 80f;
			cfg.life = 1.25f;
			cfg.pierceCount = 1;
			cfg.visualScale = 1.1f;
			cfg.igniteOnHit = true;
			cfg.recoil = i == 0 ? 14f : 0f;
			cfg.colorR = 0xFF; cfg.colorG = 0x88; cfg.colorB = 0x44;
			cfg.onHit = null;
			if (WeaponCore.fireProjectile(cfg) != null) any = true;
		}
		return any;
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

	/**
	 * Reticle aim point: first block under the nose, else a fixed converge distance.
	 *
	 * <p>Floored at {@link #MIN_CONVERGE_SU} from the eye. A raw raycast hit can land closer to the
	 * muzzles than the muzzles are to each other — see {@link #MIN_CONVERGE_SU} for why that is the
	 * one distance this must never return.
	 */
	public static Vec3d resolveAimPoint(PlayerEntity user) {
		Vec3d start = user.getEyePos();
		Vec3d dir = WeaponCore.aimDir(user);
		double probe = DescentMod.su(AIM_PROBE_SU);
		double minDist = DescentMod.su(MIN_CONVERGE_SU);
		Vec3d end = start.add(dir.multiply(probe));
		if (user.getWorld() instanceof ServerWorld sw) {
			BlockHitResult hit = sw.raycast(new RaycastContext(start, end,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
			if (hit.getType() != HitResult.Type.MISS) {
				Vec3d hitPos = hit.getPos();
				return start.distanceTo(hitPos) >= minDist ? hitPos : start.add(dir.multiply(minDist));
			}
		}
		return start.add(dir.multiply(DescentMod.su(CONVERGE_SU)));
	}
}
