package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * First-person Mega Beam — sustained thick energy column (white core + cyan sheath).
 *
 * <p>Reference: fat horizontal mega-beam with shield interaction. Hold fire drains
 * weapons energy each tick; damage is hitscan through {@link ShieldSystem} via
 * {@link WeaponCore#hitscan}.
 */
public final class MegaBeamFire {
	/** Source-units range — screen-spanning feel in 6DoF. */
	public static final float RANGE_SU = 5200f;
	/** Damage per sustain tick (every 2 game ticks ≈ 10 Hz). */
	public static final float TICK_DAMAGE = 28f;
	/** Energy per sustain tick. */
	public static final float TICK_ENERGY = 4.5f;
	/** Splash at impact point. */
	public static final float SPLASH = 55f;
	public static final float SPLASH_R_SU = 180f;

	private MegaBeamFire() {}

	/**
	 * One sustain sample. Returns false if energy empty (caller should stop using).
	 */
	public static boolean tick(PlayerEntity user, DescentPlayerData data) {
		if (!(user.getWorld() instanceof ServerWorld sw)) return true;
		if (!EnergySystem.tryConsume(data, "weapons", TICK_ENERGY)) return false;

		Vec3d aim = WeaponCore.aimDir(user);
		var muzzles = WeaponCore.allMuzzles(user, "mega_laser");
		Vec3d from = muzzles.isEmpty()
				? WeaponCore.muzzle(user, 0.85f, 0f, -0.05f)
				: muzzles.get(0);

		final Vec3d[] hit = {from.add(aim.multiply(DescentMod.su(RANGE_SU)))};
		WeaponCore.hitscan(user, from, aim, RANGE_SU, TICK_DAMAGE, DamageClass.ENERGY, ctx -> {
			hit[0] = ctx.hitPos();
			WeaponCore.splashDamage(user, sw, ctx.hitPos(),
					SPLASH, (float) DescentMod.su(SPLASH_R_SU), DamageClass.ENERGY);
		}, false, true);

		WeaponFx.megaBeam(sw, from, hit[0]);
		return true;
	}
}
