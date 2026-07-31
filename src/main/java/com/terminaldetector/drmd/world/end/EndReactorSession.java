package com.terminaldetector.drmd.world.end;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;

/**
 * Replaces the vanilla Ender Dragon fight with a Descent mega-reactor base.
 * Shield crystals (End crystals on pillars) → exposed giga-core → destroy for exit gateways.
 */
public final class EndReactorSession {
	private static boolean seededThisRun;

	private EndReactorSession() {}

	/**
	 * Where the fight lives in a given world.
	 *
	 * <p>The End is a band of the overworld column now, not a dimension you portal to, so the
	 * arena has to exist up there — otherwise {@code /d6 level end} drops you among End islands with
	 * nothing in them while the actual fight sits behind a portal the design says should not exist.
	 * The old End dimension keeps its copy: it is still reachable by vanilla means, and the fight
	 * state is per-world, so the two never interfere.
	 */
	public static BlockPos arenaCenter(ServerWorld world) {
		if (world.getRegistryKey() == World.OVERWORLD) {
			return new BlockPos(0, END_BAND_ARENA_Y, 0);
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
		if (y < 40 || y > 120) y = 72;
		return new BlockPos(0, y, 0);
	}

	/**
	 * Arena floor inside the END band.
	 *
	 * <p>Sits among the island shelf rather than above it — {@code generateBase} clears a radius-30
	 * volume first, so it carves its own space and the surrounding islands become the approach.
	 * Kept well under the column ceiling: the pillars and their crystals reach 16 above the floor,
	 * and writes past the height limit are silently dropped, which would decapitate the towers.
	 */
	private static final int END_BAND_ARENA_Y = 940;

	public static void onServerTick(MinecraftServer server) {
		ServerWorld end = server.getWorld(World.END);
		if (end != null) suppressDragons(end);
		if (seededThisRun) return;
		seededThisRun = true;
		for (ServerWorld world : arenaWorlds(server)) {
			ensureBase(world);
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		seededThisRun = false;
		server.execute(() -> {
			for (ServerWorld world : arenaWorlds(server)) {
				suppressDragons(world);
				ensureBase(world);
			}
			seededThisRun = true;
		});
	}

	private static java.util.List<ServerWorld> arenaWorlds(MinecraftServer server) {
		java.util.ArrayList<ServerWorld> worlds = new java.util.ArrayList<>(2);
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		if (overworld != null) worlds.add(overworld);
		ServerWorld end = server.getWorld(World.END);
		if (end != null) worlds.add(end);
		return worlds;
	}

	/** Called each dragon-fight tick — remove dragons, keep our fight. */
	public static void suppressDragons(ServerWorld end) {
		for (EnderDragonEntity dragon : end.getAliveEnderDragons()) {
			dragon.discard();
		}
	}

	public static void ensureBase(ServerWorld end) {
		EndReactorState state = EndReactorState.get(end);
		if (state.isBaseGenerated()) {
			ensureBossAlive(end, state);
			return;
		}
		BlockPos center = arenaCenter(end);
		generateBase(end, center);
		spawnBoss(end, center);
		state.setBaseGenerated(true);
		state.setPhase(EndReactorState.Phase.SHIELDED);
		DescentMod.LOGGER.info("Reactor arena generated in {} at {}",
				end.getRegistryKey().getValue(), center.toShortString());
	}

	private static void ensureBossAlive(ServerWorld end, EndReactorState state) {
		if (state.isDefeated()) return;
		boolean alive = !end.getEntitiesByType(ModEntities.END_REACTOR_BOSS, e -> e.isAlive()).isEmpty();
		if (!alive && state.getPhase() != EndReactorState.Phase.DEFEATED) {
			spawnBoss(end, arenaCenter(end));
		}
	}

	public static void generateBase(ServerWorld world, BlockPos center) {
		// Clear a working volume so the base replaces the stock podium without leftover clutter
		for (int x = -30; x <= 30; x++) {
			for (int z = -30; z <= 30; z++) {
				if (x * x + z * z > 30 * 30) continue;
				for (int y = 1; y <= 22; y++) {
					world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}

		// Central platform
		for (int x = -28; x <= 28; x++) {
			for (int z = -28; z <= 28; z++) {
				if (x * x + z * z > 28 * 28) continue;
				BlockPos p = center.add(x, 0, z);
				world.setBlockState(p, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (x * x + z * z > 22 * 22) {
					world.setBlockState(p.up(), Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
				} else if ((x + z) % 5 == 0) {
					world.setBlockState(p.up(), Blocks.PURPUR_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		// Reactor ring
		for (int a = 0; a < 360; a += 8) {
			double rad = Math.toRadians(a);
			int x = (int) (Math.cos(rad) * 10);
			int z = (int) (Math.sin(rad) * 10);
			for (int y = 1; y <= 8; y++) {
				world.setBlockState(center.add(x, y, z), Blocks.CRYING_OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		// Core pedestal
		for (int y = 1; y <= 6; y++) {
			world.setBlockState(center.up(y), Blocks.RESPAWN_ANCHOR.getDefaultState(), Block.NOTIFY_ALL);
		}
		world.setBlockState(center.up(7), ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState(), Block.NOTIFY_ALL);

		ReactorDisplayEntity core = ModEntities.REACTOR_DISPLAY.create(world);
		if (core != null) {
			core.refreshPositionAndAngles(center.getX() + 0.5, center.getY() + 9.0, center.getZ() + 0.5, 0, 0);
			world.spawnEntity(core);
		}

		// Shield crystal pillars (dragon-analogue) — 4 towers with End crystals
		BlockPos[] pillars = {
				center.add(22, 0, 0), center.add(-22, 0, 0),
				center.add(0, 0, 22), center.add(0, 0, -22)
		};
		for (BlockPos base : pillars) {
			for (int y = 1; y <= 14; y++) {
				world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (y % 3 == 0) {
					world.setBlockState(base.up(y).east(), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
					world.setBlockState(base.up(y).west(), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
			world.setBlockState(base.up(15), Blocks.BEDROCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			EndCrystalEntity crystal = spawnCrystal(world, base.up(16));
			if (crystal != null) world.spawnEntity(crystal);

			// Defense turrets on mid platforms
			placeTurretPad(world, base.add(3, 8, 0), ModWorldBlocks.LASER_TURRET);
			placeTurretPad(world, base.add(-3, 8, 0), ModWorldBlocks.PLASMA_TURRET);
			placeTurretPad(world, base.add(0, 8, 3), ModWorldBlocks.POINT_DEFENSE_TURRET);
		}

		// Inner ring volume turrets
		for (int i = 0; i < 6; i++) {
			double ang = i * Math.PI * 2 / 6;
			BlockPos t = center.add((int) (Math.cos(ang) * 14), 2, (int) (Math.sin(ang) * 14));
			placeTurretPad(world, t, ModWorldBlocks.VOLUME_TURRET);
		}

		// Approach bridges toward outer islands
		for (int d = 28; d <= 48; d++) {
			world.setBlockState(center.add(d, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(center.add(-d, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(center.add(0, 0, d), Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(center.add(0, 0, -d), Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	private static void placeTurretPad(ServerWorld world, BlockPos pad, Block turret) {
		world.setBlockState(pad, Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
		world.setBlockState(pad.up(), turret.getDefaultState(), Block.NOTIFY_ALL);
	}

	private static EndCrystalEntity spawnCrystal(ServerWorld world, BlockPos at) {
		EndCrystalEntity c = net.minecraft.entity.EntityType.END_CRYSTAL.create(world);
		if (c == null) return null;
		c.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		c.setShowBottom(false);
		return c;
	}

	private static void spawnBoss(ServerWorld world, BlockPos center) {
		EndReactorBossEntity boss = ModEntities.END_REACTOR_BOSS.create(world);
		if (boss == null) return;
		boss.refreshPositionAndAngles(center.getX() + 0.5, center.getY() + 10.0, center.getZ() + 0.5, 0, 0);
		boss.setAnchor(new Vec3d(center.getX() + 0.5, center.getY() + 12.0, center.getZ() + 0.5));
		world.spawnEntity(boss);

		ReactorKeeperEntity keeper = ModEntities.REACTOR_KEEPER.create(world);
		if (keeper != null) {
			keeper.refreshPositionAndAngles(center.getX() + 8.5, center.getY() + 12, center.getZ() + 0.5, 0, 0);
			world.spawnEntity(keeper);
		}
	}

	/**
	 * Victory marker — the dragon-fight analogue.
	 *
	 * <p>In the End dimension that is a ring of gateways home, as vanilla does it. In the END band
	 * there is nowhere to portal <em>to</em>: the way back is the same column you flew up, so the
	 * reward is a landing pad and a lit trophy instead of four gateways that would teleport a pilot
	 * out of a world they never left.
	 */
	public static void placeExitGateways(ServerWorld world, BlockPos at) {
		world.setBlockState(at, Blocks.DRAGON_EGG.getDefaultState(), Block.NOTIFY_ALL);

		if (world.getRegistryKey() != World.END) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx * dx + dz * dz > 9) continue;
					world.setBlockState(at.add(dx, -1, dz),
							Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
			for (int i = 0; i < 4; i++) {
				double ang = i * Math.PI * 0.5;
				world.setBlockState(at.add((int) (Math.cos(ang) * 3), 0, (int) (Math.sin(ang) * 3)),
						Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			return;
		}

		ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
		BlockPos exit = overworld != null ? overworld.getSpawnPos() : BlockPos.ORIGIN;
		for (int i = 0; i < 4; i++) {
			double ang = i * Math.PI * 0.5;
			BlockPos g = at.add((int) (Math.cos(ang) * 6), 0, (int) (Math.sin(ang) * 6));
			world.setBlockState(g, Blocks.END_GATEWAY.getDefaultState(), Block.NOTIFY_ALL);
			BlockEntity be = world.getBlockEntity(g);
			if (be instanceof EndGatewayBlockEntity gateway) {
				gateway.setExitPortalPos(exit, true);
			}
		}
	}

	/**
	 * Count living shield crystals around the arena — the boss is invulnerable while any stand.
	 *
	 * <p>The search box follows the arena rather than being fixed at y 40..120. Hardcoded, it only
	 * ever matched a base sitting on an End island; up in the END band it would find nothing, the
	 * shield would read as already down, and the fight would open with the boss exposed.
	 */
	public static int countShieldCrystals(ServerWorld world) {
		BlockPos c = arenaCenter(world);
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
				c.getX() - 40, c.getY() - 8, c.getZ() - 40,
				c.getX() + 40, c.getY() + 48, c.getZ() + 40);
		return world.getEntitiesByClass(EndCrystalEntity.class, box, EndCrystalEntity::isAlive).size();
	}
}
