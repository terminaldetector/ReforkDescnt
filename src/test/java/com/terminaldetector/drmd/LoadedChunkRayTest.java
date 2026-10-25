package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.edit.LoadedChunkRay;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoadedChunkRayTest {
    @Test void fullyLoadedRayTraversesEveryCrossedChunk() {
        var visited=new ArrayList<String>();
        assertEquals(1,LoadedChunkRay.fraction(.25,.25,32.25,.25,(x,z)->{visited.add(x+":"+z);return true;}));
        assertEquals(List.of("0:0","1:0","2:0"),visited);
    }
    @Test void aThinUnloadedWedgeCannotBeSkippedByAnOtherwiseLoadedDiagonal() {
        double t=LoadedChunkRay.fraction(15.9,15.8999,16.1,16.1,(x,z)->!(x==1 && z==0));
        assertTrue(t>.49 && t<.5,"the tiny first corner crossing is checked before the diagonal");
        assertTrue(15.9+.2*t<16,"clipped endpoint remains in the loaded chunk");
    }
    @Test void exactCornerTouchesRequireBothSideChunks() {
        assertTrue(LoadedChunkRay.fraction(15,15,17,17,(x,z)->!(x==0 && z==1))<.5);
        assertEquals(1,LoadedChunkRay.fraction(15,15,17,17,(x,z)->true));
    }
    @Test void negativeCoordinatesUseFloorAndClipBeforeTheNegativeBoundary() {
        double t=LoadedChunkRay.fraction(-.1,-.1,-16.1,-.1,(x,z)->x!=-2);
        assertTrue(t>0 && t<1);assertTrue(-.1-16*t>-16);
    }
    @Test void unloadedStartAndANegativeStepFromItsBoundaryHaveNoSafeLength() {
        assertEquals(0,LoadedChunkRay.fraction(1,1,2,2,(x,z)->false));
        assertEquals(0,LoadedChunkRay.fraction(0,1,-1,1,(x,z)->x>=0));
    }
    @Test void theProbeNeverContinuesPastTheFirstUnavailableChunk() {
        var visited=new ArrayList<Integer>();
        LoadedChunkRay.fraction(1,1,65,1,(x,z)->{visited.add(x);return x==0;});
        assertEquals(List.of(0,1),visited);
    }
    @Test void invalidOrExcessiveRaysDoNotReadTheWorld() {
        assertEquals(0,LoadedChunkRay.fraction(Double.NaN,0,1,0,(x,z)->{throw new AssertionError();}));
        assertEquals(0,LoadedChunkRay.fraction(0,0,257,0,(x,z)->{throw new AssertionError();}));
    }
    @Test void aVerticalOrZeroLengthRayOnlyNeedsItsOwnLoadedColumn() {
        assertEquals(1,LoadedChunkRay.fraction(4,5,4,5,(x,z)->true));
        assertEquals(0,LoadedChunkRay.fraction(4,5,4,5,(x,z)->false));
    }
}
