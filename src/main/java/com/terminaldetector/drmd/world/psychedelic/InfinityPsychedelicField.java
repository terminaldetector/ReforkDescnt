package com.terminaldetector.drmd.world.psychedelic;

import com.terminaldetector.drmd.world.volume.VolumeField;
import com.terminaldetector.drmd.world.volume.VolumeMath;

import java.util.SplittableRandom;

import static com.terminaldetector.drmd.world.volume.VolumeMath.box;
import static com.terminaldetector.drmd.world.volume.VolumeMath.clamp;
import static com.terminaldetector.drmd.world.volume.VolumeMath.lerp;
import static com.terminaldetector.drmd.world.volume.VolumeMath.limitDensity;
import static com.terminaldetector.drmd.world.volume.VolumeMath.maxAbs;
import static com.terminaldetector.drmd.world.volume.VolumeMath.median;
import static com.terminaldetector.drmd.world.volume.VolumeMath.repeat;
import static com.terminaldetector.drmd.world.volume.VolumeMath.smoothstep;

/**
 * The Infinity preset's continuous, seeded XYZ occupancy. Generation, near geometry and
 * far preview can sample the same field without loading adjacent cubes. Saved voxel edits
 * take precedence once a cube has been generated.
 */
public final class InfinityPsychedelicField implements VolumeField {
	public static final int SPAWN_Y = VolumeMath.SPAWN_Y;
	private static final double CUBE_EDGE = 624;
	private static final double FRACTAL_EDGE = 1264;
	private static final double ABSTRACT_BEGIN = 2464;
	private static final double ABSTRACT_END = 3104;
	private static final int COARSE_STEP = 8;
	private static final Formation[] FRACTAL_FORMS = Formation.values();

	private final long seed;
	private final double offsetX;
	private final double offsetY;
	private final double offsetZ;
	private final double phase;
	private final ThreadLocal<CoarseSamples> coarseSamples = ThreadLocal.withInitial(CoarseSamples::new);

	public InfinityPsychedelicField(long seed) {
		this.seed = seed;
		SplittableRandom random = new SplittableRandom(seed);
		offsetX = random.nextDouble(-120, 120);
		offsetY = random.nextDouble(-120, 120);
		offsetZ = random.nextDouble(-120, 120);
		phase = random.nextDouble(Math.TAU);
	}

	@Override
	public double density(int x, int y, int z) {
		double wx = x + 0.5;
		double wy = (double) y - SPAWN_Y + 0.5;
		double wz = z + 0.5;
		double corridor = corridorDensity(wx, wy, wz);
		if (corridor <= -VolumeMath.DENSITY_LIMIT) {
			return VolumeMath.launchDeck(-VolumeMath.DENSITY_LIMIT, x, y, z);
		}

		double distance = regionDistance(wx, wy, wz);
		double terrain;
		if (distance <= CUBE_EDGE) {
			terrain = cubeDominion(wx, wy, wz);
		} else if (distance < FRACTAL_EDGE) {
			double t = smoothstep(CUBE_EDGE, FRACTAL_EDGE, distance);
			terrain = lerp(cubeDominion(wx, wy, wz), fractalDensity(x, y, z), t);
		} else if (distance < ABSTRACT_BEGIN) {
			terrain = fractalDensity(x, y, z);
		} else if (distance < ABSTRACT_END) {
			double t = smoothstep(ABSTRACT_BEGIN, ABSTRACT_END, distance);
			terrain = lerp(fractalDensity(x, y, z), abstractExpanse(wx, wy, wz), t);
		} else {
			terrain = abstractExpanse(wx, wy, wz);
		}
		return VolumeMath.launchDeck(limitDensity(Math.min(terrain, corridor)), x, y, z);
	}

	@Override
	public Material material(int x, int y, int z) {
		return material(x, y, z, domain(x, y, z));
	}

	public Sample sample(int x, int y, int z) {
		Domain domain = domain(x, y, z);
		int variation = (int) VolumeMath.hash(seed, Math.floorDiv(x, 192), Math.floorDiv(y, 192), Math.floorDiv(z, 192));
		return new Sample(density(x, y, z), material(x, y, z, domain), domain, variation);
	}

