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
 * MODIFIED by the DRMD 6DOF project. Derived from
 * qouteall.q_misc_util.my_util.DQuaternion in Immersive Portals 6.0.6 (Minecraft 1.21.1,
 * the Apache-2.0-licensed `1.21` line). Changes made:
 *
 *   - Rewritten as static operations on DRMD's own PortalTransform.Quat and PortalTransform.Vec3
 *     instead of a second quaternion class, so DRMD keeps one quaternion type.
 *   - Translated from Mojang mappings (Vec3, .x(), .cross, .dot) to DRMD's pure vector record,
 *     which carries no Minecraft types at all.
 *   - NBT serialisation, JOML conversions, logging and the getters dropped: DRMD's Quat is a
 *     record and the rest has no caller here.
 *   - getRotationBetween omitted deliberately — PortalTransform.rotationBetween already covers it
 *     and handles the antiparallel case this one documents as unsupported.
 */
package com.terminaldetector.drmd.vendor.immptl;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.client.portal.PortalTransform.YawPitch;

/**
 * Quaternion operations vendored from Immersive Portals.
 *
 * <p><b>Why these and not others.</b> DRMD's own {@code PortalTransform.Quat} already normalizes,
 * conjugates, rotates a vector, and builds the shortest arc between two directions — the last of
 * those better than the donor's, which documents itself as not working when the two are collinear
 * where DRMD's names a fallback axis. What DRMD had no answer for is everything below: composing two
 * rotations, building one from an axis and an angle, reading one back out of an orthonormal frame,
 * interpolating between two, and the pitch/yaw pair a camera wants. Each is needed by something
 * already planned — the first two by integrating angular velocity, the third by giving
 * {@code ShipAttitude} a real orientation instead of a forward/up vector pair.
 *
 * <p><b>Why vendored rather than written.</b> These are standard formulae, but two of them carry
 * decisions that are not: {@link #interpolate} flips one input when the dot product is negative,
 * because a quaternion and its negation are the same rotation and interpolating between them the
 * long way round is a visible spin; and {@link #fixFloatingPointErrorAccumulation} exists because
 * repeated composition drifts, and the donor's comment records what that drift actually broke — its
 * teleportation and collision. Getting the first right from memory is a coin flip and the second
 * would not have been thought of at all.
 *
 * <p>This file is Apache-2.0, whole, so its licence boundary is the file. DRMD is MIT; see
 * {@code THIRD_PARTY_ATTRIBUTIONS.md}.
 */
public final class ImmPtlQuaternions {
	private ImmPtlQuaternions() {}

	/**
	 * The rotation of {@code degrees} about {@code axis}.
	 *
	 * <p>The axis is normalized here rather than demanded of the caller — integrating an angular
	 * velocity hands this a vector whose length <em>is</em> the rate, so the caller has the angle and
	 * the direction in one object and should not have to take it apart.
	 */
	public static Quat rotationByDegrees(Vec3 axis, double degrees) {
		return rotationByRadians(axis, Math.toRadians(degrees));
	}

	/** The rotation of {@code radians} about {@code axis}. */
	public static Quat rotationByRadians(Vec3 axis, double radians) {
		double s = Math.sin(radians / 2.0);
		Vec3 unit = axis.normalized();
		return new Quat(unit.x() * s, unit.y() * s, unit.z() * s, Math.cos(radians / 2.0));
	}

	/** The axis this rotation turns about, or an arbitrary unit vector when it turns by nothing. */
	public static Vec3 rotatingAxis(Quat q) {
		return new Vec3(q.x(), q.y(), q.z()).normalized();
	}

	/** How far this rotation turns, in radians. */
	public static double rotatingAngleRadians(Quat q) {
		// Clamped before acos: a quaternion that has drifted a hair past unit length would otherwise
		// hand acos an argument outside [-1, 1] and get NaN back for a rotation that is nearly none.
		return Math.acos(Math.max(-1.0, Math.min(1.0, q.w()))) * 2.0;
	}

	public static double rotatingAngleDegrees(Quat q) {
		return Math.toDegrees(rotatingAngleRadians(q));
	}

	/**
	 * The Hamilton product: {@code first} applied, then {@code second}.
	 *
	 * <p>Named for the order it reads in rather than the order it multiplies in. The product is
	 * {@code second * first}, which is the source of most sign and order mistakes with quaternions,
	 * so the parameter names say what happens and the body does the algebra.
	 */
	public static Quat then(Quat first, Quat second) {
		return hamiltonProduct(second, first);
	}

	/** {@code a * b} — the rotation that applies {@code b} first and then {@code a}. */
	public static Quat hamiltonProduct(Quat a, Quat b) {
		double x1 = a.x(), y1 = a.y(), z1 = a.z(), w1 = a.w();
		double x2 = b.x(), y2 = b.y(), z2 = b.z(), w2 = b.w();
		return new Quat(
				w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
				w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
				w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
				w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2);
	}

