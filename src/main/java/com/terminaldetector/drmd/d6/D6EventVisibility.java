package com.terminaldetector.drmd.d6;

/**
 * How much of a world event an observer at a given distance can actually make out.
 *
 * <p><b>Why this is a class and not a radius.</b> The design asks for the same event to read
 * differently far away, at middle distance and close up — lights and a glow at one range, silhouettes
 * and missiles at another, machines and wreckage at the third. A single "event radius" cannot express
 * that, and worse, it cannot express the case the design leans on hardest: a strike beyond the range
 * where anything of it is visible still lights the sky. Distance alone does not decide; what the
 * event <em>is</em> decides with it.
 *
 * <p><b>Two laws, because there are two things being seen.</b> A shape is resolved while it covers
 * enough of the eye, and angular size falls as {@code 1/d}. A light is seen while it is bright enough
 * against the sky, and brightness falls as {@code 1/d²}. Those are different curves, so the two reaches
 * cross: a drone is a shape long before it is a light, an explosion is a light long after it stops
 * being a shape. Every effect the design wants follows from that crossing rather than from a table.
 *
 * <p><b>What falls out without being written.</b> Six small glowing objects crossing the sky at night
 * read as lights at a kilometre and as drones at three hundred metres — the design's own example, and
 * nothing here encodes it. The same flight by day is invisible at a kilometre, because daylight raises
 * what a light has to beat. A distant strike is a glow to the edge of the drawn world and a wreck only
 * when walked to.
 *
 * <p>Pure arithmetic: no world, no entity, no tick. That is what lets the answer be computed for an
 * event nobody is near.
 */
public final class D6EventVisibility {
	private D6EventVisibility() {}

	/** What an observer can make out, coarse to fine. */
	public enum Band {
		/** Nothing. The event may still be happening. */
		NONE,
		/** A light, a flash, a glow, a tracer — position and brightness, no form. */
		GLOW,
		/** Form and motion: silhouettes, craft, a fight, things falling. */
		SHAPE,
		/** Parts: which machine, what it carries, what broke. */
		DETAIL;

		public boolean atLeast(Band other) {
			return ordinal() >= other.ordinal();
		}
	}

	/**
	 * Angular size, in radians, at which parts of a thing separate.
	 *
	 * <p>A tenth of a radian is about six degrees — a fifth of a typical vertical field of view, so
	 * the thing is a noticeable object on screen rather than a mark. Below that its parts are fewer
	 * than a handful of pixels and there is nothing to read.
	 */
	public static final double DETAIL_ANGLE = 0.10;

	/**
	 * Angular size at which a form is still a form.
	 *
	 * <p>A hundredth of a radian is roughly half a degree — the moon. Recognisable as a shape and not
	 * as a point, which is exactly the line the design draws between its middle and far bands.
	 */
	public static final double SHAPE_ANGLE = 0.01;

	/**
	 * Brightness per square block at which a light stops standing out from a night sky.
	 *
	 * <p>Chosen so that the reach works out to {@code 100 × √brightness}, which makes brightness
	 * something a designer can pick by the range they want: 0.25 for a campfire seen at fifty blocks,
	 * 100 for a running light seen at a kilometre, 6700 for a strike seen to the edge of the drawn
	 * world.
	 */
	public static final double GLOW_THRESHOLD = 1.0e-4;

	/**
	 * How much harder a light has to work in daylight.
	 *
	 * <p>A hundred times the brightness for the same reach, which is ten times less reach — the
	 * factor that makes an event legible at night and unremarkable at noon without a second set of
	 * numbers for the day case.
	 */
	public static final double DAY_GLOW_PENALTY = 100.0;

	/**
	 * Past this nothing is observable, whatever its brightness.
	 *
	 * <p>Not physics but honesty about the renderer: {@code HorizonProjection.MAX_TRUE_RADIUS} is the
	 * distance past which the horizon stops being sampled, so an event further out has nothing to
	 * appear on. Kept as a number of its own rather than imported, because this class must not depend
	 * on client rendering — but the two are meant to agree, and if that constant moves this one
	 * follows.
	 */
	public static final double MAX_OBSERVED_DISTANCE = 8_192.0;

	/** Distance within which parts of a thing this size can be read. */
	public static double detailReach(double size) {
		return Math.max(0, size) / DETAIL_ANGLE;
	}

	/** Distance within which a thing this size is still a shape. */
	public static double shapeReach(double size) {
		return Math.max(0, size) / SHAPE_ANGLE;
	}

	/** Distance within which a light this bright is still visible. Zero for something that does not emit. */
	public static double glowReach(double brightness, boolean daylight) {
		if (brightness <= 0) return 0;
		return Math.sqrt(brightness / (GLOW_THRESHOLD * (daylight ? DAY_GLOW_PENALTY : 1.0)));
	}

	/**
	 * The most that can be made out at this distance.
	 *
	 * <p>Tried finest first, so that where two reaches overlap the more informative one wins: seeing a
	 * shape means the glow is visible too, and saying so would tell the caller less.
	 */
	public static Band band(double distance, double size, double brightness, boolean daylight) {
		if (!(distance >= 0) || distance > MAX_OBSERVED_DISTANCE) return Band.NONE;
		if (distance <= detailReach(size)) return Band.DETAIL;
		if (distance <= shapeReach(size)) return Band.SHAPE;
		if (distance <= glowReach(brightness, daylight)) return Band.GLOW;
		return Band.NONE;
	}
}
