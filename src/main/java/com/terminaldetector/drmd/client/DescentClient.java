package com.terminaldetector.drmd.client;

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

public class DescentClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DescentKeybinds.register();
		ModEntityRenderers.register();
		WeaponViewRenderer.register();

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
