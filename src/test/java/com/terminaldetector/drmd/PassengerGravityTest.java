package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PassengerGravityTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static final Vec3 ZERO=v(0,0,0),UP=v(0,1,0);
    private static final DynamicBodyAttachment.WorldMove FREE=(p,d)->d;
    private static Quat q(Vec3 axis,double angle){return ImmPtlQuaternions.rotationByRadians(axis,angle);}
    private static CompoundCollider floor(){return new CompoundCollider(List.of(new BodyBounds(v(-8,-.5,-8),v(8,.5,8))));}
    private static BodyGravityField field(){return new BodyGravityField(v(0,-32,0),new BodyBounds(v(-12,-4,-12),v(12,8,12)));}
    private static BodyMotion motion(Quat before,Quat after,Vec3 velocity,Vec3 spin) {
        var p=new D6PhysicsBody().withMass(0).withInertia(D6Mat3.diagonal(20,20,20)).withRotation(after).withLinearVelocity(velocity).withAngularVelocity(spin);
        return new BodyMotion(1,floor(),ZERO,ZERO,before,velocity.scaled(.05),after,p,field());
    }
    @Test void artificialGravityAndJumpUseAllSixPhysicalUpAxes() {
        for(var up:List.of(UP,v(0,-1,0),v(1,0,0),v(-1,0,0),v(0,0,1),v(0,0,-1))) {
            var rotation=com.terminaldetector.drmd.client.portal.PortalTransform.rotationBetween(UP,up);
            var b=motion(rotation,rotation,ZERO,ZERO);var feet=up.scaled(.5);
            var start=new DynamicBodyAttachment.State(feet,ZERO,true,1,rotation,1);
            var stand=DynamicBodyAttachment.step(start,DynamicBodyAttachment.Input.idle(),List.of(b),.6,1.8,.05,FREE);
            assertTrue(stand.grounded(),"support for up="+up);assertEquals(1,stand.gravityOwner());
            assertTrue(stand.feet().minus(feet).length()<1e-5);
            var jumping=DynamicBodyAttachment.step(stand,new DynamicBodyAttachment.Input(0,0,true,rotation.rotate(v(0,0,1))),List.of(b),.6,1.8,.05,FREE);
            assertFalse(jumping.grounded());assertEquals(6.8,jumping.velocity().dot(up),1e-8);
            assertEquals(1,jumping.gravityOwner(),"a jump inside the ship field must not revert to world gravity");
        }
    }
    @Test void aCompleteRollAndPitchRetainPhysicalContactsWithoutAPositionAnchor() {
        for(var axis:List.of(v(1,0,0),v(0,0,1))) {
            var state=new DynamicBodyAttachment.State(v(0,.5,0),axis.cross(v(0,.5,0)),true,1,Quat.IDENTITY,1);
            for(int tick=0;tick<140;tick++) {
                Quat before=q(axis,tick*.05),after=q(axis,(tick+1)*.05);
                var b=motion(before,after,ZERO,axis);
                state=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),List.of(b),.6,1.8,.05,FREE);
                Vec3 local=after.inverse().rotate(state.feet());
                assertTrue(state.grounded(),"lost support axis="+axis+" tick="+tick+" state="+state+" local="+local);
                assertEquals(1,state.gravityOwner());assertEquals(.5,local.y(),.025);
                assertTrue(Math.hypot(local.x(),local.z())<.4,"drift after full rotation "+local);
                assertTrue(PassengerFrame.up(state.frame()).dot(after.rotate(UP))>.999);
            }
        }
    }
    @Test void turningTheFrameIsBoundedAndNeverMovesAWorldPositionAnchor() {
        Quat after=PassengerFrame.turn(Quat.IDENTITY,v(0,-1,0),.1);
        assertEquals(.1,Math.acos(PassengerFrame.up(after).dot(UP)),1e-8);
        var state=new DynamicBodyAttachment.State(v(0,3,0),ZERO,false,-1,Quat.IDENTITY,1);
        var b=motion(q(v(1,0,0),Math.PI),q(v(1,0,0),Math.PI),ZERO,ZERO);
        var result=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),List.of(b),.6,1.8,.05,FREE);
        assertEquals(0,result.feet().x(),1e-10);assertEquals(0,result.feet().z(),1e-10);
        assertTrue(result.feet().minus(state.feet()).length()<.05,"frame rotation cannot carry position to a local anchor");
    }
    @Test void bodyFieldBoundsRotateAndNeverCreateAnInfiniteArtificialGravityVolume() {
        var field=field();Quat rotation=q(v(0,0,1),Math.PI/2);
        assertEquals(rotation.rotate(v(0,-32,0)),field.at(rotation.rotate(v(0,2,0)),ZERO,ZERO,rotation).orElseThrow());
        assertTrue(field.at(rotation.rotate(v(0,20,0)),ZERO,ZERO,rotation).isEmpty());
        assertTrue(BodyGravityField.NONE.at(ZERO,ZERO,ZERO,Quat.IDENTITY).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new BodyGravityField(v(0,65,0),field.localVolume()));
    }
    @Test void anUnloadedOrOccupiedHeadSpaceCanBlockPhysicalRotation() {
        var initial=new DynamicBodyAttachment.State(v(0,3,0),ZERO,false,-1,Quat.IDENTITY,1);
        var b=motion(q(v(0,0,1),Math.PI/2),q(v(0,0,1),Math.PI/2),ZERO,ZERO);
        var result=DynamicBodyAttachment.step(initial,DynamicBodyAttachment.Input.idle(),List.of(b),.6,1.8,.05,new DynamicBodyAttachment.Motion() {
            public boolean canOrient(Vec3 feet,Quat frame){return PassengerFrame.up(frame).dot(UP)>.999999;}
            public DynamicBodyAttachment.Move move(Vec3 feet,Quat frame,Vec3 d){return new DynamicBodyAttachment.Move(d,List.of());}
        });
        assertTrue(PassengerFrame.up(result.frame()).dot(UP)>.99999);
    }
    @Test void thinStaticWallStopsAHorizontalCharacterAndKeepsTangentialMotion() {
        var clip=new OrientedCharacterClip(new CompoundCollider(List.of(new BodyBounds(v(-3,-3,-.01),v(3,3,.01)))),.6,1.8);
        Quat horizontal=q(v(0,0,1),Math.PI/2);Vec3 feet=v(0,0,-2);
        var result=clip.move(feet,horizontal,v(.5,0,4));
        assertTrue(result.displacement().z()<1.7);assertEquals(.5,result.displacement().x(),1e-6);
        assertFalse(result.blockingNormals().isEmpty());
    }
    @Test void orientedNarrowphaseDoesNotTurnTheEmptyCornerOfABroadphaseBoxIntoAWall() {
        Quat diagonal=q(v(0,0,1),Math.PI/4);
        var obstacle=new BodyBounds(v(.15,1.3,-.2),v(.35,1.5,.2));
        var actor=PassengerFrame.box(ZERO,.6,1.8,diagonal);
        assertTrue(actor.bounds().intersects(obstacle));assertTrue(actor.contact(OrientedBox.axisAligned(obstacle),0).isEmpty());
        var clip=new OrientedCharacterClip(new CompoundCollider(List.of(obstacle)),.6,1.8);
        assertTrue(clip.canOrient(ZERO,diagonal));
    }
    @Test void deepInitialHeadOverlapCanEscapeWithoutLosingTheWholeCcdTick() {
        var shape=new CompoundCollider(List.of(new BodyBounds(v(-4,1,-4),v(4,2,4))));
        var physics=new D6PhysicsBody().withMass(0).withInertia(D6Mat3.diagonal(1,1,1));
        var ceiling=new BodyMotion(1,shape,ZERO,ZERO,Quat.IDENTITY,ZERO,Quat.IDENTITY,physics);
        var initial=new DynamicBodyAttachment.State(ZERO,v(0,-4,0),false,-1);
        var escaped=DynamicBodyAttachment.step(initial,DynamicBodyAttachment.Input.idle(),List.of(ceiling),.6,1.8,.05,FREE);
        assertTrue(escaped.feet().y()<-.2,"an opening head overlap cannot stall all CCD iterations");
        assertEquals(-5.6,escaped.velocity().y(),1e-8);assertFalse(escaped.grounded());
    }

    @Test void acceleratingPitchMovesTheActualContactPointNotJustTheShipCentre() {
        Vec3 axis=v(1,0,0),translation=v(2,.4,.3),position=ZERO;double angle=0,spin=0;
        double floorTop=-.9516129032258065;
        var interior=new CompoundCollider(List.of(new BodyBounds(v(-4,floorTop-1,-4),v(4,floorTop,4))));
        var state=new DynamicBodyAttachment.State(v(-.25,floorTop,1.5),translation,true,1,q(UP,-.016),1);
        for(int tick=0;tick<150;tick++) {
            double nextSpin=Math.min(1.1,spin+2.4*.05),nextAngle=angle+(spin+nextSpin)*.025;
            var before=q(axis,angle);var after=q(axis,nextAngle);Vec3 nextPosition=position.plus(translation.scaled(.05));
            var physics=new D6PhysicsBody().withMass(0).withInertia(D6Mat3.diagonal(20,20,20)).withPosition(nextPosition)
                .withRotation(after).withLinearVelocity(translation).withAngularVelocity(axis.scaled(nextSpin));
            var body=new BodyMotion(1,interior,ZERO,position,before,nextPosition,after,physics,field());
            state=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),List.of(body),.6,1.8,.05,FREE);
            Vec3 local=after.inverse().rotate(state.feet().minus(nextPosition));
            assertTrue(state.grounded(),"accelerating pitch lost the actual floor tick="+tick+" local="+local+" state="+state);
            assertEquals(floorTop,local.y(),.01);angle=nextAngle;spin=nextSpin;position=nextPosition;
        }
    }
    @Test void shipYawCarriesTheWholeCharacterFrameWithoutCornerOrbitDrift() {
        Vec3 axis=UP,initialFeet=v(-.25,.5,1.5);
        var state=new DynamicBodyAttachment.State(initialFeet,axis.cross(initialFeet),true,1,Quat.IDENTITY,1);
        for(int tick=0;tick<140;tick++) {
            var before=q(axis,tick*.05);var after=q(axis,(tick+1)*.05);
            state=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),List.of(motion(before,after,ZERO,axis)),.6,1.8,.05,FREE);
            assertTrue(state.grounded());
            assertTrue(state.frame().rotate(v(0,0,1)).dot(after.rotate(v(0,0,1)))>.999,"yaw belongs to the character's physical frame");
        }
        Vec3 local=q(axis,7).inverse().rotate(state.feet());
        assertTrue(local.minus(initialFeet).length()<.12,"foot-centre may not orbit a wrongly frozen corner: "+local);
    }

    @Test void aNonTickingShipCannotInventSurfaceMotionFromItsRetainedMomentum() {
        Vec3 retained=v(2,.4,.3);var physics=new D6PhysicsBody().withMass(100).withInertia(D6Mat3.diagonal(20,20,20))
            .withLinearVelocity(retained).withAngularVelocity(v(0,1,0));
        var body=new BodyMotion(1,floor(),ZERO,ZERO,Quat.IDENTITY,ZERO,Quat.IDENTITY,physics,field(),true);
        assertEquals(ZERO,body.surfaceVelocity(v(2,.5,0),1));
        body.react(v(3,0,0),v(0,.5,0));
        assertEquals(retained,physics.linearVelocity(),"paused geometry is a fixed constraint; retain state for its next actual tick");
        var result=DynamicBodyAttachment.step(new DynamicBodyAttachment.State(v(0,.5,0),ZERO,true,1,Quat.IDENTITY,1),
            DynamicBodyAttachment.Input.idle(),List.of(body),.6,1.8,.05,FREE);
        assertTrue(result.grounded());assertTrue(result.feet().minus(v(0,.5,0)).length()<1e-5);
    }

}
