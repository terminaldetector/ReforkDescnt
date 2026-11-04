package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeFlightBodyContactTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static final DynamicBodyAttachment.Motion FREE=(p,q,d)->new DynamicBodyAttachment.Move(d,List.of());
    private static DynamicBodyAttachment.State state(Vec3 p,Vec3 v){return new DynamicBodyAttachment.State(p,v,false,-1);}
    private static CompoundCollider wall(){return new CompoundCollider(List.of(new BodyBounds(v(-8,-8,-.01),v(8,8,.01))));}
    private static BodyMotion motion(CompoundCollider shape,D6PhysicsBody body,Vec3 from,Quat before) {
        return new BodyMotion(1,shape,ZERO,from,before,body.position(),body.rotation(),body);
    }
    private static D6PhysicsBody body(double mass,Vec3 p,Vec3 velocity){return new D6PhysicsBody().withMass(mass).withInertia(D6Mat3.diagonal(100,100,100)).withPosition(p).withLinearVelocity(velocity);}
    private static DynamicBodyAttachment.State fly(DynamicBodyAttachment.State s,List<BodyMotion> bodies){return DynamicBodyAttachment.freeFlight(s,bodies,.6,1.8,.05,FREE);}

    @Test void preintegratedFlightVelocityGetsNoWalkingGravityOrFrameAlignment() {
        var q=new Quat(Math.sin(.4),0,0,Math.cos(.4));
        var s=new DynamicBodyAttachment.State(v(1,4,3),v(3,2,1),true,99,q,99);
        var result=fly(s,List.of());
        assertEquals(s.feet().plus(s.velocity().scaled(.05)).x(),result.feet().x(),1e-12);
        assertEquals(s.feet().plus(s.velocity().scaled(.05)).y(),result.feet().y(),1e-12);
        assertEquals(s.velocity(),result.velocity());assertEquals(q,result.frame());
        assertFalse(result.grounded());assertEquals(-1,result.support());assertEquals(-1,result.gravityOwner());
    }
    @Test void suitDoesNotSilentlyEnterTheWalkingFieldWhileFlightOwnsMovement() {
        var b=body(0,ZERO,ZERO);
        var field=new BodyGravityField(v(0,-32,0),new BodyBounds(v(-4,-4,-4),v(4,4,4)));
        var m=new BodyMotion(1,new CompoundCollider(List.of()),ZERO,ZERO,Quat.IDENTITY,ZERO,Quat.IDENTITY,b,field);
        var result=fly(state(ZERO,v(1,2,3)),List.of(m));
        assertEquals(v(1,2,3),result.velocity());assertEquals(-1,result.gravityOwner());
    }
    @Test void maxSpeedFlightCannotTunnelThroughAThinHullAndPreservesTangentialSpeed() {
        var b=body(0,ZERO,ZERO);var result=fly(state(v(0,0,-10),v(3,2,512)),List.of(motion(wall(),b,ZERO,Quat.IDENTITY)));
        assertTrue(result.feet().z()<=-.31+1e-6);assertTrue(result.feet().z()>-.32);
        assertEquals(0,result.velocity().z(),1e-8);assertEquals(3,result.velocity().x(),1e-9);assertEquals(2,result.velocity().y(),1e-9);
    }
    @Test void fastMovingHullCannotCrossAnIdlePilot() {
        var b=body(0,v(0,0,2),v(0,0,80));
        var result=fly(state(ZERO,ZERO),List.of(motion(wall(),b,v(0,0,-2),Quat.IDENTITY)));
        assertTrue(result.feet().z()>=2.31-1e-5,"contact must advance with the actual moving surface");
        assertEquals(80,result.velocity().z(),1e-8);
    }
    @Test void separatingPilotIsNotWeldedToTheHull() {
        var b=body(0,ZERO,v(0,0,1));var start=state(v(0,0,-.31),v(0,0,-4));
        var result=fly(start,List.of(motion(wall(),b,ZERO,Quat.IDENTITY)));
        assertEquals(-4,result.velocity().z(),1e-10);assertEquals(-.51,result.feet().z(),1e-10);
    }
    @Test void rotatedCompoundDoorRemainsOpenAndTheAdjacentWallStopsFlight() {
        var q=new Quat(0,Math.sin(.3),0,Math.cos(.3));var b=body(0,ZERO,ZERO).withRotation(q);
        var door=new CompoundCollider(List.of(new BodyBounds(v(-3,-4,-.1),v(-.8,4,.1)),new BodyBounds(v(.8,-4,-.1),v(3,4,.1))));
        var start=state(q.rotate(v(0,0,-2)),q.rotate(v(0,0,80)));
        var through=fly(start,List.of(motion(door,b,ZERO,q)));
        assertTrue(q.inverse().rotate(through.feet()).z()>1.99);
        var stopped=fly(start,List.of(motion(wall(),b,ZERO,q)));
        assertTrue(q.inverse().rotate(stopped.feet()).z()<-.3);assertEquals(0,stopped.velocity().length(),1e-8);
    }
    @Test void FlightContactTransfersEqualAndOppositeMomentumToTheRealHull() {
        var b=body(20,ZERO,ZERO);var result=fly(state(v(0,-.9,-1),v(0,0,40)),List.of(motion(wall(),b,ZERO,Quat.IDENTITY)));
        assertTrue(b.linearVelocity().z()>0);assertTrue(result.velocity().z()<40);
        assertEquals(40,result.velocity().z()+20*b.linearVelocity().z(),1e-8);
        assertEquals(0,result.velocity().x()+20*b.linearVelocity().x(),1e-8);
    }
    @Test void ordinaryWorldCollisionRemovesOnlyItsClosingObliqueVelocity() {
        var n=v(-1,0,-1).normalized();
        DynamicBodyAttachment.Motion world=(p,q,d)->new DynamicBodyAttachment.Move(ZERO,List.of(n));
        var result=DynamicBodyAttachment.freeFlight(state(ZERO,v(4,3,2)),List.of(),.6,1.8,.05,world);
        assertEquals(0,result.velocity().dot(n),1e-9);assertEquals(3,result.velocity().y(),1e-9);
        assertEquals(1,result.velocity().x(),1e-9);assertEquals(-1,result.velocity().z(),1e-9);
    }
}
