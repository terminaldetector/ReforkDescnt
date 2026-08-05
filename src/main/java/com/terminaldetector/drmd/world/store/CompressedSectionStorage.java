package com.terminaldetector.drmd.world.store;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Deflate in front of another backend.
 *
 * <p>The adaptor pattern is half of why the interface is worth having: this one knows nothing about
 * sections, and sections know nothing about it. A surface section compresses well — heights across
 * 32 metres of ground barely change and long runs of cells share a colour — so six kilobytes
 * typically lands near one.
 *
 * <p>The uncompressed length is written in front of the payload so reading needs one allocation of
 * the right size rather than a growing buffer.
 */
public final class CompressedSectionStorage implements SectionStorage {
	private final SectionStorage delegate;

	public CompressedSectionStorage(SectionStorage delegate) {
		this.delegate = delegate;
	}

	@Override
	public byte[] read(long key) {
		byte[] packed = delegate.read(key);
		if (packed == null || packed.length < 4) return null;
		int length = ((packed[0] & 0xFF) << 24) | ((packed[1] & 0xFF) << 16)
				| ((packed[2] & 0xFF) << 8) | (packed[3] & 0xFF);
		if (length < 0 || length > (1 << 24)) return null;
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(packed, 4, packed.length - 4);
			byte[] out = new byte[length];
			int got = inflater.inflate(out);
			return got == length ? out : null;
		} catch (DataFormatException e) {
			return null;
		} finally {
			inflater.end();
		}
	}

	@Override
	public void write(long key, byte[] data) {
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try {
			deflater.setInput(data);
			deflater.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 16);
			out.write(data.length >>> 24);
			out.write(data.length >>> 16);
			out.write(data.length >>> 8);
			out.write(data.length);
			byte[] chunk = new byte[4096];
			while (!deflater.finished()) {
				int n = deflater.deflate(chunk);
				if (n == 0) break;
				out.write(chunk, 0, n);
			}
			delegate.write(key, out.toByteArray());
		} finally {
			deflater.end();
		}
	}

	@Override
	public void delete(long key) {
		delegate.delete(key);
	}

	@Override
	public void flush() {
		delegate.flush();
	}

	@Override
	public void close() {
		delegate.close();
	}
}
