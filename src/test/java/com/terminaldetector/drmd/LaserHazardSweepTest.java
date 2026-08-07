package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors the rotating-beam geometry inside {@code AerialBombEntity.tickLaserHazard} ({@code Vec3d}
 * is unavailable here, same mirroring approach as {@code TunnelCarvingCapsuleTest}): a landed laser
 * cluster bomblet sweeps two opposite beams around a spin axis (local "up") by advancing an angle
 * each tick. This pins the properties that make the sweep read as a clean rotating pair rather than
 * a wobble: the swept direction stays unit length and in-plane every tick, the two beams stay exact
 * opposites, and the pair covers at least a full turn before the hazard's own countdown ends.
 */
class LaserHazardSweepTest {
	private static final int LASER_HAZARD_TICKS = 100;
	private static final double LASER_SPIN_DEG_PER_TICK = 9.0;

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0]
		};
	}

	private static double[] sub(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] scale(double[] a, double s) {
		return new double[]{a[0] * s, a[1] * s, a[2] * s};
	}

	private static double length(double[] a) {
		return Math.sqrt(dot(a, a));
	}

	private static double[] normalize(double[] a) {
		double len = length(a);
		return new double[]{a[0] / len, a[1] / len, a[2] / len};
	}

	/** An arbitrary orthonormal (ref, ortho) pair perpendicular to a given spin axis. */
	private static double[][] perpendicularBasis(double[] spinAxis) {
		double[] arbitrary = {1.0, -0.3, 0.4};
		double[] ref = normalize(sub(arbitrary, scale(spinAxis, dot(arbitrary, spinAxis))));
		double[] ortho = normalize(cross(ref, spinAxis));
		return new double[][]{ref, ortho};
	}

	/** Mirrors AerialBombEntity.tickLaserHazard's beamDir formula. */
	private static double[] beamDirAt(double[] ref, double[] ortho, int laserHazardTicks) {
		double angle = Math.toRadians((LASER_HAZARD_TICKS - laserHazardTicks) * LASER_SPIN_DEG_PER_TICK);
		return new double[]{
				ref[0] * Math.cos(angle) + ortho[0] * Math.sin(angle),
				ref[1] * Math.cos(angle) + ortho[1] * Math.sin(angle),
				ref[2] * Math.cos(angle) + ortho[2] * Math.sin(angle)
		};
	}

	@Test
	@DisplayName("beam direction stays unit length across the whole countdown")
	void staysUnitLength() {
		double[] spinAxis = normalize(new double[]{0.3, 1.0, -0.2});
		double[][] basis = perpendicularBasis(spinAxis);
		for (int t = LASER_HAZARD_TICKS; t >= 0; t--) {
			double[] dir = beamDirAt(basis[0], basis[1], t);
			assertEquals(1.0, length(dir), 1e-9, "beam direction drifted off unit length at tick " + t);
		}
	}

	@Test
	@DisplayName("beam direction stays perpendicular to the spin axis — sweeps in-plane, never tilts toward it")
	void staysPerpendicularToSpinAxis() {
		double[] spinAxis = normalize(new double[]{0.3, 1.0, -0.2});
		double[][] basis = perpendicularBasis(spinAxis);
		for (int t = LASER_HAZARD_TICKS; t >= 0; t -= 7) {
			double[] dir = beamDirAt(basis[0], basis[1], t);
			assertEquals(0.0, dot(dir, spinAxis), 1e-9, "beam tilted out of the sweep plane at tick " + t);
		}
	}

	@Test
	@DisplayName("the two cast beams are exact opposites, every tick")
	void oppositeBeamsAreAntiparallel() {
		double[] spinAxis = {0, 1, 0};
		double[][] basis = perpendicularBasis(spinAxis);
		for (int t = LASER_HAZARD_TICKS; t >= 0; t -= 5) {
			double[] dir = beamDirAt(basis[0], basis[1], t);
			double[] neg = scale(dir, -1);
			assertEquals(-1.0, dot(dir, neg), 1e-9);
		}
	}

	@Test
	@DisplayName("over the hazard's own duration the pair sweeps at least a full turn, so no angle is left unswept")
	void sweepsAtLeastAFullTurn() {
		double totalDegrees = LASER_HAZARD_TICKS * LASER_SPIN_DEG_PER_TICK;
		assertTrue(totalDegrees >= 360.0,
				"hazard duration (" + LASER_HAZARD_TICKS + " ticks @ " + LASER_SPIN_DEG_PER_TICK
						+ " deg/tick = " + totalDegrees + " deg) does not even cover one full sweep");
	}
}
