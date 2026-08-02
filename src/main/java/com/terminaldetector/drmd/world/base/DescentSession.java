package com.terminaldetector.drmd.world.base;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.DrmdServerConfig;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MegaStructureGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Makes Descent a native Minecraft mode: world-spawn hub, stock megastructures,
 * auto 6DoF for every player — not an optional /d6-only session.
 */
public final class DescentSession {
	/**
	 * Landmarks waiting for someone to come near them.
	 *
	 * <p>Seeding first ran the whole set inline on SERVER_STARTED — seventeen structures on the
	 * server thread before the world was up. Spreading them one per tick was not enough, because the
	 * unit is wrong: a single landmark is not small. The megacity alone is a 112-block grid of
	 * towers, streets and sewers standing 320 blocks from spawn, where no chunk is loaded yet, so
	 * every write there generates the chunk under it first. One of those in one tick is minutes of
	 * frozen server however patiently the queue is drained.
	 *
	 * <p>So the trigger is distance, not time. A landmark is raised when a player gets within
	 * {@link #SEED_RADIUS} of it — which at spawn means the hub and the complex directly underneath,
	 * and nothing else. The rest wait until you fly out to them, by which point their chunks are
	 * loading anyway.
	 */
	private record SeedJob(BlockPos near, Runnable build) {}

	private static final java.util.ArrayDeque<SeedJob> SEED_QUEUE = new java.util.ArrayDeque<>();

	/** How close a player has to be before a queued landmark is built. */
	private static final int SEED_RADIUS = 256;

	private DescentSession() {}

	/** Build up to a few landmarks near players. Called every server tick. */
	public static void drainSeedQueue(MinecraftServer server) {
		if (SEED_QUEUE.isEmpty()) return;
		ServerWorld world = server.getOverworld();
		if (world == null) return;
		int budget = SEED_QUEUE.size() > 12 ? 3 : 1;
		while (budget-- > 0) {
			boolean built = false;
			for (var it = SEED_QUEUE.iterator(); it.hasNext(); ) {
				SeedJob job = it.next();
				if (!playerWithinSeedRange(world, job.near())) continue;
				it.remove();
				try {
					job.build().run();
				} catch (Exception e) {
					// One bad landmark must not take the rest of the queue — or the world — with it.
					DescentMod.LOGGER.error("Descent landmark seed failed", e);
				}
				built = true;
				break;
			}
			if (!built) return;
		}
	}

	/** Horizontal distance only — a landmark can span most of the column's height. */
	private static boolean playerWithinSeedRange(ServerWorld world, BlockPos at) {
		for (var player : world.getPlayers()) {
			double dx = player.getX() - at.getX();
			double dz = player.getZ() - at.getZ();
			if (dx * dx + dz * dz <= (double) SEED_RADIUS * SEED_RADIUS) return true;
		}
		return false;
	}

	public static void clearSeedQueue() {
		SEED_QUEUE.clear();
	}

	private static void enqueue(BlockPos near, Runnable job) {
		SEED_QUEUE.add(new SeedJob(near.toImmutable(), job));
	}

	/** Public landmark enqueue for satellite structures (psychedelic fractals, etc.). */
	public static void enqueueLandmark(BlockPos near, Runnable job) {
		enqueue(near, job);
	}

