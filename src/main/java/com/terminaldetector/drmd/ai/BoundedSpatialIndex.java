package com.terminaldetector.drmd.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable-position spatial hash with an explicit upper bound on work per query.
 *
 * <p>A grid alone does not make a dense flock scalable: a thousand agents in one bucket still
 * produce a million pair checks. This index therefore has two modes. If all intersecting buckets
 * fit inside the candidate budget, the query is exact. If they do not, it takes a deterministic
 * rotating sample from the query's own bucket and distributes the remaining budget across nearby
 * buckets. The returned hits are the nearest members of that bounded sample.</p>
 */
public final class BoundedSpatialIndex<T> {
	public record Point(double x, double y, double z) {
		public Point {
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("point must be finite");
			}
		}

		public double squaredDistanceTo(Point other) {
			double dx = x - other.x;
			double dy = y - other.y;
			double dz = z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	public record Hit<T>(T value, Point position, double squaredDistance) {}

	public record Query<T>(List<Hit<T>> hits, int examinedCandidates, int occupiedBuckets) {
		public Query {
			hits = List.copyOf(hits);
		}
	}

	private static final class Cell {
		private int x, y, z;

		Cell(int x, int y, int z) {
			set(x, y, z);
		}

		void set(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public boolean equals(Object value) {
			return value instanceof Cell other && x == other.x && y == other.y && z == other.z;
		}

		@Override
		public int hashCode() {
			return (x * 31 + y) * 31 + z;
		}
	}
	private record Entry<T>(T value, Point position, long sequence) {}
	private record Bucket<T>(Cell cell, List<Entry<T>> entries) {}
	private static final class CellDistance<T> {
		Bucket<T> bucket;
		boolean origin;
		double squaredDistance;
	}
	private static final class Candidate<T> {
		Entry<T> entry;
		double squaredDistance;
	}
	private static final Comparator<CellDistance<?>> CELL_ORDER = Comparator
			.comparingInt((CellDistance<?> value) -> value.origin ? 0 : 1)
			.thenComparingDouble(value -> value.squaredDistance)
			.thenComparingInt(value -> value.bucket.cell.x)
			.thenComparingInt(value -> value.bucket.cell.y)
			.thenComparingInt(value -> value.bucket.cell.z);
	private static final Comparator<Candidate<?>> CANDIDATE_ORDER = Comparator
			.comparingDouble((Candidate<?> value) -> value.squaredDistance)
			.thenComparingLong(value -> value.entry.sequence);

	private static final int MAX_RETAINED_ROWS = 128;
	private static final ThreadLocal<QueryScratch<?>> QUERY_SCRATCH = ThreadLocal.withInitial(QueryScratch::new);

	private static final class QueryScratch<T> {
		private CellDistance<T>[] occupied;
		private Candidate<T>[] candidates;
		private int occupiedCount, candidateCount;
		private boolean inUse;

		@SuppressWarnings("unchecked")
		QueryScratch() {
			occupied = (CellDistance<T>[]) new CellDistance<?>[8];
			candidates = (Candidate<T>[]) new Candidate<?>[8];
		}

		void cell(Bucket<T> bucket, boolean origin, double distance) {
			if (occupiedCount == occupied.length) occupied = Arrays.copyOf(occupied, occupied.length * 2);
			var row = occupied[occupiedCount];
			if (row == null) occupied[occupiedCount] = row = new CellDistance<>();
			row.bucket = bucket;
			row.origin = origin;
			row.squaredDistance = distance;
			occupiedCount++;
		}

		void candidate(Entry<T> entry, double distance) {
			if (candidateCount == candidates.length) candidates = Arrays.copyOf(candidates, candidates.length * 2);
			var row = candidates[candidateCount];
			if (row == null) candidates[candidateCount] = row = new Candidate<>();
			row.entry = entry;
			row.squaredDistance = distance;
			candidateCount++;
		}

		void release() {
			// Per-thread storage must not retain a large query or a previous world snapshot.
			if (occupied.length > MAX_RETAINED_ROWS) occupied = Arrays.copyOf(occupied, MAX_RETAINED_ROWS);
			if (candidates.length > MAX_RETAINED_ROWS) candidates = Arrays.copyOf(candidates, MAX_RETAINED_ROWS);
			for (int i = 0; i < Math.min(occupiedCount, occupied.length); i++) occupied[i].bucket = null;
			for (int i = 0; i < Math.min(candidateCount, candidates.length); i++) candidates[i].entry = null;
			occupiedCount = 0;
			candidateCount = 0;
			inUse = false;
		}
	}

	private final double cellSize;
	private final Map<Cell, Bucket<T>> buckets = new HashMap<>();
	private int size;

	public BoundedSpatialIndex(double cellSize) {
		if (!Double.isFinite(cellSize) || cellSize <= 0) {
			throw new IllegalArgumentException("cellSize must be finite and positive");
		}
		this.cellSize = cellSize;
	}

	public void add(T value, double x, double y, double z) {
		add(value, new Point(x, y, z));
	}

	public void add(T value, Point position) {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(position, "position");
		Cell cell = cell(position);
		buckets.computeIfAbsent(cell, key -> new Bucket<>(key, new ArrayList<>()))
				.entries.add(new Entry<>(value, position, size++));
	}

	public int size() { return size; }
	public int bucketCount() { return buckets.size(); }
	public double cellSize() { return cellSize; }

	/**
	 * Find at most {@code maxResults} values inside the sphere while examining no more than
	 * {@code maxCandidates} bucket entries. The seed rotates dense-bucket sampling between callers
	 * or ticks without sacrificing deterministic replay for the same snapshot and seed.
	 */
	public Query<T> nearest(double x, double y, double z, double radius, int maxResults,
			int maxCandidates, long seed, Predicate<? super T> filter) {
		return nearest(new Point(x, y, z), radius, maxResults, maxCandidates, seed, filter);
	}

	public Query<T> nearest(Point center, double radius, int maxResults, int maxCandidates,
			long seed, Predicate<? super T> filter) {
		Objects.requireNonNull(center, "center");
		if (!Double.isFinite(radius) || radius <= 0) {
			throw new IllegalArgumentException("radius must be finite and positive");
		}
		if (maxResults <= 0 || maxCandidates < maxResults) {
			throw new IllegalArgumentException("candidate budget must cover at least maxResults");
		}
		Objects.requireNonNull(filter, "filter");

		// Active scratch rows belong to this query. A filter may query this or another index.
		// A released scratch retains no values, so its rows can be reused for any index type.
		@SuppressWarnings("unchecked")
		var scratch = (QueryScratch<T>) QUERY_SCRATCH.get();
		if (scratch.inUse) return query(center, radius, maxResults, maxCandidates, seed, filter, new QueryScratch<>());
		scratch.inUse = true;
		try {
			return query(center, radius, maxResults, maxCandidates, seed, filter, scratch);
		} finally {
			scratch.release();
		}
	}

	private Query<T> query(Point center, double radius, int maxResults, int maxCandidates,
			long seed, Predicate<? super T> filter, QueryScratch<T> scratch) {
		double radiusSquared = radius * radius;
		Cell origin = cell(center);
		int reach = (int) Math.ceil(radius / cellSize);
		int minX = origin.x - reach, maxX = origin.x + reach;
		int minY = origin.y - reach, maxY = origin.y + reach;
		int minZ = origin.z - reach, maxZ = origin.z + reach;
		long available = 0;
		double span = 2.0 * reach + 1.0;
		// Wide queries in a sparse index can visit its stored buckets directly. Candidate
		// ordering is determined by the same sort below, independent of the hash iteration order.
		if (buckets.size() * 2.0 <= span * span * span) {
			for (Bucket<T> bucket : buckets.values()) {
				Cell cell = bucket.cell;
				if (cell.x < minX || cell.x > maxX || cell.y < minY || cell.y > maxY || cell.z < minZ || cell.z > maxZ) continue;
				available += include(bucket, center, origin, radiusSquared, scratch);
			}
		} else {
			// This mutable key only probes the map. Keys owned by buckets are never modified.
			Cell probe = new Cell(0, 0, 0);
			for (int x = minX; x <= maxX; x++) {
				for (int y = minY; y <= maxY; y++) {
					for (int z = minZ; z <= maxZ; z++) {
						probe.set(x, y, z);
						Bucket<T> bucket = buckets.get(probe);
						if (bucket != null) available += include(bucket, center, origin, radiusSquared, scratch);
					}
				}
			}
		}
		var occupied = scratch.occupied;
		int occupiedCount = scratch.occupiedCount;
		Arrays.sort(occupied, 0, occupiedCount, CELL_ORDER);

		boolean exact = available <= maxCandidates;
		int remaining = maxCandidates;
		int examined = 0;
		for (int cellIndex = 0; cellIndex < occupiedCount && remaining > 0; cellIndex++) {
			var cell = occupied[cellIndex];
			List<Entry<T>> bucket = cell.bucket.entries;
			int cellsLeft = occupiedCount - cellIndex;
			int quota;
			if (exact) {
				quota = bucket.size();
			} else {
				quota = (remaining + cellsLeft - 1) / cellsLeft;
				if (cell.origin) {
					int localShare = Math.min(maxResults * 2, maxCandidates * 2 / 3);
					quota = Math.max(quota, localShare);
				}
			}
			quota = Math.min(bucket.size(), Math.min(quota, remaining));
			int start = exact ? 0 : sampleStart(seed, cell.bucket.cell, bucket.size());
			for (int index = 0; index < quota; index++) {
				int sampledIndex = exact ? index : (int) Math.floorMod((long) start
						+ (long) index * bucket.size() / quota, bucket.size());
				Entry<T> entry = bucket.get(sampledIndex);
				examined++;
				remaining--;
				if (!filter.test(entry.value)) continue;
				double distance = center.squaredDistanceTo(entry.position);
				if (distance <= radiusSquared) scratch.candidate(entry, distance);
			}
		}
		var candidates = scratch.candidates;
		int candidateCount = scratch.candidateCount;
		Arrays.sort(candidates, 0, candidateCount, CANDIDATE_ORDER);
		int resultCount = Math.min(maxResults, candidateCount);
		List<Hit<T>> hits = new ArrayList<>(resultCount);
		for (int index = 0; index < resultCount; index++) {
			Candidate<T> candidate = candidates[index];
			hits.add(new Hit<>(candidate.entry.value, candidate.entry.position, candidate.squaredDistance));
		}
		return new Query<>(hits, examined, occupiedCount);
	}

	private int include(Bucket<T> bucket, Point center, Cell origin, double radiusSquared, QueryScratch<T> scratch) {
		double distance = squaredDistanceToCell(center, bucket.cell);
		if (distance > radiusSquared) return 0;
		scratch.cell(bucket, bucket.cell.equals(origin), distance);
		return bucket.entries.size();
	}

	private Cell cell(Point position) {
		return new Cell((int) Math.floor(position.x / cellSize),
				(int) Math.floor(position.y / cellSize),
				(int) Math.floor(position.z / cellSize));
	}

	private double squaredDistanceToCell(Point point, Cell cell) {
		double minX = cell.x * cellSize;
		double minY = cell.y * cellSize;
		double minZ = cell.z * cellSize;
		double dx = axisDistance(point.x, minX, minX + cellSize);
		double dy = axisDistance(point.y, minY, minY + cellSize);
		double dz = axisDistance(point.z, minZ, minZ + cellSize);
		return dx * dx + dy * dy + dz * dz;
	}

	private static double axisDistance(double value, double min, double max) {
		if (value < min) return min - value;
		return value > max ? value - max : 0;
	}

	private static int sampleStart(long seed, Cell cell, int size) {
		long mixed = seed ^ (cell.x * 0x9E3779B97F4A7C15L)
				^ (cell.y * 0xC2B2AE3D27D4EB4FL) ^ (cell.z * 0x165667B19E3779F9L);
		mixed ^= mixed >>> 30;
		mixed *= 0xBF58476D1CE4E5B9L;
		mixed ^= mixed >>> 27;
		mixed *= 0x94D049BB133111EBL;
		mixed ^= mixed >>> 31;
		return (int) Math.floorMod(mixed, (long) size);
	}

}
