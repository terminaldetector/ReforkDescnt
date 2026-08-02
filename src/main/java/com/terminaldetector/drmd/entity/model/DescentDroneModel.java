package com.terminaldetector.drmd.entity.model;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

/** Placeholder Descent scout drone — core + 4 spars. */
public class DescentDroneModel<T extends Entity> extends EntityModel<T> {
	private final ModelPart root;

	public DescentDroneModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("core", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-3f, -3f, -3f, 6f, 6f, 6f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("spar_n", ModelPartBuilder.create()
						.uv(0, 12).cuboid(-1f, -1f, -8f, 2f, 2f, 5f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("spar_s", ModelPartBuilder.create()
						.uv(0, 12).cuboid(-1f, -1f, 3f, 2f, 2f, 5f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("spar_e", ModelPartBuilder.create()
						.uv(16, 12).cuboid(3f, -1f, -1f, 5f, 2f, 2f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("spar_w", ModelPartBuilder.create()
						.uv(16, 12).cuboid(-8f, -1f, -1f, 5f, 2f, 2f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("eye", ModelPartBuilder.create()
						.uv(0, 20).cuboid(-1.5f, -1.5f, -4.5f, 3f, 3f, 2f),
				ModelTransform.pivot(0, 0, 0));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		root.yaw = headYaw * ((float) Math.PI / 180f);
		root.pitch = headPitch * ((float) Math.PI / 180f);
		float spin = animationProgress * 0.15f;
		var n = root.getChild("spar_n");
		var s = root.getChild("spar_s");
		n.roll = spin;
		s.roll = -spin;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
