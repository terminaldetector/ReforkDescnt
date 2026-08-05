package com.terminaldetector.drmd.world.store;

/**
 * Where sections live. Five methods, no Minecraft in any of them.
 *
 * <p>Deliberately the shape Voxy's own backend has, because that shape is the reason its storage
 * ports as an idea while its renderer does not: a section is bytes under a key, and everything that
 * makes storage interesting — compression, caching, a database instead of files, a remote store —
 * becomes another implementation rather than a change to the thing above.
 *
 * <p>Nothing here knows what a section contains. {@link SurfaceSection} is the first payload; voxel
 * sections will be the second, in the same store, under keys from the same {@link SectionKey}.
 */
public interface SectionStorage extends AutoCloseable {
	/** Section bytes, or {@code null} when nothing has been stored under this key. */
	byte[] read(long key);

	void write(long key, byte[] data);

	void delete(long key);

	/** Push everything pending to wherever it actually lives. */
	void flush();

	@Override
	void close();
}
