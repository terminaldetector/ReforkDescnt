package com.terminaldetector.drmd.world.threat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Save/authority controls only. These do not prove runtime natural AI or ten real online minutes. */
class ReconStateTest {
    private static final Point AT=new Point(40,-58,-70),SEEN=new Point(45,-57,-68);
    private static final ReconAdmission.Context WORLD=new ReconAdmission.Context("minecraft:overworld",7,"STOCK",-784,1888);
    private static final UUID OWNER=UUID.randomUUID(),SCOUT=UUID.randomUUID();
    private static ThreatProgress ready(){return new ThreatProgress(new Snapshot(Phase.QUIET,GRACE_TICKS,0,0,0,-1,-1,false,0,AT,AT));}
    private static SurvivalThreatState state(){var s=new SurvivalThreatState();s.restorePlayer(OWNER,ready());return s;}
    private static SurvivalThreatState scouting(){var s=state();assertTrue(s.reserveScout(OWNER,SCOUT,WORLD,AT,100));return s;}
    private static SurvivalThreatState observed(){var s=scouting();assertTrue(s.observeScout(OWNER,SCOUT,WORLD,SEEN,110));s.player(OWNER).advance(110,0,true,SEEN,false);return s;}
    private static SurvivalThreatState searching(){var s=observed();s.player(OWNER).advance(510,0,true,null,false);assertEquals(Phase.SEARCHING,s.progress(OWNER).orElseThrow().phase());return s;}
    private static NbtCompound write(SurvivalThreatState s){return s.writeNbt(new NbtCompound(),null);}
    private static SurvivalThreatState read(NbtCompound n){return SurvivalThreatState.fromNbt(n,null);}
    private static NbtCompound record(NbtCompound n){return n.getCompound("recon").getCompound("attempts").getCompound(OWNER.toString());}
    @Test void missingExtensionRemainsHealthyLegacyAndKeepsTwoKillPatrolSlots(){
        var s=state();assertFalse(write(s).contains("recon"));s=read(write(s));assertTrue(s.reconAvailable());
        assertTrue(s.alarm(OWNER,UUID.randomUUID(),AT,100));s.player(OWNER).advance(100,0,true,null,false);s.player(OWNER).advance(500,0,true,null,false);
        assertTrue(s.addActor(OWNER,UUID.randomUUID(),5000));assertTrue(s.addActor(OWNER,UUID.randomUUID(),5000));
        assertFalse(s.addActor(OWNER,UUID.randomUUID(),5000));assertEquals(2,s.rosterSize(OWNER));
    }
    @Test void novicePendingAlarmCooldownAndOutOfContextCannotAdmitScout(){
        var s=new SurvivalThreatState();s.restorePlayer(OWNER,new ThreatProgress());assertFalse(s.reserveScout(OWNER,SCOUT,WORLD,AT,100));
        s.restorePlayer(OWNER,ready());assertFalse(s.reserveScout(OWNER,SCOUT,WORLD,new Point(0,1888,0),100));assertTrue(s.alarm(OWNER,UUID.randomUUID(),AT,100));
        assertFalse(s.reserveScout(OWNER,SCOUT,WORLD,AT,100));s.player(OWNER).advance(100,0,true,null,false);s.cancel(OWNER,101);
        assertEquals(Phase.COOLDOWN,s.progress(OWNER).orElseThrow().phase());assertFalse(s.canScout(OWNER));assertEquals(0,s.actorCount());
    }
    @Test void aRealReservedScoutCountsAgainstSharedGlobalAndPerOwnerCaps(){
        var s=scouting();assertFalse(s.reserveScout(OWNER,UUID.randomUUID(),WORLD,AT,101));
        for(int i=1;i<SurvivalThreatState.MAX_ACTORS;i++){var id=UUID.randomUUID();s.restorePlayer(id,ready());assertTrue(s.reserveScout(id,UUID.randomUUID(),WORLD,AT,100));}
        var ninth=UUID.randomUUID();s.restorePlayer(ninth,ready());assertFalse(s.reserveScout(ninth,UUID.randomUUID(),WORLD,AT,100));
        assertEquals(8,s.actorCount());assertEquals(1,s.rosterSize(OWNER));
    }
    @Test void unloadedScoutIdentityAndAttemptPurposeRemainReservedAcrossSave(){
        var s=scouting();var before=s.reconProgress(OWNER).orElseThrow();var n=write(s);s=read(n);
        assertTrue(s.reconAvailable());assertEquals(before,s.reconProgress(OWNER).orElseThrow());assertTrue(s.hasActor(SCOUT));
        assertEquals("SCOUT_SUPPORT",record(n).getString("scoutPurpose"));assertFalse(s.canScout(OWNER));assertEquals(1,s.actorCount());
    }
    @Test void unseenInterceptionClosesAttemptIntoSameSavedCooldown(){
        var s=scouting();s.actorDied(SCOUT,120);var p=s.progress(OWNER).orElseThrow();
        assertEquals(Phase.COOLDOWN,p.phase());assertEquals(120+COOLDOWN_TICKS,p.until());assertTrue(s.reconProgress(OWNER).orElseThrow().closed());assertEquals(0,s.actorCount());
        var restored=read(write(s));assertEquals(p,restored.progress(OWNER).orElseThrow());assertFalse(restored.canScout(OWNER));
        restored.player(OWNER).advance(p.until()-1,0,true,null,true);assertFalse(restored.canScout(OWNER));
        restored.player(OWNER).advance(p.until(),0,true,null,true);assertTrue(restored.canScout(OWNER));
    }
    @Test void wrongSourceOrWorldCannotConvertTimeAloneIntoObservedWarning(){
        var s=scouting();assertFalse(s.observeScout(OWNER,UUID.randomUUID(),WORLD,SEEN,110));
        assertFalse(s.observeScout(OWNER,SCOUT,new ReconAdmission.Context("minecraft:the_nether",7,"STOCK",-784,1888),SEEN,110));
        assertFalse(s.observeScout(OWNER,SCOUT,WORLD,null,110));assertFalse(s.progress(OWNER).orElseThrow().pending());assertFalse(s.reconProgress(OWNER).orElseThrow().observed());
        assertTrue(s.observeScout(OWNER,SCOUT,WORLD,SEEN,110));s.player(OWNER).advance(110,0,true,SEEN,false);
        assertEquals(Phase.WARNING,s.progress(OWNER).orElseThrow().phase());assertEquals(510,s.progress(OWNER).orElseThrow().until());
    }
    @Test void permanentAssaultAllowanceSurvivesScoutDeathSpawnCounterResetAndColdCodec(){
        var s=observed();assertFalse(s.reserveTimedAssault(OWNER,UUID.randomUUID(),509));
        s.player(OWNER).advance(510,0,true,null,false);s.actorDied(SCOUT,511);var assault=UUID.randomUUID();assertTrue(s.reserveTimedAssault(OWNER,assault,511));
        s.actorDied(assault,512);var p=s.progress(OWNER).orElseThrow();
        s.restorePlayer(OWNER,new ThreatProgress(new Snapshot(p.phase(),p.played(),p.since(),p.until(),p.encounterEnds(),p.alarmAt(),p.lastSeen(),p.pending(),0,p.origin(),p.known())));
        var restored=read(write(s));assertTrue(restored.reconProgress(OWNER).orElseThrow().assaultIssued());assertEquals(assault,restored.reconProgress(OWNER).orElseThrow().assault());
        assertFalse(restored.canDeploy(OWNER));assertFalse(restored.reserveTimedAssault(OWNER,UUID.randomUUID(),513));assertEquals(0,restored.actorCount());
    }
    @Test void fixedTimedExpiryCannotBecomeAnEndlessVisibilityExtension(){
        var s=searching();long fixed=s.reconProgress(OWNER).orElseThrow().expires();var assault=UUID.randomUUID();assertTrue(s.reserveTimedAssault(OWNER,assault,510));
        s.extendActor(SCOUT,Long.MAX_VALUE);s.extendActor(assault,Long.MAX_VALUE);assertEquals(fixed,s.actor(SCOUT).expires());assertEquals(fixed,s.actor(assault).expires());
        s.retireTimed(OWNER,600);assertEquals(800,s.actor(SCOUT).expires());assertEquals(800,s.actor(assault).expires());
        assertEquals(Phase.COOLDOWN,s.progress(OWNER).orElseThrow().phase());assertFalse(s.canDeploy(OWNER));
    }
    @Test void failedAdmissionRollbackCannotEraseAnIssuedCombatSlot(){
        var s=scouting();s.rejectUnspawnedScout(OWNER,UUID.randomUUID());assertEquals(1,s.actorCount());
        s.rejectUnspawnedScout(OWNER,SCOUT);assertEquals(0,s.actorCount());assertTrue(s.canScout(OWNER));
        s=searching();var assault=UUID.randomUUID();assertTrue(s.reserveTimedAssault(OWNER,assault,510));s.removeActor(assault);
        s.rejectUnspawnedScout(OWNER,SCOUT);assertTrue(s.reconProgress(OWNER).orElseThrow().assaultIssued());assertFalse(s.canDeploy(OWNER));
    }
    @Test void unknownReconIsPreservedAndCannotResetRosterCooldownOrMintAReplacement(){
        var n=write(scouting());var raw=n.getCompound("recon").copy();raw.putInt("schema",9);raw.putString("future","keep");n.put("recon",raw);
        var s=read(n);var old=s.progress(OWNER).orElseThrow();assertTrue(s.available());assertFalse(s.reconAvailable());
        s.cancel(OWNER,200);s.removeActor(SCOUT);s.extendActor(SCOUT,9999);assertFalse(s.alarm(OWNER,UUID.randomUUID(),AT,200));
        assertFalse(s.canScout(OWNER));assertFalse(s.canDeploy(OWNER));assertEquals(1,s.actorCount());assertEquals(old,s.progress(OWNER).orElseThrow());
        assertEquals(raw,write(s).get("recon"));assertEquals(n.get("actors"),write(s).get("actors"));
    }
    @Test void wholeFutureStateRemainsOpaqueIncludingItsCooldownRosterAndSiegePlans(){
        var n=write(observed());n.putInt("schema",2);var siege=new NbtCompound();siege.putString("future","preserve");n.put("siegePlans",siege);n.putString("newRootField","also preserve");
        var s=read(n);assertFalse(s.available());assertFalse(s.reconAvailable());assertTrue(s.siegePlansReadOnly());
        assertFalse(s.alarm(OWNER,UUID.randomUUID(),AT,200));s.cancel(OWNER,200);s.removeActor(SCOUT);assertNull(s.player(UUID.randomUUID()));
        assertEquals(n,write(s));assertFalse(s.reserveScout(OWNER,UUID.randomUUID(),WORLD,AT,200));
    }
    @Test void corruptReconAndOpaqueOtherExtensionsAreNotSilentlyReplacedByDefaults(){
        Consumer<NbtCompound>[] corruptions=new Consumer[]{
            (Consumer<NbtCompound>)(p->p.putLong("started",-1)),(Consumer<NbtCompound>)(p->p.putByte("assaultIssued",(byte)2)),
            (Consumer<NbtCompound>)(p->p.putString("scoutPurpose","ARTILLERY")),(Consumer<NbtCompound>)(p->p.putUuid("attempt",SCOUT)),
            (Consumer<NbtCompound>)(p->p.putString("unrecognized","do not drop"))};
        for(var corrupt:corruptions){var n=write(scouting());corrupt.accept(record(n));var raw=n.get("recon").copy();var s=read(n);assertFalse(s.reconAvailable());assertEquals(raw,write(s).get("recon"));}
        var n=write(scouting());n.put("recon",NbtString.of("opaque wrong type"));assertEquals(n.get("recon"),write(read(n)).get("recon"));
        n=write(scouting());var siege=new NbtCompound();siege.putInt("schema",7);siege.putString("opaque","unchanged");n.put("siegePlans",siege);
        assertTrue(read(n).reconAvailable());assertTrue(read(n).siegePlansReadOnly());assertEquals(siege,write(read(n)).get("siegePlans"));
    }
}
