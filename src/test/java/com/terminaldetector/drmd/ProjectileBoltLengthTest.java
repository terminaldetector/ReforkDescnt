package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@code ProjectileRenderer.renderBolt}'s length formula ({@code VertexConsumer}/
 * {@code MatrixStack} are unavailable here, same mirroring approach as {@code LaserConvergenceTest}).
 *
 * <p>Replaces the old {@code ProjectileBlobStretchTest}, which pinned {@code renderBlob}'s
 * camera-facing-billboard stretch formula — {@code MESH_BOLT} no longer uses that method at all.
 * Reported as "снаряды должны стоять... как и любые другие стрелковые снаряды" (bolts should be
 * oriented the same way as any other projectile) against a reference screenshot of the original's
 * short, steady bolts: the billboard's in-plane rotation was recomputed every frame from the round's
 * velocity projected into the *current camera's* view space, so a bolt's on-screen angle depended on
 * where the player was looking as well as on the round's own direction, and two bolts converging from
 * different wing muzzles toward one aim point (deliberate — see {@code DescentLaserFire}) could read
 * as genuinely different objects rather than one volley. {@code renderBolt} instead reuses
 * {@code ModelOrientation.applyBasis} — the same world-space orientation {@code renderModel} already
 * uses for rockets/mines/drills — so a bolt's orientation is a pure function of its own flight
 * direction, with no camera dependence to wobble.
 */
class ProjectileBoltLengthTest {
	/** Mirrors renderBolt's radius term. */
	private static float radius(float scale) {
		return 0.15f * Math.max(0.55f, scale);
	}

	/** Mirrors renderBolt's length math — keep in sync if that formula changes. */
	private static float length(float speed, float scale) {
		float r = radius(scale);
		return Math.min(Math.max(speed * 0.3f, r * 2.5f), 1.4f);
	}

	@Test
	@DisplayName("the laser's corrected ~3.9 block/tick speed lands well inside the floor/ceiling, not clamped to either")
	void laserLengthIsWithinRangeNotClamped() {
		float speed = 3.875f; // WeaponSpeedConversionTest
		float len = length(speed, 1f);
		float r = radius(1f);
		assertTrue(len > r * 2.5f && len < 1.4f,
				"expected the laser's natural length (" + len + ") to sit strictly between the floor and ceiling");
	}

	@Test
	@DisplayName("a slow or tiny round never reads as a flat disc — length is floored at 2.5x its own radius")
	void neverShorterThanFloor() {
		float len = length(0.01f, 1f);
		assertEquals(radius(1f) * 2.5f, len, 1e-6f);
	}

	@Test
	@DisplayName("even the fastest bolt-type weapon stays at or under the short, reference-matched ceiling")
	void evenFastestWeaponStaysAtCeiling() {
		float megaLaserSpeed = 4.875f; // 7800 su/s, this mod's fastest MESH_BOLT weapon
		assertEquals(1.4f, length(megaLaserSpeed, 1f), 1e-6f);
	}

	@Test
	@DisplayName("visual scale grows the floor (a fatter bolt needs a longer minimum to avoid looking like a disc)")
	void largerScaleRaisesTheFloor() {
		float small = length(0.01f, 0.6f);
		float large = length(0.01f, 2.0f);
		assertTrue(large > small, "a larger visualScale should raise the floored length, not shrink it");
	}
}
