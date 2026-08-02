package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.entity.mob.ScannerEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Flying scanner — sensor core, spinning detector ring and three rocket pods.
 *
 * <p>64×64 atlas; UV origins mirror {@code scripts/gen_textures.py} ({@code ent_scanner}).
 * Built around y = 0 like {@link DescentDroneModel}, since the entity never touches ground.
 */
public class ScannerModel extends EntityModel<ScannerEntity> {
	private final ModelPart root;
	private final ModelPart ring;
	private final ModelPart eye;
	private final ModelPart[] pods = new ModelPart[3];

	public ScannerModel(ModelPart root) {
		this.root = root;
		this.ring = root.getChild("ring");
		this.eye = root.getChild("eye");
		for (int i = 0; i < 3; i++) {
			this.pods[i] = ring.getChild("pod" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		root.addChild("core", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4f, -4f, -4f, 8f, 8f, 8f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("eye", ModelPartBuilder.create()
						.uv(32, 0).cuboid(-2f, -2f, -6f, 4f, 4f, 2f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("antenna", ModelPartBuilder.create()
						.uv(18, 34).cuboid(-0.5f, -9f, -0.5f, 1f, 5f, 1f),
				ModelTransform.pivot(0, 0, 0));

		ModelPartData ring = root.addChild("ring", ModelPartBuilder.create()
						.uv(0, 16).cuboid(-8f, -1f, -8f, 16f, 2f, 16f),
				ModelTransform.pivot(0, 0, 0));

		// Rocket pods ride the ring so the salvo visibly comes off a rotating carousel.
		for (int i = 0; i < 3; i++) {
			double angle = Math.PI * 2 * i / 3.0;
			ring.addChild("pod" + i, ModelPartBuilder.create()
							.uv(0, 34).cuboid(-1.5f, -1.5f, -3f, 3f, 3f, 6f),
					ModelTransform.of(
							(float) (Math.sin(angle) * 6.5), 0f, (float) (Math.cos(angle) * 6.5),
							0f, (float) -angle, 0f));
		}

		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(ScannerEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		this.eye.yaw = headYaw * ((float) Math.PI / 180f) * 0.4f;
		this.eye.pitch = headPitch * ((float) Math.PI / 180f) * 0.4f;
		this.ring.yaw = entity.getScanSpin() * ((float) Math.PI / 180f);
		// Ring tilts up as the drone winds through its salvo, then settles on recharge.
		float agitation = entity.getFirePhase() == ScannerEntity.Fire.RECHARGE ? 0.05f : 0.22f;
		this.ring.roll = MathHelper.sin(animationProgress * 0.18f) * agitation;
		for (ModelPart pod : pods) {
			pod.pitch = MathHelper.cos(animationProgress * 0.22f) * 0.12f;
		}
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
