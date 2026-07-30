package com.terminaldetector.drmd.entity.model;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

/** Placeholder reactor core visual (entity marker / display). */
public class ReactorCoreModel<T extends Entity> extends EntityModel<T> {
	private final ModelPart root;

	public ReactorCoreModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("core", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4f, -4f, -4f, 8f, 8f, 8f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("ring", ModelPartBuilder.create()
						.uv(0, 16).cuboid(-8f, -1f, -8f, 16f, 2f, 16f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("pillar_a", ModelPartBuilder.create()
						.uv(32, 0).cuboid(-1f, -10f, -1f, 2f, 20f, 2f),
				ModelTransform.pivot(6, 0, 6));
		root.addChild("pillar_b", ModelPartBuilder.create()
						.uv(32, 0).cuboid(-1f, -10f, -1f, 2f, 20f, 2f),
				ModelTransform.pivot(-6, 0, 6));
		root.addChild("pillar_c", ModelPartBuilder.create()
						.uv(32, 0).cuboid(-1f, -10f, -1f, 2f, 20f, 2f),
				ModelTransform.pivot(6, 0, -6));
		root.addChild("pillar_d", ModelPartBuilder.create()
						.uv(32, 0).cuboid(-1f, -10f, -1f, 2f, 20f, 2f),
				ModelTransform.pivot(-6, 0, -6));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		root.getChild("ring").yaw = animationProgress * 0.05f;
		root.getChild("core").yaw = -animationProgress * 0.03f;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
