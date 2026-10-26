package com.terminaldetector.drmd.world.llod.planet;

/**
 * One planetary map cell — compact overworld surface proxy for orbital / End LLOD.
 */
public final class PlanetCell {
	public static final int CELL = 32;

	public static final int F_EXPLORED = 1;
	public static final int F_SCAR = 2;
	public static final int F_RAIN = 4;
	public static final int F_STORM = 8;

	public final int cx, cz;
	/** Surface height sample (world Y, clamped 0..255 for wire). */
	public final int height;
	/** Packed RGB biome tint. */
	public final int tint;
	public final int flags;

	public PlanetCell(int cx, int cz, int height, int tint, int flags) {
		this.cx = cx;
		this.cz = cz;
		this.height = Math.max(0, Math.min(255, height));
		this.tint = tint & 0xFFFFFF;
		this.flags = flags;
	}

	public static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xffffffffL);
	}

	public long key() {
		return key(cx, cz);
	}

	public boolean explored() { return (flags & F_EXPLORED) != 0; }
	public boolean scarred() { return (flags & F_SCAR) != 0; }
	public boolean raining() { return (flags & F_RAIN) != 0; }
	public boolean storm() { return (flags & F_STORM) != 0; }

	public PlanetCell withFlags(int newFlags) {
		return new PlanetCell(cx, cz, height, tint, newFlags);
	}

	public PlanetCell merge(PlanetCell newer) {
		return new PlanetCell(cx, cz,
				newer.explored() ? newer.height : height,
				newer.explored() ? newer.tint : tint,
				flags | newer.flags);
	}

	public static int cellOf(int block) {
		return Math.floorDiv(block, CELL);
	}

	public static int blockCenter(int cell) {
		return cell * CELL + CELL / 2;
	}
}
