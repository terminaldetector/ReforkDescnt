package com.terminaldetector.drmd.world.threat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

class ReconAdmissionTest {
    private static final UUID OWNER=UUID.randomUUID(),SCOUT=UUID.randomUUID();
    private static final ReconAdmission.Context WORLD=new ReconAdmission.Context("minecraft:overworld",91,"STOCK",-784,1888);
    private static final Point AT=new Point(30,-58,7),SEEN=new Point(42,-54,9);
    private static ReconAdmission attempt(){return new ReconAdmission(UUID.randomUUID(),OWNER,WORLD,AT,SCOUT,100);}
    @Test void unseenAttemptHasOneFixedExpiryAndCannotIssueWeapons(){
        var r=attempt();assertEquals(100+SEARCH_TICKS,r.snapshot().expires());assertFalse(r.mayIssue(101));
        assertFalse(r.observe(99,SEEN));assertFalse(r.observe(r.snapshot().expires(),SEEN));assertFalse(r.snapshot().observed());
        assertTrue(r.close());assertFalse(r.close());assertFalse(r.observe(101,SEEN));assertFalse(r.issue(UUID.randomUUID(),101));
    }
    @Test void firstObservationPinsDeadlineAndReacquisitionCannotExtendIt(){
        var r=attempt();assertTrue(r.observe(150,SEEN));long fixed=r.snapshot().expires();
        assertEquals(150+WARNING_TICKS+SEARCH_TICKS+HUNT_TICKS,fixed);assertEquals(SEEN,r.snapshot().origin());
        assertFalse(r.mayIssue(549));assertTrue(r.mayIssue(550));
        assertFalse(r.observe(160,AT));assertEquals(fixed,r.snapshot().expires());
        var restored=new ReconAdmission(r.snapshot());assertEquals(fixed,restored.snapshot().expires());assertFalse(restored.observe(170,AT));
    }
    @Test void issuedAssaultIdentityStaysConsumedAcrossSnapshotAndClose(){
        var r=attempt();r.observe(150,SEEN);var combat=UUID.randomUUID();assertTrue(r.issue(combat,550));
        var copy=new ReconAdmission(r.snapshot());assertFalse(copy.issue(UUID.randomUUID(),551));assertEquals(combat,copy.snapshot().assault());
        copy.close();assertTrue(copy.snapshot().assaultIssued());assertEquals(combat,copy.snapshot().assault());assertFalse(copy.mayIssue(552));
    }
    @Test void impossibleContextsAliasesAndTruncatedOverflowWarningsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new ReconAdmission.Context("bad world",0,"STOCK",0,1));
        assertThrows(IllegalArgumentException.class,()->new ReconAdmission.Context("minecraft:overworld",0,"UNSUPPORTED",0,1));
        assertThrows(IllegalArgumentException.class,()->new ReconAdmission(UUID.randomUUID(),OWNER,WORLD,new Point(0,1888,0),SCOUT,0));
        assertThrows(IllegalArgumentException.class,()->new ReconAdmission(UUID.randomUUID(),OWNER,WORLD,AT,OWNER,0));
        assertThrows(IllegalArgumentException.class,()->new ReconAdmission(UUID.randomUUID(),OWNER,WORLD,AT,SCOUT,Long.MAX_VALUE-10));
    }
}
