package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.planet.PlanetMap;
import com.terminaldetector.drmd.world.store.SurfaceSection;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The distant world as a continuous heightfield, with the things built on it standing up
 * out of that field.
 *
 * <p>The old flight-simulator trick rather than a mesh of real terrain — sample a height per cell,
 * stand a column there, let the steps between neighbours do the shading. Which is also why it is
 * allowed to look blocky: that chunkiness is the point, not an artefact of running out of budget.
 *
 * <p>The heights come from what the server has actually seen ({@code SurfaceStore}) wherever anyone
 * has been, and from the seed's procedural map everywhere else. So the ground a pilot has flown over
 * is drawn as it really is, complete with what they built and what they flattened, and the rest of
 * the planet is still a planet rather than a hole.
 *
 * <p>Two things keep it affordable at kilometre range. Cells are sized <em>by distance</em> — see
 * {@link HorizonGrid}, which owns that rule and is tested directly — so every column covers roughly
 * the same slice of the view however far out it is, and a horizon twelve kilometres away costs about
 * what the ground below the ship costs. And distance itself is compressed by
 * {@link HorizonProjection}, so all of it fits inside the projection's far plane while every point
 * keeps its exact bearing and elevation.
 *
 * <p>Source geometry is built around a nearby origin and rebuilt when the ship leaves that area.
 * Between rebuilds the shader reprojects the same world offsets from the current eye. The renderer
 * this replaces rebuilt up to ten thousand boxes on the render thread every frame.
 */
public final class PlanetSurfaceMesh {
	/** Bounded allocation; fine world-aligned rings emit one sloped quad per cell. */
    private static final int MAX_QUADS = 160_000;
	/** Landmarks drawn, nearest first — the rest are too far apart to tell from terrain. */
	private static final int MAX_LANDMARKS = 96;
	/** How far the ship may drift from the build origin before the field is rebuilt. */
	private static final double ORIGIN_SLACK = 64.0;
	/** And how far it may climb, since the compression is measured from the eye. */
	private static final double ALTITUDE_SLACK = 48.0;

	/** Positions are true world offsets from the build origin; compressed by the shader each frame — see {@link HorizonProjection}. */
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
		Builder b = new Builder(seed, camX, camY, camZ, 0, reach, scarCells,
				positions, colours);

		// Keep source coverage beneath the old eye as well: the current camera's near exclusion
        // is evaluated in the shader, so movement cannot expose an old inner hole.
        // Plan the rings before emitting any: the outermost one has to know where the field ends so
		// it can fade out into it. A ring that stops at full opacity leaves a rim hanging in the air.
		// The sizing rule itself lives in HorizonGrid, where it is directly testable — see that class
		// for why altitude now refines the grid instead of coarsening it.
		double drop = camY - PlanetMap.height(seed, camX, camZ);
		HorizonGrid.Plan plan = HorizonGrid.plan(0, HorizonProjection.MAX_TRUE_RADIUS, drop);
		b.fieldOuter = plan.outer();

		for (int ring = 0; ring < plan.rings(); ring++) {
			b.emitRing(plan.edges()[ring], plan.edges()[ring + 1], plan.cells()[ring]);
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
					// Cover cells intersecting either ring boundary; overlap closes T-junction gaps.
                    double distance=Math.sqrt(d2), halfDiagonal=cell*Math.sqrt(.5);
                    if (distance+halfDiagonal < ringInner || distance-halfDiagonal >= ringOuter) continue;

					// What was actually seen wins over what the seed predicts. Where nobody has been,
					// the procedural map fills in — so the horizon is never a hole, and the parts of
					// it that are real look real.
					int level = PlanetClientState.levelForCell(cell);
					short seen = PlanetClientState.INSTANCE.observedHeight(centreX, centreZ, level);
					boolean known = seen != SurfaceSection.NO_HEIGHT;
					float h = known ? seen : PlanetMap.height(seed, centreX, centreZ);
					boolean water = h < PlanetMap.SEA_LEVEL;
					double top = water ? PlanetMap.SEA_LEVEL : h;
					int rgb;
					if (scarred(centreX, centreZ)) {
						rgb = PlanetMap.scarTint();
					} else if (known) {
						rgb = PlanetClientState.INSTANCE.observedColour(centreX, centreZ, level);
					} else {
						rgb = PlanetMap.tint(seed, centreX, centreZ, h);
					}
					float alpha = alphaAt(Math.sqrt(d2));
					double x1 = x0 + cell;
					double z1 = z0 + cell;

					// A continuous heightfield, not one horizontal box top per cell. Shared
                    // world corners sample the same heights across rebuilds and ring pitches.
                    double y00 = surfaceHeight(x0,z0), y10 = surfaceHeight(x1,z0);
                    double y11 = surfaceHeight(x1,z1), y01 = surfaceHeight(x0,z1);
                    quad(x0,y00,z0,x1,y10,z0,x1,y11,z1,x0,y01,z1,argb(rgb,1f,alpha));

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
			double fadeFrom = fieldOuter * 0.55;
			if (distance > fadeFrom) {
				double tail = Math.max(1.0, fieldOuter - fadeFrom);
				alpha *= (float) Math.max(0.0, 1.0 - (distance - fadeFrom) / tail);
			}
			return Math.max(0f, Math.min(1f, alpha));
		}

        private final java.util.Map<Long,Double> heights = new java.util.HashMap<>();
        private double surfaceHeight(double x,double z) {
            long key=((long)(int)x<<32)^((int)z&0xffffffffL);
            return heights.computeIfAbsent(key,unused -> Math.max(PlanetMap.SEA_LEVEL,
                SurfaceInterpolation.height(x,z,16,(sx,sz) -> {
                    for(int level=0;level<=com.terminaldetector.drmd.world.store.SectionKey.MAX_LEVEL;level++) {
                        short h=PlanetClientState.INSTANCE.observedHeight(sx,sz,level);
                        if(h!=SurfaceSection.NO_HEIGHT)return h;
                    }
                    return PlanetMap.height(seed,sx,sz);
                })));
        }

		/** Neighbour height for a skirt, from the same source the cell itself came from. */
		private double sampleHeight(double worldX, double worldZ, int cell, int level) {
			short seen = PlanetClientState.INSTANCE.observedHeight(worldX, worldZ, level);
			return seen != SurfaceSection.NO_HEIGHT ? seen : PlanetMap.height(seed, worldX, worldZ);
		}

		/** Distance² from the camera to a world XZ position — the same {@code d2} test used to place a
		 *  cell into a ring, reused by the ring-seam skirt check to ask whether a neighbour step leaves
		 *  the ring the current cell is in. */
		private double distSquared(double worldX, double worldZ) {
			double dx = worldX - camX;
			double dz = worldZ - camZ;
			return dx * dx + dz * dz;
		}

		private boolean scarred(double worldX, double worldZ) {
			if (scarCells.isEmpty()) return false;
			int cx = Math.floorDiv((int) Math.floor(worldX), 32);
			int cz = Math.floorDiv((int) Math.floor(worldZ), 32);
			return scarCells.contains(((long) cx << 32) ^ (cz & 0xffffffffL));
		}

		/** Writes source offsets; compression is performed from the current eye in the shader. */
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
			positions[at] = (float) dx;
            positions[at + 1] = (float) dy;
            positions[at + 2] = (float) dz;
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
