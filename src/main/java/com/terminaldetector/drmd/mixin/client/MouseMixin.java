package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.input.WeaponUseClient;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

	/**
	 * Middle mouse = weapon Use (cockpit trigger). Cancels creative pick-block
	 * while a DRMD gun is held so MMB is a dedicated Use channel.
	 */
	@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
	private void drmd$middleWeaponUse(long window, int button, int action, int mods, CallbackInfo ci) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.currentScreen != null) return;
		if (!(mc.player.getMainHandStack().getItem() instanceof DescentWeaponItem)) return;

		boolean press = action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;
		boolean release = action == GLFW.GLFW_RELEASE;
		// Alt Use: sneak or Left-Alt (Ctrl is descend in 6DoF — do not steal it).
		boolean alt = mc.player.isSneaking() || (mods & GLFW.GLFW_MOD_ALT) != 0;

		if (press) {
			WeaponUseClient.setHeld(true, alt);
		} else if (release) {
			WeaponUseClient.setHeld(false, false);
		}
		ci.cancel();
	}
}