	public static Quat scaled(Quat q, double factor) {
		return new Quat(q.x() * factor, q.y() * factor, q.z() * factor, q.w() * factor);
	}

	public static Quat plus(Quat a, Quat b) {
		return new Quat(a.x() + b.x(), a.y() + b.y(), a.z() + b.z(), a.w() + b.w());
	}

	/** The four-dimensional dot product — {@code 1} for equal rotations, {@code -1} for a rotation and its negation. */
	public static double dot(Quat a, Quat b) {
		return a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w();
	}

	/**
	 * Spherical interpolation from {@code a} to {@code b}.
	 *
	 * <p>Two decisions here are the reason this is vendored rather than recalled. A quaternion and
	 * its negation describe the same rotation, so when the dot product comes out negative one input
	 * is flipped — without that, interpolating takes the long way round and the result visibly spins
	 * the wrong way. And when the two are nearly identical the spherical form divides by a sine
	 * approaching zero, so past {@code 0.9995} it falls back to a straight line and normalizes.
	 */
	public static Quat interpolate(Quat a, Quat b, double t) {
		double dot = dot(a, b);
		if (dot < 0.0) {
			a = scaled(a, -1);
			dot = -dot;
		}

		final double dotThreshold = 0.9995;
		if (dot > dotThreshold) {
			return plus(scaled(a, 1 - t), scaled(b, t)).normalized();
		}

		double theta0 = Math.acos(dot);
		double theta = theta0 * t;
		double sinTheta = Math.sin(theta);
		double sinTheta0 = Math.sin(theta0);

		double s0 = Math.cos(theta) - dot * sinTheta / sinTheta0;
		double s1 = sinTheta / sinTheta0;
		return plus(scaled(a, s0), scaled(b, s1));
	}

	/**
	 * Whether two quaternions describe close enough to the same rotation.
	 *
	 * <p>Compares both {@code a - b} and {@code a + b} and takes the smaller, because a rotation and
	 * its negation are the same rotation — a straight component-wise comparison calls those two
	 * maximally different.
	 */
	public static boolean isClose(Quat a, Quat b) {
		return distanceSquared(a, b) < 1e-8;
	}

	public static double distanceSquared(Quat a, Quat b) {
		double dx1 = a.x() - b.x(), dy1 = a.y() - b.y(), dz1 = a.z() - b.z(), dw1 = a.w() - b.w();
		double v1 = dx1 * dx1 + dy1 * dy1 + dz1 * dz1 + dw1 * dw1;

		double dx2 = a.x() + b.x(), dy2 = a.y() + b.y(), dz2 = a.z() + b.z(), dw2 = a.w() + b.w();
		double v2 = dx2 * dx2 + dy2 * dy2 + dz2 * dz2 + dw2 * dw2;

		return Math.min(v1, v2);
	}

	/**
	 * The rotation described by the three orthonormal <b>columns</b> of a rotation matrix — that is,
	 * by where it sends each basis vector.
	 *
	 * <p><b>The donor calls these rows; both names are right, in different conventions.</b> Its code
	 * reads {@code m10} out of the first argument's y, which is a column under the usual column-vector
	 * convention — but the donor's own {@code IntMatrix3} states plainly that it transforms row
	 * vectors as {@code p * m}, and under that convention row {@code i} <em>is</em> the image of basis
	 * vector {@code i}. Same three vectors, two words for them.
	 *
	 * <p>So the name here is the thing itself rather than either convention's word for it. Which
	 * reading DRMD needs was settled by arithmetic and not by argument: feeding this the three axes of
	 * a known rotation recovers that rotation exactly, and feeding it the transpose lands 1.09 away.
	 *
	 * <p>Branches on which diagonal term is largest, which is not decoration: the direct formula
	 * divides by a term that vanishes for a 180° turn, and each branch picks one that cannot. Only
	 * valid for a matrix that is a pure rotation.
	 *
	 * <p>Derivation the donor cites:
	 * {@code euclideanspace.com/maths/geometry/rotations/conversions/matrixToQuaternion}.
	 */
	public static Quat fromBasisImages(Vec3 imageOfX, Vec3 imageOfY, Vec3 imageOfZ) {
		double m00 = imageOfX.x();
		double m11 = imageOfY.y();
		double m22 = imageOfZ.z();

		double m12 = imageOfZ.y();
		double m21 = imageOfY.z();
		double m20 = imageOfX.z();
		double m02 = imageOfZ.x();
		double m01 = imageOfY.x();
		double m10 = imageOfX.y();

		double trace = m00 + m11 + m22;
		double qx, qy, qz, qw;

		if (trace > 0) {
			double s = Math.sqrt(trace + 1.0) * 2;
			qw = 0.25 * s;
			qx = (m21 - m12) / s;
			qy = (m02 - m20) / s;
			qz = (m10 - m01) / s;
		} else if (m00 > m11 && m00 > m22) {
			double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
			qw = (m21 - m12) / s;
			qx = 0.25 * s;
			qy = (m01 + m10) / s;
			qz = (m02 + m20) / s;
		} else if (m11 > m22) {
			double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
			qw = (m02 - m20) / s;
			qx = (m01 + m10) / s;
			qy = 0.25 * s;
			qz = (m12 + m21) / s;
		} else {
			double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
			qw = (m10 - m01) / s;
			qx = (m02 + m20) / s;
			qy = (m12 + m21) / s;
			qz = 0.25 * s;
		}

		return new Quat(qx, qy, qz, qw);
	}

