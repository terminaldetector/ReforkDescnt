package com.terminaldetector.drmd.world.base;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MegaStructureGenerator;
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
	 * Landmarks waiting to be built, one per tick.
	 *
	 * <p>Seeding used to run the whole set inline on SERVER_STARTED. That is seventeen structures —
	 * a megacity among them — each forcing chunk loads hundreds of blocks from spawn, all on the
	 * server thread before the world was up. It ran past the 60 s watchdog, which the player sees as
	 * a crash on join. The work itself is fine; doing it in one tick is not. Same shape as
	 * {@link com.terminaldetector.drmd.world.level.LevelBuilder}: queue it, spend a fixed budget per
	 * tick, and the landmarks are all present within a second of the world opening.
	 */
	private static final java.util.ArrayDeque<Runnable> SEED_QUEUE = new java.util.ArrayDeque<>();

	private DescentSession() {}

	/** Build at most one queued landmark. Called every server tick. */
	public static void drainSeedQueue() {
		Runnable job = SEED_QUEUE.poll();
		if (job == null) return;
		try {
			job.run();
		} catch (Exception e) {
			// One bad landmark must not take the rest of the queue — or the world — with it.
			DescentMod.LOGGER.error("Descent landmark seed failed", e);
		}
	}

	public static void clearSeedQueue() {
		SEED_QUEUE.clear();
	}

	private static void enqueue(Runnable job) {
		SEED_QUEUE.add(job);
	}

	/** Called once when overworld is ready — seed hub + nearby stock features. */
	public static void seedWorld(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (world == null) return;
		DescentWorldState state = DescentWorldState.get(world);
		if (state.isStockSeeded()) return;

		BlockPos spawn = world.getSpawnPos();
		int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		BlockPos hub = new BlockPos(spawn.getX() + 24, Math.max(surfaceY + 12, 90), spawn.getZ() + 24);

		if (!state.isSpawnHubGenerated()) {
			generateSpawnHub(world, hub);
			state.setSpawnHubGenerated(true);
			DescentMod.LOGGER.info("Descent spawn hub generated at {}", hub.toShortString());
		}

		seedStockMegastructures(world, spawn);
		seedLayerBiomes(world, spawn);
		state.setStockSeeded(true);
		DescentMod.LOGGER.info("Descent stock worldgen seeded (all practical biome layers)");
	}

	/** Soft player onboarding — 6DoF on, tip message, creative gets Pyro GX. */
	public static void onPlayerJoin(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		data.ensureInit();

		boolean first = !data.isSessionWelcomed();
		if (first) {
			// 6DoF is the default way to move in this world, but only the first join decides that.
			// Re-asserting it every login would keep overriding a pilot who switched it off with H —
			// which in creative means their building flight gets taken away on every reconnect.
			data.setEnabled(true);
			data.setSessionWelcomed(true);
			player.sendMessage(Text.literal(
					"§bDRMD 6DOF §f— Descent session is part of this world."), false);
			player.sendMessage(Text.literal(
					"§7Fly with §fH§7 · craft §fPyro GX§7 · lunar base / sky UFO / crashed saucer nearby"), false);
			player.sendMessage(Text.literal(
					"§8Crashed UFO is trap-dense — bring Pyro GX before clearing."), false);
			if (player.isCreative()) {
				player.giveItemStack(new ItemStack(ModItems.PYRO_GX));
				player.sendMessage(Text.literal("§aCreative: Pyro GX given — right-click to deploy."), false);
			}
		}
	}

	private static void generateSpawnHub(ServerWorld world, BlockPos center) {
		IndustrialComplexGenerator.generateAt(world, center, WorldRules.ComplexStyle.ANCIENT_POWER, world.getRandom());

		PyroShipEntity ship = ModEntities.PYRO_SHIP.create(world);
		if (ship != null) {
			ship.refreshPositionAndAngles(center.getX() + 0.5, center.getY() - 4, center.getZ() + 8.5, 0, 0);
			world.spawnEntity(ship);
		}

		AiRole[] roles = {
				AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.MG, AiRole.LASER, AiRole.RPG,
				AiRole.ARTILLERY, AiRole.SUPPORT, AiRole.HEAVY, AiRole.SEEKER, AiRole.HEAVY_ELITE
		};
		for (int i = 0; i < roles.length; i++) {
			DroneEntity drone = ModEntities.DRONE.create(world);
			if (drone == null) continue;
			double ang = i * (Math.PI * 2 / roles.length);
			drone.refreshPositionAndAngles(
					center.getX() + Math.cos(ang) * 18,
					center.getY() + (i % 3) * 2,
					center.getZ() + Math.sin(ang) * 18,
					0, 0);
			drone.applyRole(roles[i]);
			world.spawnEntity(drone);
		}

		// Multi-zone gravity preview (dynamic station sections)
		world.setBlockState(center.add(0, -6, 0),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_GENERATOR.getDefaultState()
						.with(com.terminaldetector.drmd.world.gravity.GravityGeneratorBlock.FACING,
								net.minecraft.util.math.Direction.DOWN),
				net.minecraft.block.Block.NOTIFY_ALL);
		world.setBlockState(center.add(12, -4, 0),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState()
						.with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,
								net.minecraft.util.math.Direction.EAST),
				net.minecraft.block.Block.NOTIFY_ALL);
		world.setBlockState(center.add(-12, -4, 0),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState()
						.with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,
								net.minecraft.util.math.Direction.WEST),
				net.minecraft.block.Block.NOTIFY_ALL);
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
			enqueue(() -> MegaStructureGenerator.generate(world, at, kind, Random.create(salt)));
		}

		// One industrial complex under spawn
		BlockPos under = new BlockPos(spawn.getX(), WorldRules.INDUSTRIAL_Y_MIN + 30, spawn.getZ());
		enqueue(() -> IndustrialComplexGenerator.generateAt(
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
		enqueue(() -> IndustrialComplexGenerator.generateAt(world, depth, styles[2 % styles.length], random));

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

		// Cyberpunk megacity — far enough out that spawn stays open sky, close enough to be the
		// obvious first destination once you have a ship. Its ground level is sampled at build time:
		// reading a heightmap 320 blocks out forces that chunk to load, which is the whole reason
		// this is off the join path.
		int cityX = spawn.getX() - 320;
		int cityZ = spawn.getZ() + 280;
		enqueue(() -> {
			int citySurface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cityX, cityZ);
			MegaStructureGenerator.generate(world,
					new BlockPos(cityX, Math.max(citySurface, WorldRules.INDUSTRIAL_Y_MAX + 24), cityZ),
					MacroEntry.Kind.MEGACITY, Random.create(world.getSeed() ^ 0xC1740001L));
		});

		// One airborne UFO near spawn sky lane
		enqueue(() -> {
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
		enqueue(() -> MegaStructureGenerator.generate(world, at, kind, Random.create(world.getSeed() ^ salt)));
	}
}
