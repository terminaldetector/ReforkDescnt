package com.terminaldetector.drmd.world.llod;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side LLOD query helpers. Distant macros become silhouettes; nearby stay as blocks.
 */
public final class LlodRegistry {
	private LlodRegistry() {}

	public record Silhouette(
		MacroEntry.Kind kind,
		Vec3d center,
		float radiusX,
		float radiusY,
		float radiusZ,
		LlodLevel level,
		int colorRgb,
		String label
	) {}

	public static List<Silhouette> queryVisible(BlockPos viewer, int max) {
		List<MacroEntry> near = new ArrayList<>(MacroWorld.all());
		near.sort(Comparator.comparingDouble(e -> e.distanceSq(viewer)));
		List<Silhouette> out = new ArrayList<>();
		for (MacroEntry e : near) {
			double d = Math.sqrt(e.distanceSq(viewer));
			LlodLevel level = LlodLevel.of(d);
			if (level == LlodLevel.NONE || level == LlodLevel.FULL) continue;
			out.add(new Silhouette(
					e.kind,
					Vec3d.ofCenter(e.center),
					e.sizeX / 2f,
					e.sizeY / 2f,
					e.sizeZ / 2f,
					level,
					e.colorRgb,
					e.label
			));
			if (out.size() >= max) break;
		}
		return out;
	}
}
