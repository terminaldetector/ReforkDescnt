package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;

/**
 * Unilateral, inelastic contact between two existing D6 bodies. Units are seconds and world axes.
 * A constraint belongs to one solver step; accumulated impulses must not survive a contact change.
 * This does not integrate either body, move Minecraft entities or impose a welded attachment.
 */
public final class BodyContactConstraint {
    private static final Vec3 ZERO = new Vec3(0, 0, 0);
    private final D6PhysicsBody a, b;
    private final Vec3 ra, rb, normal;
    private final Vec3 tangentTarget;
    private final double friction, normalMass;
    private double normalImpulse;
    private Vec3 tangentImpulse = ZERO;

    /** Normal points from b towards a; point is the common world-space contact point. */
    public BodyContactConstraint(D6PhysicsBody a, D6PhysicsBody b, Vec3 point, Vec3 normal, double friction) {
        this(a,b,point,normal,friction,ZERO);
    }
    /** Optional powered tangential contact (walking feet); still bounded by the normal reaction. */
    public BodyContactConstraint(D6PhysicsBody a, D6PhysicsBody b, Vec3 point, Vec3 normal, double friction, Vec3 tangentTarget) {
        if (a == null || b == null || a == b) throw new IllegalArgumentException("distinct contact bodies required");
        BodyBounds.finite(point); BodyBounds.finite(normal);
        if (!Double.isFinite(normal.length()) || normal.length() < 1e-10
            || !Double.isFinite(friction) || friction < 0) throw new IllegalArgumentException("invalid contact");
        this.a = a; this.b = b;
        this.ra = point.minus(a.position()); this.rb = point.minus(b.position());
        this.normal = normal.scaled(1 / normal.length()); this.friction = friction;
        BodyBounds.finite(tangentTarget);
        this.tangentTarget=tangentTarget.minus(this.normal.scaled(tangentTarget.dot(this.normal)));
        normalMass = denominator(this.normal);
    }

    public Vec3 relativeVelocity() { return a.velocityAtPoint(ra).minus(b.velocityAtPoint(rb)); }
    public double normalImpulse() { return normalImpulse; }
    public Vec3 tangentImpulse() { return tangentImpulse; }

    /** One sequential-impulse iteration. Call repeatedly when contacts share bodies. */
    public void solve() {
        if (normalMass <= 1e-12) return;
        double next = Math.max(0, normalImpulse - relativeVelocity().dot(normal) / normalMass);
        apply(normal.scaled(next - normalImpulse)); normalImpulse = next;

        Vec3 relative = relativeVelocity();
        Vec3 tangent = relative.minus(normal.scaled(relative.dot(normal))).minus(tangentTarget);
        double speed = tangent.length();
        Vec3 candidate = tangentImpulse;
        if (speed > 1e-10) {
            Vec3 direction = tangent.scaled(1 / speed);
            double effective = denominator(direction);
            if (effective > 1e-12) candidate = candidate.minus(direction.scaled(speed / effective));
        }
        double limit = friction * normalImpulse, length = candidate.length();
        if (length > limit) candidate = candidate.scaled(limit / length);
        apply(candidate.minus(tangentImpulse)); tangentImpulse = candidate;
    }

    private double denominator(Vec3 direction) {
        return a.pointImpulseDenominator(ra, direction) + b.pointImpulseDenominator(rb, direction);
    }
    private void apply(Vec3 impulse) {
        // In D6 mass zero denotes an immovable body. Never accumulate spurious angular momentum
        // on it: applyImpulse intentionally assumes its caller already chose a dynamic target.
        if (a.mass() > 1e-9) a.applyImpulse(impulse, ra);
        if (b.mass() > 1e-9) b.applyImpulse(impulse.scaled(-1), rb);
    }
}
