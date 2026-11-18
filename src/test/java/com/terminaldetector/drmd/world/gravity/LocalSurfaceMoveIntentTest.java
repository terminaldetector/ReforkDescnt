package com.terminaldetector.drmd.world.gravity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The real planner's command lease, independent of Minecraft bootstrap and path search timing. */
class LocalSurfaceMoveIntentTest {
    @Test void speculativeRequestAndCompletionNeverArmMovement(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();
        state.invalidate();
        assertNull(state.delivery(state.generation(),target));assertNull(state.current());
    }

    @Test void successfulNativeCommandRetainsExactSpeedAndGenerationUntilNativeCompletion(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();
        state.invalidate();long request=state.generation();state.accepted(true,target,1.25);
        var command=state.delivery(request,target);assertNotNull(command);
        assertEquals(request,command.generation());assertEquals(1.25,command.speed());
        assertSame(command,state.delivery(request,target)); // HEAD and RETURN see the same lease.
        state.completed(command);assertNull(state.delivery(request,target));
    }

    @Test void falseNativeReturnCannotArmOrRetainPreviousCommand(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();
        state.accepted(true,target,1);assertNotNull(state.current());
        state.accepted(false,target,2);assertNull(state.current());
    }

    @Test void stopOrRetargetCannotResurrectOldReadyGeneration(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var a=new Object();var b=new Object();
        state.accepted(true,a,1);long oldGeneration=state.generation();var old=state.current();
        state.invalidate();assertNull(state.delivery(oldGeneration,a));
        state.accepted(true,b,1.25);assertNull(state.delivery(oldGeneration,b));
        assertNull(state.delivery(state.generation(),a));assertNotNull(state.delivery(state.generation(),b));
        state.completed(old);assertNotNull(state.current()); // A late return must not erase B.
    }

    @Test void equalValueOrReusedIdDoesNotTransferTargetOwnership(){
        var state=new LocalSurfaceNavigation.MoveIntent<String>();
        var original=new String("same-id");var replacement=new String("same-id");
        state.accepted(true,original,1);assertEquals(original,replacement);
        assertNull(state.delivery(state.generation(),replacement));
        assertNotNull(state.delivery(state.generation(),original));
    }

    @Test void clearedWeakOwnerOrExplicitCancellationFailsClosed(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();
        state.accepted(true,target,1);state.current().target().clear();
        assertNull(state.delivery(state.generation(),target));
        state.accepted(true,target,1);state.cancel();assertNull(state.current());
    }

    @Test void invalidSpeedAndMissingTargetNeverReuseOldAcceptedSpeed(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();
        for(double speed:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            state.accepted(true,target,1);state.accepted(true,target,speed);assertNull(state.current());
        }
        state.accepted(true,target,1);state.accepted(true,null,1);assertNull(state.current());
    }

    @Test void onlyExactOwnedPathAndSpeedRetainThePendingCommand(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();
        var path=new String("ready13");state.accepted(true,target,1);
        var command=state.current();var marker=state.beginDelivery(command,path);assertNotNull(marker);
        assertTrue(state.ownsDelivery(marker,path,1));
        assertFalse(state.ownsDelivery(marker,new String("ready13"),1));
        assertFalse(state.ownsDelivery(marker,path,1.25));assertFalse(state.ownsDelivery(marker,null,1));
        assertSame(command,state.current()); // The native completion HEAD must not cancel itself.
    }

    @Test void speculativeOrRetiredCommandCannotCreateDeliveryAuthority(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();var path=new Object();
        assertNull(state.beginDelivery(null,path));
        state.accepted(true,target,1);var old=state.current();assertNull(state.beginDelivery(old,null));
        state.cancel();assertNull(state.beginDelivery(old,path));
        state.accepted(true,target,1);assertNull(state.beginDelivery(old,path));
    }

    @Test void identicalTargetValuesCannotTransferMarkerAcrossSearchesOrGenerations(){
        var first=new LocalSurfaceNavigation.MoveIntent<Object>();var other=new LocalSurfaceNavigation.MoveIntent<Object>();
        var target=new Object();var path=new Object();first.accepted(true,target,1);other.accepted(true,target,1);
        var marker=first.beginDelivery(first.current(),path);assertFalse(other.ownsDelivery(marker,path,1));
        first.invalidate();first.accepted(true,target,1);assertFalse(first.ownsDelivery(marker,path,1));
        assertTrue(first.ownsDelivery(first.beginDelivery(first.current(),path),path,1));
    }

    @Test void completedOrLostOwnerLeavesNoPermissionForALaterPublicPath(){
        var state=new LocalSurfaceNavigation.MoveIntent<Object>();var target=new Object();var path=new Object();
        state.accepted(true,target,1);var command=state.current();var marker=state.beginDelivery(command,path);
        state.completed(command);assertFalse(state.ownsDelivery(marker,path,1));
        state.accepted(true,target,1);assertFalse(state.ownsDelivery(marker,path,1));
        var live=state.current();var next=state.beginDelivery(live,path);live.target().clear();
        assertFalse(state.ownsDelivery(next,path,1));assertNull(state.beginDelivery(live,path));
    }
}
