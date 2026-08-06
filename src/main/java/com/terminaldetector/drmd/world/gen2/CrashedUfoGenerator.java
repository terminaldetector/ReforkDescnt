package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.AirMineEntity;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * XCOM-style crashed saucer — medium hull half-buried, dense traps + garrison.
 * Tuned for post-Pyro GX assault (too hot on foot).
 */
public final class CrashedUfoGenerator {
	private CrashedUfoGenerator() {}

	private static final AiRole[] GARRISON = {
			AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.MG, AiRole.LASER, AiRole.RPG,
			AiRole.HEAVY, AiRole.SEEKER, AiRole.HEAVY_ELITE, AiRole.ARTILLERY
	};

	public static MacroEntry generate(WorldAccess world, BlockPos hint, Random random) {
		BlockPos origin = resolveCrashSite(world, hint);
		if (world.getBlockState(origin).isOf(Blocks.LODESTONE)) {
			return new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.CRASHED_UFO,
					WorldRules.Layer.SURFACE, origin.toImmutable(), 40, 20, 40, 0x66AA88, "Crashed UFO");
		}

		int major = 18;
		int minor = 6;
		BlockPos.Mutable m = new BlockPos.Mutable();

		// Impact crater
		for (int x = -major - 4; x <= major + 4; x++) {
			for (int z = -major - 4; z <= major + 4; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > major + 4) continue;
				int dig = (int) Math.max(1, (major + 2 - d) * 0.45);
				for (int y = 0; y <= dig; y++) {
					m.set(origin.getX() + x, origin.getY() - y, origin.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				if (d > major - 1 && d < major + 3) {
					m.set(origin.getX() + x, origin.getY() - dig, origin.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.BLACKSTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}

		// Saucer hull (ellipsoid shell, tilted slightly)
		for (int x = -major; x <= major; x++) {
			for (int y = -minor; y <= minor + 2; y++) {
				for (int z = -major; z <= major; z++) {
					double nx = x / (double) major;
					double ny = (y + 1) / (double) (minor + 1);
					double nz = z / (double) major;
					double e = nx * nx + ny * ny * 1.6 + nz * nz;
					if (e > 1.05 || e < 0.55) continue;
					m.set(origin.getX() + x, origin.getY() + y - 2, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					boolean outer = e > 0.82;
					if (outer) {
						world.setBlockState(m, Blocks.OXIDIZED_COPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (Math.abs(y) < 2 && nx * nx + nz * nz < 0.35) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (random.nextInt(40) == 0) {
						world.setBlockState(m, Blocks.CYAN_STAINED_GLASS.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(m, Blocks.PRISMARINE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}

		// Interior deck + traps
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				if (x * x + z * z > 64) continue;
				world.setBlockState(origin.add(x, -1, z), Blocks.DARK_PRISMARINE.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(origin.add(x, 0, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(origin.add(x, 1, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(origin.add(x, 2, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}

		world.setBlockState(origin, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
		world.setBlockState(origin.up(3), ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState(), Block.NOTIFY_ALL);

		// Trap lattice
		placeRing(world, origin, 6, 1, ModWorldBlocks.LASER_BARRIER);
		placeRing(world, origin, 10, 1, ModWorldBlocks.HERMETIC_GATE);
		world.setBlockState(origin.add(4, 1, 0), ModWorldBlocks.LASER_TURRET.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(origin.add(-4, 1, 0), ModWorldBlocks.PLASMA_TURRET.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(origin.add(0, 1, 4), ModWorldBlocks.POINT_DEFENSE_TURRET.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(origin.add(0, 1, -4), ModWorldBlocks.VOLUME_TURRET.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(origin.add(7, 1, 7), ModWorldBlocks.MAGNETIC_ANOMALY.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(origin.add(-7, 1, -7), ModWorldBlocks.MAGNETIC_ANOMALY.getDefaultState(), Block.NOTIFY_ALL);

		if (world instanceof ServerWorld sw) {
			spawnThreats(sw, origin, random);
		}

		MacroEntry e = new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.CRASHED_UFO,
				WorldRules.Layer.SURFACE, origin.toImmutable(),
				major * 2 + 10, 16, major * 2 + 10, 0x66AA88, "Crashed UFO");
		MacroWorld.put(e);
		return e;
	}

	/**
	 * Resample the surface under the hint rather than trust its Y outright — {@code hint} can come
	 * from a caller with no idea what the ground looks like there ({@code DescentSession}'s spawn-seed
	 * list hands this a flat {@code Y=80} regardless of terrain).
	 *
	 * <p>The clamp band, {@code [55,110]}, has to match {@code ModWorldgen2.skyY}'s CRASHED_UFO case
	 * exactly, not just overlap it — that call computes the same {@code getTopY} at the same X/Z to
	 * decide whether this chunk already has a crash site (checking for the LODESTONE marker at the Y
	 * it computes) and to build the landmark job's {@code origin} if not. A looser or shifted clamp
	 * here — this used to float the top end to 120 and floor sub-50 columns to a flat 70 — means the
	 * two functions agree only for surface heights already inside [55,110] and disagree everywhere
	 * else, so the marker lands somewhere the pre-check never looks. Every chunk with a crash site on
	 * low or tall terrain then rebuilt it — full saucer, trap lattice and 24-entity garrison — on every
	 * reload, which is ordinary play for a dogfighting mod: circle back over anywhere you have already
	 * cleared.
	 */
	private static BlockPos resolveCrashSite(WorldAccess world, BlockPos hint) {
		if (!(world instanceof ServerWorld sw)) return hint;
		int y = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, hint.getX(), hint.getZ());
		return new BlockPos(hint.getX(), Math.max(55, Math.min(110, y)), hint.getZ());
	}

	private static void placeRing(WorldAccess world, BlockPos origin, int r, int y, Block block) {
		for (int a = 0; a < 360; a += 45) {
			double rad = Math.toRadians(a);
			BlockPos p = origin.add((int) (Math.cos(rad) * r), y, (int) (Math.sin(rad) * r));
			if (inLimit(world, p)) {
				world.setBlockState(p, block.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
	}

	private static void spawnThreats(ServerWorld world, BlockPos origin, Random random) {
		// Dense drone garrison — why Pyro GX is the entry ticket
		for (int i = 0; i < 14; i++) {
			DroneEntity drone = ModEntities.DRONE.create(world);
			if (drone == null) continue;
			double ang = i * (Math.PI * 2 / 14);
			double rad = 5 + (i % 4);
			drone.refreshPositionAndAngles(
					origin.getX() + Math.cos(ang) * rad,
					origin.getY() + 1 + (i % 3),
					origin.getZ() + Math.sin(ang) * rad,
					0, 0);
			drone.applyRole(GARRISON[random.nextInt(GARRISON.length)]);
			world.spawnEntity(drone);
		}
		for (int i = 0; i < 10; i++) {
			AirMineEntity mine = ModEntities.AIR_MINE.create(world);
			if (mine == null) continue;
			double ang = random.nextDouble() * Math.PI * 2;
			double rad = 3 + random.nextDouble() * 10;
			mine.refreshPositionAndAngles(
					origin.getX() + Math.cos(ang) * rad,
					origin.getY() + 1 + random.nextDouble() * 4,
					origin.getZ() + Math.sin(ang) * rad,
					0, 0);
			world.spawnEntity(mine);
		}
	}

	private static boolean inLimit(WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}
