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

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncPayload.ID, (payload, context) -> {
			DescentClientState.enabled = payload.enabled();
			DescentClientState.energy = payload.energy();
			DescentClientState.energyMax = payload.energyMax();
			DescentClientState.shield = payload.shield();
			DescentClientState.shieldMax = payload.shieldMax();
			DescentClientState.roll = payload.roll();
			DescentClientState.speed = payload.speed();
			DescentClientState.rocketSub = payload.rocketSub();
			DescentClientState.preset = payload.preset();
			DescentClientState.gravy = payload.gravy();
			DescentClientState.dashCd = payload.dashCd();
			DescentClientState.gravityFactor = payload.gravityFactor();
			DescentClientState.alwaysRun = payload.alwaysRun();
			DescentClientState.flightAssist = payload.flightAssist();
			DescentClientState.radar = payload.radar();
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
			if (DescentClientState.enabled) {
				DescentKeybinds.sendInput(client);
			}
		});
	}

	public static void openWorkshop() {
		MinecraftClient.getInstance().setScreen(new WorkshopScreen());
	}
}
