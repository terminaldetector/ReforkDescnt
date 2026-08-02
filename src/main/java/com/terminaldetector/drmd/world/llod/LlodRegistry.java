package com.terminaldetector.drmd.world.llod;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Server-side Voxel LLOD catalogue query.
 * Sends compact macro descriptors; client expands LLOD0/1/2 into voxel meshes.
 */
public final class LlodRegistry {
	private LlodRegistry() {}

	public record Silhouette(
		UUID id,
		MacroEntry.Kind kind,
		Vec3d center,
		float radiusX,
		float radiusY,
		float radiusZ,
		LlodLevel level,
		int colorRgb,
		String label,
		long seed
	) {}

	public static List<Silhouette> queryVisible(BlockPos viewer, int max) {
		return queryVisible(viewer, Vec3d.ZERO, max);
	}

	/**
	 * @param velocityBlocksPerTick player flight velocity — biases distance sort along path
	 */
	public static List<Silhouette> queryVisible(BlockPos viewer, Vec3d velocityBlocksPerTick, int max) {
		Vec3d eye = Vec3d.ofCenter(viewer);
		Vec3d foresight = eye.add(velocityBlocksPerTick.multiply(40.0)); // ~2s look-ahead
		List<MacroEntry> near = new ArrayList<>(MacroWorld.all());
		near.sort(Comparator.comparingDouble(e -> score(e, eye, foresight)));

		List<Silhouette> out = new ArrayList<>();
		int llod0 = 0, llod1 = 0, llod2 = 0;
		for (MacroEntry e : near) {
			double d = distanceToAabb(eye, e);
			LlodLevel level = LlodLevel.of(d);
			if (!level.drawsVoxels()) continue;
			// Per-band caps — keep far silhouettes but don't starve mid-bands
			if (level == LlodLevel.LLOD0 && ++llod0 > 14) continue;
			if (level == LlodLevel.LLOD1 && ++llod1 > 18) continue;
			if (level == LlodLevel.LLOD2 && ++llod2 > 22) continue;

			long seed = e.id.getMostSignificantBits() ^ e.id.getLeastSignificantBits()
					^ (((long) e.center.getX()) << 20) ^ e.center.getZ();
			out.add(new Silhouette(
					e.id,
					e.kind,
					Vec3d.ofCenter(e.center),
					Math.max(8f, e.sizeX / 2f),
					Math.max(8f, e.sizeY / 2f),
					Math.max(8f, e.sizeZ / 2f),
					level,
					e.colorRgb,
					e.label,
					seed
			));
			if (out.size() >= max) break;
		}
		return out;
	}

	/** Prefer closer to eye, then closer to foresight point (high-speed path). */
	private static double score(MacroEntry e, Vec3d eye, Vec3d foresight) {
		double dEye = distanceToAabb(eye, e);
		double dFwd = distanceToAabb(foresight, e);
		return dEye * dEye + 0.35 * dFwd * dFwd;
	}

	/** Distance from point to entry AABB (0 if inside) — better than center-only for huge macros. */
	public static double distanceToAabb(Vec3d p, MacroEntry e) {
		Box b = e.bounds();
		double dx = Math.max(0, Math.max(b.minX - p.x, p.x - b.maxX));
		double dy = Math.max(0, Math.max(b.minY - p.y, p.y - b.maxY));
		double dz = Math.max(0, Math.max(b.minZ - p.z, p.z - b.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
