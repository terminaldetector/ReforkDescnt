package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.planet.PlanetMap;

import java.util.Arrays;
import java.util.Set;

/**
 * The planet's surface as a heightfield of columns, built once and kept.
 *
 * <p>The old flight-simulator trick rather than a mesh of real terrain: sample a height per cell,
 * stand a column there, and let the steps between neighbours do the shading. Which is also why it
 * is allowed to look blocky — that chunkiness is the point, not an artefact of running out of
 * budget.
 *
 * <p>Cells are sized <em>by distance</em>: a cell is always about a seventh of its own distance
 * from the eye, so every column covers the same slice of the view however far out it is. That is
 * what keeps a horizon two kilometres away costing the same as the ground below the ship — a grid
 * of fixed-size cells would spend everything it had on the first ring and never reach the horizon.
 *
 * <p>Geometry is in world units relative to a build origin, with absolute Y, and is rebuilt only
 * when the ship has moved off that origin or the view has changed shape. Everything that changes
 * per frame — where the camera is, how far down the planet sits, how strongly it shows — is a
 * matrix and a colour multiply at draw time. The renderer this replaces rebuilt up to ten thousand
 * boxes on the render thread every frame, which is most of why it had to be switched off.
 */
public final class PlanetSurfaceMesh {
	/** How many cells fit across a ring's inner radius — sets the angular size of one column. */
	private static final double CELLS_PER_RADIUS = 7.0;
	/**
	 * Each ring reaches this many times further than the one inside it — and its cells are that
	 * much coarser. Kept modest because the jump in cell size is visible as a terrace where two
	 * rings meet, and a gentler step buys that back for a couple of hundred more columns.
	 */
	private static final double RING_GROWTH = 1.8;
	/** Finest cell, so the field never dissolves into thousands of tiny columns underfoot. */
	private static final int MIN_CELL = 32;
	/** Rings past this add nothing a pixel could show. */
	private static final int MAX_RINGS = 6;
	/** Hard ceiling on geometry, whatever the rings ask for. */
	private static final int MAX_QUADS = 16_000;
	/** How far the ship may drift from the build origin before the field is rebuilt. */
	private static final double ORIGIN_SLACK = 64.0;

	/** Positions relative to {@link #originX}/{@link #originZ}; Y is absolute world height. */
	public final float[] positions;
	public final int[] colours;
	public final int quads;
	public final double originX;
	public final double originZ;
	public final double innerRadius;
	public final double outerRadius;

	private PlanetSurfaceMesh(float[] positions, int[] colours, int quads, double originX,
							  double originZ, double innerRadius, double outerRadius) {
		this.positions = positions;
		this.colours = colours;
		this.quads = quads;
		this.originX = originX;
		this.originZ = originZ;
		this.innerRadius = innerRadius;
		this.outerRadius = outerRadius;
	}

	/** True when the ship has wandered off this build, or the view no longer wants its shape. */
	public boolean stale(double camX, double camZ, double wantedInner, double wantedOuter) {
		double dx = camX - originX;
		double dz = camZ - originZ;
		if (dx * dx + dz * dz > ORIGIN_SLACK * ORIGIN_SLACK) return true;
		if (Math.abs(wantedOuter - outerRadius) > outerRadius * 0.2) return true;
		return Math.abs(wantedInner - innerRadius) > Math.max(32.0, innerRadius * 0.25);
	}

	/**
	 * Build the field around a position.
	 *
	 * @param innerRadius where real chunks stop being drawn — the map starts there, so the two meet
	 *                    at the same bearing instead of overlapping. Zero when nothing real is drawn
	 *                    below at all, which is the usual case from the End band.
	 * @param outerRadius how much planet fits inside the projection once scaled toward the camera
	 * @param scarCells   32-block cells burnt by a reactor detonation
	 */
	public static PlanetSurfaceMesh build(long seed, double camX, double camZ,
										  double innerRadius, double outerRadius,
										  Set<Long> scarCells) {
		float[] positions = new float[MAX_QUADS * 12];
		int[] colours = new int[MAX_QUADS];
		int quads = 0;

		double ringInner = Math.max(0.0, innerRadius);
		// The walk needs a non-zero radius to size the first cell from, even at the nadir.
		double sizeFrom = Math.max(MIN_CELL * CELLS_PER_RADIUS, ringInner);
		for (int ring = 0; ring < MAX_RINGS && ringInner < outerRadius; ring++) {
			int cell = (int) Math.max(MIN_CELL, sizeFrom / CELLS_PER_RADIUS);
			double ringOuter = ring == MAX_RINGS - 1
					? outerRadius
					: Math.min(sizeFrom * RING_GROWTH, outerRadius);
			quads = emitRing(seed, camX, camZ, ringInner, ringOuter, cell, innerRadius, outerRadius,
					scarCells, positions, colours, quads);
			if (quads >= MAX_QUADS - 3) break;
			ringInner = ringOuter;
			sizeFrom = ringOuter;
		}

		return new PlanetSurfaceMesh(
				Arrays.copyOf(positions, quads * 12), Arrays.copyOf(colours, quads), quads,
				camX, camZ, innerRadius, outerRadius);
	}

