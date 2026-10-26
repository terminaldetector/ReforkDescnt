package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity;
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
 * Dark Oblivion drone — faceted sphere, recessed eye, twin side guns, equatorial ring.
 * Atlas 64×64 ({@code textures/entity/oblivion_seeker.png}).
 */
public class OblivionSeekerModel extends EntityModel<OblivionSeekerEntity> {
	private final ModelPart root;
	private final ModelPart ring;
	private final ModelPart eye;
	private final ModelPart gunL;
	private final ModelPart gunR;

	public OblivionSeekerModel(ModelPart root) {
		this.root = root;
		this.ring = root.getChild("ring");
		this.eye = root.getChild("eye");
		this.gunL = root.getChild("gun_l");
		this.gunR = root.getChild("gun_r");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// Main sphere (stacked cuboids)
		root.addChild("core", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-5f, -5f, -5f, 10f, 10f, 10f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("core_mid", ModelPartBuilder.create()
						.uv(0, 20).cuboid(-6f, -3.5f, -4f, 12f, 7f, 8f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("core_cap_u", ModelPartBuilder.create()
						.uv(40, 0).cuboid(-3.5f, 5f, -3.5f, 7f, 2f, 7f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("core_cap_d", ModelPartBuilder.create()
						.uv(40, 10).cuboid(-3.5f, -7f, -3.5f, 7f, 2f, 7f),
				ModelTransform.pivot(0, 0, 0));

		// Recessed sensor eye (front)
		root.addChild("eye_well", ModelPartBuilder.create()
						.uv(40, 20).cuboid(-2.5f, -2.5f, -7f, 5f, 5f, 2f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("eye", ModelPartBuilder.create()
						.uv(54, 20).cuboid(-1.5f, -1.5f, -7.6f, 3f, 3f, 1f),
				ModelTransform.pivot(0, 0, 0));

		// Twin barrels
		root.addChild("gun_l", ModelPartBuilder.create()
						.uv(0, 36).cuboid(-1f, -1f, -5f, 2f, 2f, 8f),
				ModelTransform.pivot(-7f, 0.5f, 1f));
		root.addChild("gun_r", ModelPartBuilder.create()
						.uv(0, 36).cuboid(-1f, -1f, -5f, 2f, 2f, 8f),
				ModelTransform.pivot(7f, 0.5f, 1f));

		// Equatorial detail ring
		root.addChild("ring", ModelPartBuilder.create()
						.uv(20, 36).cuboid(-8f, -0.75f, -8f, 16f, 1.5f, 16f),
				ModelTransform.pivot(0, 0, 0));

		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(OblivionSeekerEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		this.eye.yaw = headYaw * ((float) Math.PI / 180f) * 0.35f;
		this.eye.pitch = headPitch * ((float) Math.PI / 180f) * 0.35f;
		this.ring.yaw = entity.getRingSpin() * ((float) Math.PI / 180f);
		float agitate = entity.isAlert() ? 0.2f : 0.06f;
		this.ring.roll = MathHelper.sin(animationProgress * 0.22f) * agitate;
		float recoil = entity.getFirePhase() == OblivionSeekerEntity.Fire.MG_BURST ? 0.25f : 0.05f;
		this.gunL.pitch = -recoil + MathHelper.sin(animationProgress * 0.4f) * 0.08f;
		this.gunR.pitch = -recoil + MathHelper.cos(animationProgress * 0.4f) * 0.08f;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
