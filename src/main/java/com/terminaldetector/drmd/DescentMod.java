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
import net.minecraft.server.world.ServerWorld;
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

		com.terminaldetector.drmd.world.DrmdServerConfig.load();
		ModNetworking.register();
		ModEntities.register();
		ModBlocks.register();
		ModItems.register();
		com.terminaldetector.drmd.entity.ModWorldBlocks.register();
		com.terminaldetector.drmd.entity.ModBlockEntities.register();
		com.terminaldetector.drmd.world.gen.ModWorldgen.register();
		com.terminaldetector.drmd.world.gen2.ModWorldgen2.register();
		com.terminaldetector.drmd.world.surface.MegacityBiomeWorldgen.register();
		com.terminaldetector.drmd.world.surface.TechnogenicSeaBiomeWorldgen.register();
		com.terminaldetector.drmd.world.surface.ScorchedLandsBiomeWorldgen.register();
		com.terminaldetector.drmd.world.surface.IronGuildBiomeWorldgen.register();
		com.terminaldetector.drmd.world.surface.SurfaceEventWorldgen.register();
		com.terminaldetector.drmd.world.orbit.OrbitJunkWorldgen.register();
		com.terminaldetector.drmd.world.level.LevelBuilder.register();
		com.terminaldetector.drmd.world.portal.PortalComplexity.register();
		WeaponRegistry.bootstrap();
		DescentCommands.register();
		AiCommands.register();

		// Player state is keyed by UUID in a process-wide map, so it outlives the world unless it is
		// cleared. Leaving it behind meant a new world inherited the previous one's data — and since
		// a fresh world has no player file to read over it, the stale copy won. 6DoF ended up off in
		// every world after the first, because the flag saying the first-join branch had already run
		// was part of what carried over. Cleared at both ends: on stop for the ordinary case, on
		// start in case a stop never happened.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DescentPlayerData.clear());

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			DescentPlayerData.clear();
			ConstructionRegistry.bootstrap(server);
			com.terminaldetector.drmd.world.gen2.MacroWorld.clear();
			com.terminaldetector.drmd.world.gravity.GravityFields.clear();
			com.terminaldetector.drmd.world.trap.ShieldProjectors.clear();
			com.terminaldetector.drmd.world.smoke.SmokeSystem.clear();
			com.terminaldetector.drmd.world.fire.FireSystem.clear();
			com.terminaldetector.drmd.world.base.DescentSession.clearSeedQueue();
			// Queue is RAM-only; persisted "seeded" marks must reset or distant plates stay empty.
			ServerWorld ow = server.getOverworld();
			if (ow != null) {
				com.terminaldetector.drmd.world.DescentWorldState.get(ow).clearLandmarkSeedMarks();
			}
			com.terminaldetector.drmd.world.build.ConstructScaffold.clearAll();
			com.terminaldetector.drmd.world.dungeon.FacilityReactorFight.clear();
			com.terminaldetector.drmd.world.dungeon.ReactorAftermath.clear();
			com.terminaldetector.drmd.world.gravity.EntityGravitySystem.clear();
			com.terminaldetector.drmd.world.sync.DimensionSync.load(server);
			// Both halves of worldgen are kept off the join path. Seeding only queues the landmarks
			// and spends one per tick; the CHUNK_LOAD generators stay idle until that hand-off is
			// done, so nothing of the mod's runs while the server is still preparing spawn.
			server.execute(() -> {
				try {
					com.terminaldetector.drmd.world.base.DescentSession.seedWorld(server);
				} catch (Exception e) {
					LOGGER.error("Descent stock seed failed — world is still playable", e);
				}
				com.terminaldetector.drmd.world.gen.ModWorldgen.enableLiveGeneration();
				com.terminaldetector.drmd.world.gen2.ModWorldgen2.enableLiveGeneration();
			});
			com.terminaldetector.drmd.world.end.EndReactorSession.onServerStarted(server);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			com.terminaldetector.drmd.world.smoke.SmokeSystem.tick();
			com.terminaldetector.drmd.world.gravity.EntityGravitySystem.tick(server);
			com.terminaldetector.drmd.world.base.DescentSession.drainSeedQueue(server);
			com.terminaldetector.drmd.world.dungeon.FacilityReactorFight.tick(server);
			com.terminaldetector.drmd.world.dungeon.ReactorAftermath.tick(server);
			com.terminaldetector.drmd.world.sync.DimensionSync.tick(server);
			com.terminaldetector.drmd.world.fate.WorldEndings.tick(server);
			if (tick % 40 == 0) {
				com.terminaldetector.drmd.world.end.EndReactorSession.onServerTick(server);
			}
			com.terminaldetector.drmd.world.end.OblivionSeekerSpawner.onServerTick(server);
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
					com.terminaldetector.drmd.pickup.LootField.tick(player, data);
					com.terminaldetector.drmd.world.layer.LayerBridge.tick(player, data);
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
				// Planetary map sample + End/orbit viewport (~1 Hz staggered).
				if (tick % 20 == player.getId() % 20) {
					com.terminaldetector.drmd.world.llod.planet.PlanetMapSync.tickPlayer(player);
				}
			});
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(
				(world, chunk) -> com.terminaldetector.drmd.world.llod.planet.PlanetScarApplier.onChunkLoad(world, chunk));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ConstructionRegistry.allOverrides().forEach((id, mods) -> {
				ServerPlayNetworking.send(handler.player,
						new ModNetworking.ConstructionPayload(id, ClusterModule.listToNbt(mods)));
			});
			server.execute(() -> {
				com.terminaldetector.drmd.world.base.DescentSession.onPlayerJoin(handler.player);
				ModNetworking.syncPlayer(handler.player, DescentPlayerData.get(handler.player));
				com.terminaldetector.drmd.world.sync.DimensionSync.pushTo(handler.player);
				com.terminaldetector.drmd.world.fate.WorldEndings.pushTo(handler.player);
				unlockDrmdRecipes(handler.player);
			});
		});

		LOGGER.info("DRMD 6DOF 1.0.2 ready — cockpit Tessellator · rail-safe · Descent native");
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

	/**
	 * Ticks per second — the divisor between the flight model's blocks-per-second domain and the
	 * blocks-per-tick that {@code Entity.setVelocity} expects. Mixing the two is what made the ship
	 * top out at 19.4 blocks/tick: 388 m/s, faster than chunks can stream.
	 */
	public static final double TICKS_PER_SECOND = 20.0;
}
