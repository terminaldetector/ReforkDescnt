package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.AiCommands;
import com.terminaldetector.drmd.command.DescentCommands;
import com.terminaldetector.drmd.entity.ModBlocks;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.shield.ShieldSystem;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.weapon.registry.WeaponRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DRMD 6DOF — Minecraft Fabric port of the Garry's Mod Descent addon.
 * Preserves: 6DOF flight, energy presets, shields, weapon core, AI roles,
 * map entities, HUD, and Weapon Workshop.
 */
public class DescentMod implements ModInitializer {
	public static final String MOD_ID = "drmd";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Source-engine units → Minecraft blocks (tuned for playable feel). */
	public static final double UNIT_SCALE = 1.0 / 80.0;

	@Override
	public void onInitialize() {
		LOGGER.info("DRMD 6DOF initializing — Descent Resource Management Dynamics");

		ModNetworking.register();
		ModEntities.register();
		ModBlocks.register();
		ModItems.register();
		WeaponRegistry.bootstrap();
		DescentCommands.register();
		AiCommands.register();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			server.getPlayerManager().getPlayerList().forEach(player -> {
				DescentPlayerData data = DescentPlayerData.get(player);
				if (data.isEnabled()) {
					FlightSystem.tick(player, data);
					EnergySystem.regenTick(player, data);
					ShieldSystem.regenTick(player, data);
				}
			});
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DescentPlayerData data = DescentPlayerData.get(handler.player);
			data.ensureInit();
			// Auto-activate 6DOF shortly after join (matches GMod 0.5s spawn delay)
			server.execute(() -> {
				if (!data.isEnabled()) {
					data.setEnabled(true);
					ModNetworking.syncPlayer(handler.player, data);
				}
			});
		});

		LOGGER.info("DRMD 6DOF ready. Toggle with /6dof toggle");
	}

	public static double su(double sourceUnits) {
		return sourceUnits * UNIT_SCALE;
	}
}