	/** Called once when overworld is ready — seed hub + nearby stock features. */
	public static void seedWorld(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (world == null) return;
		DescentWorldState state = DescentWorldState.get(world);

		BlockPos spawn = world.getSpawnPos();
		int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		BlockPos hub = new BlockPos(spawn.getX() + 24, Math.max(surfaceY + 12, 90), spawn.getZ() + 24);

		// Hub must re-queue even when stockSeeded — queue is wiped every boot.
		if (!state.isSpawnHubGenerated() && !state.isPsychedelic()) {
			BlockPos hubAt = hub.toImmutable();
			enqueueLandmark(hubAt, () -> {
				if (DescentWorldState.get(world).isSpawnHubGenerated()) return;
				generateSpawnHub(world, hubAt);
				DescentWorldState.get(world).setSpawnHubGenerated(true);
				DescentMod.LOGGER.info("Descent lunar hub generated at {}", hubAt.toShortString());
			});
		}

		if (state.isStockSeeded()) return;

		// Stock option: psychedelic fractal void worlds (weightless orbital start)
		if (DrmdServerConfig.psychedelicEnabled() || state.isPsychedelic()) {
			PsychedelicWorldgen.seed(server, state);
			state.setStockSeeded(true);
			DescentMod.LOGGER.info("Descent psychedelic stock seeded — variant=#{}",
					state.getPsychedelicVariant());
			return;
		}

		// HL2 path: megacity + nearby combat landmarks without full MACRO_WORLDGEN spam.
		if (WorldFeatures.SURFACE_DISTRICTS) {
			seedSurfaceDistricts(world, spawn);
		}
		if (WorldFeatures.MACRO_WORLDGEN) {
			seedStockMegastructures(world, spawn);
			seedLayerBiomes(world, spawn);
		}
		state.setStockSeeded(true);
		DescentMod.LOGGER.info("Descent stock worldgen seeded (districts={} macro={})",
				WorldFeatures.SURFACE_DISTRICTS, WorldFeatures.MACRO_WORLDGEN);
	}

	/**
	 * Soft player onboarding — 6DoF is native every join.
	 *
	 * <p>Saving {@code enabled=false} + {@code sessionWelcomed} used to leave pilots stuck on
	 * vanilla walk until they found H; after reconnect the client attitude often never primed.
	 * This world <em>is</em> Descent — re-assert flight each login; H still toggles for the session.
	 */
	public static void onPlayerJoin(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		data.ensureInit();

		ServerWorld world = player.getServerWorld();
		DescentWorldState state = DescentWorldState.get(world);
		if (state.isPsychedelic()) {
			if (!data.isSessionWelcomed()) {
				PsychedelicWorldgen.onPlayerJoin(player, state);
			}
			// Psychedelic tip path only setEnabled(true) — still need noGravity + sync + abilities.
			com.terminaldetector.drmd.flight.FlightSystem.enable(player, data);
			return;
		}

		// Native session: always 6DoF on join (H toggles off until next login).
		com.terminaldetector.drmd.flight.FlightSystem.enable(player, data);

		boolean first = !data.isSessionWelcomed();
		if (first) {
			data.setSessionWelcomed(true);
			player.sendMessage(Text.literal(
					"§bDRMD 6DOF §f— Descent session is part of this world."), false);
			player.sendMessage(Text.literal(
					"§7Pad: §fLunar Base§7 · §fR§7 afterburner · §fH§7 6DoF · §f,§7 settings · §fN§7 ship"), false);
			String cityTip = com.terminaldetector.drmd.world.surface.MegacityRegions
					.describeNearest(player.getBlockX(), player.getBlockZ());
			player.sendMessage(Text.literal(
					"§7Explore: UFO/outpost events · biome plates · 6DoF dungeons"), false);
			player.sendMessage(Text.literal(
					"§8" + cityTip + " · /d6 megacity|technogenic|scorched|guild|weapons give_all"), false);
			if (player.isCreative()) {
				player.giveItemStack(new ItemStack(ModItems.PYRO_GX));
				for (Item w : com.terminaldetector.drmd.weapon.registry.ArsenalCatalog.creativeWeapons()) {
					player.giveItemStack(new ItemStack(w));
				}
				for (Item egg : ModItems.creativeSpawnEggs()) {
					player.giveItemStack(new ItemStack(egg));
				}
				player.sendMessage(Text.literal(
						"§aCreative: Pyro + arsenal + spawn eggs · options kit / /d6 weapons give_all"), false);
			}
		}
	}

