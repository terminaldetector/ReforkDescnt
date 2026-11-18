package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.render.DronePreviewProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DronePreviewProjectionTest {
    @Test void physicalMuzzleAxesAndHullModelUnitsShareOneFrame() {
        var anchor = DronePreviewProjection.moduleAnchor(32, -16, 8);
        assertEquals(-1, anchor.x()); assertEquals(.5, anchor.y()); assertEquals(-2, anchor.z());
        // ModelPart has already converted pixels to blocks; applying /16 again would be wrong.
        var hull = DronePreviewProjection.hullPoint(-1, -.5, -2);
        assertEquals(anchor, hull);
    }

    @Test void orbitPreservesDistancesAcrossFullYawPitchAndInvertedViews() {
        var p = new DronePreviewProjection.Point(-1.2, .7, -2.3);
        double distance = p.x() * p.x() + p.y() * p.y() + p.z() * p.z();
        for (int yaw = -360; yaw <= 360; yaw += 30) for (int pitch = -360; pitch <= 360; pitch += 30) {
            var q = DronePreviewProjection.orbit(p, yaw, pitch);
            assertEquals(distance, q.x() * q.x() + q.y() * q.y() + q.z() * q.z(), 1e-12);
        }
        assertEquals(p.x(), DronePreviewProjection.orbit(p, 360, 360).x(), 1e-12);
    }

    @Test void oneFitContainsAsymmetricNegativeLayoutAndKeepsRelativeScale() {
        var bounds = new DronePreviewProjection.Bounds(-8, -1.5, 2, 4);
        for (int width : new int[]{32, 168, 400}) for (int height : new int[]{20, 49, 180}) {
            var fit = DronePreviewProjection.fit(bounds, width, height, 4);
            for (double x : new double[]{bounds.minX(), bounds.maxX()})
                for (double y : new double[]{bounds.minY(), bounds.maxY()}) {
                    var point = new DronePreviewProjection.Point(x, y, 0);
                    assertTrue(fit.x(point) >= 4 - 1e-12 && fit.x(point) <= width - 4 + 1e-12);
                    assertTrue(fit.y(point) >= 4 - 1e-12 && fit.y(point) <= height - 4 + 1e-12);
                }
            var a = new DronePreviewProjection.Point(-4, 0, 0);
            var b = new DronePreviewProjection.Point(-2, 2, 0);
            assertEquals(fit.x(b) - fit.x(a), fit.y(a) - fit.y(b), 1e-12);
        }
    }

    @Test void pointAndFlatGeometryStayCentredAndFinite() {
        for (var bounds : new DronePreviewProjection.Bounds[]{
                new DronePreviewProjection.Bounds(2, -3, 2, -3),
                new DronePreviewProjection.Bounds(-1, 0, 4, 0)}) {
            var fit = DronePreviewProjection.fit(bounds, 168, 49, 4);
            assertTrue(Double.isFinite(fit.scale()) && fit.scale() > 0);
            var centre = new DronePreviewProjection.Point((bounds.minX() + bounds.maxX()) / 2,
                    (bounds.minY() + bounds.maxY()) / 2, 0);
            assertEquals(84, fit.x(centre)); assertEquals(24.5, fit.y(centre));
        }
    }

    @Test void malformedGeometryAndOrbitsAreRejectedRatherThanPollutingGuiVertices() {
        assertThrows(IllegalArgumentException.class, () -> new DronePreviewProjection.Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DronePreviewProjection.moduleAnchor(Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DronePreviewProjection.Bounds(2, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> DronePreviewProjection.orbit(
                new DronePreviewProjection.Point(1, 0, 0), Double.NaN, 0));
    }

    @Test void unusableViewportIsRejectedBeforeBufferWork() {
        var bounds = new DronePreviewProjection.Bounds(-1, -1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> DronePreviewProjection.fit(bounds, 0, 49, 4));
        assertThrows(IllegalArgumentException.class, () -> DronePreviewProjection.fit(bounds, 168, 8, 4));
        assertThrows(IllegalArgumentException.class, () -> DronePreviewProjection.fit(bounds, 168, 49, -1));
        assertThrows(IllegalArgumentException.class, () -> DronePreviewProjection.fit(bounds, Double.NaN, 49, 4));
    }
}
