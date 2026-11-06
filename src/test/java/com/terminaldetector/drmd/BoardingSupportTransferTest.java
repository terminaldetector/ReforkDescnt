package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual contact motor across two independently colliding moving components. */
class BoardingSupportTransferTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static final DynamicBodyAttachment.WorldMove FREE=(p,d)->d;
    private static BodyMotion deck(int id,double x,Quat q,Vec3 velocity,boolean gravity){
        return deck(id,x,q,velocity,gravity,0);
    }
    private static BodyMotion deck(int id,double x,Quat q,Vec3 velocity,boolean gravity,double time){
        var pos=q.rotate(v(x,0,0)).plus(velocity.scaled(time));var dynamics=new D6PhysicsBody().withMass(0).withInertia(D6Mat3.diagonal(20,30,20)).withPosition(pos).withRotation(q).withLinearVelocity(velocity);
        var shape=new CompoundCollider(List.of(new BodyBounds(v(-4,-.5,-4),v(4,.5,4))));
        var field=gravity?new BodyGravityField(v(0,-9.81,0),new BodyBounds(v(-12,-2,-12),v(12,10,12))):BodyGravityField.NONE;
        return new BodyMotion(id,shape,v(0,0,0),pos,q,pos.plus(velocity.scaled(.05)),q,dynamics,field);
    }
    @Test void touchingDecksRetainTheExistingSupportRegardlessOfCandidateOrder(){
        for(boolean reverse:new boolean[]{false,true}){
            var a=deck(10,-4,Quat.IDENTITY,v(0,0,3),false);var b=deck(20,4,Quat.IDENTITY,v(0,0,3),false);
            var state=new DynamicBodyAttachment.State(v(0,.5,0),v(0,0,3),true,10);
            var next=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),reverse?List.of(b,a):List.of(a,b),.6,1.8,.05,FREE);
            assertTrue(next.grounded());assertEquals(10,next.support(),"a seam must not change ownership when a spatial query changes order");assertEquals(3,next.velocity().z(),1e-8);
        }
    }
    @Test void unownedOverlappingGravityVolumesChooseTheSameFrameOwner(){
        for(boolean reverse:new boolean[]{false,true}){
            var a=deck(10,-4,Quat.IDENTITY,v(0,0,3),true);var b=deck(20,4,Quat.IDENTITY,v(0,0,3),true);
            var state=new DynamicBodyAttachment.State(v(0,2,0),v(0,0,3),false,-1,Quat.IDENTITY,-1);
            var next=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),reverse?List.of(b,a):List.of(a,b),.6,1.8,.05,FREE);
            assertEquals(10,next.gravityOwner(),"unowned overlap has deterministic selection");assertFalse(next.grounded());
        }
    }
    @Test void leavingOneDeckTransfersSupportAndJumpKeepsWorldSpeedOnABankedAssembly(){
        Quat q=new Quat(0,0,Math.sin(.3),Math.cos(.3));var carrier=v(1,2,3);var up=q.rotate(v(0,1,0));
        var a=deck(10,-4,q,carrier,true);var b=deck(20,4,q,carrier,true);
        var state=new DynamicBodyAttachment.State(q.rotate(v(.8,.5,0)),carrier,true,10,q,20);
        var moved=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),List.of(a,b),.6,1.8,.05,FREE);
        assertTrue(moved.grounded());assertEquals(20,moved.support(),"old deck no longer has a physical contact");assertEquals(20,moved.gravityOwner());
        var shiftedA=deck(10,-4,q,carrier,true,.05);var shiftedB=deck(20,4,q,carrier,true,.05);
        var jumped=DynamicBodyAttachment.step(moved,new DynamicBodyAttachment.Input(0,0,true,q.rotate(v(0,0,1))),List.of(shiftedA,shiftedB),.6,1.8,.05,FREE);
        assertFalse(jumped.grounded());assertEquals(-1,jumped.support());
        assertTrue(jumped.velocity().minus(carrier.plus(up.scaled(8.4-9.81*.05))).length()<1e-7,"carrier velocity is inherited once, with jump/gravity in the local frame");
    }
    @Test void actualContactBelowTheFeetWinsOverATouchingOldDeckEdge(){
        for(boolean reverse:new boolean[]{false,true}){
            var a=deck(10,-4,Quat.IDENTITY,v(0,0,3),false);var b=deck(20,4,Quat.IDENTITY,v(0,0,3),false);
            var state=new DynamicBodyAttachment.State(v(.2,.5,0),v(0,0,3),true,10);
            var next=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),reverse?List.of(b,a):List.of(a,b),.6,1.8,.05,FREE);
            assertTrue(next.grounded());assertEquals(20,next.support(),"the old deck still touches the toe but the new surface is below the feet centre");
        }
    }

    @Test void anExistingGravityOwnerOutranksTheOtherDeckSupportUntilItsFieldIsLeft(){
        for(boolean reverse:new boolean[]{false,true}){
            var a=deck(10,-4,Quat.IDENTITY,v(0,0,3),true);var b=deck(20,4,Quat.IDENTITY,v(0,0,3),true);
            var state=new DynamicBodyAttachment.State(v(0,2,0),v(0,0,3),false,10,Quat.IDENTITY,20);
            var next=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),reverse?List.of(b,a):List.of(a,b),.6,1.8,.05,FREE);
            assertEquals(20,next.gravityOwner(),"a continuing local frame takes precedence over another touching module");
            var departure=new DynamicBodyAttachment.State(v(-10,2,0),v(0,0,3),false,10,Quat.IDENTITY,20);
            var left=DynamicBodyAttachment.step(departure,DynamicBodyAttachment.Input.idle(),reverse?List.of(b,a):List.of(a,b),.6,1.8,.05,FREE);
            assertEquals(10,left.gravityOwner(),"a field no longer containing the actor cannot retain it");
        }
    }
    @Test void repeatedSpatialQueryReorderingCannotOscillateAContactAtTheSeam(){
        var state=new DynamicBodyAttachment.State(v(0,.5,0),v(0,0,3),true,20);
        for(int tick=0;tick<40;tick++){
            var a=deck(10,-4,Quat.IDENTITY,v(0,0,3),false,tick*.05);
            var b=deck(20,4,Quat.IDENTITY,v(0,0,3),false,tick*.05);
            state=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),tick%2==0?List.of(a,b):List.of(b,a),.6,1.8,.05,FREE);
            assertTrue(state.grounded());assertEquals(20,state.support());
            assertEquals(0,state.feet().x(),1e-8);assertEquals(3,state.velocity().z(),1e-8);
        }
        assertEquals(6,state.feet().z(),1e-7,"contact integrates actual carrier motion without an anchor teleport");
    }

    @Test void slightDeckTiltCannotMakeASolverCornerStealTheBearingSurface(){
        var q=new Quat(0,0,Math.sin(-.0001),Math.cos(-.0001));
        for(boolean reverse:new boolean[]{false,true}){
            var a=deck(10,-4,q,v(0,0,3),false);var b=deck(20,4,q,v(0,0,3),false);
            var state=new DynamicBodyAttachment.State(q.rotate(v(-.2,.5,0)),v(0,0,3),true,10);
            var next=DynamicBodyAttachment.step(state,DynamicBodyAttachment.Input.idle(),reverse?List.of(b,a):List.of(a,b),.6,1.8,.05,FREE);
            assertTrue(next.grounded());assertEquals(10,next.support(),"an impulse solver's representative corner is not the location of the feet");
        }
    }

}
