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
	public static boolean flightAssist = true;
	public static boolean radar = true;

	private DescentClientState() {}
}
