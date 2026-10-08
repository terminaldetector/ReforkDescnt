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
}
