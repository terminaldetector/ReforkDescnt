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
 * <p>Wings, the tail fin, and the thrusters are mounted with real {@code ModelTransform.of(x, y, z,
 * pitch, roll, yaw)} rotations (dihedral, sweep, outward cant) — hand-calculated the same way
 * {@link TripodModel} builds its own wing dihedral and swan-neck bend, not from an iterative
 * render-and-check loop this project still has no way to run. The core hull (fuselage, top pod,
 * ventral rig) stays axis-aligned; only the appendages that actually read better tilted are rotated.
 *
 * <p>Weapon-to-hardpoint mapping (as reported live, not from the concept art alone — the art's own
 * top slot reads as generic rockets, but cluster bombs are what actually fires from there):
 * top pod = cluster bombs, wing pods = combat lasers + offensive missiles, ventral rig = the
 * mining/construction laser. {@code laser_l}/{@code missile_l} (and the {@code _r} pair) are now
 * children of their wing mount so they tilt with the wing's dihedral instead of floating flat
 * against a tilted surface.
 */
public class PyroShipModel<T extends Entity> extends EntityModel<T> {
	private final ModelPart root;

	public PyroShipModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// Central fuselage: main hull, tapered nose, nose tip, plus a dorsal sensor spine and a
		// ventral keel strip for silhouette detail — both purely cosmetic, no hardpoint of their own.
		root.addChild("fuselage", ModelPartBuilder.create()
						.uv(2, 2).cuboid(-2.5f, -2.5f, -4f, 5f, 5f, 10f)
						.uv(2, 18).cuboid(-1.5f, -1.5f, -8f, 3f, 3f, 4f)
						.uv(20, 18).cuboid(-0.8f, -0.8f, -10f, 1.6f, 1.6f, 2f)
						.uv(2, 96).cuboid(-0.6f, -3.4f, -1f, 1.2f, 0.9f, 3f)
						.uv(2, 104).cuboid(-1f, 2.5f, -2f, 2f, 0.6f, 5f),
				ModelTransform.pivot(0, 0, 0));

