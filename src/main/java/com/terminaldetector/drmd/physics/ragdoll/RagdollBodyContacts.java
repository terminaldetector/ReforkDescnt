package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.BodyCollisionQuery;
import com.terminaldetector.drmd.physics.collision.BodyContactConstraint;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.world.contraption.boarding.BodyMotion;

import java.util.List;
import java.util.function.Predicate;

/** Projects an articulated part against a moving assembly's existing compound and contact solver. */
public final class RagdollBodyContacts {
    private static final double SKIN = .001;
    private static final double CLEARANCE = .0001;

    private RagdollBodyContacts() {}

    public static void resolve(RagdollSimulation.Part part, Vec3 before, double start, double end,
                               List<BodyMotion> hulls, Predicate<Vec3> clearPosition) {
        var limb = part.body();
        var half = part.segment().halfExtents();
        for (BodyMotion hull : hulls) {
            Vec3 move = limb.position().minus(before);
            Vec3 hullMove = hull.position(end).minus(hull.position(start));
            Vec3 relative = move.minus(hullMove);
            if (relative.lengthSquared() > 1e-12) {
                var startBox = new OrientedBox(before, half, limb.rotation());
                var sweep = hull.query(start).sweep(startBox, move, hullMove, true).orElse(null);
                if (sweep != null) {
                    double closing = relative.dot(sweep.normal());
                    if (closing < 0) {
                        Vec3 candidate = limb.position().plus(sweep.normal()
                                .scaled(-closing * (1 - sweep.fraction()) + CLEARANCE));
                        if (clearPosition.test(candidate)) limb.withPosition(candidate);
                    }
                }
            }

            var box = new OrientedBox(limb.position(), half, limb.rotation());
            BodyCollisionQuery.Contact contact = null;
            for (var hit : hull.query(end).contacts(box, SKIN)) {
                if (contact == null || hit.penetration() > contact.penetration()) contact = hit;
            }
            if (contact == null) continue;

            if (contact.penetration() > 0) {
                Vec3 candidate = limb.position().plus(contact.normal().scaled(contact.penetration() + CLEARANCE));
                if (clearPosition.test(candidate)) limb.withPosition(candidate);
            }
            var proxy = hull.proxy(end);
            var constraint = new BodyContactConstraint(limb, proxy, contact.point(), contact.normal(), .72);
            constraint.solve();
            Vec3 reaction = constraint.tangentImpulse().plus(contact.normal().scaled(constraint.normalImpulse()));
            if (reaction.lengthSquared() > 1e-16)
                hull.react(reaction.scaled(-1), contact.point().minus(proxy.position()));
        }
    }
}
