package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mouse.class)
public class MouseMixin {
	@Redirect(
			method = "updateMouse",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
			)
	)
	private void drmd$descentLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		if (DescentClientState.enabled && MinecraftClient.getInstance().currentScreen == null) {
			ShipAttitudeClient.applyMouse(player, cursorDeltaX, cursorDeltaY);
		} else {
			player.changeLookDirection(cursorDeltaX, cursorDeltaY);
		}
	}
}
