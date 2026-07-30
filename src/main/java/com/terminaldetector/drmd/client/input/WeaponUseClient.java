package com.terminaldetector.drmd.client.input;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Cockpit Use channel — middle mouse.
 * Hold fires primary (server cooldown gates ROF); sneak/Ctrl+MMB = Alt (rockets).
 */
public final class WeaponUseClient {
	private static int pulseCooldown;

	private WeaponUseClient() {}

	public static void setHeld(boolean held, boolean alt) {
		DescentClientState.weaponUseHeld = held && !alt;
		DescentClientState.weaponAltHeld = held && alt;
		if (held) {
			// Immediate first shot on press edge
			sendNow(alt);
			pulseCooldown = 0;
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.getNetworkHandler() == null) return;
		if (client.currentScreen != null) {
			DescentClientState.weaponUseHeld = false;
			DescentClientState.weaponAltHeld = false;
			return;
		}
		if (!(client.player.getMainHandStack().getItem() instanceof DescentWeaponItem)) {
			DescentClientState.weaponUseHeld = false;
			DescentClientState.weaponAltHeld = false;
			return;
		}
		if (pulseCooldown > 0) pulseCooldown--;
		boolean held = DescentClientState.weaponUseHeld || DescentClientState.weaponAltHeld;
		if (!held) return;
		// Hold-fire pulse — not every tick (avoids packet spam lagging flight).
		if (pulseCooldown <= 0) {
			sendNow(DescentClientState.weaponAltHeld);
			pulseCooldown = 2;
		}
	}

	private static void sendNow(boolean alt) {
		ClientPlayNetworking.send(new ModNetworking.ActionPayload(alt ? "weapon_alt" : "weapon_use"));
	}
}
