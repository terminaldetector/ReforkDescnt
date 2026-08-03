package com.terminaldetector.drmd.world.planet;

import net.minecraft.util.math.MathHelper;

/**
 * The planet, as a function of the world seed.
 *
 * <p>What the End band needs underneath it is a picture of the ground it is above, and the ground
 * is nine hundred blocks down — past the far plane, in chunks the client will never be sent. So the
 * surface is <em>computed</em> rather than sampled: height and colour for any world position,
 * identical on both sides from the seed alone. The client is told the seed once and can then draw
 * the whole planet without a single cell of it being streamed.
 *
 * <p>That is the difference from the map this replaces. It stored a height and a tint per explored
 * cell and streamed a viewport of them at twenty hertz, and its "noise" hashed the bits of a float
 * with no interpolation — so neighbouring cells were uncorrelated and the result was static, not
 * terrain. This is ordinary value noise summed over octaves: continents at the kilometre scale,
 * ranges inside them, detail on top, all continuous.
 *
 * <p>It does not reproduce the vanilla generator. It does not need to: the pilot is looking at a
 * planet from orbit, where what reads is the shape of the coastlines and the snow line. Nearer than
 * the far plane the real chunks are drawn instead, and the renderer stops there.
 */
public final class PlanetMap {
	private PlanetMap() {}

	/** Vanilla sea level — the map's waterline, so coasts sit where the world puts them. */
	public static final int SEA_LEVEL = 63;

	/** Continent wavelength in blocks. One "landmass" is a few of these across. */
	private static final float CONTINENT_SCALE = 1536f;
	/** Mountain-range wavelength in blocks. */
	private static final float RANGE_SCALE = 384f;

	/** Surface height at a world position, in world Y. */
	public static float height(long seed, double worldX, double worldZ) {
		float cx = (float) (worldX / CONTINENT_SCALE);
		float cz = (float) (worldZ / CONTINENT_SCALE);

		// Continents: below the shoreline constant this is sea, above it the land rises.
		float continent = fbm(seed, cx, cz, 4);
		float land = (continent - 0.46f) * 2.6f;

		if (land <= 0f) {
			// Shelf then deep — a flat sea floor reads as a hole from orbit, a sloped one as ocean.
			return SEA_LEVEL - 4f + land * 26f;
		}

		float rx = (float) (worldX / RANGE_SCALE);
		float rz = (float) (worldZ / RANGE_SCALE);
		// Ridged noise for ranges: the fold puts a crease along the ridge line instead of a dome.
		float ridge = 1f - Math.abs(fbm(seed ^ 0x5EEDL, rx, rz, 4) * 2f - 1f);
		// Relief grows with distance inland, so mountains sit in continent interiors, not on beaches.
		float relief = land * (18f + ridge * ridge * 120f * Math.min(1f, land));
		return SEA_LEVEL + 2f + relief;
	}

	/** Packed RGB for a position, as seen from orbit. */
	public static int tint(long seed, double worldX, double worldZ, float height) {
		if (height < SEA_LEVEL - 14f) return 0x0B2440;      // deep ocean
		if (height < SEA_LEVEL) return 0x1B5580;             // shelf
		if (height < SEA_LEVEL + 3f) return 0xC6B78C;        // beach

		if (height > 138f) return 0xE9F0F4;                  // snow line
		if (height > 112f) return 0x6E6A63;                  // bare rock

		// Moisture decides forest against open ground, on its own wavelength so the two do not
		// simply follow the height bands.
		float moisture = fbm(seed ^ 0x1CE1L, (float) (worldX / 700.0), (float) (worldZ / 700.0), 3);
		if (moisture > 0.56f) return 0x2C5528;               // forest
		if (moisture < 0.38f) return 0x9A8B52;               // dry steppe
		return 0x4C7638;                                     // plains
	}

	/** Burnt ground where a reactor detonation was recorded. */
	public static int scarTint() {
		return 0x2E1512;
	}

	// ── noise ──────────────────────────────────────────────────────────────────────────────────

	private static float fbm(long seed, float x, float z, int octaves) {
		float sum = 0f;
		float amplitude = 1f;
		float total = 0f;
		float frequency = 1f;
		for (int i = 0; i < octaves; i++) {
			sum += value(seed + i * 1013L, x * frequency, z * frequency) * amplitude;
			total += amplitude;
			amplitude *= 0.5f;
			frequency *= 2f;
		}
		return sum / total;
	}

	/** Value noise: lattice hashes, smoothstepped between. Continuous, unlike a raw bit hash. */
	private static float value(long seed, float x, float z) {
		int x0 = MathHelper.floor(x);
		int z0 = MathHelper.floor(z);
		float fx = smooth(x - x0);
		float fz = smooth(z - z0);
		float a = lattice(seed, x0, z0);
		float b = lattice(seed, x0 + 1, z0);
		float c = lattice(seed, x0, z0 + 1);
		float d = lattice(seed, x0 + 1, z0 + 1);
		return MathHelper.lerp(fz, MathHelper.lerp(fx, a, b), MathHelper.lerp(fx, c, d));
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	private static float lattice(long seed, int x, int z) {
		long h = seed * 0x9E3779B97F4A7C15L
				+ x * 0xC2B2AE3D27D4EB4FL
				+ z * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return ((h >>> 11) & 0x1FFFFFL) / (float) 0x1FFFFF;
	}
}
