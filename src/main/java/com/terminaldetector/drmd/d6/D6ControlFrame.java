package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.*;

/** Immutable controller forces for one existing body's material/authority phase.
 * This carries no ownership decision: the server adapter must supply a freshly
 * validated active-core key before every accumulation. It never applies impulses,
 * advances time, edits mass/pose, or retains forces in a paused body. */
public final class D6ControlFrame {
    public record Key(UUID body, long worldTick, long shapeRevision, long coreCell,
                      long coreGeneration, int authorityEpoch) {
        public Key {
            Objects.requireNonNull(body, "body identity");
            if (shapeRevision < 0 || coreGeneration < 0)
                throw new IllegalArgumentException("nonnegative material revisions required");
        }
    }
    private final Key key;
    private final Vec3 localCentre;
    private final List<D6ThrusterMixer.Output> forces;

    public D6ControlFrame(Key key, Vec3 localCentre, List<D6ThrusterMixer.Output> forces) {
        this.key = Objects.requireNonNull(key);
        finite(localCentre); this.localCentre = localCentre;
        Objects.requireNonNull(forces);
        if (forces.size() > 16) throw new IllegalArgumentException("at most sixteen physical motors");
        var seen = new HashSet<Integer>();
        for (var out : forces) {
            Objects.requireNonNull(out);
            if (out.mount() < 0 || out.mount() >= 16 || !seen.add(out.mount()))
                throw new IllegalArgumentException("unique bounded physical motor identities required");
            finite(out.localPoint()); finite(out.force());
        }
        this.forces = List.copyOf(forces);
    }

    /** Call only AFTER the caller snapshots the current substep, before its force
     * integration. Reapply for each accepted/retried dt; D6 clears accumulators at
     * integration, and restoring the PRE-accumulation snapshot cancels speculative
     * thrust. Never accumulate before taking an UNKNOWN/joint rollback snapshot. */
    public boolean accumulate(D6PhysicsBody body, Key currentActiveCore) {
        Objects.requireNonNull(body);
        if (!key.equals(currentActiveCore)) return false;
        var rotation = body.rotation();
        for (var out : forces)
            body.applyForceAtPoint(rotation.rotate(out.force()),
                    rotation.rotate(out.localPoint().minus(localCentre)));
        return true;
    }

    private static void finite(Vec3 v) {
        if (v == null || !Double.isFinite(v.lengthSquared()))
            throw new IllegalArgumentException("finite material point and force required");
    }
}
