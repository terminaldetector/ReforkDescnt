package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.micro.MicroGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TunnelCarving.carveCapsule} backs {@code WeaponFx.drillTunnel} — the tunnel drill rig and
 * the engineer's own hand tool both bore through it, and unlike the fixed Descent shaft, the
 * engineer's tool fires along the player's full look vector: pitch included, no axis this can assume
 * fixed. This pins the point-to-segment distance the whole shape is built on (mirrored — {@code
 * TunnelCarving} imports {@code Vec3d}/{@code ServerWorld}, unavailable here — using plain doubles,
 * same reasoning as this suite's other mirrors) and, separately, that chamfering helps a genuinely
 * diagonal bore the same way {@code TunnelCarvingGeometryTest} already showed for the vertical shaft.
 */
class TunnelCarvingCapsuleTest {
	/** Mirrors TunnelCarving.distSqToSegment. */
	private static double distSqToSegment(double px, double py, double pz,
										   double ax, double ay, double az,
										   double bx, double by, double bz) {
		double abx = bx - ax, aby = by - ay, abz = bz - az;
		double abLenSq = abx * abx + aby * aby + abz * abz;
		double t = abLenSq < 1e-9 ? 0.0
				: ((px - ax) * abx + (py - ay) * aby + (pz - az) * abz) / abLenSq;
		t = Math.max(0.0, Math.min(1.0, t));
		double cx = ax + abx * t, cy = ay + aby * t, cz = az + abz * t;
		double dx = px - cx, dy = py - cy, dz = pz - cz;
		return dx * dx + dy * dy + dz * dz;
	}

	@Test
	@DisplayName("a point on the segment itself is distance zero")
	void pointOnSegmentIsZero() {
		double d2 = distSqToSegment(5, 5, 5, 0, 0, 0, 10, 10, 10);
		assertEquals(0.0, d2, 1e-9);
	}

	@Test
	@DisplayName("distance clamps to the nearer endpoint rather than the infinite line")
	void beyondAnEndpointClampsToThatEndpoint() {
		// Segment along X from 0 to 10; point is "before" the start, not beside the line.
		double d2 = distSqToSegment(-3, 0, 0, 0, 0, 0, 10, 0, 0);
		assertEquals(9.0, d2, 1e-9, "should measure to the start endpoint (dist 3), not the extended line (dist 0)");
	}

	@Test
	@DisplayName("perpendicular distance to the middle of a segment is the straight-line offset")
	void perpendicularToMidpointIsPlainOffset() {
		double d2 = distSqToSegment(5, 4, 0, 0, 0, 0, 10, 0, 0);
		assertEquals(16.0, d2, 1e-9); // offset 4, straight up from the midpoint
	}

	@Test
	@DisplayName("a zero-length segment degrades to a plain point (sphere) distance")
	void degenerateSegmentIsAPointDistance() {
		double d2 = distSqToSegment(3, 4, 0, 1, 1, 1, 1, 1, 1);
		double expected = (3 - 1) * (3 - 1) + (4 - 1) * (4 - 1) + (0 - 1) * (0 - 1);
		assertEquals(expected, d2, 1e-9);
	}

	/** Mirrors TunnelCarving.capsuleMask's per-cell test for a diagonal segment. */
	private static long capsuleMask(int blockX, int blockY, int blockZ,
									 double ax, double ay, double az, double bx, double by, double bz, double r2) {
		return MicroGrid.build((cx, cy, cz) -> {
			double wx = blockX + (cx + 0.5) * MicroGrid.CELL_SIZE;
			double wy = blockY + (cy + 0.5) * MicroGrid.CELL_SIZE;
			double wz = blockZ + (cz + 0.5) * MicroGrid.CELL_SIZE;
			return distSqToSegment(wx, wy, wz, ax, ay, az, bx, by, bz) > r2;
		});
	}

	@Test
	@DisplayName("chamfering never clears a cell a true diagonal capsule does not reach")
	void chamferNeverOvershootsADiagonalCapsule() {
		// A genuinely 3D diagonal bore, not axis-aligned on any plane — the case a fixed-axis
		// shortcut (like the vertical shaft's own 2D circle) cannot cover.
		double ax = 0, ay = 0, az = 0, bx = 6, by = 3, bz = 4;
		double radius = 2.5;
		double r2 = radius * radius;
		int outer = (int) Math.ceil(radius) + 1;

		for (int x = -outer; x <= (int) bx + outer; x++) {
			for (int y = -outer; y <= (int) by + outer; y++) {
				for (int z = -outer; z <= (int) bz + outer; z++) {
					long mask = capsuleMask(x, y, z, ax, ay, az, bx, by, bz, r2);
					for (int cx = 0; cx < MicroGrid.EDGE; cx++) {
						for (int cy = 0; cy < MicroGrid.EDGE; cy++) {
							for (int cz = 0; cz < MicroGrid.EDGE; cz++) {
								if (MicroGrid.get(mask, cx, cy, cz)) continue; // kept solid, nothing to check
								double wx = x + (cx + 0.5) * MicroGrid.CELL_SIZE;
								double wy = y + (cy + 0.5) * MicroGrid.CELL_SIZE;
								double wz = z + (cz + 0.5) * MicroGrid.CELL_SIZE;
								double d2 = distSqToSegment(wx, wy, wz, ax, ay, az, bx, by, bz);
								assertTrue(d2 <= r2,
										"cleared cell at block (" + x + "," + y + "," + z + ") cell ("
												+ cx + "," + cy + "," + cz + ") lies outside the true capsule");
							}
						}
					}
				}
			}
		}
	}
}
