package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;

/** Client display interpolation on an absolute tick timeline, independent of partial-tick resets. */
public final class BodyRotationTimeline {
    private Quat from = Quat.IDENTITY, target = Quat.IDENTITY;
    private boolean initialized;
    private double start, lastTime;
    private static final double DURATION = 2;

    public void reset(Quat pose, double time) {
        from = target = checked(pose); checkTime(time);
        start = lastTime = time; initialized = true;
    }

    public void receive(Quat pose, double time) {
        pose = checked(pose); checkTime(time);
        if (!initialized) { reset(pose, time); return; }
        // Velocity-only POSE packets must not restart an unfinished turn to the same rotation.
        if (Math.abs(ImmPtlQuaternions.dot(target, pose)) > 1 - 1e-12) return;
        from = sample(time); target = pose; start = lastTime;
    }

    public Quat sample(double time) {
        checkTime(time);
        if (!initialized) return Quat.IDENTITY;
        // Multiple cameras and packet application can sample an older partial tick. Never rewind.
        lastTime = Math.max(lastTime, time);
        double t = Math.max(0, Math.min(1, (lastTime - start) / DURATION));
        return t >= 1 ? target : ImmPtlQuaternions.interpolate(from, target, t).normalized();
    }

    private static void checkTime(double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("invalid render time");
    }
    private static Quat checked(Quat q) {
        if (q == null) throw new IllegalArgumentException("missing render pose");
        double length = q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();
        if (!Double.isFinite(length) || length < 1e-20) throw new IllegalArgumentException("invalid render pose");
        return q.normalized();
    }
}
