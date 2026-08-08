package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@code ProjectileRenderer.renderBlob}'s stretch formula ({@code VertexConsumer}/
 * {@code MatrixStack} are unavailable here, same mirroring approach as {@code LaserConvergenceTest}).
 * Pins the fix for "projectile display bugged on firing" (still broken after the debug-layer swap and
 * the winding fix, both of which touched only the box/quad path, not this one): a bolt's screen-track
 * stretch was sized from speed alone. The multiplier/cap here were re-derived after
 * {@code WeaponSpeedConversionTest}'s fix corrected the actual per-tick speed by 20x — the old numbers
 * ({@code clamp(speed*0.55, 0, 7)}) were sized for the pre-fix ~70 block/tick laser and produced a
 * streak several times longer than the ground a correctly-paced ~4 block/tick round actually covers,
 * read as "just bars" and long enough for ordinary per-bolt angle variation (dual/quad convergence,
 * velocity-sync quantisation) to show as a visible wobble at the far end — a shorter streak is far less
 * sensitive to the same angular variation, so tightening this reads as steadier too, not just shorter.
 * The muzzle-proximity distance cap (0.6x round-to-camera distance) is unchanged and still covers the
 * "streak appears full-size at the muzzle" case on its own terms.
 */
class ProjectileBlobStretchTest {
	private static final float HALF_BOLT = 0.26f;

	/** Mirrors renderBlob's stretch/length math — keep in sync if that formula changes. */
	private static float length(float speed, float distToCamera) {
		float stretch = (float) Math.min(Math.max(speed * 0.3, 0.0), 3.0);
		stretch = Math.min(stretch, distToCamera * 0.6f);
		return HALF_BOLT + stretch;
	}

	@Test
	@DisplayName("at a muzzle's actual distance, a bolt's streak stays shorter than the distance itself")
	void freshBoltAtMuzzleDistanceIsCapped() {
		float speed = 3.875f; // laser: 6200 su/s, correctly converted (WeaponSpeedConversionTest)
		float muzzleDist = 1.5f; // blocks — a wing-mount muzzle offset (WeaponClusters/DefaultLayouts)
		float len = length(speed, muzzleDist);
		assertTrue(len < muzzleDist,
				"streak half-length (" + len + ") must stay under the round's own distance to camera (" + muzzleDist + ")");
	}

	@Test
	@DisplayName("once the round is far enough out, the cap stops applying and the streak reaches its natural size")
	void farBoltReachesNaturalClamp() {
		float speed = 3.875f;
		float farDist = 10f; // a couple of ticks of flight at this speed already clears this
		float len = length(speed, farDist);
		assertEquals(HALF_BOLT + speed * 0.3f, len, 1e-6f,
				"far from the camera the speed-derived stretch should govern, not the distance cap");
	}

	@Test
	@DisplayName("even the fastest bolt-type weapon never reaches the absolute 3-block stretch ceiling")
	void evenFastestWeaponStaysUnderAbsoluteCeiling() {
		float megaLaserSpeed = 4.875f; // 7800 su/s, this mod's fastest MESH_BOLT weapon
		float len = length(megaLaserSpeed, 50f);
		assertTrue(len < HALF_BOLT + 3.0f,
				"the 3-block absolute ceiling should be a defensive cap, not something current weapons reach");
	}

	@Test
	@DisplayName("the cap never produces a negative or degenerate length, even at zero distance")
	void zeroDistanceIsSafe() {
		float len = length(3.875f, 0f);
		assertEquals(HALF_BOLT, len, 1e-6f,
				"at zero distance the streak should collapse to just the base half-size, not vanish or go negative");
	}

	@Test
	@DisplayName("the distance cap grows monotonically, so the streak grows in smoothly behind the round")
	void capGrowsMonotonicallyWithDistance() {
		float speed = 3.875f;
		float prev = length(speed, 0f);
		for (float d = 0.5f; d <= 20f; d += 0.5f) {
			float cur = length(speed, d);
			assertTrue(cur >= prev, "length should never shrink as distance increases (d=" + d + ")");
			prev = cur;
		}
	}
}
