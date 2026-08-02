package com.terminaldetector.drmd.client.sync;

import com.terminaldetector.drmd.network.ModNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-side snapshot of facility breaches / meteor falls / last detonation. */
public final class ClientReactorSync {
	public static final ClientReactorSync INSTANCE = new ClientReactorSync();

	private List<ModNetworking.ReactorSyncPayload.Breach> breaches = List.of();
	private List<ModNetworking.ReactorSyncPayload.Fall> falls = List.of();
	private int lastX, lastY, lastZ;
	private long lastGameTime = -1;
	private boolean lastOrbital;

	private ClientReactorSync() {}

	public void apply(ModNetworking.ReactorSyncPayload payload) {
		breaches = Collections.unmodifiableList(new ArrayList<>(payload.breaches()));
		falls = Collections.unmodifiableList(new ArrayList<>(payload.falls()));
		lastX = payload.lastX();
		lastY = payload.lastY();
		lastZ = payload.lastZ();
		lastGameTime = payload.lastGameTime();
		lastOrbital = payload.lastOrbital();
	}

	public void clear() {
		breaches = List.of();
		falls = List.of();
		lastGameTime = -1;
	}

	public List<ModNetworking.ReactorSyncPayload.Breach> breaches() { return breaches; }
	public List<ModNetworking.ReactorSyncPayload.Fall> falls() { return falls; }
	public long lastGameTime() { return lastGameTime; }
	public boolean lastOrbital() { return lastOrbital; }
	public int lastX() { return lastX; }
	public int lastY() { return lastY; }
	public int lastZ() { return lastZ; }

	/** Nearest BREACH timer in seconds, or -1. */
	public int nearestBreachSeconds(double px, double py, double pz) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (var b : breaches) {
			if (!"BREACH".equals(b.phase())) continue;
			double dx = px - (b.x() + 0.5);
			double dy = py - (b.y() + 0.5);
			double dz = pz - (b.z() + 0.5);
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = Math.max(0, b.ticksLeft() / 20);
			}
		}
		return best;
	}
}
