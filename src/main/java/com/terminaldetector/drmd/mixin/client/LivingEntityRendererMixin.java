package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.gravity.FootGravityCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Align the local player model with foot-gravity UP so legs point into the floor
 * (wall/ceiling) — matches the stabilized camera.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("RETURN"))
	private void drmd$alignLocalUp(LivingEntity entity, MatrixStack matrices,
								   float animationProgress, float bodyYaw, float tickDelta, float scale,
								   CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != entity) return;
		if (DescentClientState.enabled) return;
		// Follows the smoothed up vector rather than the raw synced one, so the model rolls onto
		// the wall over the same 0.4 s the view does instead of snapping ahead of it.
		FootGravityCamera.applyModelUp(matrices, bodyYaw);
	}
}
