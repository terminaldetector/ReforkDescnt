package com.terminaldetector.drmd.aeris;

/**
 * ÆRis/Mirai — step 6 of the brief's work order ("a minimal XYZ density generator").
 *
 * <p>Deliberately has zero Minecraft imports. This is the whole experiment: a real
 * {@code density = F(x, y, z)} evaluated at a genuine point in space, not a heightmap sampled at
 * (x, z) and stretched into a column. Sign convention matches vanilla's own {@code DensityFunction}:
 * {@code >= 0} is solid, {@code < 0} is air. Being dependency-free means {@link AerisDensityTest} calls
 * this exact production code directly instead of mirroring a copy of the formula, unlike every other
 * pure-logic test in this project (those mirror because the real code depends on classes only the
 * Minecraft/Fabric classpath provides).
 *
 * <p>Two independent fields, unioned ({@code max}):
 * <ul>
 *   <li>{@link #sphereDensity} — a 3D grid of {@link #CELL}-sized cells, each independently deciding (by
 *   hash) whether it holds one sphere, clamped to sit fully inside its own cell. Because the vertical
 *   axis of the cell grid is hashed independently per cell, a fixed (x, z) column generically passes
 *   through several unrelated cells going up — i.e. several independent spheres, not one heightmap
 *   surface. This is the direct, hands-on test of doc 01/02's central question: can one X/Z column hold
 *   more than one surface along Y.
 *   <li>{@link #foamDensity} — bounded, blobby regions (same cell grid, disjoint hash range) filled with
 *   thresholded 3D value noise, giving interior cavities and connecting tunnels inside a closed volume,
 *   rather than an unbounded solid mass.
 * </ul>
 *
 * <p>The seed is a fixed constant, not the world seed — {@code ChunkGenerator.populateNoise}'s
 * available parameters (per this session's research, not a locally-compiled and verified fact) don't
 * hand the world seed straight through, and threading it in from elsewhere is scope this first
 * experiment doesn't need: the question being tested is "does F(x,y,z) worldgen work in MC's real
 * pipeline at all," not "is it seeded correctly per world." Making the seed a real codec field is a
 * natural, small follow-up once the core mechanism is confirmed.
 */
public final class AerisDensity {
	private static final long SEED = 20260811L;
	private static final double CELL = 48.0;
	private static final double MIN_RADIUS = 6.0;
	private static final double MAX_RADIUS = 14.0;
	/** Keeps a clamped sphere fully inside its home cell — never need to check neighbour cells. */
	private static final double MAX_OFFSET = CELL * 0.5 - MAX_RADIUS - 2.0;

	private AerisDensity() {}

	/** {@code >= 0} is solid, {@code < 0} is air — same convention as vanilla's DensityFunction. */
	public static double density(double x, double y, double z) {
		return Math.max(sphereDensity(x, y, z), foamDensity(x, y, z));
	}

	public static boolean isSolid(double x, double y, double z) {
		return density(x, y, z) >= 0.0;
	}

