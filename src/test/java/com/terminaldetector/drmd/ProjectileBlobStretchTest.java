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
 * stretch was sized from speed alone ({@code clamp(speed*0.55, 0, 7)} blocks), so a fresh bolt reached
 * its ~7-block half-length before it had moved anywhere — and centred a couple of blocks out at a
 * muzzle's actual distance, that streak subtended most of the screen as a flat wash of colour instead
 * of a bolt. The fix caps the stretch by the round's own distance to the camera (0.6x) so it grows in
 * behind the round instead of appearing full-size at the muzzle.
 */
class ProjectileBlobStretchTest {
	private static final float HALF_BOLT = 0.26f;

	/** Mirrors renderBlob's stretch/length math — keep in sync if that formula changes. */
	private static float length(float speed, float distToCamera) {
		float stretch = (float) Math.min(Math.max(speed * 0.55, 0.0), 7.0);
		stretch = Math.min(stretch, distToCamera * 0.6f);
		return HALF_BOLT + stretch;
	}

	@Test
	@DisplayName("at a muzzle's actual distance, a fast bolt's streak no longer swallows the whole view")
	void freshBoltAtMuzzleDistanceIsCapped() {
		float speed = 70f; // ~6200 source units/tick, this mod's laser speed
		float muzzleDist = 1.5f; // blocks — a wing-mount muzzle offset (WeaponClusters/DefaultLayouts)
		float len = length(speed, muzzleDist);
		assertTrue(len < muzzleDist,
				"streak half-length (" + len + ") must stay under the round's own distance to camera (" + muzzleDist + ")");
	}

	@Test
	@DisplayName("once the round is far enough out, the cap stops applying and the streak reaches full size")
	void farBoltReachesNaturalClamp() {
		float speed = 70f;
		float farDist = 50f; // one tick of flight at this speed already clears this
		float len = length(speed, farDist);
		assertEquals(HALF_BOLT + 7.0f, len, 1e-6f,
				"far from the camera the natural 7-block clamp should govern, not the distance cap");
	}

	@Test
	@DisplayName("the cap never produces a negative or degenerate length, even at zero distance")
	void zeroDistanceIsSafe() {
		float len = length(70f, 0f);
		assertEquals(HALF_BOLT, len, 1e-6f,
				"at zero distance the streak should collapse to just the base half-size, not vanish or go negative");
	}

	@Test
	@DisplayName("the distance cap grows monotonically, so the streak grows in smoothly behind the round")
	void capGrowsMonotonicallyWithDistance() {
		float speed = 70f;
		float prev = length(speed, 0f);
		for (float d = 0.5f; d <= 20f; d += 0.5f) {
			float cur = length(speed, d);
			assertTrue(cur >= prev, "length should never shrink as distance increases (d=" + d + ")");
			prev = cur;
		}
	}
}
