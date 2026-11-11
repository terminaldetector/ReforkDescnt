package com.terminaldetector.drmd.world.threat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Encounter timing, observation and save continuity without a game bootstrap. */
class ThreatProgressTest {
    private static final Point ALARM=new Point(18,220,-7),SEEN=new Point(30,225,-9);
    private static ThreatProgress ready(){return new ThreatProgress(new Snapshot(Phase.QUIET,GRACE_TICKS,0,0,0,-1,-1,false,0,ALARM,ALARM));}
    private static ThreatProgress searching(){var p=ready();assertTrue(p.alarm(100,ALARM));p.advance(100,0,true,null,false);p.advance(500,0,true,null,false);assertEquals(Phase.SEARCHING,p.phase());return p;}

    @Test void offlineWorldClockCannotSpendNoviceProtectionOrSkipTheWarning(){
        var p=new ThreatProgress();assertTrue(p.alarm(100,ALARM));
        p.advance(10_100,10_000,true,null,false);
        assertEquals(100,p.snapshot().played());assertEquals(Phase.QUIET,p.phase());
        for(int i=1;i<120;i++)p.advance(10_100+i,100,true,null,false);
        assertEquals(Phase.WARNING,p.phase());assertEquals(10_219+WARNING_TICKS,p.snapshot().until());
        p.advance(p.snapshot().until()-1,0,true,null,false);assertEquals(Phase.WARNING,p.phase());
        p.advance(p.snapshot().until(),0,true,null,false);assertEquals(Phase.SEARCHING,p.phase());
    }
    @Test void lateAlarmProcessingStillGivesTheEntireWarning(){
        var p=ready();p.alarm(100,ALARM);p.advance(1_000,0,true,null,false);
        assertEquals(Phase.WARNING,p.phase());assertEquals(1_400,p.snapshot().until());assertEquals(0,p.spawned());
        assertFalse(p.alarm(1_001,SEEN));assertEquals(ALARM,p.origin());
    }
    @Test void lostSightSearchesTheLastActualObservationWithoutFollowingHiddenMovement(){
        var p=searching();p.spawnedActor();p.advance(510,0,true,SEEN,false);
        assertEquals(Phase.HUNTING,p.phase());assertEquals(SEEN,p.lastKnown());
        p.advance(709,0,true,null,false);assertEquals(Phase.HUNTING,p.phase());
        p.advance(710,0,true,null,false);assertEquals(Phase.SEARCHING,p.phase());assertEquals(SEEN,p.lastKnown());
        assertEquals(510,p.snapshot().lastSeen());
    }
    @Test void hideAndReacquireNeverExtendTheFiniteEncounter(){
        var p=searching();p.spawnedActor();long end=p.snapshot().encounterEnds();
        for(long t=510;t<end;t+=300){p.advance(t,0,true,SEEN,false);p.advance(Math.min(t+200,end),0,true,null,false);assertEquals(end,p.snapshot().encounterEnds());}
        p.advance(end,0,true,SEEN,false);assertEquals(Phase.COOLDOWN,p.phase());
    }
    @Test void defeatedPatrolStartsOnePersistedCooldownAndCannotBeReplaced(){
        var p=searching();p.spawnedActor();p.spawnedActor();
        p.advance(520,0,true,null,true);assertEquals(Phase.COOLDOWN,p.phase());
        long until=p.snapshot().until();var restored=new ThreatProgress(p.snapshot());
        assertFalse(restored.alarm(521,ALARM));restored.advance(until-1,0,true,null,true);
        assertEquals(Phase.COOLDOWN,restored.phase());assertEquals(until,restored.snapshot().until());
        restored.advance(until,0,true,null,true);assertEquals(Phase.QUIET,restored.phase());assertEquals(0,restored.spawned());assertTrue(restored.alarm(until,SEEN));
    }
    @Test void reloadingSearchKeepsTheLifetimePatrolAllowanceEvenAfterActorsDisappear(){
        var p=searching();p.spawnedActor();p.spawnedActor();var restored=new ThreatProgress(p.snapshot());
        assertEquals(2,restored.spawned());assertThrows(IllegalStateException.class,restored::spawnedActor);
        restored.advance(550,0,true,null,true);assertEquals(Phase.COOLDOWN,restored.phase());
    }
    @Test void earlyNaturalDetectionDoesNotCancelTheSecondInitialPatrolSlot(){
        var p=searching();p.spawnedActor();p.advance(505,0,true,SEEN,false);assertEquals(Phase.HUNTING,p.phase());
        p.spawnedActor();assertEquals(2,p.spawned());assertThrows(IllegalStateException.class,p::spawnedActor);
    }
    @Test void leavingTheAlarmAreaRetiresAnActiveEncounterAndExpiredQuietAlarmsDoNothing(){
        var p=searching();p.advance(501,0,false,null,false);assertEquals(Phase.COOLDOWN,p.phase());
        var quiet=ready();quiet.alarm(1,ALARM);quiet.advance(ALARM_TTL+1L,0,true,SEEN,false);
        assertEquals(Phase.QUIET,quiet.phase());assertFalse(quiet.snapshot().pending());
    }
    @Test void snapshotsRejectNonfiniteLocationsAndImpossibleAllowances(){
        assertThrows(IllegalArgumentException.class,()->new Point(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new Point(0,30_000_001,0));
        assertThrows(IllegalArgumentException.class,()->new Snapshot(Phase.SEARCHING,0,3,2,10,0,0,false,0,ALARM,ALARM));
        assertThrows(IllegalArgumentException.class,()->new Snapshot(Phase.SEARCHING,0,0,10,10,0,0,false,3,ALARM,ALARM));
        assertThrows(IllegalArgumentException.class,()->deadline(-1,10));assertEquals(Long.MAX_VALUE,deadline(Long.MAX_VALUE-5,10));
    }
}
