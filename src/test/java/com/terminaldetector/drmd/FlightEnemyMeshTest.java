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
}
