package com.terminaldetector.drmd.world.micro;

import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Blocks that come apart in stages instead of all at once.
 *
 * <p>Where a hit meets the world. The shape lives in {@link MicroGrid}, what is left of each block
 * lives in {@link MicroStore}, and this is the part that knows about hardness, particles, sound and
 * when a block finally stops existing.
 *
 * <p>Progress uses the crack overlay together with the carved block's quarter-cell geometry and
 * collision shape. The block entity is authoritative for the remaining mask.
 */
public final class BlockDamage {
	/** Damage that removes a cell's worth of an ordinary block. */
	private static final float DAMAGE_PER_CELL = 4f;

	/** Hardness above which a block barely notices small arms. */
	private static final float TOUGH = 6f;

	private BlockDamage() {}

	/**
	 * Put damage into a block at the exact point something hit it.
	 *
	 * @param impact where the hit landed, in world coordinates — its fractional part is where in the
	 *               block the crater goes, which is why the same wall chews through from the side
	 *               you are shooting
	 * @return true when the block was destroyed by this hit
	 */
	public static boolean hit(ServerWorld world, BlockPos pos, Vec3d impact, float damage) {
		if (!Float.isFinite(damage) || damage <= 0) return false;
		BlockState state = world.getBlockState(pos);
		if (state.isAir()) return false;
		if (!isDamageable(world, pos, state)) return false;

		MicroStore store = MicroStore.get(world);
		long before = world.getBlockEntity(pos) instanceof CarvedBlockEntity carved ? carved.mask() : MicroGrid.FULL;
		double radius = radiusFor(state, world, pos, damage);
		if (radius <= 0) return false;

		long after = MicroGrid.carve(before,
				impact.x - pos.getX(), impact.y - pos.getY(), impact.z - pos.getZ(), radius);
		if (after == before) {
			// The crater fell entirely on cells that were already gone. Nudge the centre inward so a
			// stream of fire into an existing hole still deepens it instead of doing nothing.
			after = MicroGrid.carve(before, 0.5, 0.5, 0.5, radius * 0.6);
			if (after == before) return false;
		}

		MicroGrid.Stage was = MicroGrid.stage(before);
		MicroGrid.Stage now = MicroGrid.stage(after);

		if (MicroGrid.isEmpty(after)) {
			destroy(world, pos, sourceOf(world, pos, state));
			store.clear(pos);
			return true;
		}

		if (!applyShape(world, pos, state, after)) return false;
		store.set(pos, after);
		if (now != was) announceStage(world, pos, sourceOf(world, pos, state), now);
		return false;
	}