	/**
	 * The rotation whose local X is {@code axisW} and whose local Y is {@code axisH}.
	 *
	 * <p>This is the one {@code ShipAttitude} wants: it keeps a forward and an up vector, which is
	 * exactly a frame, and this turns a frame into an orientation that can be composed and
	 * interpolated instead of nudged.
	 */
	public static Quat fromFrame(Vec3 axisW, Vec3 axisH) {
		return fromBasisImages(axisW, axisH, axisW.cross(axisH));
	}

	/** This rotation's local X axis. */
	public static Vec3 axisW(Quat q) {
		return q.rotate(new Vec3(1, 0, 0));
	}

	/** This rotation's local Y axis. */
	public static Vec3 axisH(Quat q) {
		return q.rotate(new Vec3(0, 1, 0));
	}

	/** This rotation's local Z axis. */
	public static Vec3 normal(Quat q) {
		return q.rotate(new Vec3(0, 0, 1));
	}

	/**
	 * The camera rotation for a pitch and yaw, in degrees.
	 *
	 * <p>This is the rotation applied to the <em>world</em> for rendering, not to the entity's head;
	 * the head's is its inverse. The {@code yaw + 180} is not a typo — it is Minecraft's own
	 * convention, where yaw zero looks toward positive Z.
	 */
	public static Quat cameraRotation(double pitchDegrees, double yawDegrees) {
		Quat pitch = rotationByDegrees(new Vec3(1, 0, 0), pitchDegrees);
		Quat yaw = rotationByDegrees(new Vec3(0, 1, 0), yawDegrees + 180);
		return hamiltonProduct(pitch, yaw);
	}

	/** The inverse of {@link #cameraRotation} — and roughly right for rotations that are not a camera's. */
	public static YawPitch toYawPitch(Quat q) {
		double x = q.x(), y = q.y(), z = q.z(), w = q.w();

		double cosYaw = 2 * (y * y + z * z) - 1;
		double sinYaw = -(x * z + y * w) * 2;

		double cosPitch = 1 - 2 * (x * x + z * z);
		double sinPitch = (x * w + y * z) * 2;

		return new YawPitch(
				Math.toDegrees(Math.atan2(sinYaw, cosYaw)),
				Math.toDegrees(Math.atan2(sinPitch, cosPitch)));
	}

	/**
	 * Snap components that have drifted next to 0 or ±1 back onto them, then normalize.
	 *
	 * <p>Kept for the reason the donor gives rather than for tidiness: composing rotations
	 * repeatedly accumulates floating-point error, and in their case the accumulated error broke
	 * teleportation and collision — a portal that no longer quite agreed with itself about where its
	 * far side was. Anything DRMD composes every tick has the same exposure.
	 */
	public static Quat fixFloatingPointErrorAccumulation(Quat q) {
		return new Quat(
				snap(q.x()), snap(q.y()), snap(q.z()), snap(q.w())).normalized();
	}

	private static double snap(double value) {
		final double threshold = 0.0000001;
		if (Math.abs(value) < threshold) return 0;
		if (Math.abs(value - 1) < threshold) return 1;
		if (Math.abs(value + 1) < threshold) return -1;
		return value;
	}

	/** Whether every component is exactly 0 or ±1 — an axis-aligned rotation, which some code may special-case. */
	public static boolean isAxisAligned(Quat q) {
		return isZeroOrUnit(q.x()) && isZeroOrUnit(q.y()) && isZeroOrUnit(q.z()) && isZeroOrUnit(q.w());
	}

	private static boolean isZeroOrUnit(double coordinate) {
		return coordinate == 0 || coordinate == 1 || coordinate == -1;
	}

	/** Whether this is close enough to unit length to be a rotation at all. */
	public static boolean isValid(Quat q) {
		return Math.abs(dot(q, q)) > 0.9;
	}
}
