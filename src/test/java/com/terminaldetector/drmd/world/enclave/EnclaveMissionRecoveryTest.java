package com.terminaldetector.drmd.world.enclave;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionContract.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionLedger.*;

class EnclaveMissionRecoveryTest {
    private static final UUID OWNER=new UUID(4,9),OTHER=new UUID(4,10);
    private static final Context WORLD=new Context("minecraft:overworld",42,"NATIVE_CUBIC",-100_000,100_000);
    private static final Point OUTSIDE=new Point(0,65537,0);
    private static final EnclaveMissionContract CONTRACT=new EnclaveMissionContract("drmd:lab_infiltration",1,"EXPLORE_SHAFT","quest.drmd.explore_shaft",1,List.of(
        new Step(Type.ENTER_VOLUME,new Box(new Point(0,0,0),new Point(2,2,2)),null,null),
        new Step(Type.BREAK_BLOCK,null,new Point(3,0,0),"minecraft:iron_block")));
    private static final String HASH=CONTRACT.hash();
    private static Site bind(EnclaveMissionLedger ledger,int x){return ledger.bind(WORLD,new Point(x,65537,0),new Point(x+10,65537,0),"miners",CONTRACT);}
    private static void ready(EnclaveMissionLedger ledger,Site site){
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,site.id(),WORLD,HASH,OUTSIDE));
        assertTrue(ledger.observePosition(OWNER,WORLD,HASH,site.anchor()));
        assertTrue(ledger.observeBreak(OWNER,WORLD,HASH,site.anchor().add(new Point(3,0,0)),"minecraft:iron_block",site.anchor()));
    }
    @Test void withdrawalReleasesBlockedOwnerSlotWithoutReplacingAuthoredSites(){
        var ledger=new EnclaveMissionLedger();var first=bind(ledger,0);var second=bind(ledger,40);
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,first.id(),WORLD,HASH,OUTSIDE));
        assertTrue(ledger.observePosition(OWNER,WORLD,HASH,first.anchor()));
        assertEquals(Outcome.OWNER_BUSY,ledger.accept(OWNER,second.id(),WORLD,HASH,OUTSIDE));
        long version=ledger.mutationVersion();assertTrue(ledger.abandon(OWNER,WORLD));assertEquals(version+1,ledger.mutationVersion());
        assertTrue(ledger.active(OWNER).isEmpty());assertEquals(Outcome.UNAVAILABLE,ledger.status(OWNER,first.id(),WORLD,HASH));
        assertEquals(first,ledger.site(first.id()).orElseThrow());assertEquals(second,ledger.site(second.id()).orElseThrow());
        assertFalse(ledger.abandon(OWNER,WORLD));assertEquals(version+1,ledger.mutationVersion());
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,second.id(),WORLD,HASH,OUTSIDE));
    }
    @Test void foreignOwnerAndChangedWorldCannotDeleteRealOwnerProgress(){
        var ledger=new EnclaveMissionLedger();var site=bind(ledger,0);ledger.accept(OWNER,site.id(),WORLD,HASH,OUTSIDE);
        String before=ledger.write().toString();
        assertFalse(ledger.abandon(OTHER,WORLD));
        for(var foreign:List.of(new Context("minecraft:the_end",42,WORLD.backend(),WORLD.bottomY(),WORLD.topY()),
            new Context(WORLD.dimension(),43,WORLD.backend(),WORLD.bottomY(),WORLD.topY()),
            new Context(WORLD.dimension(),42,"STOCK",WORLD.bottomY(),WORLD.topY()),
            new Context(WORLD.dimension(),42,WORLD.backend(),WORLD.bottomY()+1,WORLD.topY())))assertFalse(ledger.abandon(OWNER,foreign));
        assertEquals(before,ledger.write().toString());assertEquals(0,ledger.active(OWNER).orElseThrow().cursor());
    }
    @Test void readyButUnclaimedWithdrawalNeverAwardsReputation(){
        var memory=new FactionMemory();var ledger=memory.missions();var site=bind(ledger,0);ready(ledger,site);
        assertEquals(Outcome.READY,ledger.status(OWNER,site.id(),WORLD,HASH));
        assertTrue(ledger.abandon(OWNER,WORLD));assertFalse(memory.claimMission(OWNER,site.id(),WORLD,HASH));
        assertEquals(0,memory.getRep(OWNER,EnclaveOrigin.MINERS));
        var restored=FactionMemory.fromNbt(memory.writeNbt(new NbtCompound(),null),null);
        assertEquals(0,restored.getRep(OWNER,EnclaveOrigin.MINERS));assertTrue(restored.missions().activeOwners().isEmpty());
        assertEquals(Outcome.UNAVAILABLE,restored.missions().status(OWNER,site.id(),WORLD,HASH));
    }
    @Test void cancellingAnotherActiveMissionCannotResetAnExistingClaimReceipt(){
        var memory=new FactionMemory();var ledger=memory.missions();var first=bind(ledger,0);var second=bind(ledger,40);ready(ledger,first);
        assertTrue(memory.claimMission(OWNER,first.id(),WORLD,HASH));assertEquals(3,memory.getRep(OWNER,EnclaveOrigin.MINERS));
        ledger.accept(OWNER,second.id(),WORLD,HASH,OUTSIDE);assertTrue(ledger.abandon(OWNER,WORLD));
        assertEquals(Outcome.CLAIMED,ledger.status(OWNER,first.id(),WORLD,HASH));assertFalse(ledger.abandon(OWNER,WORLD));
        assertFalse(memory.claimMission(OWNER,first.id(),WORLD,HASH));assertEquals(Outcome.CLAIMED,ledger.accept(OWNER,first.id(),WORLD,HASH,OUTSIDE));
        var raw=memory.writeNbt(new NbtCompound(),null);assertEquals(1,raw.getCompound("missions").getInt("schema"));
        var restored=FactionMemory.fromNbt(raw,null);assertEquals(3,restored.getRep(OWNER,EnclaveOrigin.MINERS));assertFalse(restored.claimMission(OWNER,first.id(),WORLD,HASH));
        assertEquals(first,restored.missions().site(first.id()).orElseThrow());assertEquals(second,restored.missions().site(second.id()).orElseThrow());
    }
    @Test void repeatedUnclaimedWithdrawalsDoNotLeakTheFiniteOwnerOrInstanceBudget(){
        var ledger=new EnclaveMissionLedger();var site=bind(ledger,0);
        for(int i=0;i<MAX_OWNERS+32;i++){
            var owner=new UUID(8,i);assertEquals(Outcome.ACCEPTED,ledger.accept(owner,site.id(),WORLD,HASH,OUTSIDE));assertTrue(ledger.abandon(owner,WORLD));
        }
        var raw=(NbtCompound)ledger.write();assertEquals(0,raw.getList("instances",NbtElement.COMPOUND_TYPE).size());assertEquals(1,raw.getList("sites",NbtElement.COMPOUND_TYPE).size());
        assertTrue(ledger.activeOwners().isEmpty());assertTrue(EnclaveMissionLedger.read(raw).available());
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,site.id(),WORLD,HASH,OUTSIDE));
    }
    @Test void futureQuarantineAndMissingStateRemainUnchangedOnWithdrawal(){
        var empty=new EnclaveMissionLedger();assertFalse(empty.abandon(OWNER,WORLD));assertNull(empty.write());
        var ledger=new EnclaveMissionLedger();var site=bind(ledger,0);ledger.accept(OWNER,site.id(),WORLD,HASH,OUTSIDE);
        var future=((NbtCompound)ledger.write()).copy();future.putInt("schema",99);var loaded=EnclaveMissionLedger.read(future);
        assertFalse(loaded.available());assertFalse(loaded.abandon(OWNER,WORLD));assertEquals(future,loaded.write());
        var before=ledger.write();assertThrows(NullPointerException.class,()->ledger.abandon(null,WORLD));assertThrows(NullPointerException.class,()->ledger.abandon(OWNER,null));assertEquals(before,ledger.write());
    }
}
