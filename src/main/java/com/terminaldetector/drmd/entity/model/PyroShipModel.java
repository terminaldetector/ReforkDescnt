package com.terminaldetector.drmd.entity.model;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

/**
 * Placeholder Descent Pyro-class ship — elongated fuselage + wings + thruster.
 */
public class PyroShipModel<T extends Entity> extends EntityModel<T> {
	private final ModelPart root;

	public PyroShipModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		root.addChild("fuselage", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-2f, -2f, -10f, 4f, 4f, 16f)
						.uv(0, 20).cuboid(-1.5f, -1.5f, -12f, 3f, 3f, 3f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("wing_l", ModelPartBuilder.create()
						.uv(24, 0).cuboid(-10f, -0.5f, -2f, 8f, 1f, 6f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("wing_r", ModelPartBuilder.create()
						.uv(24, 8).cuboid(2f, -0.5f, -2f, 8f, 1f, 6f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("fin", ModelPartBuilder.create()
						.uv(40, 20).cuboid(-0.5f, -6f, 2f, 1f, 5f, 4f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("thruster", ModelPartBuilder.create()
						.uv(0, 28).cuboid(-1.5f, -1.5f, 6f, 3f, 3f, 3f),
				ModelTransform.pivot(0, 0, 0));

		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		root.yaw = headYaw * ((float) Math.PI / 180f);
		root.pitch = headPitch * ((float) Math.PI / 180f);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
