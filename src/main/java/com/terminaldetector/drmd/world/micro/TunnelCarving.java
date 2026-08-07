package com.terminaldetector.drmd.world.micro;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Rounds a carved tunnel's boundary at quarter-cell precision instead of leaving it a whole-block
 * silhouette — first for a fixed vertical shaft, then for however a player or machine actually digs
 * one ({@link #carveCapsule}, behind {@code WeaponFx.drillTunnel}: the tunnel drill rig and the
 * engineer's own boring tool both go through it).
 *
 * <p>A circle rasterized by testing whole blocks (a Descent shaft's own carve does exactly this —
 * {@code dx*dx+dz*dz <= r*r} at integer offsets) has no notion of a block being partly in and partly
 * out: every boundary block is either fully air or fully left solid, so the "circle" is really an
 * octagon-ish blob whose corners are a whole block wide. At {@code WorldLevels.SHAFT_RADIUS} = 3 that
 * corner is a third of the tunnel's own radius — big enough that a 6DoF ship reads it as a snag, not
 * a curve. This carves those corners at {@link MicroGrid}'s quarter-cell resolution, the same
 * granularity {@code BlockDamage} already uses for combat, and through the same block —
 * {@link CarvedBlock} — rather than a second, parallel one: this mod's own microblock doc already
 * frames a carved corner and a shot-off corner as one operation on one representation, and a
 * generation- or tool-authored one is no different from a combat-authored one once it exists.
 *
 * <p>Only the boundary is ever touched this way. Cells a true shape still fully contains are left to
 * whichever fast path already clears whole blocks ({@code cutDescentShaft}'s own loop, or the plain
 * {@code breakBlock} call in {@link #carveCapsule} for a block the capsule swallows entirely) —
 * {@link CarvedBlock} only appears where a block is genuinely partial. Rendering is real geometry, not
 * a texture cheat, but it is still the source block's own model stretched over the merged shape
 * ({@link com.terminaldetector.drmd.client.render.CarvedBlockRenderer}) — true diagonal faces are not
 * what this buys, a finer staircase is. That reads as a chamfered, quarter-block-precision edge at
 * these radii; it is not yet the sloped/polygonal face a much wider bore would need to look
 * convincingly round, which is future work, not silently dropped scope.
 */
public final class TunnelCarving {
	/** Half a unit cube's space diagonal — how far a corner can sit from the block's own centre. */
	private static final double HALF_DIAGONAL = Math.sqrt(3.0) / 2.0;

	private TunnelCarving() {}

	/**
	 * Chamfer the boundary of a vertical cylindrical tunnel already carved to whole-block precision.
	 *
	 * @param centerX  tunnel axis X
	 * @param centerZ  tunnel axis Z
	 * @param radius   the same radius the whole-block pass carved to
	 * @param y0       one end of the tunnel's Y range (either order)
	 * @param y1       the other end
	 * @return blocks turned into {@link CarvedBlock}s
	 */
	public static int carveBoundaryRing(ServerWorld world, int centerX, int centerZ, double radius, int y0, int y1) {
		int yLo = Math.min(y0, y1);
		int yHi = Math.max(y0, y1);
		int outer = (int) Math.ceil(radius) + 1;
		int written = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				// The pattern is a pure function of (dx, dz) — the tunnel is a vertical cylinder, so
				// every Y level wants the identical mask. Computed once per column, not once per block.
				long mask = circleMask(dx, dz, radius);
				if (mask == MicroGrid.FULL || mask == MicroGrid.EMPTY) continue;

				int x = centerX + dx;
				int z = centerZ + dz;
				for (int y = yLo; y <= yHi; y++) {
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (state.isAir()) continue;
					if (CarvedBlock.replace(world, pos, state, mask) != null) written++;
				}
			}
		}
		return written;
	}

	/** Which of a boundary block's 64 cells a true circle of this radius still covers, from its centre. */
	private static long circleMask(int dx, int dz, double radius) {
		double r2 = radius * radius;
		return MicroGrid.build((cx, cy, cz) -> {
			double wx = dx + (cx + 0.5) * MicroGrid.CELL_SIZE;
			double wz = dz + (cz + 0.5) * MicroGrid.CELL_SIZE;
			return wx * wx + wz * wz > r2; // kept solid: outside the true circle
		});
	}

	/**
	 * Carve the capsule (a sphere of {@code radius} swept along the segment {@code segStart}→
	 * {@code segEnd}) that a drill bore actually cuts — {@code WeaponFx.drillTunnel}'s previous
	 * shape was the same union of spheres, just tested per whole block instead of per quarter-cell.
	 *
	 * <p>Unlike the vertical shaft, a drill's axis is not fixed to one plane — the engineer's own
	 * tool fires along the player's full look vector, pitch included — so this cannot reduce to a
	 * 2D circle the way {@link #carveBoundaryRing} does; every candidate block is measured against
	 * the segment directly (point-to-segment distance, clamped to the segment's own ends, which is
	 * exactly what makes the swept-sphere shape a capsule and not an infinite cylinder).
	 *
	 * <p>Three outcomes per block, cheapest test first: outside the capsule even at its nearest
	 * corner (skip), inside it even at its farthest corner (the existing fast path — a plain
	 * {@code breakBlock}, so drops, XP and hardness feel exactly as they did before this), or neither
	 * (quarter-cell mask via {@link CarvedBlock}, same as the shaft boundary).
	 *
	 * @return blocks removed or carved, matching what the caller's own "N blocks bored" count meant before
	 */
	public static int carveCapsule(ServerWorld world, Vec3d segStart, Vec3d segEnd, double radius,
									LivingEntity breaker) {
		int minX = (int) Math.floor(Math.min(segStart.x, segEnd.x) - radius - 1);
		int maxX = (int) Math.floor(Math.max(segStart.x, segEnd.x) + radius + 1);
		int minY = (int) Math.floor(Math.min(segStart.y, segEnd.y) - radius - 1);
		int maxY = (int) Math.floor(Math.max(segStart.y, segEnd.y) + radius + 1);
		int minZ = (int) Math.floor(Math.min(segStart.z, segEnd.z) - radius - 1);
		int maxZ = (int) Math.floor(Math.max(segStart.z, segEnd.z) + radius + 1);

		double r2 = radius * radius;
		int carved = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					double centerDist = Math.sqrt(distSqToSegment(x + 0.5, y + 0.5, z + 0.5, segStart, segEnd));
					if (centerDist > radius + HALF_DIAGONAL) continue; // no corner can reach the capsule

					pos.set(x, y, z);
					if (!isBoreable(world, pos)) continue;

					if (centerDist < radius - HALF_DIAGONAL) {
						if (world.breakBlock(pos.toImmutable(), true, breaker)) carved++;
						continue;
					}

					long mask = capsuleMask(x, y, z, segStart, segEnd, r2);
					if (mask == MicroGrid.FULL) continue;
					if (mask == MicroGrid.EMPTY) {
						if (world.breakBlock(pos.toImmutable(), true, breaker)) carved++;
						continue;
					}
					BlockState state = world.getBlockState(pos);
					if (CarvedBlock.replace(world, pos, state, mask) != null) carved++;
				}
			}
		}
		return carved;
	}

	/** Mirrors the hardness/bedrock gate {@code WeaponFx.drillTunnel} already applied. */
	private static boolean isBoreable(ServerWorld world, BlockPos pos) {
		if (world.isOutOfHeightLimit(pos)) return false;
		BlockState state = world.getBlockState(pos);
		if (state.isAir()) return false;
		float hardness = state.getHardness(world, pos);
		return hardness >= 0 && hardness < 50 && !state.isOf(Blocks.BEDROCK);
	}

	private static long capsuleMask(int blockX, int blockY, int blockZ, Vec3d segStart, Vec3d segEnd, double r2) {
		return MicroGrid.build((cx, cy, cz) -> {
			double wx = blockX + (cx + 0.5) * MicroGrid.CELL_SIZE;
			double wy = blockY + (cy + 0.5) * MicroGrid.CELL_SIZE;
			double wz = blockZ + (cz + 0.5) * MicroGrid.CELL_SIZE;
			return distSqToSegment(wx, wy, wz, segStart, segEnd) > r2; // kept solid: outside the capsule
		});
	}

	/** Squared distance from a world point to the segment {@code a}→{@code b}, clamped to its ends. */
	private static double distSqToSegment(double px, double py, double pz, Vec3d a, Vec3d b) {
		double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
		double abLenSq = abx * abx + aby * aby + abz * abz;
		double t = abLenSq < 1e-9 ? 0.0
				: ((px - a.x) * abx + (py - a.y) * aby + (pz - a.z) * abz) / abLenSq;
		t = Math.max(0.0, Math.min(1.0, t));
		double cx = a.x + abx * t, cy = a.y + aby * t, cz = a.z + abz * t;
		double dx = px - cx, dy = py - cy, dz = pz - cz;
		return dx * dx + dy * dy + dz * dz;
	}
}
