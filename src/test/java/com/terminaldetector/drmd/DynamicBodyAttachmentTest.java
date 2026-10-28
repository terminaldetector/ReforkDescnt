package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DynamicBodyAttachmentTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static final DynamicBodyAttachment.WorldMove FREE=(p,d)->d;
    private static CompoundCollider floor(){return new CompoundCollider(List.of(new BodyBounds(v(-8,-.5,-8),v(8,.5,8))));}
    private static D6PhysicsBody rigid(double mass,Vec3 pos,Vec3 velocity) {
        return new D6PhysicsBody().withMass(mass).withInertia(D6Mat3.diagonal(20,30,20)).withPosition(pos).withLinearVelocity(velocity);
    }
    private static BodyMotion motion(D6PhysicsBody b,Vec3 start,Vec3 end) {
        return new BodyMotion(1,floor(),v(0,0,0),start,Quat.IDENTITY,end,Quat.IDENTITY,b);
    }
    private static DynamicBodyAttachment.State standing(Vec3 velocity){return new DynamicBodyAttachment.State(v(0,.5,0),velocity,true,1);}
    @Test void frictionPicksUpMovingDeckSpeedWithoutSnappingToAnAnchor() {
        var b=rigid(0,v(0,0,0),v(3,0,0));var state=standing(v(0,0,0));Vec3 pos=v(0,0,0);
        for(int i=0;i<30;i++) {
            Vec3 next=pos.plus(v(.15,0,0));
            state=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),List.of(motion(b,pos,next)),.6,1.8,.05,FREE);
            assertTrue(state.grounded());assertEquals(.5,state.feet().y(),1e-5);
            if(i==0){assertTrue(state.velocity().x()<1.1);assertTrue(state.feet().x()<.1,"contact must not copy the deck translation");}
            pos=next;
        }
        assertEquals(3,state.velocity().x(),1e-8);assertTrue(state.feet().x()>4);
    }
    @Test void poweredFootContactWalksRelativeToTheDeckAndReactsOnItsMass() {
        var b=rigid(20,v(0,0,0),v(0,0,0));
        var result=DynamicBodyAttachment.step(standing(v(0,0,0)),new DynamicBodyAttachment.Input(1,0,false,v(0,0,1)),
            List.of(motion(b,v(0,0,0),v(0,0,0))),.6,1.8,.05,FREE);
        assertTrue(result.feet().x()>.02,"walking cannot be cancelled by static friction");
        assertTrue(b.linearVelocity().x()<0,"the deck receives the foot's opposite impulse");
        assertEquals(0,result.velocity().x()+20*b.linearVelocity().x(),1e-8);
        assertEquals(-1.6,result.velocity().y()+20*b.linearVelocity().y(),1e-8,"only the actor's external gravity changes pair momentum");
    }
    @Test void jumpingRetainsCarrierWorldVelocityExactlyOnce() {
        var b=rigid(0,v(0,0,0),v(3,4,0));
        var result=DynamicBodyAttachment.step(standing(v(3,4,0)),new DynamicBodyAttachment.Input(0,0,true,v(0,0,1)),
            List.of(motion(b,v(0,0,0),v(.15,.2,0))),.6,1.8,.05,FREE);
        assertFalse(result.grounded());assertEquals(3,result.velocity().x(),1e-9);
        assertEquals(4+.42*20-32*.05,result.velocity().y(),1e-8);
        assertTrue(result.feet().y()>.7);
    }
    @Test void jumpFromTurningDeckPreservesAngularPointVelocity() {
        var b=rigid(0,v(0,0,0),v(3,0,0)).withAngularVelocity(v(0,1,0));
        var q=new Quat(0,Math.sin(.025),0,Math.cos(.025));
        var m=new BodyMotion(1,floor(),v(0,0,0),v(0,0,0),Quat.IDENTITY,v(.15,0,0),q,b);
        Vec3 inherited=m.surfaceVelocity(v(1,.5,0),0);
        var initial=new DynamicBodyAttachment.State(v(1,.5,0),inherited,true,1);
        var result=DynamicBodyAttachment.step(initial,new DynamicBodyAttachment.Input(0,0,true,v(0,0,1)),List.of(m),.6,1.8,.05,FREE);
        assertEquals(3,result.velocity().x(),1e-8);assertEquals(-1,result.velocity().z(),1e-8);
        assertEquals(.42*20-32*.05,result.velocity().y(),1e-8);
    }
    @Test void fastFallCannotPassThroughAThinCompoundFloor() {
        var shape=new CompoundCollider(List.of(new BodyBounds(v(-4,-.01,-4),v(4,.01,4))));
        var b=rigid(0,v(0,0,0),v(0,0,0));var m=new BodyMotion(1,shape,v(0,0,0),v(0,0,0),Quat.IDENTITY,v(0,0,0),Quat.IDENTITY,b);
        var result=DynamicBodyAttachment.step(new DynamicBodyAttachment.State(v(0,2,0),v(0,-80,0),false,-1),
            DynamicBodyAttachment.Input.idle(),List.of(m),.6,1.8,.05,FREE);
        assertTrue(result.grounded());assertTrue(result.feet().y()>=.01-1e-7);assertTrue(result.feet().y()<.02);
    }
    @Test void narrowphaseLeavesAnActualDoorwayOpenAndStopsAtSolidWall() {
        var frame=new CompoundCollider(List.of(new BodyBounds(v(-2,-1,-.1),v(-.5,3,.1)),new BodyBounds(v(.5,-1,-.1),v(2,3,.1))));
        var b=rigid(0,v(0,0,0),v(0,0,0));
        var initial=new DynamicBodyAttachment.State(v(0,0,-2),v(0,0,80),false,-1);
        var door=new BodyMotion(1,frame,v(0,0,0),v(0,0,0),Quat.IDENTITY,v(0,0,0),Quat.IDENTITY,b);
        var through=DynamicBodyAttachment.step(initial,DynamicBodyAttachment.Input.idle(),List.of(door),.6,1.8,.05,FREE);
        assertTrue(through.feet().z()>1.9);
        var solid=new CompoundCollider(List.of(new BodyBounds(v(-2,-1,-.1),v(2,3,.1))));
        var wall=new BodyMotion(1,solid,v(0,0,0),v(0,0,0),Quat.IDENTITY,v(0,0,0),Quat.IDENTITY,b);
        var blocked=DynamicBodyAttachment.step(initial,DynamicBodyAttachment.Input.idle(),List.of(wall),.6,1.8,.05,FREE);
        assertTrue(blocked.feet().z()<=-.4+1e-6);assertEquals(0,blocked.velocity().z(),1e-7);
    }
    @Test void invalidCommandsCannotEnterTheServerMotor() {
        assertThrows(IllegalArgumentException.class,()->new DynamicBodyAttachment.Input(Double.NaN,0,false,v(0,0,1)));
        assertThrows(IllegalArgumentException.class,()->new DynamicBodyAttachment.Input(2,0,false,v(0,0,1)));
        assertThrows(IllegalArgumentException.class,()->new DynamicBodyAttachment.Input(0,0,false,v(0,0,0)));
        assertThrows(IllegalArgumentException.class,()->DynamicBodyAttachment.step(standing(v(0,0,0)),DynamicBodyAttachment.Input.idle(),List.of(),.6,1.8,10,FREE));
    }
    @Test void freePhysicalDeckAndWalkerStayInContactAcrossRepeatedWorldTicks() {
        var mass=new D6MassProperties();
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)mass.addBlock(v(x+.5,.5,z+.5),1);
        var body=new D6PhysicsBody().withMassProperties(mass).withPosition(v(0,0,0)).withLinearVelocity(v(3,0,0));
        var shape=new CompoundCollider(List.of(new BodyBounds(v(-4,-.5,-4),v(4,.5,4))));
        var state=standing(v(3,0,0));
        for(int tick=0;tick<70;tick++) {
            Vec3 before=body.position();Quat rotation=body.rotation();body.step(.05);
            var motion=new BodyMotion(1,shape,v(0,0,0),before,rotation,body.position(),body.rotation(),body);
            var input=tick>=35&&tick<47?new DynamicBodyAttachment.Input(0,1,false,v(0,0,1)):DynamicBodyAttachment.Input.idle();
            state=DynamicBodyAttachment.step(state,input,List.of(motion),.6,1.8,.05,FREE);
            assertTrue(state.grounded(),"contact lost tick="+tick+" state="+state+" body="+body.position()+" q="+body.rotation());
            Vec3 local=body.rotation().inverse().rotate(state.feet().minus(body.position()));
            assertTrue(Math.abs(local.x())<3.7&&Math.abs(local.z())<3.7,"walking unintentionally left the deck tick="+tick+" local="+local);
        }
    }
    @Test void tinyContactCorrectionDoesNotDiscardWalkingTimeAtVanillaMovementThreshold() {
        var body=rigid(0,v(0,0,0),v(3,0,0));
        var initial=new DynamicBodyAttachment.State(v(0,.5-.00002,0),v(3,0,0),true,1);
        DynamicBodyAttachment.WorldMove vanillaThreshold=(p,d)->d.lengthSquared()>1e-7?d:v(0,0,0);
        var result=DynamicBodyAttachment.step(initial,DynamicBodyAttachment.Input.idle(),List.of(motion(body,v(0,0,0),v(.15,0,0))),
            .6,1.8,.05,vanillaThreshold);
        assertTrue(result.grounded());assertTrue(result.feet().x()>.149,"no CCD substep may silently lose its time");
        assertTrue(result.feet().y()>=.5);assertEquals(0,result.velocity().y(),1e-9);
    }
    @Test void tinyTimeOfImpactMovementCannotBeMistakenForAWorldWall() {
        var body=rigid(0,v(0,0,0),v(3,0,0));
        var initial=new DynamicBodyAttachment.State(v(0,.50001,0),v(3,-1,0),false,-1);
        DynamicBodyAttachment.WorldMove vanillaThreshold=(p,d)->d.lengthSquared()>1e-7?d:v(0,0,0);
        var result=DynamicBodyAttachment.step(initial,DynamicBodyAttachment.Input.idle(),List.of(motion(body,v(0,0,0),v(.15,0,0))),
            .6,1.8,.05,vanillaThreshold);
        assertTrue(result.grounded());assertTrue(result.feet().x()>.149);
        assertEquals(3,result.velocity().x(),1e-9,"the ignored tiny fraction cannot zero world momentum");
    }
}
