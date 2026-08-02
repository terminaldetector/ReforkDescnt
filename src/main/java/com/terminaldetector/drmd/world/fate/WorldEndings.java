package com.terminaldetector.drmd.world.fate;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.mob.CyberMobEntity;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.llod.planet.PlanetMapState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Applies the two philosophies:
 * <ol>
 *   <li>{@link WorldFate#SILENCE} — switch off the machine; world decays.</li>
 *   <li>{@link WorldFate#VOID} — crack the planetary core; nothing remains but flight.</li>
 * </ol>
 */
public final class WorldEndings {
	private WorldEndings() {}

	public static WorldFateState state(MinecraftServer server) {
		ServerWorld ow = server.getOverworld();
		return ow != null ? WorldFateState.get(ow) : null;
	}

	public static WorldFate fate(MinecraftServer server) {
		WorldFateState s = state(server);
		return s != null ? s.fate() : WorldFate.CONTINUING;
	}

	/** Ending 1 — giga-reactor destroyed. */
	public static void applySilence(MinecraftServer server, BlockPos at) {
		WorldFateState s = state(server);
		if (s == null || s.isVoid()) return;
		if (s.fate() == WorldFate.SILENCE) return;
		s.setFate(WorldFate.SILENCE, server.getOverworld().getTime());
		DescentMod.LOGGER.info("World fate → SILENCE @ {}", at.toShortString());

		// Infrastructure that held the remnants starts to fail — extra planet scars.
		ServerWorld ow = server.getOverworld();
		if (ow != null) {
			PlanetMapState map = PlanetMapState.get(ow);
			for (int i = 0; i < 24; i++) {
				int ox = at.getX() + (ow.getRandom().nextInt(257) - 128);
				int oz = at.getZ() + (ow.getRandom().nextInt(257) - 128);
				map.scarBlock(ox, oz, 2);
			}
		}

		cullMachines(server);
		broadcast(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			p.sendMessage(Text.literal(
					"§7The machine falls silent. §8Orders end. The infrastructure that held the remnants begins to fail."), false);
			p.sendMessage(Text.literal(
					"§8Victory — at the cost of a slow decay. The world already lost; you only switched it off."), false);
		}
	}

	/** Ending 2 — dark-energy bomb at the planetary core. */
	public static void applyVoid(MinecraftServer server, ServerPlayerEntity trigger, BlockPos core) {
		WorldFateState s = state(server);
		if (s == null) return;
		s.setFate(WorldFate.VOID, server.getOverworld().getTime());
		DescentMod.LOGGER.info("World fate → VOID (core crack) by {}", trigger.getName().getString());

		cullMachines(server);
		for (ServerWorld w : server.getWorlds()) {
			// Strip nearby matter — the system unravels.
			clearSphere(w, core, 48);
		}
		// Lift every player into empty flight — no credits, just continuance.
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			enterVoidFlight(p);
			p.sendMessage(Text.literal(
					"§5The core answers. §7Overworld, Nether, End — one system, undone."), false);
			p.sendMessage(Text.literal(
					"§8No titles. Nothing left but space. You may keep flying."), false);
		}
		broadcast(server);
	}

	public static void enterVoidFlight(ServerPlayerEntity p) {
		DescentPlayerData data = DescentPlayerData.get(p);
		data.setEnabled(true);
		data.setAlwaysRun(true);
		data.setFlightAssist(false);
		// Park above the old column — empty half-cube sky.
		double y = Math.max(p.getY(), WorldLevels.ORBITAL_TOP + 40);
		p.requestTeleport(p.getX(), y, p.getZ());
		clearSphere(p.getServerWorld(), p.getBlockPos(), 64);
		ModNetworking.syncPlayer(p, data);
	}

	public static boolean allowMachineSpawn(MinecraftServer server) {
		WorldFateState s = state(server);
		return s == null || !s.machinesSilent();
	}

	/** Periodic silence decay + void clearing. */
	public static void tick(MinecraftServer server) {
		WorldFateState s = state(server);
		if (s == null) return;
		if (s.fate() == WorldFate.CONTINUING) return;

		if (s.fate() == WorldFate.SILENCE && server.getTicks() % 100 == 0) {
			s.bumpDecay();
			cullMachines(server);
			if (s.decayTicks() % 5 == 0) {
				ServerWorld ow = server.getOverworld();
				if (ow != null && !ow.getPlayers().isEmpty()) {
					ServerPlayerEntity p = ow.getPlayers().get(0);
					PlanetMapState.get(ow).scarBlock(
							p.getBlockX() + ow.getRandom().nextInt(64) - 32,
							p.getBlockZ() + ow.getRandom().nextInt(64) - 32, 1);
				}
			}
		}

		if (s.isVoid() && server.getTicks() % 20 == 0) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				DescentPlayerData data = DescentPlayerData.get(p);
				if (!data.isEnabled()) {
					data.setEnabled(true);
					ModNetworking.syncPlayer(p, data);
				}
				clearSphere(p.getServerWorld(), p.getBlockPos(), 28);
				if (p.getY() < WorldLevels.SKY_TOP) {
					p.requestTeleport(p.getX(), WorldLevels.ORBITAL_TOP + 20, p.getZ());
				}
			}
		}

		if (server.getTicks() % 40 == 0) broadcast(server);
	}

	public static void broadcast(MinecraftServer server) {
		WorldFateState s = state(server);
		if (s == null) return;
		ModNetworking.FatePayload payload = new ModNetworking.FatePayload(s.fate().name(), s.decayTicks());
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static void pushTo(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		WorldFateState s = state(server);
		if (s == null || s.fate() == WorldFate.CONTINUING) return;
		ServerPlayNetworking.send(player, new ModNetworking.FatePayload(s.fate().name(), s.decayTicks()));
		if (s.isVoid()) enterVoidFlight(player);
	}

	private static void cullMachines(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerWorld w = p.getServerWorld();
			Box box = p.getBoundingBox().expand(192);
			for (HostileEntity e : w.getEntitiesByClass(HostileEntity.class, box,
					ent -> ent instanceof CyberMobEntity || ent instanceof DroneEntity
							|| ent.getType() == ModEntities.OBLIVION_SEEKER
							|| ent.getType() == ModEntities.REACTOR_KEEPER
							|| ent.getType() == ModEntities.DRONE_SWARM
							|| ent.getType() == ModEntities.SKY_UFO)) {
				e.discard();
			}
		}
	}

	private static void clearSphere(ServerWorld world, BlockPos center, int r) {
		if (world == null || center == null) return;
		BlockPos.Mutable m = new BlockPos.Mutable();
		int r2 = r * r;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > r2) continue;
					m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (world.isOutOfHeightLimit(m)) continue;
					if (world.getBlockState(m).isAir()) continue;
					world.setBlockState(m, Blocks.AIR.getDefaultState(), 3);
				}
			}
		}
	}
}
