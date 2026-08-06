package com.terminaldetector.drmd.world.llod;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural voxel shells for LLOD0/1/2 — deterministic from kind + center + size.
 * LLOD0 aims for up to ~thousands of surface cubes so distant megastructures stay readable in 6DoF flight.
 */
public final class VoxelLodMesh {
	public record Voxel(float x, float y, float z, float half) {}

	private VoxelLodMesh() {}

	public static List<Voxel> build(MacroEntry.Kind kind, Vec3d center,
									float rx, float ry, float rz,
									LlodLevel level, long seed) {
		if (!level.drawsVoxels()) return List.of();
		return switch (level) {
			case LLOD0 -> silhouette(kind, center, rx, ry, rz, seed, level.voxelBudget);
			case LLOD1 -> largeForms(kind, center, rx, ry, rz, seed, level.voxelBudget);
			case LLOD2 -> region(kind, center, rx, ry, rz);
			default -> List.of();
		};
	}

	/** Dense surface silhouette — several hundred to ~2800 cubes. */
	private static List<Voxel> silhouette(MacroEntry.Kind kind, Vec3d c,
										  float rx, float ry, float rz,
										  long seed, int budget) {
		List<Voxel> out = new ArrayList<>(Math.min(budget, 512));
		int res = resolutionForBudget(budget, kind);
		float hx = Math.max(rx, 4f);
		float hy = Math.max(ry, 4f);
		float hz = Math.max(rz, 4f);
		float cell = Math.max(hx, Math.max(hy, hz)) * 2f / res;
		float half = cell * 0.48f;

		for (int ix = 0; ix < res; ix++) {
			for (int iy = 0; iy < res; iy++) {
				for (int iz = 0; iz < res; iz++) {
					float nx = (ix + 0.5f) / res * 2f - 1f;
					float ny = (iy + 0.5f) / res * 2f - 1f;
					float nz = (iz + 0.5f) / res * 2f - 1f;
					if (!occupies(kind, nx, ny, nz, seed)) continue;
					// Surface only — skip fully interior cells
					if (isInterior(kind, nx, ny, nz, seed, 2f / res)) continue;
					float x = (float) c.x + nx * hx;
					float y = (float) c.y + ny * hy;
					float z = (float) c.z + nz * hz;
					out.add(new Voxel(x, y, z, half));
					if (out.size() >= budget) return out;
				}
			}
		}
		return out;
	}

	/** Large structural forms — fewer, thicker voxels. */
	private static List<Voxel> largeForms(MacroEntry.Kind kind, Vec3d c,
										  float rx, float ry, float rz,
										  long seed, int budget) {
		List<Voxel> out = new ArrayList<>();
		int res = 8;
		float hx = Math.max(rx, 6f);
		float hy = Math.max(ry, 6f);
		float hz = Math.max(rz, 6f);
		float cell = Math.max(hx, Math.max(hy, hz)) * 2f / res;
		float half = cell * 0.55f;

		for (int ix = 0; ix < res; ix++) {
			for (int iy = 0; iy < res; iy++) {
				for (int iz = 0; iz < res; iz++) {
					float nx = (ix + 0.5f) / res * 2f - 1f;
					float ny = (iy + 0.5f) / res * 2f - 1f;
					float nz = (iz + 0.5f) / res * 2f - 1f;
					if (!occupies(kind, nx, ny, nz, seed)) continue;
					out.add(new Voxel(
							(float) c.x + nx * hx,
							(float) c.y + ny * hy,
							(float) c.z + nz * hz,
							half));
					if (out.size() >= budget) return out;
				}
			}
		}
		return out;
	}

