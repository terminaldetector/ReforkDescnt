package com.terminaldetector.drmd.world.threat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Intent protocol tests, not live perception/weapon or approved balance evidence. */
class SiegePlanTest {
    private static final UUID ID=UUID.fromString("bfaa943a-90c7-4643-8f0e-c5dcdebf820d"),OWNER=UUID.fromString("798ae23e-6056-4c64-96a7-bef6c7896fea"),
        SITE=UUID.fromString("e21b6622-9d40-476a-bb23-6529d03c660d"),OBSERVER=UUID.fromString("9cc30f7e-e94c-42a6-9db9-848d5627c1f7"),
        FIRST=UUID.fromString("c6bf8d3c-3467-4406-9fe9-d7f69ae72093"),SECOND=UUID.fromString("9351bb5c-0825-42cf-a69b-104c7d096585");
    private static final String WORLD="minecraft:overworld";
    private static final Point POINT=new Point(18,140,-7),MOVED=new Point(22,144,-8);
    private static final SiegePlan.Readiness READY=new SiegePlan.Readiness(true,true,true,true,true);
    private static SiegePlan.Observation sight(UUID observer,long at,Point point){return new SiegePlan.Observation(SITE,observer,OWNER,WORLD,at,point,null,null);}
    private static SiegePlan planned(){return new SiegePlan(ID,OWNER,WORLD,100,sight(OBSERVER,100,POINT));}
    private static SiegePlan preaged(){
        var s=planned().snapshot();return new SiegePlan(new SiegePlan.Snapshot(s.id(),s.participant(),s.world(),s.phase(),s.proposedAt(),s.clock(),GRACE_TICKS,s.since(),s.until(),s.warningEnds(),s.encounterEnds(),s.cooldownEnds(),s.origin(),s.known(),s.actors(),s.acks()));
    }
    private static SiegePlan siege(){var p=preaged();assertTrue(p.bindFutureIdentity(0,FIRST));assertTrue(p.bindFutureIdentity(1,SECOND));p.advance(100,0,true,true,null,false);p.advance(500,0,true,true,sight(FIRST,500,POINT),false);assertEquals(SiegePlan.Phase.SIEGE,p.snapshot().phase());return p;}
    @Test void onlineGraceAndFullWarningCannotBeSpentByAnOfflineWorldClockGap(){
        var p=planned();p.advance(10_100,10_000,true,true,null,false);
        assertEquals(100,p.snapshot().online());assertEquals(SiegePlan.Phase.PLANNED,p.snapshot().phase());
        for(int i=1;i<120;i++)p.advance(10_100+i,100,true,true,null,false);
        assertEquals(SiegePlan.Phase.WARNING,p.snapshot().phase());long end=p.snapshot().warningEnds();assertEquals(10_219+WARNING_TICKS,end);
        p.advance(end-1,0,true,true,null,false);assertEquals(SiegePlan.Phase.WARNING,p.snapshot().phase());
        p.advance(end,0,true,true,null,false);assertEquals(SiegePlan.Phase.SEARCHING,p.snapshot().phase());
    }
    @Test void hiddenOrRereadPointDoesNotRefreshObservationOrFollowAHiddenMovement(){
        var p=siege();p.advance(699,0,true,true,null,false);assertEquals(SiegePlan.Phase.SIEGE,p.snapshot().phase());
        p.advance(700,0,true,true,null,false);assertEquals(SiegePlan.Phase.SEARCHING,p.snapshot().phase());
        p.advance(710,0,true,true,sight(FIRST,500,POINT),false);
        assertEquals(SiegePlan.Phase.SEARCHING,p.snapshot().phase());assertEquals(500,p.snapshot().known().at());assertEquals(POINT,p.snapshot().known().point());
        var foreign=new SiegePlan.Observation(SITE,FIRST,OWNER,"minecraft:the_nether",720,MOVED,null,null);
        p.advance(720,0,true,true,foreign,false);assertEquals(POINT,p.snapshot().known().point());
        p.advance(730,0,true,true,sight(FIRST,731,MOVED),false);assertEquals(500,p.snapshot().known().at());
    }
    @Test void hideReacquireAndRestoreCannotExtendTheFixedEncounter(){
        var p=siege();long end=p.snapshot().encounterEnds();
        for(long now=750;now<end;now+=250){p.advance(now,0,true,true,sight(FIRST,now,MOVED),false);assertEquals(end,p.snapshot().encounterEnds());p.advance(Math.min(end,now+200),0,true,true,null,false);}
        var restored=new SiegePlan(p.snapshot());restored.advance(end,0,true,true,sight(FIRST,end,POINT),false);
        assertEquals(SiegePlan.Phase.RETREATING,restored.snapshot().phase());assertEquals(end+COOLDOWN_TICKS,restored.snapshot().cooldownEnds());
        assertTrue(restored.requestFire(0,FIRST,end,sight(FIRST,end,POINT),READY).isEmpty());
        restored.advance(end+LOST_SIGHT_TICKS,0,true,true,null,false);assertEquals(SiegePlan.Phase.COOLDOWN,restored.snapshot().phase());
        restored.advance(end+COOLDOWN_TICKS,0,true,true,null,false);assertEquals(SiegePlan.Phase.CLOSED,restored.snapshot().phase());
    }
    @Test void futureIdentityBindingIsDistinctIrreversibleDataAndRequiredForFire(){
        var p=preaged();assertTrue(p.bindFutureIdentity(0,FIRST));assertFalse(p.bindFutureIdentity(1,FIRST));assertFalse(p.bindFutureIdentity(0,SECOND));
        p.advance(100,0,true,true,null,false);p.advance(500,0,true,true,sight(FIRST,500,POINT),false);
        assertTrue(p.requestFire(1,SECOND,500,sight(SECOND,500,POINT),READY).isEmpty());assertTrue(p.bindFutureIdentity(1,SECOND));
        assertTrue(p.requestFire(1,SECOND,500,sight(SECOND,500,POINT),READY).isPresent());
    }
    @Test void failedReadinessNeverQueuesARequestOrDebitsTheProposedAllowance(){
        var p=siege();var valid=p.requestFire(0,FIRST,500,sight(FIRST,500,POINT),READY).orElseThrow();
        for(int flag=0;flag<5;flag++){
            var r=new SiegePlan.Readiness(flag!=0,flag!=1,flag!=2,flag!=3,flag!=4);
            assertTrue(p.requestFire(0,FIRST,500,sight(FIRST,500,POINT),r).isEmpty());assertEquals(5,p.remaining(0));
        }
        assertFalse(p.acknowledge(valid,UUID.randomUUID(),500));assertTrue(p.snapshot().acks().isEmpty());
        assertTrue(p.requestFire(0,FIRST,501,sight(FIRST,500,POINT),READY).isEmpty());
    }
    @Test void actualEmissionIdentityConsumesOnceAndCannotBeReusedByTheOtherSlot(){
        var p=siege();var first=p.requestFire(0,FIRST,500,sight(FIRST,500,POINT),READY).orElseThrow();UUID shot=UUID.randomUUID();
        assertTrue(p.acknowledge(first,shot,500));assertFalse(p.acknowledge(first,shot,500));assertEquals(4,p.remaining(0));
        var second=p.requestFire(1,SECOND,500,sight(SECOND,500,POINT),READY).orElseThrow();
        assertFalse(p.acknowledge(second,shot,500));assertEquals(5,p.remaining(1));assertTrue(p.acknowledge(second,UUID.randomUUID(),500));assertEquals(2,p.snapshot().acks().size());
    }
    @Test void foreignInstancePermitHasNoAuthorityEvenWithTheSameSnapshotIdentity(){
        var p=siege();var copy=new SiegePlan(p.snapshot());var foreign=copy.requestFire(0,FIRST,500,sight(FIRST,500,POINT),READY).orElseThrow();
        assertFalse(p.acknowledge(foreign,UUID.randomUUID(),500));assertEquals(5,p.remaining(0));
    }
    @Test void lateAcknowledgementInvalidatesThePermitBeforeAnOldTimestampReplay(){
        var p=siege();var permit=p.requestFire(0,FIRST,500,sight(FIRST,500,POINT),READY).orElseThrow();
        assertFalse(p.acknowledge(permit,UUID.randomUUID(),501));assertFalse(p.acknowledge(permit,UUID.randomUUID(),500));assertEquals(5,p.remaining(0));
        var current=p.requestFire(0,FIRST,501,sight(FIRST,501,POINT),READY).orElseThrow();assertTrue(p.acknowledge(current,UUID.randomUUID(),501));
    }
    @Test void loadingCannotRestoreAnUnacknowledgedCurrentTickPermit(){
        var p=siege();var permit=p.requestFire(0,FIRST,500,sight(FIRST,500,POINT),READY).orElseThrow();var restored=new SiegePlan(p.snapshot());
        assertFalse(restored.acknowledge(permit,UUID.randomUUID(),500));assertEquals(5,restored.remaining(0));assertTrue(restored.snapshot().acks().isEmpty());
    }
    @Test void exactlyTenFiniteAcknowledgementsRetireBothSlotsAndSurviveReload(){
        var p=siege();long now=500;
        for(int slot=0;slot<2;slot++)for(int i=0;i<5;i++){
            UUID actor=slot==0?FIRST:SECOND;var permit=p.requestFire(slot,actor,now,sight(actor,now,POINT),READY).orElseThrow();
            assertTrue(p.acknowledge(permit,UUID.randomUUID(),now));now++;
        }
        assertEquals(10,p.snapshot().acks().size());assertEquals(0,p.remaining(0));assertEquals(0,p.remaining(1));assertEquals(SiegePlan.Phase.RETREATING,p.snapshot().phase());
        var restored=new SiegePlan(p.snapshot());for(int i=0;i<10_000;i++)assertTrue(restored.requestFire(0,FIRST,now,sight(FIRST,now,POINT),READY).isEmpty());
        assertEquals(10,restored.snapshot().acks().size());assertThrows(UnsupportedOperationException.class,()->restored.snapshot().acks().clear());
    }
    @Test void snapshotsAndUpdatesCannotDropHistoryOrRestartAnAlreadyIssuedWarning(){
        var p=siege();var before=p.snapshot();var permit=p.requestFire(0,FIRST,500,sight(FIRST,500,POINT),READY).orElseThrow();p.acknowledge(permit,UUID.randomUUID(),500);
        assertFalse(SiegePlan.canFollow(p.snapshot(),before));assertTrue(SiegePlan.canFollow(before,p.snapshot()));
        var mutable=new ArrayList<>(p.snapshot().acks());var s=p.snapshot();var copied=new SiegePlan.Snapshot(s.id(),s.participant(),s.world(),s.phase(),s.proposedAt(),s.clock(),s.online(),s.since(),s.until(),s.warningEnds(),s.encounterEnds(),s.cooldownEnds(),s.origin(),s.known(),s.actors(),mutable);mutable.clear();assertEquals(1,copied.acks().size());
        var warning=preaged();warning.advance(100,0,true,true,null,false);var w=warning.snapshot();
        var restarted=new SiegePlan.Snapshot(w.id(),w.participant(),w.world(),w.phase(),w.proposedAt(),101,w.online(),101,501,501,4101,0,w.origin(),w.known(),w.actors(),w.acks());
        assertFalse(SiegePlan.canFollow(w,restarted));
    }
    @Test void escapeIneligibilityOrConfirmedPairDefeatClosesFireWithoutAReplacement(){
        for(int reason=0;reason<3;reason++){
            var p=siege();p.advance(501,0,reason!=0,reason!=1,sight(FIRST,501,POINT),reason==2);
            assertEquals(SiegePlan.Phase.RETREATING,p.snapshot().phase());assertTrue(p.requestFire(0,FIRST,501,sight(FIRST,501,POINT),READY).isEmpty());assertFalse(p.bindFutureIdentity(0,UUID.randomUUID()));
        }
    }
    @Test void invalidOrExhaustedClockCannotCreateAShortenedWarning(){
        assertThrows(IllegalArgumentException.class,()->new SiegePlan.Observation(SITE,FIRST,OWNER,"not-a-world",100,POINT,null,null));
        var p=siege();assertThrows(IllegalArgumentException.class,()->p.advance(499,0,true,true,null,false));
        long now=Long.MAX_VALUE-5;var at=sight(OBSERVER,now,POINT);var s=new SiegePlan.Snapshot(ID,OWNER,WORLD,SiegePlan.Phase.PLANNED,now,now,GRACE_TICKS,now,now,0,0,0,at,at,new SiegePlan.Bindings(null,null),List.of());
        var nearEnd=new SiegePlan(s);nearEnd.advance(now,0,true,true,null,false);assertEquals(SiegePlan.Phase.CLOSED,nearEnd.snapshot().phase());
    }
}
