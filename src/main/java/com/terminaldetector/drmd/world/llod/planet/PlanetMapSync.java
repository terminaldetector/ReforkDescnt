package com.terminaldetector.drmd.world.llod.planet;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync planetary viewport to clients in End / high orbit so the floor map + fog-of-war render.
 */
public final class PlanetMapSync {
	/** Cells radius around focus (diameter ≈ 49 cells → ~25k procedural+explored). */
	public static final int VIEW_RADIUS = 12;

	private PlanetMapSync() {}

	public static void tickPlayer(ServerPlayerEntity player) {
		ServerWorld ow = player.getServer().getOverworld();
		if (ow == null) return;

		DescentPlayerData data = DescentPlayerData.get(player);
		if (player.getWorld().getRegistryKey() == World.OVERWORLD) {
			data.setLastOverworldBlock(player.getBlockX(), player.getBlockZ());
			PlanetMapSampler.tickPlayer(player);
		}

		boolean needMap = player.getWorld().getRegistryKey() == World.END
				|| (player.getWorld().getRegistryKey() == World.OVERWORLD
				&& player.getY() >= WorldLevels.SKY_TOP - 20);
		if (!needMap) return;

		int focusX = data.getLastOverworldX();
		int focusZ = data.getLastOverworldZ();
		int cx = PlanetCell.cellOf(focusX);
		int cz = PlanetCell.cellOf(focusZ);
		PlanetMapState map = PlanetMapState.get(ow);
		List<PlanetCell> view = map.viewport(cx, cz, VIEW_RADIUS, ow.getSeed());

		ArrayList<ModNetworking.PlanetMapPayload.Cell> wire = new ArrayList<>(view.size());
		for (PlanetCell c : view) {
			wire.add(new ModNetworking.PlanetMapPayload.Cell(
					c.cx, c.cz, c.height, c.tint, c.flags));
		}
		ServerPlayNetworking.send(player, new ModNetworking.PlanetMapPayload(
				cx, cz, ow.getSeed(), wire));
	}
}
