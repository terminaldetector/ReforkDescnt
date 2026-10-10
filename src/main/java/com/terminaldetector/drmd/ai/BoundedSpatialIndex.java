package com.terminaldetector.drmd.ai;

import java.util.ArrayList;
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

	private record Cell(int x, int y, int z) {}
	private record Entry<T>(T value, Point position, long sequence) {}
	private record CellDistance(Cell cell, double squaredDistance) {}
	private record Candidate<T>(Entry<T> entry, double squaredDistance) {}

	private final double cellSize;
	private final Map<Cell, List<Entry<T>>> buckets = new HashMap<>();
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
		buckets.computeIfAbsent(cell, ignored -> new ArrayList<>())
				.add(new Entry<>(value, position, size++));
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

		double radiusSquared = radius * radius;
		Cell origin = cell(center);
		int reach = (int) Math.ceil(radius / cellSize);
		List<CellDistance> occupied = new ArrayList<>();
		long available = 0;
		for (int x = origin.x - reach; x <= origin.x + reach; x++) {
			for (int y = origin.y - reach; y <= origin.y + reach; y++) {
				for (int z = origin.z - reach; z <= origin.z + reach; z++) {
					Cell cell = new Cell(x, y, z);
					List<Entry<T>> bucket = buckets.get(cell);
					if (bucket == null) continue;
					double distance = squaredDistanceToCell(center, cell);
					if (distance > radiusSquared) continue;
					occupied.add(new CellDistance(cell, distance));
					available += bucket.size();
				}
			}
		}
		occupied.sort(Comparator
				.comparingInt((CellDistance value) -> value.cell.equals(origin) ? 0 : 1)
				.thenComparingDouble(CellDistance::squaredDistance)
				.thenComparingInt(value -> value.cell.x)
				.thenComparingInt(value -> value.cell.y)
				.thenComparingInt(value -> value.cell.z));

		boolean exact = available <= maxCandidates;
		int remaining = maxCandidates;
		int examined = 0;
		List<Candidate<T>> candidates = new ArrayList<>(Math.min(maxCandidates, maxResults * 2));
		for (int cellIndex = 0; cellIndex < occupied.size() && remaining > 0; cellIndex++) {
			Cell cell = occupied.get(cellIndex).cell;
			List<Entry<T>> bucket = buckets.get(cell);
			int cellsLeft = occupied.size() - cellIndex;
			int quota;
			if (exact) {
				quota = bucket.size();
			} else {
				quota = (remaining + cellsLeft - 1) / cellsLeft;
				if (cell.equals(origin)) {
					int localShare = Math.min(maxResults * 2, maxCandidates * 2 / 3);
					quota = Math.max(quota, localShare);
				}
			}
			quota = Math.min(bucket.size(), Math.min(quota, remaining));
			int start = exact ? 0 : sampleStart(seed, cell, bucket.size());
			for (int index = 0; index < quota; index++) {
				int sampledIndex = exact ? index : (int) Math.floorMod((long) start
						+ (long) index * bucket.size() / quota, bucket.size());
				Entry<T> entry = bucket.get(sampledIndex);
				examined++;
				remaining--;
				if (!filter.test(entry.value)) continue;
				double distance = center.squaredDistanceTo(entry.position);
				if (distance <= radiusSquared) candidates.add(new Candidate<>(entry, distance));
			}
		}
		candidates.sort(Comparator
				.comparingDouble((Candidate<T> value) -> value.squaredDistance)
				.thenComparingLong(value -> value.entry.sequence));
		int resultCount = Math.min(maxResults, candidates.size());
		List<Hit<T>> hits = new ArrayList<>(resultCount);
		for (int index = 0; index < resultCount; index++) {
			Candidate<T> candidate = candidates.get(index);
			hits.add(new Hit<>(candidate.entry.value, candidate.entry.position, candidate.squaredDistance));
		}
		return new Query<>(hits, examined, occupied.size());
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
