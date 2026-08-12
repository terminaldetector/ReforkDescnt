package com.terminaldetector.drmd.world.structure;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Mid-air detonation — a near-verbatim extraction of what {@code SkyUfoEntity.destroyFromCore} already
 * does in production today (explosion + fire + sound), generalized so a future structure can reuse the
 * exact same, already-proven VFX sequence instead of a bespoke copy. Zero visual risk: nothing about the
 * effect itself changes, only where the code lives.
 */
public final class StructureShockwave {
	private StructureShockwave() {}

	/**
	 * @param center     detonation point, already block-centered (e.g. {@code Vec3d.ofCenter(epicenter)}) —
	 *                   matches how {@code destroyFromCore} computed it, so the sound/explosion land on the
	 *                   same point as before
	 * @param source     explosion source entity for the un-attributed path (typically the structure's own
	 *                   entity, if it has one); mirrors {@code sw.createExplosion(this, ...)}
	 * @param culprit    the player to credit/attribute the kill to, or {@code null} for an unattributed
	 *                   detonation (reactor rupture, timer, impact — no specific player responsible)
	 * @param explosionPower  matches {@code WeaponFx.explode}'s/{@code createExplosion}'s power parameter
	 * @param smokeRadius     matches {@code SmokeSystem.emitExplosion}'s radius parameter (unattributed path only)
	 * @param igniteRadius    matches {@code FireSystem.igniteBlast}'s radius parameter
	 * @param igniteChance    matches {@code FireSystem.igniteBlast}'s chance parameter
	 */
	public static void detonate(ServerWorld world, Vec3d center, Entity source, PlayerEntity culprit,
			float explosionPower, float smokeRadius, int igniteRadius, int igniteChance) {
		if (culprit != null) {
			WeaponFx.explode(culprit, world, center, 180f, explosionPower, DamageClass.EXPLOSIVE, true);
		} else {
			world.createExplosion(source, center.x, center.y, center.z, explosionPower, true, World.ExplosionSourceType.TNT);
			SmokeSystem.emitExplosion(center, smokeRadius);
		}
		FireSystem.igniteBlast(world, BlockPos.ofFloored(center.x, center.y, center.z), igniteRadius, igniteChance);
		world.playSound(null, center.x, center.y, center.z,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.5f, 0.55f);
	}
}
