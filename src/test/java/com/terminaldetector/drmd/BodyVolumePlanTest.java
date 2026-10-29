package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.contraption.BodyVolumePlan;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyVolumePlanTest {
    private BodyBounds box(double x,double y,double z,double sx,double sy,double sz){return new BodyBounds(new Vec3(x,y,z),new Vec3(x+sx,y+sy,z+sz));}
    @Test void longHullLoadsItsFarEndAndEmptyInteriorInsteadOfOnlyThePivot() {
        var plan=BodyVolumePlan.columns(box(0,2156,0,128,16,16),-16384,16384);
        assertEquals(30,plan.size());assertTrue(plan.stream().anyMatch(c->c.x()==7&&c.z()==0&&c.minY()<=134&&c.maxY()>=135));
        assertTrue(plan.stream().anyMatch(c->c.x()==3&&c.z()==0),"empty interior is part of the full ship volume");
        assertTrue(plan.stream().noneMatch(c->c.z()>1||c.z()<-1),"no pivot sphere of unrelated terrain");
    }
    @Test void negativeCoordinatesUseFloorDivisionOnAllThreeAxes() {
        var plan=BodyVolumePlan.columns(box(-127,-513,-17,2,2,2),-16384,16384);
        assertTrue(plan.stream().anyMatch(c->c.x()==-8&&c.z()==-2&&c.minY()<=-33));
        assertEquals(plan.size(),plan.stream().map(BodyVolumePlan.Column::key).distinct().count());
    }
    @Test void verticalExtentIsClippedToTheWorldWithoutChangingItsHorizontalVolume() {
        var plan=BodyVolumePlan.columns(box(0,-70,0,16,400,16),-64,320);
        assertTrue(plan.stream().allMatch(c->c.minY()==-4&&c.maxY()==19));
        assertTrue(BodyVolumePlan.columns(box(0,400,0,1,1,1),-64,320).isEmpty());
    }
    @Test void oneColumnViewRepresentsEveryRequestedYIncludingOddHeightRanges() {
        for(int lo=-20;lo<=20;lo++)for(int hi=lo;hi<=lo+24;hi++) {
            var c=new BodyVolumePlan.Column(0,0,lo,hi);
            for(int y=lo;y<=hi;y++)assertTrue(Math.abs(y-c.centerY())<=c.radiusY());
        }
    }
    @Test void invalidOrUnboundedRequestsFailBeforeAWorldTicketCouldBeAdded() {
        assertThrows(IllegalArgumentException.class,()->BodyVolumePlan.columns(box(0,0,0,100000,1,100000),-16384,16384));
        assertThrows(IllegalArgumentException.class,()->BodyVolumePlan.columns(box(40000000,0,0,1,1,1),-16384,16384));
        assertThrows(IllegalArgumentException.class,()->new BodyVolumePlan.Column(0,0,3,2));
    }
}
