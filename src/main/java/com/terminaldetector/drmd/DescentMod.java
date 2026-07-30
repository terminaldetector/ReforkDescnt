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
import com.terminaldetector.drmd.workshop.ClusterModule;
import com.terminaldetector.drmd.workshop.ConstructionRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
		LOGGER.info("DRMD 6DOF initializing — 6DoF world mode (volume-first)");

		ModNetworking.register();
		ModEntities.register();
		ModBlocks.register();
		ModItems.register();
		com.terminaldetector.drmd.entity.ModWorldBlocks.register();
		com.terminaldetector.drmd.entity.ModBlockEntities.register();
		com.terminaldetector.drmd.world.gen.ModWorldgen.register();
		com.terminaldetector.drmd.world.gen2.ModWorldgen2.register();
		WeaponRegistry.bootstrap();
		DescentCommands.register();
		AiCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ConstructionRegistry.bootstrap(server);
			com.terminaldetector.drmd.world.gen2.MacroWorld.clear();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			server.getPlayerManager().getPlayerList().forEach(player -> {
				DescentPlayerData data = DescentPlayerData.get(player);
				if (data.isEnabled()) {
					FlightSystem.tick(player, data);
					EnergySystem.regenTick(player, data);
					ShieldSystem.regenTick(player, data);
				}
				// LLOD silhouette sync ~2 Hz
				if (tick % 10 == player.getId() % 10) {
					ModNetworking.syncLlod(player);
				}
			});
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DescentPlayerData data = DescentPlayerData.get(handler.player);
			data.ensureInit();
			// Sync all construction overrides to joining client
			ConstructionRegistry.allOverrides().forEach((id, mods) -> {
				ServerPlayNetworking.send(handler.player,
						new ModNetworking.ConstructionPayload(id, ClusterModule.listToNbt(mods)));
			});
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
