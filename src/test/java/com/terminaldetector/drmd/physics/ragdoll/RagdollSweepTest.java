package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollSweepTest {
	@Test void aThinWallIsHitWhenBothThirtyFiveMetrePerSecondSubstepEndpointsAreClear(){
		var half=new Vec3(.05,.05,.05);var wallMin=new Vec3(.1,-1,-1);var wallMax=new Vec3(.2875,1,1);
		double distance=35*.0125;
		assertTrue(.05<wallMin.x()&&distance-.05>wallMax.x(),"negative control: old endpoint boxes miss the wall completely");
		var hit=RagdollSweep.firstContact(new Vec3(0,0,0),new Vec3(distance,0,0),half,wallMin,wallMax);
		assertNotNull(hit);assertEquals(.05/distance,hit.fraction(),1e-12);assertEquals(new Vec3(-1,0,0),hit.normal());
	}
	@Test void allSixApproachAxesReturnTheEarliestOpposingFace(){
		for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
			double[] to={0,0,0},low={-1,-1,-1},high={1,1,1},normal={0,0,0};to[axis]=sign*.4375;normal[axis]=-sign;
			low[axis]=sign>0?.1:-.2875;high[axis]=sign>0?.2875:-.1;
			var hit=RagdollSweep.firstContact(new Vec3(0,0,0),v(to),new Vec3(.05,.05,.05),v(low),v(high));
			assertNotNull(hit);assertEquals(4.0/35,hit.fraction(),1e-12);assertEquals(v(normal),hit.normal());
		}
	}
	@Test void parallelGrazingDepartureAndInitialOverlapDoNotCreateAnEnteringImpulse(){
		var half=new Vec3(.05,.05,.05);var low=new Vec3(.1,0,-1);var high=new Vec3(.2875,.2,1);
		assertNull(RagdollSweep.firstContact(new Vec3(.05,-1,0),new Vec3(.05,1,0),half,low,high));
		assertNull(RagdollSweep.firstContact(new Vec3(.05,.1,0),new Vec3(-1,.1,0),half,low,high));
		assertNull(RagdollSweep.firstContact(new Vec3(.15,.1,0),new Vec3(-1,.1,0),half,low,high));
		assertNull(RagdollSweep.firstContact(new Vec3(0,2,0),new Vec3(1,2,0),half,low,high));
	}
	@Test void enteringAtStartAndAtEndRemainContactsAndBadValuesAreRejected(){
		var half=new Vec3(.05,.05,.05);var low=new Vec3(.1,-1,-1);var high=new Vec3(.2875,1,1);
		assertEquals(0,RagdollSweep.firstContact(new Vec3(.05,0,0),new Vec3(1,0,0),half,low,high).fraction(),0);
		assertEquals(1,RagdollSweep.firstContact(new Vec3(0,0,0),new Vec3(.05,0,0),half,low,high).fraction(),1e-12);
		assertThrows(IllegalArgumentException.class,()->RagdollSweep.firstContact(new Vec3(Double.NaN,0,0),new Vec3(1,0,0),half,low,high));
	}
	private static Vec3 v(double[] a){return new Vec3(a[0],a[1],a[2]);}
}
