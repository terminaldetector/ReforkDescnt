package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.DescentMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sections as files under the world folder, one file each.
 *
 * <p>Not the compact region format the plan eventually wants, and deliberately so for a first
 * backend: a file per section has no index to corrupt, no torn tail to recover from and no
 * compaction pass to get wrong, which matters more right now than the bytes it wastes. A section is
 * six kilobytes before compression and under one after it, so a thoroughly explored world costs tens
 * of megabytes. When that stops being acceptable, a region-packed backend is one more implementation
 * of {@link SectionStorage} and nothing above it changes — which is the whole reason the interface
 * is five methods wide.
 *
 * <p>Writes land in a map first and go to disk on {@link #flush()}, so a busy ingest does not turn
 * into a write per observation.
 */
public final class FileSectionStorage implements SectionStorage {
	private final Path root;
	private final Map<Long, byte[]> pending = new ConcurrentHashMap<>();

	public FileSectionStorage(Path root) {
		this.root = root;
	}

	private Path pathOf(long key) {
		return root.resolve("L" + SectionKey.level(key))
				.resolve(SectionKey.sectionX(key) + "_" + SectionKey.sectionZ(key) + ".bin");
	}

	@Override
	public byte[] read(long key) {
		byte[] queued = pending.get(key);
		if (queued != null) return queued.length == 0 ? null : queued;
		Path path = pathOf(key);
		try {
			if (!Files.exists(path)) return null;
			return Files.readAllBytes(path);
		} catch (IOException e) {
			DescentMod.LOGGER.warn("Section {} unreadable: {}", SectionKey.describe(key), e.toString());
			return null;
		}
	}

	@Override
	public void write(long key, byte[] data) {
		pending.put(key, data);
	}

	@Override
	public void delete(long key) {
		// An empty array is the tombstone: it has to survive in the map until flush, or a delete
		// followed by a read would still find the file that flush has not removed yet.
		pending.put(key, new byte[0]);
	}

	@Override
	public void flush() {
		if (pending.isEmpty()) return;
		for (Map.Entry<Long, byte[]> entry : pending.entrySet()) {
			long key = entry.getKey();
			byte[] data = entry.getValue();
			Path path = pathOf(key);
			try {
				if (data.length == 0) {
					Files.deleteIfExists(path);
				} else {
					Files.createDirectories(path.getParent());
					Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
					Files.write(tmp, data);
					Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				pending.remove(key, data);
			} catch (IOException e) {
				// Keep it pending: the next flush tries again rather than losing the observation.
				DescentMod.LOGGER.warn("Section {} not written: {}", SectionKey.describe(key), e.toString());
			}
		}
	}

	@Override
	public void close() {
		flush();
	}

	public int pendingCount() {
		return pending.size();
	}
}
