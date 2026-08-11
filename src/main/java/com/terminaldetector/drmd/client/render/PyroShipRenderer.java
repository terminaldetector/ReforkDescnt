package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.entity.model.PyroShipModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class PyroShipRenderer extends EntityRenderer<PyroShipEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "pyro_ship"), "main");
	private static final Identifier TEXTURE = Identifier.of(DescentMod.MOD_ID, "textures/entity/pyro_ship.png");
	private final PyroShipModel<PyroShipEntity> model;

	public PyroShipRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.model = new PyroShipModel<>(ctx.getPart(LAYER));
		this.shadowRadius = 0.8f;
	}

	/**
	 * Whole orientation goes through {@link ModelOrientation#applyBasis} with a constant
	 * {@code bodyYaw=180f} — same convention {@code ProjectileRenderer} uses for rocket/mine bodies,
	 * not the {@code LivingEntityRenderer} one ({@code bodyYaw} = the entity's own live yaw). The two
	 * differ because {@code LivingEntityRenderer.setupTransforms} has already rotated the stack by
	 * {@code 180-bodyYaw} before that mixin's own {@code applyBasis} call runs, and passing the real
	 * bodyYaw is what undoes that specific rotation; this renderer builds its whole transform from
	 * scratch like {@code ProjectileRenderer} does, so passing a constant 180 makes that internal
	 * undo-step a no-op and {@code applyBasis} rotates straight from the given world forward/up — the
	 * same reasoning as {@code ProjectileRenderer}'s own {@code applyBasis(matrices, 180f, ...)} calls.
	 *
	 * <p>Roll is only known for the local pilot's own ship (nothing syncs a remote ship's attitude —
	 * same limitation {@code LivingEntityRendererMixin} already has for the pilot's own body); every
	 * other ship falls back to a plain yaw/pitch-derived forward with world-up, i.e. no visible roll,
	 * which is the same thing this renderer did before this change.
	 */
	@Override
	public void render(PyroShipEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		Vec3d forward;
		Vec3d up;
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean localPiloted = entity.getFirstPassenger() == mc.player
				&& DescentClientState.enabled && DescentClientState.attitudeValid && ShipAttitudeClient.isPrimed();
		if (localPiloted) {
			var att = ShipAttitudeClient.get();
			forward = att.forward();
			up = att.up();
		} else {
			forward = rotationVector(entity.getPitch(), yaw);
			up = new Vec3d(0, 1, 0);
		}
		ModelOrientation.applyBasis(matrices, 180f, forward, up);
		matrices.translate(0, -0.5, 0);
		model.setAngles(entity, 0, 0, entity.age + tickDelta, 0, 0);
		VertexConsumer vc = consumers.getBuffer(model.getLayer(TEXTURE));
		model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	/** Vanilla's own yaw/pitch → look-vector formula, inlined rather than trusted from memory as a
	 * method name/signature on {@code Entity} — see the class-level risk note in this codebase's other
	 * hand-reconstructed API surfaces (aeris-mirai step 6) for why that's the safer bet here. */
	private static Vec3d rotationVector(float pitch, float yaw) {
		double yawRad = Math.toRadians(-yaw - 180.0);
		double pitchRad = Math.toRadians(-pitch);
		double cosPitch = Math.cos(pitchRad);
		return new Vec3d(Math.sin(yawRad) * cosPitch, Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
	}

	@Override
	public Identifier getTexture(PyroShipEntity entity) {
		return TEXTURE;
	}
}