	private Domain domain(int x, int y, int z) {
		double distance = regionDistance(x + 0.5, (double) y - SPAWN_Y + 0.5, z + 0.5);
		if (distance <= CUBE_EDGE) return Domain.CUBE_DOMINION;
		if (distance < FRACTAL_EDGE) return Domain.FRACTURE_TRANSITION;
		if (distance < ABSTRACT_BEGIN + (ABSTRACT_END - ABSTRACT_BEGIN) * 0.5) return Domain.FRACTAL_DEPTHS;
		return Domain.ABSTRACT_EXPANSE;
	}

	private Material material(int x, int y, int z, Domain domain) {
		if (VolumeMath.isLaunchDeck(x, y, z)) return VolumeMath.deckMaterial(x, y, z);
		double seam = Math.abs(Math.sin(x / 49.0 + y / 37.0 - z / 61.0 + phase));
		return switch (domain) {
			case CUBE_DOMINION -> seam < 0.19 ? Material.DEEPSLATE : Material.BASALT;
			case FRACTURE_TRANSITION -> seam < 0.28 ? Material.TUFF : Material.DEEPSLATE;
			case FRACTAL_DEPTHS -> seam < 0.11 ? Material.AMETHYST : Material.DEEPSLATE;
			case ABSTRACT_EXPANSE -> seam < 0.13 ? Material.CALCITE : Material.BASALT;
		};
	}

	private double regionDistance(double x, double y, double z) {
		double radius = maxAbs(x, y, z);
		double warped = radius + 64 * Math.sin(x / 311.0 + phase) * Math.cos(y / 257.0) * Math.sin(z / 283.0 + phase);
		if (warped <= ABSTRACT_END) return warped;
		// The initial cube is singular. Farther formations recur on a continuous macro scale.
		return lerp(1648, ABSTRACT_END, 0.5 + 0.5 * Math.cos((warped - ABSTRACT_END) / 900.0));
	}

	private double cubeDominion(double x, double y, double z) {
		double localX = repeat(x + offsetX * 0.32, 320);
		double localY = repeat(y + offsetY * 0.32, 320);
		double localZ = repeat(z + offsetZ * 0.32, 320);
		double radius = maxAbs(localX, localY, localZ);
		double chamber = Math.max(23 - Math.abs(radius - 126), 13 - Math.abs(radius - 65));
		double aperture = median(Math.abs(localX), Math.abs(localY), Math.abs(localZ)) - 28;
		double nestedChambers = Math.min(chamber, aperture);

		double giantRadius = maxAbs(x, y, z);
		double giantWalls = Math.max(19 - Math.abs(giantRadius - 228), 26 - Math.abs(giantRadius - 514));
		double giantOpenings = median(Math.abs(x), Math.abs(y), Math.abs(z)) - 57;
		return limitDensity(Math.max(nestedChambers, Math.min(giantWalls, giantOpenings)));
	}

	private static double corridorDensity(double x, double y, double z) {
		// The launch route remains navigable through every morphology. It joins a branching XYZ grid.
		double launchY = y - 10 - 18 * Math.sin(x / 291.0);
		double launchZ = z - 24 * Math.sin(x / 359.0);
		double launch = -box(x, launchY, launchZ, Double.POSITIVE_INFINITY, 37, 44);
		double dx = repeat(x, 328);
		double dy = repeat(y, 320);
		double dz = repeat(z, 328);
		double nearestBranch = Math.sqrt(Math.min(dx * dx + dy * dy,
				Math.min(dy * dy + dz * dz, dz * dz + dx * dx))) - 26;
		return Math.min(launch, nearestBranch);
	}

	private double fractalDensity(int x, int y, int z) {
		int cx = Math.floorDiv(x, COARSE_STEP);
		int cy = Math.floorDiv(y, COARSE_STEP);
		int cz = Math.floorDiv(z, COARSE_STEP);
		double tx = Math.floorMod(x, COARSE_STEP) / (double) COARSE_STEP;
		double ty = Math.floorMod(y, COARSE_STEP) / (double) COARSE_STEP;
		double tz = Math.floorMod(z, COARSE_STEP) / (double) COARSE_STEP;
		CoarseSamples cache = coarseSamples.get();
		double a = coarseAt(cache, cx, cy, cz);
		if (tx == 0.0 && ty == 0.0 && tz == 0.0) return a;
		double lowZ = lerp(lerp(a, coarseAt(cache, cx + 1, cy, cz), tx),
				lerp(coarseAt(cache, cx, cy + 1, cz), coarseAt(cache, cx + 1, cy + 1, cz), tx), ty);
		double highZ = lerp(lerp(coarseAt(cache, cx, cy, cz + 1), coarseAt(cache, cx + 1, cy, cz + 1), tx),
				lerp(coarseAt(cache, cx, cy + 1, cz + 1), coarseAt(cache, cx + 1, cy + 1, cz + 1), tx), ty);
		double coarse = lerp(lowZ, highZ, tz);
		if (Math.abs(coarse) >= 5) return coarse;
		// Refine where the interpolated field can intersect a block. Fade back to the same lattice
		// at the edge of this band so cube borders never depend on the refinement decision.
		double fine = rawFractalDensity(x + 0.5, (double) y - SPAWN_Y + 0.5, z + 0.5);
		return lerp(fine, coarse, smoothstep(1, 5, Math.abs(coarse)));
	}

