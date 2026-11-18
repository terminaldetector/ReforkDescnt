package com.terminaldetector.drmd;

import com.terminaldetector.drmd.physics.collision.BodyTopology.Cell;
import com.terminaldetector.drmd.physics.collision.UnsupportedStructure;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.physics.collision.UnsupportedStructure.Contact.*;

class UnsupportedStructureTest {
    @Test void detachedMixedNegativeCoordinatesArePreservedWithoutPackedAliases(){
        var a=new Cell(-17,-513,-17);var b=new Cell(-16,-513,-17);var c=new Cell(-16,-512,-17);
        var original=Set.of(a,b,c);
        var result=UnsupportedStructure.examine(a,p->original.contains(p)?MATERIAL:EMPTY,256,16);
        assertEquals(UnsupportedStructure.Status.DETACHED,result.status());assertEquals(original,result.cells());
        assertThrows(UnsupportedOperationException.class,()->result.cells().clear());
    }
    @Test void oneKnownAnchorOrUnknownFaceRefusesTheEntireComponent(){
        var root=new Cell(0,0,0);var block=new Cell(0,1,0);var boundary=new Cell(1,1,0);
        for(var stop:List.of(ANCHOR,UNKNOWN)){
            var result=UnsupportedStructure.examine(root,p->p.equals(boundary)?stop:p.equals(root)||p.equals(block)?MATERIAL:EMPTY,256,16);
            assertEquals(stop==ANCHOR?UnsupportedStructure.Status.ANCHORED:UnsupportedStructure.Status.UNAVAILABLE,result.status());
        }
    }
    @Test void oversizedConnectedComponentIsRefusedRatherThanSilentlyCutIntoBodies(){
        var reads=new AtomicInteger();
        var result=UnsupportedStructure.examine(new Cell(0,0,0),p->{reads.incrementAndGet();return p.x()>=0&&p.x()<5&&p.y()>=0&&p.y()<5&&p.z()>=0&&p.z()<11?MATERIAL:EMPTY;},256,16);
        assertEquals(UnsupportedStructure.Status.TOO_LARGE,result.status());assertEquals(256,result.cells().size());
        assertTrue(reads.get()<=256*6+1,"one finite face shell, never a scan of the surrounding world");
    }
    @Test void radiusAndUnknownSeedRemainRefusalsEvenForThinStructures(){
        var root=new Cell(0,0,0);
        assertEquals(UnsupportedStructure.Status.TOO_LARGE,UnsupportedStructure.examine(root,p->p.y()==0&&p.z()==0&&p.x()>=0&&p.x()<20?MATERIAL:EMPTY,256,16).status());
        assertEquals(UnsupportedStructure.Status.UNAVAILABLE,UnsupportedStructure.examine(root,p->UNKNOWN,256,16).status());
    }
}
