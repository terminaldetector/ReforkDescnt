package com.terminaldetector.drmd.world.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sections in a map. What the client uses, and what the tests run against. */
public final class MemorySectionStorage implements SectionStorage {
	private final Map<Long, byte[]> sections = new ConcurrentHashMap<>();

	@Override
	public byte[] read(long key) {
		return sections.get(key);
	}

	@Override
	public void write(long key, byte[] data) {
		sections.put(key, data);
	}

	@Override
	public void delete(long key) {
		sections.remove(key);
	}

	@Override
	public void flush() {
		// Nothing behind this one.
	}

	@Override
	public void close() {
		sections.clear();
	}

	public int size() {
		return sections.size();
	}
}
