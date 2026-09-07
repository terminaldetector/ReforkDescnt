package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Everything the world remembers about places things happened to.
 *
 * <p><b>The invariant, and the reason for the whole class:</b> no two entries overlap. Adding one
 * that touches existing entries folds them all into a single record, so "what happened here" has one
 * answer instead of a pile. Without that, a village struck a dozen times accumulates a dozen
 * overlapping craters, the save grows without bound, and no query can say anything useful.
 *
 * <p>Folding is transitive: a large consequence can bridge two that were previously separate, and
 * merging with the first grows it enough to reach the second. So the scan repeats until nothing more
 * overlaps. Quadratic in the worst case and bounded by {@link #MAX_ENTRIES}, on an operation that
 * happens once per finished event rather than per tick.
 *
 * <p>Pure of Minecraft, like everything else in this package.
 */
public final class D6ConsequenceMap {

	/**
	 * How many places the world remembers.
	 *
	 * <p>There has to be a limit, and admitting it is better than discovering it: a save that grows
	 * forever eventually will not load. {@code ScarMapState} caps its cells at 12,000 for the same
	 * reason. Four thousand distinct ruined places is a great deal of history, and merging means the
	 * count grows with places rather than with events.
	 */
	public static final int MAX_ENTRIES = 4096;

	private final List<D6Consequence> entries = new ArrayList<>();

	/**
	 * Record what an event left behind, folding it into anything it touches.
	 *
	 * @return the entry that ended up in the map, which is the incoming one only if nothing overlapped
	 */
	public D6Consequence add(D6Consequence incoming) {
		D6Consequence merged = incoming;
		boolean grew = true;
		while (grew) {
			grew = false;
			for (Iterator<D6Consequence> it = entries.iterator(); it.hasNext(); ) {
				D6Consequence existing = it.next();
				if (merged.overlaps(existing)) {
					merged = merged.deepenedBy(existing);
					it.remove();
					// Kept scanning rather than restarting: the rest of this pass is still worth
					// checking against the grown one, and the outer loop catches what it grew past.
					grew = true;
				}
			}
		}
		entries.add(merged);
		if (entries.size() > MAX_ENTRIES) evictLeastTelling();
		return merged;
	}

	/**
	 * Drop the entry that says the least: the mildest, and among equals the oldest.
	 *
	 * <p>Severity before age deliberately. A recent minor scuff is worth less than an old massacre,
	 * and the design's point is that the world keeps a history — so what survives should be what a
	 * history would keep.
	 */
	private void evictLeastTelling() {
		int worstIndex = 0;
		D6Consequence worst = entries.get(0);
		for (int i = 1; i < entries.size(); i++) {
			D6Consequence candidate = entries.get(i);
			boolean milder = worst.severity().atLeast(candidate.severity())
					&& !candidate.severity().atLeast(worst.severity());
			boolean sameAndOlder = candidate.severity() == worst.severity()
					&& candidate.createdTick() < worst.createdTick();
			if (milder || sameAndOlder) {
				worst = candidate;
				worstIndex = i;
			}
		}
		entries.remove(worstIndex);
	}

	/**
	 * What happened at a point, or null.
	 *
	 * <p>At most one can contain it, which is what the no-overlap invariant buys: this is the answer
	 * rather than one of several.
	 */
	public D6Consequence at(Vec3 point) {
		for (D6Consequence entry : entries) {
			if (entry.contains(point)) return entry;
		}
		return null;
	}

	/** Everything within {@code radius} of a point — what an NPC there would have heard about. */
	public List<D6Consequence> near(Vec3 point, double radius) {
		List<D6Consequence> found = new ArrayList<>();
		for (D6Consequence entry : entries) {
			double reach = radius + entry.radius();
			if (point.minus(entry.centre()).lengthSquared() <= reach * reach) found.add(entry);
		}
		return found;
	}

	/**
	 * Append without folding — for reloading records that were already folded when they were saved.
	 *
	 * <p>Going through {@link #add} would be correct and self-repairing, and at the cap it would also
	 * be several million overlap tests during world load for an answer already known. This trusts the
	 * save, which is reasonable because nothing but this class writes it.
	 */
	public void restore(D6Consequence stored) {
		entries.add(stored);
	}

	public List<D6Consequence> all() {
		return Collections.unmodifiableList(entries);
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
	}
}
