package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlightLoadingClipTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static DynamicBodyAttachment.Motion fence(FlightLoadingClip loading){
        return (feet,frame,requested)->new DynamicBodyAttachment.Move(loading.clip(feet,frame,requested),List.of());
    }
    @Test void anUnavailableCubeStopsTheWholeSweptSuitWithoutConsumingMomentumThenNaturallyReleases(){
        var unknown=new BodyBounds(new Vec3(-16,16,-16),new Vec3(16,32,16));
        var initial=new DynamicBodyAttachment.State(new Vec3(0,13,0),new Vec3(0,40,0),false,-1);
        var wait=DynamicBodyAttachment.freeFlight(initial,List.of(),.6,1.8,.05,fence(new FlightLoadingClip(List.of(unknown),.6,1.8)));
        assertTrue(wait.feet().y()<=14.2+1e-6&&wait.feet().y()>13);
        assertEquals(initial.velocity(),wait.velocity());
        var still=DynamicBodyAttachment.freeFlight(wait,List.of(),.6,1.8,.05,fence(new FlightLoadingClip(List.of(unknown),.6,1.8)));
        assertEquals(wait.feet().y(),still.feet().y(),1e-6);
        assertEquals(initial.velocity(),still.velocity());
        var ready=DynamicBodyAttachment.freeFlight(still,List.of(),.6,1.8,.05,fence(new FlightLoadingClip(List.of(),.6,1.8)));
        assertEquals(still.feet().y()+2,ready.feet().y(),1e-9);
        assertEquals(initial.velocity(),ready.velocity());
    }
    @Test void knownMaterialRemainsARealCollisionAndDoesConsumeItsNormalVelocity(){
        var material=new OrientedCharacterClip(new CompoundCollider(List.of(new BodyBounds(new Vec3(-16,16,-16),new Vec3(16,32,16)))),.6,1.8);
        var result=DynamicBodyAttachment.freeFlight(new DynamicBodyAttachment.State(new Vec3(0,13,0),new Vec3(0,40,0),false,-1),List.of(),.6,1.8,.05,material);
        assertTrue(result.feet().y()<=14.2+1e-6);assertEquals(0,result.velocity().y(),1e-9);
    }
    @Test void negativeSignedYAndFullActorVolumeUseTheSameBoundaryPolicy(){
        var clip=new FlightLoadingClip(List.of(new BodyBounds(new Vec3(-16,-528,-16),new Vec3(16,-512,16))),.6,1.8);
        var delta=clip.clip(new Vec3(0,-510,0),Quat.IDENTITY,new Vec3(0,-4,0));
        assertTrue(delta.y()>-2.000001&&delta.y()<-1.9);
        assertEquals(ZERO,new FlightLoadingClip(List.of(new BodyBounds(new Vec3(-16,-528,-16),new Vec3(16,-512,16))),.6,1.8)
            .clip(new Vec3(0,-512.1,0),Quat.IDENTITY,new Vec3(0,-1,0)));
    }
}
