package com.terminaldetector.drmd.flight;

import net.minecraft.util.math.MathHelper;

/**
 * Four afterburner / accelerator grades — traffic-light graduation.
 *
 * <pre>
 *   1 GREEN  — eco cruise
 *   2 YELLOW — stock Descent burn
 *   3 ORANGE — hot
 *   4 RED    — max burn
 * </pre>
 *
 * Engine allocation still scales inside each tier (Interceptor vs Siege).
 */
public final class AfterburnerTiers {
	public static final int MIN = 1;
	public static final int MAX = 4;
	public static final int DEFAULT = 2;

	private AfterburnerTiers() {}

	public static int clamp(int tier) {
		return MathHelper.clamp(tier, MIN, MAX);
	}

	/** Energy drain while holding R (units / second from the shared pool). */
	public static float costPerSec(int tier) {
		return switch (clamp(tier)) {
			case 1 -> 4.0f;
			case 2 -> 6.0f;
			case 3 -> 9.0f;
			default -> 14.0f;
		};
	}

	/** Acceleration multiplier range at engAlloc 0..1. */
	public static float accelMult(int tier, float engAlloc) {
		float a = MathHelper.clamp(engAlloc, 0f, 1f);
		return switch (clamp(tier)) {
			case 1 -> MathHelper.lerp(a, 1.15f, 1.45f);
			case 2 -> MathHelper.lerp(a, 1.35f, 2.05f);
			case 3 -> MathHelper.lerp(a, 1.55f, 2.40f);
			default -> MathHelper.lerp(a, 1.85f, 2.85f);
		};
	}

	/** Max-speed multiplier range at engAlloc 0..1. */
	public static float speedMult(int tier, float engAlloc) {
		float a = MathHelper.clamp(engAlloc, 0f, 1f);
		return switch (clamp(tier)) {
			case 1 -> MathHelper.lerp(a, 1.12f, 1.40f);
			case 2 -> MathHelper.lerp(a, 1.35f, 1.95f);
			case 3 -> MathHelper.lerp(a, 1.50f, 2.25f);
			default -> MathHelper.lerp(a, 1.70f, 2.60f);
		};
	}

	/** Extra FOV when burning (degrees). */
	public static float fovBoost(int tier) {
		return switch (clamp(tier)) {
			case 1 -> 3f;
			case 2 -> 6f;
			case 3 -> 9f;
			default -> 12f;
		};
	}

	/** ARGB HUD / button color — traffic light. */
	public static int colorArgb(int tier) {
		return switch (clamp(tier)) {
			case 1 -> 0xFF44CC66; // green
			case 2 -> 0xFFE8C84A; // yellow
			case 3 -> 0xFFFF8C33; // orange
			default -> 0xFFFF4444; // red
		};
	}

	public static String colorName(int tier) {
		return switch (clamp(tier)) {
			case 1 -> "green";
			case 2 -> "yellow";
			case 3 -> "orange";
			default -> "red";
		};
	}

	public static String shortLabel(int tier) {
		return switch (clamp(tier)) {
			case 1 -> "G1";
			case 2 -> "Y2";
			case 3 -> "O3";
			default -> "R4";
		};
	}
}
