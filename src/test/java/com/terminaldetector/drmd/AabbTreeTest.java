package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AabbTreeTest {
    private static BodyBounds box(double x,double y,double z,double edge) {
        return new BodyBounds(new Vec3(x,y,z),new Vec3(x+edge,y+edge,z+edge));
    }
    @Test void findsTheFarEdgeOfOneLargeLeafAtArbitraryPositiveAndNegativeHeight() {
        var tree=new AabbTree<Integer>();
        tree.update(1,new BodyBounds(new Vec3(-200,-9000,-80),new Vec3(200,-8900,80)));
        tree.update(2,new BodyBounds(new Vec3(-200,9000,-80),new Vec3(200,9100,80)));
        assertEquals(List.of(1),tree.query(box(199,-8990,0,.2)).values());
        assertEquals(List.of(2),tree.query(box(-199,9099,0,.2)).values());assertEquals(2,tree.size());
        assertTrue(tree.query(box(0,0,0,1)).values().isEmpty());
    }
    @Test void smallMotionRetainsTheLeafButQueriesUseItsUpdatedExactBounds() {
        var tree=new AabbTree<Integer>();assertTrue(tree.update(7,box(0,0,0,1)));
        assertFalse(tree.update(7,box(.1,0,0,1)));assertEquals(1,tree.size());
        assertTrue(tree.query(box(0,0,0,.05)).values().isEmpty());
        assertEquals(List.of(7),tree.query(box(1.05,0,0,.01)).values());
        assertTrue(tree.update(7,box(500,500,500,1)));assertTrue(tree.query(box(0,0,0,2)).values().isEmpty());
        assertEquals(List.of(7),tree.query(box(500,500,500,1)).values());
    }
    @Test void orderedInsertionsStayBalancedAndDistantHullsArePruned() {
        var tree=new AabbTree<Integer>();for(int i=0;i<4096;i++)tree.update(i,box(i*64,-2000,0,2));
        assertTrue(tree.height()<32,"height="+tree.height());
        var q=tree.query(box(2048*64,-2000,0,2));assertEquals(List.of(2048),q.values());
        System.out.println("BODY_INDEX_PRUNE bodies="+tree.size()+" nodes="+q.visitedNodes()+" height="+tree.height()+" hits="+q.values().size());
        assertTrue(q.visitedNodes()<100,"nodes="+q.visitedNodes());
    }
    @Test void densePhysicalContactsAreNeverSilentlyTruncatedOrDuplicated() {
        var tree=new AabbTree<Integer>();for(int i=0;i<2048;i++)tree.update(i,box(0,0,0,2));
        var result=tree.query(box(1,1,1,.1));assertEquals(2048,result.values().size());
        assertEquals(2048,new HashSet<>(result.values()).size());assertTrue(tree.height()<32);
    }
    @Test void insertMoveAndRemoveMatchBruteForceOverThousandsOfDeterministicChanges() {
        var tree=new AabbTree<Integer>();var expected=new HashMap<Integer,BodyBounds>();var random=new Random(285019);
        for(int step=0;step<6000;step++) {
            int id=random.nextInt(180);
            if(random.nextInt(5)==0){assertEquals(expected.remove(id)!=null,tree.remove(id));}
            else {var b=box(random.nextInt(160)-80,random.nextInt(20000)-10000,random.nextInt(160)-80,1+random.nextInt(80));expected.put(id,b);tree.update(id,b);}
            var query=box(random.nextInt(200)-100,random.nextInt(20000)-10000,random.nextInt(200)-100,100);
            var actual=tree.query(query).values();var wanted=new HashSet<Integer>();expected.forEach((k,b)->{if(b.intersects(query))wanted.add(k);});
            assertEquals(wanted,new HashSet<>(actual),"step="+step);assertEquals(wanted.size(),actual.size());assertEquals(expected.size(),tree.size());
            if(step%50==0)assertEquals(expected.keySet(),new HashSet<>(tree.query(box(-20000,-20000,-20000,40000)).values()));
        }
        for(int id:List.copyOf(expected.keySet()))assertTrue(tree.remove(id));
        assertEquals(0,tree.size());assertEquals(-1,tree.height());assertTrue(tree.query(box(0,0,0,1)).values().isEmpty());
    }
    @Test void clearingAndReusingIdsCannotRetainOldWorldGeometry() {
        var tree=new AabbTree<Integer>();tree.update(1,box(0,0,0,10));tree.clear();tree.update(1,box(100,100,100,10));
        assertTrue(tree.query(box(0,0,0,10)).values().isEmpty());assertEquals(List.of(1),tree.query(box(100,100,100,10)).values());
        assertTrue(tree.remove(1));assertFalse(tree.remove(1));assertEquals(0,tree.query(box(0,0,0,1)).visitedNodes());
    }
}
