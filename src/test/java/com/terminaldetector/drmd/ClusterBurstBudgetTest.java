package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.bombardment.ClusterBurstBudget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClusterBurstBudgetTest {
    @Test void WholeHeavyBurstsAreDeferredRatherThanTruncated() {
        var budget=new ClusterBurstBudget();
        for(int i=0;i<3;i++)assertTrue(budget.reserve(1,0,14,()->0));
        assertFalse(budget.reserve(1,0,14,()->0));assertTrue(budget.reserve(2,0,14,()->42));
    }
    @Test void DifferentChunksStillShareOneWorldTickCeiling() {
        var budget=new ClusterBurstBudget();
        for(int i=0;i<9;i++)assertTrue(budget.reserve(1,i,14,()->0));
        assertFalse(budget.reserve(1,10,7,()->0));
    }
    @Test void ReloadedLiveEntitiesCountAgainstTheWorldLimit() {
        var budget=new ClusterBurstBudget();assertFalse(budget.reserve(1,0,14,()->505));
        assertTrue(budget.reserve(1,0,7,()->0));assertFalse(budget.reserve(1,1,7,()->0));
        assertTrue(budget.reserve(2,0,14,()->0));
    }
    @Test void InvalidCountsDoNotAllocateOrConsumeCapacity() {
        var budget=new ClusterBurstBudget();assertFalse(budget.reserve(1,0,10000,()->{throw new AssertionError();}));
        assertTrue(budget.reserve(1,0,14,()->0));
    }
}
