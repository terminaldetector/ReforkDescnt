package com.terminaldetector.drmd.entity.model;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

/**
 * Pyro GX — 6DOF drone hull, hand-built from the ÆRis/concept-art hull layout: a tapered central
 * fuselage, twin outboard weapon pods (laser + missile per pod, port and starboard), a top-mounted
 * twin-tube cluster-bomb dispenser, and an aft-ventral mining/construction laser rig. Nose points
 * along -Z and -Y is up, matching {@link com.terminaldetector.drmd.client.render.ModelOrientation}'s
 * documented convention — see {@code PyroShipRenderer} for where that basis is applied.
 *
 * <p>Every cuboid is placed by absolute local coordinates with no part-level rotation (the same
 * convention the previous placeholder used) — deliberately, since this project has no way to render
 * a frame and check a rotated part's silhouette against the concept art before shipping it.
 *
 * <p>Weapon-to-hardpoint mapping (as reported live, not from the concept art alone — the art's own
 * top slot reads as generic rockets, but cluster bombs are what actually fires from there):
 * top pod = cluster bombs, wing pods = combat lasers + offensive missiles, ventral rig = the
 * mining/construction laser.
 */
public class PyroShipModel<T extends Entity> extends EntityModel<T> {
	private final ModelPart root;

	public PyroShipModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// Central fuselage: main hull, tapered nose, nose tip.
		root.addChild("fuselage", ModelPartBuilder.create()
						.uv(2, 2).cuboid(-2.5f, -2.5f, -4f, 5f, 5f, 10f)
						.uv(2, 18).cuboid(-1.5f, -1.5f, -8f, 3f, 3f, 4f)
						.uv(20, 18).cuboid(-0.8f, -0.8f, -10f, 1.6f, 1.6f, 2f),
				ModelTransform.pivot(0, 0, 0));

		// Wing pods (the hull's "Y" arms) — overlap the fuselage's side faces by 0.5 so there is no
		// seam gap. Same uv origin on both sides: same size box, only mirrored in world position, so
		// there is nothing a flat-color texture would show differently between them.
		root.addChild("wing_l", ModelPartBuilder.create()
						.uv(44, 2).cuboid(-9f, -1.5f, -3f, 7f, 3f, 6f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("wing_r", ModelPartBuilder.create()
						.uv(44, 2).cuboid(2f, -1.5f, -3f, 7f, 3f, 6f),
				ModelTransform.pivot(0, 0, 0));

		// Combat lasers — forward on each pod. "Sides are combat lasers and offensive missiles."
		root.addChild("laser_l", ModelPartBuilder.create()
						.uv(44, 20).cuboid(-9.5f, -1f, -5.5f, 2f, 2f, 2.5f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("laser_r", ModelPartBuilder.create()
						.uv(44, 20).cuboid(7.5f, -1f, -5.5f, 2f, 2f, 2.5f),
				ModelTransform.pivot(0, 0, 0));

		// Offensive missile tubes — aft on each pod, alongside the laser.
		root.addChild("missile_l", ModelPartBuilder.create()
						.uv(100, 2).cuboid(-9.5f, -0.5f, 0f, 2f, 2f, 2.5f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("missile_r", ModelPartBuilder.create()
						.uv(100, 2).cuboid(7.5f, -0.5f, 0f, 2f, 2f, 2.5f),
				ModelTransform.pivot(0, 0, 0));

		// Top pod — cluster bomb dispenser, twin tubes. "Cluster bombs fire from the top."
		root.addChild("top_pod", ModelPartBuilder.create()
						.uv(2, 28).cuboid(-2f, -4.5f, -3f, 4f, 2f, 4f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("top_pod_tubes", ModelPartBuilder.create()
						.uv(44, 28).cuboid(-1.5f, -3.5f, -6f, 1.2f, 1.2f, 3f)
						.uv(44, 28).cuboid(0.3f, -3.5f, -6f, 1.2f, 1.2f, 3f),
				ModelTransform.pivot(0, 0, 0));

		// Ventral rig — mining/construction laser, housing + downward emitter nub.
		// "Below comes the construction beam and the mining beam."
		root.addChild("bottom_rig", ModelPartBuilder.create()
						.uv(74, 28).cuboid(-1.5f, 2.5f, -1f, 3f, 2f, 4f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("bottom_rig_emitter", ModelPartBuilder.create()
						.uv(114, 28).cuboid(-0.6f, 4.5f, 0.4f, 1.2f, 2f, 1.2f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("fin", ModelPartBuilder.create()
						.uv(2, 50).cuboid(-0.5f, -6.5f, 3f, 1f, 4.5f, 3.5f),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("thruster_l", ModelPartBuilder.create()
						.uv(34, 50).cuboid(-2.2f, -1.2f, 6f, 2f, 2.4f, 2.5f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("thruster_r", ModelPartBuilder.create()
						.uv(34, 50).cuboid(0.2f, -1.2f, 6f, 2f, 2.4f, 2.5f),
				ModelTransform.pivot(0, 0, 0));

		return TexturedModelData.of(data, 128, 96);
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
