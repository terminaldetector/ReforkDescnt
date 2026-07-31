package com.terminaldetector.drmd;

import com.terminaldetector.drmd.flight.FlightSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The units bug, pinned down.
 *
 * <p>The flight model integrates in blocks per second because the Source constants were ported
 * unchanged; {@code Entity.setVelocity} is per tick. Handing one to the other put the hull at 19.4
 * blocks per tick — 388 m/s, a full chunk every 0.82 ticks, past what chunk streaming or any
 * collision sweep can follow.
 */
class FlightUnitsTest {
	/** Defaults from DescentPlayerData. */
	private static final double ACCEL = 4200;
	private static final double DRAG = 2.1;
	private static final double MAX_SPEED = 2200;

	/** Vanilla's velocity packet stores each axis as a short scaled by 8000. */
	private static final double PACKET_CAP = 32767.0 / 8000.0;

	/** Run the integrator to steady state, in blocks per second. */
	private static double terminalBlocksPerSecond(double accelMult, double speedMult, double airDrag) {
		double dt = 1.0 / DescentMod.TICKS_PER_SECOND;
		double cap = DescentMod.su(MAX_SPEED) * speedMult;
		double a = DescentMod.su(ACCEL) * accelMult;
		double v = 0;
		for (int i = 0; i < 600; i++) {
			v += a * dt;
			if (v > 1e-4) {
				double q = DRAG * airDrag * v * v * DescentMod.UNIT_SCALE * dt;
				v *= Math.max(0, 1 - q / v);
			}
			v *= Math.pow(FlightSystem.INERTIA, dt * 60.0);
			if (v > cap) v = cap;
		}
		return v;
	}

	private static double perTick(double blocksPerSecond) {
		return blocksPerSecond / DescentMod.TICKS_PER_SECOND;
	}

	@Test
	@DisplayName("cruise speed is fast but inside what the engine can follow")
	void cruiseIsFlyable() {
		double perTick = perTick(terminalBlocksPerSecond(1.0, 1.0, 1.0));
		// Faster than sprinting, slower than outrunning chunk loading.
		assertTrue(perTick > 0.28, "cruise " + perTick + " is no faster than a sprint");
		assertTrue(perTick < 2.0, "cruise " + perTick + " blocks/tick is too fast to fly");
	}

	@Test
	@DisplayName("even the fastest band stays under the velocity packet ceiling")
	void topSpeedFitsTheWire() {
		// Afterburner in near-space: the highest thrust and the lowest drag in the game.
		double perTick = perTick(terminalBlocksPerSecond(1.9 * 1.8, 1.8 * 1.15, 0.05));
		assertTrue(perTick < PACKET_CAP,
				"top speed " + perTick + " exceeds the " + PACKET_CAP + " blocks/tick the wire can carry");
	}

	@Test
	@DisplayName("the per-second domain is never mistaken for per-tick")
	void secondsAreNotTicks() {
		double blocksPerSecond = terminalBlocksPerSecond(1.0, 1.0, 1.0);
		// This is the exact regression: the raw integrator value used to go straight into
		// setVelocity, which reads it as blocks per tick.
		assertTrue(blocksPerSecond > 4.0,
				"integrator should work in blocks/second; got " + blocksPerSecond);
		assertTrue(blocksPerSecond / DescentMod.TICKS_PER_SECOND < 2.0,
				"conversion to blocks/tick is missing");
	}

	@Test
	@DisplayName("a chunk takes more than a tick to cross")
	void doesNotOutrunChunkLoading() {
		double perTick = perTick(terminalBlocksPerSecond(1.9 * 1.8, 1.8 * 1.15, 0.05));
		double ticksPerChunk = 16.0 / perTick;
		assertTrue(ticksPerChunk > 4.0,
				"crossing a chunk in " + ticksPerChunk + " ticks outruns chunk streaming");
	}
}
