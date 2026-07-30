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

		seedStockMegastructures(world, spawn);
		state.setStockSeeded(true);
		DescentMod.LOGGER.info("Descent stock worldgen seeded around spawn");
	}

	/** Soft player onboarding — 6DoF on, tip message, creative gets Pyro GX. */
	public static void onPlayerJoin(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		data.ensureInit();
		if (!data.isEnabled()) {
			data.setEnabled(true);
		}

		boolean first = !data.isSessionWelcomed();
		if (first) {
			data.setSessionWelcomed(true);
			player.sendMessage(Text.literal(
					"§bDRMD 6DOF §f— Descent session is part of this world."), false);
			player.sendMessage(Text.literal(
					"§7Fly with §fH§7 · craft §fPyro GX§7 · explore industrial caves & sky megastructures"), false);
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
			int dist = 180 + i * 40;
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
}
