package com.terminaldetector.drmd.world.planet;

import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import com.terminaldetector.drmd.world.scar.ScarMapState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells clients what they need to draw the distant world: the seed, where it is burnt, and what has
 * been built on it.
 *
 * <p>The surface itself is a function of the seed ({@link PlanetMap}), so there are no heights and
 * no tints to send. Scars and landmarks are the two parts the world decides rather than computes,
 * and both change rarely — a reactor going up, a generator raising a locator — so this compares
 * counts a few times a minute instead of streaming anything.
 */
public final class PlanetSync {
	/** How often the scar set is compared against what clients were last told. */
	private static final int CHECK_INTERVAL = 100;

	private static int lastBroadcastScars = -1;
	private static int lastBroadcastLandmarks = -1;

	private PlanetSync() {}

	public static void reset() {
		lastBroadcastScars = -1;
		lastBroadcastLandmarks = -1;
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
		int marks = MacroWorld.size();
		if (scars == lastBroadcastScars && marks == lastBroadcastLandmarks) return;
		lastBroadcastScars = scars;
		lastBroadcastLandmarks = marks;
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
		return new ModNetworking.PlanetPayload(overworld.getSeed(), cells, landmarks());
	}

	/**
	 * The catalogue, as silhouettes.
	 *
	 * <p>Only what stands up out of the ground is worth sending. A rift is a hole and the fauna
	 * moves, so both are dropped here rather than on the client — a landmark nobody can draw is
	 * bytes on the wire for nothing.
	 */
	private static List<ModNetworking.PlanetPayload.Landmark> landmarks() {
		List<ModNetworking.PlanetPayload.Landmark> out = new ArrayList<>();
		for (MacroEntry entry : MacroWorld.all()) {
			if (out.size() >= ModNetworking.PlanetPayload.MAX_LANDMARKS) break;
			if (!standsUp(entry.kind)) continue;
			out.add(new ModNetworking.PlanetPayload.Landmark(
					entry.center.getX(), entry.center.getY(), entry.center.getZ(),
					entry.sizeX, entry.sizeY, entry.sizeZ,
					entry.colorRgb, entry.kind.ordinal()));
		}
		return out;
	}

	private static boolean standsUp(MacroEntry.Kind kind) {
		return switch (kind) {
			case RIFT, CANYON, WORM, SWARM, KEEPER, UFO -> false;
			default -> true;
		};
	}
}