	private double coarseAt(CoarseSamples cache, int x, int y, int z) {
		int slot = (int) VolumeMath.hash(0, x, y, z) & CoarseSamples.MASK;
		if (!cache.present[slot] || cache.x[slot] != x || cache.y[slot] != y || cache.z[slot] != z) {
			cache.x[slot] = x;
			cache.y[slot] = y;
			cache.z[slot] = z;
			cache.density[slot] = rawFractalDensity(x * (double) COARSE_STEP + 0.5,
					y * (double) COARSE_STEP - SPAWN_Y + 0.5, z * (double) COARSE_STEP + 0.5);
			cache.present[slot] = true;
		}
		return cache.density[slot];
	}

	private double rawFractalDensity(double x, double y, double z) {
		// Long oblique regions make shape changes readable during flight along any axis.
		double coordinate = (x + 0.63 * y - 0.79 * z + offsetX * 3
				+ 136 * Math.sin(z / 488.0 + phase)) / 512.0;
		long region = (long) Math.floor(coordinate);
		int index = Math.floorMod(region, FRACTAL_FORMS.length);
		double progress = coordinate - region;
		double first = formationDensity(FRACTAL_FORMS[index], x, y, z);
		if (progress <= 0.72) return first;
		double next = formationDensity(FRACTAL_FORMS[(index + 1) % FRACTAL_FORMS.length], x, y, z);
		return lerp(first, next, smoothstep(0.72, 1, progress));
	}

	private double formationDensity(Formation formation, double x, double y, double z) {
		double cellX = repeat(x + offsetX, 416);
		double cellY = repeat(y + offsetY, 416);
		double cellZ = repeat(z + offsetZ, 416);
		return switch (formation) {
			case MANDELBOX -> limitDensity(Math.min(171 - maxAbs(cellX, cellY, cellZ),
					mandelbox(cellX / 93, cellY / 93, cellZ / 93)));
			case MENGER -> menger(cellX, cellY, cellZ);
			case MANDELBULB -> mandelbulb(cellX / 126, cellY / 126, cellZ / 126);
			case QUATERNION_JULIA -> quaternionJulia(cellX / 119, cellY / 119, cellZ / 119,
					0.19 * Math.sin((x - z) / 357.0 + phase));
			case GYROID -> gyroid(x / 48 + phase, y / 48 + Math.sin(z / 275.0), z / 48);
		};
	}

	private static double menger(double x, double y, double z) {
		double density = 174 - maxAbs(x, y, z);
		double cellSize = 348;
		for (int detail = 0; detail < 3; detail++) {
			double hole = median(Math.abs(repeat(x, cellSize)), Math.abs(repeat(y, cellSize)), Math.abs(repeat(z, cellSize)));
			density = Math.min(density, hole - cellSize / 6.0);
			cellSize /= 3;
		}
		return limitDensity(density);
	}

	private static double mandelbox(double cx, double cy, double cz) {
		double x = 0;
		double y = 0;
		double z = 0;
		double derivative = 1;
		for (int iteration = 0; iteration < 10; iteration++) {
			x = 2 * clamp(x, -1, 1) - x;
			y = 2 * clamp(y, -1, 1) - y;
			z = 2 * clamp(z, -1, 1) - z;
			double radiusSq = x * x + y * y + z * z;
			double fold = 1.0 / clamp(radiusSq, 0.25, 1);
			double scale = 1.85 * fold;
			x = x * scale + cx;
			y = y * scale + cy;
			z = z * scale + cz;
			derivative = derivative * scale + 1;
			double radius = Math.sqrt(x * x + y * y + z * z);
			if (radius > 8) return limitDensity(-1 - 200 * radius / derivative);
		}
		return 12;
	}

