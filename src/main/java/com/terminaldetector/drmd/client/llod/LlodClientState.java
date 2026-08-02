package com.terminaldetector.drmd.client.llod;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.llod.LlodLevel;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client cache of macro descriptors for Voxel LLOD expansion.
 */
public final class LlodClientState {
	public static final LlodClientState INSTANCE = new LlodClientState();

	public record Entry(
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

	private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

	private LlodClientState() {}

	public void set(List<Entry> next) {
		entries.clear();
		entries.addAll(next);
	}

	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public void clear() {
		entries.clear();
	}
}
