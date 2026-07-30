package com.terminaldetector.drmd.world.build;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Construction Mode — enabled automatically after Pyro GX landing / dismount.
 * Surface-normal placement with no absolute "up".
 */
public final class ConstructionMode {
	private static final Map<UUID, Boolean> ACTIVE = new ConcurrentHashMap<>();

	private ConstructionMode() {}

	public static boolean isActive(UUID id) {
		return ACTIVE.getOrDefault(id, false);
	}

	public static void set(ServerPlayerEntity player, boolean on) {
		if (on) ACTIVE.put(player.getUuid(), true);
		else ACTIVE.remove(player.getUuid());
		player.sendMessage(Text.literal(on
				? "§aConstruction Mode §fON — place on any surface (local normal)"
				: "§7Construction Mode OFF"), false);
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
