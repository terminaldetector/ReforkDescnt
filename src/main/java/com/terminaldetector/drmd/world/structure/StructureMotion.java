package com.terminaldetector.drmd.world.structure;

/**
 * Pure motion-interpolation core for a structure rendered as a continuous visual batch rather than
 * moved block by block — zero Minecraft dependency, same idiom as {@link StructureCrash}/
 * {@link StructureDelta}. Deliberately reimplements shortest-path yaw wrapping locally rather than
 * calling {@code MathHelper.lerpAngleDegrees}, so this file stays permanently plain-{@code javac}-
 * compilable rather than only until the first Minecraft-typed call got added to it.
 *
 * <p>Two ticks of server-authoritative state ({@link #interpolate}'s {@code prev}/{@code curr}) bracket
 * every point the client actually renders between them. {@link #fraction} turns local render time
 * (the client's own tick counter plus the frame's partial-tick delta) into where between those two
 * samples "now" actually falls — clamped past 1 rather than snapped back to it, so a late or dropped
 * packet reads as the structure coasting briefly on its last known heading rather than freezing dead
 * the instant a sample is overdue.
 */
public final class StructureMotion {
	/**
	 * How far past the newest sample render time may extrapolate before {@link #fraction} stops
	 * advancing. 1.5 means: once a sample is as overdue as the gap between the two samples it followed
	 * (a dropped packet at the sync rate this is built for), coast on the last heading for that same
	 * span again, then hold — long enough to ride out one missed packet invisibly, short enough that a
	 * genuinely stalled sync does not drift the structure somewhere the next real sample has to
	 * suddenly correct from.
	 */
	public static final double MAX_EXTRAPOLATION = 1.5;

	private StructureMotion() {}

	public record Sample(double x, double y, double z, float yaw, long tick) {}

	/** Blend between two samples. {@code fraction} is expected pre-clamped via {@link #fraction}. */
	public static Sample interpolate(Sample prev, Sample curr, double fraction) {
		double x = lerp(prev.x(), curr.x(), fraction);
		double y = lerp(prev.y(), curr.y(), fraction);
		double z = lerp(prev.z(), curr.z(), fraction);
		float yaw = lerpYaw(prev.yaw(), curr.yaw(), (float) fraction);
		return new Sample(x, y, z, yaw, curr.tick());
	}

	/**
	 * Where "now" falls between {@code prevTick} and {@code currTick}, given the client's own local
	 * tick counter and the frame's partial-tick delta — 0 at {@code prevTick}, 1 at {@code currTick},
	 * clamped to {@link #MAX_EXTRAPOLATION} beyond that rather than left unbounded.
	 */
	public static double fraction(long prevTick, long currTick, long localTick, double tickDelta) {
		double span = Math.max(1.0, (double) (currTick - prevTick));
		double renderTime = localTick + tickDelta;
		double f = (renderTime - prevTick) / span;
		return clamp(f, 0.0, MAX_EXTRAPOLATION);
	}

	private static double lerp(double a, double b, double f) {
		return a + (b - a) * f;
	}

	/** Shortest-path yaw blend in degrees — always turns the short way around, never past 180° either side. */
	private static float lerpYaw(float a, float b, float f) {
		return a + wrapDegrees(b - a) * f;
	}

	/** Folds a degree delta into (-180, 180]. */
	private static float wrapDegrees(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
