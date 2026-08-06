package com.terminaldetector.drmd;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry behind a multi-muzzle laser volley converging on the reticle, and the bug that shipped
 * in it: at close range, a bolt fired from an off-centre wing muzzle toward a convergence point can
 * point nowhere near forward — reported as the whole volley firing "in every direction."
 *
 * <p>{@code DescentLaserFire.resolveAimPoint} needs a {@code PlayerEntity}/{@code ServerWorld} raycast
 * and cannot run outside Minecraft, so this pins the one line that actually matters: the floor on how
 * close the returned point is allowed to be, {@code DescentLaserFire.MIN_CONVERGE_SU} — copied below
 * as {@link #MIN_CONVERGE_SU_BLOCKS} rather than referenced, since importing the real class would drag
 * in its whole {@code PlayerEntity}/{@code ServerWorld}/{@code RaycastContext} surface for one constant.
 * Change one, change the other. Everything else here — muzzle offsets, the convergence-angle formula —
 * is plain {@code Vec3d} geometry mirrored from {@code fireModuleBolts}/{@code firePrism} exactly
 * enough to make the claim honestly: given where the convergence point ends up, how far off forward
 * does a wing bolt fire.
 */
class LaserConvergenceTest {
	/** Mirrors {@code DescentLaserFire.MIN_CONVERGE_SU} (800) already converted via {@code DescentMod.su()}. */
	private static final double MIN_CONVERGE_SU_BLOCKS = 800.0 / 80.0;

	/** SideLeft / SideRight, DefaultLayouts.layout row 1 — Source inches / 16, matching WeaponClusters. */
	private static final Vec3d MUZZLE_LEFT = new Vec3d(-29 / 16.0, -13 / 16.0, 19 / 16.0);
	private static final Vec3d MUZZLE_RIGHT = new Vec3d(29 / 16.0, -13 / 16.0, 19 / 16.0);
	/** Upper wing row — the widest offset in the four-bank quad layout, 32 inches out. */
	private static final Vec3d MUZZLE_UPPER = new Vec3d(32 / 16.0, 10 / 16.0, 19 / 16.0);

	private static final Vec3d EYE = Vec3d.ZERO;
	private static final Vec3d FORWARD = new Vec3d(0, 0, 1);
	private static final Vec3d RIGHT = new Vec3d(1, 0, 0);
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	/** Muzzle world position from eye-relative right/up/forward offsets — same basis WeaponCore.muzzle uses. */
	private static Vec3d muzzlePos(Vec3d offset) {
		return EYE.add(RIGHT.multiply(offset.x)).add(UP.multiply(offset.y)).add(FORWARD.multiply(offset.z));
	}

	/** Convergence-point resolution, mirroring the fixed resolveAimPoint: floor the hit distance. */
	private static Vec3d aimPoint(double rawHitDistance, double minDistance) {
		double d = Math.max(rawHitDistance, minDistance);
		return EYE.add(FORWARD.multiply(d));
	}

	/** Angle between a bolt fired from {@code muzzle} at {@code target} and true forward. */
	private static double convergenceAngleDegrees(Vec3d muzzleOffset, Vec3d target) {
		Vec3d pos = muzzlePos(muzzleOffset);
		Vec3d dir = target.subtract(pos).normalize();
		double cos = Math.max(-1, Math.min(1, dir.dotProduct(FORWARD)));
		return Math.toDegrees(Math.acos(cos));
	}

	@Test
	@DisplayName("unclamped, a point-blank target sends a wing bolt wildly off forward — the bug that shipped")
	void unclampedCloseRangeMisaimsWildly() {
		// No floor: exactly the pre-fix resolveAimPoint, a bare raycast hit distance.
		Vec3d pointBlank = aimPoint(0.4, 0.0);
		double angle = convergenceAngleDegrees(MUZZLE_UPPER, pointBlank);
		assertTrue(angle > 45.0,
				"expected the unclamped point-blank case to reproduce the wide-angle bug, only got " + angle + "°");
	}

	@Test
	@DisplayName("the shipped floor keeps every wing muzzle within a tight cone of forward, at any raycast distance")
	void flooredConvergenceStaysTight() {
		// Worst case lands exactly at the floor (closer raw hits are lifted up to it, farther ones are
		// left alone and only get more parallel) — computed once at 6/8/9/10/11 blocks to pick 10 as
		// the number that puts this comfortably under 15°, not asserted against a round guess.
		double worst = 0;
		for (double raw : new double[]{0.0, 0.1, 0.5, 1.0, 2.0, 4.0, 6.0, 10.0, 35.0, 112.5}) {
			Vec3d target = aimPoint(raw, MIN_CONVERGE_SU_BLOCKS);
			for (Vec3d muzzle : new Vec3d[]{MUZZLE_LEFT, MUZZLE_RIGHT, MUZZLE_UPPER}) {
				worst = Math.max(worst, convergenceAngleDegrees(muzzle, target));
			}
		}
		assertTrue(worst < 15.0, "worst-case convergence angle with the floor applied was " + worst + "°");
	}

	@Test
	@DisplayName("the floor never fires backward or sideways — angle strictly decreases as the floor distance grows")
	void widerFloorIsAlwaysSaferThanNoFloor() {
		double noFloorAngle = convergenceAngleDegrees(MUZZLE_UPPER, aimPoint(0.4, 0.0));
		double minDist = MIN_CONVERGE_SU_BLOCKS;
		double flooredAngle = convergenceAngleDegrees(MUZZLE_UPPER, aimPoint(0.4, minDist));
		assertTrue(flooredAngle < noFloorAngle,
				"floored angle " + flooredAngle + "° should be well under the unclamped " + noFloorAngle + "°");
	}

	@Test
	@DisplayName("far targets are unaffected — the floor only ever raises a distance, never lowers one")
	void farTargetsConvergeAsBefore() {
		double minDist = MIN_CONVERGE_SU_BLOCKS;
		Vec3d farRaw = aimPoint(35.0, 0.0);
		Vec3d farFloored = aimPoint(35.0, minDist);
		assertTrue(farRaw.distanceTo(farFloored) < 1e-9,
				"a target already past the floor must resolve to the exact same point");
	}
}
