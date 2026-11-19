package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.entity.model.FlightEnemyMesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightEnemyMeshTest {
    @Test void wholeHullFitsItsConservativeCollisionEnvelopeAtEveryOrientation() {
        for(var kind:FlightEnemyKind.values())for(var face:FlightEnemyMesh.faces(kind))for(var v:face.vertices()) {
            double radius=Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z());
            assertTrue(Double.isFinite(radius)&&radius<=kind.diameter*.5+1e-5,"rotating hull radius "+kind);
        }
    }
    @Test void facesHaveFiniteUnitNormalsAndOnlyTriangleOrQuadTopology() {
        for(var kind:FlightEnemyKind.values())for(var face:FlightEnemyMesh.faces(kind)) {
            assertTrue(face.vertices().size()==3||face.vertices().size()==4);var n=face.normal();
            assertEquals(1,Math.sqrt(n.x()*n.x()+n.y()*n.y()+n.z()*n.z()),1e-5);
        }
    }
    @Test void eachKindHasItsOwnGeometryAndALitEmitter() {
        var orb=FlightEnemyMesh.faces(FlightEnemyKind.ORB);var rocket=FlightEnemyMesh.faces(FlightEnemyKind.ROCKET);var jet=FlightEnemyMesh.faces(FlightEnemyKind.JET);
        assertNotEquals(orb,rocket);assertNotEquals(orb,jet);assertNotEquals(rocket,jet);
        for(var kind:FlightEnemyKind.values())assertTrue(FlightEnemyMesh.faces(kind).stream().anyMatch(FlightEnemyMesh.Face::emissive));
    }
    @Test void meshesStayInsideTheSmallPerAgentFaceBudget() {
        for(var kind:FlightEnemyKind.values()){int n=FlightEnemyMesh.faces(kind).size();assertTrue(n>=8&&n<=24);}
    }
    @Test void everyClosedHullPlaneFacesAwayFromTheLocalOrigin(){
        for(var kind:FlightEnemyKind.values()){
            long closed=0;
            for(var face:FlightEnemyMesh.faces(kind)){
                if(!face.closedHull())continue;
                closed++;
                var p=face.vertices().getFirst();var n=face.normal();
                assertTrue(p.x()*n.x()+p.y()*n.y()+p.z()*n.z()>0,"outward lit surface: "+kind+" face="+face);
            }
            assertTrue(closed>=8,kind.toString());
        }
    }
    @Test void distantClosedHullDropsBackFacesWhileTheRocketFinsRemainTwoSided(){
        for(var kind:FlightEnemyKind.values()){
            var faces=FlightEnemyMesh.faces(kind,false);
            long visible=faces.stream().filter(f->FlightEnemyMesh.emitFace(kind,f,false,100,30,-60)).count();
            assertTrue(visible>0&&visible<faces.size(),kind.toString());
            assertTrue(faces.stream().allMatch(f->FlightEnemyMesh.emitFace(kind,f,true,100,30,-60)));
            assertTrue(faces.stream().allMatch(f->FlightEnemyMesh.emitFace(kind,f,false,0,0,0)));
        }
        var kind=FlightEnemyKind.ROCKET;
        var fins=FlightEnemyMesh.faces(kind,false).stream().filter(f->!f.closedHull()).toList();
        assertEquals(4,fins.size());
        assertTrue(fins.stream().allMatch(f->FlightEnemyMesh.emitFace(kind,f,false,0,100,0)
            &&FlightEnemyMesh.emitFace(kind,f,false,0,-100,0)));
    }
}
