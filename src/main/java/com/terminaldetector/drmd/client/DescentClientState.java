package com.terminaldetector.drmd.client;

/** Mirrored server sync for HUD / camera. */
public final class DescentClientState {
	public static boolean enabled;
	public static float energy = 100, energyMax = 100;
	public static float shield = 100, shieldMax = 100;
	public static float roll;
	public static float speed;
	public static int rocketSub;
	public static String preset = "balanced";
	public static float gravy = 100;
	public static float dashCd;
	public static float gravityFactor;
	public static boolean alwaysRun;
	/** Afterburner accelerator grade 1..4 (traffic light). */
	public static int afterburnerTier = 2;
	public static boolean flightAssist = true;
	public static boolean radar = true;
	/** 0..0.85 local smoke density for HUD / tactics. */
	public static float smokeObscurity;
	/** Descent attitude (client-authoritative while flying). */
	public static float pitch;
	public static boolean attitudeValid;
	public static float attFx, attFy, attFz, attUx, attUy, attUz;
	/** Ship velocity in blocks/tick, straight from the server integrator. */
	public static float velX, velY, velZ;
	/** Flight model constants, mirrored so the client can run the same integrator. */
	public static float accel = 4200f, drag = 2.1f, maxSpeed = 2200f, allocEngines = 0.33f;
	/** On-foot local gravity (torch / generator) — camera + travel. */
	public static boolean footGravity;
	public static float localUx = 0f, localUy = 1f, localUz = 0f;
	/** Holding Mega Beam (FP thick column). Cleared when use stops. */
	public static boolean megaBeamActive;

	private DescentClientState() {}
}