	private static double mandelbulb(double cx, double cy, double cz) {
		double radiusSq = cx * cx + cy * cy + cz * cz;
		if (radiusSq <= 0.41) return 16;
		if (radiusSq > 1.75) return limitDensity(-4 - (Math.sqrt(radiusSq) - 1.32) * 108);
		double x = cx;
		double y = cy;
		double z = cz;
		double derivative = 1;
		for (int iteration = 0; iteration < 9; iteration++) {
			double radius = Math.sqrt(radiusSq);
			if (radius > 2) return limitDensity(-1 - 76 * radius * Math.log(radius) / derivative);
			double theta = 8 * Math.atan2(Math.sqrt(x * x + y * y), z);
			double phi = 8 * Math.atan2(y, x);
			double radiusFourth = radiusSq * radiusSq;
			double power = radiusFourth * radiusFourth;
			derivative = 8 * radiusFourth * radiusSq * radius * derivative + 1;
			double latitude = power * Math.sin(theta);
			x = latitude * Math.cos(phi) + cx;
			y = latitude * Math.sin(phi) + cy;
			z = power * Math.cos(theta) + cz;
			radiusSq = x * x + y * y + z * z;
		}
		return radiusSq > 4 ? -1 : 10;
	}

	private static double quaternionJulia(double x, double y, double z, double w) {
		double radiusSq = w * w + x * x + y * y + z * z;
		if (radiusSq > 2.6) return limitDensity(-4 - (Math.sqrt(radiusSq) - 1.61) * 100);
		double derivative = 1;
		for (int iteration = 0; iteration < 11; iteration++) {
			double radius = Math.sqrt(radiusSq);
			if (radius > 2) return limitDensity(-1 - 70 * radius * Math.log(radius) / derivative);
			double nextW = w * w - x * x - y * y - z * z - 0.68;
			x = 2 * w * x + 0.28;
			y = 2 * w * y + 0.13;
			z = 2 * w * z - 0.16;
			w = nextW;
			derivative = Math.max(1e-4, 2 * radius * derivative);
			radiusSq = w * w + x * x + y * y + z * z;
		}
		return radiusSq > 4 ? -1 : 10;
	}

	private static double gyroid(double x, double y, double z) {
		double value = Math.sin(x) * Math.cos(y) + Math.sin(y) * Math.cos(z) + Math.sin(z) * Math.cos(x);
		return limitDensity(11 - Math.abs(value) * 36);
	}

	private double abstractExpanse(double x, double y, double z) {
		double dx = repeat(x + offsetX, 512);
		double dy = repeat(y + offsetY, 512);
		double dz = repeat(z + offsetZ, 512);
		double edge = maxAbs(dx, dy, dz);
		double lattice = gyroid(x / 64, y / 64 + phase, z / 64) - 7;
		if (edge >= 224) return limitDensity(lattice);
		double radial = Math.sqrt(dx * dx + dz * dz);
		double angle = Math.atan2(dz, dx);
		double torus = 24 - Math.hypot(radial - 148, dy - 45 * Math.sin(3 * angle + phase));
		double diagonal = (Math.abs(dx + 0.45 * dy) + Math.abs(dz - 0.4 * dy)) * 0.62;
		double crystal = Math.min(103 - diagonal - Math.abs(dy) * 0.44, Math.abs(dy) - 18);
		double formation = limitDensity(Math.max(torus, crystal));
		return limitDensity(Math.max(lattice, lerp(formation, -VolumeMath.DENSITY_LIMIT, smoothstep(194, 224, edge))));
	}

	private enum Formation {
		MANDELBOX, MENGER, MANDELBULB, QUATERNION_JULIA, GYROID
	}

	public enum Domain {
		CUBE_DOMINION, FRACTURE_TRANSITION, FRACTAL_DEPTHS, ABSTRACT_EXPANSE
	}

	public record Sample(double density, Material material, Domain domain, int variation) {}

	/** Fixed memory per generation worker, independent of explored world size. */
	private static final class CoarseSamples {
		private static final int SIZE = 1024;
		private static final int MASK = SIZE - 1;
		private final boolean[] present = new boolean[SIZE];
		private final int[] x = new int[SIZE];
		private final int[] y = new int[SIZE];
		private final int[] z = new int[SIZE];
		private final double[] density = new double[SIZE];
	}
}
