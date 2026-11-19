package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import com.terminaldetector.drmd.physics.ragdoll.RagdollSimulation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagdollSimulationTest {
	@Test
	void authoredLimitedJetRoutesAFrontCoreHitByHullAndRetainsExactEffectiveMass(){
		var rig=com.terminaldetector.drmd.entity.model.FlightWreckRig.create(com.terminaldetector.drmd.entity.FlightEnemyKind.JET,1,2);
		var axis=new Vec3(1,2,-3).normalized();double angle=.9,s=Math.sin(angle/2);
		var rotation=new Quat(axis.x()*s,axis.y()*s,axis.z()*s,Math.cos(angle/2));
		var simulation=new RagdollSimulation(rig,new Vec3(3,5,-7),rotation,new Vec3(.2,-.1,.3));
		var core=simulation.parts().get(0).body();var localHit=new Vec3(-.2,0,.995);
		var hit=core.position().plus(rotation.rotate(localHit));var impulse=rotation.rotate(new Vec3(0,0,-2));
		assertTrue(simulation.parts().get(1).body().position().minus(hit).lengthSquared()<core.position().minus(hit).lengthSquared(),"negative control: nearest COM would incorrectly select the left wing");
		var before=simulation.parts().stream().map(p->p.body().snapshot()).toList();
		var velocity=core.linearVelocity();var momentum=core.angularMomentum();var offset=hit.minus(core.position());
		var direction=impulse.normalized();double denominator=core.pointImpulseDenominator(offset,direction);
		var pointVelocity=core.velocityAtPoint(offset);
		assertEquals(0,simulation.applyImpulseAt(hit,impulse));
		assertTrue(core.linearVelocity().minus(velocity).minus(impulse.scaled(.1)).length()<1e-12,"10kg authored core receives J/m exactly");
		assertTrue(core.angularMomentum().minus(momentum).minus(offset.cross(impulse)).length()<1e-12,"actual world lever arm supplies r cross J");
		assertEquals(impulse.length()*denominator,core.velocityAtPoint(offset).minus(pointVelocity).dot(direction),1e-11);
		assertTrue(simulation.parts().get(1).body().matches(before.get(1))&&simulation.parts().get(2).body().matches(before.get(2)),"unstruck wings receive no direct impulse");
		for(int tick=0;tick<40;tick++)simulation.step(.05,rotation.rotate(new Vec3(0,-9.81,0)));
		for(int i=0;i<rig.joints().size();i++){
			var joint=rig.joints().get(i);var limits=joint.angularLimits();
			var angles=com.terminaldetector.drmd.physics.ragdoll.RagdollAngularLimit.angles(simulation.parts().get(joint.first()).body().rotation(),simulation.parts().get(joint.second()).body().rotation(),limits);
			assertTrue(angles.swing()<=limits.maxSwing()+.01&&angles.twist()>=limits.minTwist()-.01&&angles.twist()<=limits.maxTwist()+.01,"existing authored swing/twist limits remain effective after off-centre hit");
			assertTrue(simulation.jointError(i)<.03,"existing positional joint remains attached");
		}
	}

	@Test
	void surfaceRoutingPreservesWingHitsAndRejectsNonfiniteInputsBeforeMutation(){
		var rig=com.terminaldetector.drmd.entity.model.FlightWreckRig.create(com.terminaldetector.drmd.entity.FlightEnemyKind.JET,1,2);
		var simulation=new RagdollSimulation(rig,new Vec3(0,0,0),Quat.IDENTITY,new Vec3(0,0,0));
		assertEquals(1,simulation.nearestPartAt(new Vec3(-1,.02,.115)));
		assertEquals(2,simulation.nearestPartAt(new Vec3(1,.02,.115)));
		var before=simulation.parts().stream().map(p->p.body().snapshot()).toList();
		assertThrows(IllegalArgumentException.class,()->simulation.applyImpulseAt(new Vec3(Double.NaN,0,0),new Vec3(1,0,0)));
		assertThrows(IllegalArgumentException.class,()->simulation.applyImpulseAt(new Vec3(0,0,0),new Vec3(0,Double.POSITIVE_INFINITY,0)));
		for(int i=0;i<before.size();i++)assertTrue(simulation.parts().get(i).body().matches(before.get(i)));
	}

	private static RagdollRig twoParts(double firstMass, double secondMass) {
		return new RagdollRig(List.of(
				new RagdollRig.Segment("a", firstMass, new Vec3(.5, .2, .2), new Vec3(0, 0, 0)),
				new RagdollRig.Segment("b", secondMass, new Vec3(.5, .2, .2), new Vec3(1, 0, 0))),
				List.of(new RagdollRig.Joint(0, 1, new Vec3(.5, 0, 0), new Vec3(-.5, 0, 0), 0)));
	}

	@Test
	@DisplayName("gravity accelerates every ragdoll part equally regardless of mass")
	void gravityIsMassIndependent() {
		RagdollSimulation simulation = new RagdollSimulation(
				new RagdollRig(List.of(
						new RagdollRig.Segment("light", 1, new Vec3(.2, .2, .2), new Vec3(0, 0, 0)),
						new RagdollRig.Segment("heavy", 20, new Vec3(.2, .2, .2), new Vec3(2, 0, 0))), List.of()),
				new Vec3(0, 0, 0), Quat.IDENTITY, new Vec3(0, 0, 0));
		for (int i = 0; i < 20; i++) simulation.step(.05, new Vec3(0, -9.81, 0));
		assertEquals(simulation.parts().get(0).body().linearVelocity().y(),
				simulation.parts().get(1).body().linearVelocity().y(), 1e-9);
		assertEquals(-9.81, simulation.parts().get(0).body().linearVelocity().y(), 1e-8);
	}

	@Test
	@DisplayName("ball joint stays connected after an off-centre impulse")
	void jointSurvivesImpulse() {
		RagdollSimulation simulation = new RagdollSimulation(twoParts(2, 1),
				new Vec3(0, 4, 0), Quat.IDENTITY, new Vec3(0, 0, 0));
		simulation.applyImpulse(1, new Vec3(0, 0, 8), new Vec3(.3, .2, 0));
		for (int i = 0; i < 200; i++) simulation.step(.01, new Vec3(0, -9.81, 0));
		assertTrue(simulation.jointError(0) < .02, "joint separated by " + simulation.jointError(0));
		assertTrue(simulation.parts().get(1).body().angularVelocity().length() > .01,
				"off-centre hit should turn the struck part");
	}

	@Test
	@DisplayName("rig rejects duplicate names and out-of-range joints")
	void validationRejectsMalformedRig() {
		var segment = new RagdollRig.Segment("same", 1, new Vec3(.2, .2, .2), new Vec3(0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new RagdollRig(List.of(segment, segment), List.of()));
		assertThrows(IllegalArgumentException.class, () -> new RagdollRig(List.of(segment),
				List.of(new RagdollRig.Joint(0, 1, new Vec3(0, 0, 0), new Vec3(0, 0, 0), 0))));
	}

	@Test
	@DisplayName("world adapter can resolve contacts without entering the pure ragdoll core")
	void collisionAdapterClampsIntegratedParts() {
		RagdollSimulation simulation = new RagdollSimulation(
				new RagdollRig(List.of(new RagdollRig.Segment(
						"body", 2, new Vec3(.2, .2, .2), new Vec3(0, 0, 0))), List.of()),
				new Vec3(0, .1, 0), Quat.IDENTITY, new Vec3(0, -3, 0));
		simulation.step(.05, new Vec3(0, -9.81, 0), 4, 2, (part, fallback) -> {
			if (part.body().position().y() < 0)
				part.body().withPosition(new Vec3(part.body().position().x(), 0, part.body().position().z()))
						.withLinearVelocity(new Vec3(0, 0, 0));
		});
		assertTrue(simulation.parts().get(0).body().position().y() >= 0);
	}


	@Test
	@DisplayName("joint corrections cannot carry a contacted limb through a wall")
	void collisionAdapterRunsBetweenConstraintIterations() {
		RagdollSimulation simulation = new RagdollSimulation(RagdollRig.scoutDrone(),
				new Vec3(4.5, 5, 0), Quat.IDENTITY, new Vec3(0, 0, 0));
		// Give one spar a turn so the test covers changing projected extents, not only axis-aligned boxes.
		simulation.applyImpulse(1, new Vec3(0, 0, 2), new Vec3(.04, .05, 0));
		for (int tick = 0; tick < 120; tick++) {
			simulation.step(.05, new Vec3(-9.81, 0, 0), 4, 8, (part, fallback) -> {
				double radius = projectedXRadius(part);
				double min = part.body().position().x() - radius;
				double max = part.body().position().x() + radius;
				if (min >= 1 || max <= 0) return;
				double towardRight = 1 - min;
				double towardLeft = max;
				Vec3 normal = towardRight <= towardLeft ? new Vec3(1, 0, 0) : new Vec3(-1, 0, 0);
				double depth = Math.min(towardRight, towardLeft);
				part.body().withPosition(part.body().position().plus(normal.scaled(depth + 1e-5)));
				part.body().contactImpulse(normal.scaled(-radius), normal, .72);
			});
		}
		double minimumX = simulation.parts().stream()
				.mapToDouble(part -> part.body().position().x() - projectedXRadius(part)).min().orElseThrow();
		assertTrue(simulation.centreOfMass().x() < 3.5, "sideways gravity did not reach the wall");
		assertTrue(minimumX >= .975, "joint solver tunnelled through the wall by " + (1 - minimumX));
	}

	@Test
	@DisplayName("aggregate motion is mass weighted")
	void aggregateStateUsesMass() {
		RagdollSimulation simulation = new RagdollSimulation(twoParts(3, 1),
				new Vec3(0, 0, 0), Quat.IDENTITY, new Vec3(0, 0, 0));
		simulation.parts().get(0).body().withLinearVelocity(new Vec3(4, 0, 0));
		simulation.parts().get(1).body().withLinearVelocity(new Vec3(0, 0, 0));
		assertEquals(3, simulation.centreVelocity().x(), 1e-9);
		assertEquals(4, simulation.totalMass(), 1e-9);
	}

	private static double projectedXRadius(RagdollSimulation.Part part) {
		Vec3 half = part.segment().halfExtents();
		Quat rotation = part.body().rotation();
		return Math.abs(rotation.rotate(new Vec3(half.x(), 0, 0)).x())
				+ Math.abs(rotation.rotate(new Vec3(0, half.y(), 0)).x())
				+ Math.abs(rotation.rotate(new Vec3(0, 0, half.z())).x());
	}
}
