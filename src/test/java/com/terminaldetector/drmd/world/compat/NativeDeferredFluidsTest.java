package com.terminaldetector.drmd.world.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.compat.NativeDeferredFluids.*;

class NativeDeferredFluidsTest {
    private static Edge edge(int index,int y,String family,long created) {
        int x=index*16+15;return new Edge(new Point(x,y,-8),new Point(x+1,y,-8),family,created);
    }
    private static final class Engine implements Access {
        final Map<Point,Source> sources=new HashMap<>();
        final Set<Point> targets=new HashSet<>();
        boolean currentTickAlreadyProcessed=true,queued,accepts,validTarget=true;
        int sourceReads,targetReads,scheduleCalls;
        public Source source(Point at){sourceReads++;return sources.getOrDefault(at,new Source(false,null));}
        public boolean targetValid(Point at){return validTarget;}
        public boolean targetAvailable(Point at){targetReads++;return targets.contains(at);}
        public boolean queueFuture(Point at,String family){return ensureFuture(()->queued,()->{scheduleCalls++;if(accepts)queued=true;});}
        void ready(Edge e){sources.put(e.source(),new Source(true,e.family()));targets.add(e.target());}
    }
    @Test void duplicateKeepsOriginalWorkInsteadOfRefreshingItsAge() {
        var p=new Pending();var first=edge(0,32,"minecraft:water",10);
        assertEquals(Offer.ADDED,p.offer(first));assertEquals(Offer.COALESCED,p.offer(edge(0,32,"minecraft:water",1_000_000)));
        assertEquals(List.of(first),p.rotate(16));assertEquals(1,p.size());
    }
    @Test void fullCapacityRefusesNewWorkWithoutEvictingAnyOldIdentity() {
        var p=new Pending();for(int i=0;i<MAX_ENTRIES;i++)assertEquals(Offer.ADDED,p.offer(edge(i,32,"minecraft:water",i)));
        assertEquals(Offer.FULL,p.offer(edge(MAX_ENTRIES,32,"minecraft:water",99)));
        assertTrue(p.contains(edge(0,32,"minecraft:water",0)));assertTrue(p.contains(edge(MAX_ENTRIES-1,32,"minecraft:water",0)));
        assertEquals(Offer.COALESCED,p.offer(edge(0,32,"minecraft:water",999)));assertEquals(MAX_ENTRIES,p.size());
    }
    @Test void fairBudgetEventuallyInspectsEveryUnknownEntry() {
        var p=new Pending();for(int i=0;i<37;i++)p.offer(edge(i,32,"minecraft:water",0));
        Set<Point> inspected=new HashSet<>();Access unknown=new Access() {
            public Source source(Point at){inspected.add(at);return new Source(false,null);}
            public boolean targetAvailable(Point at){fail("unknown source must not inspect target");return false;}
            public boolean queueFuture(Point at,String family){fail("unknown source must not schedule");return false;}
        };
        for(int i=0;i<3;i++)assertEquals(ATTEMPTS_PER_TICK,process(p,unknown,ATTEMPTS_PER_TICK).attempted());
        assertEquals(37,inspected.size());assertEquals(37,p.size());
    }
    @Test void unavailableSourceIsRetainedWithoutTargetReadsOrScheduling() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",0);p.offer(e);var engine=new Engine();
        assertEquals(new Pass(1,0,0),process(p,engine,16));assertTrue(p.contains(e));assertEquals(0,engine.targetReads);assertEquals(0,engine.scheduleCalls);
    }
    @Test void unavailableTargetKeepsAValidatedSourcePending() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",0);p.offer(e);var engine=new Engine();engine.sources.put(e.source(),new Source(true,e.family()));
        assertEquals(new Pass(1,0,0),process(p,engine,16));assertTrue(p.contains(e));assertEquals(1,engine.targetReads);assertEquals(0,engine.scheduleCalls);
    }
    @Test void loadedSourceReplacementInvalidatesOnlyThatFluidWork() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",0);p.offer(e);var engine=new Engine();engine.sources.put(e.source(),new Source(true,"minecraft:lava"));
        assertEquals(new Pass(1,0,1),process(p,engine,16));assertFalse(p.contains(e));assertEquals(0,engine.targetReads);assertEquals(0,engine.scheduleCalls);
    }
    @Test void currentTickDoesNotAcknowledgeRefusedFutureScheduling() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",0);p.offer(e);var engine=new Engine();engine.ready(e);
        assertTrue(engine.currentTickAlreadyProcessed);assertFalse(engine.queued);
        assertEquals(new Pass(1,0,0),process(p,engine,16));assertTrue(p.contains(e));assertEquals(1,engine.scheduleCalls);
        engine.accepts=true;
        assertEquals(new Pass(1,1,0),process(p,engine,16));assertFalse(p.contains(e));assertTrue(engine.queued);
    }
    @Test void alreadyQueuedFutureWorkAcknowledgesWithoutAddingAnotherTick() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",0);p.offer(e);var engine=new Engine();engine.ready(e);engine.queued=true;
        assertEquals(new Pass(1,1,0),process(p,engine,16));assertEquals(0,engine.scheduleCalls);assertEquals(0,p.size());
    }
    @Test void ancientUnknownWorkSurvivesRepeatedPollingAndColdCodecRoundtrip() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",Long.MIN_VALUE+17);p.offer(e);
        for(int i=0;i<2000;i++)process(p,new Engine(),16);
        var restored=Pending.read(p.writeNbt(new NbtCompound(),null),null);
        assertEquals(List.of(e),restored.rotate(16));assertFalse(restored.isDirty());
    }
    @Test void farPositiveAndNegativeBlockYKeepDistinctIdentityAndExplicitNbtInts() {
        var p=new Pending();var high=edge(0,2301,"minecraft:water",7);var low=edge(0,-1795,"minecraft:water",8);
        var farHigh=edge(1,65535,"minecraft:lava",9);var farLow=edge(1,-65537,"minecraft:lava",10);
        for(var e:List.of(high,low,farHigh,farLow))assertEquals(Offer.ADDED,p.offer(e));
        assertNotEquals(high.key(),low.key());assertNotEquals(farHigh.key(),farLow.key());
        var nbt=p.writeNbt(new NbtCompound(),null);var list=nbt.getList("entries",10);
        assertEquals(2301,list.getCompound(0).getInt("sy"));assertEquals(-1795,list.getCompound(1).getInt("sy"));
        assertEquals(-8,list.getCompound(0).getInt("sz"));assertFalse(list.getCompound(0).contains("packed"));
        assertEquals(List.of(high,low,farHigh,farLow),Pending.read(nbt,null).rotate(16));
    }
    @Test void canonicalFamiliesRemainSeparateEvenAtIdenticalCoordinates() {
        var p=new Pending();var water=edge(0,-1,"minecraft:water",0);var lava=edge(0,-1,"minecraft:lava",0);p.offer(water);p.offer(lava);
        var engine=new Engine();engine.sources.put(water.source(),new Source(true,"minecraft:water"));
        assertEquals(new Pass(2,0,1),process(p,engine,16));assertTrue(p.contains(water));assertFalse(p.contains(lava));
    }
    @Test void unsupportedSchemaPreservesOriginalDataAndRefusesNewAdmission() {
        var nbt=new NbtCompound();nbt.putInt("schema",99);nbt.putString("future","keep me");nbt.put("entries",new NbtList());
        var p=Pending.read(nbt,null);assertTrue(p.readOnly());assertFalse(p.isDirty());
        assertEquals(Offer.READ_ONLY,p.offer(edge(0,32,"minecraft:water",0)));assertEquals(nbt,p.writeNbt(new NbtCompound(),null));
    }
    @Test void malformedIdentityDoesNotBecomeAZeroCoordinateJob() {
        var valid=new Pending();valid.offer(edge(0,2301,"minecraft:water",0));var nbt=valid.writeNbt(new NbtCompound(),null);
        nbt.getList("entries",10).getCompound(0).remove("ty");var restored=Pending.read(nbt,null);
        assertTrue(restored.readOnly());assertEquals(nbt,restored.writeNbt(new NbtCompound(),null));assertEquals(0,restored.size());
    }
    @Test void emptyStateAndFairPollingDoNotProduceSaveWrites() {
        var p=new Pending();assertFalse(p.isDirty());p.rotate(16);assertFalse(p.isDirty());
        p.offer(edge(0,32,"minecraft:water",0));var restored=Pending.read(p.writeNbt(new NbtCompound(),null),null);
        restored.rotate(16);assertFalse(restored.isDirty());assertEquals(1,restored.size());
    }
    @Test void independentStatesDoNotShareWorldWork() {
        var first=new Pending();var second=new Pending();var e=edge(0,32,"minecraft:water",0);first.offer(e);
        assertEquals(1,first.size());assertEquals(0,second.size());second.offer(edge(1,32,"minecraft:lava",0));
        first.remove(e);assertEquals(1,second.size());
    }
    @Test void nonAdjacentOrSameCubeTargetsCannotEnterTheMetadataQueue() {
        assertThrows(IllegalArgumentException.class,()->new Edge(new Point(1,1,1),new Point(2,1,1),"minecraft:water",0));
        assertThrows(IllegalArgumentException.class,()->new Edge(new Point(15,1,1),new Point(17,1,1),"minecraft:water",0));
    }
    @Test void positionsBeyondExplicitCubeBoundsCannotAliasAStoredJob() {
        assertThrows(IllegalArgumentException.class,()->new Edge(new Point(16_777_215,32,0),new Point(16_777_216,32,0),"minecraft:water",0));
    }
    @Test void provablyInvalidTargetRetiresWithoutLoadingEitherCell() {
        var p=new Pending();var e=edge(0,32,"minecraft:water",0);p.offer(e);var engine=new Engine();engine.validTarget=false;
        assertEquals(new Pass(1,0,1),process(p,engine,16));assertEquals(0,p.size());
        assertEquals(0,engine.sourceReads);assertEquals(0,engine.targetReads);assertEquals(0,engine.scheduleCalls);
    }
    @Test void invalidSourceIsNotConfusedWithTemporarilyUnloadedSource() {
        var p=new Pending();var invalid=edge(0,32,"minecraft:water",0);var unknown=edge(1,32,"minecraft:water",0);p.offer(invalid);p.offer(unknown);
        var engine=new Engine();engine.sources.put(invalid.source(),new Source(true,null));
        assertEquals(new Pass(2,0,1),process(p,engine,16));assertFalse(p.contains(invalid));assertTrue(p.contains(unknown));
        assertEquals(0,engine.targetReads);assertEquals(0,engine.scheduleCalls);
    }
}
