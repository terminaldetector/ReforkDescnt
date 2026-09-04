package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlPlane;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.jetbrains.annotations.Nullable;

/**
 * One portal's effect on everything about a body, not just on where it is.
 *
 * <p><b>The rule this exists to enforce</b> is that crossing a portal is a spatial transformation and
 * not a teleport. A teleport moves a position and leaves the rest of the state describing the world
 * the body just left: it arrives still travelling the old way, still facing the old way, still
 * tumbling about an axis that no longer means anything. So position, velocity, orientation and
 * angular momentum all go through the same rotation, and the accessors below exist so that no caller
 * can transform one and forget another.
 *
 * <p><b>Entry is against the normal.</b> A portal's normal points out at the side you stand on, so
 * walking in means moving along {@code -sourceNormal}, and coming out means moving along
 * {@code +destNormal}. The rotation is therefore the one taking {@code sourceNormal} to
 * {@code -destNormal}, which is what {@code PortalTransform.cameraRotation} already builds. A
 * consequence worth stating because it looks wrong at first: a point standing a metre <em>in front
 * of</em> the source arrives a metre <em>behind</em> the destination, facing out through it. That is
 * the same map the see-through view uses, which is why what you see through a portal is where walking
 * into it puts you.
 *
 * <p><b>Orientation composes on the left.</b> An orientation maps body axes to world axes; the portal
 * rotates world axes; so the new orientation is the portal's rotation applied after the body's. Same
 * side as {@link D6PhysicsBody} applies its angular increment, and for the same reason.
 *
 * <p><b>Angular momentum is rotated and not scaled.</b> {@code L = Iω} and a portal that scaled a
 * body would scale its inertia too, which is a longer conversation about what happens to mass. It
 * does not need having yet: native linking refuses any scale but 1 —
 * see {@code MirrorLinkerItem} — and this class says so rather than quietly producing a number for a
 * case nobody has decided.
 *
 * <p>Pure, like the rest of {@code d6}: no Minecraft, no entities, no world. It transforms values.
 */
public record D6PortalTransform(
		Vec3 sourcePoint, Vec3 sourceNormal,
		Vec3 destPoint, Vec3 destNormal,
		double scale) {

	/** A portal with no size change, which is the only kind DRMD links natively. */
	public static D6PortalTransform of(Vec3 sourcePoint, Vec3 sourceNormal, Vec3 destPoint, Vec3 destNormal) {
		return new D6PortalTransform(sourcePoint, sourceNormal, destPoint, destNormal, 1.0);
	}

	/** The rotation this portal applies to everything that goes through it. */
	public Quat rotation() {
		return PortalTransform.cameraRotation(sourceNormal, destNormal);
	}

	/** The source face, as a plane — for {@link #crossingPoint}. */
	public ImmPtlPlane sourcePlane() {
		return new ImmPtlPlane(sourcePoint, sourceNormal);
	}

	public Vec3 transformPoint(Vec3 point) {
		return PortalTransform.transformPoint(point, sourcePoint, sourceNormal, destPoint, destNormal, scale);
	}

	/**
	 * A direction or a velocity: rotated, and scaled with the portal.
	 *
	 * <p>Scaled because a body twice the size crossing at one metre a second is crossing at half a
	 * body-length a second, and staying at one metre a second on the far side would make it look like
	 * it had braked.
	 */
	public Vec3 transformVelocity(Vec3 velocity) {
		return rotation().rotate(velocity).scaled(scale);
	}

	public Quat transformOrientation(Quat orientation) {
		return ImmPtlQuaternions.hamiltonProduct(rotation(), orientation).normalized();
	}

	/** Rotated only — see the class note on why scale is left out of this one. */
	public Vec3 transformAngularMomentum(Vec3 angularMomentum) {
		return rotation().rotate(angularMomentum);
	}

	/**
	 * Where the body actually crossed, between where it was and where it is, or null if it did not.
	 *
	 * <p>The reason this is here and not left to the caller: a crossing is decided once a tick, and
	 * without the crossing point the body arrives not where it went through but wherever the tick
	 * boundary happened to land it. At the speeds this project flies at, that is metres.
	 */
	@Nullable
	public Vec3 crossingPoint(Vec3 from, Vec3 to) {
		return sourcePlane().intersectionWithSegment(from, to);
	}

	/**
	 * Whether a body moving from {@code from} to {@code to} went through this face this tick.
	 *
	 * <p>Direction matters: leaving through the front is not entering. A crossing counts only when
	 * the body started on the normal's side and finished behind it.
	 */
	public boolean crossedInward(Vec3 from, Vec3 to) {
		ImmPtlPlane plane = sourcePlane();
		return plane.distanceTo(from) > 0 && plane.distanceTo(to) <= 0
				&& plane.intersectionWithSegment(from, to) != null;
	}

	/**
	 * Carry a whole body through, in one call.
	 *
	 * <p>One call rather than four, because the failure mode of four is transforming three of them.
	 * Position is placed from where the body actually crossed when that is known, and from its
	 * current position otherwise.
	 */
	public void apply(D6PhysicsBody body, @Nullable Vec3 crossedAt) {
		Vec3 origin = crossedAt == null ? body.position() : crossedAt;
		body.withPosition(transformPoint(origin))
				.withLinearVelocity(transformVelocity(body.linearVelocity()))
				.withRotation(transformOrientation(body.rotation()))
				.withAngularMomentum(transformAngularMomentum(body.angularMomentum()));
	}

	/** The transform that undoes this one — the far side of the same pair. */
	public D6PortalTransform inverse() {
		return new D6PortalTransform(destPoint, destNormal, sourcePoint, sourceNormal,
				scale == 0 ? 0 : 1.0 / scale);
	}
}
