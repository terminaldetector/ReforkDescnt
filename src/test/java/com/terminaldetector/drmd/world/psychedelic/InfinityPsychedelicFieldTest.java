package com.terminaldetector.drmd.world.psychedelic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

import static com.terminaldetector.drmd.world.psychedelic.InfinityPsychedelicField.Domain.ABSTRACT_EXPANSE;
import static com.terminaldetector.drmd.world.psychedelic.InfinityPsychedelicField.Domain.CUBE_DOMINION;
import static com.terminaldetector.drmd.world.psychedelic.InfinityPsychedelicField.Domain.FRACTAL_DEPTHS;
import static com.terminaldetector.drmd.world.psychedelic.InfinityPsychedelicField.Domain.FRACTURE_TRANSITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfinityPsychedelicFieldTest {
	private record Point(int x, int y, int z) {}

	@Test
	void routeRemainsOpenThroughEveryFormationAndBeyondTheStartingCube() {
		InfinityPsychedelicField field = new InfinityPsychedelicField(90125);
		assertEquals(CUBE_DOMINION, field.sample(0, 90, 0).domain());
		assertEquals(FRACTURE_TRANSITION, field.sample(944, 90, 0).domain());
		assertEquals(FRACTAL_DEPTHS, field.sample(1680, 90, 0).domain());
		assertEquals(ABSTRACT_EXPANSE, field.sample(3200, 90, 0).domain());
		for (int x = -6000; x <= 6000; x += 13) {
			assertTrue(field.density(x, 84, -8) < 0, "Lower side of launch route at x=" + x);
			assertTrue(field.density(x, 98, 8) < 0, "Upper side of launch route at x=" + x);
		}
	}

	@Test
	void samplesStayDeterministicAcrossCubeOrderAndThreads() {
		InfinityPsychedelicField field = new InfinityPsychedelicField(154899);
		InfinityPsychedelicField reload = new InfinityPsychedelicField(154899);
		InfinityPsychedelicField anotherSeed = new InfinityPsychedelicField(154900);
		List<Point> locations = new ArrayList<>();
		SplittableRandom random = new SplittableRandom(527);
		for (int i = 0; i < 768; i++) {
			// Group adjacent positions across signed cube boundaries. One group is at huge-world altitude.
			locations.add(new Point(1024 + random.nextInt(256), -96 + random.nextInt(32), -96 + random.nextInt(32)));
			locations.add(new Point(random.nextInt(-1024, 1024), 490_000 + random.nextInt(64), random.nextInt(-1024, 1024)));
		}
		locations.add(new Point(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
		var expected = locations.stream().map(p -> field.sample(p.x(), p.y(), p.z())).toList();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < locations.size(); i++) order.add(i);
		Collections.shuffle(order, new Random(678));
		order.parallelStream().forEach(i -> {
			Point p = locations.get(i);
			var sample = reload.sample(p.x(), p.y(), p.z());
			assertEquals(expected.get(i), sample);
			assertTrue(Double.isFinite(sample.density()));
		});
		assertTrue(locations.stream().anyMatch(p -> field.density(p.x(), p.y(), p.z())
				!= anotherSeed.density(p.x(), p.y(), p.z())), "Seed should vary occupancy geometry");
	}

	@Test
	void neighbouringVerticalSamplesContainAirAndMultipleSolidLayers() {
		InfinityPsychedelicField field = new InfinityPsychedelicField(1802406);
		int stackedColumns = 0;
		int solid = 0;
		int total = 0;
		for (int x = 1402; x <= 1662; x += 20) {
			for (int z = 391; z <= 631; z += 20) {
				int layers = 0;
				boolean previousSolid = false;
				for (int y = -1300; y <= 1700; y += 8) {
					boolean occupied = field.density(x, y, z) > 0;
					if (occupied && !previousSolid) layers++;
					if (occupied) solid++;
					total++;
					previousSolid = occupied;
				}
				if (layers >= 2) stackedColumns++;
			}
		}
		assertTrue(stackedColumns > 50, "Several independent surfaces per X/Z column");
		assertTrue(solid > total / 30 && solid < total * 4 / 5, "The field has substantial solids and navigable space");
	}
}
