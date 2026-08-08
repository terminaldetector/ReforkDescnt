package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors the two smoothstep ramps that decide how strongly {@code CoreSkyDome} and
 * {@code OrbitalBeltSkyRenderer.paintOblivionEnvelope} occlude the vanilla sun and moon
 * ({@code VertexConsumer}/{@code MinecraftClient} are unavailable here, same mirroring approach as
 * {@code LevelSkyTest}). Both exist because {@code ClientWorldMixin.drmd$levelSky} only tints
 * {@code getSkyColor} — the background colour — while the sun and moon are separate quads vanilla
 * draws regardless of it; hiding those needed a depth-tested occluder instead, gated by these ramps
 * so it fades in rather than popping at a boundary, the same principle {@link LevelSky} itself already
 * follows for the colour tint.
 */
class SkyOcclusionRampTest {
	private static final int INDUSTRIAL_TOP = 40;
	private static final int ABYSS_TOP = -64;
	private static final int ORBITAL_TOP = 1580;

	/** Mirrors CoreSkyDome.lowerAlpha — keep in sync if that formula changes. */
	private static float lowerAlpha(double y) {
		if (y >= INDUSTRIAL_TOP) return 0f;
		if (y <= ABYSS_TOP) return 1f;
		float t = (float) ((INDUSTRIAL_TOP - y) / (double) (INDUSTRIAL_TOP - ABYSS_TOP));
		return t * t * (3 - 2 * t);
	}

	/** Mirrors OrbitalBeltSkyRenderer.envelopeAlpha — keep in sync if that formula changes. */
	private static float envelopeAlpha(double y) {
		if (y <= ORBITAL_TOP) return 0f;
		double top = ORBITAL_TOP + 100;
		if (y >= top) return 1f;
		float t = (float) ((y - ORBITAL_TOP) / (top - ORBITAL_TOP));
		return t * t * (3 - 2 * t);
	}

	@Test
	@DisplayName("lowerAlpha is exactly 0 at the Industrial plateau and exactly 1 by the Abyss, not just close")
	void lowerAlphaHitsItsEndpoints() {
		assertEquals(0f, lowerAlpha(INDUSTRIAL_TOP), 1e-9);
		assertEquals(1f, lowerAlpha(ABYSS_TOP), 1e-9);
		assertEquals(1f, lowerAlpha(ABYSS_TOP - 500), 1e-9, "held flat below the Abyss, not extrapolated");
		assertEquals(0f, lowerAlpha(INDUSTRIAL_TOP + 500), 1e-9, "held flat above Industrial, not extrapolated");
	}

	@Test
	@DisplayName("envelopeAlpha is exactly 0 at the Orbital ceiling and exactly 1 a hundred blocks into the End band")
	void envelopeAlphaHitsItsEndpoints() {
		assertEquals(0f, envelopeAlpha(ORBITAL_TOP), 1e-9);
		assertEquals(1f, envelopeAlpha(ORBITAL_TOP + 100), 1e-9);
		assertEquals(0f, envelopeAlpha(ORBITAL_TOP - 500), 1e-9, "held flat below Orbital, not extrapolated");
		assertEquals(1f, envelopeAlpha(ORBITAL_TOP + 600), 1e-9, "held flat deep in the End band, not extrapolated");
	}

	@Test
	@DisplayName("both ramps are monotonic and continuous — no step a pilot flying straight through would see")
	void ramps() {
		double step = 0.25;
		float prevLower = lowerAlpha(INDUSTRIAL_TOP + 200);
		for (double y = INDUSTRIAL_TOP + 200; y >= ABYSS_TOP - 200; y -= step) {
			float cur = lowerAlpha(y);
			assertTrue(cur >= prevLower - 1e-6, "lowerAlpha must never decrease while descending, at y=" + y);
			assertTrue(Math.abs(cur - prevLower) < 0.01, "lowerAlpha step too large near y=" + y);
			prevLower = cur;
		}
		float prevEnv = envelopeAlpha(ORBITAL_TOP - 200);
		for (double y = ORBITAL_TOP - 200; y <= ORBITAL_TOP + 300; y += step) {
			float cur = envelopeAlpha(y);
			assertTrue(cur >= prevEnv - 1e-6, "envelopeAlpha must never decrease while climbing, at y=" + y);
			assertTrue(Math.abs(cur - prevEnv) < 0.01, "envelopeAlpha step too large near y=" + y);
			prevEnv = cur;
		}
	}

	@Test
	@DisplayName("both ramps stay within 0..1 everywhere")
	void staysInRange() {
		for (double y = -2000; y <= 3000; y += 3.7) {
			float lo = lowerAlpha(y);
			float en = envelopeAlpha(y);
			assertTrue(lo >= 0f && lo <= 1f, "lowerAlpha out of range at y=" + y + ": " + lo);
			assertTrue(en >= 0f && en <= 1f, "envelopeAlpha out of range at y=" + y + ": " + en);
		}
	}
}
