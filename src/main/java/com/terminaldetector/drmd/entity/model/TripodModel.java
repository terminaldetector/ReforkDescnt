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
 * War of the Worlds–style fighting-machine tripod.
 *
 * <p>Silhouette: cobra-hood head with a red heat-ray eye, compact undercarriage, three long
 * multi-jointed legs splayed wide, and hanging probe tentacles — closer to the 2005 film machine
 * than a cubic robot.
 *
 * <p>128×128 atlas ({@code textures/entity/tripod.png} / {@code ent_tripod} in gen_textures).
 * Model space is Y-down (ground toward +Y); the hull builds into −Y.
 */
public class TripodModel extends EntityModel<TripodEntity> {
	private final ModelPart root;
	private final ModelPart hood;
	private final ModelPart eye;
	private final ModelPart emitter;
	private final ModelPart[] hips = new ModelPart[3];
	private final ModelPart[] uppers = new ModelPart[3];
	private final ModelPart[] lowers = new ModelPart[3];
	private final ModelPart[] tentacles = new ModelPart[4];

	public TripodModel(ModelPart root) {
		this.root = root;
		this.hood = root.getChild("hood");
		this.eye = hood.getChild("eye");
		this.emitter = hood.getChild("emitter");
		for (int i = 0; i < 3; i++) {
			this.hips[i] = root.getChild("hip" + i);
			this.uppers[i] = hips[i].getChild("upper" + i);
			this.lowers[i] = uppers[i].getChild("lower" + i);
		}
		for (int i = 0; i < 4; i++) {
			this.tentacles[i] = root.getChild("tentacle" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// —— undercarriage / “body” the legs hang from ——
		root.addChild("body", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-6f, -6f, -6f, 12f, 8f, 12f)
						.uv(0, 20).cuboid(-4.5f, -2f, -4.5f, 9f, 5f, 9f),
				ModelTransform.pivot(0, -6f, 0));

		// —— cobra hood / shield head (tilted forward visually via shape) ——
		ModelPartData hood = root.addChild("hood", ModelPartBuilder.create()
						// main shield plate
						.uv(48, 0).cuboid(-10f, -18f, -8f, 20f, 16f, 12f)
						// rear spine / nacelle
						.uv(48, 28).cuboid(-5f, -14f, 2f, 10f, 12f, 8f)
						// brow ridge
						.uv(0, 34).cuboid(-8f, -20f, -7f, 16f, 4f, 6f),
				ModelTransform.pivot(0, -10f, 0));

		hood.addChild("eye", ModelPartBuilder.create()
						.uv(112, 0).cuboid(-3f, -3f, -2f, 6f, 6f, 3f),
				ModelTransform.pivot(0, -10f, -9f));

		// Heat-ray / plasma emitter under the eye
		hood.addChild("emitter", ModelPartBuilder.create()
						.uv(112, 10).cuboid(-1.5f, -1.5f, -14f, 3f, 3f, 14f)
						.uv(112, 28).cuboid(-2.5f, -2.5f, -16f, 5f, 5f, 3f),
				ModelTransform.pivot(0, -6f, -6f));

		// —— three long articulated legs at 120° ——
		for (int i = 0; i < 3; i++) {
			double angle = Math.PI * 2 * i / 3.0 + Math.PI / 6.0; // slight offset for film stance
			float hx = (float) (Math.sin(angle) * 5.5);
			float hz = (float) (Math.cos(angle) * 5.5);

			ModelPartData hip = root.addChild("hip" + i, ModelPartBuilder.create()
							.uv(0, 44).cuboid(-3f, -3f, -3f, 6f, 5f, 6f),
					ModelTransform.of(hx, -4f, hz, 0.35f, (float) -angle, 0f));

			// Upper segment — long, slightly tapered look via thin cuboid
			ModelPartData upper = hip.addChild("upper" + i, ModelPartBuilder.create()
							.uv(24, 44).cuboid(-2f, 0f, -2f, 4f, 18f, 4f)
							.uv(40, 44).cuboid(-2.5f, 15f, -2.5f, 5f, 4f, 5f), // knee collar
					ModelTransform.pivot(0, 2f, 0));

			// Lower segment + clawed foot
			ModelPartData lower = upper.addChild("lower" + i, ModelPartBuilder.create()
							.uv(60, 48).cuboid(-1.5f, 0f, -1.5f, 3f, 20f, 3f)
							.uv(72, 48).cuboid(-1f, 16f, -1f, 2f, 6f, 2f),
					ModelTransform.pivot(0, 18f, 0));

			lower.addChild("foot" + i, ModelPartBuilder.create()
							.uv(80, 48).cuboid(-3.5f, 0f, -5f, 7f, 3f, 9f)
							.uv(80, 60).cuboid(-4f, 1f, -7f, 2f, 2f, 4f)
							.uv(92, 60).cuboid(2f, 1f, -7f, 2f, 2f, 4f),
					ModelTransform.pivot(0, 24f, 0));
		}

		// —— hanging probe tentacles (WotW underside cables) ——
		for (int i = 0; i < 4; i++) {
			double a = Math.PI * 2 * i / 4.0 + 0.4;
			root.addChild("tentacle" + i, ModelPartBuilder.create()
							.uv(112, 40).cuboid(-0.75f, 0f, -0.75f, 1.5f, 14f, 1.5f)
							.uv(118, 40).cuboid(-1.25f, 12f, -1.25f, 2.5f, 3f, 2.5f),
					ModelTransform.of(
							(float) (Math.sin(a) * 3.2),
							0f,
							(float) (Math.cos(a) * 3.2),
							0.15f, (float) -a, 0f));
		}

		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void setAngles(TripodEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		float yaw = headYaw * ((float) Math.PI / 180f);
		float pitch = headPitch * ((float) Math.PI / 180f);
		this.hood.yaw = yaw * 0.85f;
		this.hood.pitch = pitch * 0.55f;
		this.eye.yaw = yaw * 0.25f;
		this.eye.pitch = pitch * 0.35f;
		this.emitter.pitch = pitch * 0.7f + entity.getChargeLevel() * 0.35f;

		float gait = MathHelper.clamp(limbDistance, 0f, 1f);
		float charge = entity.getChargeLevel();
		float plant = 1f - charge * 0.85f;
		float amp = gait * 0.7f * plant;

		for (int i = 0; i < 3; i++) {
			float phase = limbAngle * 0.55f + i * ((float) Math.PI * 2f / 3f);
			float swing = MathHelper.cos(phase) * amp;
			float lift = MathHelper.sin(phase) * amp * 0.45f;
			// Hip opens outward; upper swings; knee opposite for spider-like stride.
			hips[i].pitch = 0.35f + swing * 0.55f + charge * 0.2f;
			hips[i].roll = MathHelper.sin(phase * 0.5f) * amp * 0.12f;
			uppers[i].pitch = swing * 0.85f - lift * 0.3f;
			lowers[i].pitch = -0.55f - swing * 0.9f + lift * 0.5f + charge * 0.25f;
		}

		// Tentacles sway / hang heavier while charging.
		for (int i = 0; i < tentacles.length; i++) {
			float t = animationProgress * 0.12f + i * 1.1f;
			tentacles[i].pitch = 0.2f + MathHelper.sin(t) * 0.18f + charge * 0.25f;
			tentacles[i].roll = MathHelper.cos(t * 0.8f) * 0.12f;
		}

		// Hull squats as the heat-ray charges.
		this.root.pivotY = charge * 2.2f;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
