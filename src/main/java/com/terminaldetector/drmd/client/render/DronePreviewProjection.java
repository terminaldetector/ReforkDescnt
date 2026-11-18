package com.terminaldetector.drmd.client.render;

/** Finite orthographic equipment inspection; independent of the live camera and physics. */
public final class DronePreviewProjection {
    private DronePreviewProjection() {}

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite preview point");
        }
    }

    /** The WeaponCore/WeaponClusters inch-to-block convention, without a bank offset. */
    public static Point moduleAnchor(double forward, double right, double up) {
        return new Point(right / 16, up / 16, -forward / 16);
    }

    /** ModelPart.render already divided model units by16; only its Y-down convention changes. */
    public static Point hullPoint(double x, double y, double z) { return new Point(x, -y, z); }

    public static Point orbit(Point point, double yawDegrees, double pitchDegrees) {
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees))
            throw new IllegalArgumentException("Non-finite preview orbit");
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double x = Math.cos(yaw) * point.x + Math.sin(yaw) * point.z;
        double z = -Math.sin(yaw) * point.x + Math.cos(yaw) * point.z;
        return new Point(x, Math.cos(pitch) * point.y - Math.sin(pitch) * z,
                Math.sin(pitch) * point.y + Math.cos(pitch) * z);
    }

    public record Bounds(double minX, double minY, double maxX, double maxY) {
        public Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX)
                    || !Double.isFinite(maxY) || minX > maxX || minY > maxY)
                throw new IllegalArgumentException("Invalid preview bounds");
        }
    }

    public record Fit(double scale, double centreX, double centreY, double width, double height) {
        public double x(Point point) { return width / 2 + (point.x - centreX) * scale; }
        public double y(Point point) { return height / 2 - (point.y - centreY) * scale; }
    }

    /** A common fit preserves actual relative module positions/scales. */
    public static Fit fit(Bounds bounds, double width, double height, double margin) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(margin)
                || width <= 0 || height <= 0 || margin < 0 || margin * 2 >= Math.min(width, height))
            throw new IllegalArgumentException("Invalid preview viewport");
        double dx = Math.max(1e-6, bounds.maxX - bounds.minX);
        double dy = Math.max(1e-6, bounds.maxY - bounds.minY);
        double scale = Math.min((width - margin * 2) / dx, (height - margin * 2) / dy);
        return new Fit(scale, bounds.minX / 2 + bounds.maxX / 2,
                bounds.minY / 2 + bounds.maxY / 2, width, height);
    }
}
