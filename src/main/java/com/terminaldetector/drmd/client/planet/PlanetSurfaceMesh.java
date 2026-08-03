package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.planet.PlanetMap;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The distant world as voxels: a heightfield of columns, with the things built on it standing up
 * out of that field.
 *
 * <p>The old flight-simulator trick rather than a mesh of real terrain — sample a height per cell,
 * stand a column there, let the steps between neighbours do the shading. Which is also why it is
 * allowed to look blocky: that chunkiness is the point, not an artefact of running out of budget.
 *
 * <p>Two things keep it affordable at kilometre range. Cells are sized <em>by distance</em> — a
 * cell is always about a seventh of its own distance from the eye — so every column covers the same
 * slice of the view however far out it is, and a horizon twelve kilometres away costs about what
 * the ground below the ship costs. And distance itself is compressed by
 * {@link HorizonProjection}, so all of it fits inside the projection's far plane while every point
 * keeps its exact bearing and elevation.
 *
 * <p>Because the compression is per-vertex and measured from the eye, geometry is built <em>around
 * the camera</em> and rebuilt when the ship leaves that origin. Between rebuilds the field is a
 * rigid body: the renderer shifts it and multiplies its colour, nothing more. The renderer this
 * replaces rebuilt up to ten thousand boxes on the render thread every frame.
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
	/** Enough rings to walk from the chunk edge out to the far radius at that growth. */
	private static final int MAX_RINGS = 9;
	/** Hard ceiling on geometry, whatever the rings ask for. */
	private static final int MAX_QUADS = 18_000;
	/** Landmarks drawn, nearest first — the rest are too far apart to tell from terrain. */
	private static final int MAX_LANDMARKS = 48;
	/** How far the ship may drift from the build origin before the field is rebuilt. */
	private static final double ORIGIN_SLACK = 64.0;
	/** And how far it may climb, since the compression is measured from the eye. */
	private static final double ALTITUDE_SLACK = 48.0;

	/** Positions are compressed and relative to the build origin — see {@link HorizonProjection}. */
	public final float[] positions;
	public final int[] colours;
	public final int quads;
	public final double originX;
	public final double originY;
	public final double originZ;
	public final double innerRadius;
	public final double reach;

	private PlanetSurfaceMesh(float[] positions, int[] colours, int quads, double originX,
							  double originY, double originZ, double innerRadius, double reach) {
		this.positions = positions;
		this.colours = colours;
		this.quads = quads;
		this.originX = originX;
		this.originY = originY;
		this.originZ = originZ;
		this.innerRadius = innerRadius;
		this.reach = reach;
	}

	/** True when the ship has wandered off this build, or the view no longer wants its shape. */
	public boolean stale(double camX, double camY, double camZ, double wantedInner, double wantedReach) {
		double dx = camX - originX;
		double dz = camZ - originZ;
		if (dx * dx + dz * dz > ORIGIN_SLACK * ORIGIN_SLACK) return true;
		if (Math.abs(camY - originY) > ALTITUDE_SLACK) return true;
		if (Math.abs(wantedReach - reach) > reach * 0.2) return true;
		return Math.abs(wantedInner - innerRadius) > Math.max(32.0, innerRadius * 0.25);
	}

	/**
	 * Build the field around the eye.
	 *
	 * @param innerRadius where real chunks stop being drawn. Inside it the compression is the
	 *                    identity, so the map continues the real ground with no step at the join.
	 * @param reach       the compressed field's outer shell, inside the far plane
	 */
	public static PlanetSurfaceMesh build(long seed, double camX, double camY, double camZ,
										  double innerRadius, double reach,
										  Set<Long> scarCells, List<HorizonLandmark> landmarks) {
		float[] positions = new float[MAX_QUADS * 12];
		int[] colours = new int[MAX_QUADS];
		Builder b = new Builder(seed, camX, camY, camZ, innerRadius, reach, scarCells,
				positions, colours);

		// Plan the rings before emitting any: the outermost one has to know where the field ends so
		// it can fade out into it. A ring that stops at full opacity leaves a rim hanging in the air.
		double[] ringEdges = new double[MAX_RINGS + 1];
		int[] ringCells = new int[MAX_RINGS];
		int rings = 0;
		double ringInner = Math.max(0.0, innerRadius);
		// The walk needs a non-zero radius to size the first cell from, even at the nadir.
		double sizeFrom = Math.max(MIN_CELL * CELLS_PER_RADIUS, ringInner);
		ringEdges[0] = ringInner;
		while (rings < MAX_RINGS && ringInner < HorizonProjection.MAX_TRUE_RADIUS) {
			ringCells[rings] = (int) Math.max(MIN_CELL, sizeFrom / CELLS_PER_RADIUS);
			ringInner = Math.min(sizeFrom * RING_GROWTH, HorizonProjection.MAX_TRUE_RADIUS);
			ringEdges[++rings] = ringInner;
			sizeFrom = ringInner;
		}
		b.fieldOuter = ringEdges[rings];

		for (int ring = 0; ring < rings; ring++) {
			b.emitRing(ringEdges[ring], ringEdges[ring + 1], ringCells[ring]);
			if (b.full()) break;
		}

		b.emitLandmarks(landmarks);

		return new PlanetSurfaceMesh(
				Arrays.copyOf(positions, b.quads * 12), Arrays.copyOf(colours, b.quads), b.quads,
				camX, camY, camZ, innerRadius, reach);
	}

	/** Holds the build's shared state so the emit methods stay readable. */
	private static final class Builder {
		private final long seed;
		private final double camX;
		private final double camY;
		private final double camZ;
		private final double inner;
		private final double reach;
		private final Set<Long> scarCells;
		private final float[] positions;
		private final int[] colours;
		private int quads;
		/** Where the ring walk ends — the outer edge the haze fades into. */
		private double fieldOuter = HorizonProjection.MAX_TRUE_RADIUS;

		Builder(long seed, double camX, double camY, double camZ, double inner, double reach,
				Set<Long> scarCells, float[] positions, int[] colours) {
			this.seed = seed;
			this.camX = camX;
			this.camY = camY;
			this.camZ = camZ;
			this.inner = inner;
			this.reach = reach;
			this.scarCells = scarCells;
			this.positions = positions;
			this.colours = colours;
		}

		boolean full() {
			return quads >= MAX_QUADS - 8;
		}

		void emitRing(double ringInner, double ringOuter, int cell) {
			// Snap the grid to the world, not to the camera: a cell keeps its height as the ship
			// moves, so the ground does not crawl underneath it between rebuilds.
			int minX = (int) Math.floor((camX - ringOuter) / cell);
			int maxX = (int) Math.floor((camX + ringOuter) / cell);
			int minZ = (int) Math.floor((camZ - ringOuter) / cell);
			int maxZ = (int) Math.floor((camZ + ringOuter) / cell);
			double inner2 = ringInner * ringInner;
			double outer2 = ringOuter * ringOuter;

			for (int gx = minX; gx <= maxX; gx++) {
				for (int gz = minZ; gz <= maxZ; gz++) {
					if (full()) return;
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
					double top = water ? PlanetMap.SEA_LEVEL : h;
					int rgb = scarred(centreX, centreZ)
							? PlanetMap.scarTint()
							: PlanetMap.tint(seed, centreX, centreZ, h);
					float alpha = alphaAt(Math.sqrt(d2));
					double x1 = x0 + cell;
					double z1 = z0 + cell;

					quad(x0, top, z0, x1, top, z0, x1, top, z1, x0, top, z1, argb(rgb, 1f, alpha));

					if (water) continue; // the sea is flat; a skirt on it reads as a wall

					// Skirts toward the two neighbours the grid walks away from. Every cell has its
					// far sides covered by the next cell's near sides, so two per cell tiles the
					// field with no doubled faces — and the step they draw is what makes a column
					// read as a column rather than a floating tile.
					double hx = PlanetMap.height(seed, centreX + cell, centreZ);
					double hz = PlanetMap.height(seed, centreX, centreZ + cell);
					int side = argb(rgb, 0.68f, alpha);
					double floor = PlanetMap.SEA_LEVEL - 6.0;
					if (top > hx) {
						double bottom = Math.max(hx, floor);
						quad(x1, top, z0, x1, bottom, z0, x1, bottom, z1, x1, top, z1, side);
					}
					if (top > hz) {
						double bottom = Math.max(hz, floor);
						quad(x0, top, z1, x0, bottom, z1, x1, bottom, z1, x1, top, z1, side);
					}
				}
			}
		}

		/**
		 * Stand the built world up out of the terrain.
		 *
		 * <p>A locator is a mast and a dish; a megacity is a handful of towers of different heights.
		 * Four boxes at most, from the catalogue entry's own footprint and height — enough that the
		 * thing on the horizon is recognisably the thing you would fly to, which a single grey box
		 * never is.
		 */
		void emitLandmarks(List<HorizonLandmark> landmarks) {
			int drawn = 0;
			for (HorizonLandmark mark : landmarks) {
				if (full() || drawn >= MAX_LANDMARKS) return;
				if (!mark.drawable()) continue;
				double dx = mark.x() - camX;
				double dz = mark.z() - camZ;
				double d = Math.sqrt(dx * dx + dz * dz);
				// Inside the chunk radius the real structure is being drawn; past the field there is
				// nothing to attach to.
				if (d < inner || d > HorizonProjection.MAX_TRUE_RADIUS) continue;

				double base = PlanetMap.height(seed, mark.x(), mark.z());
				double groundTop = Math.max(base, PlanetMap.SEA_LEVEL);
				float alpha = alphaAt(d);
				int rgb = mark.colour();
				double half = mark.halfWidth();
				double height = mark.height();

				switch (mark.kind()) {
					case MEGA_LOCATOR, LOCATOR -> {
						// Mast, then the dish near the top — the Spark read, and the reason a
						// locator is legible from kilometres out.
						box(mark.x(), groundTop, mark.z(), half * 0.22, height, rgb, alpha);
						double dishY = groundTop + height * 0.82;
						box(mark.x(), dishY, mark.z(), half * 1.15, height * 0.1, rgb, alpha);
						box(mark.x(), groundTop, mark.z(), half * 0.7, height * 0.12, rgb, alpha);
					}
					case MEGACITY -> {
						// A skyline, not a slab: heights vary by position so the outline is ragged.
						for (int i = 0; i < 5; i++) {
							double ang = i * (Math.PI * 2 / 5);
							double ox = mark.x() + Math.cos(ang) * half * 0.55;
							double oz = mark.z() + Math.sin(ang) * half * 0.55;
							double th = height * (0.45 + 0.55 * frac(mark.x() + i * 31, mark.z() + i * 17));
							box(ox, groundTop, oz, half * 0.22, th, rgb, alpha);
						}
						box(mark.x(), groundTop, mark.z(), half * 0.85, height * 0.25, rgb, alpha);
					}
					case INDUSTRIAL_COMPLEX, STATION -> {
						box(mark.x(), groundTop, mark.z(), half * 0.9, height * 0.45, rgb, alpha);
						box(mark.x() + half * 0.4, groundTop, mark.z(), half * 0.18, height, rgb, alpha);
					}
					case IRON_GUILD -> {
						box(mark.x(), groundTop, mark.z(), half * 0.8, height * 0.5, rgb, alpha);
						box(mark.x(), groundTop, mark.z(), half * 0.25, height, rgb, alpha);
					}
					case SCORCHED_TOWN -> {
						box(mark.x(), groundTop, mark.z(), half * 0.9, height * 0.3, rgb, alpha);
						box(mark.x() - half * 0.3, groundTop, mark.z(), half * 0.14, height * 0.8, rgb, alpha);
					}
					case LUNAR_BASE, CRASHED_UFO -> box(mark.x(), groundTop, mark.z(),
							half, height * 0.35, rgb, alpha);
					default -> box(mark.x(), groundTop, mark.z(), half * 0.6, height, rgb, alpha);
				}
				drawn++;
			}
		}

		/** Five faces of an upright box — the bottom is never seen from outside the field. */
		private void box(double cx, double baseY, double cz, double half, double height,
						 int rgb, float alpha) {
			if (full()) return;
			double x0 = cx - half;
			double x1 = cx + half;
			double z0 = cz - half;
			double z1 = cz + half;
			double y0 = baseY;
			double y1 = baseY + Math.max(1.0, height);
			int top = argb(rgb, 1f, alpha);
			int side = argb(rgb, 0.72f, alpha);
			int dark = argb(rgb, 0.55f, alpha);
			quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top);
			quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, side);
			quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, side);
			quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, dark);
			quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, dark);
		}

		/**
		 * Opacity for a point: up out of the real chunks it abuts, down into haze at the horizon.
		 *
		 * <p>Both ends matter. Without the inner ramp the map would start as a hard ring exactly
		 * where vanilla stops drawing ground; without the outer one it would end at a rim in mid-air
		 * — and the outer one is what a horizon actually looks like.
		 */
		private float alphaAt(double distance) {
			float alpha = 1f;
			if (inner > 1.0) {
				double band = inner * 0.2;
				if (distance < inner + band) {
					alpha *= (float) (0.25 + 0.75 * Math.max(0.0, distance - inner) / band);
				}
			}
			double fadeFrom = fieldOuter * 0.55;
			if (distance > fadeFrom) {
				double tail = Math.max(1.0, fieldOuter - fadeFrom);
				alpha *= (float) Math.max(0.0, 1.0 - (distance - fadeFrom) / tail);
			}
			return Math.max(0f, Math.min(1f, alpha));
		}

		private boolean scarred(double worldX, double worldZ) {
			if (scarCells.isEmpty()) return false;
			int cx = Math.floorDiv((int) Math.floor(worldX), 32);
			int cz = Math.floorDiv((int) Math.floor(worldZ), 32);
			return scarCells.contains(((long) cx << 32) ^ (cz & 0xffffffffL));
		}

		/** Writes one quad, compressing each corner along its own sight line. */
		private void quad(double x0, double y0, double z0, double x1, double y1, double z1,
						  double x2, double y2, double z2, double x3, double y3, double z3,
						  int argb) {
			if (full()) return;
			int i = quads * 12;
			project(i, x0, y0, z0);
			project(i + 3, x1, y1, z1);
			project(i + 6, x2, y2, z2);
			project(i + 9, x3, y3, z3);
			colours[quads] = argb;
			quads++;
		}

		private void project(int at, double worldX, double worldY, double worldZ) {
			double dx = worldX - camX;
			double dy = worldY - camY;
			double dz = worldZ - camZ;
			double f = HorizonProjection.factor(dx, dy, dz, inner, reach);
			positions[at] = (float) (dx * f);
			positions[at + 1] = (float) (dy * f);
			positions[at + 2] = (float) (dz * f);
		}
	}

	private static float frac(double a, double b) {
		long h = (long) a * 0x9E3779B97F4A7C15L ^ (long) b * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 33;
		return ((h >>> 11) & 0xFFFFL) / (float) 0xFFFF;
	}

	private static int argb(int rgb, float shade, float alpha) {
		int r = (int) (((rgb >> 16) & 0xFF) * shade);
		int g = (int) (((rgb >> 8) & 0xFF) * shade);
		int b = (int) ((rgb & 0xFF) * shade);
		int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
