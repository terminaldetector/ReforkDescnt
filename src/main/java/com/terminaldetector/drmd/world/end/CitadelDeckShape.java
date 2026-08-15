package com.terminaldetector.drmd.world.end;

/**
 * Pure geometry for the Citadel-style End reactor station — zero Minecraft imports, directly unit-testable
 * (mirrors the {@code SkyUfoShape}/{@code SkyUfoShapeTest} idiom, itself mirroring {@code AerisDensity}).
 * A uniform-footprint cube: same horizontal half-extent at every level, six decks stacked bottom-to-top,
 * one central open shaft running the full height so every deck connects to every other without a
 * privileged "the only way up" corridor.
 */
public final class CitadelDeckShape {
	/** Horizontal half-extent of the station's outer hull, in blocks — a 73x73 footprint. */
	public static final int HALF_EXTENT = 36;
	/** Radius of the central open shaft, shared by every deck. */
	public static final int SHAFT_RADIUS = 10;

	private CitadelDeckShape() {}

	public enum Deck { REACTOR_CORE, ENGINEERING, STORAGE, LABS, COMMAND, FLIGHT, NONE }

	public enum Cell { HULL, GLASS, SHAFT_AIR, ROOM_AIR, DECK_FLOOR, WALL_INTERIOR, NONE }

	private record DeckRange(Deck deck, int floorY, int topY) {}

	/**
	 * Bottom to top, boss chamber at the base — mirrors both a dungeon-crawl pacing (overview on entry,
	 * confrontation at the end) and the source game's own elevator-shaft descent structure. Contiguous
	 * ranges (each floorY == the previous topY): no gaps, no overlaps, nothing needs building below
	 * REACTOR_CORE's own floor.
	 */
	private static final DeckRange[] DECKS = {
			new DeckRange(Deck.REACTOR_CORE, 0, 32),
			new DeckRange(Deck.ENGINEERING, 32, 54),
			new DeckRange(Deck.STORAGE, 54, 74),
			new DeckRange(Deck.LABS, 74, 98),
			new DeckRange(Deck.COMMAND, 98, 120),
			new DeckRange(Deck.FLIGHT, 120, 148),
	};

	/** Which deck a local Y (relative to the station's own floor) belongs to, or {@code NONE} outside the stack. */
	public static Deck deckAt(int localY) {
		for (DeckRange r : DECKS) {
			if (localY >= r.floorY() && localY < r.topY()) return r.deck();
		}
		return Deck.NONE;
	}

	public static int deckFloorY(Deck deck) {
		for (DeckRange r : DECKS) if (r.deck() == deck) return r.floorY();
		throw new IllegalArgumentException("no range for " + deck);
	}

	public static int deckTopY(Deck deck) {
		for (DeckRange r : DECKS) if (r.deck() == deck) return r.topY();
		throw new IllegalArgumentException("no range for " + deck);
	}

	/**
	 * Classify a local cell (relative to the station's own center/floor origin). The shaft always wins
	 * over everything else — it runs open through every deck's own floor, which is what makes the whole
	 * stack read as one connected structure rather than six sealed rooms. The outer hull (with a glass
	 * seam pattern, same modulo idiom as {@code SkyUfoShape}'s) sits at the exact footprint boundary; a
	 * one-block wall backing sits just inside it, giving the hull real thickness rather than a paper-thin
	 * shell; a deck's own floor plate only appears off the shaft and off the wall.
	 */
	public static Cell classify(int x, int y, int z) {
		Deck deck = deckAt(y);
		if (deck == Deck.NONE) return Cell.NONE;

		int ax = Math.abs(x), az = Math.abs(z);
		if (ax > HALF_EXTENT || az > HALF_EXTENT) return Cell.NONE;

		if (x * x + z * z <= SHAFT_RADIUS * SHAFT_RADIUS) return Cell.SHAFT_AIR;

		boolean onOuterFace = ax == HALF_EXTENT || az == HALF_EXTENT;
		if (onOuterFace) {
			return (x + z) % 9 == 0 ? Cell.GLASS : Cell.HULL;
		}

		boolean inWallBacking = ax == HALF_EXTENT - 1 || az == HALF_EXTENT - 1;
		if (inWallBacking) return Cell.WALL_INTERIOR;

		if (y == deckFloorY(deck)) return Cell.DECK_FLOOR;
		return Cell.ROOM_AIR;
	}
}
