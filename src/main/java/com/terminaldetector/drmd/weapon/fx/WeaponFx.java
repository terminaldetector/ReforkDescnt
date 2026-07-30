package com.terminaldetector.drmd.weapon.fx;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Shared weapon VFX / terrain effects — beams, melt stages, blasts, drill carve.
 * Reuses SmokeSystem / FireSystem / AtmosphereRules (same stack as bomb bay).
 */
public final class WeaponFx {
	private WeaponFx() {}

	/** Particle beam along a segment (engineer laser look). */
	public static void beam(ServerWorld world, Vec3d from, Vec3d to, Vector3f color, float size) {
		Vec3d delta = to.subtract(from);
		double len = delta.length();
		if (len < 1e-4) return;
		int n = MathHelper.clamp((int) (len * 3.5), 6, 96);
		for (int i = 0; i <= n; i++) {
			Vec3d p = from.add(delta.multiply(i / (double) n));
			world.spawnParticles(new DustParticleEffect(color, size), p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 2, 0.05, 0.05, 0.05, 0.01);
	}

	public static void beamEnergy(ServerWorld world, Vec3d from, Vec3d to) {
		beam(world, from, to, new Vector3f(0.35f, 1f, 0.55f), 1.05f);
	}

	public static void beamDrill(ServerWorld world, Vec3d from, Vec3d to) {
		beam(world, from, to, new Vector3f(1f, 0.55f, 0.12f), 1.15f);
		world.spawnParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 2, 0.15, 0.15, 0.15, 0.02);
		world.spawnParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 4, 0.1, 0.1, 0.1, 0.01);
	}

	public static void beamExotic(ServerWorld world, Vec3d from, Vec3d to) {
		beam(world, from, to, new Vector3f(0.2f, 0.9f, 1f), 1.1f);
	}

	/**
	 * Soft “оплавление”: heat stage → soften → break. Intensity 1..3.
	 * Reuses existing block palette (no new assets).
	 */
	public static boolean melt(ServerWorld world, BlockPos pos, int intensity, LivingEntity breaker) {
		BlockState st = world.getBlockState(pos);
		if (st.isAir() || st.getHardness(world, pos) < 0 || st.isOf(Blocks.BEDROCK)) return false;
		float hard = st.getHardness(world, pos);
		if (hard >= 50) return false;

		world.spawnParticles(ParticleTypes.FLAME,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				4 + intensity * 2, 0.25, 0.25, 0.25, 0.01);
		world.spawnParticles(ParticleTypes.SMOKE,
				pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
				3, 0.2, 0.2, 0.2, 0.01);
		world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS,
				0.25f + intensity * 0.1f, 1.4f + world.getRandom().nextFloat() * 0.3f);

		BlockState next = meltStage(st, intensity);
		if (next == null) {
			if (intensity >= 2 && hard < 20) {
				world.breakBlock(pos, true, breaker);
				return true;
			}
			return false;
		}
		if (next.isAir()) {
			world.breakBlock(pos, true, breaker);
		} else {
			world.setBlockState(pos, next, Block.NOTIFY_ALL);
			if (intensity >= 2 && world.getRandom().nextInt(4) == 0) {
				FireSystem.ignite(world, pos.up(), 1);
			}
		}
		return true;
	}

	private static BlockState meltStage(BlockState st, int intensity) {
		if (st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.CRYING_OBSIDIAN)) {
			return intensity >= 3 ? Blocks.LAVA.getDefaultState() : Blocks.MAGMA_BLOCK.getDefaultState();
		}
		if (st.isOf(Blocks.STONE) || st.isOf(Blocks.DEEPSLATE) || st.isOf(Blocks.COBBLESTONE)
				|| st.isOf(Blocks.STONE_BRICKS) || st.isOf(Blocks.DEEPSLATE_BRICKS)) {
			return intensity >= 2 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.COBBLED_DEEPSLATE.getDefaultState();
		}
		if (st.isOf(Blocks.IRON_ORE) || st.isOf(Blocks.DEEPSLATE_IRON_ORE) || st.isOf(Blocks.IRON_BLOCK)
				|| st.isOf(Blocks.GOLD_BLOCK) || st.isOf(Blocks.COPPER_BLOCK)) {
			return Blocks.MAGMA_BLOCK.getDefaultState();
		}
		if (st.isOf(Blocks.SAND) || st.isOf(Blocks.RED_SAND) || st.isOf(Blocks.GLASS)
				|| st.isOf(Blocks.SANDSTONE)) {
			return intensity >= 2 ? Blocks.AIR.getDefaultState() : Blocks.GLASS.getDefaultState();
		}
		if (st.isOf(Blocks.NETHERRACK) || st.isOf(Blocks.MAGMA_BLOCK)) {
			return intensity >= 2 ? Blocks.LAVA.getDefaultState() : Blocks.MAGMA_BLOCK.getDefaultState();
		}
		if (st.isOf(Blocks.ICE) || st.isOf(Blocks.PACKED_ICE) || st.isOf(Blocks.BLUE_ICE)
				|| st.isOf(Blocks.SNOW_BLOCK)) {
			return Blocks.WATER.getDefaultState();
		}
		if (intensity >= 3) return Blocks.AIR.getDefaultState();
		return null;
	}

	/**
	 * Heavy-weapon blast — shield-aware splash + smoke/fire + optional soft crater.
	 * Avoids stacking vanilla explosion entity damage on top of WeaponCore splash.
	 */
	public static void explode(LivingEntity attacker, ServerWorld world, Vec3d pos,
							   float splashDamage, float splashRadiusBlocks,
							   DamageClass dmgClass, boolean breakBlocks) {
		if (splashDamage > 0 && splashRadiusBlocks > 0) {
			WeaponCore.splashDamage(attacker, world, pos, splashDamage, splashRadiusBlocks, dmgClass);
		}
		float power = AtmosphereRules.scaleBlast(pos.y,
				MathHelper.clamp(splashRadiusBlocks * 0.55f, 1.15f, 7.5f));
		SmokeSystem.emitExplosion(pos, power);
		world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		if (power > 2.8f) {
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
				0.7f + power * 0.08f, 0.85f + world.getRandom().nextFloat() * 0.2f);
		if (breakBlocks) {
			int r = MathHelper.clamp((int) (splashRadiusBlocks * 0.65f), 1, 5);
			BlockPos center = BlockPos.ofFloored(pos);
			for (BlockPos p : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
				if (p.getSquaredDistance(center) > r * r) continue;
				BlockState st = world.getBlockState(p);
				float h = st.getHardness(world, p);
				if (!st.isAir() && h >= 0 && h < 15 && !st.isOf(Blocks.BEDROCK)) {
					if (world.getRandom().nextFloat() < 0.55f) {
						world.breakBlock(p, true, attacker);
					}
				}
			}
		}
		if (dmgClass == DamageClass.EXPLOSIVE && power > 2.4f) {
			FireSystem.igniteBlast(world, BlockPos.ofFloored(pos),
					3 + (int) power, Math.max(2, (int) (splashRadiusBlocks * 0.35f)));
		}
	}

	/** 3×3 soft carve — ProjectileFramework DRILL_CHARGE behaviour, shared. */
	public static void drillCarve(ServerWorld world, BlockPos center, LivingEntity breaker) {
		for (BlockPos p : BlockPos.iterate(center.add(-1, -1, -1), center.add(1, 1, 1))) {
			BlockState st = world.getBlockState(p);
			float h = st.getHardness(world, p);
			if (!st.isAir() && h >= 0 && h < 20 && !st.isOf(Blocks.BEDROCK)) {
				if (world.getRandom().nextInt(3) == 0) melt(world, p, 2, breaker);
				else world.breakBlock(p, true, breaker);
			}
		}
		world.spawnParticles(ParticleTypes.LAVA,
				center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
				8, 0.6, 0.6, 0.6, 0.02);
	}
}
