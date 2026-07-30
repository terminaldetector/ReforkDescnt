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
	@Override
	public void onInitializeClient() {
		DescentKeybinds.register();
		ModEntityRenderers.register();
		WeaponViewRenderer.register();
		LlodSilhouetteRenderer.register();
		com.terminaldetector.drmd.client.smoke.SmokeRenderer.register();

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncPayload.ID, (payload, context) -> {
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
			var player = context.client().player;
			if (player != null) {
				com.terminaldetector.drmd.world.LocalOrientation.setUp(player.getUuid(),
						new Vec3d(payload.localUx(), payload.localUy(), payload.localUz()));
				if (payload.footGravity()) {
					com.terminaldetector.drmd.world.gravity.FootGravitySystem.adoptClient(
							player.getUuid(),
							new Vec3d(payload.localUx(), payload.localUy(), payload.localUz()));
				} else {
					com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(player.getUuid());
				}
			}
			// Prime Descent attitude when 6DoF turns on from server (/d6 / join).
			if (payload.enabled() && !wasEnabled) {
				if (player != null) {
					com.terminaldetector.drmd.client.flight.ShipAttitudeClient.resetFromPlayer(player);
				}
				com.terminaldetector.drmd.client.gravity.FootGravityCamera.reset();
			}
			if (!payload.enabled()) {
				DescentClientState.attitudeValid = false;
				com.terminaldetector.drmd.client.flight.ShipAttitudeClient.clear();
			}
		});

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

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.getNetworkHandler() == null) return;
			DescentKeybinds.tick(client);
			com.terminaldetector.drmd.client.gravity.FootGravityCamera.tickClient();
			if (DescentClientState.enabled) {
				DescentKeybinds.sendInput(client);
			}
			// Age client-only smoke on dedicated clients; integrated SP uses server tick
			if (client.world != null && client.world.getTime() % 2 == 0
					&& !client.isIntegratedServerRunning()) {
				com.terminaldetector.drmd.world.smoke.SmokeSystem.tick();
			}
			float smoke = com.terminaldetector.drmd.world.smoke.SmokeSystem.obscurityAt(client.player.getEyePos());
			DescentClientState.smokeObscurity = smoke;
			if (smoke > 0.15f && client.world != null && client.world.getTime() % 3 == 0) {
				var pos = client.player.getEyePos();
				client.world.addParticle(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
						pos.x, pos.y, pos.z, 0, 0.01, 0);
			}
		});
	}

	public static void openWorkshop() {
		MinecraftClient.getInstance().setScreen(new WorkshopScreen());
	}
}