	/** Region proxies — handful of mega cubes covering the footprint. */
	private static List<Voxel> region(MacroEntry.Kind kind, Vec3d c, float rx, float ry, float rz) {
		List<Voxel> out = new ArrayList<>(8);
		float hx = Math.max(rx, 8f);
		float hy = Math.max(ry, 8f);
		float hz = Math.max(rz, 8f);
		switch (kind) {
			case RING, ARCH -> {
				out.add(new Voxel((float) c.x - hx * 0.6f, (float) c.y, (float) c.z, hx * 0.35f));
				out.add(new Voxel((float) c.x + hx * 0.6f, (float) c.y, (float) c.z, hx * 0.35f));
				out.add(new Voxel((float) c.x, (float) c.y + hy * 0.5f, (float) c.z, hx * 0.4f));
			}
			case CANYON, RIFT -> {
				out.add(new Voxel((float) c.x, (float) c.y, (float) c.z, Math.max(hx, hz) * 0.5f));
				out.add(new Voxel((float) c.x, (float) (c.y - hy * 0.4f), (float) c.z, Math.max(hx, hz) * 0.35f));
			}
			case SPIRAL_RANGE -> {
				for (int i = 0; i < 5; i++) {
					double a = i * Math.PI * 0.4;
					out.add(new Voxel(
							(float) (c.x + Math.cos(a) * hx * 0.5),
							(float) (c.y - hy + i * hy * 0.4),
							(float) (c.z + Math.sin(a) * hz * 0.5),
							hx * 0.22f));
				}
			}
			case WORM -> {
				for (int i = -3; i <= 3; i++) {
					out.add(new Voxel((float) c.x + i * hx * 0.25f, (float) c.y, (float) c.z, hy * 0.35f));
				}
			}
			case UFO, CRASHED_UFO -> {
				out.add(new Voxel((float) c.x, (float) c.y, (float) c.z, hx * 0.55f));
				out.add(new Voxel((float) c.x, (float) (c.y - hy * 0.25f), (float) c.z, hx * 0.25f));
			}
			case LUNAR_BASE -> {
				out.add(new Voxel((float) c.x, (float) c.y, (float) c.z, hx * 0.5f));
				out.add(new Voxel((float) c.x + hx * 0.4f, (float) c.y, (float) c.z, hx * 0.2f));
				out.add(new Voxel((float) c.x - hx * 0.4f, (float) c.y, (float) c.z, hx * 0.2f));
			}
			default -> out.add(new Voxel((float) c.x, (float) c.y, (float) c.z,
					Math.max(hx, Math.max(hy, hz)) * 0.55f));
		}
		return out;
	}

	private static int resolutionForBudget(int budget, MacroEntry.Kind kind) {
		// Surface of a cube grid ~ 6*r^2; pick r so 6r^2 ≈ budget * fillFactor
		double fill = switch (kind) {
			case RING, ARCH -> 0.25;
			case FLOATING_CONTINENT, INVERTED_ISLAND -> 0.35;
			case SPIRAL_RANGE, CANYON, RIFT -> 0.3;
			case WORM, SWARM -> 0.2;
			case UFO, CRASHED_UFO -> 0.28;
			case LUNAR_BASE -> 0.32;
			default -> 0.4;
		};
		int r = (int) Math.ceil(Math.sqrt(budget / (6.0 * fill)));
		return MathHelperClamp(r, 10, 28);
	}

	private static int MathHelperClamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static boolean isInterior(MacroEntry.Kind kind, float nx, float ny, float nz, long seed, float step) {
		return occupies(kind, nx + step, ny, nz, seed)
				&& occupies(kind, nx - step, ny, nz, seed)
				&& occupies(kind, nx, ny + step, nz, seed)
				&& occupies(kind, nx, ny - step, nz, seed)
				&& occupies(kind, nx, ny, nz + step, seed)
				&& occupies(kind, nx, ny, nz - step, seed);
	}

