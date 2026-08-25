package com.terminaldetector.drmd.client.sync;

import com.terminaldetector.drmd.network.ModNetworking.UfoMotionPayload;
import com.terminaldetector.drmd.world.structure.StructureMotion;
import com.terminaldetector.drmd.world.structure.StructureMotion.Sample;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of the two most recent {@link UfoMotionPayload} samples per Sky UFO entity id —
 * one more {@code client/.../XyzClientState.INSTANCE} singleton fed by a {@code ClientPlayNetworking}
 * receiver, same shape as {@link ClientReactorSync}. Deliberately keyed by entity id rather than
 * holding entity references: nothing here needs the local client {@code Entity} object at all, only
 * the two brackets {@link StructureMotion#interpolate} renders between.
 */
public final class ClientSkyUfoMotionSync {
	public static final ClientSkyUfoMotionSync INSTANCE = new ClientSkyUfoMotionSync();

	private record Track(Sample prev, Sample curr) {}

	private final Map<Integer, Track> tracks = new ConcurrentHashMap<>();

	private ClientSkyUfoMotionSync() {}

	public void apply(UfoMotionPayload payload) {
		Sample sample = new Sample(payload.x(), payload.y(), payload.z(), payload.yaw(), payload.tick());
		tracks.compute(payload.entityId(), (id, existing) -> {
			// The first sample ever received for an id has nothing to bracket from yet — start both
			// ends at the same point so interpolate() is the identity until a second sample arrives,
			// rather than lerping from some arbitrary default.
			Sample prev = existing != null ? existing.curr() : sample;
			return new Track(prev, sample);
		});
	}

	/** Drops a UFO's cached motion — call when it's discarded/destroyed so a reused id can't inherit stale samples. */
	public void remove(int entityId) {
		tracks.remove(entityId);
	}

	public void clear() {
		tracks.clear();
	}

	/** The interpolated transform to render {@code entityId} at right now, or {@code null} if nothing has synced yet. */
	public Sample sampleAt(int entityId, long localTick, double tickDelta) {
		Track t = tracks.get(entityId);
		if (t == null) return null;
		double fraction = StructureMotion.fraction(t.prev().tick(), t.curr().tick(), localTick, tickDelta);
		return StructureMotion.interpolate(t.prev(), t.curr(), fraction);
	}
}
