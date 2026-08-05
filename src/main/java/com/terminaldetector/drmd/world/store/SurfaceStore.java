package com.terminaldetector.drmd.world.store;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The observed world, at every level of detail: what has been seen, where, and what colour it was.
 *
 * <p>Sits between a {@link SectionStorage} and everything that wants to know what the ground looks
 * like beyond the chunks it holds. Sections are loaded on demand, kept while they are being used and
 * written back when they are evicted or the world saves.
 *
 * <p>Coarser levels are <em>rebuilt</em> from their children rather than accumulated into. Folding
 * each observation upward as it arrives would be cheaper by an order, and wrong the moment the world
 * gets shorter — a mountain dug away, a tower shot down — because a running maximum has no way to
 * come back down. Rebuilding a parent from four children whenever they change costs a pass over
 * 4096 cells on a budget, and survives Stage 10 turning terrain into rubble.
 *
 * <p>The rebuild climbs one level per pass: a section that has just been rebuilt marks its own
 * parent, and so on up. That ordering is not decoration — see the note on {@link #set}.
 */
public final class SurfaceStore {
	/** Sections kept resident before the coldest are written back and dropped. */
	private static final int RESIDENT_CAP = 512;

	private final SectionStorage storage;
	private final Map<Long, SurfaceSection> live = new ConcurrentHashMap<>();
	private final Deque<Long> residency = new ArrayDeque<>();
	private final Set<Long> dirtyParents = ConcurrentHashMap.newKeySet();

	public SurfaceStore(SectionStorage storage) {
		this.storage = storage;
	}

	/** The section for a key, loaded or created. */
	public SurfaceSection section(long key) {
		SurfaceSection existing = live.get(key);
		if (existing != null) return existing;
		SurfaceSection loaded = load(key);
		if (loaded == null) loaded = new SurfaceSection();
		SurfaceSection raced = live.putIfAbsent(key, loaded);
		if (raced != null) return raced;
		synchronized (residency) {
			residency.addLast(key);
		}
		evictIfCrowded();
		return loaded;
	}

	/** The section for a key only if something is known there — never creates one. */
	public SurfaceSection peek(long key) {
		SurfaceSection existing = live.get(key);
		if (existing != null) return existing;
		SurfaceSection loaded = load(key);
		if (loaded == null) return null;
		live.putIfAbsent(key, loaded);
		synchronized (residency) {
			residency.addLast(key);
		}
		return loaded;
	}

	private SurfaceSection load(long key) {
		byte[] bytes = storage.read(key);
		return bytes == null ? null : SurfaceSection.fromBytes(bytes);
	}

	/**
	 * Record what is on top at a world position, replacing what was there.
	 *
	 * <p>Only level 0 is written. The levels above it are marked for rebuild and done in
	 * {@link #rebuildDirty}, on a budget, off the path of whatever is ingesting.
	 */
	public void set(int blockX, int blockZ, int worldHeight, int rgb) {
		long key = SectionKey.of(0, SectionKey.sectionOf(blockX, 0), SectionKey.sectionOf(blockZ, 0));
		section(key).set(
				SectionKey.cellInSection(blockX, 0), SectionKey.cellInSection(blockZ, 0),
				worldHeight, rgb);
		// Only the immediate parent. Rebuilding it marks *its* parent, so the change climbs the
		// levels one pass at a time. Marking the whole chain at once looks equivalent and is not:
		// the dirty set has no order, so level 2 could be rebuilt before level 1 exists, find no
		// children, quietly build nothing — and it is out of the set by then, so nothing ever
		// fixes it. That leaves the coarse rings of the horizon permanently empty.
		markParent(key);
	}

	private void markParent(long key) {
		long parent = SectionKey.parent(key);
		if (parent != key) dirtyParents.add(parent);
	}

	/** Top height at a position and level, or {@link SurfaceSection#NO_HEIGHT} if never seen. */
	public short heightAt(int blockX, int blockZ, int level) {
		SurfaceSection s = peek(SectionKey.of(level,
				SectionKey.sectionOf(blockX, level), SectionKey.sectionOf(blockZ, level)));
		if (s == null) return SurfaceSection.NO_HEIGHT;
		return s.height(SectionKey.cellInSection(blockX, level), SectionKey.cellInSection(blockZ, level));
	}

	/** Colour at a position and level. Only meaningful where {@link #heightAt} is known. */
	public int colourAt(int blockX, int blockZ, int level) {
		SurfaceSection s = peek(SectionKey.of(level,
				SectionKey.sectionOf(blockX, level), SectionKey.sectionOf(blockZ, level)));
		if (s == null) return 0;
		return s.colour(SectionKey.cellInSection(blockX, level), SectionKey.cellInSection(blockZ, level));
	}

	/**
	 * Rebuild up to {@code budget} coarse sections whose children have changed.
	 *
	 * <p>Fresh section, four children folded in — so a parent can get shorter as well as taller.
	 */
	public int rebuildDirty(int budget) {
		int done = 0;
		var it = dirtyParents.iterator();
		while (it.hasNext() && done < budget) {
			long key = it.next();
			it.remove();
			rebuild(key);
			done++;
		}
		return done;
	}

	private void rebuild(long key) {
		int level = SectionKey.level(key);
		if (level <= 0) return;
		SurfaceSection parent = new SurfaceSection();
		boolean any = false;
		for (int qx = 0; qx < 2; qx++) {
			for (int qz = 0; qz < 2; qz++) {
				long childKey = SectionKey.of(level - 1,
						SectionKey.sectionX(key) * 2 + qx, SectionKey.sectionZ(key) * 2 + qz);
				SurfaceSection child = peek(childKey);
				if (child == null) continue;
				parent.mipFrom(child, qx, qz);
				any = true;
			}
		}
		if (!any) return;
		live.put(key, parent);
		synchronized (residency) {
			residency.addLast(key);
		}
		store(key, parent);
		markParent(key);
	}

	private void evictIfCrowded() {
		while (live.size() > RESIDENT_CAP) {
			Long oldest;
			synchronized (residency) {
				oldest = residency.pollFirst();
			}
			if (oldest == null) return;
			SurfaceSection section = live.remove(oldest);
			if (section != null && section.isDirty()) store(oldest, section);
		}
	}

	private void store(long key, SurfaceSection section) {
		storage.write(key, section.toBytes());
		section.clean();
	}

	/**
	 * Install a section built elsewhere.
	 *
	 * <p>What the client does with everything the server sends it: the section is already whole, so
	 * there is nothing to merge and nothing to rebuild from — it simply replaces what was there.
	 */
	public void put(long key, SurfaceSection section) {
		live.put(key, section);
		synchronized (residency) {
			residency.addLast(key);
		}
		store(key, section);
		evictIfCrowded();
	}

	/** Drop everything, resident and stored. */
	public void reset() {
		live.clear();
		dirtyParents.clear();
		synchronized (residency) {
			residency.clear();
		}
	}

	/** Write every dirty section back and push the backend. */
	public void flush() {
		for (Map.Entry<Long, SurfaceSection> entry : live.entrySet()) {
			if (entry.getValue().isDirty()) store(entry.getKey(), entry.getValue());
		}
		storage.flush();
	}

	public void close() {
		flush();
		storage.close();
		live.clear();
		dirtyParents.clear();
		synchronized (residency) {
			residency.clear();
		}
	}

	public int residentCount() {
		return live.size();
	}

	public int dirtyParentCount() {
		return dirtyParents.size();
	}

	/** Keys of everything resident and known — what a streamer would consider sending. */
	public Set<Long> residentKeys() {
		return new HashSet<>(live.keySet());
	}
}
