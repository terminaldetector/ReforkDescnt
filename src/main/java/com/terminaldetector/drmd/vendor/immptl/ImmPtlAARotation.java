/*
 * Copyright the Immersive Portals authors (qouteall and contributors).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---
 *
 * MODIFIED by the DRMD 6DOF project. Derived from qouteall.q_misc_util.my_util.AARotation in
 * Immersive Portals 6.0.6 (Minecraft 1.21.1, the Apache-2.0-licensed `1.21` line). Changes made:
 *
 *   - Translated from Mojang mappings to Yarn: getNormal to getVector, fromDelta to fromVector,
 *     getStepX/Y/Z to getOffsetX/Y/Z.
 *   - The vanilla BlockRotation conversions dropped, along with rotationsSortedByAngle — none has
 *     a caller here, and the first would pin four enum constant names for no gain.
 *   - Apache Commons Validate replaced with plain checks; Guava's ImmutableList not needed.
 *   - The quaternion field is DRMD's PortalTransform.Quat, via ImmPtlQuaternions.
 */
package com.terminaldetector.drmd.vendor.immptl;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

/**
 * The twenty-four ways a cube can be turned without being mirrored.
 *
 * <p>Vanilla's own octahedral group includes reflections; this is the rotation subgroup, which is
 * the one a physical object can actually be put into. Each constant is named by where it sends
 * {@code +Z} and then by a quarter turn about that.
 *
 * <p><b>What DRMD needs it for.</b> Moving structures presently have no rotation at all —
 * {@code StructureInstance} holds an integer {@code BlockPos} anchor and {@code StructureMover}
 * relocates by erasing and rewriting blocks. Turning a structure needs exactly two things this
 * provides: the image of a block's offset from the anchor ({@link #transform}), and the new facing of
 * each block that has one ({@link #transformDirection}). The same pair is what a portal needs to map
 * a block layout from one face onto another.
 *
 * <p>Composition and inversion are precomputed into 24×24 and 24-entry tables at class load, because
 * both are otherwise a search over the group and both are called per block.
 *
 * <p>This file is Apache-2.0, whole; DRMD is MIT. See {@code THIRD_PARTY_ATTRIBUTIONS.md}.
 */
public enum ImmPtlAARotation {

	SOUTH_ROT0(Direction.SOUTH, Direction.EAST),
	SOUTH_ROT90(Direction.SOUTH, Direction.UP),
	SOUTH_ROT180(Direction.SOUTH, Direction.WEST),
	SOUTH_ROT270(Direction.SOUTH, Direction.DOWN),

	NORTH_ROT0(Direction.NORTH, Direction.WEST),
	NORTH_ROT90(Direction.NORTH, Direction.UP),
	NORTH_ROT180(Direction.NORTH, Direction.EAST),
	NORTH_ROT270(Direction.NORTH, Direction.DOWN),

	EAST_ROT0(Direction.EAST, Direction.NORTH),
	EAST_ROT90(Direction.EAST, Direction.UP),
	EAST_ROT180(Direction.EAST, Direction.SOUTH),
	EAST_ROT270(Direction.EAST, Direction.DOWN),

	WEST_ROT0(Direction.WEST, Direction.SOUTH),
	WEST_ROT90(Direction.WEST, Direction.UP),
	WEST_ROT180(Direction.WEST, Direction.NORTH),
	WEST_ROT270(Direction.WEST, Direction.DOWN),

	UP_ROT0(Direction.UP, Direction.NORTH),
	UP_ROT90(Direction.UP, Direction.WEST),
	UP_ROT180(Direction.UP, Direction.SOUTH),
	UP_ROT270(Direction.UP, Direction.EAST),

	DOWN_ROT0(Direction.DOWN, Direction.SOUTH),
	DOWN_ROT90(Direction.DOWN, Direction.WEST),
	DOWN_ROT180(Direction.DOWN, Direction.NORTH),
	DOWN_ROT270(Direction.DOWN, Direction.EAST);

	public static final ImmPtlAARotation IDENTITY = SOUTH_ROT0;

	public final Direction transformedX;
	public final Direction transformedY;
	public final Direction transformedZ;
	public final ImmPtlIntMatrix3 matrix;
	public final Quat quaternion;

	ImmPtlAARotation(Direction transformedZ, Direction transformedX) {
		this.transformedZ = transformedZ;
		this.transformedX = transformedX;
		// Y is not free: two axes of a right-handed frame fix the third, and deriving it rather than
		// listing it is what makes all 24 constants above impossible to mistype into a reflection.
		this.transformedY = directionCrossProduct(transformedZ, transformedX);
		this.matrix = new ImmPtlIntMatrix3(
				this.transformedX.getVector(),
				this.transformedY.getVector(),
				this.transformedZ.getVector());
		this.quaternion = matrix.toQuaternion();
	}

