package com.terminaldetector.drmd.physics;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravyPhysicsTest {
	@Test
	void modeLaunchProfilesRemainMechanicallyDistinct() {
		assertTrue(GravyPhysics.Mode.RAIL.launchSpeed() >= GravyPhysics.Mode.SWARM.launchSpeed() + 10,
				"the heavy rail needs a visibly faster launch than the swarm volley");
		assertTrue(GravyPhysics.Mode.SHIFT.launchSpeed() < GravyPhysics.Mode.RAIL.launchSpeed(),
				"the field thrower pays for gravity conversion rather than rail speed");
	}

	@Test
	void railHoldDistanceKeepsLongBodiesOutOfTheCamera() {
		assertEquals(GravyPhysics.Mode.RAIL.holdDistance(),
				GravyPhysics.holdDistance(GravyPhysics.Mode.RAIL,.5),1.0e-9);
		assertEquals(GravyPhysics.Mode.RAIL.holdDistance()+3.5,
				GravyPhysics.holdDistance(GravyPhysics.Mode.RAIL,4),1.0e-9);
		assertEquals(GravyPhysics.Mode.SWARM.holdDistance(),
				GravyPhysics.holdDistance(GravyPhysics.Mode.SWARM,20),1.0e-9);
	}

	@Test
	void weaponProfilesEnforceSeparateTargetAndMassBudgets() {
		assertTrue(GravyPhysics.Mode.SWARM.accepts(7, 29, 3));
		assertFalse(GravyPhysics.Mode.SWARM.accepts(8, 10, 1), "swarm count is bounded");
		assertFalse(GravyPhysics.Mode.SWARM.accepts(3, 31, 2), "swarm mass is bounded");
		assertTrue(GravyPhysics.Mode.RAIL.accepts(0, 0, 300), "rail accepts one heavy body");
		assertFalse(GravyPhysics.Mode.RAIL.accepts(1, 1, 1), "rail never inherits a swarm list");
		assertFalse(GravyPhysics.Mode.SHIFT.accepts(0, 0, 120), "field thrower cannot tow a whole tower");
	}

	@Test
	void springHasSameAccelerationAcrossMassAndAHardPerTickCap() {
		Vec3d delta = new Vec3d(.15, -.08, .04);
		Vec3d velocity = new Vec3d(.2, 0, -.1);
		Vec3d light = GravyPhysics.springImpulse(GravyPhysics.Mode.RAIL, delta, velocity, 1);
		Vec3d heavy = GravyPhysics.springImpulse(GravyPhysics.Mode.RAIL, delta, velocity, 10);
		assertEquals(light.x, heavy.x / 10, 1.0e-9);
		assertEquals(light.y, heavy.y / 10, 1.0e-9);
		assertEquals(light.z, heavy.z / 10, 1.0e-9);

		Vec3d clamped = GravyPhysics.springImpulse(
				GravyPhysics.Mode.SWARM, new Vec3d(1000, 0, 0), Vec3d.ZERO, 2);
		assertTrue(clamped.length() <= 2 * 2.4 + 1.0e-9);
	}

	@Test
	void swarmFormationLivesInAimPlaneEvenWhenLookingVertically() {
		Vec3d aim = new Vec3d(0, 1, 0);
		Vec3d first = GravyPhysics.formationOffset(GravyPhysics.Mode.SWARM, 0, 8, aim);
		Vec3d second = GravyPhysics.formationOffset(GravyPhysics.Mode.SWARM, 1, 8, aim);
		assertEquals(0, first.dotProduct(aim), 1.0e-9);
		assertEquals(0, second.dotProduct(aim), 1.0e-9);
		assertTrue(first.squaredDistanceTo(second) > .1);
		assertEquals(Vec3d.ZERO,
				GravyPhysics.formationOffset(GravyPhysics.Mode.RAIL, 0, 1, new Vec3d(1, 0, 0)));
	}
}
