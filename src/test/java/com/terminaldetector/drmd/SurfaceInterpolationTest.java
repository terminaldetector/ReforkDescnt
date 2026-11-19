package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.SurfaceInterpolation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceInterpolationTest {
    @Test void slopedTerrainIsContinuousAcrossPositiveAndNegativeChunkBoundaries() {
        for(double x=-65;x<=65;x+=.125)for(double z:new double[]{-32,-.01,0,16,32})
            assertEquals(80+x*.25-z*.125,SurfaceInterpolation.height(x,z,16,(a,b)->80+a*.25-b*.125),1e-10);
    }
    @Test void measuredPlateauDoesNotOvershootOrProduceAStaircase() {
        double previous=Double.NEGATIVE_INFINITY;
        for(double x=8;x<=24;x+=.25) {
            double h=SurfaceInterpolation.height(x,8,16,(a,b)->a<16?64:96);
            assertTrue(h>=64 && h<=96);assertTrue(h>=previous);
            if(x>8 && x<24)assertTrue(h>64 && h<96);
            previous=h;
        }
    }
    @Test void coarseMaximumDoesNotTurnUnknownChildrenIntoAFalseCeiling() {
        int[] coarseReads={0};
        var observations=(SurfaceInterpolation.ObservedSampler)(x,z,level) -> {
            if(level!=0) { coarseReads[0]++;return 2161; }
            return x>=0&&x<16&&z>=0&&z<16 ? (short)2161 : Short.MIN_VALUE;
        };
        // Actual retained failure: L0(40,104) was unknown while L3's MAX was2161.
        assertEquals(63,SurfaceInterpolation.groundHeight(40,104,16,observations,(x,z)->63));
        assertEquals(63,SurfaceInterpolation.groundHeight(504,504,16,observations,(x,z)->63));
        assertEquals(0,coarseReads[0]);
        assertEquals(2161,SurfaceInterpolation.groundHeight(8,8,16,observations,(x,z)->63));
    }
    @Test void absentFineObservationsPreserveTheProceduralSlopeAcrossSectionSeams() {
        for(double x:new double[]{-512.001,-512,-.001,0,511.999,512,512.001})
            for(double z:new double[]{-512,-16,0,512})
                assertEquals(70+x*.01-z*.02,
                    SurfaceInterpolation.groundHeight(x,z,16,(a,b,l)->Short.MIN_VALUE,
                        (a,b)->70+a*.01-b*.02),1e-10);
    }
    @Test void knownFineTerrainKeepsItsActualHeightAndLocalInterpolation() {
        var observations=(SurfaceInterpolation.ObservedSampler)(x,z,level)->(short)(x<16?64:96);
        assertEquals(64,SurfaceInterpolation.groundHeight(8,8,16,observations,(x,z)->999));
        assertEquals(80,SurfaceInterpolation.groundHeight(16,8,16,observations,(x,z)->999));
        assertEquals(96,SurfaceInterpolation.groundHeight(24,8,16,observations,(x,z)->999));
    }

}