	/** Where this rotation sends an integer offset. */
	public BlockPos transform(Vec3i vec) {
		return matrix.transform(vec);
	}

	/** Where this rotation sends a face. */
	public Direction transformDirection(Direction direction) {
		BlockPos v = transform(direction.getVector());
		return Direction.fromVector(v.getX(), v.getY(), v.getZ());
	}

	/**
	 * The cross product of two perpendicular faces, as a face.
	 *
	 * <p>Throws on two faces of the same axis rather than returning null: the cross product of
	 * parallel directions is the zero vector, which is not a direction, and a caller that got here
	 * with one has a bug rather than an edge case.
	 */
	public static Direction directionCrossProduct(Direction a, Direction b) {
		if (a.getAxis() == b.getAxis()) {
			throw new IllegalArgumentException("cross product of two directions on the same axis: " + a + " " + b);
		}
		Direction result = Direction.fromVector(
				a.getOffsetY() * b.getOffsetZ() - a.getOffsetZ() * b.getOffsetY(),
				a.getOffsetZ() * b.getOffsetX() - a.getOffsetX() * b.getOffsetZ(),
				a.getOffsetX() * b.getOffsetY() - a.getOffsetY() * b.getOffsetX());
		if (result == null) {
			throw new IllegalStateException("cross product of " + a + " and " + b + " is not a direction");
		}
		return result;
	}

	/** A quarter turn of {@code direction} about {@code axis}; a direction on that axis is unmoved. */
	public static Direction rotate90DegreesAlong(Direction direction, Direction axis) {
		if (direction.getAxis() == axis.getAxis()) return direction;
		return directionCrossProduct(axis, direction);
	}

	private static final ImmPtlAARotation[][] MULTIPLICATION = new ImmPtlAARotation[24][24];
	private static final ImmPtlAARotation[] INVERSE = new ImmPtlAARotation[24];

	static {
		for (ImmPtlAARotation a : values()) {
			for (ImmPtlAARotation b : values()) {
				MULTIPLICATION[a.ordinal()][b.ordinal()] = a.rawMultiply(b);
			}
		}
		for (ImmPtlAARotation rotation : values()) {
			ImmPtlAARotation found = null;
			for (ImmPtlAARotation candidate : values()) {
				if (rotation.multiply(candidate) == IDENTITY) {
					found = candidate;
					break;
				}
			}
			if (found == null) {
				// Unreachable for a group, and worth saying so loudly rather than leaving a null in the
				// table for some later caller to trip over.
				throw new IllegalStateException("no inverse for " + rotation);
			}
			INVERSE[rotation.ordinal()] = found;
		}
	}

	/** {@code other} applied first, then this. */
	public ImmPtlAARotation multiply(ImmPtlAARotation other) {
		return MULTIPLICATION[this.ordinal()][other.ordinal()];
	}

	public ImmPtlAARotation getInverse() {
		return INVERSE[this.ordinal()];
	}

	private ImmPtlAARotation rawMultiply(ImmPtlAARotation other) {
		return fromZX(transformDirection(other.transformedZ), transformDirection(other.transformedX));
	}

	public static ImmPtlAARotation fromZX(Direction transformedZ, Direction transformedX) {
		for (ImmPtlAARotation value : values()) {
			if (value.transformedZ == transformedZ && value.transformedX == transformedX) return value;
		}
		throw new IllegalArgumentException("no rotation sends +Z to " + transformedZ + " and +X to " + transformedX);
	}

	public static ImmPtlAARotation fromYZ(Direction transformedY, Direction transformedZ) {
		for (ImmPtlAARotation value : values()) {
			if (value.transformedY == transformedY && value.transformedZ == transformedZ) return value;
		}
		throw new IllegalArgumentException("no rotation sends +Y to " + transformedY + " and +Z to " + transformedZ);
	}

	public static ImmPtlAARotation fromXY(Direction transformedX, Direction transformedY) {
		for (ImmPtlAARotation value : values()) {
			if (value.transformedX == transformedX && value.transformedY == transformedY) return value;
		}
		throw new IllegalArgumentException("no rotation sends +X to " + transformedX + " and +Y to " + transformedY);
	}

	/** The quarter turn about {@code axis}, as a member of this group. */
	public static ImmPtlAARotation quarterTurnAbout(Direction axis) {
		return fromXY(
				rotate90DegreesAlong(Direction.EAST, axis),
				rotate90DegreesAlong(Direction.UP, axis));
	}
}
