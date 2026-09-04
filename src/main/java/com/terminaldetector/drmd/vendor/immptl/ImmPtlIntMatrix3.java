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
 * MODIFIED by the DRMD 6DOF project. Derived from qouteall.q_misc_util.my_util.IntMatrix3 in
 * Immersive Portals 6.0.6 (Minecraft 1.21.1, the Apache-2.0-licensed `1.21` line). Changes made:
 *
 *   - Translated from Mojang mappings to Yarn: getNormal to getVector, fromDelta to fromVector.
 *   - Made a record; equals and hashCode are generated rather than written out.
 *   - The OctahedralGroup constructor and toMatrix (JOML) dropped — neither has a caller here, and
 *     the first would need a Yarn equivalent of a class DRMD never touches.
 *   - Helper.scale inlined.
 *   - toQuaternion returns DRMD's PortalTransform.Quat through ImmPtlQuaternions.
 */
package com.terminaldetector.drmd.vendor.immptl;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.Nullable;

/**
 * A 3×3 integer matrix — in practice, one of the 24 ways a cube can be turned.
 *
 * <p><b>Row vectors, and the donor says so.</b> This transforms as {@code p * m}, so the left factor
 * is applied first and each of {@code x}, {@code y}, {@code z} is the image of the corresponding
 * basis vector. Minecraft's own transformations use column vectors, {@code m * p}, where the right
 * one applies first. Rotation matrices are orthogonal, so the two conventions produce the same matrix
 * for the same rotation and only the multiplication order differs — which is why this is easy to get
 * wrong and worth stating at the top rather than deducing later.
 *
 * <p>This file is Apache-2.0, whole; DRMD is MIT. See {@code THIRD_PARTY_ATTRIBUTIONS.md}.
 */
public record ImmPtlIntMatrix3(Vec3i x, Vec3i y, Vec3i z) {

	public static ImmPtlIntMatrix3 identity() {
		return new ImmPtlIntMatrix3(
				new Vec3i(1, 0, 0),
				new Vec3i(0, 1, 0),
				new Vec3i(0, 0, 1));
	}

	/** {@code p * this} — the image of an integer vector under this rotation. */
	public BlockPos transform(Vec3i p) {
		return new BlockPos(
				x.getX() * p.getX() + y.getX() * p.getY() + z.getX() * p.getZ(),
				x.getY() * p.getX() + y.getY() * p.getY() + z.getY() * p.getZ(),
				x.getZ() * p.getX() + y.getZ() * p.getY() + z.getZ() * p.getZ());
	}

	/** The product with {@code this} applied first — the row-vector order, as the class note says. */
	public ImmPtlIntMatrix3 multiply(ImmPtlIntMatrix3 other) {
		return new ImmPtlIntMatrix3(other.transform(x), other.transform(y), other.transform(z));
	}

	/**
	 * Where this rotation sends a face.
	 *
	 * <p>Null only if the matrix is not a rotation: the image of a unit axis vector under one is
	 * always another unit axis vector, which is always a {@link Direction}.
	 */
	@Nullable
	public Direction transformDirection(Direction direction) {
		BlockPos v = transform(direction.getVector());
		return Direction.fromVector(v.getX(), v.getY(), v.getZ());
	}

	/** The same rotation as a quaternion — the three rows are the basis images this wants. */
	public Quat toQuaternion() {
		return ImmPtlQuaternions.fromBasisImages(toVec3(x), toVec3(y), toVec3(z));
	}

	private static Vec3 toVec3(Vec3i v) {
		return new Vec3(v.getX(), v.getY(), v.getZ());
	}
}