	/**
	 * Give the block the shape its mask describes.
	 *
	 * <p>The first hit swaps the ordinary block for a carved one that carries both the original state
	 * and the mask — that swap is what turns a hole from a texture into somewhere a ship can fly
	 * through. Later hits only update the mask it already has.
	 *
	 * <p>The crack overlay stays on top of it. The carved shape shows what is gone; the cracks show
	 * how close what is left is to going, which the shape alone does not say at a glance.
	 */
	private static boolean applyShape(ServerWorld world, BlockPos pos, BlockState state, long mask) {
		if (state.isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.CARVED)) {
			if (world.getBlockEntity(pos) instanceof CarvedBlockEntity carved) carved.setMask(mask);
			else return false;
		} else {
			if (CarvedBlock.replace(world, pos, state, mask) == null) return false;
		}
		world.setBlockBreakingInfo(breakerId(pos), pos, MicroGrid.crackStage(mask));
		com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world, pos);
		return true;
	}

	/** The block this one used to be, for drops, sounds and particles. */
	private static BlockState sourceOf(ServerWorld world, BlockPos pos, BlockState state) {
		if (state.isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.CARVED)
				&& world.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
			return carved.source();
		}
		return state.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock organic ? organic.source() : state;
	}

	/** Called only for vanilla-selected blast cells. Keep the centre's normal destruction/loot path. */
	public static boolean fractureExplosion(ServerWorld world, BlockPos pos, Vec3d centre, float power) {
		BlockState state = world.getBlockState(pos);
		if (!Float.isFinite(power) || power <= 0 || !isDamageable(world,pos,state)) return false;
		double distance = Vec3d.ofCenter(pos).distanceTo(centre);
		if (distance < power * .55) return false;
		float damage = (float)(power * 12 * Math.max(0, 1-distance/(power*2)));
		if (damage <= 0) return false;
		Vec3d toward = centre.subtract(Vec3d.ofCenter(pos)).normalize().multiply(.45);
		long before = world.getBlockEntity(pos) instanceof CarvedBlockEntity c ? c.mask() : MicroGrid.FULL;
		double radius = radiusFor(state,world,pos,damage);
		long after = MicroGrid.carve(before, .5+toward.x, .5+toward.y, .5+toward.z, radius);
		if (after == before || MicroGrid.isEmpty(after)) return false;
		if (!applyShape(world,pos,state,after)) return false;
		MicroStore.get(world).set(pos,after);
		return true;
	}

	/** Spherical damage — an explosion, a reactor going up. */
	public static void blast(ServerWorld world, Vec3d centre, double radius, float damage) {
		int r = (int) Math.ceil(radius);
		BlockPos origin = BlockPos.ofFloored(centre);
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = origin.add(dx, dy, dz);
					Vec3d cell = Vec3d.ofCenter(pos);
					double distance = cell.distanceTo(centre);
					if (distance > radius) continue;
					// Falls off with distance, so a blast hollows out a crater rather than a cube.
					float here = (float) (damage * (1.0 - distance / radius));
					hit(world, pos, cell, here);
				}
			}
		}
	}

	public static boolean isDamageable(ServerWorld world, BlockPos pos, BlockState state) {
		if (state.isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.CARVED) || state.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock) return true;
		// Generic carving cannot preserve inventories, fluids or the geometry of partial blocks.
		if (world.getBlockEntity(pos) != null || !state.getFluidState().isEmpty()
				|| !state.isFullCube(world, pos) || state.isOf(net.minecraft.block.Blocks.TNT)) return false;
		if (state.getHardness(world, pos) < 0) return false;   // bedrock and friends
		return !state.isIn(BlockTags.WITHER_IMMUNE);
	}

	/**
	 * How much of the block one hit takes out.
	 *
	 * <p>Scaled by hardness, so stone gives way slower than dirt and a reinforced wall slower still,
	 * and clamped so nothing short of a blast can swallow a whole block in one shot — the point of
	 * the ladder is that walls come apart visibly rather than blinking out.
	 */
	private static double radiusFor(BlockState state, ServerWorld world, BlockPos pos, float damage) {
		// A carved shell is soft by itself; what resists is the material it is standing in for.
		BlockState material = sourceOf(world, pos, state);
		float hardness = Math.max(0.2f, material.getHardness(world, pos));
		float resistance = Math.min(hardness, TOUGH);
		double cells = damage / (DAMAGE_PER_CELL * resistance);
		if (cells < 0.35) return 0;
		// A sphere of n cells has radius (3n / 4pi)^(1/3), in cells; convert to block fractions.
		double radiusCells = Math.cbrt(cells * 3.0 / (4.0 * Math.PI));
		return Math.min(0.75, Math.max(0.30, radiusCells * MicroGrid.CELL_SIZE * 2.0));
	}

	private static void destroy(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockBreakingInfo(breakerId(pos), pos, -1);
		world.breakBlock(pos, false);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, 0.3, 0.3, 0.3, 0.05);
		world.spawnParticles(ParticleTypes.ASH,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.35, 0.35, 0.35, 0.02);
		world.playSound(null, pos, state.getSoundGroup().getBreakSound(),
				SoundCategory.BLOCKS, 0.7f, 0.9f);
		// The ground changed shape, so the horizon's memory of it is stale.
		com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world, pos);
	}

	/** A puff at each rung of the ladder, so the stage a wall is at is readable while shooting it. */
	private static void announceStage(ServerWorld world, BlockPos pos, BlockState state,
									  MicroGrid.Stage stage) {
		int count = switch (stage) {
			case CHIPPED -> 3;
			case HALVED -> 6;
			case CRITICAL -> 10;
			case DEBRIS -> 16;
			default -> 0;
		};
		if (count == 0) return;
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, count, 0.25, 0.25, 0.25, 0.02);
		if (stage == MicroGrid.Stage.DEBRIS) {
			world.spawnParticles(ParticleTypes.ASH,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
		}
	}

	/**
	 * A stable id per position for the crack overlay.
	 *
	 * <p>Vanilla keys breaking progress by the entity doing the breaking, because normally a player
	 * is. Here the damage has no single owner, so the position stands in for one — two blocks then
	 * never share a progress slot and knock each other's cracks out.
	 */
	private static int breakerId(BlockPos pos) {
		// Negative, to stay clear of real entity ids.
		return -(Math.abs((int) (pos.asLong() * 0x9E3779B1L >>> 33)) % 1_000_000) - 1;
	}
}
