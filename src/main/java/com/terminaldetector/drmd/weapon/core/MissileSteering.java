package com.terminaldetector.drmd.weapon.core;

import net.minecraft.util.math.Vec3d;

/**
 * The turn a guided warhead makes in one tick.
 *
 * <p>Its own class because it is the one part of a missile that is pure geometry — no entity, no
 * world, nothing to bootstrap — and it is the part that was quietly wrong. Three weapons share it:
 * the Homing missile chasing a lock, the Guided missile chasing the pilot's aim, and the blobs a
 * Smart Missile sheds. They differ only by turn rate.
 */
public final class MissileSteering {
	private MissileSteering() {}

	/**
	 * Bend {@code vel} toward {@code desired} by one tick's worth of turn at {@code turnRate} deg/s.
	 *
	 * <p>Speed is preserved: a missile that turns does not also slow down.
	 *
	 * @return the new velocity, or {@code vel} unchanged when there is nothing sensible to do
	 */
	public static Vec3d steer(Vec3d vel, Vec3d desired, float turnRate) {
		if (desired.lengthSquared() < 1e-6) return vel;
		Vec3d cur = vel.lengthSquared() > 1e-6 ? vel.normalize() : desired;
		Vec3d want = desired.normalize();

		// Straight back is singular for a blend: cur and want cancel, and depending on which side of
		// a half the blend factor lands, the missile either snaps round instantly or refuses to turn
		// at all. Neither is a turn. A homing missile at 140 deg/s sat in the second case — a target
		// directly behind it was never turned toward, at any range, for the missile's whole life.
		// Nudge the target off-axis so there is an arc to rotate through; the next ticks finish it.
		if (cur.dotProduct(want) < -0.999) {
			Vec3d off = Math.abs(cur.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
			want = want.add(cur.crossProduct(off).normalize().multiply(0.2)).normalize();
		}

		double maxRad = Math.toRadians(turnRate / 20.0);
		Vec3d blended = cur.add(want.subtract(cur).multiply(Math.min(1.0, maxRad * 3)));
		// Guard above Vec3d.normalize's own 1e-4 cutoff, so it never hands back a zero heading.
		if (blended.lengthSquared() < 1e-6) return vel;
		return blended.normalize().multiply(vel.length());
	}
}
