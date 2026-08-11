package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.gravity.FootGravityCamera;
import com.terminaldetector.drmd.client.render.ModelOrientation;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Put the pilot's model in the same orientation the camera is in, and hide it entirely while piloting
 * the Pyro GX — Descent never shows its own ship's pilot from outside, only the hull.
 *
 * <p>Two orientation cases, one call: flying free, the model takes the ship's basis so it pitches and
 * rolls with the hull instead of staying upright through a barrel roll; on foot under a gravity torch
 * it takes the surface's, so legs point into the wall being walked on. Piloting the Pyro GX is a third
 * case handled by the second injection below — hidden outright rather than oriented, since
 * {@code PyroShipRenderer} draws the hull itself and there is nothing useful left for the pilot's own
 * body to show.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("RETURN"))
	private void drmd$alignLocalUp(LivingEntity entity, MatrixStack matrices,
								   float animationProgress, float bodyYaw, float tickDelta, float scale,
								   CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		// Only the local pilot: nobody else's attitude is synced to this client, so a remote player
		// has nothing to orient by and is left to vanilla.
		if (mc.player != entity) return;

		if (DescentClientState.enabled) {
			if (!DescentClientState.attitudeValid || !ShipAttitudeClient.isPrimed()) return;
			var att = ShipAttitudeClient.get();
			ModelOrientation.applyBasis(matrices, bodyYaw, att.forward(), att.up());
			return;
		}

		// Foot gravity follows the smoothed up vector rather than the raw synced one, so the model
		// rolls onto the wall over the same 0.4 s the view does instead of snapping ahead of it.
		if (FootGravityCamera.settled()) return;
		Vec3d up = FootGravityCamera.visualUp();
		Vec3d look = entity.getRotationVec(tickDelta);
		Vec3d forward = look.subtract(up.multiply(look.dotProduct(up)));
		if (forward.lengthSquared() < 1e-6) return;
		ModelOrientation.applyBasis(matrices, bodyYaw, forward, up);
	}

	/**
	 * Local-only: this client's own view of itself. A nearby player watching someone else pilot the
	 * Pyro GX still sees that pilot's body seated in the hull — hiding it everywhere would need a
	 * server-synced visibility flag, a bigger change than this pass makes; see
	 * {@code aeris-mirai}-style scope notes in the PR/report for why that's deliberately left out here.
	 */
	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void drmd$hidePilotInShip(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
									  VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == entity && entity.getVehicle() instanceof PyroShipEntity) {
			ci.cancel();
		}
	}
}
