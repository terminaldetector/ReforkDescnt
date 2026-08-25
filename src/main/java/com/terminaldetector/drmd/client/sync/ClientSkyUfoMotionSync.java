package com.terminaldetector.drmd.client.sync;

import com.terminaldetector.drmd.network.ModNetworking.UfoMotionPayload;
import com.terminaldetector.drmd.world.structure.StructureMotion;
import com.terminaldetector.drmd.world.structure.StructureMotion.Sample;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side cache of the two most recent {@link UfoMotionPayload} samples per Sky UFO entity id —
 * one more {@code client/.../XyzClientState.INSTANCE} singleton fed by a {@code ClientPlayNetworking}
 * receiver, same shape as {@link ClientReactorSync}. A plain {@link HashMap}, not a concurrent one:
 * both the network receiver and the renderer that reads this run on the client thread — the receiver
 * is wrapped in {@code context.client().execute(...)}, same as every other payload handler in
 * {@code DescentClient} — so there is no cross-thread access to guard against, matching
 * {@link ClientReactorSync}'s own plain-collection precedent.
 *
 * <p>Deliberately keyed by entity id rather than holding entity references, and deliberately never
 * consulted by iterating the client world's entity list: {@code SkyUfoEntity} only ever broadcasts a
 * sample while it is actually in virtual flight (see its own {@code broadcastMotion}), so a cached id
 * having a fresh sample at all <em>is</em> the "render the virtual hull here" signal — no separate
 * DataTracker/entity lookup needed to decide who's virtual right now.
 */
public final class ClientSkyUfoMotionSync {
	public static final ClientSkyUfoMotionSync INSTANCE = new ClientSkyUfoMotionSync();

	/**
	 * A UFO that stops broadcasting (destroyed, or genuinely gone quiet) should stop being rendered
	 * rather than hang as a frozen ghost at its last known spot forever — three seconds is generously
	 * longer than the half-rate (~10-tick) broadcast interval a live UFO keeps up, so this only ever
	 * trips once a UFO is actually gone, never as a false hit against normal sync jitter.
	 */
	private static final long STALE_AFTER_TICKS = 60;

	private record Track(Sample prev, Sample curr) {}

	private final Map<Integer, Track> tracks = new HashMap<>();

	private ClientSkyUfoMotionSync() {}

	public void apply(UfoMotionPayload payload) {
		Sample sample = new Sample(payload.x(), payload.y(), payload.z(), payload.yaw(), payload.tick());
		Track existing = tracks.get(payload.entityId());
		// The first sample ever received for an id has nothing to bracket from yet — start both ends
		// at the same point so interpolate() is the identity until a second sample arrives, rather
		// than lerping from some arbitrary default.
		Sample prev = existing != null ? existing.curr() : sample;
		tracks.put(payload.entityId(), new Track(prev, sample));
	}

	public void clear() {
		tracks.clear();
	}

	/** Every entity id with a cached sample right now — the renderer's iteration surface. */
	public Set<Integer> entityIds() {
		return tracks.keySet();
	}

	/**
	 * The interpolated transform to render {@code entityId} at right now, or {@code null} if nothing
	 * has synced yet or the last sample is stale enough that this id should no longer be drawn.
	 */
	public Sample sampleAt(int entityId, long localTick, double tickDelta) {
		Track t = tracks.get(entityId);
		if (t == null) return null;
		if (localTick - t.curr().tick() > STALE_AFTER_TICKS) return null;
		double fraction = StructureMotion.fraction(t.prev().tick(), t.curr().tick(), localTick, tickDelta);
		return StructureMotion.interpolate(t.prev(), t.curr(), fraction);
	}
}
