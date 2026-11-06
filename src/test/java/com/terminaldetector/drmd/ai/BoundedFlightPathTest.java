package com.terminaldetector.drmd.ai;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BoundedFlightPathTest {
    private static BoundedFlightPath.Cell p(int x,int y,int z){return new BoundedFlightPath.Cell(x,y,z);}
    private static void finish(BoundedFlightPath s){for(int i=0;i<10000&&s.status()==BoundedFlightPath.Status.SEARCHING;i++)assertTrue(s.step(3)<=3);assertNotEquals(BoundedFlightPath.Status.SEARCHING,s.status());}
    @Test void climbsToAHighOpeningInsteadOfCuttingThroughTheWall() {
        BoundedFlightPath.Space space=(a,b)->b.x()!=0||(b.y()==3&&b.z()==0);
        var s=new BoundedFlightPath(p(-3,0,0),p(3,0,0),8,1024,space);finish(s);
        assertEquals(BoundedFlightPath.Status.FOUND,s.status());assertTrue(s.path().contains(p(0,3,0)));
        for(int i=1;i<s.path().size();i++){var a=s.path().get(i-1);var b=s.path().get(i);assertTrue(space.clear(a,b));assertEquals(1,Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z()));}
    }
    @Test void goesSidewaysThroughAnOpeningInACeiling() {
        var s=new BoundedFlightPath(p(0,-3,0),p(0,3,0),8,1024,(a,b)->b.y()!=0||(b.x()==3&&b.z()==0));finish(s);
        assertEquals(BoundedFlightPath.Status.FOUND,s.status());assertTrue(s.path().contains(p(3,0,0)));
    }
    @Test void testsSweptEdgesEvenWhenTheirEndpointsAreFree() {
        var s=new BoundedFlightPath(p(0,0,0),p(1,0,0),3,128,(a,b)->!(a.equals(p(0,0,0))&&b.equals(p(1,0,0))));finish(s);
        assertEquals(BoundedFlightPath.Status.FOUND,s.status());assertTrue(s.path().size()>2);
    }
    @Test void neverTakesADiagonalShortcutBetweenTwoIsolatedCells() {
        var cells=Set.of(p(0,0,0),p(1,1,0));var s=new BoundedFlightPath(p(0,0,0),p(1,1,0),3,128,(a,b)->cells.contains(b));finish(s);
        assertEquals(BoundedFlightPath.Status.NO_ROUTE,s.status());assertTrue(s.path().isEmpty());
    }
    @Test void capsMemoryAndClearanceCallsWithoutClaimingTheGoalIsUnreachable() {
        int[] calls={0};var s=new BoundedFlightPath(p(0,0,0),p(8,8,8),10,8,(a,b)->{calls[0]++;return true;});
        while(s.status()==BoundedFlightPath.Status.SEARCHING){int before=calls[0];assertTrue(s.step(2)<=2);assertTrue(calls[0]-before<=12);assertTrue(s.visitedCells()<=8);}
        assertEquals(BoundedFlightPath.Status.LIMIT_REACHED,s.status());
    }
    @Test void handlesNegativeCoordinatesDeterministically() {
        var a=new BoundedFlightPath(p(-100,-100,-100),p(-98,-96,-102),8,256,(u,v)->true);
        var b=new BoundedFlightPath(p(-100,-100,-100),p(-98,-96,-102),8,256,(u,v)->true);finish(a);finish(b);assertEquals(a.path(),b.path());assertEquals(9,a.path().size());
    }
    @Test void outsideHorizonDoesNotReadSpaceAndZeroBudgetDoesNoWork() {
        int[] calls={0};var s=new BoundedFlightPath(p(0,0,0),p(49,0,0),48,128,(a,b)->{calls[0]++;return true;});assertEquals(BoundedFlightPath.Status.LIMIT_REACHED,s.status());assertEquals(0,calls[0]);
        var inside=new BoundedFlightPath(p(0,0,0),p(1,0,0),2,8,(a,b)->{calls[0]++;return true;});assertEquals(0,inside.step(0));assertEquals(0,calls[0]);assertThrows(IllegalArgumentException.class,()->inside.step(129));
    }
    @Test void alreadyAtGoalNeedsNoGeometryQueries() {
        var s=new BoundedFlightPath(p(1,2,3),p(1,2,3),2,8,(a,b)->{fail("no movement");return false;});finish(s);assertEquals(List.of(p(1,2,3)),s.path());
    }
    @Test void combatSizedBodiesReachAnOffsetOpeningWithinTheExisting512CellBudget(){
        for(double half:new double[]{.45,.9,1.2}){
            int width=(int)Math.ceil(half*2)+1;double originY=half==.45?.5:1.5;int[] calls={0};
            BoundedFlightPath.Space space=(a,b)->{
                calls[0]++;
                if(.5+Math.max(a.x(),b.x())+half<=6||.5+Math.min(a.x(),b.x())-half>=7)return true;
                return originY+Math.min(a.y(),b.y())-half>=4&&originY+Math.max(a.y(),b.y())+half<=4+width
                        &&.5+Math.min(a.z(),b.z())-half>=0&&.5+Math.max(a.z(),b.z())+half<=width;
            };
            var search=new BoundedFlightPath(p(0,0,0),p(18,half==.45?1:0,0),32,512,space,2);
            for(int i=0;i<1000&&search.status()==BoundedFlightPath.Status.SEARCHING;i++){
                int before=calls[0];assertTrue(search.step(8)<=8);assertTrue(calls[0]-before<=48);assertTrue(search.visitedCells()<=512);
            }
            assertEquals(BoundedFlightPath.Status.FOUND,search.status(),"actual compound clearance half="+half);
            for(int i=1;i<search.path().size();i++)assertTrue(space.clear(search.path().get(i-1),search.path().get(i)));
        }
    }
    @Test void rejectsUnboundedCombatHeuristicConfiguration(){
        assertThrows(IllegalArgumentException.class,()->new BoundedFlightPath(p(0,0,0),p(1,0,0),8,512,(a,b)->true,0));
        assertThrows(IllegalArgumentException.class,()->new BoundedFlightPath(p(0,0,0),p(1,0,0),8,512,(a,b)->true,5));
    }

}
