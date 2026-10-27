package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.edit.LoadedCubeRay;
import com.terminaldetector.drmd.world.edit.LoadedChunkRay;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoadedCubeRayTest {
    @Test void anUnloadedVerticalCubeStopsAnOtherwiseLoadedColumn() {
        double f=LoadedCubeRay.fraction(8,15.5,8,8,17,8,(x,y,z)->y==0);
        assertTrue(f<1.0/3 && f>1.0/3-1e-5);
    }
    @Test void negativeVerticalSeamsRoundDown() {
        double f=LoadedCubeRay.fraction(8,-15.5,8,8,-17,8,(x,y,z)->y==-1);
        assertTrue(f<1.0/3 && f>1.0/3-1e-5);
    }
    @Test void xyzCornerChecksAllSevenTouchedNeighbours() {
        var visited=new HashSet<D6ChunkPos>();
        assertEquals(1,LoadedCubeRay.fraction(15,15,15,17,17,17,(x,y,z)->{visited.add(new D6ChunkPos(x,y,z));return true;}));
        assertEquals(8,visited.size());
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)assertTrue(visited.contains(new D6ChunkPos(x,y,z)));
    }
    @Test void aFaceTouchedOnlyAtTheCornerCannotBeSkipped() {
        double f=LoadedCubeRay.fraction(15,15,15,17,17,17,(x,y,z)->!(x==0 && y==1 && z==0));
        assertTrue(f<.5 && f>.49);
    }
    @Test void signedZeroDoesNotTurnAnInactiveAxisIntoNaN() {
        assertEquals(1,LoadedCubeRay.fraction(0,0,0,-0.0,32,0,(x,y,z)->true));
    }
    @Test void zeroLengthStillRequiresItsOwnVolume() {
        assertEquals(1,LoadedCubeRay.fraction(-1,-1,-1,-1,-1,-1,(x,y,z)->true));
        assertEquals(0,LoadedCubeRay.fraction(-1,-1,-1,-1,-1,-1,(x,y,z)->false));
    }
    @Test void malformedAndOversizedRaysCannotProbeTheBackend() {
        LoadedCubeRay.Available noQuery=(x,y,z)->{fail("Invalid ray queried the world");return true;};
        assertEquals(0,LoadedCubeRay.fraction(0,0,0,0,257,0,noQuery));
        assertEquals(0,LoadedCubeRay.fraction(0,Double.NaN,0,0,0,0,noQuery));
        assertEquals(0,LoadedCubeRay.fraction(30_000_001,0,0,30_000_002,0,0,noQuery));
    }
    @Test void horizontalRaysRetainTheExistingTwoDimensionalBoundaryPolicy() {
        var random=new Random(271026);
        for(int i=0;i<1000;i++) {
            double x=random.nextDouble()*128-64,z=random.nextDouble()*128-64;
            double ex=random.nextDouble()*128-64,ez=random.nextDouble()*128-64;
            var missing=new D6ChunkPos(random.nextInt(8)-4,0,random.nextInt(8)-4);
            double old=LoadedChunkRay.fraction(x,z,ex,ez,(cx,cz)->cx!=missing.x() || cz!=missing.z());
            double now=LoadedCubeRay.fraction(x,8,z,ex,8,ez,(cx,cy,cz)->cx!=missing.x() || cz!=missing.z());
            assertEquals(old,now,1e-12);
        }
    }
}
