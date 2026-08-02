package com.terminaldetector.drmd.world.build;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Construction Mode — enabled after Pyro GX landing / Construction Pad.
 * Server map + client mirror so place prediction matches.
 */
public final class ConstructionMode {
	private static final Map<UUID, Boolean> ACTIVE = new ConcurrentHashMap<>();
	/** Client-only mirror of local player's construction flag (from SyncPayload). */
	private static volatile boolean clientMirror;

	private ConstructionMode() {}

	public static boolean isActive(UUID id) {
		return ACTIVE.getOrDefault(id, false);
	}

	/** Works on both sides — client uses mirror, server uses UUID map. */
	public static boolean isActive(PlayerEntity player) {
		if (player.getWorld().isClient) return clientMirror;
		return isActive(player.getUuid());
	}

	public static void setClientMirror(boolean on) {
		clientMirror = on;
	}

	public static boolean clientMirror() {
		return clientMirror;
	}

	public static void set(ServerPlayerEntity player, boolean on) {
		if (on) ACTIVE.put(player.getUuid(), true);
		else ACTIVE.remove(player.getUuid());
		player.sendMessage(Text.literal(on
				? "§aConstruction Mode §fON — place on any surface (local normal)"
				: "§7Construction Mode OFF"), false);
		com.terminaldetector.drmd.network.ModNetworking.syncPlayer(player,
				com.terminaldetector.drmd.DescentPlayerData.get(player));
	}

	public static void toggle(ServerPlayerEntity player) {
		set(player, !isActive(player.getUuid()));
	}

	/** Called when pilot leaves / lands the Pyro GX. */
	public static void onShipLanded(ServerPlayerEntity player) {
		set(player, true);
		player.sendMessage(Text.literal("§bPyro GX secured — adaptive building unlocked."), false);
	}
}
