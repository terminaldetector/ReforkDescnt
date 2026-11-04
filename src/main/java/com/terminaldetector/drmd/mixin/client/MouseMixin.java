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
		boolean open = MinecraftClient.getInstance().currentScreen == null;
		if(open&&com.terminaldetector.drmd.client.flight.UfoControlClient.active()&&!net.minecraft.client.util.InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(),org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)){com.terminaldetector.drmd.client.flight.UfoControlClient.mouse(cursorDeltaX,cursorDeltaY);return;}
		if (DescentClientState.enabled && open) {
			ShipAttitudeClient.applyMouse(player, cursorDeltaX, cursorDeltaY);
		} else if (open && !com.terminaldetector.drmd.client.gravity.FootGravityCamera.settled()) {
			// A gravity torch redefines which way is up; look has to turn in that frame or the pilot
			// loses most of their headings the moment the surface goes vertical.
			com.terminaldetector.drmd.client.gravity.FootLook.applyMouse(player, cursorDeltaX, cursorDeltaY);
		} else {
			player.changeLookDirection(cursorDeltaX, cursorDeltaY);
		}
	}
}
