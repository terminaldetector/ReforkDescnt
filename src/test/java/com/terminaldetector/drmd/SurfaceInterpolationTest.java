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
}
