package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.TeleportAbilityState;
import com.terminaldetector.drmd.ai.TeleportAbilityState.Phase;
import com.terminaldetector.drmd.ai.TeleportAbilityState.Saved;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Protocol acceptance: warning opportunity, replay refusal and save authority, not a visual/teleport simulation. */
final class TeleportAbilityStateTest {
    @Test void aVisibleWarningGetsTwentyDistinctNormalTicksBeforeOneCommitTicket(){
        var s=new TeleportAbilityState();long start=s.requestStart();assertTrue(s.warn(start));assertFalse(s.mayFire());
        for(int tick=1;tick<20;tick++){assertFalse(s.tick(tick));assertFalse(s.tick(tick));assertEquals(Phase.WARN,s.phase());}
        assertTrue(s.tick(20));long commit=s.epoch();assertFalse(s.tick(20));assertTrue(s.committed(commit));assertFalse(s.committed(commit));
        assertEquals(Phase.RECOVERY,s.phase());assertEquals(100,s.cooldown());
    }
    @Test void aLateHitInvalidatesTheAlreadyQueuedCommitWithoutGrantingAnotherStart(){
        var s=new TeleportAbilityState();s.warn(s.requestStart());for(int t=1;t<=20;t++)s.tick(t);long queued=s.epoch();
        s.interrupt();assertFalse(s.committed(queued));assertEquals(-1,s.requestStart());assertFalse(s.mayFire());
    }
    @Test void finiteRecoveryAndCooldownResumeOrdinaryGunThenAbilityWithoutDeadlineExtension(){
        var s=new TeleportAbilityState();s.warn(s.requestStart());s.interrupt();
        for(int t=1;t<20;t++){s.tick(t);assertFalse(s.mayFire());}
        s.tick(20);assertEquals(Phase.COOLDOWN,s.phase());assertTrue(s.mayFire());assertFalse(s.ready());
        for(int t=21;t<=100;t++)s.tick(t);assertTrue(s.ready());assertTrue(s.requestStart()>0);
    }
    @Test void additionalHitsCannotExtendTheFixedRecoveryIntoAnInfiniteStun(){
        var s=new TeleportAbilityState();s.warn(s.requestStart());s.interrupt();
        for(int t=1;t<=20;t++){s.tick(t);s.interrupt();}
        assertEquals(Phase.COOLDOWN,s.phase());assertEquals(80,s.cooldown());assertTrue(s.mayFire());
    }
    @Test void everyPendingSaveCancelsSafelyInsteadOfReplayingAnUnseenWarning(){
        for(var pending:new Saved[]{new Saved(Phase.START_PENDING,0,0),new Saved(Phase.WARN,1,0),new Saved(Phase.COMMIT_PENDING,0,0)}){
            var s=new TeleportAbilityState();assertTrue(s.restore(pending));assertEquals(Phase.RECOVERY,s.phase());
            assertEquals(20,s.remaining());assertEquals(100,s.cooldown());assertFalse(s.mayFire());assertEquals(-1,s.requestStart());
        }
    }
    @Test void malformedOrInconsistentSavedCountersCannotCreateAuthority(){
        for(var bad:new Saved[]{new Saved(Phase.WARN,0,0),new Saved(Phase.WARN,21,0),new Saved(Phase.RECOVERY,20,1),new Saved(Phase.IDLE,0,1),new Saved(Phase.COOLDOWN,0,101),new Saved(null,0,0)}){
            var s=new TeleportAbilityState();assertFalse(s.restore(bad));assertFalse(s.ready());assertEquals(-1,s.requestStart());assertTrue(s.mayFire());
        }
    }
    @Test void backwardTimeAndRepeatedTicksCannotEraseTheInterruptOpportunity(){
        var s=new TeleportAbilityState();s.warn(s.requestStart());s.tick(100);s.tick(99);assertEquals(Phase.RECOVERY,s.phase());assertEquals(100,s.cooldown());
        assertFalse(s.committed(s.epoch()));var r=new TeleportAbilityState();assertTrue(r.restore(new Saved(Phase.COOLDOWN,0,72)));r.tick(5000);assertEquals(71,r.cooldown());
    }
}
