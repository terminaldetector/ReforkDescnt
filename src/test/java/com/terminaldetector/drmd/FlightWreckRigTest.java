package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.entity.model.*;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightWreckRigTest {
    @Test void eachOriginalFaceBelongsInsideItsOwnRigidComponent(){
        for(var kind:FlightEnemyKind.values())for(double scale:new double[]{.5,1,2}){
            var rig=FlightWreckRig.create(kind,scale);var faces=FlightEnemyMesh.faces(kind);int[] used=new int[rig.segments().size()];
            assertTrue(used.length<=5);
            for(int i=0;i<faces.size();i++){
                int part=FlightWreckRig.partForFace(kind,i);assertTrue(part>=0&&part<used.length);used[part]++;var segment=rig.segments().get(part);var half=segment.halfExtents();var rest=segment.restPosition();
                for(var v:faces.get(i).vertices())assertTrue(Math.abs(v.x()*scale-rest.x())<=half.x()+1e-5&&Math.abs(v.y()*scale-rest.y())<=half.y()+1e-5&&Math.abs(v.z()*scale-rest.z())<=half.z()+1e-5,"authored face inside its physical part: "+kind+"/"+part);
            }
            for(int n:used)assertTrue(n>0,"no invisible leftover scout part");
        }
    }
    @Test void everyJointMeetsAtRestAtEveryScale(){
        for(var kind:FlightEnemyKind.values())for(double scale:new double[]{.5,1,2}){
            var rig=FlightWreckRig.create(kind,scale);
            for(var joint:rig.joints()){
                var a=rig.segments().get(joint.first()).restPosition().plus(joint.firstAnchor());var b=rig.segments().get(joint.second()).restPosition().plus(joint.secondAnchor());
                assertTrue(a.minus(b).length()<1e-10,"no initial anchor explosion");
            }
        }
    }
    @Test void scalingPreservesTopologyAndUsesVolumeMass(){
        for(var kind:FlightEnemyKind.values()){
            var one=FlightWreckRig.create(kind,1);var two=FlightWreckRig.create(kind,2);assertEquals(one.segments().size(),two.segments().size());assertEquals(one.joints().size(),two.joints().size());
            double mass=one.segments().stream().mapToDouble(RagdollRig.Segment::mass).sum(),large=two.segments().stream().mapToDouble(RagdollRig.Segment::mass).sum();assertEquals(mass*8,large,1e-10);
        }
        assertThrows(IllegalArgumentException.class,()->FlightWreckRig.create(FlightEnemyKind.JET,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->FlightWreckRig.create(FlightEnemyKind.JET,0));
    }
    @Test void legacyScoutGeometryAndMassRemainExactlyTheSame(){
        var old=RagdollRig.scoutDrone();var loaded=FlightWreckRig.create(null,1);assertEquals(old.segments(),loaded.segments());assertEquals(old.joints(),loaded.joints());
    }
}
