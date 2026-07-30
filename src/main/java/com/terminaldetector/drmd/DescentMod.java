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
			com.terminaldetector.drmd.world.gravity.GravityFields.clear();
			com.terminaldetector.drmd.world.smoke.SmokeSystem.clear();
			com.terminaldetector.drmd.world.fire.FireSystem.clear();
			server.execute(() -> com.terminaldetector.drmd.world.base.DescentSession.seedWorld(server));
			com.terminaldetector.drmd.world.end.EndReactorSession.onServerStarted(server);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			com.terminaldetector.drmd.world.smoke.SmokeSystem.tick();
			if (tick % 40 == 0) {
				com.terminaldetector.drmd.world.end.EndReactorSession.onServerTick(server);
			}
			server.getWorlds().forEach(world -> {
				if (tick % 5 == 0) {
					com.terminaldetector.drmd.world.fire.FireSystem.tick(world);
				}
			});
			server.getPlayerManager().getPlayerList().forEach(player -> {
				DescentPlayerData data = DescentPlayerData.get(player);
				if (data.isEnabled()) {
					FlightSystem.tick(player, data);
					EnergySystem.regenTick(player, data);
					ShieldSystem.regenTick(player, data);
					com.terminaldetector.drmd.physics.GravyPhysics.tick(player);
				} else {
					com.terminaldetector.drmd.world.gravity.FootGravitySystem.tick(player);
				}
				if (tick % 20 == player.getId() % 20) {
					var pos = player.getBlockPos();
					com.terminaldetector.drmd.world.atmosphere.AtmosphereRules.tickWaterSuppression(
							player.getServerWorld(), pos, 2);
					com.terminaldetector.drmd.world.atmosphere.AtmosphereRules.tickDeepPressure(
							player.getServerWorld(), pos);
				}
				if (tick % 10 == player.getId() % 10) {
					ModNetworking.syncLlod(player);
				}
			});
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ConstructionRegistry.allOverrides().forEach((id, mods) -> {
				ServerPlayNetworking.send(handler.player,
						new ModNetworking.ConstructionPayload(id, ClusterModule.listToNbt(mods)));
			});
			server.execute(() -> {
				com.terminaldetector.drmd.world.base.DescentSession.onPlayerJoin(handler.player);
				ModNetworking.syncPlayer(handler.player, DescentPlayerData.get(handler.player));
				unlockDrmdRecipes(handler.player);
			});
		});

		LOGGER.info("DRMD 6DOF ready — Descent session is native to this Minecraft world");
	}

	/**
	 * Put the whole DRMD tree in the recipe book on join.
	 *
	 * <p>Survival crafting works either way, but without this the recipes never surface: none of
	 * them is gated behind an advancement, so the book would stay empty and players would have to
	 * look the patterns up externally.
	 */
	private static void unlockDrmdRecipes(net.minecraft.server.network.ServerPlayerEntity player) {
		var server = player.getServer();
		if (server == null) return;
		var recipes = server.getRecipeManager().values().stream()
				.filter(entry -> entry.id().getNamespace().equals(MOD_ID))
				.toList();
		if (!recipes.isEmpty()) {
			player.unlockRecipes(recipes);
		}
	}

	public static double su(double sourceUnits) {
		return sourceUnits * UNIT_SCALE;
	}
}
