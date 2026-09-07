package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/**
 * What an event left behind — the part of it that outlives it and can be found later.
 *
 * <p><b>Why this is separate from the event.</b> An event has a beginning and an end; a consequence
 * has only a beginning. The design's whole "observation, then a long gap, then discovery" shape
 * depends on the second outliving the first: the strike is over in seconds and the black ground is
 * there when a player walks in a week later. Keeping them one object would mean either events that
 * never end or history that disappears.
 *
 * <p><b>Severity is one ordered chain, because the design writes it as one.</b> A settlement is
 * attacked, then destroyed, then the ground is burnt, then there is wreckage, then the area is
 * dangerous — each step later and worse than the last. Modelling that as separate flags would allow
 * "dangerous but not attacked", which is not a state the world can be in.
 *
 * <p><b>What this is not.</b> Not the blocks. Burning the ground still goes through
 * {@code ScarApplier} and {@code ScarMapState}; those say <em>that</em> a place is scarred, for the
 * planet map. This says <em>why</em>, <em>when</em> and <em>how badly</em>, which is what a player
 * piecing a story together and an NPC with something to mention both need, and what neither of those
 * stores.
 */
public record D6Consequence(
		Severity severity,
		Vec3 centre,
		double radius,
		long causeEventId,
		String causeType,
		long createdTick) {

	/** The design's own chain, in the order it gives it. Worse is later. */
	public enum Severity {
		ATTACKED,
		DESTROYED,
		BURNED,
		WRECKAGE,
		HAZARDOUS;

		public boolean atLeast(Severity other) {
			return ordinal() >= other.ordinal();
		}
	}

	public boolean contains(Vec3 point) {
		return point.minus(centre).lengthSquared() <= radius * radius;
	}

	/** Whether two of these describe the same piece of ground. */
	public boolean overlaps(D6Consequence other) {
		double reach = radius + other.radius;
		return centre.minus(other.centre).lengthSquared() <= reach * reach;
	}

	/**
	 * Fold another consequence into this one.
	 *
	 * <p>Two strikes on the same village are one ruin, not two, and the alternative — a record per
	 * event — fills a save with thousands of overlapping craters and makes "what happened here" an
	 * unanswerable question.
	 *
	 * <p>The rules follow from what a person standing there would say. The worse severity wins,
	 * because damage does not undo. The area is the smallest sphere holding both, because the ruin is
	 * as big as everything that was ruined. The creation time is the earlier, because that is when
	 * this place started being like this. And the cause is the <b>later</b> one — asked what happened
	 * here, the answer is the most recent thing, with the older left to whatever tells longer stories.
	 */
	public D6Consequence deepenedBy(D6Consequence other) {
		Severity worst = severity.atLeast(other.severity) ? severity : other.severity;
		boolean otherIsNewer = other.createdTick >= createdTick;
		Sphere area = enclosing(centre, radius, other.centre, other.radius);
		return new D6Consequence(
				worst,
				area.centre(),
				area.radius(),
				otherIsNewer ? other.causeEventId : causeEventId,
				otherIsNewer ? other.causeType : causeType,
				Math.min(createdTick, other.createdTick));
	}

	/** A centre and a radius, so {@link #enclosing} can return both. */
	public record Sphere(Vec3 centre, double radius) {}

	/**
	 * The smallest sphere containing two spheres.
	 *
	 * <p>Exact rather than approximate — the usual shortcut of keeping one centre and growing the
	 * radius to reach the far edge of the other makes the area grow every time two are folded, and
	 * after a dozen strikes a village's "ruin" covers a region.
	 */
	public static Sphere enclosing(Vec3 c1, double r1, Vec3 c2, double r2) {
		Vec3 between = c2.minus(c1);
		double distance = between.length();
		// Containment first, and not only as an optimisation: with one inside the other the general
		// formula would still be right but the direction is undefined when the centres coincide.
		if (distance + r2 <= r1) return new Sphere(c1, r1);
		if (distance + r1 <= r2) return new Sphere(c2, r2);
		double radius = (distance + r1 + r2) / 2;
		// Along the line between the centres, far enough from the first to just reach its edge.
		return new Sphere(c1.plus(between.scaled((radius - r1) / distance)), radius);
	}
}
