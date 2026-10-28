package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import java.util.*;

/** World-space narrowphase over an immutable local compound. The BVH is never rebuilt for motion. */
public final class BodyCollisionQuery {
    public record Contact(BodyBounds localShape, Vec3 normal, double penetration, Vec3 point) {}
    public record Sweep(BodyBounds localShape, double fraction, Vec3 normal) {}
    private final CompoundCollider collider;
    private final Vec3 position, localCom;
    private final Quat rotation;

    public BodyCollisionQuery(CompoundCollider collider, Vec3 position, Vec3 localCom, Quat rotation) {
        Objects.requireNonNull(collider); BodyBounds.finite(position); BodyBounds.finite(localCom);
        // Validate quaternion exactly as the OBB does, including zero and overflow cases.
        new OrientedBox(position, new Vec3(1, 1, 1), rotation);
        this.collider = collider; this.position = position; this.localCom = localCom;
        this.rotation = rotation.normalized();
    }

    /** Normal points from the hull into the queried box. Skin admits separated resting contacts. */
    public List<Contact> contacts(OrientedBox actor, double skin) {
        if (!Double.isFinite(skin) || skin < 0) throw new IllegalArgumentException("invalid skin");
        var hits = new ArrayList<Contact>();
        for (BodyBounds shape : collider.query(toLocal(expand(actor.bounds(), skin)))) {
            var hull = OrientedBox.local(shape, position, localCom, rotation);
            actor.contact(hull, skin).ifPresent(c -> {
                // A single representative point, not a clipped face manifold. Project the actor's
                // supporting face into the actual hull primitive; broadphase corners are not hits.
                Vec3 supporting = actor.supportCentre(c.normal().scaled(-1));
                Vec3 onHull = hull.closestPoint(supporting.plus(c.normal().scaled(c.penetration())));
                Vec3 onActor = actor.closestPoint(onHull.minus(c.normal().scaled(c.penetration())));
                hits.add(new Contact(shape, c.normal(), c.penetration(), onHull.plus(onActor).scaled(.5)));
            });
        }
        return List.copyOf(hits);
    }

    /** Exact translational CCD for fixed orientations, accounting for both sides' displacement. */
    public Optional<Sweep> sweep(OrientedBox actor, Vec3 movement, Vec3 bodyMovement) {
        return sweep(actor,movement,bodyMovement,false);
    }
    /** A character may escape an existing overlap; closing motion still resolves every primitive. */
    public Optional<Sweep> sweep(OrientedBox actor,Vec3 movement,Vec3 bodyMovement,boolean openingOverlaps) {
        return sweep(actor,movement,bodyMovement,openingOverlaps,hit->true);
    }
    public Optional<Sweep> sweep(OrientedBox actor,Vec3 movement,Vec3 bodyMovement,boolean openingOverlaps,java.util.function.Predicate<Sweep> accepted) {
        BodyBounds.finite(movement); BodyBounds.finite(bodyMovement);
        Vec3 relative = movement.minus(bodyMovement);
        BodyBounds swept = actor.bounds().union(translate(actor.bounds(), relative));
        Sweep best = null;
        for (BodyBounds shape : collider.query(toLocal(swept))) {
            var hull=OrientedBox.local(shape,position,localCom,rotation);
            var overlap=openingOverlaps?actor.contact(hull,0):Optional.<OrientedBox.Contact>empty();
            if(overlap.isPresent()&&overlap.get().penetration()>1e-9&&relative.dot(overlap.get().normal())>=-1e-12)continue;
            var hit = actor.sweep(hull, movement, bodyMovement);
            if (hit.isPresent() && (best == null || hit.get().fraction() < best.fraction())) {
                var h = hit.get();var candidate=new Sweep(shape,h.fraction(),h.normal());
                if(accepted.test(candidate))best=candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private BodyBounds toLocal(BodyBounds bounds) {
        return OrientedBox.local(bounds, localCom, position, rotation.inverse()).bounds();
    }
    private static BodyBounds expand(BodyBounds b, double skin) {
        Vec3 s = new Vec3(skin, skin, skin);
        return new BodyBounds(b.min().minus(s), b.max().plus(s));
    }
    private static BodyBounds translate(BodyBounds b, Vec3 movement) {
        return new BodyBounds(b.min().plus(movement), b.max().plus(movement));
    }
}
