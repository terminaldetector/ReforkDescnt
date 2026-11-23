package com.terminaldetector.drmd.world.volume;

import com.terminaldetector.drmd.world.profile.WorldProfile;
import com.terminaldetector.drmd.world.psychedelic.InfinityPsychedelicField;

import static com.terminaldetector.drmd.world.volume.VolumeMath.DENSITY_LIMIT;
import static com.terminaldetector.drmd.world.volume.VolumeMath.SPAWN_Y;
import static com.terminaldetector.drmd.world.volume.VolumeMath.box;
import static com.terminaldetector.drmd.world.volume.VolumeMath.hash;
import static com.terminaldetector.drmd.world.volume.VolumeMath.limitDensity;
import static com.terminaldetector.drmd.world.volume.VolumeMath.repeat;

/** Seeded occupancy sources for the other Ungravity Overworld presets. */
public final class PresetVolumeFields {
	private PresetVolumeFields() {}

	/** Cube population and the client horizon use the same profile and field definitions. */
	public static VolumeField forProfile(WorldProfile profile) {
		long seed = profile.seed();
		return switch (profile.preset()) {
			case UNGRAVITY_CONTINUUM -> continuum(seed);
			case INFINITY_PSYCHEDELIC -> new InfinityPsychedelicField(seed);
			case VERTICAL_MEGACITY -> verticalMegacity(seed);
			case ABYSS_COMPLEX -> abyssComplex(seed);
			case ORBITAL_RUINS -> orbitalRuins(seed);
			case NONE -> throw new IllegalArgumentException("A volume field requires an Ungravity preset");
		};
	}

	public static VolumeField continuum(long seed) { return new Continuum(seed); }
	public static VolumeField verticalMegacity(long seed) { return new Megacity(seed); }
	public static VolumeField abyssComplex(long seed) { return new Abyss(seed); }
	public static VolumeField orbitalRuins(long seed) { return new Orbital(seed); }

	private abstract static class DeckField implements VolumeField {
		@Override
		public final double density(int x, int y, int z) {
			return VolumeMath.launchDeck(limitDensity(terrain(x + 0.5, y + 0.5, z + 0.5)), x, y, z);
		}

		@Override
		public final Material material(int x, int y, int z) {
			return VolumeMath.isLaunchDeck(x, y, z)
					? VolumeMath.deckMaterial(x, y, z) : terrainMaterial(x + 0.5, y + 0.5, z + 0.5);
		}

		abstract double terrain(double x, double y, double z);
		abstract Material terrainMaterial(double x, double y, double z);
	}

	private static final class Continuum extends DeckField {
		private final long seed;
		private final double phase;
		private final Orbital orbit;

		Continuum(long seed) {
			this.seed = seed;
			phase = phase(seed);
			orbit = new Orbital(seed);
		}

		@Override
		double terrain(double x, double y, double z) {
			if (y > 300) {
				double islands = y < 1272 ? Math.min(Math.min(y - 340, 1272 - y), skyIsland(x, y, z)) : -DENSITY_LIMIT;
				double belt = y > 1180 ? Math.min(y - 1180, orbit.terrain(x, y, z)) : -DENSITY_LIMIT;
				return Math.max(islands, belt);
			}

			double surface = surface(x, z);
			double ground = surface - y + 11 * Math.sin(x / 56.0 + phase) * Math.cos(y / 31.0) * Math.cos(z / 71.0);
			if (ground <= -DENSITY_LIMIT) return ground;
			double shaft = Math.hypot(repeat(x - 42, 288), repeat(z, 288)) - 17;
			double caveY = repeat(y - 25 * Math.sin(x / 126.0 + phase) * Math.cos(z / 141.0), 112);
			double caveX = repeat(x - 18 * Math.sin(y / 192.0), 248);
			double caveZ = repeat(z + 21 * Math.cos(y / 160.0), 248);
			double tunnels = Math.hypot(Math.min(Math.abs(caveX), Math.abs(caveZ)), caveY) - 19;
			double hall = -box(repeat(x - 71, 288), repeat(y, 168), repeat(z - 41, 288), 35, 15, 38);
			// Keep the shallow crust intact between shafts while carving the full tunnel volume below it.
			double subsurface = Math.max(15 - (surface - y), Math.min(tunnels, hall));
			double dockExit = -box(x - 22, y - 91, z, 34, 12, 12);
			return Math.min(ground, Math.min(dockExit, Math.min(shaft, subsurface)));
		}

