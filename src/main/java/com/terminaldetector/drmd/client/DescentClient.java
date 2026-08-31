package com.terminaldetector.drmd.client;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.render.ModEntityRenderers;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import com.terminaldetector.drmd.network.ModNetworking;
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
	/** True once the pilot has toggled flight themselves — suppresses the join auto-arm for the rest of the session. */
	private static boolean userFlightChoice;
	/** True once any SyncPayload arrived — force-arm must not fight intentional H-off. */
	private static boolean sawSync;
	/** Ticks until the next join auto-arm retry is allowed; 0 = due now. */
	private static int enableRetryCooldown;
	/** Last player Y for seam-teleport predictor snap. */
	private static double lastY = Double.NaN;

	/**
	 * Landmark wire records to the client's own form.
	 *
	 * <p>The kind arrives as an ordinal, so a server running a different build could name one this
	 * client has never heard of. Those are dropped rather than guessed at — an out-of-range ordinal
	 * would otherwise take down the receiver thread with an array index.
	 */
	private static java.util.List<com.terminaldetector.drmd.client.planet.HorizonLandmark> horizonLandmarks(
			ModNetworking.PlanetPayload payload) {
		var kinds = com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.values();
		var out = new ArrayList<com.terminaldetector.drmd.client.planet.HorizonLandmark>(
				payload.landmarks().size());
		for (var l : payload.landmarks()) {
			if (l.kind() < 0 || l.kind() >= kinds.length) continue;
			out.add(new com.terminaldetector.drmd.client.planet.HorizonLandmark(
					l.x(), l.y(), l.z(), l.sizeX(), l.sizeY(), l.sizeZ(), l.colour(), kinds[l.kind()]));
		}
		return out;
	}

	/** Call when the pilot locally chooses toggle — suppress join force-arm. */
	public static void markUserFlightChoice() {
		userFlightChoice = true;
	}

	@Override
	public void onInitializeClient() {
		DescentConfig.load();
		DescentKeybinds.register();
		ModEntityRenderers.register();
		WeaponViewRenderer.register();
		com.terminaldetector.drmd.client.render.CockpitRenderer.register();
		// No seam curtain. It painted a two-colour checkerboard across every layer boundary within
		// 120 blocks, which put a blue-green grid over the ground at Y 40 — where the game is played.
		// A boundary the player can see is a boundary; the column is meant to read as one space.
		com.terminaldetector.drmd.client.sky.OrbitalBeltSkyRenderer.register();
		com.terminaldetector.drmd.client.sky.CoreSkyDome.register();
		com.terminaldetector.drmd.client.planet.PlanetFloorRenderer.register();
		com.terminaldetector.drmd.client.render.SkyUfoHullRenderer.register();
		com.terminaldetector.drmd.client.render.MegaBeamViewRenderer.register();
		com.terminaldetector.drmd.client.render.ConstructScaffoldRenderer.register();
		com.terminaldetector.drmd.client.smoke.SmokeRenderer.register();
		com.terminaldetector.drmd.client.portal.MirrorReflectionRenderer.register();
		com.terminaldetector.drmd.client.portal.PortalSeeThroughRenderer.register();
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

		// Seed, scars and the landmark catalogue: everything the voxel horizon needs, sent once
		// rather than streamed.
		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.PlanetPayload.ID, (payload, context) ->
				context.client().execute(() ->
						com.terminaldetector.drmd.client.planet.PlanetClientState.INSTANCE.apply(
								payload.seed(), payload.scarCells(), horizonLandmarks(payload))));

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SurfacePayload.ID, (payload, context) ->
				context.client().execute(() ->
						com.terminaldetector.drmd.client.planet.PlanetClientState.INSTANCE.acceptSection(
								payload.key(), payload.data())));

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

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.UfoMotionPayload.ID, (payload, context) ->
				context.client().execute(() ->
						com.terminaldetector.drmd.client.sync.ClientSkyUfoMotionSync.INSTANCE.apply(payload)));

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

		// Pause-menu button: GameMenuScreenMixin.addDrawableChild (reliable).
		// Keybind below opens the same screen in-world without Esc.

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> {
					DescentClientState.resetSession();
					com.terminaldetector.drmd.client.planet.PlanetClientState.INSTANCE.clear();
					userFlightChoice = false;
					sawSync = false;
					enableRetryCooldown = 0;
					lastY = Double.NaN;
				});

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
				(handler, sender, client) -> client.execute(() -> {
					userFlightChoice = false;
					sawSync = false;
					enableRetryCooldown = 0;
					lastY = Double.NaN;
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
			// Retry every 3s while no SyncPayload has ever arrived — never re-arm after H-off / sync.
			// A single attempt raced the server's own onPlayerJoin often enough on a freshly created
			// world (megacity/technogenic-sea/etc. region binding plus spawn-region prep can run past
			// the 3s mark) that a dropped or too-early "enable" request left the pilot stuck reading
			// disabled for the rest of the session with no further recovery attempt.
			if (!DescentClientState.enabled && !userFlightChoice && !sawSync
					&& client.player.age > 60) {
				if (enableRetryCooldown <= 0) {
					ClientPlayNetworking.send(new ModNetworking.ActionPayload("enable"));
					enableRetryCooldown = 60;
				} else {
					enableRetryCooldown--;
				}
			}
			// Seam teleport yanks Y; soft SyncPayload blend leaves the predictor fighting for seconds.
			double y = client.player.getY();
			if (DescentClientState.enabled && !Double.isNaN(lastY) && Math.abs(y - lastY) > 12.0) {
				com.terminaldetector.drmd.client.flight.DescentFlightMotion.snapToServerVelocity(
						DescentClientState.velX, DescentClientState.velY, DescentClientState.velZ);
			}
			lastY = y;
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
		sawSync = true;
		userFlightChoice = true; // sync settled arm state — never force-arm over it
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
