package com.terminaldetector.drmd.world.planet;

import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.scar.ScarMapState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells clients what they need to draw the planet: the seed, and where it is burnt.
 *
 * <p>The surface itself is a function of the seed ({@link PlanetMap}), so there is nothing else to
 * send — no heights, no tints, no viewport. Scars are the one part the world decides rather than
 * computes, and they change only when a reactor goes up, so this checks for that a few times a
 * minute rather than streaming.
 */
public final class PlanetSync {
	/** How often the scar set is compared against what clients were last told. */
	private static final int CHECK_INTERVAL = 100;

	private static int lastBroadcastScars = -1;

	private PlanetSync() {}

	public static void reset() {
		lastBroadcastScars = -1;
	}

	/** Join: the client has nothing, so it gets the snapshot immediately. */
	public static void pushTo(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		ServerWorld overworld = server.getOverworld();
		if (overworld == null) return;
		ServerPlayNetworking.send(player, snapshot(overworld));
	}

	/** Server tick: re-send to everyone when a detonation has changed the picture. */
	public static void tick(MinecraftServer server) {
		if (server.getTicks() % CHECK_INTERVAL != 0) return;
		ServerWorld overworld = server.getOverworld();
		if (overworld == null) return;
		int scars = ScarMapState.get(overworld).size();
		if (scars == lastBroadcastScars) return;
		lastBroadcastScars = scars;
		ModNetworking.PlanetPayload payload = snapshot(overworld);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static ModNetworking.PlanetPayload snapshot(ServerWorld overworld) {
		ScarMapState scars = ScarMapState.get(overworld);
		List<Long> cells = new ArrayList<>(Math.min(scars.size(), ModNetworking.PlanetPayload.MAX_SCARS));
		for (long key : scars.cells()) {
			if (cells.size() >= ModNetworking.PlanetPayload.MAX_SCARS) break;
			cells.add(key);
		}
		return new ModNetworking.PlanetPayload(overworld.getSeed(), cells);
	}
}