	/**
	 * Descent 1 lunar-base hub at spawn — grey disc, turrets, micro-reactor, Keeper —
	 * plus a Pyro pad and a light friendly drone ring for first contact.
	 */
	private static void generateSpawnHub(ServerWorld world, BlockPos center) {
		MegaStructureGenerator.generate(world, center, MacroEntry.Kind.LUNAR_BASE, world.getRandom());

		PyroShipEntity ship = ModEntities.PYRO_SHIP.create(world);
		if (ship != null) {
			ship.refreshPositionAndAngles(center.getX() + 0.5, center.getY() + 8, center.getZ() + 14.5, 0, 0);
			world.spawnEntity(ship);
		}

		// Friendly pad escorts — SUPPORT only so the hub is not a death ring on join.
		for (int i = 0; i < 4; i++) {
			DroneEntity drone = ModEntities.DRONE.create(world);
			if (drone == null) continue;
			double ang = i * (Math.PI * 2 / 4);
			drone.refreshPositionAndAngles(
					center.getX() + Math.cos(ang) * 26,
					center.getY() + 4 + (i % 2) * 2,
					center.getZ() + Math.sin(ang) * 26,
					0, 0);
			drone.applyRole(AiRole.SUPPORT);
			world.spawnEntity(drone);
		}

		world.setBlockState(center.add(0, -1, 0),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_GENERATOR.getDefaultState()
						.with(com.terminaldetector.drmd.world.gravity.GravityGeneratorBlock.FACING,
								net.minecraft.util.math.Direction.DOWN),
				net.minecraft.block.Block.NOTIFY_ALL);
		world.setBlockState(center.add(14, 1, 0),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState()
						.with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,
								net.minecraft.util.math.Direction.EAST),
				net.minecraft.block.Block.NOTIFY_ALL);
		world.setBlockState(center.add(-14, 1, 0),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState()
						.with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,
								net.minecraft.util.math.Direction.WEST),
				net.minecraft.block.Block.NOTIFY_ALL);
	}

	/**
	 * Surface districts path — hub is pad-only.
	 * UFOs / outposts / ruins are random {@link com.terminaldetector.drmd.world.surface.SurfaceEventWorldgen}
	 * events (≥400 m from spawn), not a spawn-tied package. Biome plates own their own dungeons.
	 */
	private static void seedSurfaceDistricts(ServerWorld world, BlockPos spawn) {
		DescentMod.LOGGER.info(
				"Surface districts: hub pad only @ spawn {}; events/plates discover on chunk load",
				spawn.toShortString());
	}

	private static void seedStockMegastructures(ServerWorld world, BlockPos spawn) {
		Random random = world.getRandom();
		MacroEntry.Kind[] kinds = {
				MacroEntry.Kind.ARCH, MacroEntry.Kind.RING, MacroEntry.Kind.FLOATING_CONTINENT,
				MacroEntry.Kind.SPIRAL_RANGE, MacroEntry.Kind.INVERTED_ISLAND,
				MacroEntry.Kind.CANYON, MacroEntry.Kind.RIFT
		};
		for (int i = 0; i < kinds.length; i++) {
			MacroEntry.Kind kind = kinds[i];
			double ang = i * (Math.PI * 2 / kinds.length);
			int dist = 160 + i * 36;
			int x = spawn.getX() + (int) (Math.cos(ang) * dist);
			int z = spawn.getZ() + (int) (Math.sin(ang) * dist);
			int y = switch (kind) {
				case RIFT, CANYON -> WorldRules.INDUSTRIAL_Y_MIN + 28;
				default -> WorldRules.SKY_PRACTICAL_MIN + 20 + i * 8;
			};
			BlockPos at = new BlockPos(x, y, z);
			// The loop counter is not effectively final, so the seed is resolved before capture.
			long salt = world.getSeed() ^ (i * 31L);
			enqueue(at, () -> MegaStructureGenerator.generate(world, at, kind, Random.create(salt)));
		}

		// One industrial complex under spawn
		BlockPos under = new BlockPos(spawn.getX(), WorldRules.INDUSTRIAL_Y_MIN + 30, spawn.getZ());
		enqueue(under, () -> IndustrialComplexGenerator.generateAt(
				world, under, WorldRules.ComplexStyle.CRYSTAL_REACTOR, random));
	}

	/**
	 * Ensure every practical Descent biome layer has a seeded landmark near spawn
	 * so HUD BIOME / LAYER readouts and exploration routes are immediately playable.
	 */
	private static void seedLayerBiomes(ServerWorld world, BlockPos spawn) {
		Random random = world.getRandom();
		WorldRules.ComplexStyle[] styles = WorldRules.ComplexStyle.values();

		// Depth reactors — second industrial node
		BlockPos depth = new BlockPos(spawn.getX() - 64, WorldRules.INDUSTRIAL_Y_MIN + 18, spawn.getZ() + 48);
		enqueue(depth, () -> IndustrialComplexGenerator.generateAt(world, depth, styles[2 % styles.length], random));

		// Surface corridor — canyon + rift pair
		enqueueMega(world, new BlockPos(spawn.getX() + 96, WorldRules.INDUSTRIAL_Y_MAX - 8, spawn.getZ() - 40),
				MacroEntry.Kind.CANYON, 0xC0FFEE);
		enqueueMega(world, new BlockPos(spawn.getX() - 110, WorldRules.INDUSTRIAL_Y_MIN + 36, spawn.getZ() - 90),
				MacroEntry.Kind.RIFT, 0xBEEF);

		// Sky archipelago sample
		enqueueMega(world, new BlockPos(spawn.getX() + 48, WorldRules.SKY_PRACTICAL_MIN + 40, spawn.getZ() + 120),
				MacroEntry.Kind.FLOATING_CONTINENT, 0x51A10001L);

		// Orbital belt (top practical band)
		enqueueMega(world, new BlockPos(spawn.getX() - 80, WorldRules.SKY_PRACTICAL_MAX - 12, spawn.getZ() + 60),
				MacroEntry.Kind.RING, 0x0B817100L);

		// Near-end space marker island
		enqueueMega(world, new BlockPos(spawn.getX() + 20, WorldRules.SKY_PRACTICAL_MAX - 4, spawn.getZ() - 140),
				MacroEntry.Kind.INVERTED_ISLAND, 0xEAD10001L);

		// Descent 1 lunar base (sky) — micro-reactor + Keeper
		enqueueMega(world, new BlockPos(spawn.getX() - 140, WorldRules.SKY_PRACTICAL_MIN + 55, spawn.getZ() + 90),
				MacroEntry.Kind.LUNAR_BASE, 0x10AAB001L);

		// Crashed XCOM UFO — dense traps; tip: clear with Pyro GX
		enqueueMega(world, new BlockPos(spawn.getX() + 180, 80, spawn.getZ() - 60),
				MacroEntry.Kind.CRASHED_UFO, 0x0F00A001L);

		// Megacity is biome-region based ({@link com.terminaldetector.drmd.world.surface.MegacityRegions}),
		// not a spawn-offset landmark — see MegacityBiomeWorldgen.

		// One airborne UFO near spawn sky lane
		enqueue(new BlockPos(spawn.getX() + 100, WorldRules.SKY_PRACTICAL_MIN + 48, spawn.getZ() + 40), () -> {
			var ufo = ModEntities.SKY_UFO.create(world);
			if (ufo != null) {
				ufo.refreshPositionAndAngles(spawn.getX() + 100.5, WorldRules.SKY_PRACTICAL_MIN + 48,
						spawn.getZ() + 40.5, 0, 0);
				world.spawnEntity(ufo);
			}
		});
	}

	/** Queue one megastructure; the salt is mixed with the world seed at build time. */
	private static void enqueueMega(ServerWorld world, BlockPos at, MacroEntry.Kind kind, long salt) {
		enqueue(at, () -> MegaStructureGenerator.generate(world, at, kind, Random.create(world.getSeed() ^ salt)));
	}
}
