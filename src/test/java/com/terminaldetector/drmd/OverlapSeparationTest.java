package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.OverlapSeparation;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlapSeparationTest {
    @Test void pairEscapesAreFiniteUnitVectorsAndDoNotAccelerateTheWholeFlock() {
        for(int a=0;a<128;a++) for(int b=a+1;b<128;b++) {
            var one=OverlapSeparation.between(new UUID(0,a),new UUID(0,b));
            var two=OverlapSeparation.between(new UUID(0,b),new UUID(0,a));
            assertEquals(1,one.x()*one.x()+one.y()*one.y()+one.z()*one.z(),1e-12);
            assertEquals(-one.x(),two.x(),1e-12); assertEquals(-one.y(),two.y(),1e-12); assertEquals(-one.z(),two.z(),1e-12);
        }
    }
    @Test void fullyCoincidentFlockHasIndependentEscapeVectors() {
        var sums=new java.util.HashSet<String>();
        for(int a=0;a<128;a++) {
            double x=0,y=0,z=0;
            for(int b=0;b<128;b++) {
                var d=OverlapSeparation.between(new UUID(0,a),new UUID(0,b));x+=d.x();y+=d.y();z+=d.z();
            }
            assertTrue(x*x+y*y+z*z>1e-6);
            sums.add(Math.round(x*100)+":"+Math.round(y*100)+":"+Math.round(z*100));
        }
        assertEquals(128,sums.size());
    }
    @Test void identityIsIgnoredAndDirectionIsStable() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertEquals(new OverlapSeparation.Direction(0,0,0),OverlapSeparation.between(a,a));
        assertEquals(OverlapSeparation.between(a,b),OverlapSeparation.between(a,b));
    }
}
