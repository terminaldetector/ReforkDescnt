package com.terminaldetector.drmd.world.event;

import com.terminaldetector.drmd.d6.D6Consequence.Severity;

/**
 * What each kind of event leaves behind, if anything.
 *
 * <p><b>Why this is here and not on the event.</b> {@code D6WorldEvent} deliberately holds no
 * content — no participants, no objective, no consequence — because everything on it has to be
 * arithmetic for an unobserved event to cost nothing. What a strike leaves is content, so it lives on
 * the Minecraft side with the rest of the content.
 *
 * <p>Deriving it from the type rather than storing it on the event has a second benefit worth having:
 * these numbers can be retuned without migrating a single save.
 *
 * <p><b>Not every event leaves a mark.</b> A patrol that passes overhead and continues has nothing to
 * find afterwards, and saying so with a null is the point — otherwise the world fills with records of
 * things that did not happen anywhere.
 */
public final class EventAftermath {
	private EventAftermath() {}

	/** What is left at the place an event ended. */
	public record Trace(Severity severity, double radius) {}

	/**
	 * @return what this kind of event leaves, or null for one that leaves nothing
	 */
	public static Trace forType(String type) {
		if (type == null) return null;
		return switch (type) {
			// A flight is the design's own example of something with no aftermath at all: six lights
			// cross the sky and are gone, and the player is meant to be left with nothing but the
			// memory of having seen them.
			case "flight" -> null;
			case "raid" -> new Trace(Severity.ATTACKED, 16);
			case "swarm" -> new Trace(Severity.DESTROYED, 32);
			case "strike" -> new Trace(Severity.BURNED, 24);
			case "reactor" -> new Trace(Severity.HAZARDOUS, 48);
			default -> null;
		};
	}
}
