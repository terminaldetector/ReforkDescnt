package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RagdollRestCertificateTest {
    private static final Vec3 ZERO=new Vec3(0,0,0),UP=new Vec3(0,1,0),GRAVITY=new Vec3(0,-9.81,0);
    @Test void wholeRigCanBeSupportedByTwoPartsWhileAnInternalPartIsAboveTheFloor(){
        var c=new RagdollRestCertificate();c.begin(new Vec3(0,1,0),GRAVITY,10,2,.05);
        c.contact(0,new Vec3(-1,.5,0),new Vec3(0,2.4525,0),ZERO,UP,0);
        c.contact(2,new Vec3(1,.5,0),new Vec3(0,2.4525,0),ZERO,UP,0);
        assertTrue(c.balanced());assertEquals(java.util.Set.of(0,2),c.supports());
        assertEquals(0,c.linearResidual(),1e-12);assertEquals(0,c.angularResidual(),1e-12);
    }
    @Test void independentFlatPartsDoNotHideAWholeRigTorqueOrMissingSupportImpulse(){
        var c=new RagdollRestCertificate();c.begin(new Vec3(0,1,0),GRAVITY,10,2,.05);
        c.contact(0,new Vec3(1,.5,0),new Vec3(0,4.905,0),ZERO,UP,0);
        assertFalse(c.balanced(),"full vertical reaction away from COM leaves a real rig torque");
        c.begin(new Vec3(0,1,0),GRAVITY,10,2,.05);c.contact(0,ZERO,new Vec3(0,1,0),ZERO,UP,0);
        assertFalse(c.balanced(),"quiet endpoint cannot invent the missing gravity reaction");
    }
    @Test void wallAndDifferentSupportPlanesRemainFullSimulation(){
        var c=new RagdollRestCertificate();c.begin(ZERO,GRAVITY,10,2,.05);
        c.contact(0,ZERO,new Vec3(0,4.905,0),ZERO,new Vec3(1,0,0),0);assertFalse(c.balanced());
        c.begin(ZERO,GRAVITY,10,2,.05);c.contact(0,ZERO,new Vec3(0,2.4525,0),ZERO,UP,0);c.contact(1,ZERO,new Vec3(0,2.4525,0),ZERO,UP,1);assertFalse(c.balanced());
    }
    @Test void supportFootprintsBelongToActualContactPoseRatherThanLaterJointCorrectedEndpoint(){
        var c=new RagdollRestCertificate();c.begin(new Vec3(0,1,0),GRAVITY,10,2,.05);
        c.contact(0,new Vec3(-1,.5,0),new Vec3(0,2.4525,0),ZERO,UP,0,-1.2,-.8,-.2,.2);
        c.contact(2,new Vec3(1,.5,0),new Vec3(0,2.4525,0),ZERO,UP,0,.8,1.2,-.2,.2);
        assertTrue(c.balanced());var footprints=c.footprints();assertEquals(2,footprints.size());
        assertEquals(0,footprints.get(0).plane(),0);assertEquals(-1.2,footprints.get(0).minX(),0);
        // The rig may support a limb through its real joints after the last contact iteration.
        // No endpoint argument can overwrite the contact geometry used by the measured wrench.
        var endpoint=new Vec3(-1,.5003,0);assertTrue(endpoint.y()-.5>.0001);
        assertEquals(0,c.footprints().get(0).plane(),0);assertTrue(c.balanced());
    }
    @Test void allArticulatedPartsNeedTwentyConsecutiveQuietIntervalsAndFiniteSleepWindows(){
        var c=new RagdollSleepController();var poses=List.of(new RagdollSleepController.Pose(ZERO,Quat.IDENTITY,1),new RagdollSleepController.Pose(new Vec3(1,1,0),Quat.IDENTITY,.5));
        assertFalse(c.observe(true,poses,.01,.00001,.0002));
        for(int t=0;t<19;t++)assertFalse(c.observe(true,poses,.01,.00001,.0002));
        assertTrue(c.observe(true,poses,.01,.00001,.0002));c.sleep();
        for(int t=0;t<20;t++)assertTrue(c.skipTick());assertFalse(c.skipTick(),"a new actual solver observation is mandatory after20skips");
        c.wake();assertFalse(c.ready());
    }
    @Test void oneMovingLimbOrEnergyOrInterestVetoInvalidatesTheWholeCertificate(){
        for(int veto=0;veto<4;veto++){
            var c=new RagdollSleepController();var pose=new RagdollSleepController.Pose(ZERO,Quat.IDENTITY,1);var poses=List.of(pose,pose);
            for(int t=0;t<21;t++)c.observe(true,poses,.01,0,.0002);assertTrue(c.ready());
            var changed=List.of(pose,new RagdollSleepController.Pose(new Vec3(.002,0,0),Quat.IDENTITY,1));
            assertFalse(c.observe(veto!=0,veto==1?changed:poses,veto==2?.021:.01,veto==3?.001:0,.0002));assertFalse(c.ready());
        }
    }
}
