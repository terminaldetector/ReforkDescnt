package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.entity.mob.TripodEntity;
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
 * Tripod strider — cubic hull, sensor turret, plasma lance, three splayed legs.
 *
 * <p>Texture is 128×128; the UV origins below must stay in step with {@code scripts/gen_textures.py}
 * ({@code ent_tripod}) or the atlas will land on the wrong faces.
 *
 * <p>Model space here is Y-down (the renderer flips it), so the ground sits at y = +24 and the hull
 * is built upward into negative Y.
 */
public class TripodModel extends EntityModel<TripodEntity> {
	private final ModelPart root;
	private final ModelPart sensor;
	private final ModelPart lance;
	private final ModelPart[] legs = new ModelPart[3];

	public TripodModel(ModelPart root) {
		this.root = root;
		this.sensor = root.getChild("sensor");
		this.lance = root.getChild("lance");
		for (int i = 0; i < 3; i++) {
			this.legs[i] = root.getChild("leg" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		root.addChild("hull", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-9f, -20f, -9f, 18f, 18f, 18f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("sensor", ModelPartBuilder.create()
						.uv(72, 0).cuboid(-5f, -7f, -5f, 10f, 7f, 10f),
				ModelTransform.pivot(0, -20f, 0));

		root.addChild("lance", ModelPartBuilder.create()
						.uv(72, 17).cuboid(-1.5f, -1.5f, -12f, 3f, 3f, 12f),
				ModelTransform.pivot(0, -12f, -9f));

		// Three legs at 120° — splayed outward so the hull reads as carried, not balanced.
		for (int i = 0; i < 3; i++) {
			double angle = Math.PI * 2 * i / 3.0;
			ModelPartData leg = root.addChild("leg" + i, ModelPartBuilder.create()
							.uv(0, 36).cuboid(-2.5f, 0f, -2.5f, 5f, 26f, 5f)
							.uv(20, 36).cuboid(-3.5f, 24f, -3.5f, 7f, 3f, 7f),
					ModelTransform.of(
							(float) (Math.sin(angle) * 6.5),
							-2f,
							(float) (Math.cos(angle) * 6.5),
							0f, (float) -angle, 0f));
			leg.addChild("hip" + i, ModelPartBuilder.create()
							.uv(20, 36).cuboid(-3.5f, -2f, -3.5f, 7f, 3f, 7f),
					ModelTransform.pivot(0, 0, 0));
		}

		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void setAngles(TripodEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		this.sensor.yaw = headYaw * ((float) Math.PI / 180f);
		this.sensor.pitch = headPitch * ((float) Math.PI / 180f);
		this.lance.pitch = headPitch * ((float) Math.PI / 180f) * 0.6f;

		// Legs stride out of phase; a charging tripod plants itself and stops walking.
		float gait = MathHelper.clamp(limbDistance, 0f, 1f);
		float charge = entity.getChargeLevel();
		float amplitude = gait * 0.55f * (1f - charge * 0.8f);
		for (int i = 0; i < legs.length; i++) {
			float phase = limbAngle * 0.6f + i * ((float) Math.PI * 2f / 3f);
			legs[i].pitch = MathHelper.cos(phase) * amplitude;
			legs[i].roll = MathHelper.sin(phase) * amplitude * 0.25f;
		}
		// Hull squats slightly as the lance charges.
		this.root.pivotY = charge * 1.6f;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
