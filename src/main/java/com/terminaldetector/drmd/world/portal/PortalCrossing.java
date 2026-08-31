package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.client.portal.PortalTransform;

/**
 * When an entity has gone through a portal, and where it comes out — pure arithmetic, zero Minecraft
 * imports, the {@code SkirtGeometry}/{@link PortalTransform} idiom: the caller samples the positions,
 * this file only decides and computes.
 *
 * <p>Exists because a linked charged mirror does nothing at all without Immersive Portals installed.
 * The link is stored — {@code ChargedMirrorBlockEntity} keeps the partner's position and dimension —
 * but nothing ever read it: the actual travel was done by an ImmPtl {@code Portal} entity spawned in
 * {@code ImmPtlMirrorBridge.linkPortals}. With ImmPtl absent the block says so itself and stays
 * decorative. This is the arithmetic half of doing it natively.
 *
 * <p>Rotation convention is inherited from {@link PortalTransform}, unchanged: the rotation takes the
 * source portal's outward normal onto the <em>negation</em> of the destination's, so walking face-first
 * into one leaves you walking face-first out of the other rather than backwards out the far side.
 */
public final class PortalCrossing {
	private PortalCrossing() {}

	/**
	 * How far in front of the destination the traveller is placed, along the destination's outward
	 * normal.
	 *
	 * <p>Not cosmetic. Landing exactly on the destination plane leaves the traveller ambiguously on
	 * either side of it, so the very next tick can read as another crossing and bounce them straight
	 * back. A step clear of the plane makes the next tick's test unambiguous.
	 */
	public static final double EXIT_CLEARANCE = 0.35;

	/** Where a traveller comes out, and which way it is moving when it does. */
	public record Exit(PortalTransform.Vec3 position, PortalTransform.Vec3 velocity) {}

	/**
	 * Whether the step from {@code prev} to {@code now} passed through the portal's face, entering from
	 * the side its normal points to.
	 *
	 * <p>Direction matters: a portal is entered from the front. Someone walking up to the back of it
	 * has not travelled, and testing an undirected sign change would teleport them anyway.
	 *
	 * <p>Deliberately tests the <em>segment</em>, not whether a position is inside the block. A fast
	 * enough entity moves further than the portal is thick in a single tick and would otherwise step
	 * clean over it — the classic tunnelling miss, and this project's ships are fast.
	 */
	public static boolean crossedInward(PortalTransform.Vec3 prev, PortalTransform.Vec3 now,
			PortalTransform.Vec3 planePoint, PortalTransform.Vec3 normal) {
		PortalTransform.Vec3 n = normal.normalized();
		double before = prev.minus(planePoint).dot(n);
		double after = now.minus(planePoint).dot(n);
		return before > 0 && after <= 0;
	}

	/**
	 * Where along the step the plane was met, as a fraction of it — so the caller can check the entity
	 * passed through the portal's opening rather than through the wall a few blocks to its left.
	 *
	 * @return 0..1 along {@code prev}→{@code now}, or -1 when the step does not cross the plane at all.
	 */
	public static double crossingFraction(PortalTransform.Vec3 prev, PortalTransform.Vec3 now,
			PortalTransform.Vec3 planePoint, PortalTransform.Vec3 normal) {
		PortalTransform.Vec3 n = normal.normalized();
		double before = prev.minus(planePoint).dot(n);
		double after = now.minus(planePoint).dot(n);
		double delta = before - after;
		if (Math.abs(delta) < 1e-12) return -1;
		double t = before / delta;
		if (t < 0 || t > 1) return -1;
		return t;
	}

	/** The point on the portal plane the step passed through, or {@code null} if it did not. */
	public static PortalTransform.Vec3 crossingPoint(PortalTransform.Vec3 prev, PortalTransform.Vec3 now,
			PortalTransform.Vec3 planePoint, PortalTransform.Vec3 normal) {
		double t = crossingFraction(prev, now, planePoint, normal);
		if (t < 0) return null;
		return prev.plus(now.minus(prev).scaled(t));
	}

	/**
	 * Transform a traveller from one portal to its partner.
	 *
	 * <p>Position goes through {@link PortalTransform#transformPoint} at 1:1 scale; velocity is turned
	 * by the same rotation but not translated, because it is a direction and has no place in the world
	 * to be moved from. The result is then stepped {@link #EXIT_CLEARANCE} clear of the destination
	 * plane along its outward normal — the direction a traveller leaves by, given the convention above.
	 */
	public static Exit exitFor(PortalTransform.Vec3 entryPos, PortalTransform.Vec3 entryVelocity,
			PortalTransform.Vec3 srcPos, PortalTransform.Vec3 srcNormal,
			PortalTransform.Vec3 dstPos, PortalTransform.Vec3 dstNormal) {
		PortalTransform.Vec3 dstN = dstNormal.normalized();
		PortalTransform.Quat rotation =
				PortalTransform.rotationBetween(srcNormal.normalized(), dstN.scaled(-1));

		PortalTransform.Vec3 moved =
				PortalTransform.transformPoint(entryPos, srcPos, srcNormal, dstPos, dstNormal, 1.0);
		PortalTransform.Vec3 placed = moved.plus(dstN.scaled(EXIT_CLEARANCE));
		return new Exit(placed, rotation.rotate(entryVelocity));
	}
}
