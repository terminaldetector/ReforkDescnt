package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FreeFlightStaticSweepTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    @Test void oneContinuousWorldCastStopsAHighSpeedSuitAtTheRealThinWall(){
        var wall=new OrientedCharacterClip(new CompoundCollider(List.of(new BodyBounds(new Vec3(25,-4,-4),new Vec3(25.0625,4,4)))),.6,1.8);
        var calls=new AtomicInteger();DynamicBodyAttachment.Motion actual=(feet,frame,want)->{calls.incrementAndGet();return wall.move(feet,frame,want);};
        var state=new DynamicBodyAttachment.State(ZERO,new Vec3(1000,0,0),false,-1);
        var after=DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,.05,actual);
        assertEquals(1,calls.get());assertTrue(after.feet().x()>24.69&&after.feet().x()<=24.700001);
        assertEquals(0,after.velocity().x(),1e-9);
        var replay=DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,.05,wall);
        assertEquals(after,replay);
    }
    @Test void aLoadingFenceHoldsTranslationButNotMomentumAndTheSameReplayRecoversAfterFull(){
        var loading=new FlightLoadingClip(List.of(new BodyBounds(new Vec3(16,-16,-16),new Vec3(32,16,16))),.6,1.8);
        var calls=new AtomicInteger();DynamicBodyAttachment.Motion pending=(feet,frame,want)->{calls.incrementAndGet();return new DynamicBodyAttachment.Move(loading.clip(feet,frame,want),List.of());};
        var state=new DynamicBodyAttachment.State(new Vec3(14,0,0),new Vec3(1000,0,0),false,-1);
        var after=DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,.05,pending);
        assertEquals(1,calls.get());assertTrue(after.feet().x()<=15.700001);assertEquals(state.velocity(),after.velocity());
        var replay=DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,.05,pending);assertEquals(after,replay);
        var ready=DynamicBodyAttachment.freeFlight(after,List.of(),.6,1.8,.05,(feet,frame,want)->new DynamicBodyAttachment.Move(want,List.of()));
        assertEquals(after.feet().x()+50,ready.feet().x(),1e-9);assertEquals(state.velocity(),ready.velocity());
    }
    @Test void obliqueMaterialProjectsOnlyClosingMomentumAndInvalidDurationsStillReject(){
        var normal=new Vec3(-.6,0,-.8);var velocity=new Vec3(20,7,40);
        DynamicBodyAttachment.Motion material=(feet,frame,want)->new DynamicBodyAttachment.Move(want.scaled(.25),List.of(normal));
        var state=new DynamicBodyAttachment.State(ZERO,velocity,false,-1);
        var result=DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,.05,material);
        var expected=velocity.minus(normal.scaled(velocity.dot(normal)));
        assertEquals(expected,result.velocity());assertEquals(7,result.velocity().y(),1e-12);
        assertThrows(IllegalArgumentException.class,()->DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,0,material));
        assertThrows(IllegalArgumentException.class,()->DynamicBodyAttachment.freeFlight(state,List.of(),.6,1.8,.1,material));
    }
}
