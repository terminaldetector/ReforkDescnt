package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every event in the world, and the decision of which ones currently have to exist as entities.
 *
 * <p><b>What this is for.</b> {@link D6WorldEvent} answers what a single event looks like to a single
 * observer. Something has to hold all of them and answer the question that actually drives the game
 * loop: since last tick, which events crossed into needing real entities, and which stopped? That
 * question is edge-triggered — you spawn on the way in and despawn on the way out — so a registry
 * that only reported current state would make every caller keep its own copy of the previous one.
 *
 * <p><b>The trap this exists to avoid.</b> A boundary that is a single distance thrashes. An observer
 * standing near the edge of the realisation range crosses it back and forth as they walk, and each
 * crossing spawns or despawns a group of drones — several times a second, at the worst possible
 * moment, when the player is looking straight at it. So the distance to release is further out than
 * the distance to realise: {@link #HYSTERESIS} apart. Two thresholds instead of one, which is the
 * standard answer and worth stating because the bug it prevents is invisible until it is on screen.
 *
 * <p><b>Cost.</b> One squared-distance comparison per event per observer, against a reach computed
 * from the event alone. No entity, no chunk, no world access. Ten thousand events and four players is
 * forty thousand compares, which is nothing — and events nobody is near never get further than that
 * compare, which is the whole design.
 *
 * <p>Not thread-safe, and deliberately so: this is server-tick state with one owner. Pure of
 * Minecraft, so the whole of it is testable as arithmetic.
 */
public final class D6EventRegistry {

	/**
	 * How much further away an event is released than it is realised, as a fraction.
	 *
	 * <p>Fifteen percent of the shape reach — 75 blocks on a five-block craft. Wide enough that
	 * ordinary walking and flying cannot cross both thresholds repeatedly, narrow enough that the
	 * entities are not kept alive far past where they are worth drawing.
	 */
	public static final double HYSTERESIS = 0.15;

	/** What changed in one {@link #update}. Both lists are in insertion order and may be empty. */
	public record Change(List<Long> realised, List<Long> released) {
		public boolean isEmpty() {
			return realised.isEmpty() && released.isEmpty();
		}
	}

	private static final Change NOTHING = new Change(List.of(), List.of());

	private final Map<Long, D6WorldEvent> events = new LinkedHashMap<>();
	private final Set<Long> realised = new LinkedHashSet<>();

	public void add(D6WorldEvent event) {
		events.put(event.id(), event);
	}

	public D6WorldEvent get(long id) {
		return events.get(id);
	}

	public int size() {
		return events.size();
	}

	public Collection<D6WorldEvent> events() {
		return Collections.unmodifiableCollection(events.values());
	}

	public boolean isRealised(long id) {
		return realised.contains(id);
	}

	/** Ids of every event currently realised, in the order they were realised. */
	public Set<Long> realisedIds() {
		return Collections.unmodifiableSet(realised);
	}

	/**
	 * Drop an event outright.
	 *
	 * <p>Also drops it from the realised set, so the registry stays consistent — but that means a
	 * caller removing a realised event gets no {@code released} notice for it and has to despawn what
	 * it spawned. The ordinary path is {@link #purgeFinished} after {@link #update}, where the release
	 * has already been reported.
	 *
	 * @return whether there was such an event
	 */
	public boolean remove(long id) {
		realised.remove(id);
		return events.remove(id) != null;
	}

	/**
	 * The best any of the observers can make out of this event.
	 *
	 * <p>Independent of what has been realised: this answers what is visible, {@link #update} decides
	 * what has to exist.
	 */
	public D6EventVisibility.Band bandFor(long id, List<Vec3> observers, long tick, boolean daylight) {
		D6WorldEvent event = events.get(id);
		if (event == null) return D6EventVisibility.Band.NONE;
		D6EventVisibility.Band best = D6EventVisibility.Band.NONE;
		for (Vec3 observer : observers) {
			D6EventVisibility.Band band = event.bandFor(observer, tick, daylight);
			if (band.atLeast(best)) best = band;
		}
		return best;
	}

	/**
	 * Decide what has to exist now, and report only what changed.
	 *
	 * <p>An event can be realised only while it is actually running — a scheduled one that has not
	 * begun and a finished one both release, the second of which is how the end of an event reaches
	 * the caller at all.
	 *
	 * <p>Call this before {@link #purgeFinished}, so that a finishing event is reported as released on
	 * the tick it ends rather than disappearing from under its entities.
	 *
	 * @param observers where the players are, in world blocks. An empty list releases everything,
	 *                  which is correct: with nobody to see it, nothing has to exist.
	 * @param daylight  whether the sky is bright, which changes only how far a glow carries — the
	 *                  shape reach that decides realisation does not depend on it, so this is passed
	 *                  through for {@link #bandFor} and does not move the boundary.
	 */
	public Change update(List<Vec3> observers, long tick, boolean daylight) {
		List<Long> nowRealised = null;
		List<Long> nowReleased = null;

		for (D6WorldEvent event : events.values()) {
			long id = event.id();
			boolean was = realised.contains(id);
			boolean running = event.hasStarted(tick) && !event.isFinished(tick);
			boolean should;
			if (!running) {
				should = false;
			} else {
				double nearest = nearestDistance(event, observers, tick);
				double reach = D6EventVisibility.shapeReach(event.size());
				// Two thresholds: cross the inner one to appear, the outer one to leave.
				should = was ? nearest <= reach * (1 + HYSTERESIS) : nearest <= reach;
			}

			if (should && !was) {
				realised.add(id);
				if (nowRealised == null) nowRealised = new ArrayList<>();
				nowRealised.add(id);
			} else if (!should && was) {
				realised.remove(id);
				if (nowReleased == null) nowReleased = new ArrayList<>();
				nowReleased.add(id);
			}
		}

		if (nowRealised == null && nowReleased == null) return NOTHING;
		return new Change(
				nowRealised == null ? List.of() : Collections.unmodifiableList(nowRealised),
				nowReleased == null ? List.of() : Collections.unmodifiableList(nowReleased));
	}

	/** Infinity when nobody is watching, which fails every threshold and so releases. */
	private static double nearestDistance(D6WorldEvent event, List<Vec3> observers, long tick) {
		if (observers.isEmpty()) return Double.POSITIVE_INFINITY;
		Vec3 where = event.positionAt(tick);
		double nearestSquared = Double.POSITIVE_INFINITY;
		for (Vec3 observer : observers) {
			double squared = observer.minus(where).lengthSquared();
			if (squared < nearestSquared) nearestSquared = squared;
		}
		return Math.sqrt(nearestSquared);
	}

	/**
	 * Remove the events that are over, and hand them back.
	 *
	 * <p>The return value is the point rather than a courtesy: an event that has ended is where a
	 * consequence comes from — the burnt ground, the wreck, the settlement that is no longer there —
	 * and something has to turn one into the other. Dropping them silently would lose exactly the
	 * part of the design that says the world keeps a history.
	 */
	public List<D6WorldEvent> purgeFinished(long tick) {
		List<D6WorldEvent> finished = null;
		for (D6WorldEvent event : events.values()) {
			if (event.isFinished(tick)) {
				if (finished == null) finished = new ArrayList<>();
				finished.add(event);
			}
		}
		if (finished == null) return List.of();
		for (D6WorldEvent event : finished) {
			events.remove(event.id());
			realised.remove(event.id());
		}
		return Collections.unmodifiableList(finished);
	}
}
