package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.portal.mirror.MirrorReflection;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guards for the mirror-block bounce math.
 *
 * <p>{@code normalFor} deliberately does not mirror {@code GravityGeneratorBlockEntity}'s
 * {@code facing.getOpposite()} idiom — see its own javadoc. A wrong sign there would silently
 * reflect every bounce through the back of the block instead of the front, so that case gets its
 * own explicit test rather than relying on the general reflect() cases to catch it indirectly.
 */
class MirrorReflectionTest {
	private static void assertVecEquals(Vec3d expected, Vec3d actual, double eps, String msg) {
		assertEquals(expected.x, actual.x, eps, msg + " (x)");
		assertEquals(expected.y, actual.y, eps, msg + " (y)");
		assertEquals(expected.z, actual.z, eps, msg + " (z)");
	}

	@Test
	@DisplayName("reflection never changes speed")
	void reflectPreservesSpeed() {
		Vec3d[] velocities = {
				new Vec3d(0, 0, -30), new Vec3d(12, -5, 7), new Vec3d(1, 1, 1), new Vec3d(-4, 0.5, 9)
		};
		Vec3d[] normals = {
				new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1),
				new Vec3d(1, 1, 0), new Vec3d(0.3, -0.8, 0.5)
		};
		for (Vec3d v : velocities) {
			for (Vec3d n : normals) {
				assertEquals(v.length(), MirrorReflection.reflect(v, n).length(), 1e-9,
						"speed changed reflecting " + v + " off " + n);
			}
		}
	}

	@Test
	@DisplayName("a head-on shot bounces straight back")
	void headOnBounceReverses() {
		Vec3d v = new Vec3d(0, 0, -30);
		Vec3d n = new Vec3d(0, 0, 1);
		assertVecEquals(new Vec3d(0, 0, 30), MirrorReflection.reflect(v, n), 1e-9,
				"head-on bounce must reverse exactly");
	}

	@Test
	@DisplayName("a glancing shot keeps its in-plane component and flips only the normal component")
	void glancingBounceFlipsOnlyNormalComponent() {
		// 45 degrees into a Z-facing mirror: X (in-plane) must survive untouched, Z must flip sign.
		Vec3d v = new Vec3d(1, 0, -1);
		Vec3d n = new Vec3d(0, 0, 1);
		assertVecEquals(new Vec3d(1, 0, 1), MirrorReflection.reflect(v, n), 1e-9,
				"45-degree glance must preserve the in-plane axis and flip the normal axis");
	}

	@Test
	@DisplayName("bouncing off the same mirror twice returns the original heading")
	void doubleReflectionIsIdentity() {
		Vec3d v = new Vec3d(6, -14, 3);
		Vec3d n = new Vec3d(0.4, 0.4, 0.82); // arbitrary non-axis-aligned plane, still unit-normalizable
		Vec3d once = MirrorReflection.reflect(v, n);
		Vec3d twice = MirrorReflection.reflect(once, n);
		assertVecEquals(v, twice, 1e-9, "two bounces off the same plane must undo each other");
	}

	@Test
	@DisplayName("the reflection normal points the same way FACING does, not the opposite")
	void normalMatchesFacingDirectly() {
		for (Direction d : Direction.values()) {
			assertVecEquals(Vec3d.of(d.getVector()), MirrorReflection.normalFor(d), 1e-9,
					"normalFor(" + d + ") must not be inverted like a gravity mount's down-vector");
		}
	}
}
