package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

import java.util.Arrays;

/**
 * Something happening in the world, whether or not anyone is there to see it.
 *
 * <p><b>The problem this solves.</b> The design asks for events that continue without the player —
 * a patrol that reaches its target, a strike that lands, a settlement that falls, all of it while the
 * player is somewhere else entirely. Minecraft simulates loaded chunks and nothing else, and this
 * project already has the measurement to prove the obvious fix is unaffordable: at simulation
 * distance 31 the server ticks about 3,969 chunks and falls behind. Keeping events alive by keeping
 * the world loaded around them is not available.
 *
 * <p><b>The answer is to make an unobserved event cost nothing at all.</b> An event here is not
 * ticked. Where it is, which phase it is in, and how far through that phase — all three are closed
 * forms in the world clock. {@link #positionAt} is a multiply and an add. {@link #phaseAt} is a walk
 * over a handful of durations. Ten thousand events nobody is near cost ten thousand records of memory
 * and no time whatsoever, because nothing runs.
 *
 * <p>The consequence is the design's requirement rather than a compromise for it: since the state at
 * any tick is computed rather than accumulated, a player may witness the beginning, the middle, the
 * end, arrive afterwards, or miss it entirely, and every one of those reads the same event correctly.
 * Nothing has to have been simulated for the answer to exist.
 *
 * <p><b>Where entities come in.</b> Real drones, real missiles, real block damage are needed exactly
 * when someone can see form and motion — {@link D6EventVisibility.Band#SHAPE} or better. Further out
 * the client can draw a light in the sky from this record alone. So the realisation radius is not a
 * tuned constant: it is the distance at which the event stops being a light and becomes a thing, and
 * {@link #needsEntities} says so directly.
 *
 * <p><b>What this deliberately does not hold.</b> No participants, no objective, no dialogue, no
 * consequences — those are content and belong to whatever authors events. This is the part that has
 * to be arithmetic to be affordable, and keeping it free of the rest is what lets it be tested as
 * arithmetic.
 *
 * <p>A class rather than a record because of the phase array: a record would give it identity
 * equality and a hash over the reference, which is a trap worth not laying.
 */
public final class D6WorldEvent {

	/** Returned by {@link #phaseAt} for a tick before the event begins. */
	public static final int NOT_STARTED = -1;

	private final long id;
	private final String type;
	private final Vec3 origin;
	private final Vec3 velocity;
	private final long startTick;
	private final int[] phaseDurations;
	private final double size;
	private final double brightness;

	/**
	 * @param origin         where it begins, in world blocks
	 * @param velocity       blocks per tick; zero for something that stays put
	 * @param phaseDurations ticks per phase, in order. The design's swarm — scout, contact, signal,
	 *                       gather, intercept, attack, destroy, withdraw — is eight numbers here and
	 *                       nothing else. Durations of zero are allowed and pass through instantly,
	 *                       which is how a phase that only marks a moment is written.
	 * @param size           the largest span of the thing, in blocks — what decides whether it reads
	 *                       as a shape
	 * @param brightness     how much light it throws; see {@link D6EventVisibility#GLOW_THRESHOLD} for
	 *                       what the numbers mean. Zero for something that does not glow.
	 */
	public D6WorldEvent(long id, String type, Vec3 origin, Vec3 velocity, long startTick,
			int[] phaseDurations, double size, double brightness) {
		if (phaseDurations == null || phaseDurations.length == 0) {
			throw new IllegalArgumentException("an event needs at least one phase");
		}
		for (int duration : phaseDurations) {
			if (duration < 0) {
				throw new IllegalArgumentException(
						"phase durations cannot be negative: " + Arrays.toString(phaseDurations));
			}
		}
		this.id = id;
		this.type = type;
		this.origin = origin;
		this.velocity = velocity;
		this.startTick = startTick;
		// Copied, so that a caller reusing its array cannot rewrite an event's history after the fact.
		this.phaseDurations = phaseDurations.clone();
		this.size = size;
		this.brightness = brightness;
	}

	public long id() {
		return id;
	}

	public String type() {
		return type;
	}

	public Vec3 origin() {
		return origin;
	}

	public long startTick() {
		return startTick;
	}

	public double size() {
		return size;
	}

	public double brightness() {
		return brightness;
	}

	public int phaseCount() {
		return phaseDurations.length;
	}

	public int phaseDuration(int phase) {
		return phaseDurations[phase];
	}

	/** Total ticks from start to finish. */
	public long duration() {
		long total = 0;
		for (int duration : phaseDurations) total += duration;
		return total;
	}

	/** The tick after which the event is over. */
	public long endTick() {
		return startTick + duration();
	}

	public boolean hasStarted(long tick) {
		return tick >= startTick;
	}

	public boolean isFinished(long tick) {
		return tick >= endTick();
	}

	/** The tick a phase begins on. */
	public long phaseStartTick(int phase) {
		long at = startTick;
		for (int i = 0; i < phase; i++) at += phaseDurations[i];
		return at;
	}

	/**
	 * Which phase the event is in.
	 *
	 * @return {@link #NOT_STARTED} before it begins, an index in {@code [0, phaseCount)} during, and
	 *         {@link #phaseCount()} once it is over — so a caller can test all three against named
	 *         values instead of asking twice.
	 */
	public int phaseAt(long tick) {
		if (tick < startTick) return NOT_STARTED;
		long into = tick - startTick;
		for (int i = 0; i < phaseDurations.length; i++) {
			// Strictly less, so a phase of length n covers n ticks and the next begins on the n-th.
			// A zero-length phase therefore never tests true, which is what makes it instantaneous.
			if (into < phaseDurations[i]) return i;
			into -= phaseDurations[i];
		}
		return phaseDurations.length;
	}

	/**
	 * How far through the current phase, from 0 to 1.
	 *
	 * <p>0 before the event and 1 after it, so a caller interpolating something does not have to
	 * special-case the ends. A zero-length phase is never current, so this never divides by zero.
	 */
	public double phaseProgress(long tick) {
		int phase = phaseAt(tick);
		if (phase == NOT_STARTED) return 0;
		if (phase == phaseDurations.length) return 1;
		long into = tick - phaseStartTick(phase);
		return (double) into / phaseDurations[phase];
	}

	/**
	 * Where it is at a given tick — a multiply and an add, which is the whole point.
	 *
	 * <p>Clamped at both ends: before it starts it is at its origin, and after it finishes it stays
	 * where it stopped rather than continuing off the map. An event that has ended is still somewhere,
	 * because its wreckage is.
	 */
	public Vec3 positionAt(long tick) {
		long elapsed = Math.max(0, Math.min(tick, endTick()) - startTick);
		return origin.plus(velocity.scaled(elapsed));
	}

	/** What an observer there and then could make out. */
	public D6EventVisibility.Band bandFor(Vec3 observer, long tick, boolean daylight) {
		double distance = observer.minus(positionAt(tick)).length();
		return D6EventVisibility.band(distance, size, brightness, daylight);
	}

	/**
	 * Whether the event has to exist as real entities for this observer.
	 *
	 * <p>True from {@code SHAPE} upward, and that is the architecture in one line: form and motion
	 * cannot be faked, a light in the sky can. Below this the event is a record and a formula, and
	 * costs nothing.
	 */
	public boolean needsEntities(Vec3 observer, long tick, boolean daylight) {
		return bandFor(observer, tick, daylight).atLeast(D6EventVisibility.Band.SHAPE);
	}

	@Override
	public String toString() {
		return "D6WorldEvent[" + id + " " + type + " at " + origin + " from " + startTick
				+ " for " + duration() + "]";
	}
}
