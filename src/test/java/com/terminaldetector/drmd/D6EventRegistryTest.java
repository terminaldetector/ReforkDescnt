package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6EventRegistry;
import com.terminaldetector.drmd.d6.D6EventRegistry.Change;
import com.terminaldetector.drmd.d6.D6EventVisibility.Band;
import com.terminaldetector.drmd.d6.D6WorldEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry, and mostly the boundary: when an event has to become real entities and when it can
 * go back to being a record.
 *
 * <p>A five-block craft is a shape out to 500 blocks and is released at 575, which is where the
 * numbers below come from.
 */
class D6EventRegistryTest {

	private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
	private static final Vec3 STILL = new Vec3(0, 0, 0);

	/** A five-block craft sitting at the origin, running from tick 0 to 1000. */
	private static D6WorldEvent craft(long id) {
		return new D6WorldEvent(id, "craft", ORIGIN, STILL, 0, new int[] { 1000 }, 5, 100);
	}

	private static List<Vec3> at(double x) {
		return List.of(new Vec3(x, 0, 0));
	}

	@Test
	@DisplayName("an approaching observer realises the event, a departing one releases it further out")
	void realiseAndRelease() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(craft(1));

		assertTrue(registry.update(at(600), 10, false).isEmpty(), "too far to bother with");
		assertFalse(registry.isRealised(1), "still just a record");

		assertEquals(List.of(1L), registry.update(at(500), 10, false).realised(), "at the shape reach");
		assertTrue(registry.isRealised(1), "now it has to exist");

		assertTrue(registry.update(at(550), 10, false).isEmpty(), "past 500 but inside 575");
		assertTrue(registry.isRealised(1), "kept, because leaving costs more than arriving");

		assertEquals(List.of(1L), registry.update(at(580), 10, false).released(), "past 575");
		assertFalse(registry.isRealised(1), "back to being a record");
	}

	@Test
	@DisplayName("walking back and forth across the inner threshold does not thrash")
	void hysteresisStopsTheThrash() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(craft(1));

		int transitions = 0;
		// The same walk with one threshold instead of two gives three transitions: realise, release,
		// realise — a group of drones spawning and despawning while the player watches.
		for (double distance : new double[] { 600, 499, 501, 499 }) {
			Change change = registry.update(at(distance), 10, false);
			transitions += change.realised().size() + change.released().size();
		}

		assertEquals(1, transitions, "one crossing in, and nothing after it");
		assertTrue(registry.isRealised(1), "and it stayed realised");
	}

	@Test
	@DisplayName("the nearest observer decides, so one player close is enough")
	void nearestObserverWins() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(craft(1));

		List<Vec3> two = List.of(new Vec3(5000, 0, 0), new Vec3(400, 0, 0));
		assertEquals(List.of(1L), registry.update(two, 10, false).realised(), "one of them is close");
	}

	@Test
	@DisplayName("with nobody watching, nothing has to exist")
	void nobodyWatching() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(craft(1));
		registry.update(at(100), 10, false);
		assertTrue(registry.isRealised(1), "realised while watched");

		assertEquals(List.of(1L), registry.update(List.of(), 10, false).released(), "everyone left");
		assertFalse(registry.isRealised(1), "released");
		assertEquals(1, registry.size(), "but the event is still happening");
	}

	@Test
	@DisplayName("an event that has not begun is never realised, however close anyone stands")
	void notYetStarted() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(new D6WorldEvent(1, "later", ORIGIN, STILL, 1000, new int[] { 100 }, 5, 100));

		assertTrue(registry.update(at(0), 0, false).isEmpty(), "standing on top of it, before it starts");
		assertFalse(registry.isRealised(1), "nothing to see yet");
	}

	@Test
	@DisplayName("an event ending releases itself, which is how its end reaches the caller")
	void finishingReleases() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(new D6WorldEvent(1, "brief", ORIGIN, STILL, 0, new int[] { 100 }, 5, 100));

		registry.update(at(50), 50, false);
		assertTrue(registry.isRealised(1), "running and watched");

		assertEquals(List.of(1L), registry.update(at(50), 100, false).released(), "over on tick 100");
		assertFalse(registry.isRealised(1), "released even though the observer never moved");
	}

	@Test
	@DisplayName("finished events are handed back, because that is where a consequence comes from")
	void purgeHandsBackTheFinished() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(new D6WorldEvent(1, "strike", ORIGIN, STILL, 0, new int[] { 100 }, 5, 100));
		registry.add(craft(2));

		registry.update(at(50), 100, false);
		List<D6WorldEvent> finished = registry.purgeFinished(100);

		assertEquals(1, finished.size(), "one of the two ended");
		assertEquals("strike", finished.get(0).type(), "and it is the one that can leave a crater");
		assertEquals(1, registry.size(), "the other is still running");
		assertNull(registry.get(1), "the finished one is gone");
	}

	@Test
	@DisplayName("removing an event drops it from the realised set too")
	void removeIsConsistent() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(craft(1));
		registry.update(at(100), 10, false);
		assertTrue(registry.isRealised(1), "realised first");

		assertTrue(registry.remove(1), "there was such an event");
		assertFalse(registry.isRealised(1), "and it is not realised any more");
		assertFalse(registry.remove(1), "removing it twice says so");
	}

	@Test
	@DisplayName("the band is the best of the observers, not the nearest one's alone")
	void bandTakesTheBest() {
		D6EventRegistry registry = new D6EventRegistry();
		registry.add(craft(1));

		// 800 blocks is a glow for brightness 100 at night; 400 is a shape.
		assertEquals(Band.GLOW, registry.bandFor(1, at(800), 10, false), "one distant observer");
		assertEquals(Band.SHAPE,
				registry.bandFor(1, List.of(new Vec3(800, 0, 0), new Vec3(400, 0, 0)), 10, false),
				"and one who is closer");
		assertEquals(Band.NONE, registry.bandFor(99, at(10), 10, false), "no such event");
	}

	@Test
	@DisplayName("a moving event realises itself by arriving, with the observer standing still")
	void theEventCanBeTheOneMoving() {
		D6EventRegistry registry = new D6EventRegistry();
		// Crossing at ten blocks a tick from 3000 out, passing the observer at the origin.
		registry.add(new D6WorldEvent(1, "flight", new Vec3(0, 0, 3000), new Vec3(0, 0, -10),
				0, new int[] { 400 }, 5, 100));
		List<Vec3> watcher = at(0);

		assertTrue(registry.update(watcher, 200, false).isEmpty(), "still a kilometre out");
		assertEquals(List.of(1L), registry.update(watcher, 260, false).realised(), "400 blocks away");
	}
}
