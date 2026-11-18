package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.BodyPortalDisplay;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyPortalDisplayTest {
    private static Quat turn(double radians){return new Quat(0,0,Math.sin(radians/2),Math.cos(radians/2));}
    private static void same(Quat a,Quat b){assertTrue(a.rotate(new Vec3(1,2,3)).minus(b.rotate(new Vec3(1,2,3))).length()<1e-9);}
    private static final Vec3 EXIT=new Vec3(2048.125,304.5,-4096.75);

    @Test void missingOldEpochAndOriginPreserveOrdinaryTwoTickFlight(){
        var d=new BodyPortalDisplay();
        assertTrue(d.receivePose(0,Quat.IDENTITY,null,0).accepted());
        assertFalse(d.receivePose(0,turn(2),null,10).discontinuity());
        same(turn(1),d.sample(11));same(turn(2),d.sample(12));
        assertEquals(0,d.epoch());assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(EXIT));
    }

    @Test void bothPacketOrdersEmitOneAtomicDestinationSnapAndNoRotationAcrossThePortal(){
        for(boolean positionFirst:new boolean[]{false,true}){
            var d=new BodyPortalDisplay();d.receivePose(0,Quat.IDENTITY,null,1);
            if(positionFirst)assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(EXIT));
            var warp=d.receivePose(1,turn(2.8),EXIT,17);
            assertTrue(warp.accepted());assertTrue(warp.discontinuity());assertEquals(EXIT,warp.snapOrigin());
            for(double frame:new double[]{16.5,17,17.1,17.99,18})same(turn(2.8),d.sample(frame));
            assertFalse(d.receivePose(1,turn(2.8),EXIT,18).discontinuity());
            if(!positionFirst)assertEquals(BodyPortalDisplay.PositionAction.ALREADY_AT_WARP_DESTINATION,d.receivePosition(EXIT));
            assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(EXIT.plus(new Vec3(1,0,0))));
        }
    }

    @Test void renderSamplesDoNotConsumeTheOneQuantizedDestinationAcknowledgement(){
        var d=new BodyPortalDisplay();d.receivePose(3,turn(1),EXIT,10);
        for(double time:new double[]{10,10.5,9.9,10.8})same(turn(1),d.sample(time));
        var quantized=EXIT.plus(new Vec3(1.0/8192,-1.0/8192,0));
        assertEquals(BodyPortalDisplay.PositionAction.ALREADY_AT_WARP_DESTINATION,d.receivePosition(quantized));
        assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(quantized));assertEquals(3,d.epoch());
    }

    @Test void stalePoseCannotRewindStateOrConsumeANewerPendingWarp(){
        var d=new BodyPortalDisplay();d.receivePose(4,turn(2),EXIT,10);
        assertFalse(d.receivePose(3,null,null,Double.NaN).accepted());
        same(turn(2),d.sample(10));assertEquals(4,d.epoch());
        assertEquals(BodyPortalDisplay.PositionAction.ALREADY_AT_WARP_DESTINATION,d.receivePosition(EXIT));
        d.receivePose(4,turn(3),EXIT,11);same(turn(2.5),d.sample(12));same(turn(3),d.sample(13));
    }

    @Test void newerWarpReplacesPendingDestinationAndMovingPositionsResumeWithoutInventingAnEpoch(){
        var d=new BodyPortalDisplay();d.receivePose(1,turn(1),EXIT,1);var next=EXIT.plus(new Vec3(-900,100,700));
        assertEquals(next,d.receivePose(2,turn(-2),next,2).snapOrigin());
        assertFalse(d.receivePose(1,turn(1),EXIT,3).accepted());same(turn(-2),d.sample(3));
        assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(next.plus(new Vec3(2,0,0))));
        assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(EXIT));
        d.reset(2,turn(-2),0);assertEquals(BodyPortalDisplay.PositionAction.APPLY,d.receivePosition(next));same(turn(-2),d.sample(0));
    }

    @Test void invalidNewPoseCannotConsumeTheEpochOrOverwriteTheCurrentDisplay(){
        var d=new BodyPortalDisplay();d.receivePose(1,turn(1),EXIT,1);
        assertThrows(IllegalArgumentException.class,()->d.receivePose(2,turn(2),EXIT,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->d.receivePose(2,turn(2),null,2));
        assertThrows(IllegalArgumentException.class,()->d.receivePose(2,new Quat(0,0,0,0),EXIT,2));
        assertThrows(IllegalArgumentException.class,()->d.receivePose(2,turn(2),new Vec3(Double.POSITIVE_INFINITY,0,0),2));
        assertEquals(1,d.epoch());same(turn(1),d.sample(2));
        assertEquals(BodyPortalDisplay.PositionAction.ALREADY_AT_WARP_DESTINATION,d.receivePosition(EXIT));
    }
}
