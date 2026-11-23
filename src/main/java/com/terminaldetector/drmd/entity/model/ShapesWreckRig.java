package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import com.terminaldetector.drmd.physics.ragdoll.RagdollSimulation;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.gravity.ActorFrameMath;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.List;

/** Infantry part coordinates match the Shapes renderer's baked model in its upright pose. */
public final class ShapesWreckRig {
    public static final String ID = "shapes_infantry";
    public enum Part {
        TORSO("body", 22, 0, 38, 6, 4, 6, 2),
        HEAD("head", 5, 0, 37, -4, 4, 4, 4),
        RIGHT_ARM("right_arm", 4, -5, 36, 13, 1, 15, 1),
        LEFT_ARM("left_arm", 4, 5, 36, 13, 1, 15, 1),
        RIGHT_LEG("right_leg", 7, -2, 29, 15, 1, 15, 1),
        LEFT_LEG("left_leg", 7, 2, 29, 15, 1, 15, 1);

        private final String modelPart;
        private final double mass;
        private final Vec3 pivot;
        private final Vec3 half;
        private final double assetCenterY;

        Part(String modelPart, double mass, int x, int y, int centerY, int hx, int hy, int hz) {
            this.modelPart = modelPart;
            this.mass = mass;
            this.pivot = new Vec3(x / 16.0, y / 16.0, 0);
            this.half = new Vec3(hx / 16.0, hy / 16.0, hz / 16.0);
            this.assetCenterY = centerY / 16.0;
        }

        public String modelPart() { return modelPart; }
        public double assetCenterY() { return assetCenterY; }
        private Vec3 centerFromPivot(double scale) { return new Vec3(0, -assetCenterY * scale, 0); }
        private Vec3 restCenter() { return pivot.plus(centerFromPivot(1)); }
    }

    private static final List<Part> PARTS = List.of(Part.values());

    private ShapesWreckRig() {}

    public static List<Part> parts() { return PARTS; }
    private static final double MASS = PARTS.stream().mapToDouble(part -> part.mass).sum();

    public static double mass(double scale) { return MASS * scale * scale * scale; }

    public static RagdollRig create(double scale) {
        if (!FlightWreckRig.validScale(scale)) throw new IllegalArgumentException("invalid infantry model scale");
        var segments = PARTS.stream().map(part -> new RagdollRig.Segment(part.modelPart,
                part.mass * scale * scale * scale, part.half.scaled(scale), part.restCenter().scaled(scale))).toList();
        var joints = PARTS.subList(1, PARTS.size()).stream().map(part -> {
            double swing = part == Part.HEAD ? 85 : 90;
            double twist = part == Part.RIGHT_ARM || part == Part.LEFT_ARM ? 150 : 95;
            var limits = new RagdollRig.AngularLimits(Quat.IDENTITY, Quat.IDENTITY,
                    Math.toRadians(swing), Math.toRadians(-twist), Math.toRadians(twist));
            return new RagdollRig.Joint(0, part.ordinal(),
                    part.pivot.minus(Part.TORSO.restCenter()).scaled(scale), part.centerFromPivot(scale).scaled(-1),
                    0, limits);
        }).toList();
        return new RagdollRig(segments, joints);
    }

    public static float gunPitch(float headPitch) { return -MathHelper.HALF_PI + headPitch; }
    public static float gunYaw(float headYaw, boolean left) { return headYaw + (left ? .45f : -.1f); }

    private record Pose(Vec3 center, Quat rotation) {}

    private static Pose pose(ShapesInfantryEntity actor, Part part, Quat frame, double scale) {
        float aimYaw = MathHelper.wrapDegrees(actor.getHeadYaw() - actor.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        float aimPitch = actor.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        float swing = MathHelper.clamp(MathHelper.cos(actor.limbAnimator.getPos() * .6662f)
                * .7f * actor.limbAnimator.getSpeed(), -.4f, .4f);
        float pitch = switch (part) {
            case TORSO -> 0;
            case HEAD -> aimPitch;
            case RIGHT_ARM -> actor.hasInfantryGun() ? gunPitch(aimPitch) : -swing;
            case LEFT_ARM -> actor.hasInfantryGun() ? gunPitch(aimPitch) : swing;
            case RIGHT_LEG -> swing;
            case LEFT_LEG -> -swing;
        };
        float yaw = switch (part) {
            case HEAD -> aimYaw;
            case RIGHT_ARM -> actor.hasInfantryGun() ? gunYaw(aimYaw, false) : 0;
            case LEFT_ARM -> actor.hasInfantryGun() ? gunYaw(aimYaw, true) : 0;
            default -> 0;
        };
        var angles = new Quaternionf().rotationZYX(-actor.hitTilt(part, actor.age), -yaw, pitch);
        var local = new Quat(angles.x, angles.y, angles.z, angles.w);
        Vec3 pivot = part == Part.HEAD && actor.isAlert() ? part.pivot.plus(new Vec3(0, 5 / 16.0, 0)) : part.pivot;
        Vec3 offset = pivot.scaled(scale).plus(local.rotate(part.centerFromPivot(scale)));
        return new Pose(toPure(actor.getPos()).plus(frame.rotate(offset)),
                ImmPtlQuaternions.hamiltonProduct(frame, local).normalized());
    }

    private static Quat frame(ShapesInfantryEntity actor) {
        Vec3d up = ActorFrames.active(actor) ? ActorFrames.up(actor) : ActorFrameMath.WORLD_UP;
        Vec3d forward = ActorFrameMath.toWorld(Vec3d.fromPolar(0, actor.bodyYaw), up);
        Vec3d right = up.crossProduct(forward).normalize();
        var axes = new Matrix3f((float) right.x, (float) right.y, (float) right.z,
                (float) up.x, (float) up.y, (float) up.z, (float) forward.x, (float) forward.y, (float) forward.z);
        var q = new Quaternionf().setFromNormalized(axes);
        return new Quat(q.x, q.y, q.z, q.w).normalized();
    }

    public static void inheritPose(RagdollSimulation simulation, ShapesInfantryEntity actor) {
        Quat frame = frame(actor);
        for (Part part : PARTS) {
            Pose pose = pose(actor, part, frame, actor.getScale());
            simulation.parts().get(part.ordinal()).body().withPosition(pose.center).withRotation(pose.rotation);
        }
    }

    public static Part nearestPart(ShapesInfantryEntity actor, Vec3d hit) {
        Part nearest = Part.TORSO;
        double distance = Double.POSITIVE_INFINITY;
        Vec3 point = toPure(hit);
        Quat frame = frame(actor);
        double scale = actor.getScale();
        for (Part part : PARTS) {
            Pose pose = pose(actor, part, frame, scale);
            var box = new OrientedBox(pose.center, part.half.scaled(scale), pose.rotation);
            double candidate = box.closestPoint(point).minus(point).lengthSquared();
            if (candidate < distance) {
                nearest = part;
                distance = candidate;
            }
        }
        return nearest;
    }

    private static Vec3 toPure(Vec3d point) { return new Vec3(point.x, point.y, point.z); }
}
