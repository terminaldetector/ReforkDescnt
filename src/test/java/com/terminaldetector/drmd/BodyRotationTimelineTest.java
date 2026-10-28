package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.BodyRotationTimeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyRotationTimelineTest {
    private static Quat turn(double radians){return new Quat(0,0,Math.sin(radians/2),Math.cos(radians/2));}
    private static void same(Quat a,Quat b){assertTrue(a.rotate(new Vec3(1,2,3)).minus(b.rotate(new Vec3(1,2,3))).length()<1e-9);}
    @Test void firstSpawnUsesItsReceivedPoseWithoutAnIdentityTurn() {
        var t=new BodyRotationTimeline();t.receive(turn(2),17);same(turn(2),t.sample(17));
    }
    @Test void completedTurnDoesNotReplayWhenPartialTickWrapsOnLaterFrames() {
        var t=new BodyRotationTimeline();t.receive(Quat.IDENTITY,0);t.receive(turn(2),10);
        same(turn(1),t.sample(11));same(turn(2),t.sample(12));
        for(int tick=12;tick<40;tick++)for(double delta:new double[]{0,.1,.5,.99})same(turn(2),t.sample(tick+delta));
    }
    @Test void velocityOnlyPacketsDoNotPostponeTheEndOfAStationaryTurn() {
        var t=new BodyRotationTimeline();t.receive(Quat.IDENTITY,0);t.receive(turn(2),10);
        t.receive(turn(2),10.5);t.receive(turn(2),11);same(turn(1),t.sample(11));
        t.receive(turn(2),11.5);same(turn(2),t.sample(12));
    }
    @Test void retargetAndOlderCameraTimeKeepTheLastDisplayedPoseContinuous() {
        var t=new BodyRotationTimeline();t.receive(Quat.IDENTITY,0);t.receive(turn(2),10);
        var displayed=t.sample(11.5);t.receive(turn(-1),11);same(displayed,t.sample(11));
        same(displayed,t.sample(11.5));same(turn(-1),t.sample(13.5));
    }
    @Test void NegatedQuaternionDoesNotIntroduceAFullSpinOrResetProgress() {
        var t=new BodyRotationTimeline();t.receive(Quat.IDENTITY,0);t.receive(turn(1),1);
        Quat q=turn(1);t.receive(new Quat(-q.x(),-q.y(),-q.z(),-q.w()),2);
        same(turn(.5),t.sample(2));same(turn(1),t.sample(3));
    }
    @Test void resetStartsANewClockAndNonFiniteInputsAreRejected() {
        var t=new BodyRotationTimeline();t.receive(turn(1),100);t.reset(turn(2),0);same(turn(2),t.sample(0));
        assertThrows(IllegalArgumentException.class,()->t.receive(new Quat(0,0,0,0),1));
        assertThrows(IllegalArgumentException.class,()->t.sample(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->t.receive(turn(1),Double.POSITIVE_INFINITY));
    }
}
