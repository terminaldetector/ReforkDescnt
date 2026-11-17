package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.weapon.core.RelativeProjectileSweep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class RelativeProjectileSweepTest {
    private static final Vec3 HALF=new Vec3(.175,.175,.175),ZERO=new Vec3(0,0,0);
    @Test void fastOpposingRoundsContactBetweenBothEndpointBoxes(){
        var c=RelativeProjectileSweep.firstContact(new Vec3(0,0,0),new Vec3(3.125,0,0),HALF,
            new Vec3(4,0,0),new Vec3(-1.375,0,0),HALF).orElseThrow();
        assertEquals(3.65/4.5,c.fraction(),1e-12);assertEquals(-1,c.normal().x(),0);
        // First box approaches from negative X, so its contact normal points left.
        assertTrue(c.point().x()>2&&c.point().x()<3);
    }
    @Test void skewPassIsNotAnInterceptDespiteCrossingWorldXAxis(){
        assertTrue(RelativeProjectileSweep.firstContact(ZERO,new Vec3(4,0,0),HALF,
            new Vec3(4,.351,0),new Vec3(-2,0,0),HALF).isEmpty());
    }
    @Test void swappedActualTickOrdersReturnTheSameTimeAndMaterialPoint(){
        Vec3 a=new Vec3(0,0,0),da=new Vec3(3.125,.2,0),b=new Vec3(4,.2,0),db=new Vec3(-1.375,-.1,0);
        var first=RelativeProjectileSweep.firstContact(a,da,HALF,b,db,HALF).orElseThrow();
        var second=RelativeProjectileSweep.firstContact(b,db,HALF,a,da,HALF).orElseThrow();
        assertEquals(first.fraction(),second.fraction(),1e-12);
        assertEquals(0,first.point().minus(second.point()).lengthSquared(),1e-20);
        assertEquals(0,first.normal().plus(second.normal()).lengthSquared(),1e-20);
    }
    @Test void translationAndAxisPermutationPreserveTheContactFraction(){
        Vec3 a=new Vec3(0,0,0),da=new Vec3(0,0,4),b=new Vec3(0,0,5),db=new Vec3(0,0,-2),shift=new Vec3(1e6,-40,21);
        var c=RelativeProjectileSweep.firstContact(a,da,HALF,b,db,HALF).orElseThrow();
        var moved=RelativeProjectileSweep.firstContact(a.plus(shift),da,HALF,b.plus(shift),db,HALF).orElseThrow();
        assertEquals(c.fraction(),moved.fraction(),1e-10);
        assertEquals(0,c.point().plus(shift).minus(moved.point()).lengthSquared(),1e-18);
        assertEquals(0,c.normal().x(),0);assertEquals(-1,c.normal().z(),0);
    }
    @Test void equalVelocitySeparatedBoxesStaySeparatedWhileAnOverlapStartsAtZero(){
        assertTrue(RelativeProjectileSweep.firstContact(ZERO,new Vec3(2,0,0),HALF,new Vec3(2,0,0),new Vec3(2,0,0),HALF).isEmpty());
        assertEquals(0,RelativeProjectileSweep.firstContact(ZERO,ZERO,HALF,new Vec3(.1,0,0),ZERO,HALF).orElseThrow().fraction(),0);
    }
    @Test void invalidDimensionsAndNonfiniteTrajectoriesNeverAuthorizeContact(){
        assertTrue(RelativeProjectileSweep.firstContact(ZERO,ZERO,new Vec3(0,1,1),ZERO,ZERO,HALF).isEmpty());
        assertTrue(RelativeProjectileSweep.firstContact(ZERO,new Vec3(Double.NaN,0,0),HALF,ZERO,ZERO,HALF).isEmpty());
    }
}
