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
	private DescentSession() {}

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

		// Keep SERVER_STARTED seeding tiny — bulk megastructures used to block the server
		// thread for >60s (Watchdog / "crash on join"). Distant landmarks come from
		// CHUNK_LOAD worldgen after live generation is enabled.
		BlockPos under = new BlockPos(spawn.getX(), WorldRules.INDUSTRIAL_Y_MIN + 30, spawn.getZ());
		IndustrialComplexGenerator.generateAt(world, under, WorldRules.ComplexStyle.CRYSTAL_REACTOR, world.getRandom());
		var ufo = ModEntities.SKY_UFO.create(world);
		if (ufo != null) {
			ufo.refreshPositionAndAngles(spawn.getX() + 100.5, WorldRules.SKY_PRACTICAL_MIN + 48,
					spawn.getZ() + 40.5, 0, 0);
			world.spawnEntity(ufo);
		}

		state.setStockSeeded(true);
		DescentMod.LOGGER.info("Descent stock seed complete (hub + under-spawn complex + sky UFO)");
	}

	private static void giveIfPresent(ServerPlayerEntity player, net.minecraft.item.Item item) {
		if (item == null) return;
		player.giveItemStack(new ItemStack(item));
	}

	/** Soft player onboarding — 6DoF on, tip message, creative gets Pyro GX. */
	public static void onPlayerJoin(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		data.ensureInit();
		// Always fully arm thrusters on join (clear foot gravity / setNoGravity / sync).
		com.terminaldetector.drmd.flight.FlightSystem.enable(player);

		boolean first = !data.isSessionWelcomed();
		if (first) {
			data.setSessionWelcomed(true);
			player.sendMessage(Text.literal(
					"§bDRMD 6DOF §f— thrusters ON. Mouse = full sphere · WASD/Space/Shift = fly."), false);
			player.sendMessage(Text.literal(
					"§7§fH§7 toggles thrusters off/on · creative tab §fDRMD 6DOF§7 · Pyro GX nearby"), false);
			player.sendMessage(Text.literal(
					"§8Crashed UFO is trap-dense — bring Pyro GX before clearing."), false);
			if (player.isCreative()) {
				giveIfPresent(player, com.terminaldetector.drmd.weapon.items.SessionControlItems.REACTOR_STARTER);
				giveIfPresent(player, com.terminaldetector.drmd.weapon.items.SessionControlItems.SIXDOF_CORE);
				giveIfPresent(player, com.terminaldetector.drmd.weapon.items.SessionControlItems.STARTER_KIT);
				giveIfPresent(player, ModItems.PYRO_GX);
				player.sendMessage(Text.literal(
						"§aCreative: session tools + Pyro GX — ПКМ на «Запуск реактора» без консоли."), false);
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
			MegaStructureGenerator.generate(world, at, kind, Random.create(world.getSeed() ^ (i * 31L)));
		}

		// One industrial complex under spawn
		BlockPos under = new BlockPos(spawn.getX(), WorldRules.INDUSTRIAL_Y_MIN + 30, spawn.getZ());
		IndustrialComplexGenerator.generateAt(world, under, WorldRules.ComplexStyle.CRYSTAL_REACTOR, random);
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
		IndustrialComplexGenerator.generateAt(world, depth, styles[2 % styles.length], random);

		// Surface corridor — canyon + rift pair
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() + 96, WorldRules.INDUSTRIAL_Y_MAX - 8, spawn.getZ() - 40),
				MacroEntry.Kind.CANYON, Random.create(world.getSeed() ^ 0xC0FFEE));
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() - 110, WorldRules.INDUSTRIAL_Y_MIN + 36, spawn.getZ() - 90),
				MacroEntry.Kind.RIFT, Random.create(world.getSeed() ^ 0xBEEF));

		// Sky archipelago sample
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() + 48, WorldRules.SKY_PRACTICAL_MIN + 40, spawn.getZ() + 120),
				MacroEntry.Kind.FLOATING_CONTINENT, Random.create(world.getSeed() ^ 0x51A10001L));

		// Orbital belt (top practical band)
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() - 80, WorldRules.SKY_PRACTICAL_MAX - 12, spawn.getZ() + 60),
				MacroEntry.Kind.RING, Random.create(world.getSeed() ^ 0x0B817100L));

		// Near-end space marker island
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() + 20, WorldRules.SKY_PRACTICAL_MAX - 4, spawn.getZ() - 140),
				MacroEntry.Kind.INVERTED_ISLAND, Random.create(world.getSeed() ^ 0xEAD10001L));

		// Descent 1 lunar base (sky) — micro-reactor + Keeper
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() - 140, WorldRules.SKY_PRACTICAL_MIN + 55, spawn.getZ() + 90),
				MacroEntry.Kind.LUNAR_BASE, Random.create(world.getSeed() ^ 0x10AAB001L));

		// Crashed XCOM UFO — dense traps; tip: clear with Pyro GX
		MegaStructureGenerator.generate(world,
				new BlockPos(spawn.getX() + 180, 80, spawn.getZ() - 60),
				MacroEntry.Kind.CRASHED_UFO, Random.create(world.getSeed() ^ 0x0F00A001L));

		// One airborne UFO near spawn sky lane
		var ufo = ModEntities.SKY_UFO.create(world);
		if (ufo != null) {
			ufo.refreshPositionAndAngles(spawn.getX() + 100.5, WorldRules.SKY_PRACTICAL_MIN + 48,
					spawn.getZ() + 40.5, 0, 0);
			world.spawnEntity(ufo);
		}
	}
}
