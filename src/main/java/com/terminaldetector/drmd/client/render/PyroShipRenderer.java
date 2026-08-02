package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.entity.model.PyroShipModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class PyroShipRenderer extends EntityRenderer<PyroShipEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "pyro_ship"), "main");
	private static final Identifier TEXTURE = Identifier.of(DescentMod.MOD_ID, "textures/entity/pyro_ship.png");
	private final PyroShipModel<PyroShipEntity> model;

	public PyroShipRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.model = new PyroShipModel<>(ctx.getPart(LAYER));
		this.shadowRadius = 0.8f;
	}

	@Override
	public void render(PyroShipEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getPitch()));
		matrices.scale(-1f, -1f, 1f);
		matrices.translate(0, -0.5, 0);
		model.setAngles(entity, 0, 0, entity.age + tickDelta, 0, 0);
		VertexConsumer vc = consumers.getBuffer(model.getLayer(TEXTURE));
		model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(PyroShipEntity entity) {
		return TEXTURE;
	}
}
