package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.level.WorldLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pilot loitering inside the End band was seeing "OBLIVION SEAM" ghosted through the flight HUD's
 * ENERGY/THRUST panels almost continuously — not the brief crossing flash the message is meant to be.
 *
 * <p>Cause: {@code SeamWarmup.tickColumn}'s End branch gated the repeating action-bar announce on the
 * same {@code crit} flag it uses for streaming radius, and that flag is deliberately {@code true} for
 * a pilot's <em>entire</em> time above {@code END_SEAM_Y} — correct for streaming (islands keep loading
 * in as you roam), wrong for a one-shot notification, which then re-fired every 80 ticks forever.
 * {@code InGameHudMixin} draws the DRMD flight HUD after vanilla's own render pass, and its panels are
 * only translucently filled ({@code PANEL = 0xA6000C10}), so an action-bar message that never stops
 * repeating shows through them continuously — read from the outside as the HUD itself glitching.
 *
 * <p>{@code SeamWarmup.approaching}/{@code WARM_CRITICAL}/{@code END_SEAM_Y} are private, so the seam
 * check is mirrored here rather than imported — a {@code ServerPlayerEntity} tick can't run outside
 * Minecraft anyway. {@code WorldLevels.ORBITAL_TOP} is a plain constant and is used directly.
 */
class SeamAnnounceTest {
	/** Mirrors {@code SeamWarmup.WARM_CRITICAL}. */
	private static final int WARM_CRITICAL = 10;
	/** Mirrors {@code SeamWarmup.END_SEAM_Y} (= {@code WorldLevels.ORBITAL_TOP}). */
	private static final int END_SEAM_Y = WorldLevels.ORBITAL_TOP;

	/** Mirrors {@code SeamWarmup.approaching}. */
	private static boolean approaching(double y, double projectedY, int seamY, int window) {
		if (Math.abs(y - seamY) <= window) return true;
		if (Math.abs(projectedY - seamY) <= window) return true;
		return (y - seamY) * (projectedY - seamY) < 0;
	}

	/** The bug that shipped: gating the announce on the same flag streaming uses. */
	private static boolean preFixShouldAnnounce(double y, double projectedY) {
		boolean crit = approaching(y, projectedY, END_SEAM_Y, WARM_CRITICAL) || y >= END_SEAM_Y;
		return crit;
	}

	/** The fix: gate the announce on seam-face proximity alone; streaming keeps the wider flag. */
	private static boolean fixedShouldAnnounce(double y, double projectedY) {
		return approaching(y, projectedY, END_SEAM_Y, WARM_CRITICAL);
	}

	@Test
	@DisplayName("pre-fix: loitering deep inside the band still says the announce should fire — the bug")
	void preFixNeverStopsInsideTheBand() {
		double deepInside = END_SEAM_Y + 300; // well past the seam, not climbing toward anything
		assertTrue(preFixShouldAnnounce(deepInside, deepInside),
				"this is the exact condition that made the message repeat forever once inside the band");
	}

	@Test
	@DisplayName("fixed: loitering deep inside the band no longer re-triggers the announce")
	void fixedStopsOnceSafelyPastTheFace() {
		double deepInside = END_SEAM_Y + 300;
		assertFalse(fixedShouldAnnounce(deepInside, deepInside),
				"an announce this far from the seam face, with no crossing projected, would still ghost"
						+ " through the flight HUD for as long as the pilot stays up there");
	}

	@Test
	@DisplayName("fixed: an actual crossing still announces — the heads-up itself is not lost")
	void fixedStillAnnouncesRealCrossing() {
		double justBelow = END_SEAM_Y - 4;
		double projectedAcross = END_SEAM_Y + 40;
		assertTrue(fixedShouldAnnounce(justBelow, projectedAcross),
				"a pilot actually about to cross the seam should still get the heads-up");
	}

	@Test
	@DisplayName("fixed: a fast climb from well below still announces via the projected position")
	void fixedAnnouncesOnFastApproachFromBelow() {
		// Mirrors the reported screenshot: sky and scenery read as the Sky band (well below the seam)
		// while the HUD already says "End band streaming" — a steep climb projects into the window
		// from LOOKAHEAD_SECONDS out, well before actual Y gets anywhere near it.
		double wellBelow = END_SEAM_Y - 250;
		double projectedNearSeam = END_SEAM_Y + 2;
		assertTrue(fixedShouldAnnounce(wellBelow, projectedNearSeam),
				"projected-arrival should still trigger the heads-up even far from the seam by actual Y");
	}
}
