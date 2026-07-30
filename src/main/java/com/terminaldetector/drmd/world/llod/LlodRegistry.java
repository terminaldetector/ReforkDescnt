package com.terminaldetector.drmd.world.llod;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.util.math.BlockPos;
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
		List<MacroEntry> near = new ArrayList<>(MacroWorld.all());
		near.sort(Comparator.comparingDouble(e -> e.distanceSq(viewer)));
		List<Silhouette> out = new ArrayList<>();
		int llod0 = 0;
		for (MacroEntry e : near) {
			double d = Math.sqrt(e.distanceSq(viewer));
			LlodLevel level = LlodLevel.of(d);
			if (!level.drawsVoxels()) continue;
			// Cap farthest dense silhouettes so frame stays playable
			if (level == LlodLevel.LLOD0 && ++llod0 > 6) continue;
			long seed = e.id.getMostSignificantBits() ^ e.id.getLeastSignificantBits()
					^ (((long) e.center.getX()) << 20) ^ e.center.getZ();
			out.add(new Silhouette(
					e.id,
					e.kind,
					Vec3d.ofCenter(e.center),
					e.sizeX / 2f,
					e.sizeY / 2f,
					e.sizeZ / 2f,
					level,
					e.colorRgb,
					e.label,
					seed
			));
			if (out.size() >= max) break;
		}
		return out;
	}
}
