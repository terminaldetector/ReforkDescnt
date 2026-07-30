package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void drmd$cancelBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (DescentClientState.enabled) ci.cancel();
	}

	/** Always-run camera bank for Descent roll (tiltViewWhenHurt runs every frame). */
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
	private void drmd$cameraBank(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (DescentClientState.enabled && Math.abs(DescentClientState.roll) > 0.05f) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(DescentClientState.roll));
		}
	}
}
