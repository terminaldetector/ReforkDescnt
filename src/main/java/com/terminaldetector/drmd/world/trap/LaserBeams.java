package com.terminaldetector.drmd.world.trap;

import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Shared laser-barrier beam caster — static blocks and cyclic minecart barriers.
 */
public final class LaserBeams {
	private static final Vector3f BARRIER_COLOR = new Vector3f(1f, 0.25f, 0.35f);

	private LaserBeams() {}

	public static void cast(ServerWorld world, Vec3d from, Direction dir, int length, float damage) {
		cast(world, from, Vec3d.of(dir.getVector()), length, damage);
	}

	public static void cast(ServerWorld world, Vec3d from, Vec3d dir, int length, float damage) {
		if (dir.lengthSquared() < 1e-8) return;
		Vec3d step = dir.normalize();
		Vec3d tip = from;
		for (int i = 1; i <= length; i++) {
			tip = from.add(step.multiply(i));
			BlockPos p = BlockPos.ofFloored(tip);
			if (!world.getBlockState(p).isAir() && !world.getBlockState(p).getCollisionShape(world, p).isEmpty()) {
				break;
			}
			Box box = new Box(tip.x - 0.35, tip.y - 0.35, tip.z - 0.35,
					tip.x + 0.35, tip.y + 0.35, tip.z + 0.35);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LaserBeams::isBarrierTarget)) {
				e.damage(world.getDamageSources().magic(), damage);
			}
			if (i % 2 == 0) {
				world.spawnParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
			}
		}
		WeaponFx.beam(world, from, tip, BARRIER_COLOR, 0.85f);
	}

	/** Dual beams: inward to ring centre + local UP — covers 6DoF approaches. */
	public static void castRingPair(ServerWorld world, Vec3d from, Vec3d inward, Vec3d up, int length, float damage) {
		cast(world, from, inward, length, damage);
		cast(world, from, up, Math.max(4, length / 2), damage * 0.75f);
	}

	public static boolean isBarrierTarget(LivingEntity e) {
		if (!e.isAlive()) return false;
		if (e instanceof PyroShipEntity) return false;
		if (e instanceof EndReactorBossEntity) return false;
		if (e instanceof ReactorKeeperEntity) return false;
		if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
		return true;
	}
}