	/** Occupancy in normalized [-1,1]^3 space. */
	static boolean occupies(MacroEntry.Kind kind, float x, float y, float z, long seed) {
		float r2 = x * x + y * y + z * z;
		return switch (kind) {
			case RING -> {
				float major = (float) Math.sqrt(x * x + z * z);
				float tube = (major - 0.72f) * (major - 0.72f) + y * y;
				yield tube < 0.08f && r2 < 1.15f;
			}
			case ARCH -> {
				float major = (float) Math.sqrt(x * x + y * y);
				yield Math.abs(major - 0.75f) < 0.12f && Math.abs(z) < 0.35f && y > -0.05f;
			}
			case FLOATING_CONTINENT -> {
				float disk = x * x + z * z;
				yield disk < 1.0f && y < 0.35f && y > -0.55f * (1f - disk);
			}
			case INVERTED_ISLAND -> {
				float disk = x * x + z * z;
				yield disk < 1.0f && y > -0.2f && y < 0.55f * (1f - disk);
			}
			case SPIRAL_RANGE -> {
				float ang = (float) Math.atan2(z, x);
				float spiral = (ang / (float) (Math.PI * 2) + 0.5f);
				float yy = (y + 1f) * 0.5f;
				float rad = (float) Math.sqrt(x * x + z * z);
				yield Math.abs(rad - (0.3f + spiral * 0.5f)) < 0.18f && Math.abs(yy - spiral) < 0.2f;
			}
			case CANYON, RIFT -> Math.abs(x) < 0.28f && Math.abs(z) < 0.85f && Math.abs(y) < 0.95f;
			case WORM -> {
				float spine = Math.abs(y) + Math.abs(z);
				yield Math.abs(x) < 0.95f && spine < 0.22f + 0.08f * (float) Math.sin(x * 8 + (seed & 7));
			}
			case SWARM -> {
				float n = hash(seed, x, y, z);
				yield r2 < 1.0f && n > 0.72f;
			}
			case INDUSTRIAL_COMPLEX, STATION -> {
				boolean shell = Math.abs(x) > 0.7f || Math.abs(y) > 0.7f || Math.abs(z) > 0.7f;
				boolean core = r2 < 0.2f;
				boolean tunnel = Math.abs(y) < 0.12f && Math.abs(z) < 0.12f;
				yield (shell && r2 < 1.05f) || core || tunnel;
			}
			case LUNAR_BASE -> {
				float disk = x * x + z * z;
				boolean deck = disk < 1.0f && Math.abs(y) < 0.35f;
				boolean rim = disk > 0.72f && disk < 1.05f && Math.abs(y) < 0.55f;
				boolean core = r2 < 0.18f;
				yield deck || rim || core;
			}
			case UFO, CRASHED_UFO -> {
				float disk = x * x + z * z;
				boolean saucer = disk < 1.0f && Math.abs(y) < 0.22f + 0.35f * (1f - disk);
				boolean dome = disk < 0.35f && y > 0 && y < 0.55f;
				boolean keel = disk < 0.2f && y < 0 && y > -0.45f;
				yield saucer || dome || keel;
			}
			case MEGACITY -> {
				// Plate rim + tower grid silhouette for orbit LLOD.
				float disk = x * x + z * z;
				boolean plate = disk < 1.05f && y > -0.15f && y < 0.08f;
				boolean tower = Math.abs(((x * 5f) % 1f) - 0.5f) < 0.18f
						&& Math.abs(((z * 5f) % 1f) - 0.5f) < 0.18f
						&& y > 0.05f && y < 0.85f && disk < 0.92f;
				boolean pyramid = Math.abs(x) + Math.abs(z) < 0.35f * (1f - y) && y > 0 && y < 0.7f;
				yield plate || tower || pyramid;
			}
			case MEGA_LOCATOR, LOCATOR -> {
				// Spark seascape — mast + parabolic dish silhouette for horizon LLOD.
				float disk = x * x + z * z;
				boolean mast = disk < 0.12f && y > -0.9f && y < 0.55f;
				float dishY = 0.55f + disk * 0.55f;
				boolean dish = disk > 0.2f && disk < 1.0f && Math.abs(y - dishY) < 0.08f;
				boolean pad = disk < 0.55f && y > -1.0f && y < -0.75f;
				yield mast || dish || pad;
			}
			case SCORCHED_TOWN -> {
				float disk = x * x + z * z;
				boolean ash = disk < 1.0f && y > -0.2f && y < 0.05f;
				boolean ruin = Math.abs(((x * 4f) % 1f) - 0.5f) < 0.2f
						&& Math.abs(((z * 4f) % 1f) - 0.5f) < 0.2f
						&& y > 0 && y < 0.55f && disk < 0.85f;
				boolean crater = disk < 0.28f && y > -0.35f && y < 0.02f;
				yield ash || ruin || crater;
			}
			case IRON_GUILD -> {
				float disk = x * x + z * z;
				boolean yard = disk < 1.0f && y > -0.15f && y < 0.06f;
				boolean hall = Math.abs(x) < 0.35f && Math.abs(z) < 0.28f && y > 0 && y < 0.45f;
				boolean stack = Math.abs(x - 0.35f) < 0.18f && Math.abs(z - 0.2f) < 0.18f
						&& y > 0 && y < 0.7f;
				yield yard || hall || stack;
			}
			case KEEPER -> r2 < 0.55f;
			default -> r2 < 0.85f;
		};
	}

	private static float hash(long seed, float x, float y, float z) {
		int n = (int) seed;
		n ^= Float.floatToIntBits(x * 12.9898f);
		n ^= Float.floatToIntBits(y * 78.233f);
		n ^= Float.floatToIntBits(z * 37.719f);
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
	}
}
