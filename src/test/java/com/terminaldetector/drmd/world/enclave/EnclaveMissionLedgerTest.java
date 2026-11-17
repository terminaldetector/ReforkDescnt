package com.terminaldetector.drmd.world.enclave;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionContract.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionLedger.*;

class EnclaveMissionLedgerTest {
    private static final Context CONTEXT=new Context("minecraft:overworld",17,"NATIVE_CUBIC",-100_000,100_000);
    private static final Point HERALD=new Point(0,65_537,0), ANCHOR=new Point(10,65_537,0);
    private static final Point OUTSIDE=new Point(8,65_537,0), INSIDE=new Point(10,65_537,0), PANEL=new Point(13,65_537,0);
    private static final EnclaveMissionContract CONTRACT=new EnclaveMissionContract("drmd:lab_infiltration",1,
        "EXPLORE_SHAFT","quest.drmd.explore_shaft",1,List.of(
            new Step(Type.ENTER_VOLUME,new Box(new Point(0,0,0),new Point(2,2,2)),null,null),
            new Step(Type.BREAK_BLOCK,null,new Point(3,0,0),"minecraft:iron_block")));
    private static final String HASH=CONTRACT.hash();
    private static final UUID OWNER=UUID.fromString("1d8dbe77-3c95-430e-b252-14f5ca8e0d8e");
    private static Site bind(EnclaveMissionLedger ledger) { return ledger.bind(CONTEXT,HERALD,ANCHOR,"miners",CONTRACT); }
    private static void ready(EnclaveMissionLedger ledger,Site site) {
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,site.id(),CONTEXT,HASH,OUTSIDE));
        assertTrue(ledger.observePosition(OWNER,CONTEXT,HASH,INSIDE));
        assertTrue(ledger.observeBreak(OWNER,CONTEXT,HASH,PANEL,"minecraft:iron_block",INSIDE));
        assertEquals(Outcome.READY,ledger.status(OWNER,site.id(),CONTEXT,HASH));
    }
    @Test void ordinarySequenceRequiresAttributedOrderedEventsAndExplicitClaim() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,site.id(),CONTEXT,HASH,OUTSIDE));
        assertFalse(ledger.claim(OWNER,site.id(),CONTEXT,HASH));
        assertFalse(ledger.observeBreak(OWNER,CONTEXT,HASH,PANEL,"minecraft:iron_block",INSIDE));
        assertFalse(ledger.observePosition(UUID.randomUUID(),CONTEXT,HASH,INSIDE));
        assertTrue(ledger.observePosition(OWNER,CONTEXT,HASH,INSIDE));
        assertFalse(ledger.observeBreak(OWNER,CONTEXT,HASH,PANEL,"minecraft:stone",INSIDE));
        assertFalse(ledger.observeBreak(OWNER,CONTEXT,HASH,new Point(13,1,0),"minecraft:iron_block",INSIDE));
        assertTrue(ledger.observeBreak(OWNER,CONTEXT,HASH,PANEL,"minecraft:iron_block",INSIDE));
        assertFalse(ledger.observeBreak(OWNER,CONTEXT,HASH,PANEL,"minecraft:iron_block",INSIDE));
        assertTrue(ledger.claim(OWNER,site.id(),CONTEXT,HASH));
        assertFalse(ledger.claim(OWNER,site.id(),CONTEXT,HASH));
        assertEquals(Outcome.CLAIMED,ledger.accept(OWNER,site.id(),CONTEXT,HASH,OUTSIDE));
        assertTrue(ledger.activeOwners().isEmpty());
    }
    @Test void acceptingInsideDoesNotInferEntryAndPauseRequiresFreshOutsideEdge() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);
        ledger.accept(OWNER,site.id(),CONTEXT,HASH,INSIDE);
        assertFalse(ledger.observePosition(OWNER,CONTEXT,HASH,INSIDE));
        assertFalse(ledger.observePosition(OWNER,CONTEXT,HASH,OUTSIDE));
        ledger.pause(OWNER);
        assertFalse(ledger.observePosition(OWNER,CONTEXT,HASH,INSIDE));
        assertFalse(ledger.observePosition(OWNER,CONTEXT,HASH,OUTSIDE));
        assertTrue(ledger.observePosition(OWNER,CONTEXT,HASH,INSIDE));
    }
    @Test void contextSeedBackendBoundsDimensionAndDefinitionAreAllAuthoritative() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);
        ledger.accept(OWNER,site.id(),CONTEXT,HASH,OUTSIDE);
        for(var foreign:List.of(new Context("minecraft:the_end",17,"NATIVE_CUBIC",-100_000,100_000),
            new Context(CONTEXT.dimension(),18,CONTEXT.backend(),CONTEXT.bottomY(),CONTEXT.topY()),
            new Context(CONTEXT.dimension(),17,"VANILLA",CONTEXT.bottomY(),CONTEXT.topY()),
            new Context(CONTEXT.dimension(),17,CONTEXT.backend(),-99_999,100_000))) {
            assertEquals(Outcome.BLOCKED,ledger.status(OWNER,site.id(),foreign,HASH));
            assertFalse(ledger.observePosition(OWNER,foreign,HASH,INSIDE));
            assertFalse(ledger.claim(OWNER,site.id(),foreign,HASH));
        }
        assertFalse(ledger.observePosition(OWNER,CONTEXT,"changed-definition",INSIDE));
        assertEquals(0,ledger.active(OWNER).orElseThrow().cursor());
        ledger.observePosition(OWNER,CONTEXT,HASH,OUTSIDE);
        assertTrue(ledger.observePosition(OWNER,CONTEXT,HASH,INSIDE));
    }
    @Test void coldDtoReloadRetainsCursorButDoesNotInferAnEntryEdge() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);
        ledger.accept(OWNER,site.id(),CONTEXT,HASH,OUTSIDE);
        var loaded=EnclaveMissionLedger.read(ledger.write());
        assertTrue(loaded.available());assertEquals(ledger.namespace(),loaded.namespace());
        assertFalse(loaded.observePosition(OWNER,CONTEXT,HASH,INSIDE));
        loaded.observePosition(OWNER,CONTEXT,HASH,OUTSIDE);
        assertTrue(loaded.observePosition(OWNER,CONTEXT,HASH,INSIDE));
        var afterEntry=EnclaveMissionLedger.read(loaded.write());
        assertEquals(1,afterEntry.active(OWNER).orElseThrow().cursor());
        assertTrue(afterEntry.observeBreak(OWNER,CONTEXT,HASH,PANEL,"minecraft:iron_block",INSIDE));
    }
    @Test void claimedTombstoneSurvivesSerializationAndCannotBeReboundOrReset() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);ready(ledger,site);
        assertTrue(ledger.claim(OWNER,site.id(),CONTEXT,HASH));
        var loaded=EnclaveMissionLedger.read(ledger.write());
        assertEquals(Outcome.CLAIMED,loaded.status(OWNER,site.id(),CONTEXT,HASH));
        assertFalse(loaded.claim(OWNER,site.id(),CONTEXT,HASH));
        assertThrows(IllegalStateException.class,()->bind(loaded));
        assertTrue(loaded.invalidate(CONTEXT.dimension(),HERALD));
        assertFalse(loaded.invalidate(CONTEXT.dimension(),HERALD));
        assertThrows(IllegalStateException.class,()->bind(loaded));
        assertTrue(EnclaveMissionLedger.read(loaded.write()).available());
    }
    @Test void invalidationKeepsIdentityAndFreesOnlyTheActiveOwnerSlot() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);
        ledger.accept(OWNER,site.id(),CONTEXT,HASH,OUTSIDE);
        assertTrue(ledger.invalidate(CONTEXT.dimension(),HERALD));
        assertTrue(ledger.activeOwners().isEmpty());
        assertEquals(Outcome.BLOCKED,ledger.status(OWNER,site.id(),CONTEXT,HASH));
        assertFalse(ledger.claim(OWNER,site.id(),CONTEXT,HASH));
        var other=ledger.bind(CONTEXT,new Point(30,65_537,0),new Point(40,65_537,0),"miners",CONTRACT);
        assertEquals(Outcome.ACCEPTED,ledger.accept(OWNER,other.id(),CONTEXT,HASH,OUTSIDE));
        assertTrue(EnclaveMissionLedger.read(ledger.write()).available());
    }
    @Test void signedCoordinatesDoNotAliasAtPackedPositionHeights() {
        var ledger=new EnclaveMissionLedger();var high=bind(ledger);
        var low=ledger.bind(CONTEXT,new Point(0,1,0),new Point(10,1,0),"miners",CONTRACT);
        assertNotEquals(high.id(),low.id());
        var loaded=EnclaveMissionLedger.read(ledger.write());
        assertEquals(high,loaded.site(CONTEXT.dimension(),HERALD).orElseThrow());
        assertEquals(low,loaded.site(CONTEXT.dimension(),new Point(0,1,0)).orElseThrow());
        assertThrows(ArithmeticException.class,()->ledger.bind(CONTEXT,new Point(90,0,0),new Point(Integer.MAX_VALUE,0,0),"miners",CONTRACT));
        assertThrows(IllegalArgumentException.class,()->ledger.bind(CONTEXT,new Point(91,0,0),new Point(1,100_000,0),"miners",CONTRACT));
    }
    @Test void boundedAdmissionNeverEvictsOwnersOrClaimedRecords() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);
        for(int i=0;i<MAX_OWNERS;i++)assertEquals(Outcome.ACCEPTED,ledger.accept(new UUID(0,i),site.id(),CONTEXT,HASH,OUTSIDE));
        assertEquals(Outcome.CAPACITY,ledger.accept(UUID.randomUUID(),site.id(),CONTEXT,HASH,OUTSIDE));
        var second=ledger.bind(CONTEXT,new Point(30,65_537,0),new Point(40,65_537,0),"miners",CONTRACT);
        assertEquals(Outcome.OWNER_BUSY,ledger.accept(new UUID(0,0),second.id(),CONTEXT,HASH,OUTSIDE));
        assertEquals(MAX_OWNERS,EnclaveMissionLedger.read(ledger.write()).activeOwners().size());
        assertThrows(UnsupportedOperationException.class,()->ledger.activeOwners().clear());
        for(int i=2;i<MAX_SITES;i++)ledger.bind(CONTEXT,new Point(i*100,65_537,0),new Point(i*100+10,65_537,0),"miners",CONTRACT);
        assertThrows(IllegalStateException.class,()->ledger.bind(CONTEXT,new Point(9000,0,0),new Point(9010,0,0),"miners",CONTRACT));
    }
    @Test void futureMalformedAndDuplicateStateRemainOpaqueAndCannotReward() {
        var ledger=new EnclaveMissionLedger();var site=bind(ledger);ready(ledger,site);
        var original=(NbtCompound)ledger.write();
        var variants=new ArrayList<NbtElement>();
        var future=original.copy();future.putInt("schema",2);variants.add(future);
        var numeric=original.copy();numeric.putString("schema","1");variants.add(numeric);
        var duplicate=original.copy();var list=(NbtList)duplicate.get("instances");list.add(list.get(0).copy());variants.add(duplicate);
        var origin=original.copy();((NbtList)origin.get("sites")).getCompound(0).putString("origin","unknown-origin");variants.add(origin);
        var orphan=original.copy();((NbtList)orphan.get("instances")).getCompound(0).putUuid("site",UUID.randomUUID());variants.add(orphan);
        variants.add(NbtString.of("unexpected-extension"));
        for(var raw:variants) {
            var loaded=EnclaveMissionLedger.read(raw);assertFalse(loaded.available());assertTrue(loaded.problem().isPresent());
            assertEquals(raw,loaded.write());assertFalse(loaded.claim(OWNER,site.id(),CONTEXT,HASH));
            assertThrows(IllegalStateException.class,()->bind(loaded));
        }
    }
    @Test void sameFactionStateCommitsClaimAndClampedReputationExactlyOnce() {
        var memory=new FactionMemory();var ledger=memory.missions();var site=bind(ledger);ready(ledger,site);
        memory.addRep(OWNER,EnclaveOrigin.MINERS,99);
        assertFalse(memory.claimMission(UUID.randomUUID(),site.id(),CONTEXT,HASH));
        assertTrue(memory.claimMission(OWNER,site.id(),CONTEXT,HASH));
        assertEquals(100,memory.getRep(OWNER,EnclaveOrigin.MINERS));
        var saved=memory.writeNbt(new NbtCompound(),null);
        var loaded=FactionMemory.fromNbt(saved,null);
        assertEquals(100,loaded.getRep(OWNER,EnclaveOrigin.MINERS));
        assertEquals(Outcome.CLAIMED,loaded.missions().status(OWNER,site.id(),CONTEXT,HASH));
        assertFalse(loaded.claimMission(OWNER,site.id(),CONTEXT,HASH));
        assertEquals(100,loaded.getRep(OWNER,EnclaveOrigin.MINERS));
    }
    @Test void invalidObservationCannotPartiallyAdvanceOrLeaveUndirtiedState() {
        var contract=new EnclaveMissionContract("drmd:three_steps",1,"EXPLORE_SHAFT","quest.drmd.explore_shaft",1,
            List.of(CONTRACT.objectives().getFirst(),CONTRACT.objectives().get(1),
                new Step(Type.ENTER_VOLUME,new Box(new Point(5,0,0),new Point(7,2,2)),null,null)));
        var ledger=new EnclaveMissionLedger();var site=ledger.bind(CONTEXT,HERALD,ANCHOR,"miners",contract);
        ledger.accept(OWNER,site.id(),CONTEXT,contract.hash(),OUTSIDE);
        ledger.observePosition(OWNER,CONTEXT,contract.hash(),INSIDE);
        long version=ledger.mutationVersion();
        assertThrows(NullPointerException.class,()->ledger.observeBreak(OWNER,CONTEXT,contract.hash(),PANEL,"minecraft:iron_block",null));
        assertEquals(version,ledger.mutationVersion());assertEquals(1,ledger.active(OWNER).orElseThrow().cursor());
    }
    @Test void extremeLegacyReputationCannotWrapWhenApplyingClaimReward() {
        for(int oldValue:new int[]{Integer.MAX_VALUE,Integer.MIN_VALUE}) {
            var memory=new FactionMemory();var site=bind(memory.missions());ready(memory.missions(),site);
            var tag=memory.writeNbt(new NbtCompound(),null);
            var player=new NbtCompound();var rep=new NbtCompound();rep.putInt("miners",oldValue);player.put("rep",rep);
            var players=new NbtCompound();players.put(OWNER.toString(),player);tag.put("players",players);
            var loaded=FactionMemory.fromNbt(tag,null);
            assertTrue(loaded.claimMission(OWNER,site.id(),CONTEXT,HASH));
            assertEquals(oldValue>0?100:-100,loaded.getRep(OWNER,EnclaveOrigin.MINERS));
            assertFalse(loaded.claimMission(OWNER,site.id(),CONTEXT,HASH));
        }
    }
    @Test void legacyFactionDataAndFutureMissionExtensionSurviveTogether() {
        var legacy=new FactionMemory();legacy.addRep(OWNER,EnclaveOrigin.ENGINEERS,12);legacy.markBrokeControl(OWNER);
        var saved=legacy.writeNbt(new NbtCompound(),null);assertFalse(saved.contains("missions"));
        var future=new NbtCompound();future.putInt("schema",999);future.putString("opaque","preserve me");saved.put("missions",future);
        var loaded=FactionMemory.fromNbt(saved,null);
        assertFalse(loaded.missions().available());assertEquals(12,loaded.getRep(OWNER,EnclaveOrigin.ENGINEERS));
        assertTrue(loaded.brokeControl(OWNER));loaded.addRep(OWNER,EnclaveOrigin.ENGINEERS,1);
        assertEquals(future,loaded.writeNbt(new NbtCompound(),null).get("missions"));
        assertEquals(13,loaded.getRep(OWNER,EnclaveOrigin.ENGINEERS));
    }
}
