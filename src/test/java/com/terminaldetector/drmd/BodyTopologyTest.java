package com.terminaldetector.drmd;
import com.terminaldetector.drmd.physics.collision.BodyTopology;
import com.terminaldetector.drmd.physics.collision.BodyTopology.Cell;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BodyTopologyTest {
    private static Cell c(int x,int y,int z){return new Cell(x,y,z);}
    private Set<Cell> line(int end){var result=new HashSet<Cell>();for(int x=0;x<=end;x++)result.add(c(x,0,0));return result;}
    @Test void breakingABridgeDetachesTheSmallerSideAndKeepsLegacyIsolatedParts() {
        var before=line(5);before.add(c(100,0,0));var after=new HashSet<>(before);after.remove(c(2,0,0));
        var result=BodyTopology.afterRemoval(before,after,Set.of(c(2,0,0)));
        assertEquals(List.of(Set.of(c(0,0,0),c(1,0,0))),result.detached());
        assertTrue(result.detached().stream().noneMatch(set->set.contains(c(100,0,0))));
    }
    @Test void twoIndependentCutsOfOneStructureChooseOneLargestRemainingBody() {
        var before=line(8);var removed=Set.of(c(2,0,0),c(5,0,0));var after=new HashSet<>(before);after.removeAll(removed);
        assertEquals(List.of(Set.of(c(0,0,0),c(1,0,0)),Set.of(c(3,0,0),c(4,0,0))),BodyTopology.afterRemoval(before,after,removed).detached());
    }
    @Test void aNewBypassInTheSameAtomicBatchPreventsAnArtificialFracture() {
        var before=line(4);var after=new HashSet<>(before);after.remove(c(2,0,0));after.addAll(Set.of(c(1,1,0),c(2,1,0),c(3,1,0)));
        assertTrue(BodyTopology.afterRemoval(before,after,Set.of(c(2,0,0))).detached().isEmpty());
    }
    @Test void aCornerChipUsesALocalProbeInsteadOfScanningAll32768Cells() {
        var before=new HashSet<Cell>();for(int x=0;x<32;x++)for(int y=0;y<32;y++)for(int z=0;z<32;z++)before.add(c(x,y,z));
        var after=new HashSet<>(before);after.remove(c(0,0,0));var result=BodyTopology.afterRemoval(before,after,Set.of(c(0,0,0)));
        assertTrue(result.detached().isEmpty());assertTrue(result.visitedCells()<64,"connected boundary is proved locally: "+result.visitedCells());
    }
    @Test void removingALeafOrTheWholeBodyCreatesNoSpuriousFragment() {
        var before=line(4);var after=new HashSet<>(before);after.remove(c(0,0,0));var result=BodyTopology.afterRemoval(before,after,Set.of(c(0,0,0)));
        assertTrue(result.detached().isEmpty());assertEquals(0,result.visitedCells());assertTrue(BodyTopology.afterRemoval(before,Set.of(),before).detached().isEmpty());
    }
    @Test void falselyReportingAnOccupiedOrNewCellAsRemovedIsRejected() {
        var before=line(2);
        assertThrows(IllegalArgumentException.class,()->BodyTopology.afterRemoval(before,before,Set.of(c(1,0,0))));
        assertThrows(IllegalArgumentException.class,()->BodyTopology.afterRemoval(before,Set.of(),Set.of(c(9,0,0))));
    }
}
