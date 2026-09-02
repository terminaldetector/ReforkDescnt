package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.DescentMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Says why a mirror or portal is showing nothing — once per change, not once per frame.
 *
 * <p>Exists because this is the one part of DRMD where a green build says nothing about whether the
 * feature works, and a blank surface has five different causes that look identical from a chair: no
 * face found, out of range, seen from behind, line of sight blocked, or measured off-screen. Each
 * points at a different file, and without this the only way to tell them apart is to guess.
 *
 * <p>Quiet by construction. A line is written only when the summary changes, so standing in front of
 * a working mirror produces one line and then silence; walking around a world with the toggle on and
 * no mirrors in it produces one line for the whole session. Nothing is written at all while the
 * feature is off, since the callers return before reaching this.
 *
 * <p>The log rather than the screen, deliberately: it costs no rendering, it cannot itself be the
 * thing that breaks the frame being diagnosed, and it survives the client being closed — which the
 * chat message it would otherwise be does not.
 */
public final class PortalViewDiagnostics {
	private PortalViewDiagnostics() {}

	/** Channel → the last summary written for it. Two keys, ever. */
	private static final Map<String, String> LAST = new HashMap<>();

	/** The last line written for a channel, for the diagnostics report to carry. Null if never. */
	public static String lastSummary(String channel) {
		return LAST.get(channel);
	}

	/**
	 * Write {@code summary} for {@code channel} if it differs from the last one written there.
	 *
	 * @param channel {@code "mirror"} or {@code "portal"} — kept separate so one going quiet does not
	 *                mask the other changing.
	 */
	public static void report(String channel, String summary) {
		String previous = LAST.put(channel, summary);
		if (summary.equals(previous)) return;
		DescentMod.LOGGER.info("[drmd view] {}: {}", channel, summary);
	}

	/**
	 * The summary for a frame that drew nothing, naming the stage that stopped it.
	 *
	 * <p>Counts fall through in the order the renderer applies them, so the first zero is the real
	 * answer and the ones after it are consequences.
	 *
	 * @param found     faces the scan returned near the camera
	 * @param facing    of those, the ones seen from the front (a mirror does not use this — pass
	 *                  {@code found})
	 * @param inRange   of those, the ones inside the render range
	 * @param unblocked of those, the ones with clear line of sight
	 */
	public static String whyNothingDrawn(int found, int facing, int inRange, int unblocked) {
		if (found == 0) return "nothing drawn: none in range of the scan";
		if (facing == 0) return "nothing drawn: all " + found + " seen from behind";
		if (inRange == 0) return "nothing drawn: all " + facing + " beyond the render range";
		if (unblocked == 0) return "nothing drawn: line of sight blocked to all " + inRange;
		// Past every gate and still nothing on screen: the face measured off-screen, or there was no
		// off-screen target to draw into. Both live in MirrorScreenBounds/MirrorFramebuffer.
		return "nothing drawn: " + unblocked + " visible but measured off-screen or no render target";
	}
}
