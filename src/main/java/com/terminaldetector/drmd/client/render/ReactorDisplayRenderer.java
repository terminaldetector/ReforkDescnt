package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.entity.model.ReactorCoreModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class ReactorDisplayRenderer extends EntityRenderer<ReactorDisplayEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "reactor_core"), "main");
	private static final Identifier TEXTURE = Identifier.of(DescentMod.MOD_ID, "textures/entity/reactor_core.png");
	private final ReactorCoreModel<ReactorDisplayEntity> model;

	public ReactorDisplayRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.model = new ReactorCoreModel<>(ctx.getPart(LAYER));
	}

	@Override
	public void render(ReactorDisplayEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		matrices.scale(-1.5f, -1.5f, 1.5f);
		matrices.translate(0, -1.0, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 2f));
		model.setAngles(entity, 0, 0, entity.age + tickDelta, 0, 0);
		VertexConsumer vc = consumers.getBuffer(model.getLayer(TEXTURE));
		model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(ReactorDisplayEntity entity) {
		return TEXTURE;
	}
}
