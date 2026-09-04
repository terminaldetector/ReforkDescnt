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
 * MODIFIED by the DRMD 6DOF project. Derived from qouteall.q_misc_util.my_util.Plane in
 * Immersive Portals 6.0.6 (Minecraft 1.21.1, the Apache-2.0-licensed `1.21` line). Changes made:
 *
 *   - Translated from Mojang mappings to DRMD's pure PortalTransform.Vec3 record, so this file
 *     carries no Minecraft types and is testable without a game bootstrap.
 *   - Renamed to ImmPtlPlane, and the getX-prefixed accessors renamed to match DRMD's record
 *     style; the field pos is called point.
 *   - The primitive-argument overloads of getDistanceTo and rayTraceGetT dropped — they exist
 *     upstream to dodge allocation in a hot loop that has no counterpart here.
 *   - equals and toString dropped: a record generates both, and the donor only overrode them
 *     because its own equals had to ignore the normalization done in the constructor.
 */
package com.terminaldetector.drmd.vendor.immptl;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A plane, as a point on it and its unit normal.
 *
 * <p><b>Why this is worth having as a type.</b> DRMD already does most of this arithmetic, three
 * times, as loose functions: {@code PortalTransform.reflectPoint} is {@link #reflect},
 * {@code ObliqueNearPlane.offsetFor} is {@link #equationW}, and {@code PortalCrossing.withinFace}
 * projects onto a plane by hand. Each is correct and none of them knows the others exist, which is
 * how a sign convention drifts between two of them.
 *
 * <p><b>And what it adds.</b> {@link #intersectionWithSegment} is the one DRMD has no answer for and
 * needs: crossing a portal is decided per tick, between the position a mover had and the position it
 * has, and that is a segment against this plane. Knowing the crossing <em>point</em> rather than only
 * that it happened is the difference between arriving where you went through and arriving wherever
 * the tick boundary landed.
 *
 * <p>The normal is normalized in the constructor, so every distance below is a real distance rather
 * than one scaled by whatever length the caller happened to pass.
 *
 * <p>This file is Apache-2.0, whole; DRMD is MIT. See {@code THIRD_PARTY_ATTRIBUTIONS.md}.
 */
public record ImmPtlPlane(Vec3 point, Vec3 normal) {

	/** Below this, a direction is treated as parallel to the plane and no intersection is reported. */
	private static final double PARALLEL_EPSILON = 0.00001;

	public ImmPtlPlane(Vec3 point, Vec3 normal) {
		this.point = point;
		this.normal = normal.normalized();
	}

	/** Signed distance from the plane — positive on the side the normal points at. */
	public double distanceTo(Vec3 p) {
		return normal.dot(p.minus(point));
	}

	/** The nearest point on the plane. */
	public Vec3 project(Vec3 p) {
		return p.minus(normal.scaled(distanceTo(p)));
	}

	/** The mirror image across the plane. */
	public Vec3 reflect(Vec3 p) {
		return p.minus(normal.scaled(2 * distanceTo(p)));
	}

	public boolean isInFront(Vec3 p) {
		return distanceTo(p) > 0;
	}

	/** The same plane pushed {@code distance} along its own normal. */
	public ImmPtlPlane movedBy(double distance) {
		return new ImmPtlPlane(point.plus(normal.scaled(distance)), normal);
	}

	/** The same plane facing the other way, so what was in front is now behind. */
	public ImmPtlPlane flipped() {
		return new ImmPtlPlane(point, normal.scaled(-1));
	}

	/** A parallel plane through another point. */
	public ImmPtlPlane through(Vec3 otherPoint) {
		return new ImmPtlPlane(otherPoint, normal);
	}

	/**
	 * Where a ray from {@code origin} along {@code direction} meets the plane, or null when it never
	 * does — either because the ray runs parallel to the plane, or because the plane is behind it.
	 */
	@Nullable
	public Vec3 rayTrace(Vec3 origin, Vec3 direction) {
		double t = rayTraceT(origin, direction);
		if (Double.isNaN(t) || t < 0) return null;
		return origin.plus(direction.scaled(t));
	}

	/**
	 * How far along {@code origin + direction * t} the plane lies, or NaN when the direction is
	 * parallel to it.
	 *
	 * <p>{@code direction} need not be normalized, and deliberately so: handed the vector from one
	 * end of a segment to the other, {@code t} comes out as a fraction of that segment, which is what
	 * {@link #intersectionWithSegment} then only has to range-check.
	 */
	public double rayTraceT(Vec3 origin, Vec3 direction) {
		double alongNormal = normal.dot(direction);
		if (Math.abs(alongNormal) < PARALLEL_EPSILON) return Double.NaN;
		return -distanceTo(origin) / alongNormal;
	}

	/**
	 * Where the segment from {@code from} to {@code to} crosses the plane, or null if it does not.
	 *
	 * <p>This is the portal-crossing test: a mover's position last tick and this tick are the two
	 * ends, and a non-null answer is both "it went through" and "here is where".
	 */
	@Nullable
	public Vec3 intersectionWithSegment(Vec3 from, Vec3 to) {
		Vec3 along = to.minus(from);
		double t = rayTraceT(from, along);
		if (Double.isNaN(t) || t < 0 || t > 1) return null;
		return from.plus(along.scaled(t));
	}

	/**
	 * A plane between two, at {@code progress} from the first to the second.
	 *
	 * <p>Straight-line interpolation of the point and of the normal, with the normal renormalized
	 * after. Enough for a plane that is being animated; not a shortest-arc turn between two
	 * orientations, which is {@link ImmPtlQuaternions#interpolate}'s job.
	 */
	public static ImmPtlPlane interpolate(ImmPtlPlane a, ImmPtlPlane b, double progress) {
		return new ImmPtlPlane(lerp(a.point, b.point, progress), lerp(a.normal, b.normal, progress));
	}

	private static Vec3 lerp(Vec3 from, Vec3 to, double progress) {
		return from.plus(to.minus(from).scaled(progress));
	}

	// The plane as ax + by + cz + d > 0 for the side the normal points at, which is the form a clip
	// plane wants:
	//     (p - point) · normal > 0
	//     p · normal - point · normal > 0
	public double equationX() {
		return normal.x();
	}

	public double equationY() {
		return normal.y();
	}

	public double equationZ() {
		return normal.z();
	}

	/** The constant term — the same number {@code ObliqueNearPlane.offsetFor} computes. */
	public double equationW() {
		return -normal.dot(point);
	}
}
