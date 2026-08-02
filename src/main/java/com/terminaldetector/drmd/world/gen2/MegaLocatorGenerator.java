package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * Spark-style technogenic locator — dark tower + parabolic dish on the water.
 * Mega variant registers a large {@link MacroWorld} entry — a landmark on the radar.
 * Uses {@link ModWorldBlocks#LOCATOR_CORE} / resonator / panel for gameplay effects.
 */
public final class MegaLocatorGenerator {
	private MegaLocatorGenerator() {}

	public static MacroEntry generateMega(WorldAccess world, BlockPos xz, Random random) {
		return generate(world, xz, random, true);
	}

	public static MacroEntry generateSmall(WorldAccess world, BlockPos xz, Random random) {
		return generate(world, xz, random, false);
	}

	private static MacroEntry generate(WorldAccess world, BlockPos xz, Random random, boolean mega) {
		int surfaceY = surfaceY(world, xz.getX(), xz.getZ());
		int baseY = Math.max(62, surfaceY);
		BlockPos base = new BlockPos(xz.getX(), baseY, xz.getZ());

		int towerH = mega ? 48 + random.nextInt(16) : 22 + random.nextInt(10);
		int shaftR = mega ? 5 : 3;
		int dishR = mega ? 18 : 8;
		BlockState hull = ModWorldBlocks.LOCATOR_PANEL.getDefaultState();
		BlockState trim = Blocks.OXIDIZED_COPPER.getDefaultState();
		BlockState dish = Blocks.IRON_BLOCK.getDefaultState();
		BlockState light = Blocks.SEA_LANTERN.getDefaultState();
		BlockState pad = Blocks.DARK_PRISMARINE.getDefaultState();
		BlockState deep = Blocks.DEEPSLATE_TILES.getDefaultState();

		int padR = mega ? 14 : 7;
		for (int x = -padR; x <= padR; x++) {
			for (int z = -padR; z <= padR; z++) {
				if (x * x + z * z > padR * padR) continue;
				set(world, base.add(x, -1, z), pad);
				set(world, base.add(x, 0, z), Blocks.WATER.getDefaultState());
			}
		}

		for (int y = 0; y <= towerH; y++) {
			for (int x = -shaftR; x <= shaftR; x++) {
				for (int z = -shaftR; z <= shaftR; z++) {
					int d = x * x + z * z;
					if (d > shaftR * shaftR) continue;
					boolean shell = d >= (shaftR - 1) * (shaftR - 1);
					BlockPos p = base.add(x, y, z);
					if (shell) {
						BlockState skin = (y % 6 == 0) ? trim : ((y % 3 == 0) ? hull : deep);
						set(world, p, skin);
					} else {
						set(world, p, Blocks.AIR.getDefaultState());
					}
				}
			}
			if (y % 8 == 0) set(world, base.add(0, y, 0), light);
			if (mega && y % 12 == 0 && y > 0) {
				set(world, base.add(shaftR, y, 0), ModWorldBlocks.LOCATOR_RESONATOR.getDefaultState());
				set(world, base.add(-shaftR, y, 0), ModWorldBlocks.LOCATOR_RESONATOR.getDefaultState());
			}
		}

		BlockPos dishC = base.add(0, towerH + 2, 0);
		for (int x = -dishR; x <= dishR; x++) {
			for (int z = -dishR; z <= dishR; z++) {
				float dist = MathHelper.sqrt(x * x + z * z);
				if (dist > dishR || dist < dishR * 0.25f) continue;
				int y = (int) (dist * dist / (dishR * 0.9f));
				if (y > dishR / 2) continue;
				set(world, dishC.add(x, y, z), (x + z) % 4 == 0 ? hull : dish);
				if ((x + z) % 5 == 0) set(world, dishC.add(x, y + 1, z), light);
			}
		}
		for (int y = 0; y < dishR / 2 + 4; y++) {
			set(world, dishC.add(0, y, 0), trim);
		}
		set(world, dishC.add(0, dishR / 2 + 4, 0),
				mega ? ModWorldBlocks.LOCATOR_CORE.getDefaultState() : ModWorldBlocks.LOCATOR_RESONATOR.getDefaultState());

		// Heart of the pad — gameplay ping, keyed by the MacroWorld entry
		set(world, base.add(0, 1, 0),
				mega ? ModWorldBlocks.LOCATOR_CORE.getDefaultState() : ModWorldBlocks.LOCATOR_RESONATOR.getDefaultState());
		set(world, base.add(2, 1, 0), ModWorldBlocks.LOCATOR_PANEL.getDefaultState());
		set(world, base.add(-2, 1, 0), ModWorldBlocks.LOCATOR_PANEL.getDefaultState());

		MacroEntry.Kind kind = mega ? MacroEntry.Kind.MEGA_LOCATOR : MacroEntry.Kind.LOCATOR;
		int sx = mega ? 56 : 24;
		int sy = mega ? towerH + dishR + 8 : towerH + 12;
		int sz = mega ? 56 : 24;
		int color = mega ? 0x66AADD : 0x5588AA;
		String label = mega ? "Mega Locator" : "Locator";
		MacroEntry e = new MacroEntry(UUID.nameUUIDFromBytes(
				("drmd-locator-" + xz.getX() + "," + xz.getZ() + (mega ? "-M" : "-S")).getBytes()),
				kind, WorldRules.Layer.SURFACE,
				base.add(0, towerH / 2, 0), sx, sy, sz, color, label);
		MacroWorld.put(e);
		return e;
	}

	private static int surfaceY(WorldAccess world, int x, int z) {
		if (world instanceof ServerWorld sw) {
			return sw.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
		}
		for (int y = 90; y >= 40; y--) {
			BlockPos p = new BlockPos(x, y, z);
			if (!world.getBlockState(p).isAir() || !world.getFluidState(p).isEmpty()) return y;
		}
		return 63;
	}

	private static void set(WorldAccess world, BlockPos pos, BlockState state) {
		int y = pos.getY();
		if (y < world.getBottomY() || y >= world.getBottomY() + world.getHeight()) return;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}
}
