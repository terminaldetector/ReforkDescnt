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
        return getTexturedModelData(com.terminaldetector.drmd.client.presentation.Ps1AssetProfile.NATIVE);
    }

    public static TexturedModelData getLowPolyTexturedModelData() {
        return getTexturedModelData(com.terminaldetector.drmd.client.presentation.Ps1AssetProfile.LOW_POLY);
    }

    private static TexturedModelData getTexturedModelData(com.terminaldetector.drmd.client.presentation.Ps1AssetProfile profile) {
        var data=new ModelData();var root=data.getRoot();
        var parts=new java.util.HashMap<String,ModelPartData>();parts.put("",root);
        for(var part:PyroShipGeometry.parts(profile)) {
            var builder=ModelPartBuilder.create();
            for(var box:part.boxes())builder.uv(box.u(),box.v()).cuboid(box.x(),box.y(),box.z(),box.width(),box.height(),box.depth());
            var parent=parts.get(part.parent());
            if(parent==null)throw new IllegalStateException("Missing authored Pyro part parent: "+part.parent());
            parts.put(part.name(),parent.addChild(part.name(),builder,ModelTransform.of(part.x(),part.y(),part.z(),part.pitch(),part.yaw(),part.roll())));
        }
        return TexturedModelData.of(data,128,128);
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
