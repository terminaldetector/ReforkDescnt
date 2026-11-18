package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.entity.model.*;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import com.terminaldetector.drmd.physics.ragdoll.RagdollSimulation;
import com.terminaldetector.drmd.physics.ragdoll.RagdollAngularLimit;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
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
    @Test void onlyVersionTwoJetWingsUseAuthoredLimitsAndScalingKeepsTheirAngularContract(){
        for(var kind:FlightEnemyKind.values())for(double scale:new double[]{.5,1,2,16}){
            var legacy=FlightWreckRig.create(kind,scale,FlightWreckRig.LEGACY_VERSION);
            var limited=FlightWreckRig.create(kind,scale,FlightWreckRig.LIMITED_JET_VERSION);
            assertEquals(legacy.segments(),limited.segments(),"versioning must not rewrite hull mass or geometry");
            for(int i=0;i<legacy.joints().size();i++){
                assertNull(legacy.joints().get(i).angularLimits());
                var limits=limited.joints().get(i).angularLimits();
                if(kind==FlightEnemyKind.JET){
                    assertNotNull(limits);assertEquals(Math.toRadians(35),limits.maxSwing());
                    assertEquals(Math.toRadians(-12),limits.minTwist());assertEquals(Math.toRadians(12),limits.maxTwist());
                    assertEquals(limits,FlightWreckRig.create(kind,1,2).joints().get(i).angularLimits());
                }else assertNull(limits);
            }
        }
        assertThrows(IllegalArgumentException.class,()->FlightWreckRig.create(FlightEnemyKind.JET,1,99));
    }
    @Test void actualLimitedJetSolvesBothWingsWhileItsLegacyCounterpartCanOverRotate(){
        Quat root=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3),1.1);
        double legacyMaximum=0;
        for(int version:new int[]{1,2}){
            var rig=FlightWreckRig.create(FlightEnemyKind.JET,1,version);
            var simulation=new RagdollSimulation(rig,new Vec3(0,4,0),root,new Vec3(0,0,0));
            simulation.applyImpulse(1,root.rotate(new Vec3(0,0,8)),new Vec3(.4,.08,0));
            for(int step=0;step<200;step++){
                simulation.step(.01,root.rotate(new Vec3(0,-9.81,0)));
                for(int i=0;i<rig.joints().size();i++){
                    var joint=rig.joints().get(i);var limits=FlightWreckRig.create(FlightEnemyKind.JET,1,2).joints().get(i).angularLimits();
                    var angles=RagdollAngularLimit.angles(simulation.parts().get(joint.first()).body().rotation(),simulation.parts().get(joint.second()).body().rotation(),limits);
                    if(version==1)legacyMaximum=Math.max(legacyMaximum,angles.swing());
                    else{
                        assertTrue(angles.swing()<=limits.maxSwing()+.01,"JET wing swing "+angles.swing());
                        assertTrue(angles.twist()>=limits.minTwist()-.01&&angles.twist()<=limits.maxTwist()+.01,"JET wing twist "+angles.twist());
                        assertTrue(simulation.jointError(i)<.02,"JET anchor remains attached");
                    }
                }
            }
        }
        assertTrue(legacyMaximum>Math.toRadians(55),"counterfactual must actually over-rotate the unlimited legacy rig");
    }
}
