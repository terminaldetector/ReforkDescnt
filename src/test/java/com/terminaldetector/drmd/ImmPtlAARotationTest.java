package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlAARotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The twenty-four axis-aligned rotations, vendored from Immersive Portals.
 *
 * <p>Tested as a group rather than case by case. Twenty-four constants each written as a pair of
 * directions is twenty-four chances to transpose two letters, and a transposed pair does not look
 * wrong — it produces a reflection, which turns a structure inside out and renders perfectly while
 * doing it. So the assertions here are the properties that a reflection cannot satisfy: every
 * determinant is +1, all twenty-four are distinct, the composition closes, and every one has exactly
 * one inverse.
 *
 * <p>These were checked independently before being written down.
 */
class ImmPtlAARotationTest {

	private static void assertVec(Vec3i expected, BlockPos actual, String what) {
		assertEquals(expected.getX(), actual.getX(), what + " x");
		assertEquals(expected.getY(), actual.getY(), what + " y");
		assertEquals(expected.getZ(), actual.getZ(), what + " z");
	}

	@Test
	@DisplayName("all twenty-four are rotations, not reflections")
	void everyOneIsAProperRotation() {
		for (ImmPtlAARotation r : ImmPtlAARotation.values()) {
			Vec3i x = r.transformedX.getVector();
			Vec3i y = r.transformedY.getVector();
			Vec3i z = r.transformedZ.getVector();
			int determinant =
					x.getX() * (y.getY() * z.getZ() - y.getZ() * z.getY())
					- x.getY() * (y.getX() * z.getZ() - y.getZ() * z.getX())
					+ x.getZ() * (y.getX() * z.getY() - y.getY() * z.getX());
			// -1 would be a reflection: same axes, wrong handedness, and nothing about it looks wrong
			// until a structure comes out mirrored.
			assertEquals(1, determinant, r + " is a reflection, not a rotation");
		}
	}

	@Test
	@DisplayName("all twenty-four are distinct, so none of the pairs was mistyped into a duplicate")
	void allDistinct() {
		Set<String> seen = new HashSet<>();
		for (ImmPtlAARotation r : ImmPtlAARotation.values()) {
			assertTrue(seen.add(r.transformedX + "|" + r.transformedY + "|" + r.transformedZ),
					r + " duplicates another rotation's frame");
		}
		assertEquals(24, seen.size());
	}

	@Test
	@DisplayName("transforming a basis vector gives the direction the constant is named for")
	void transformAgreesWithTheNamedAxes() {
		for (ImmPtlAARotation r : ImmPtlAARotation.values()) {
			assertVec(r.transformedX.getVector(), r.transform(Direction.EAST.getVector()), r + " +X");
			assertVec(r.transformedY.getVector(), r.transform(Direction.UP.getVector()), r + " +Y");
			assertVec(r.transformedZ.getVector(), r.transform(Direction.SOUTH.getVector()), r + " +Z");
		}
	}

	@Test
	@DisplayName("composition closes, and the identity is the identity")
	void compositionIsAGroup() {
		for (ImmPtlAARotation a : ImmPtlAARotation.values()) {
			assertSame(a, a.multiply(ImmPtlAARotation.IDENTITY), a + " changed when composed with the identity");
			assertSame(a, ImmPtlAARotation.IDENTITY.multiply(a), "the identity changed " + a);
			for (ImmPtlAARotation b : ImmPtlAARotation.values()) {
				assertNotNull(a.multiply(b), a + " * " + b + " left the group");
			}
		}
	}

	@Test
	@DisplayName("every rotation has an inverse, and inverting twice comes back")
	void inversesExistAndAreInvolutive() {
		for (ImmPtlAARotation r : ImmPtlAARotation.values()) {
			assertSame(ImmPtlAARotation.IDENTITY, r.multiply(r.getInverse()), r + " times its inverse is not the identity");
			assertSame(r, r.getInverse().getInverse(), r + " did not survive being inverted twice");
		}
	}

	@Test
	@DisplayName("composition applies the argument first, as its own doc says")
	void compositionOrderIsRightArgumentFirst() {
		ImmPtlAARotation a = ImmPtlAARotation.quarterTurnAbout(Direction.UP);
		ImmPtlAARotation b = ImmPtlAARotation.quarterTurnAbout(Direction.EAST);
		Direction start = Direction.SOUTH;

		// a.multiply(b) means b first, then a.
		assertEquals(a.transformDirection(b.transformDirection(start)),
				a.multiply(b).transformDirection(start),
				"the composed rotation does not apply the argument first");
	}

	@Test
	@DisplayName("a quarter turn about an axis lands where it should")
	void quarterTurns() {
		assertSame(ImmPtlAARotation.EAST_ROT0, ImmPtlAARotation.quarterTurnAbout(Direction.UP));
		assertSame(ImmPtlAARotation.DOWN_ROT270, ImmPtlAARotation.quarterTurnAbout(Direction.EAST));
		assertSame(ImmPtlAARotation.SOUTH_ROT90, ImmPtlAARotation.quarterTurnAbout(Direction.SOUTH));
		// Four quarter turns about any axis is a full turn.
		for (Direction axis : Direction.values()) {
			ImmPtlAARotation q = ImmPtlAARotation.quarterTurnAbout(axis);
			assertSame(ImmPtlAARotation.IDENTITY, q.multiply(q).multiply(q).multiply(q),
					"four quarter turns about " + axis + " is not a full turn");
		}
	}

	@Test
	@DisplayName("the cross product of two faces on the same axis is a bug, not an edge case")
	void crossProductRefusesParallelDirections() {
		assertThrows(IllegalArgumentException.class,
				() -> ImmPtlAARotation.directionCrossProduct(Direction.UP, Direction.DOWN));
		assertEquals(Direction.NORTH,
				ImmPtlAARotation.directionCrossProduct(Direction.UP, Direction.EAST));
	}

	@Test
	@DisplayName("the quaternion of each rotation agrees with the matrix it came from")
	void quaternionAgreesWithTheMatrix() {
		// Ties the two vendored files together: if fromBasisImages took the transpose, this would
		// disagree for every rotation that is not its own inverse.
		Vec3 probe = new Vec3(1, 2, 3);
		for (ImmPtlAARotation r : ImmPtlAARotation.values()) {
			BlockPos byMatrix = r.transform(new Vec3i(1, 2, 3));
			Vec3 byQuaternion = r.quaternion.rotate(probe);
			assertEquals(byMatrix.getX(), byQuaternion.x(), 1e-9, r + " x");
			assertEquals(byMatrix.getY(), byQuaternion.y(), 1e-9, r + " y");
			assertEquals(byMatrix.getZ(), byQuaternion.z(), 1e-9, r + " z");
		}
	}
}
