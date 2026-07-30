package com.terminaldetector.drmd.world.fire;

import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Fire — 3D volume spread (ceilings, walls, industrial halls).
 * Multi-level with smoke: local fire blocks + tracked foci for secondary ignition.
 */
public final class FireSystem {
	public static final class Focus {
		public BlockPos pos;
		public int intensity;
		public int life;

		Focus(BlockPos pos, int intensity, int life) {
			this.pos = pos.toImmutable();
			this.intensity = intensity;
			this.life = life;
		}
	}

	private static final Map<Long, Focus> FOCI = new ConcurrentHashMap<>();

	private FireSystem() {}

	public static void clear() {
		FOCI.clear();
	}

	public static void ignite(ServerWorld world, BlockPos pos, int intensity) {
		FOCI.put(pos.asLong(), new Focus(pos, intensity, 100 + intensity * 20));
		if (world.getBlockState(pos).isAir() || world.getBlockState(pos).isReplaceable()) {
			world.setBlockState(pos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
		}
		SmokeSystem.emit(Vec3d.ofCenter(pos), SmokeSystem.Source.FIRE, 1.2f, 0.7f, 80);
	}

	/** Bombardment / explosion aftermath — multiple foci. */
	public static void igniteBlast(ServerWorld world, BlockPos center, int count, int radius) {
		for (int i = 0; i < count; i++) {
			int dx = world.getRandom().nextInt(radius * 2 + 1) - radius;
			int dy = world.getRandom().nextInt(Math.max(1, radius)) - radius / 2;
			int dz = world.getRandom().nextInt(radius * 2 + 1) - radius;
			BlockPos p = center.add(dx, dy, dz);
			if (isFlammable(world, p) || world.getBlockState(p).isAir()) {
				ignite(world, p, 2 + world.getRandom().nextInt(3));
			}
		}
	}

	public static void tick(ServerWorld world) {
		List<Long> dead = new ArrayList<>();
		List<Focus> spread = new ArrayList<>();
		for (Focus f : FOCI.values()) {
			f.life--;
			if (f.life <= 0) {
				dead.add(f.pos.asLong());
				continue;
			}
			if (world.getTime() % 10 == 0) {
				SmokeSystem.emit(Vec3d.ofCenter(f.pos).add(0, 0.5, 0),
						SmokeSystem.Source.FIRE, 0.8f + f.intensity * 0.3f, 0.5f, 40);
			}
			// 3D spread: all 6 directions including ceilings
			if (world.getRandom().nextInt(12) == 0) {
				Direction dir = Direction.random(world.getRandom());
				BlockPos next = f.pos.offset(dir);
				if (isFlammable(world, next) || (dir == Direction.UP && world.getBlockState(next).isAir()
						&& isFlammable(world, next.up()))) {
					spread.add(new Focus(next, Math.max(1, f.intensity - 1), 60 + f.intensity * 10));
				}
			}
		}
		dead.forEach(FOCI::remove);
		for (Focus f : spread) {
			if (FOCI.size() < 200) ignite(world, f.pos, f.intensity);
		}
	}

	private static boolean isFlammable(ServerWorld world, BlockPos pos) {
		var st = world.getBlockState(pos);
		return st.isOf(Blocks.OAK_LOG) || st.isOf(Blocks.SPRUCE_LOG) || st.isOf(Blocks.BIRCH_LOG)
				|| st.isOf(Blocks.JUNGLE_LOG) || st.isOf(Blocks.ACACIA_LOG) || st.isOf(Blocks.DARK_OAK_LOG)
				|| st.isOf(Blocks.OAK_PLANKS) || st.isOf(Blocks.SPRUCE_PLANKS) || st.isOf(Blocks.BIRCH_PLANKS)
				|| st.isOf(Blocks.OAK_LEAVES) || st.isOf(Blocks.SPRUCE_LEAVES) || st.isOf(Blocks.BIRCH_LEAVES)
				|| st.isOf(Blocks.WHITE_WOOL) || st.isOf(Blocks.HAY_BLOCK) || st.isOf(Blocks.BOOKSHELF)
				|| st.isOf(Blocks.BARREL) || st.isOf(Blocks.CRAFTING_TABLE) || st.isOf(Blocks.CHEST)
				|| (st.getBlock().getBlastResistance() < 1.5f && !st.isAir() && !st.isLiquid());
	}

	public static int focusCount() {
		return FOCI.size();
	}
}