		@Override
		Material terrainMaterial(double x, double y, double z) {
			if (y > 1180 && orbit.terrain(x, y, z) > 0) return orbit.terrainMaterial(x, y, z);
			if (y > 300) return Material.STONE;
			if (y < -480) return Material.BASALT;
			if (y < -95) return Material.DEEPSLATE;
			double depth = surface(x, z) - y;
			if (depth < 1 && y < 170) return Material.GRASS;
			return depth < 4 && y < 170 ? Material.DIRT : Material.STONE;
		}

		private double surface(double x, double z) {
			return 75 + 92 * Math.sin(x / 319.0 + phase) * Math.cos(z / 287.0 - phase)
					+ 49 * Math.sin((x + z) / 101.0 + phase) * Math.sin((x - z) / 196.0);
		}

		private double skyIsland(double x, double y, double z) {
			long cellX = cell(x, 336);
			long cellY = cell(y, 208);
			long cellZ = cell(z, 336);
			long random = hash(seed ^ 0x69736c616e64734cL, cellX, cellY, cellZ);
			if ((random & 7) < 3) return -DENSITY_LIMIT;
			double dx = x - cellX * 336;
			double dy = y - cellY * 208;
			double dz = z - cellZ * 336;
			double island = 82 + ((random >>> 8) & 31) - Math.sqrt(dx * dx + 7 * dy * dy + dz * dz);
			return island > -8 ? island + 9 * Math.sin(x / 26.0 + phase) * Math.cos(z / 28.0) : island;
		}
	}

	private static final class Megacity extends DeckField {
		private static final int DISTRICT_SIZE = 256;
		private final long seed;

		Megacity(long seed) { this.seed = seed; }

		@Override
		double terrain(double x, double y, double z) {
			long districtX = cell(x, DISTRICT_SIZE);
			long districtZ = cell(z, DISTRICT_SIZE);
			long id = hash(seed ^ 0x746f776572734cL, districtX, 0, districtZ);
			double floor = -128 + ((id >>> 10) & 63);
			double roof = 2560 + ((id >>> 18) & 2047);
			double extent = 52 + ((id >>> 4) & 15);
			double dx = x - districtX * DISTRICT_SIZE;
			double dz = z - districtZ * DISTRICT_SIZE;
			double wall = extent - Math.max(Math.abs(dx), Math.abs(dz));
			double vertical = Math.min(y - floor, roof - y);
			double section = repeat(y - SPAWN_Y, 24);
			double shaft = Math.max(Math.abs(dx), Math.abs(dz)) - 12;
			double slab = Math.min(Math.min(wall, shaft), 1.6 - Math.abs(section));
			double supports = 3.5 - Math.max(Math.abs(Math.abs(dx) - (extent - 4)), Math.abs(Math.abs(dz) - (extent - 4)));
			double windows = Math.max(Math.abs(section - 7) - 3.5,
					Math.min(Math.abs(repeat(dx, 24)), Math.abs(repeat(dz, 24))) - 5);
			double facade = Math.min(2.4 - Math.abs(wall), windows);
			double tower = Math.max(Math.min(Math.min(y + 216, roof - y), supports), Math.min(vertical, Math.max(slab, facade)));
			// Opposite directions connect on offset service levels. Open their doors in the facade.
			double alongX = Math.min(8 - Math.abs(dz), 1.7 - Math.abs(repeat(y - 77, 256)));
			double alongZ = Math.min(8 - Math.abs(dx), 1.7 - Math.abs(repeat(y - 205, 256)));
			double bridge = Math.min(Math.min(y - 60, 2370 - y), Math.max(alongX, alongZ));
			double doorX = Math.min(7 - Math.abs(dz), 5 - Math.abs(repeat(y - 83, 256)));
			double doorZ = Math.min(7 - Math.abs(dx), 5 - Math.abs(repeat(y - 211, 256)));
			tower = Math.min(tower, -Math.min(2390 - y, Math.max(doorX, doorZ)));
			double foundations = -214 - y;
			return Math.max(foundations, Math.max(tower, bridge));
		}

		@Override
		Material terrainMaterial(double x, double y, double z) {
			if (y < -214) return Material.DEEPSLATE;
			return Math.abs(repeat(y - 91, 128)) < 0.65 && Math.abs(repeat(x + z, 19)) < 0.8
					? Material.LIGHT : Material.ENGINEERED;
		}
	}

	private static final class Abyss extends DeckField {
		private final double phase;

		Abyss(long seed) { phase = phase(seed); }