	/**
	 * The sphere field alone — exposed separately (not just folded into {@link #density}) so a renderer
	 * or generator can tell which of the two unioned fields actually produced a given solid voxel, e.g.
	 * to colour spheres differently from foam. Same {@code >= 0} solid convention as {@link #density}.
	 */
	public static double sphereDensity(double x, double y, double z) {
		long cx = cellIndex(x), cy = cellIndex(y), cz = cellIndex(z);
		long h = hash(cx, cy, cz, SEED ^ 0x53504845524553L);
		if ((h & 0xFF) >= 96) return -1e9; // ~37.5% of cells contain a sphere
		double ox = (frac01(h, 1) * 2.0 - 1.0) * MAX_OFFSET;
		double oy = (frac01(h, 2) * 2.0 - 1.0) * MAX_OFFSET;
		double oz = (frac01(h, 3) * 2.0 - 1.0) * MAX_OFFSET;
		double radius = MIN_RADIUS + frac01(h, 4) * (MAX_RADIUS - MIN_RADIUS);
		double centerX = cellCenter(cx) + ox;
		double centerY = cellCenter(cy) + oy;
		double centerZ = cellCenter(cz) + oz;
		double dx = x - centerX, dy = y - centerY, dz = z - centerZ;
		return radius - Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The foam field alone — see {@link #sphereDensity} for why this is exposed separately. */
	public static double foamDensity(double x, double y, double z) {
		long cx = cellIndex(x), cy = cellIndex(y), cz = cellIndex(z);
		long h = hash(cx, cy, cz, SEED ^ 0x464F414D4652454DL);
		if ((h & 0xFF) < 160 || (h & 0xFF) >= 224) return -1e9; // a disjoint ~25% of cells, no fighting with spheres
		double lx = x - cellCenter(cx), ly = y - cellCenter(cy), lz = z - cellCenter(cz);
		double envelopeRadius = CELL * 0.42;
		double toCenter = Math.sqrt(lx * lx + ly * ly + lz * lz);
		double envelope = envelopeRadius - toCenter;
		if (envelope < -6.0) return -1e9; // cheap early-out well outside the blob
		double n = valueNoise3(x * 0.18, y * 0.18, z * 0.18);
		// Solid only where BOTH "inside the rounded envelope" and "noise says solid" — porous texture
		// (cavities, connecting tunnels) bounded inside a closed volume, never an unbounded solid mass.
		return Math.min(envelope, n * 10.0);
	}

	private static long cellIndex(double v) {
		return Math.floorDiv((long) Math.floor(v), (long) CELL);
	}

	private static double cellCenter(long cellIndex) {
		return cellIndex * CELL + CELL * 0.5;
	}

	private static double valueNoise3(double x, double y, double z) {
		long x0 = (long) Math.floor(x), y0 = (long) Math.floor(y), z0 = (long) Math.floor(z);
		double fx = x - x0, fy = y - y0, fz = z - z0;
		double sx = smooth(fx), sy = smooth(fy), sz = smooth(fz);
		double c000 = lattice(x0, y0, z0), c100 = lattice(x0 + 1, y0, z0);
		double c010 = lattice(x0, y0 + 1, z0), c110 = lattice(x0 + 1, y0 + 1, z0);
		double c001 = lattice(x0, y0, z0 + 1), c101 = lattice(x0 + 1, y0, z0 + 1);
		double c011 = lattice(x0, y0 + 1, z0 + 1), c111 = lattice(x0 + 1, y0 + 1, z0 + 1);
		double x00 = lerp(c000, c100, sx), x10 = lerp(c010, c110, sx);
		double x01 = lerp(c001, c101, sx), x11 = lerp(c011, c111, sx);
		double y0v = lerp(x00, x10, sy), y1v = lerp(x01, x11, sy);
		return lerp(y0v, y1v, sz); // ~[-1, 1]
	}

	private static double lattice(long x, long y, long z) {
		return frac01(hash(x, y, z, SEED), 0) * 2.0 - 1.0;
	}

	private static double smooth(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10); // Perlin's quintic smoothstep
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** Deterministic, not cryptographic — only needs to be well-distributed enough to look varied. */
	private static long hash(long x, long y, long z, long salt) {
		long h = salt;
		h = (h ^ x) * 0x9E3779B97F4A7C15L;
		h ^= (h >>> 31);
		h = (h ^ y) * 0xBF58476D1CE4E5B9L;
		h ^= (h >>> 31);
		h = (h ^ z) * 0x94D049BB133111EBL;
		h ^= (h >>> 31);
		return h;
	}

	private static double frac01(long h, int salt) {
		long v = hash(h, salt, salt * 0x2545F4914F6CDD1DL, 0x1234567L);
		return ((v >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) (1L << 53);
	}
}
