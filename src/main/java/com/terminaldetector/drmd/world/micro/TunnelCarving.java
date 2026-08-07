package com.terminaldetector.drmd.world.micro;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Rounds a carved vertical tunnel's boundary at quarter-cell precision instead of leaving it a
 * whole-block silhouette.
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
 * worldgen-authored one is no different from a combat-authored one once it exists.
 *
 * <p>Only the boundary ring is touched. Blocks a true circle at this resolution still fully contains
 * are left to whatever whole-block pass already cleared them (they read {@link MicroGrid#EMPTY} here
 * and are skipped); blocks it does not reach at all read {@link MicroGrid#FULL} and are left exactly
 * as generated. Rendering is real geometry, not a texture cheat, but it is still the source block's
 * own model stretched over the merged shape ({@link com.terminaldetector.drmd.client.render.CarvedBlockRenderer}) —
 * true diagonal faces are not what this buys, a finer staircase is. That reads as a chamfered,
 * quarter-block-precision edge at this scale; it is not yet the sloped/polygonal face a much wider
 * bore would need to look convincingly round, which is future work, not silently dropped scope.
 */
public final class TunnelCarving {
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
				long mask = boundaryMask(dx, dz, radius);
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
	private static long boundaryMask(int dx, int dz, double radius) {
		double r2 = radius * radius;
		return MicroGrid.build((cx, cy, cz) -> {
			double wx = dx + (cx + 0.5) * MicroGrid.CELL_SIZE;
			double wz = dz + (cz + 0.5) * MicroGrid.CELL_SIZE;
			return wx * wx + wz * wz > r2; // kept solid: outside the true circle
		});
	}
}
