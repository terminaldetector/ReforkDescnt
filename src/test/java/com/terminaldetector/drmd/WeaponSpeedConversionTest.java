package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@code WeaponCore.fireProjectile}'s speed conversion ({@code ServerWorld}/{@code PlayerEntity}
 * are unavailable here, same mirroring approach as {@code FlightScrapeDampingTest}). Pins the fix: a
 * weapon's {@code speed} field and a piloted ship's inherited flight velocity both start life as
 * blocks-*per-second* (Source/Descent's own convention, and the flight model's own internal one — see
 * {@code ServerPlayerFlightTravelMixin}, which divides the identical {@code getFlightVelocity()} by
 * {@code TICKS_PER_SECOND} at its own hand-off), but {@code ProjectileEntity.tick} adds the result to
 * position once per *tick*. Missing that conversion made every round travel {@code TICKS_PER_SECOND}
 * (20×) its intended speed — confirmed against the Descent source (docs/WEAPON_FX.md): a blob billboard
 * is a fixed-size sprite with no engine-level speed/distance scaling, so nothing in the original masked
 * an overly fast round the way this port's stretch/tracer machinery incidentally did.
 */
class WeaponSpeedConversionTest {
	private static final double UNIT_SCALE = 1.0 / 80.0;
	private static final double TICKS_PER_SECOND = 20.0;

	/** Mirrors the fixed WeaponCore.fireProjectile: su(speed) / TICKS_PER_SECOND. */
	private static double perTickSpeed(double sourceUnitsPerSecond) {
		return sourceUnitsPerSecond * UNIT_SCALE / TICKS_PER_SECOND;
	}

	@Test
	@DisplayName("the laser's 6200 su/s converts to about 3.9 blocks/tick, not about 77")
	void laserSpeedIsPerTickNotPerSecond() {
		assertEquals(3.875, perTickSpeed(6200), 1e-9);
	}

	@Test
	@DisplayName("the fixed conversion is exactly 20x smaller than the old (buggy) su()-only value")
	void fixedConversionIsTwentyTimesSmallerThanBefore() {
		double buggyPerTick = 6200 * UNIT_SCALE; // the old, un-tick-converted value
		double fixedPerTick = perTickSpeed(6200);
		assertEquals(TICKS_PER_SECOND, buggyPerTick / fixedPerTick, 1e-9);
	}

	@Test
	@DisplayName("over a 5s (100-tick) lifetime, a laser now travels a plausible travel-time-bolt range")
	void lifetimeRangeIsPlausibleNotAbsurd() {
		double range = perTickSpeed(6200) * 100; // life=5f -> 100 ticks, matching WeaponCore.setLifeTicks
		assertTrue(range > 50 && range < 1000,
				"expected a few hundred blocks of range for a 5s laser lifetime, got " + range);
	}

	@Test
	@DisplayName("a piloted ship's per-second flight velocity converts with the same /TICKS_PER_SECOND factor")
	void shipVelocityInheritanceUsesSameConversion() {
		double flightBlocksPerSecond = 40.0; // representative cruise speed, blocks/s (docs/MOVEMENT.md)
		double perTick = flightBlocksPerSecond / TICKS_PER_SECOND;
		assertEquals(2.0, perTick, 1e-9);
	}

	@Test
	@DisplayName("zero speed converts to zero regardless of scale, never NaN or negative")
	void zeroSpeedStaysZero() {
		assertEquals(0.0, perTickSpeed(0), 1e-9);
	}
}
