package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import com.terminaldetector.drmd.physics.collision.CompoundCollider;
import com.terminaldetector.drmd.world.contraption.boarding.BodyGravityField;
import com.terminaldetector.drmd.world.contraption.boarding.BodyMotion;
import com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Observable physics contracts for the additive entry, not a model of the item scheduler. */
final class PassiveBodyContactTest {
    private static final Vec3 ZERO = new Vec3(0, 0, 0);
    private static final DynamicBodyAttachment.Motion FREE = (p, q, d) -> new DynamicBodyAttachment.Move(d, List.of());
    private static final CompoundCollider DECK = new CompoundCollider(List.of(
        new BodyBounds(new Vec3(-5, -.5, -5), new Vec3(5, .5, 5))));
    private static D6PhysicsBody body(double mass, Vec3 velocity) {
        return new D6PhysicsBody().withMass(mass).withInertia(D6Mat3.diagonal(1000, 1000, 1000))
            .withPosition(ZERO).withLinearVelocity(velocity);
    }
    private static BodyMotion deck(D6PhysicsBody body, BodyGravityField field) {
        return new BodyMotion(1, DECK, ZERO, ZERO, Quat.IDENTITY, ZERO, Quat.IDENTITY, body, field);
    }
    private static DynamicBodyAttachment.State state(Vec3 feet, Vec3 velocity) {
        return new DynamicBodyAttachment.State(feet, velocity, false, -1);
    }
    @Test void preintegratedForceAndFrameArePreservedWithoutASecondMotorOrGravity() {
        var q = new Quat(0, Math.sin(.3), 0, Math.cos(.3));
        var start = new DynamicBodyAttachment.State(new Vec3(1, 2, 3), new Vec3(2, -4, 1), false, -1, q, -1);
        var result = DynamicBodyAttachment.passiveContact(start, List.of(), .25, .25, .05, FREE);
        assertEquals(start.velocity(), result.velocity()); assertEquals(q, result.frame());
        assertTrue(start.feet().plus(start.velocity().scaled(.05)).minus(result.feet()).length() < 1e-12);
        assertFalse(result.grounded());
    }
    @Test void passiveContactUsesFrictionWhileExistingSuitContactStillSlidesFreely() {
        var carrier = new Vec3(2, 0, 0);
        var initial = state(new Vec3(0, .50001, 0), new Vec3(0, -.8, 0));
        var passive = DynamicBodyAttachment.passiveContact(initial, List.of(deck(body(0, carrier), BodyGravityField.NONE)), .25, .25, .05, FREE);
        var suit = DynamicBodyAttachment.freeFlight(initial, List.of(deck(body(0, carrier), BodyGravityField.NONE)), .25, .25, .05, FREE);
        assertTrue(passive.velocity().x() > .1 && passive.velocity().x() <= 2);
        assertEquals(0, suit.velocity().x(), 1e-10);
        assertTrue(passive.feet().y() >= .5 - 1e-6 && passive.grounded());
        assertEquals(0, passive.velocity().y(), 1e-8);
    }
    @Test void passiveReactionKeepsEqualAndOppositeMomentumOnTheExistingHull() {
        var hull = body(40, ZERO);
        var initial = state(new Vec3(0, .7, 0), new Vec3(3, -10, 0));
        var after = DynamicBodyAttachment.passiveContact(initial, List.of(deck(hull, BodyGravityField.NONE)), .25, .25, .05, FREE);
        var sum = after.velocity().plus(hull.linearVelocity().scaled(40));
        assertEquals(initial.velocity().x(), sum.x(), 1e-7);
        assertEquals(initial.velocity().y(), sum.y(), 1e-7);
        assertEquals(initial.velocity().z(), sum.z(), 1e-7);
        assertTrue(hull.linearVelocity().y() < 0 && after.velocity().y() > initial.velocity().y());
    }
    @Test void aFiniteBodyFieldDoesNotDoubleAnAlreadyIntegratedItemAcceleration() {
        var field = new BodyGravityField(new Vec3(0, -16, 0),
            new BodyBounds(new Vec3(-8, -8, -8), new Vec3(8, 8, 8)));
        var initial = state(new Vec3(0, 4, 0), new Vec3(0, -.8, 0));
        var after = DynamicBodyAttachment.passiveContact(initial, List.of(deck(body(0, ZERO), field)), .25, .25, .05, FREE);
        assertEquals(initial.velocity(), after.velocity());
        assertEquals(3.96, after.feet().y(), 1e-12);
        assertEquals(-1, after.gravityOwner());
    }
}
