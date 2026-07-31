package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import com.terminaldetector.drmd.client.gravity.FootGravityCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void drmd$cancelBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		// View bob walks the camera along world axes, which fights any tilted frame.
		if (DescentClientState.enabled || !FootGravityCamera.settled()) ci.cancel();
	}

	/**
	 * 6DoF bank while flying; otherwise stabilize camera to local gravity UP
	 * (torch / wall / ceiling) so the world reads upright.
	 */
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void drmd$cameraBank(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (DescentClientState.enabled) {
			float roll = DescentCamera.viewRoll();
			if (Math.abs(roll) > 0.05f) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
			}
			ci.cancel();
			return;
		}
		if (!FootGravityCamera.settled()) {
			FootGravityCamera.apply(matrices, tickDelta);
			ci.cancel();
		}
	}

	/** Mild FOV stretch under afterburner / speed — Descent menu FOV feel. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void drmd$fovBoost(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		float boost = DescentCamera.fovBoost();
		if (boost > 0.01f) {
			cir.setReturnValue(cir.getReturnValueD() + boost);
		}
	}
}
