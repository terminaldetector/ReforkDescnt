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
 * Early War of the Worlds war machine (1953 / transistor-age look).
 *
 * <p>Big copper chassis like a mid-century electronic set, manta wing plates, long swan neck
 * with a heat-ray bulb — tall spindly legs under a heavy body. Intentionally large.
 *
 * <p>128×128 atlas ({@code textures/entity/tripod.png}). Y-down model space (ground toward +Y).
 */
public class TripodModel extends EntityModel<TripodEntity> {
	private final ModelPart root;
	private final ModelPart chassis;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart ray;
	private final ModelPart[] hips = new ModelPart[3];
	private final ModelPart[] uppers = new ModelPart[3];
	private final ModelPart[] lowers = new ModelPart[3];

	public TripodModel(ModelPart root) {
		this.root = root;
		this.chassis = root.getChild("chassis");
		this.neck = chassis.getChild("neck");
		this.head = neck.getChild("head");
		this.ray = head.getChild("ray");
		for (int i = 0; i < 3; i++) {
			this.hips[i] = root.getChild("hip" + i);
			this.uppers[i] = hips[i].getChild("upper" + i);
			this.lowers[i] = uppers[i].getChild("lower" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// —— transistor / manta chassis (the bulk of the machine) ——
		ModelPartData chassis = root.addChild("chassis", ModelPartBuilder.create()
						// main copper body
						.uv(0, 0).cuboid(-14f, -10f, -10f, 28f, 12f, 20f)
						// top dome (valve / tube housing)
						.uv(0, 32).cuboid(-10f, -16f, -7f, 20f, 6f, 14f)
						// underbelly
						.uv(0, 52).cuboid(-11f, 2f, -8f, 22f, 4f, 16f),
				ModelTransform.pivot(0, -28f, 0));

		// Manta side wings / heat vanes
		chassis.addChild("wing_l", ModelPartBuilder.create()
						.uv(68, 0).cuboid(-16f, -2f, -6f, 16f, 3f, 12f)
						.uv(68, 16).cuboid(-20f, -1f, -4f, 6f, 2f, 8f),
				ModelTransform.of(-12f, -4f, 0f, 0f, 0f, 0.18f));
		chassis.addChild("wing_r", ModelPartBuilder.create()
						.uv(68, 0).cuboid(0f, -2f, -6f, 16f, 3f, 12f)
						.uv(68, 16).cuboid(14f, -1f, -4f, 6f, 2f, 8f),
				ModelTransform.of(12f, -4f, 0f, 0f, 0f, -0.18f));

		// Rear radiator grille plate
		chassis.addChild("radiator", ModelPartBuilder.create()
						.uv(68, 28).cuboid(-8f, -8f, 8f, 16f, 10f, 4f),
				ModelTransform.pivot(0, 0, 0));

		// —— swan neck (1953 goose-neck heat ray) ——
		ModelPartData neck = chassis.addChild("neck", ModelPartBuilder.create()
						.uv(96, 0).cuboid(-2.5f, -18f, -2.5f, 5f, 20f, 5f)
						.uv(96, 26).cuboid(-3f, -10f, -3.5f, 6f, 6f, 6f),
				ModelTransform.of(0f, -12f, -8f, 0.55f, 0f, 0f));

		ModelPartData head = neck.addChild("head", ModelPartBuilder.create()
						// bulbous sensor / lens housing
						.uv(96, 42).cuboid(-5f, -5f, -5f, 10f, 10f, 10f)
						.uv(68, 44).cuboid(-3.5f, -3.5f, -7f, 7f, 7f, 3f),
				ModelTransform.of(0f, -18f, 0f, -0.35f, 0f, 0f));

		head.addChild("ray", ModelPartBuilder.create()
						.uv(112, 0).cuboid(-1.5f, -1.5f, -16f, 3f, 3f, 12f)
						.uv(112, 16).cuboid(-2.5f, -2.5f, -18f, 5f, 5f, 4f),
				ModelTransform.pivot(0, 0, -4f));

		// —— three tall spindly legs under the heavy body ——
		for (int i = 0; i < 3; i++) {
			double angle = Math.PI * 2 * i / 3.0;
			float hx = (float) (Math.sin(angle) * 9f);
			float hz = (float) (Math.cos(angle) * 9f);

			ModelPartData hip = root.addChild("hip" + i, ModelPartBuilder.create()
							.uv(0, 72).cuboid(-3.5f, -3f, -3.5f, 7f, 5f, 7f),
					ModelTransform.of(hx, -24f, hz, 0.45f, (float) -angle, 0f));

			ModelPartData upper = hip.addChild("upper" + i, ModelPartBuilder.create()
							.uv(28, 72).cuboid(-2f, 0f, -2f, 4f, 22f, 4f)
							.uv(44, 72).cuboid(-2.5f, 18f, -2.5f, 5f, 5f, 5f),
					ModelTransform.pivot(0, 2f, 0));

			ModelPartData lower = upper.addChild("lower" + i, ModelPartBuilder.create()
							.uv(64, 72).cuboid(-1.5f, 0f, -1.5f, 3f, 24f, 3f),
					ModelTransform.pivot(0, 22f, 0));

			lower.addChild("foot" + i, ModelPartBuilder.create()
							.uv(76, 72).cuboid(-4f, 0f, -6f, 8f, 3f, 10f)
							.uv(76, 86).cuboid(-5f, 1f, -8f, 3f, 2f, 5f)
							.uv(92, 86).cuboid(2f, 1f, -8f, 3f, 2f, 5f),
					ModelTransform.pivot(0, 24f, 0));
		}

		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void setAngles(TripodEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		float yaw = headYaw * ((float) Math.PI / 180f);
		float pitch = headPitch * ((float) Math.PI / 180f);
		float charge = entity.getChargeLevel();

		// Chassis yaws slowly; neck tracks harder — that transistor head turns to look at you.
		this.chassis.yaw = yaw * 0.35f;
		this.neck.yaw = yaw * 0.7f;
		this.neck.pitch = 0.55f + pitch * 0.4f - charge * 0.15f;
		this.head.yaw = yaw * 0.35f;
		this.head.pitch = -0.35f + pitch * 0.5f;
		this.ray.pitch = pitch * 0.4f + charge * 0.55f;

		// Idle: chassis hums / floats a little (1953 machines felt suspended).
		float hum = MathHelper.sin(animationProgress * 0.08f) * 0.6f;
		this.chassis.pivotY = -28f + hum - charge * 1.5f;

		float gait = MathHelper.clamp(limbDistance, 0f, 1f);
		float plant = 1f - charge * 0.8f;
		float amp = gait * 0.55f * plant;

		for (int i = 0; i < 3; i++) {
			float phase = limbAngle * 0.5f + i * ((float) Math.PI * 2f / 3f);
			float swing = MathHelper.cos(phase) * amp;
			float lift = MathHelper.sin(phase) * amp * 0.4f;
			hips[i].pitch = 0.45f + swing * 0.4f + charge * 0.15f;
			hips[i].roll = MathHelper.sin(phase * 0.5f) * amp * 0.1f;
			uppers[i].pitch = swing * 0.7f - lift * 0.25f;
			lowers[i].pitch = -0.65f - swing * 0.75f + lift * 0.4f + charge * 0.2f;
		}

		this.root.pivotY = charge * 2.8f;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
