package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Consequence;
import com.terminaldetector.drmd.d6.D6Consequence.Severity;
import com.terminaldetector.drmd.d6.D6ConsequenceMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the world remembers, and the one property the store exists to keep: that two records never
 * describe the same ground.
 */
class D6ConsequenceTest {

	private static final double EPS = 1e-9;

	private static D6Consequence at(Severity severity, double x, double radius, String cause, long tick) {
		return new D6Consequence(severity, new Vec3(x, 0, 0), radius, tick, cause, tick);
	}

	@Test
	@DisplayName("the enclosing sphere of two is exact, not one grown to reach the other")
	void enclosingIsExact() {
		D6Consequence.Sphere s = D6Consequence.enclosing(new Vec3(0, 0, 0), 10, new Vec3(100, 0, 0), 20);

		assertEquals(65, s.radius(), EPS, "half of 100 + 10 + 20");
		assertEquals(55, s.centre().x(), EPS, "on the line, 65 from the far edge of each");
		// The shortcut of keeping the first centre would need radius 120 here — nearly twice as much,
		// and it compounds every time two are folded.
		assertEquals(65, s.centre().minus(new Vec3(0, 0, 0)).length() + 10, EPS, "touches the first");
		assertEquals(65, s.centre().minus(new Vec3(100, 0, 0)).length() + 20, EPS, "and the second");
	}

	@Test
	@DisplayName("one sphere inside another is just the outer one, whichever order")
	void containmentIsHandled() {
		D6Consequence.Sphere a = D6Consequence.enclosing(new Vec3(0, 0, 0), 50, new Vec3(10, 0, 0), 5);
		D6Consequence.Sphere b = D6Consequence.enclosing(new Vec3(10, 0, 0), 5, new Vec3(0, 0, 0), 50);

		assertEquals(50, a.radius(), EPS, "big first");
		assertEquals(0, a.centre().x(), EPS, "and its centre kept");
		assertEquals(50, b.radius(), EPS, "small first");
		assertEquals(0, b.centre().x(), EPS, "same answer");
	}

	@Test
	@DisplayName("folding keeps the worst damage, the earliest date and the latest cause")
	void deepening() {
		D6Consequence old = at(Severity.BURNED, 0, 10, "strike", 100);
		D6Consequence recent = at(Severity.ATTACKED, 5, 10, "raid", 900);

		D6Consequence merged = old.deepenedBy(recent);

		assertEquals(Severity.BURNED, merged.severity(), "damage does not undo");
		assertEquals(100, merged.createdTick(), "when this place started being like this");
		assertEquals("raid", merged.causeType(), "but asked what happened, the answer is the last thing");
	}

	@Test
	@DisplayName("distant consequences stay separate")
	void distantOnesDoNotMerge() {
		D6ConsequenceMap map = new D6ConsequenceMap();
		map.add(at(Severity.ATTACKED, 0, 10, "a", 100));
		map.add(at(Severity.BURNED, 100, 20, "b", 200));

		assertEquals(2, map.size(), "a hundred blocks apart with thirty of reach between them");
	}

	@Test
	@DisplayName("one consequence bridging two others folds all three into one")
	void bridgingMergesTransitively() {
		D6ConsequenceMap map = new D6ConsequenceMap();
		map.add(at(Severity.ATTACKED, 0, 10, "a", 100));
		map.add(at(Severity.BURNED, 100, 20, "b", 200));

		// Reaches both: 50 from each centre, with 45 of its own radius.
		D6Consequence merged = map.add(at(Severity.DESTROYED, 50, 45, "c", 300));

		assertEquals(1, map.size(), "one place, not three");
		assertEquals(Severity.BURNED, merged.severity(), "the worst of the three");
		assertEquals(55, merged.centre().x(), EPS, "enclosing all of them");
		assertEquals(65, merged.radius(), EPS, "and no larger than it has to be");
		assertEquals(100, merged.createdTick(), "since the first of them");
		assertEquals("b", merged.causeType(), "the most recent of what it swallowed");
	}

	@Test
	@DisplayName("after any run of adds, no two records describe the same ground")
	void entriesNeverOverlap() {
		D6ConsequenceMap map = new D6ConsequenceMap();
		// Deliberately a chain that keeps bridging: each reaches the last.
		double[] centres = { 0, 30, 300, 320, 160, 900, 500, 700, 600 };
		for (int i = 0; i < centres.length; i++) {
			map.add(at(Severity.values()[i % Severity.values().length], centres[i], 40, "e" + i, 100 + i));
		}

		List<D6Consequence> all = map.all();
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				assertFalse(all.get(i).overlaps(all.get(j)),
						"entries " + i + " and " + j + " describe the same ground");
			}
		}
		assertTrue(all.size() < centres.length, "and some of them did merge");
	}

	@Test
	@DisplayName("a point has one answer or none")
	void queryByPoint() {
		D6ConsequenceMap map = new D6ConsequenceMap();
		map.add(at(Severity.HAZARDOUS, 0, 50, "reactor", 100));

		D6Consequence here = map.at(new Vec3(40, 0, 0));
		assertNotNull(here, "inside");
		assertEquals(Severity.HAZARDOUS, here.severity(), "and it is the one");
		assertNull(map.at(new Vec3(60, 0, 0)), "outside");
	}

	@Test
	@DisplayName("near finds what an NPC standing there would have heard about")
	void queryByRadius() {
		D6ConsequenceMap map = new D6ConsequenceMap();
		map.add(at(Severity.ATTACKED, 0, 10, "a", 100));
		map.add(at(Severity.BURNED, 500, 10, "b", 200));

		assertEquals(1, map.near(new Vec3(0, 0, 0), 100).size(), "one within a hundred blocks");
		assertEquals(2, map.near(new Vec3(250, 0, 0), 245).size(), "both, from between them");
		assertEquals(0, map.near(new Vec3(-5000, 0, 0), 100).size(), "none from far away");
	}

	@Test
	@DisplayName("at the cap it forgets the mildest and oldest, not the worst")
	void evictionKeepsWhatAHistoryWouldKeep() {
		D6ConsequenceMap map = new D6ConsequenceMap();
		// Ten blocks apart with a radius of one, so none of them merge.
		map.add(at(Severity.HAZARDOUS, 0, 1, "massacre", 0));
		for (int i = 1; i <= D6ConsequenceMap.MAX_ENTRIES; i++) {
			map.add(at(Severity.ATTACKED, i * 10, 1, "scuff" + i, i));
		}

		assertEquals(D6ConsequenceMap.MAX_ENTRIES, map.size(), "capped");
		assertEquals(Severity.HAZARDOUS, map.at(new Vec3(0, 0, 0)).severity(), "the worst survived");
		assertNull(map.at(new Vec3(10, 0, 0)), "the oldest mild one is what went");
	}
}
