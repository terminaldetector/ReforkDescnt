package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagdollPortalTransferTest {
	private static final Vec3 ZERO=new Vec3(0,0,0), X=new Vec3(1,0,0), NEG_Y=new Vec3(0,-1,0);
	private static final Vec3 SOURCE=new Vec3(3,5,-7), DESTINATION=new Vec3(-12,8,15);
	private static final D6PortalTransform QUARTER=D6PortalTransform.of(SOURCE,X,DESTINATION,NEG_Y);

	@Test void wholeArticulatedStateRotatesWithoutChangingEnergyMassOrJointContracts(){
		var simulation=fixture();
		var rig=simulation.rig();
		var before=simulation.parts().stream().map(part->part.body().snapshot()).toList();
		var positions=simulation.parts().stream().map(part->part.body().position()).toList();
		var velocities=simulation.parts().stream().map(part->part.body().linearVelocity()).toList();
		var momenta=simulation.parts().stream().map(part->part.body().angularMomentum()).toList();
		var angular=simulation.parts().stream().map(part->part.body().angularVelocity()).toList();
		var forces=simulation.parts().stream().map(part->part.body().accumulatedForce()).toList();
		var torques=simulation.parts().stream().map(part->part.body().accumulatedTorque()).toList();
		double energy=energy(simulation), mass=simulation.totalMass(), jointError=simulation.jointError(0);
		var limits=rig.joints().get(0).angularLimits();
		var angles=RagdollAngularLimit.angles(simulation.parts().get(0).body().rotation(),simulation.parts().get(1).body().rotation(),limits);
		var prepared=RagdollPortalTransfer.prepare(simulation,QUARTER,new Vec3(.3,.2,-.1)).orElseThrow();
		for(int i=0;i<before.size();i++)assertTrue(simulation.parts().get(i).body().matches(before.get(i)),"preflight must not temporarily turn live limbs");
		assertTrue(prepared.apply());assertFalse(prepared.apply(),"one commit must not rotate the body twice");
		assertSame(rig,simulation.rig());assertSame(limits,simulation.rig().joints().get(0).angularLimits());
		assertEquals(mass,simulation.totalMass(),0);assertEquals(energy,energy(simulation),1e-10);
		assertEquals(jointError,simulation.jointError(0),1e-12);
		var movedAngles=RagdollAngularLimit.angles(simulation.parts().get(0).body().rotation(),simulation.parts().get(1).body().rotation(),limits);
		assertEquals(angles.swing(),movedAngles.swing(),1e-12);assertEquals(angles.twist(),movedAngles.twist(),1e-12);
		for(int i=0;i<simulation.parts().size();i++){
			var body=simulation.parts().get(i).body();
			near(DESTINATION.plus(quarter(positions.get(i).minus(SOURCE))),body.position());
			near(quarter(velocities.get(i)),body.linearVelocity());near(quarter(momenta.get(i)),body.angularMomentum());
			near(quarter(angular.get(i)),body.angularVelocity());
			near(quarter(forces.get(i)),body.accumulatedForce());near(quarter(torques.get(i)),body.accumulatedTorque());
		}
	}

	@Test void remainderDefinesOnlyTheDestinationSweepAndInverseRestoresEveryPart(){
		var simulation=fixture();var original=simulation.parts().stream().map(part->part.body().snapshot()).toList();
		Vec3 remainder=new Vec3(2,-.25,.5);
		var prepared=RagdollPortalTransfer.prepare(simulation,QUARTER,remainder).orElseThrow();
		Vec3 plannedCentre=prepared.destinationCentre();
		for(int i=0;i<prepared.destinationParts().size();i++)near(remainder,prepared.destinationParts().get(i).position().minus(prepared.sweepStartParts().get(i).position()));
		assertTrue(prepared.apply());near(plannedCentre,simulation.centreOfMass());near(plannedCentre,prepared.destinationCentre());
		assertTrue(RagdollPortalTransfer.prepare(simulation,QUARTER.inverse(),ZERO).orElseThrow().apply());
		for(int i=0;i<simulation.parts().size();i++){
			var body=simulation.parts().get(i).body();near(original.get(i).position(),body.position());
			for(var axis:List.of(X,new Vec3(0,1,0),new Vec3(0,0,1)))near(original.get(i).rotation().rotate(axis),body.rotation().rotate(axis));
		}
	}

	@Test void aLateInputToTheLastLimbInvalidatesTheWholeTokenBeforeAnyFirstLimbMoves(){
		var simulation=fixture();var prepared=RagdollPortalTransfer.prepare(simulation,QUARTER,ZERO).orElseThrow();
		var first=simulation.parts().get(0).body();var last=simulation.parts().get(1).body();var firstBefore=first.snapshot();
		last.applyTorque(new Vec3(.1,.2,.3));var lastBefore=last.snapshot();
		assertFalse(prepared.current());assertFalse(prepared.apply());
		assertTrue(first.matches(firstBefore));assertTrue(last.matches(lastBefore),"refusal must preserve the newly queued input too");
	}

	@Test void pendingForceAndTorqueAreRotatedThenConsumedByExactlyOneNormalForceIntegration(){
		var body=new D6PhysicsBody().withMass(2).withInertia(D6Mat3.diagonal(.7,1.5,3))
				.withPosition(SOURCE).withRotation(turn(new Vec3(1,2,3),.8))
				.withLinearVelocity(new Vec3(1,-2,3)).withAngularMomentum(new Vec3(.2,-.3,.4));
		body.applyForce(new Vec3(4,6,-2));body.applyTorque(new Vec3(.6,-.2,.8));
		QUARTER.apply(body,null);
		near(new Vec3(2,1,3),body.linearVelocity());near(new Vec3(.3,.2,.4),body.angularMomentum());
		near(new Vec3(-6,4,-2),body.accumulatedForce());near(new Vec3(.2,.6,.8),body.accumulatedTorque());
		body.integrateForces(.025);
		near(new Vec3(1.925,1.05,2.975),body.linearVelocity());near(new Vec3(.305,.215,.42),body.angularMomentum());
		var velocity=body.linearVelocity();var momentum=body.angularMomentum();
		body.integrateForces(.025);near(velocity,body.linearVelocity());near(momentum,body.angularMomentum());
		near(ZERO,body.accumulatedForce());near(ZERO,body.accumulatedTorque());
	}

	@Test void unsupportedScaleAndNonfiniteDestinationRefuseWithoutMutatingEvenOnePart(){
		var simulation=fixture();var before=simulation.parts().stream().map(part->part.body().snapshot()).toList();
		assertTrue(RagdollPortalTransfer.prepare(simulation,new D6PortalTransform(SOURCE,X,DESTINATION,NEG_Y,2),ZERO).isEmpty());
		assertTrue(RagdollPortalTransfer.prepare(simulation,D6PortalTransform.of(SOURCE,X,new Vec3(Double.NaN,0,0),NEG_Y),ZERO).isEmpty());
		assertTrue(RagdollPortalTransfer.prepare(simulation,QUARTER,new Vec3(Double.POSITIVE_INFINITY,0,0)).isEmpty());
		for(int i=0;i<before.size();i++)assertTrue(simulation.parts().get(i).body().matches(before.get(i)));
	}

	private static RagdollSimulation fixture(){
		var limits=new RagdollRig.AngularLimits(turn(X,.2),turn(new Vec3(0,1,0),-.3),.8,-.4,.5);
		var rig=new RagdollRig(List.of(new RagdollRig.Segment("a",3,new Vec3(.3,.2,.1),ZERO),
				new RagdollRig.Segment("b",1,new Vec3(.1,.2,.25),new Vec3(.6,0,0))),
				List.of(new RagdollRig.Joint(0,1,new Vec3(.3,0,0),new Vec3(-.3,0,0),0,limits)));
		var simulation=new RagdollSimulation(rig,SOURCE,turn(new Vec3(1,2,-3),.9),new Vec3(1,2,-.5));
		int index=0;
		for(var part:simulation.parts()){
			var b=part.body();b.withPosition(b.position().plus(new Vec3(index*.03,-index*.04,.01)))
					.withRotation(ImmPtlQuaternions.hamiltonProduct(b.rotation(),turn(new Vec3(2,-1,1),.2*index)).normalized())
					.withAngularMomentum(new Vec3(.05+index*.03,-.06,.04));
			b.applyForce(new Vec3(.4+index,-.2,.6));b.applyTorque(new Vec3(.02,.01+index*.03,-.04));index++;
		}
		return simulation;
	}
	private static double energy(RagdollSimulation simulation){double e=0;for(var p:simulation.parts()){var b=p.body();e+=.5*b.mass()*b.linearVelocity().lengthSquared()+.5*b.angularVelocity().dot(b.angularMomentum());}return e;}
	private static Vec3 quarter(Vec3 v){return new Vec3(-v.y(),v.x(),v.z());}
	private static Quat turn(Vec3 axis,double angle){var v=axis.normalized().scaled(Math.sin(angle/2));return new Quat(v.x(),v.y(),v.z(),Math.cos(angle/2));}
	private static void near(Vec3 a,Vec3 b){assertTrue(a.minus(b).length()<1e-11,()->a+" != "+b);}
}
