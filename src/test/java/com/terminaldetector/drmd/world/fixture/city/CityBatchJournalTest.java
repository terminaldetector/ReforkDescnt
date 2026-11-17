package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real byte persistence and CAS-recovery decisions, without a Minecraft bootstrap. */
class CityBatchJournalTest {
    @TempDir Path directory;
    private static final CityBatchJournal.Identity OWNER=new CityBatchJournal.Identity(new UUID(17,42),"minecraft:overworld","a".repeat(64));
    private static final CityBlueprint.Point A=new CityBlueprint.Point(4,80,7),B=new CityBlueprint.Point(5,80,7);
    private static CityBatchJournal.Batch batch(){return new CityBatchJournal.Batch(0,0,2,List.of(
        new CityBatchJournal.Change(A,"minecraft:air","minecraft:stone_bricks"),
        new CityBatchJournal.Change(B,"minecraft:air","minecraft:iron_block")));}

    @Test void preparedIsDurableBeforeTheWorldCommitAndReopensAsPending() throws IOException {
        var file=directory.resolve("city.journal");
        try(var writer=CityBatchJournal.open(file,OWNER)){writer.prepare(batch());assertEquals(batch(),writer.snapshot().pending().orElseThrow());assertEquals(0,writer.snapshot().cursor());}
        try(var reader=CityBatchJournal.open(file,OWNER)){assertEquals(batch(),reader.snapshot().pending().orElseThrow());assertEquals(0,reader.snapshot().cursor());}
    }
    @Test void onlyDurableCommitAdvancesCursorAndRepeatedAcknowledgmentsAreIdempotent() throws IOException {
        var file=directory.resolve("city.journal");
        try(var writer=CityBatchJournal.open(file,OWNER)){
            writer.prepare(batch());long prepared=Files.size(file);writer.prepare(batch());assertEquals(prepared,Files.size(file));
            writer.commit(0);long committed=Files.size(file);writer.commit(0);assertEquals(committed,Files.size(file));assertEquals(2,writer.snapshot().cursor());assertTrue(writer.snapshot().pending().isEmpty());
        }
        try(var reader=CityBatchJournal.open(file,OWNER)){assertEquals(2,reader.snapshot().cursor());assertTrue(reader.snapshot().pending().isEmpty());}
    }
    @Test void manualRecoveryDistinguishesBeforeAfterMissingAndForeignOrMixedState(){
        var before=Map.of(A,"minecraft:air",B,"minecraft:air");var after=Map.of(A,"minecraft:stone_bricks",B,"minecraft:iron_block");
        assertEquals(CityBatchJournal.Recovery.RETRY,CityBatchJournal.reconcile(batch(),p->Optional.ofNullable(before.get(p))));
        assertEquals(CityBatchJournal.Recovery.ALREADY_COMMITTED,CityBatchJournal.reconcile(batch(),p->Optional.ofNullable(after.get(p))));
        assertEquals(CityBatchJournal.Recovery.UNAVAILABLE,CityBatchJournal.reconcile(batch(),p->Optional.empty()));
        var mixed=Map.of(A,"minecraft:stone_bricks",B,"minecraft:air");assertEquals(CityBatchJournal.Recovery.CONFLICT,CityBatchJournal.reconcile(batch(),p->Optional.ofNullable(mixed.get(p))));
        var foreign=Map.of(A,"minecraft:diamond_block",B,"minecraft:air");assertEquals(CityBatchJournal.Recovery.CONFLICT,CityBatchJournal.reconcile(batch(),p->Optional.ofNullable(foreign.get(p))));
    }
    @Test void wrongOwnerWorldOrPlanCannotReopenAndRewriteTheJournal() throws IOException {
        var file=directory.resolve("city.journal");try(var writer=CityBatchJournal.open(file,OWNER)){writer.prepare(batch());}byte[] bytes=Files.readAllBytes(file);
        for(var wrong:List.of(new CityBatchJournal.Identity(UUID.randomUUID(),OWNER.world(),OWNER.planHash()),new CityBatchJournal.Identity(OWNER.owner(),"minecraft:the_nether",OWNER.planHash()),new CityBatchJournal.Identity(OWNER.owner(),OWNER.world(),"b".repeat(64)))){
            assertThrows(IOException.class,()->CityBatchJournal.open(file,wrong));assertArrayEquals(bytes,Files.readAllBytes(file));
        }
    }
    @Test void incompleteTailIsQuarantinedWithoutAuthorizingReplayOrErasingBytes() throws IOException {
        var file=directory.resolve("city.journal");try(var writer=CityBatchJournal.open(file,OWNER)){writer.prepare(batch());}
        Files.write(file,new byte[]{1,2,3},StandardOpenOption.APPEND);byte[] damaged=Files.readAllBytes(file);
        try(var reader=CityBatchJournal.open(file,OWNER)){assertTrue(reader.snapshot().incompleteTail());assertThrows(IOException.class,()->reader.commit(0));}
        assertArrayEquals(damaged,Files.readAllBytes(file));
    }
    @Test void oversizedOrDuplicateBatchesAreRejectedBeforeJournalMutation() throws IOException {
        var file=directory.resolve("city.journal");try(var writer=CityBatchJournal.open(file,OWNER)){
            long empty=Files.size(file);var changes=new ArrayList<CityBatchJournal.Change>();for(int x=0;x<65;x++)changes.add(new CityBatchJournal.Change(new CityBlueprint.Point(x,80,0),"minecraft:air","minecraft:iron_block"));
            assertThrows(IllegalArgumentException.class,()->new CityBatchJournal.Batch(0,0,65,changes));
            assertThrows(IllegalArgumentException.class,()->new CityBatchJournal.Batch(0,0,2,List.of(batch().changes().getFirst(),batch().changes().getFirst())));assertEquals(empty,Files.size(file));
        }
    }
    @Test void absentParentIsNotSilentlyCreatedOrClaimedDurable(){
        var parent=directory.resolve("not-provisioned");
        assertThrows(IOException.class,()->CityBatchJournal.open(parent.resolve("city.journal"),OWNER));
        assertFalse(Files.exists(parent));
    }
    @Test void completeCorruptFrameFailsClosedAndPreservesOriginalBytes() throws IOException {
        var file=directory.resolve("city.journal");try(var writer=CityBatchJournal.open(file,OWNER)){writer.prepare(batch());}
        var bytes=Files.readAllBytes(file);bytes[bytes.length-1]^=1;Files.write(file,bytes);
        assertThrows(IOException.class,()->CityBatchJournal.open(file,OWNER));assertArrayEquals(bytes,Files.readAllBytes(file));
    }
}