		// Small forward canards, purely cosmetic — a fighter-silhouette cue near the nose taper.
		root.addChild("canard_l", ModelPartBuilder.create()
						.uv(20, 96).cuboid(-4.5f, -0.4f, -6.5f, 3f, 0.8f, 1.6f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("canard_r", ModelPartBuilder.create()
						.uv(20, 96).cuboid(1.5f, -0.4f, -6.5f, 3f, 0.8f, 1.6f),
				ModelTransform.pivot(0, 0, 0));

		// Wing mounts (the hull's "Y" arms) — rolled outward for dihedral, matching TripodModel's
		// own wing_l/wing_r idiom. laser_l/missile_l are children so they inherit the tilt.
		ModelPartData wingL = root.addChild("wing_l", ModelPartBuilder.create()
						.uv(44, 2).cuboid(-7f, -1.5f, -3f, 7f, 3f, 6f),
				ModelTransform.of(-2f, 0f, 0f, 0f, 0f, 0.12f));
		ModelPartData wingR = root.addChild("wing_r", ModelPartBuilder.create()
						.uv(44, 2).cuboid(0f, -1.5f, -3f, 7f, 3f, 6f),
				ModelTransform.of(2f, 0f, 0f, 0f, 0f, -0.12f));

		// Combat lasers — forward on each pod, plus a thin barrel tip. "Sides are combat lasers and
		// offensive missiles."
		wingL.addChild("laser_l", ModelPartBuilder.create()
						.uv(44, 20).cuboid(-7.5f, -1f, -5.5f, 2f, 2f, 2.5f)
						.uv(38, 96).cuboid(-7f, -0.6f, -6.5f, 1.2f, 1.2f, 1f),
				ModelTransform.pivot(0, 0, 0));
		wingR.addChild("laser_r", ModelPartBuilder.create()
						.uv(44, 20).cuboid(5.5f, -1f, -5.5f, 2f, 2f, 2.5f)
						.uv(38, 96).cuboid(5.8f, -0.6f, -6.5f, 1.2f, 1.2f, 1f),
				ModelTransform.pivot(0, 0, 0));

		// Offensive missile tubes — aft on each pod, alongside the laser, plus twin tube caps.
		wingL.addChild("missile_l", ModelPartBuilder.create()
						.uv(100, 2).cuboid(-7.5f, -0.5f, 0f, 2f, 2f, 2.5f)
						.uv(60, 96).cuboid(-7.5f, -0.3f, 2.5f, 0.8f, 0.8f, 0.6f)
						.uv(60, 100).cuboid(-6.3f, -0.3f, 2.5f, 0.8f, 0.8f, 0.6f),
				ModelTransform.pivot(0, 0, 0));
		wingR.addChild("missile_r", ModelPartBuilder.create()
						.uv(100, 2).cuboid(5.5f, -0.5f, 0f, 2f, 2f, 2.5f)
						.uv(60, 96).cuboid(5.5f, -0.3f, 2.5f, 0.8f, 0.8f, 0.6f)
						.uv(60, 100).cuboid(6.7f, -0.3f, 2.5f, 0.8f, 0.8f, 0.6f),
				ModelTransform.pivot(0, 0, 0));

		// Top pod — cluster bomb dispenser, twin tubes, plus a small sensor mast. "Cluster bombs
		// fire from the top."
		root.addChild("top_pod", ModelPartBuilder.create()
						.uv(2, 28).cuboid(-2f, -4.5f, -3f, 4f, 2f, 4f)
						.uv(80, 96).cuboid(-0.4f, -5.5f, -1f, 0.8f, 1f, 0.8f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("top_pod_tubes", ModelPartBuilder.create()
						.uv(44, 28).cuboid(-1.5f, -3.5f, -6f, 1.2f, 1.2f, 3f)
						.uv(44, 28).cuboid(0.3f, -3.5f, -6f, 1.2f, 1.2f, 3f),
				ModelTransform.pivot(0, 0, 0));

		// Ventral rig — mining/construction laser, housing + downward emitter nub, plus a support
		// strut to the fuselage. "Below comes the construction beam and the mining beam."
		root.addChild("bottom_rig", ModelPartBuilder.create()
						.uv(74, 28).cuboid(-1.5f, 2.5f, -1f, 3f, 2f, 4f)
						.uv(90, 96).cuboid(-0.5f, 1.8f, -0.5f, 1f, 0.8f, 1f),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("bottom_rig_emitter", ModelPartBuilder.create()
						.uv(114, 28).cuboid(-0.6f, 4.5f, 0.4f, 1.2f, 2f, 1.2f),
				ModelTransform.pivot(0, 0, 0));

		// Tail fin — swept aft (pitch) off a mount at its hull base, plus a short tip antenna.
		root.addChild("fin", ModelPartBuilder.create()
						.uv(2, 50).cuboid(-0.5f, -4.5f, 0f, 1f, 4.5f, 3.5f)
						.uv(100, 96).cuboid(-0.2f, -5.3f, 2.6f, 0.4f, 1f, 0.4f),
				ModelTransform.of(0f, -2f, 3f, 0.15f, 0f, 0f));

		// Thrusters — canted slightly outward (yaw), each with a trailing nozzle ring.
		root.addChild("thruster_l", ModelPartBuilder.create()
						.uv(34, 50).cuboid(-1f, -1.2f, 0f, 2f, 2.4f, 2.5f)
						.uv(2, 112).cuboid(-0.7f, -0.9f, 2.5f, 1.4f, 1.8f, 0.8f),
				ModelTransform.of(-1.2f, 0f, 6f, 0f, -0.1f, 0f));
		root.addChild("thruster_r", ModelPartBuilder.create()
						.uv(34, 50).cuboid(-1f, -1.2f, 0f, 2f, 2.4f, 2.5f)
						.uv(2, 112).cuboid(-0.7f, -0.9f, 2.5f, 1.4f, 1.8f, 0.8f),
				ModelTransform.of(1.2f, 0f, 6f, 0f, 0.1f, 0f));

		return TexturedModelData.of(data, 128, 128);
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
