package com.terminaldetector.drmd.world.end;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.world.level.WorldLevels;
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
	 *
	 * <p>An offset from {@link WorldLevels#ORBITAL_TOP}, not a bare Y — this was {@code 940} when the
	 * END band was 880…1024 (60 above its own floor), and stayed {@code 940} across a rescale that
	 * moved the band to 1580…1888. The arena would have sat inside the Orbital band instead, five
	 * hundred blocks under the islands it is meant to be dug into.
	 */
	private static final int END_BAND_ARENA_Y = WorldLevels.ORBITAL_TOP + 60;

	public static void onServerTick(MinecraftServer server) {
		ServerWorld end = server.getWorld(World.END);
		// Vanilla mode: leave the real End's own dragons alone.
		if (end != null && WorldLevels.isAdvancedColumn(end)) suppressDragons(end);
		for (ServerWorld world : arenaWorlds(server)) {
			if (playerNearArena(world)) ensureBase(world);
		}
	}

	/**
	 * Nothing is built here.
	 *
	 * <p>This used to raise both arenas on the spot. Each one clears a radius-30 cylinder — some
	 * sixty thousand block writes — and doing two of them in a single task on the server thread, at
	 * the moment the world opens, stalled startup at "Preparing spawn 100%". The End's copy was
	 * worse still: its chunks do not exist yet, so the clear had to generate them first.
	 *
	 * <p>The arena is raised when a player is actually near it instead — see {@link #onServerTick}.
	 * It is a one-off per world, {@code EndReactorState} remembers it, and until someone flies up to
	 * the band or steps into the End there is no reason for it to exist.
	 */
	public static void onServerStarted(MinecraftServer server) {
		// Intentionally empty.
	}

	/**
	 * Is anyone close enough to justify raising the arena?
	 *
	 * <p>Deliberately does not call {@link #arenaCenter}: for the End that samples a heightmap, which
	 * generates the centre chunk. Distance is measured against the column axis instead, so the check
	 * itself never touches the world.
	 */
	private static boolean playerNearArena(ServerWorld world) {
		boolean band = world.getRegistryKey() == World.OVERWORLD;
		for (var player : world.getPlayers()) {
			double x = player.getX();
			double z = player.getZ();
			if (x * x + z * z > ARENA_WAKE_RADIUS * ARENA_WAKE_RADIUS) continue;
			// In the band the arena is one level of many, so surface play near the origin must not
			// wake it — only flying up into the END band / Oblivion seam does.
			if (band) {
				double dy = Math.abs(player.getY() - END_BAND_ARENA_Y);
				// SeamWarmup starts at ORBITAL_TOP±72 — wake earlier than full 256 Y so the
				// arena exists before the critical 10-block seam approach.
				double yWake = com.terminaldetector.drmd.world.layer.SeamWarmup.nearEndSeam(player.getY())
						? 120.0 : ARENA_WAKE_RADIUS;
				if (dy > yWake) continue;
			}
			return true;
		}
		return false;
	}

	/** How close a player has to be before the arena is raised. */
	private static final int ARENA_WAKE_RADIUS = 256;

	private static java.util.List<ServerWorld> arenaWorlds(MinecraftServer server) {
		java.util.ArrayList<ServerWorld> worlds = new java.util.ArrayList<>(2);
		// Overworld End-band arena only when the custom band is enabled; otherwise leave the
		// upper column identical to stock Minecraft sky.
		if (com.terminaldetector.drmd.world.WorldFeatures.END_BAND) {
			ServerWorld overworld = server.getWorld(World.OVERWORLD);
			if (overworld != null) worlds.add(overworld);
		}
		ServerWorld end = server.getWorld(World.END);
		// Vanilla mode: the real End is a normal dragon-fight dimension, not a reactor arena.
		if (end != null && WorldLevels.isAdvancedColumn(end)) worlds.add(end);
		return worlds;
	}

	/** Called each dragon-fight tick — remove dragons, keep our fight. */
	public static void suppressDragons(ServerWorld end) {
		for (EnderDragonEntity dragon : end.getAliveEnderDragons()) {
			dragon.discard();
		}
	}

	/**
	 * Mixin entry — cheap every tick. Heavy arena build only when a player is near
	 * (or the base already exists and needs a boss check).
	 */
	public static void onDragonFightTick(ServerWorld end) {
		try {
			suppressDragons(end);
			EndReactorState state = EndReactorState.get(end);
			if (state.isBaseGenerated()) {
				ensureBossAlive(end, state);
				return;
			}
			if (!playerNearArena(end)) return;
			ensureBase(end);
		} catch (Exception e) {
			// Dragon-fight mixin runs every End tick — never let arena code kill the server.
			DescentMod.LOGGER.error("End reactor tick failed — skipped", e);
		}
	}

	public static void ensureBase(ServerWorld end) {
		EndReactorState state = EndReactorState.get(end);
		if (state.isBaseGenerated()) {
			ensureBossAlive(end, state);
			return;
		}
		BlockPos center = arenaCenter(end);
		try {
			generateBase(end, center);
			spawnBoss(end, center);
			state.setBaseGenerated(true);
			state.setPhase(EndReactorState.Phase.SHIELDED);
			DescentMod.LOGGER.info("Reactor arena generated in {} at {}",
					end.getRegistryKey().getValue(), center.toShortString());
		} catch (Exception e) {
			// Never take down the integrated server over a bad rail / block write.
			DescentMod.LOGGER.error("Reactor arena generate failed in {} at {}",
					end.getRegistryKey().getValue(), center.toShortString(), e);
		}
	}

	private static void ensureBossAlive(ServerWorld end, EndReactorState state) {
		if (state.isDefeated()) return;
		boolean alive = !end.getEntitiesByType(ModEntities.END_REACTOR_BOSS, e -> e.isAlive()).isEmpty();
		if (!alive && state.getPhase() != EndReactorState.Phase.DEFEATED) {
			spawnBoss(end, arenaCenter(end));
		}
	}

	/**
	 * Builds the station shell. Skeleton for now (Phase 2 of the Citadel redesign, see
	 * {@code CitadelStationGenerator}): exterior hull + central shaft + six deck floors only — no
	 * turrets, shield crystals, reactor pedestal, or approach bridges yet, those are later phases.
	 * {@code placeTurretPad}/{@code spawnCrystal} below are temporarily unused between this phase and the
	 * next, kept rather than deleted since they're exactly what the next phase re-wires, just from new
	 * per-deck coordinates instead of the old flat disc's.
	 */
	public static void generateBase(ServerWorld world, BlockPos center) {
		CitadelStationGenerator.generate(world, center);
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
		// Pre-boss Oblivion Seekers — solo hunters that still paint aggro onto End machines.
		OblivionSeekerSpawner.spawnArenaEscorts(world, center, 3);
	}

	/**
	 * Victory marker — the dragon-fight analogue (primary ending).
	 *
	 * <p>In the End dimension that is a ring of gateways home, as vanilla does it. In the END band
	 * there is nowhere to portal <em>to</em>: the way back is the same column you flew up, so the
	 * reward is a landing pad and a lit trophy instead of four gateways that would teleport a pilot
	 * out of a world they never left.
	 *
	 * <p>Second ending (escape / survive aftermath) needs nothing special here — dig path,
	 * {@code ReactorAftermath}, and DimensionSync already cover it.
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
