package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.end.CitadelDeckShape;
import com.terminaldetector.drmd.world.end.CitadelDeckShape.Cell;
import com.terminaldetector.drmd.world.end.CitadelDeckShape.Deck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link CitadelDeckShape} directly — pure geometry, mirrors the {@code SkyUfoShape}/
 * {@code SkyUfoShapeTest} idiom.
 */
class CitadelDeckShapeTest {
	@Test
	@DisplayName("deckAt partitions every local Y across all six decks with no gaps or overlaps")
	void deckAtPartitionsFullHeightWithNoGaps() {
		int lowest = CitadelDeckShape.deckFloorY(Deck.REACTOR_CORE);
		int highest = CitadelDeckShape.deckTopY(Deck.FLIGHT);
		for (int y = lowest; y < highest; y++) {
			assertNotEquals(Deck.NONE, CitadelDeckShape.deckAt(y), "y=" + y + " should belong to some deck");
		}
		assertEquals(Deck.NONE, CitadelDeckShape.deckAt(lowest - 1), "below the lowest deck should be NONE");
		assertEquals(Deck.NONE, CitadelDeckShape.deckAt(highest), "at/above the highest deck's top should be NONE");
	}

	@Test
	@DisplayName("every deck's own floor/top bounds are internally consistent with deckAt")
	void deckBoundsAgreeWithDeckAt() {
		for (Deck d : Deck.values()) {
			if (d == Deck.NONE) continue;
			int floor = CitadelDeckShape.deckFloorY(d);
			int top = CitadelDeckShape.deckTopY(d);
			assertTrue(floor < top, d + ": floor should be below top");
			assertEquals(d, CitadelDeckShape.deckAt(floor), d + ": deckAt(floor) should be this deck");
			assertEquals(d, CitadelDeckShape.deckAt(top - 1), d + ": deckAt(top-1) should be this deck");
		}
	}

	@Test
	@DisplayName("the shaft column stays open at every deck's own floor and mid-height")
	void shaftColumnStaysOpenAtEveryDeck() {
		for (Deck d : Deck.values()) {
			if (d == Deck.NONE) continue;
			int floor = CitadelDeckShape.deckFloorY(d);
			int mid = (floor + CitadelDeckShape.deckTopY(d)) / 2;
			assertEquals(Cell.SHAFT_AIR, CitadelDeckShape.classify(0, floor, 0), d + " floor, shaft column");
			assertEquals(Cell.SHAFT_AIR, CitadelDeckShape.classify(0, mid, 0), d + " mid-height, shaft column");
		}
	}

	@Test
	@DisplayName("every Cell kind occurs at a hand-verified representative point")
	void everyCellKindOccursAtAWitnessPoint() {
		int half = CitadelDeckShape.HALF_EXTENT;
		int floorY = CitadelDeckShape.deckFloorY(Deck.REACTOR_CORE);
		int y = floorY + 5; // mid-room height, off the floor plate

		assertEquals(Cell.NONE, CitadelDeckShape.classify(0, floorY - 1, 0),
				"below the lowest deck should be NONE");
		assertEquals(Cell.SHAFT_AIR, CitadelDeckShape.classify(0, y, 0),
				"shaft column should be open");
		assertEquals(Cell.ROOM_AIR, CitadelDeckShape.classify(half / 2, y, 0),
				"mid-room, off the shaft and off the wall, should be open");
		assertEquals(Cell.WALL_INTERIOR, CitadelDeckShape.classify(half - 1, y, 0),
				"just inside the outer face should be solid wall backing");
		// (x + z) % 9 == 0 at the outer face -> glass; z=1 breaks that parity at the same x -> hull.
		assertEquals(Cell.GLASS, CitadelDeckShape.classify(half, y, 0),
				"outer face at the glass-parity offset should be glass");
		assertEquals(Cell.HULL, CitadelDeckShape.classify(half, y, 1),
				"outer face off the glass-parity offset should be hull");
		assertEquals(Cell.DECK_FLOOR, CitadelDeckShape.classify(half / 2, floorY, 0),
				"a deck's own floor Y, off the shaft, should be a walkable floor plate");
	}

	@Test
	@DisplayName("classify and deckAt are deterministic")
	void isDeterministic() {
		assertEquals(CitadelDeckShape.classify(10, 40, -5), CitadelDeckShape.classify(10, 40, -5));
		assertEquals(CitadelDeckShape.deckAt(40), CitadelDeckShape.deckAt(40));
	}
}
