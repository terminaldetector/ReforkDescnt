package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.planet.HorizonExtent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HorizonExtentTest {
    @Test void theRealOuterExtentIsContinuousMonotoneAndFlatAtBothEnds(){
        double outer=12000,previous=1;
        assertEquals(1,HorizonExtent.opacity(outer*.55,outer));assertEquals(0,HorizonExtent.opacity(outer,outer));
        for(int d=0;d<=13000;d++){double next=HorizonExtent.opacity(d,outer);assertTrue(next<=previous&&next>=0);previous=next;}
        for(double join:new double[]{outer*.55,outer})assertEquals(HorizonExtent.opacity(join-.001,outer),HorizonExtent.opacity(join+.001,outer),1e-10);
    }
    @Test void cameraTranslationChangesOpacityContinuouslyWithoutAnyGeometryRebuildOrOriginStep(){
        double before=HorizonExtent.at(9000,0,0,0,12000),previous=before;
        for(int i=1;i<=64;i++){double next=HorizonExtent.at(9000,0,i,0,12000);assertTrue(next>previous);assertTrue(next-previous<.001);previous=next;}
        assertEquals(previous,HorizonExtent.at(3000,0,-5936,0,12000),1e-12,"world-relative translation leaves the same eye ray unchanged");
    }
}
