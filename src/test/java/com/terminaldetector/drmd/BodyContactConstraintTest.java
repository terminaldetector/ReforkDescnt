package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyContactConstraintTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static D6PhysicsBody body(double mass,Vec3 position,Vec3 velocity) {
        return new D6PhysicsBody().withMass(mass).withInertia(D6Mat3.diagonal(2,3,4))
            .withPosition(position).withLinearVelocity(velocity);
    }
    private static void same(Vec3 a,Vec3 b){assertTrue(a.minus(b).length()<1e-9,a+" != "+b);}
    private static void solve(BodyContactConstraint c){for(int i=0;i<24;i++)c.solve();}
    private static Vec3 momentum(D6PhysicsBody a,D6PhysicsBody b) {
        return a.linearVelocity().scaled(a.mass()).plus(b.linearVelocity().scaled(b.mass()));
    }
    private static Vec3 angular(D6PhysicsBody b) {
        return b.angularMomentum().plus(b.position().cross(b.linearVelocity().scaled(b.mass())));
    }
    private static double energy(D6PhysicsBody b) {
        return .5*(b.mass()*b.linearVelocity().lengthSquared()+b.angularVelocity().dot(b.angularMomentum()));
    }
    @Test void twoMovingBodiesExchangeEqualOppositeImpulseAtTheirActualMasses() {
        var a=body(2,v(-1,0,0),v(4,0,0));var b=body(6,v(1,0,0),v(-2,0,0));
        Vec3 before=momentum(a,b);double energy=energy(a)+energy(b);
        var c=new BodyContactConstraint(a,b,v(0,0,0),v(-1,0,0),0);solve(c);
        same(before,momentum(a,b));same(v(-.5,0,0),a.linearVelocity());same(a.linearVelocity(),b.linearVelocity());
        assertTrue(energy(a)+energy(b)<energy);assertEquals(9,c.normalImpulse(),1e-9);
    }
    @Test void offCentreContactConservesTotalWorldAngularAndLinearMomentum() {
        var a=body(3,v(-1,0,0),v(5,2,0));var b=body(8,v(1,0,0),v(-1,0,1));
        a.withRotation(new Quat(0,0,Math.sin(.3),Math.cos(.3))).withAngularVelocity(v(.2,.4,-.1));
        Vec3 p=momentum(a,b),l=angular(a).plus(angular(b));double before=energy(a)+energy(b);
        var c=new BodyContactConstraint(a,b,v(0,.7,.3),v(-1,0,0),.6);solve(c);
        same(p,momentum(a,b));same(l,angular(a).plus(angular(b)));
        assertTrue(energy(a)+energy(b)<=before+1e-9);assertTrue(a.angularMomentum().length()>0);
        assertTrue(c.relativeVelocity().dot(v(-1,0,0))>=-1e-8);
    }
    @Test void movingAndRotatingSurfaceIsMeasuredAtTheContactPoint() {
        var a=body(1,v(2,1,0),v(0,-3,0));var b=body(0,v(0,0,0),v(4,0,0));
        // An immovable support may still have prescribed motion: it receives no reaction impulse.
        b.withAngularVelocity(v(0,0,2));var point=v(2,0,0);
        var c=new BodyContactConstraint(a,b,point,v(0,1,0),0);
        same(v(-4,-7,0),c.relativeVelocity());Vec3 before=b.angularMomentum();solve(c);
        assertEquals(4,a.velocityAtPoint(point.minus(a.position())).y(),1e-9);
        same(v(4,0,0),b.linearVelocity());same(before,b.angularMomentum());
    }
    @Test void frictionCannotExceedTheNormalReactionOrPullAnOpeningContactBack() {
        var a=body(1,v(0,0,0),v(100,-2,0));var b=body(0,v(0,0,0),v(0,0,0));
        var c=new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),.25);solve(c);
        assertEquals(.5,c.tangentImpulse().length(),1e-9);same(v(99.5,0,0),a.linearVelocity());
        a.withLinearVelocity(v(10,3,4));var jump=new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),100);solve(jump);
        same(v(10,3,4),a.linearVelocity());assertEquals(0,jump.normalImpulse());
    }
    @Test void zeroGravityContactHasNoUnphysicalTangentialWeld() {
        var a=body(1,v(0,0,0),v(0,0,0));var b=body(10,v(0,0,0),v(7,0,0));
        var c=new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),.9);solve(c);
        same(v(0,0,0),a.linearVelocity());same(v(7,0,0),b.linearVelocity());
    }
    @Test void gravityReactionGraduallyAcceleratesPassengerByBoundedFriction() {
        var a=body(1,v(0,0,0),v(0,0,0));var b=body(0,v(0,0,0),v(3,0,0));
        for(int tick=0;tick<40;tick++) {
            a.withLinearVelocity(a.linearVelocity().plus(v(0,-9.81*.05,0)));
            var c=new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),.6);solve(c);
            assertTrue(c.tangentImpulse().length()<=.6*c.normalImpulse()+1e-12);
            if(tick==0)assertTrue(a.linearVelocity().x()<.3,"contact force cannot snap velocity to the ship");
        }
        same(v(3,0,0),a.linearVelocity());
        // Departure starts from the existing world velocity: do not add the surface speed twice.
        a.withLinearVelocity(a.linearVelocity().plus(v(0,4.2,0)));
        solve(new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),.6));same(v(3,4.2,0),a.linearVelocity());
    }
    @Test void rotatedContactAndCommonVelocityProduceTheSameReaction() {
        var q=new Quat(Math.sin(.4),0,0,Math.cos(.4));var shift=v(123,-45,67);
        var a=body(2,v(0,0,0),v(1,-3,2));var b=body(3,v(0,0,0),v(-1,1,0));
        var aa=body(2,v(0,0,0),q.rotate(a.linearVelocity()).plus(shift));
        var bb=body(3,v(0,0,0),q.rotate(b.linearVelocity()).plus(shift));
        var c=new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),.7);
        var cc=new BodyContactConstraint(aa,bb,v(0,0,0),q.rotate(v(0,1,0)),.7);solve(c);solve(cc);
        same(q.rotate(a.linearVelocity()).plus(shift),aa.linearVelocity());
        same(q.rotate(b.linearVelocity()).plus(shift),bb.linearVelocity());
    }
    @Test void immovablePairAndInvalidContactsCannotInjectMomentum() {
        var a=body(0,v(0,0,0),v(0,-1,0));var b=body(0,v(0,0,0),v(0,0,0));
        solve(new BodyContactConstraint(a,b,v(10,4,7),v(0,1,0),1));
        same(v(0,0,0),a.angularMomentum());same(v(0,0,0),b.angularMomentum());
        assertThrows(IllegalArgumentException.class,()->new BodyContactConstraint(a,a,v(0,0,0),v(0,1,0),1));
        assertThrows(IllegalArgumentException.class,()->new BodyContactConstraint(a,b,v(0,0,0),v(0,0,0),1));
        assertThrows(IllegalArgumentException.class,()->new BodyContactConstraint(a,b,v(0,0,0),v(0,1,0),Double.NaN));
    }
    @Test void asymmetricRotatingContactsNeverCreateEnergyOrLosePairMomentum() {
        var random=new java.util.Random(261028);
        for(int trial=0;trial<200;trial++) {
            var a=body(1+random.nextDouble()*9,v(-1,.2,0),v(random.nextDouble()*8,random.nextDouble()*8,random.nextDouble()*8));
            var b=body(1+random.nextDouble()*9,v(1,-.2,0),v(random.nextDouble()*8,random.nextDouble()*8,random.nextDouble()*8));
            a.withRotation(new Quat(random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()))
                .withAngularVelocity(v(random.nextDouble(),random.nextDouble(),random.nextDouble()));
            b.withRotation(new Quat(random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()))
                .withAngularVelocity(v(random.nextDouble(),random.nextDouble(),random.nextDouble()));
            Vec3 p=momentum(a,b),l=angular(a).plus(angular(b));double before=energy(a)+energy(b);
            var c=new BodyContactConstraint(a,b,v(0,random.nextDouble(),random.nextDouble()),
                v(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5),random.nextDouble());
            solve(c);same(p,momentum(a,b));same(l,angular(a).plus(angular(b)));
            assertTrue(energy(a)+energy(b)<=before+1e-8,"passive contact injected energy trial="+trial);
            assertTrue(c.normalImpulse()>=0);assertTrue(Double.isFinite(c.relativeVelocity().length()));
        }
    }
}
