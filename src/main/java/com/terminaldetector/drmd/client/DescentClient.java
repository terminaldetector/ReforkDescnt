package com.terminaldetector.drmd.client;

import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.llod.LlodClientState;
import com.terminaldetector.drmd.client.llod.LlodSilhouetteRenderer;
import com.terminaldetector.drmd.client.render.ModEntityRenderers;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.llod.LlodLevel;
import com.terminaldetector.drmd.workshop.ClusterModule;
import com.terminaldetector.drmd.workshop.ConstructionRegistry;
import com.terminaldetector.drmd.workshop.WorkshopScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public class DescentClient implements ClientModInitializer {
	/** One enable-request per session after join if SyncPayload never armed flight. */
	private static boolean enableRequested;

	@Override
	public void onInitializeClient() {
		DescentKeybinds.register();
		ModEntityRenderers.register();
		WeaponViewRenderer.register();
		com.terminaldetector.drmd.client.render.CockpitRenderer.register();
		com.terminaldetector.drmd.client.render.BoundarySeamRenderer.register();
		LlodSilhouetteRenderer.register();
		com.terminaldetector.drmd.client.llod.HybridHorizonRenderer.register();
		com.terminaldetector.drmd.client.llod.planet.PlanetFloorRenderer.register();
		com.terminaldetector.drmd.client.sky.OrbitalBeltSkyRenderer.register();
		com.terminaldetector.drmd.client.render.MegaBeamViewRenderer.register();
		com.terminaldetector.drmd.client.render.ConstructScaffoldRenderer.register();
		com.terminaldetector.drmd.client.smoke.SmokeRenderer.register();
		registerRenderLayers();

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncPayload.ID, (payload, context) ->
				context.client().execute(() -> applySync(payload, context.client())));

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructionPayload.ID, (payload, context) -> {
			String id = ConstructionRegistry.normalize(payload.weaponId());
			if (payload.modules() == null || payload.modules().isEmpty()) {
				ConstructionRegistry.clearOverride(id);
			} else {
				ConstructionRegistry.setOverride(id, ClusterModule.listFromNbt(payload.modules()));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.LlodPayload.ID, (payload, context) -> {
			ArrayList<LlodClientState.Entry> next = new ArrayList<>(payload.entries().size());
			for (var e : payload.entries()) {
				MacroEntry.Kind kind;
				LlodLevel level;
				try {
					kind = MacroEntry.Kind.valueOf(e.kind());
				} catch (Exception ex) {
					kind = MacroEntry.Kind.STATION;
				}
				try {
					level = LlodLevel.valueOf(e.level());
				} catch (Exception ex) {
					// Legacy packet names
					level = switch (e.level()) {
						case "SILHOUETTE", "MEDIUM" -> LlodLevel.LLOD0;
						case "FULL" -> LlodLevel.CHUNK;
						default -> LlodLevel.LLOD1;
					};
				}
				next.add(new LlodClientState.Entry(
						e.id(),
						kind, new Vec3d(e.x(), e.y(), e.z()),
						e.rx(), e.ry(), e.rz(), level, e.color(), e.label(), e.seed()));
			}
			LlodClientState.INSTANCE.set(next);
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.PlanetMapPayload.ID, (payload, context) -> {
			ArrayList<com.terminaldetector.drmd.world.llod.planet.PlanetCell> cells =
					new ArrayList<>(payload.cells().size());
			for (var c : payload.cells()) {
				cells.add(new com.terminaldetector.drmd.world.llod.planet.PlanetCell(
						c.cx(), c.cz(), c.height(), c.tint(), c.flags()));
			}
			com.terminaldetector.drmd.client.llod.planet.PlanetMapClientState.INSTANCE.set(
					cells, payload.originCx(), payload.originCz(), payload.seed());
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ScaffoldPayload.ID, (payload, context) ->
				context.client().execute(() -> com.terminaldetector.drmd.client.build.ScaffoldClientState.INSTANCE.set(
						payload.active(), payload.shapeId(), payload.positions())));

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SmokePayload.ID, (payload, context) ->
				context.client().execute(() -> {
					// Integrated SP already shares the server SmokeSystem map — skip overwrite races.
					if (context.client().isIntegratedServerRunning()) return;
					ArrayList<com.terminaldetector.drmd.world.smoke.SmokeSystem.NetCloud> wire =
							new ArrayList<>(payload.clouds().size());
					for (var c : payload.clouds()) {
						wire.add(new com.terminaldetector.drmd.world.smoke.SmokeSystem.NetCloud(
								c.idMsb(), c.idLsb(), c.x(), c.y(), c.z(),
								c.radius(), c.density(), c.life(), c.sourceOrdinal(), c.colorRgb()));
					}
					com.terminaldetector.drmd.world.smoke.SmokeSystem.applyNetwork(wire);
				}));

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ReactorSyncPayload.ID, (payload, context) ->
				context.client().execute(() ->
						com.terminaldetector.drmd.client.sync.ClientReactorSync.INSTANCE.apply(payload)));

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.FatePayload.ID, (payload, context) ->
				context.client().execute(() -> {
					DescentClientState.worldFate = payload.fate() == null ? "CONTINUING" : payload.fate();
					DescentClientState.fateDecayTicks = payload.decayTicks();
					if (DescentClientState.isVoidEnding()) {
						DescentClientState.enabled = true;
						var p = context.client().player;
						if (p != null && !com.terminaldetector.drmd.client.flight.ShipAttitudeClient.isPrimed()) {
							com.terminaldetector.drmd.client.flight.ShipAttitudeClient.resetFromPlayer(p);
						}
					}
				}));

		com.terminaldetector.drmd.client.config.DescentConfig.load();

		// Pause-menu button: GameMenuScreenMixin.addDrawableChild (reliable).
		// Keybind below opens the same screen in-world without Esc.

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> {
					DescentClientState.resetSession();
					enableRequested = false;
				});

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
				(handler, sender, client) -> client.execute(() -> {
					// Integrated SP shares DescentPlayerData STORE with the server — mirror enable
					// immediately so flight/cockpit do not wait on a delayed SyncPayload.
					var p = client.player;
					if (p != null && com.terminaldetector.drmd.DescentPlayerData.get(p).isEnabled()) {
						DescentClientState.enabled = true;
						com.terminaldetector.drmd.client.flight.ShipAttitudeClient.resetFromPlayer(p);
					}
				}));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.getNetworkHandler() == null) return;
			// Integrated / late sync: pick up server enable if client mirror is still cold.
			if (!DescentClientState.enabled
					&& com.terminaldetector.drmd.DescentPlayerData.get(client.player).isEnabled()) {
				DescentClientState.enabled = true;
				com.terminaldetector.drmd.client.flight.ShipAttitudeClient.resetFromPlayer(client.player);
			}
			// One-shot: if still disarmed a few seconds after join, request enable (not toggle).
			if (!DescentClientState.enabled && !enableRequested
					&& client.player.age > 60) {
				enableRequested = true;
				ClientPlayNetworking.send(new ModNetworking.ActionPayload("enable"));
			}
			DescentKeybinds.tick(client);
			com.terminaldetector.drmd.client.gravity.FootGravityCamera.tickClient();
			if (DescentClientState.enabled) {
				// Self-heal: travel mixin needs a primed basis or the hull sits dead.
				if (!DescentClientState.attitudeValid
						|| !com.terminaldetector.drmd.client.flight.ShipAttitudeClient.isPrimed()) {
					com.terminaldetector.drmd.client.flight.ShipAttitudeClient.resetFromPlayer(client.player);
				}
				DescentKeybinds.sendInput(client);
			}
			// Dedicated clients receive authoritative SmokePayload snapshots; do not local-tick.
			float smoke = com.terminaldetector.drmd.world.smoke.SmokeSystem.obscurityAt(client.player.getEyePos());
			DescentClientState.smokeObscurity = smoke;
			if (smoke > 0.15f && client.world != null && client.world.getTime() % 3 == 0) {
				var pos = client.player.getEyePos();
				client.world.addParticle(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
						pos.x, pos.y, pos.z, 0, 0.01, 0);
			}
		});
	}

	/** Alpha-bearing DRMD blocks need a non-solid layer or their textures render opaque. */
	private static void registerRenderLayers() {
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
				net.minecraft.client.render.RenderLayer.getTranslucent(),
				com.terminaldetector.drmd.entity.ModWorldBlocks.LASER_BARRIER,
				com.terminaldetector.drmd.entity.ModBlocks.COMBAT_ZONE);
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
				net.minecraft.client.render.RenderLayer.getCutout(),
				com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH);
	}

	public static void openWorkshop() {
		MinecraftClient.getInstance().setScreen(new WorkshopScreen());
	}

	public static void openShipCustomize() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(new com.terminaldetector.drmd.client.screen.ShipCustomizeScreen(mc.currentScreen));
	}

	/** Pause / keybind entry into client 6DoF options. */
	public static void openSettings() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(new com.terminaldetector.drmd.client.screen.DescentSettingsScreen(mc.currentScreen));
	}

	/** Apply authoritative flight HUD sync on the client thread. */
	private static void applySync(ModNetworking.SyncPayload payload, MinecraftClient client) {
		boolean wasEnabled = DescentClientState.enabled;
		DescentClientState.enabled = payload.enabled();
		DescentClientState.energy = payload.energy();
		DescentClientState.energyMax = payload.energyMax();
		DescentClientState.shield = payload.shield();
		DescentClientState.shieldMax = payload.shieldMax();
		if (!DescentClientState.attitudeValid) {
			DescentClientState.roll = payload.roll();
		}
		DescentClientState.speed = payload.speed();
		DescentClientState.rocketSub = payload.rocketSub();
		DescentClientState.preset = payload.preset();
		DescentClientState.gravy = payload.gravy();
		DescentClientState.dashCd = payload.dashCd();
		DescentClientState.gravityFactor = payload.gravityFactor();
		DescentClientState.alwaysRun = payload.alwaysRun();
		DescentClientState.flightAssist = payload.flightAssist();
		DescentClientState.radar = payload.radar();
		DescentClientState.footGravity = payload.footGravity();
		DescentClientState.localUx = payload.localUx();
		DescentClientState.localUy = payload.localUy();
		DescentClientState.localUz = payload.localUz();
		DescentClientState.velX = payload.velX();
		DescentClientState.velY = payload.velY();
		DescentClientState.velZ = payload.velZ();
		DescentClientState.accel = payload.accel();
		DescentClientState.drag = payload.drag();
		DescentClientState.maxSpeed = payload.maxSpeed();
		DescentClientState.allocEngines = payload.allocEngines();
		DescentClientState.afterburnerTier = com.terminaldetector.drmd.flight.AfterburnerTiers
				.clamp(payload.afterburnerTier());

		// Keep client STORE in lockstep — LivingEntityMixin foot-gravity gates on this.
		var local = client.player;
		if (local != null) {
			com.terminaldetector.drmd.DescentPlayerData.get(local).setEnabled(payload.enabled());
		}

		if (local != null && payload.enabled() && !local.isSpectator()) {
			// Authority, not command: client integrator converges on this. See DescentFlightMotion.
			com.terminaldetector.drmd.client.flight.DescentFlightMotion.onServerVelocity(
					payload.velX(), payload.velY(), payload.velZ());
		}
		if (local != null) {
			com.terminaldetector.drmd.world.LocalOrientation.setUp(local.getUuid(),
					new Vec3d(payload.localUx(), payload.localUy(), payload.localUz()));
			if (payload.footGravity()) {
				com.terminaldetector.drmd.world.gravity.FootGravitySystem.adoptClient(
						local.getUuid(),
						new Vec3d(payload.localUx(), payload.localUy(), payload.localUz()));
			} else {
				com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(local.getUuid());
			}
		}
		// Prime attitude whenever flight is on but the client basis is cold — not only on
		// the false→true edge (stale enabled=true across worlds skipped the prime).
		if (payload.enabled()) {
			boolean cold = !wasEnabled
					|| !DescentClientState.attitudeValid
					|| !com.terminaldetector.drmd.client.flight.ShipAttitudeClient.isPrimed();
			if (cold && local != null) {
				com.terminaldetector.drmd.client.flight.ShipAttitudeClient.resetFromPlayer(local);
				com.terminaldetector.drmd.client.gravity.FootGravityCamera.reset();
			}
		} else {
			DescentClientState.attitudeValid = false;
			com.terminaldetector.drmd.client.flight.ShipAttitudeClient.clear();
			com.terminaldetector.drmd.client.flight.DescentFlightMotion.clear();
		}
	}
}