		@Override
		double terrain(double x, double y, double z) {
			double dx = repeat(x, 288);
			double dz = repeat(z, 288);
			double dy = repeat(y - SPAWN_Y - 9, 96);
			double radius = Math.hypot(dx, dz);
			double tunnel = Math.hypot(Math.min(Math.abs(dx), Math.abs(dz)), dy) - 15;
			double circularGallery = Math.hypot(radius - 92, dy) - 17;
			double shaft = radius - 12;
			double laboratories = -box(Math.abs(dx) - 72, dy, Math.abs(dz) - 58, 26, 16, 22);
			double crust = 198 + 18 * Math.sin((x + z) / 271.0 + phase) - y;
			return Math.min(crust, Math.min(Math.min(tunnel, circularGallery), Math.min(shaft, laboratories)));
		}

		@Override
		Material terrainMaterial(double x, double y, double z) {
			double edge = Math.abs(repeat(y - SPAWN_Y - 9, 96));
			if (edge >= 15 && edge < 19) return Material.ENGINEERED;
			return Math.sin(x / 47.0 - y / 71.0 + z / 96.0 + phase) > 0.8 ? Material.TUFF : Material.DEEPSLATE;
		}
	}

	private static final class Orbital extends DeckField {
		private static final int CELL_SIZE = 448;
		private final long seed;
		private final double phase;

		Orbital(long seed) {
			this.seed = seed;
			phase = phase(seed);
		}

		@Override
		double terrain(double x, double y, double z) {
			double height = y - SPAWN_Y;
			long indexX = cell(x, CELL_SIZE);
			long indexY = cell(height, CELL_SIZE);
			long indexZ = cell(z, CELL_SIZE);
			long id = hash(seed ^ 0x6f72626974616c4cL, indexX, indexY, indexZ);
			double dx = x - indexX * CELL_SIZE;
			double dy = height - indexY * CELL_SIZE;
			double dz = z - indexZ * CELL_SIZE;
			if (station(indexX, indexY, indexZ, id)) return stationDensity(dx, dy, dz);
			if ((id & 7) == 1) return -DENSITY_LIMIT;
			double radius = 98 + ((id >>> 29) & 47);
			double rockyBody = radius - Math.sqrt(dx * dx + 1.35 * dy * dy + dz * dz);
			if (rockyBody <= -12) return rockyBody;
			double crags = 10 * Math.sin(x / 21.0 + phase) * Math.cos(y / 17.0) * Math.cos(z / 23.0);
			double galleries = Math.hypot(dy, Math.min(Math.abs(dx), Math.abs(dz))) - 14;
			double cavern = Math.sqrt(dx * dx + dy * dy + dz * dz) - 33;
			return Math.min(rockyBody + crags, Math.min(galleries, cavern));
		}

		@Override
		Material terrainMaterial(double x, double y, double z) {
			long indexX = cell(x, CELL_SIZE);
			long indexY = cell(y - SPAWN_Y, CELL_SIZE);
			long indexZ = cell(z, CELL_SIZE);
			long id = hash(seed ^ 0x6f72626974616c4cL, indexX, indexY, indexZ);
			if (station(indexX, indexY, indexZ, id)) return Material.ENGINEERED;
			return Math.sin((x + 0.8 * y - z) / 49.0 + phase) > 0.89 ? Material.CALCITE : Material.BASALT;
		}

		private static boolean station(long x, long y, long z, long id) {
			return (x == 0 && y == 0 && z == 0) || (id & 7) == 0;
		}

		private static double stationDensity(double x, double y, double z) {
			double plan = 64 - Math.max(Math.abs(x), Math.abs(z));
			double aperture = Math.max(Math.abs(x), Math.abs(z)) - 10;
			double deck = Math.min(Math.min(plan, aperture), 2 - Math.abs(repeat(y + 2, 26)));
			double supports = 3.2 - Math.max(Math.abs(Math.abs(x) - 57), Math.abs(Math.abs(z) - 57));
			double rim = Math.min(2.4 - Math.abs(plan), 3 - Math.abs(repeat(y - 4, 26)));
			double gangway = Math.max(box(x, y + 2, z, 112, 2, 8), box(x, y + 2, z, 8, 2, 112));
			return Math.max(gangway, Math.min(40 - Math.abs(y), Math.max(deck, Math.max(rim, supports))));
		}
	}

	private static long cell(double coordinate, int size) {
		return (long) Math.floor(coordinate / size + 0.5);
	}

	private static double phase(long seed) {
		return Math.TAU * (hash(seed, 0, 0, 0) >>> 11) * 0x1.0p-53;
	}
}
