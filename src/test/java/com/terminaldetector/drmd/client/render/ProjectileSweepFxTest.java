package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.weapon.projectile.ProjectileVisual;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileSweepFxTest {
    private static final double EPSILON = 1e-6;
    private static final ShotVisualProfile LASER = ShotVisualProfile.of(ProjectileVisual.LASER);

    @Test
    void actualRibbonFollowsTheSweepInEveryDirection() {
        var directions = List.of(
                new Vec3d(0, 0, 1), new Vec3d(0, 0, -1), new Vec3d(0, 1, 0), new Vec3d(0, -1, 0),
                new Vec3d(2, -3, 5).normalize());
        for (Vec3d forward : directions) {
            for (Vec3d toEye : List.of(new Vec3d(7, -3, .5), new Vec3d(-2, 9, 5), forward)) {
                var side = ShotGeometry.transverse(forward, toEye);
                var sink = new GeometrySink();
                ShotGeometry.ribbon(sink, new MatrixStack().peek(), forward, side, 0, 1.7f,
                        .14f, .12f, 0xAAFFCC, .9f);

                assertEquals(4, sink.points.size());
                midpoint(sink.points.get(1), sink.points.get(2), Vec3d.ZERO);
                midpoint(sink.points.get(0), sink.points.get(3), forward.multiply(-1.7));
                for (Vec3d vertex : sink.points) {
                    assertTrue(vertex.dotProduct(forward) <= EPSILON, "art must not lead the collision tip");
                }
            }
        }
    }

    @Test
    void directHeadOnViewHasACompactCapOnTheSweepPlane() {
        var direction = new Vec3d(0, 1, 0);
        var side = ShotGeometry.transverse(direction, direction);
        var sink = new GeometrySink();
        ShotGeometry.disc(sink, new MatrixStack().peek(), side, direction.crossProduct(side),
                .16f, 0xFFFFFF, 1);
        assertEquals(4, sink.points.size());
        assertTrue(sink.points.get(0).distanceTo(sink.points.get(2)) > .4);
        for (Vec3d point : sink.points) assertEquals(0, point.dotProduct(direction), EPSILON);
    }

    @Test
    void cruiseAndHighSpeedTrailsStayCappedNearTheCamera() {
        assertTrue(LASER.streakLength(3.875, 1, 10) > .5);
        assertEquals(1.125, LASER.streakLength(3.875, 1, 1.5), EPSILON);
        assertEquals(0, LASER.streakLength(3.875, 1, 0), EPSILON);
        assertEquals(3.25, LASER.streakLength(100, 4, 200), EPSILON);
        assertTrue(LASER.streakLength(4, 1, 20) > LASER.streakLength(1, 1, 20));
    }

    private static void midpoint(Vec3d a, Vec3d b, Vec3d expected) {
        assertEquals(0, a.lerp(b, .5).squaredDistanceTo(expected), EPSILON);
    }

    private static final class GeometrySink implements VertexConsumer {
        final List<Vec3d> points = new ArrayList<>();
        @Override public VertexConsumer vertex(float x, float y, float z) {
            points.add(new Vec3d(x, y, z));
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
