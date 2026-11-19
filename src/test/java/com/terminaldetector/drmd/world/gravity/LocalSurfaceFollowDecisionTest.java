package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;

/** Actual age51 CaveSpider geometry: the current edge is clear, but a premature diagonal cuts the plate. */
class LocalSurfaceFollowDecisionTest {
    private static final Vec3d BEFORE=new Vec3d(-6047933.98,24.502397995512503,-2720276.657298289);
    private static final Box BODY=new Box(-6047933.98,24.152398001472967,-2720277.007298283,
        -6047933.48,24.85239798955204,-2720276.3072982947);
    private static final Box PLATE=new Box(-6047934,25,-2720286,-6047919,26,-2720277);
    private static final Vec3d CURRENT=new Vec3d(-6047933.98,24.5,-2720276.5);
    private static final Vec3d NEXT=new Vec3d(-6047933.98,25.5,-2720276.5);
    // These are the two actually observed nodes; no assertion invents the unobserved rest of the path.
    private static final IntFunction<Vec3d> POINT=index->switch(index){
        case 5->CURRENT;case 6->NEXT;default->throw new AssertionError("unexpected unobserved node "+index);
    };
    private static boolean clear(Box box,Vec3d feet,Vec3d target,Box obstacle){
        return !box.union(box.offset(target.subtract(feet))).contract(1e-6).intersects(obstacle);
    }

    @Test void observedPrematureCornerRetainsCurrentIndexAndSafeWaypoint(){
        assertTrue(BEFORE.distanceTo(CURRENT)<.18);
        assertTrue(clear(BODY,BEFORE,CURRENT,PLATE));assertFalse(clear(BODY,BEFORE,NEXT,PLATE));
        var queries=new ArrayList<Integer>();
        var decision=LocalSurfaceFollowDecision.select(BEFORE,5,13,POINT,index->{queries.add(index);return clear(BODY,BEFORE,POINT.apply(index),PLATE);});
        assertTrue(decision.clear());assertEquals(5,decision.index());assertEquals(List.of(6,5),queries);
    }

    @Test void actualBodyClearOfCornerAdvancesWithOneReusedQuery(){
        // Actual later resting feet from the unchanged first run, after native momentum approached the old waypoint.
        var feet=new Vec3d(-6047933.98,24.502397995512503,-2720276.5134718125);
        var box=BODY.offset(feet.subtract(BEFORE));assertTrue(clear(box,feet,NEXT,PLATE));
        var queries=new ArrayList<Integer>();
        var decision=LocalSurfaceFollowDecision.select(feet,5,13,POINT,index->{queries.add(index);return clear(box,feet,POINT.apply(index),PLATE);});
        assertTrue(decision.clear());assertEquals(6,decision.index());assertEquals(List.of(6),queries);
    }

    @Test void obstructedCurrentEdgeStillFailsClosedWithinTwoQueries(){
        var blocked=new Box(-6047934,24.75,-2720286,-6047919,26,-2720277);
        assertFalse(clear(BODY,BEFORE,CURRENT,blocked));assertFalse(clear(BODY,BEFORE,NEXT,blocked));
        var queries=new ArrayList<Integer>();
        var decision=LocalSurfaceFollowDecision.select(BEFORE,5,13,POINT,index->{queries.add(index);return clear(BODY,BEFORE,POINT.apply(index),blocked);});
        assertFalse(decision.clear());assertEquals(5,decision.index());assertEquals(List.of(6,5),queries);
    }
    @Test void observedCowCentreCongestionCanAdvanceUsingNativeBodyWidth(){
        // Actual age24 Cow, target waypoint index3, from targeting-and-wreck-fallback-stock.
        var feet=new Vec3d(-4233069.053221275,27.98,-2004647.9995762235);
        var current=new Vec3d(-4233069.5,27.98,-2004648);
        var next=current.add(0,0,1);var up=new Vec3d(0,-1,0);
        assertTrue(feet.distanceTo(current)>.18);
        assertTrue(LocalSurfaceFollowDecision.nativeWaypointReached(ActorFrameMath.toLocal(current.subtract(feet),up),.9f));
        var queries=new ArrayList<Integer>();
        var decision=LocalSurfaceFollowDecision.select(3,10,index->LocalSurfaceFollowDecision.nativeWaypointReached(
            ActorFrameMath.toLocal((index==3?current:next).subtract(feet),up),.9f),index->{queries.add(index);return true;});
        assertTrue(decision.clear());assertEquals(4,decision.index());assertEquals(List.of(4),queries);
    }

    @Test void nativeC1WidthReachIsCardinalFrameRelativeAndRetainsNativeBoundaries(){
        for(var up:List.of(new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1))){
            var delta=new Vec3d(.4,.99,-.4);var world=ActorFrameMath.toWorld(delta,up);
            assertTrue(LocalSurfaceFollowDecision.nativeWaypointReached(ActorFrameMath.toLocal(world,up),.9f));
        }
        assertFalse(LocalSurfaceFollowDecision.nativeWaypointReached(new Vec3d(.46,0,0),.9f));
        assertFalse(LocalSurfaceFollowDecision.nativeWaypointReached(new Vec3d(0,1,0),.9f));
        assertFalse(LocalSurfaceFollowDecision.nativeWaypointReached(Vec3d.ZERO,Float.NaN));
        assertTrue(LocalSurfaceFollowDecision.nativeWaypointReached(new Vec3d(.44,0,.44),.6f));
        assertFalse(LocalSurfaceFollowDecision.nativeWaypointReached(new Vec3d(.46,0,0),.6f));
    }

    @Test void nativeWidthAcceptanceCannotConsumeTheRealUnsafeCorner(){
        var queries=new ArrayList<Integer>();
        var decision=LocalSurfaceFollowDecision.select(5,13,index->LocalSurfaceFollowDecision.nativeWaypointReached(
            ActorFrameMath.toLocal(POINT.apply(index).subtract(BEFORE),new Vec3d(1,0,0)),.9f),
            index->{queries.add(index);return clear(BODY,BEFORE,POINT.apply(index),PLATE);});
        assertTrue(decision.clear());assertEquals(5,decision.index());assertEquals(List.of(6,5),queries);
    }

    @Test void nativeWidthReachRetainsFourNodeAndTwoQueryCaps(){
        var reached=new ArrayList<Integer>();var queries=new ArrayList<Integer>();
        var decision=LocalSurfaceFollowDecision.select(2,20,index->{reached.add(index);return true;},index->{queries.add(index);return index==2;});
        assertTrue(decision.clear());assertEquals(2,decision.index());assertEquals(List.of(2,3,4,5),reached);assertEquals(List.of(6,2),queries);
    }

}
