package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.bombardment.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class ClusterProgramTest {
    private static final Vec3 F=new Vec3(0,0,1),U=new Vec3(0,1,0);
    @Test void everyStagePartitionsTheOriginalSevenAndFourteenWithoutDuplicates(){
        for(var pattern:ClusterProgram.Pattern.values())for(int total:new int[]{7,14}){var p=ClusterProgram.preset(pattern);var ids=new HashSet<Integer>();int previous=-1;for(int stage=0;stage<p.stages();stage++){assertTrue(p.due(stage)>previous);previous=p.due(stage);for(int i=p.first(total,stage);i<p.first(total,stage)+p.count(total,stage);i++)assertTrue(ids.add(i));}assertEquals(total,ids.size());assertEquals(0,Collections.min(ids));assertEquals(total-1,Collections.max(ids));}
    }
    @Test void fanHasDistinctOrderedLateralBearingsAndRetainsAForwardBias(){var p=ClusterProgram.preset(ClusterProgram.Pattern.FAN);double previous=Double.POSITIVE_INFINITY;for(int i=0;i<7;i++){var v=p.direction(7,i,F,U);assertEquals(1,v.length(),1e-12);assertTrue(v.z()>.8);assertTrue(v.x()<previous);previous=v.x();}}
    @Test void ringHasBalancedRadialComponentsAndDeterministicDirections(){var p=ClusterProgram.preset(ClusterProgram.Pattern.RING);var sum=new Vec3(0,0,0);for(int i=0;i<14;i++){var a=p.direction(14,i,F,U);assertEquals(a,p.direction(14,i,F,U));assertTrue(a.z()>0);sum=sum.plus(a);}assertEquals(0,sum.x(),1e-12);assertEquals(0,sum.y(),1e-12);}
    @Test void allPatternsAreCovariantUnderARealTiltedRotationIncludingVerticalForward(){var q=new Quat(.2,.3,.4,.5).normalized();for(var pattern:ClusterProgram.Pattern.values()){var p=ClusterProgram.preset(pattern);for(var f:List.of(F,new Vec3(0,1,0))){var u=f.equals(F)?U:F;for(int i=0;i<14;i++)assertTrue(q.rotate(p.direction(14,i,f,u)).minus(p.direction(14,i,q.rotate(f),q.rotate(u))).lengthSquared()<1e-24);}}}
    @Test void armingRequiresBothActualTravelAndClockAndDisarmingCannotRelease(){var f=new ClusterFlight(ClusterProgram.preset(ClusterProgram.Pattern.STAGGER),F,U);assertThrows(IllegalStateException.class,f::released);f.moved(2,20);assertFalse(f.armed());f.moved(1,20);assertTrue(f.armed());assertTrue(f.due(20));f.released();f.disarm();assertFalse(f.due(100));assertThrows(IllegalStateException.class,f::released);}
    @Test void invalidOrUnboundedProgramsAndRecursiveRosterSizesAreRefused(){assertThrows(IllegalArgumentException.class,()->new ClusterProgram(ClusterProgram.Pattern.FAN,0,1,100,1,10000,0,Double.NaN));var p=ClusterProgram.preset(ClusterProgram.Pattern.FAN);assertThrows(IllegalArgumentException.class,()->p.count(28,0));assertThrows(IllegalArgumentException.class,()->p.direction(7,7,F,U));assertThrows(IllegalArgumentException.class,()->p.direction(7,0,F,F));}
    @Test void stagedBirthAndImpactAllowancesShareTheActualTickAndRefuseBeforeExtraWork(){var b=new ClusterBurstBudget();for(int i=0;i<12;i++)assertTrue(b.reserveStage(1,0,4,()->0));assertFalse(b.reserveStage(1,0,1,()->{throw new AssertionError("one live scan per actual tick");}));assertTrue(b.reserveBlast(1,0));assertTrue(b.reserveBlast(1,0));assertFalse(b.reserveBlast(1,0));assertTrue(b.reserveBlast(1,1));assertTrue(b.reserveBlast(1,1));assertFalse(b.reserveBlast(1,2));assertTrue(b.reserveBlast(2,0));assertFalse(b.reserveStage(2,0,4,()->510));}
}