	private static int emitRing(long seed, double camX, double camZ, double inner, double outer,
								int cell, double fadeFrom, double fadeTo, Set<Long> scarCells,
								float[] positions, int[] colours, int quads) {
		// Snap the grid to the world, not to the camera: a cell keeps its height as the ship moves,
		// so the ground does not crawl underneath it between rebuilds.
		int minX = (int) Math.floor((camX - outer) / cell);
		int maxX = (int) Math.floor((camX + outer) / cell);
		int minZ = (int) Math.floor((camZ - outer) / cell);
		int maxZ = (int) Math.floor((camZ + outer) / cell);
		double inner2 = inner * inner;
		double outer2 = outer * outer;

		for (int gx = minX; gx <= maxX; gx++) {
			for (int gz = minZ; gz <= maxZ; gz++) {
				if (quads >= MAX_QUADS - 3) return quads;
				double x0 = gx * (double) cell;
				double z0 = gz * (double) cell;
				double centreX = x0 + cell * 0.5;
				double centreZ = z0 + cell * 0.5;
				double mx = centreX - camX;
				double mz = centreZ - camZ;
				double d2 = mx * mx + mz * mz;
				if (d2 < inner2 || d2 >= outer2) continue;

				float h = PlanetMap.height(seed, centreX, centreZ);
				boolean water = h < PlanetMap.SEA_LEVEL;
				float top = water ? PlanetMap.SEA_LEVEL : h;
				int rgb = scarred(scarCells, centreX, centreZ)
						? PlanetMap.scarTint()
						: PlanetMap.tint(seed, centreX, centreZ, h);
				float alpha = alphaAt(Math.sqrt(d2), fadeFrom, fadeTo);

				float rx0 = (float) (x0 - camX);
				float rz0 = (float) (z0 - camZ);
				float rx1 = rx0 + cell;
				float rz1 = rz0 + cell;

				quads = quad(positions, colours, quads,
						rx0, top, rz0, rx1, top, rz0, rx1, top, rz1, rx0, top, rz1,
						argb(rgb, 1f, alpha));

				if (water) continue; // the sea is flat; a skirt on it reads as a wall

				// Skirts toward the two neighbours the grid walks away from. Every cell has its far
				// sides covered by the next cell's near sides, so two per cell tiles the whole field
				// with no doubled faces — and the step they draw is what makes a column read as a
				// column rather than a floating tile.
				float hx = PlanetMap.height(seed, centreX + cell, centreZ);
				float hz = PlanetMap.height(seed, centreX, centreZ + cell);
				int side = argb(rgb, 0.68f, alpha);
				float floor = PlanetMap.SEA_LEVEL - 6f;
				if (top > hx) {
					float bottom = Math.max(hx, floor);
					quads = quad(positions, colours, quads,
							rx1, top, rz0, rx1, bottom, rz0, rx1, bottom, rz1, rx1, top, rz1, side);
				}
				if (top > hz) {
					float bottom = Math.max(hz, floor);
					quads = quad(positions, colours, quads,
							rx0, top, rz1, rx0, bottom, rz1, rx1, bottom, rz1, rx1, top, rz1, side);
				}
			}
		}
		return quads;
	}

	/**
	 * Opacity for a cell: up out of the real chunks it abuts, down into haze at the horizon.
	 *
	 * <p>Both ends matter. Without the inner ramp the map would start as a hard ring exactly where
	 * vanilla stops drawing ground; without the outer one it would end at a rim in mid-air.
	 */
	private static float alphaAt(double distance, double fadeFrom, double outer) {
		float alpha = 1f;
		if (fadeFrom > 1.0) {
			double band = fadeFrom * 0.2;
			if (distance < fadeFrom + band) {
				alpha *= (float) (0.25 + 0.75 * Math.max(0.0, distance - fadeFrom) / band);
			}
		}
		double tail = outer * 0.25;
		if (distance > outer - tail) {
			alpha *= (float) Math.max(0.0, (outer - distance) / tail);
		}
		return Math.max(0f, Math.min(1f, alpha));
	}

	private static boolean scarred(Set<Long> scarCells, double worldX, double worldZ) {
		if (scarCells.isEmpty()) return false;
		int cx = Math.floorDiv((int) Math.floor(worldX), 32);
		int cz = Math.floorDiv((int) Math.floor(worldZ), 32);
		return scarCells.contains(((long) cx << 32) ^ (cz & 0xffffffffL));
	}

	private static int quad(float[] positions, int[] colours, int quads,
							float x0, float y0, float z0, float x1, float y1, float z1,
							float x2, float y2, float z2, float x3, float y3, float z3,
							int argb) {
		int i = quads * 12;
		positions[i] = x0; positions[i + 1] = y0; positions[i + 2] = z0;
		positions[i + 3] = x1; positions[i + 4] = y1; positions[i + 5] = z1;
		positions[i + 6] = x2; positions[i + 7] = y2; positions[i + 8] = z2;
		positions[i + 9] = x3; positions[i + 10] = y3; positions[i + 11] = z3;
		colours[quads] = argb;
		return quads + 1;
	}

	private static int argb(int rgb, float shade, float alpha) {
		int r = (int) (((rgb >> 16) & 0xFF) * shade);
		int g = (int) (((rgb >> 8) & 0xFF) * shade);
		int b = (int) ((rgb & 0xFF) * shade);
		int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
