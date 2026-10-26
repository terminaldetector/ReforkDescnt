package com.terminaldetector.drmd.world.llod.planet;

import java.util.ArrayList;
import java.util.List;

/**
 * Complex voxel occupancy for planetary cells — ridges, scar craters, weather decks.
 * Deterministic from seed + cell coords so unexplored fog-of-war matches across clients.
 */
public final class PlanetVoxelMath {
	public record Voxel(float x, float y, float z, float half, int argb) {}

	private PlanetVoxelMath() {}

	/**
	 * Expand a cell into world-space voxels for orbital / End floor draw.
	 *
	 * @param floorY  world Y of the planetary floor plane (End void bottom / orbit shelf)
	 * @param scale   horizontal scale of one cell on the floor mesh
	 */
	public static List<Voxel> expand(PlanetCell cell, long worldSeed, double originX, double originZ,
									 float floorY, float scale, int budget) {
		List<Voxel> out = new ArrayList<>(Math.min(budget, 64));
		boolean explored = cell.explored();
		float hx = heightNorm(cell, worldSeed);
		float scar = cell.scarred() ? 1f : 0f;
		int baseTint = explored ? cell.tint : proceduralTint(worldSeed, cell.cx, cell.cz);
		float r = ((baseTint >> 16) & 0xFF) / 255f;
		float g = ((baseTint >> 8) & 0xFF) / 255f;
		float b = (baseTint & 0xFF) / 255f;
		if (!explored) {
			r *= 0.35f; g *= 0.38f; b *= 0.55f; // fog-of-war desaturate
		}
		if (scar > 0) {
			r = Math.min(1f, r * 0.45f + 0.25f);
			g *= 0.35f;
			b *= 0.35f;
		}

		double cx = originX + cell.cx * scale + scale * 0.5;
		double cz = originZ + cell.cz * scale + scale * 0.5;
		int res = explored ? 5 : 3;
		float cellHalf = scale * 0.48f;
		float step = (cellHalf * 2f) / res;

		for (int ix = 0; ix < res; ix++) {
			for (int iz = 0; iz < res; iz++) {
				float nx = (ix + 0.5f) / res * 2f - 1f;
				float nz = (iz + 0.5f) / res * 2f - 1f;
				float ridge = noise(worldSeed, cell.cx + nx, cell.cz + nz) * 0.35f;
				float crater = scar > 0 ? Math.max(0f, 0.55f - (nx * nx + nz * nz)) * 0.9f : 0f;
				float h = (hx * 0.55f + ridge - crater) * (explored ? 14f : 8f);
				float y = floorY + h;
				float x = (float) (cx + nx * cellHalf);
				float z = (float) (cz + nz * cellHalf);
				float half = step * 0.48f;
				// Multi-layer shell: surface + subsurface voxel for thickness (orbital readability).
				out.add(new Voxel(x, y, z, half, argb(r, g, b, explored ? 0.72f : 0.38f)));
				if (explored && h > 4f) {
					out.add(new Voxel(x, y - half * 1.6f, z, half * 0.9f,
							argb(r * 0.6f, g * 0.55f, b * 0.5f, 0.55f)));
				}
				if (out.size() >= budget) return out;
			}
		}

		// Weather deck above cell
		if (cell.raining() || cell.storm()) {
			float a = cell.storm() ? 0.45f : 0.28f;
			float wy = floorY + (explored ? 22f : 16f) + hx * 6f;
			out.add(new Voxel((float) cx, wy, (float) cz, cellHalf * 0.85f,
					argb(0.75f, 0.78f, 0.9f, a)));
			if (cell.storm()) {
				out.add(new Voxel((float) cx, wy + 3f, (float) cz, cellHalf * 0.5f,
						argb(0.35f, 0.35f, 0.45f, 0.4f)));
			}
		}
		return out;
	}

	/** Procedural unexplored cell for fog-of-war. */
	public static PlanetCell procedural(long seed, int cx, int cz) {
		float n = noise(seed, cx * 0.37f, cz * 0.37f);
		int h = 55 + (int) (n * 40);
		return new PlanetCell(cx, cz, h, proceduralTint(seed, cx, cz), 0);
	}

	static float heightNorm(PlanetCell cell, long seed) {
		if (cell.explored()) return cell.height / 120f;
		return noise(seed, cell.cx * 0.41f, cell.cz * 0.41f);
	}

	static int proceduralTint(long seed, int cx, int cz) {
		float n = noise(seed ^ 0xABCDl, cx * 0.19f, cz * 0.19f);
		int r = (int) (30 + n * 40);
		int g = (int) (60 + n * 90);
		int b = (int) (40 + (1f - n) * 50);
		return (r << 16) | (g << 8) | b;
	}

	static float noise(long seed, float x, float z) {
		long h = seed ^ (Float.floatToIntBits(x) * 0x9E3779B9L) ^ (Float.floatToIntBits(z) * 0x85EBCA77L);
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		return ((h >>> 8) & 0xFFFF) / 65535f;
	}

	private static int argb(float r, float g, float b, float a) {
		int A = Math.max(0, Math.min(255, (int) (a * 255)));
		int R = Math.max(0, Math.min(255, (int) (r * 255)));
		int G = Math.max(0, Math.min(255, (int) (g * 255)));
		int B = Math.max(0, Math.min(255, (int) (b * 255)));
		return (A << 24) | (R << 16) | (G << 8) | B;
	}
}
