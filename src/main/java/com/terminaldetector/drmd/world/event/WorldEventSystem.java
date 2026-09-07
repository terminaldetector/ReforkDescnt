package com.terminaldetector.drmd.world.event;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Consequence;
import com.terminaldetector.drmd.d6.D6EventRegistry;
import com.terminaldetector.drmd.d6.D6WorldEvent;
import com.terminaldetector.drmd.diag.DiagTrace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the world-event registry from the server tick.
 *
 * <p>All of the deciding happens in {@link D6EventRegistry}; this only supplies the three things it
 * cannot get for itself — the clock, where the players are, and whether the sky is bright — and
 * reports what came back.
 *
 * <p><b>Nothing is spawned yet.</b> The registry says which events would have to become entities and
 * that answer goes to the diagnostics, deliberately, so that the next flight report says whether the
 * spine runs before anything in the world depends on it. Guessing is what has cost this project time
 * before.
 *
 * <p>What an ending event leaves behind, however, is recorded now: that costs nothing, needs no
 * entity, and is the half of the design that survives being unobserved.
 */
public final class WorldEventSystem {
	private WorldEventSystem() {}

	/**
	 * Ticks between passes.
	 *
	 * <p>Four times a second. The margin that stops the boundary thrashing is fifteen percent of an
	 * event's shape reach — 75 blocks on a five-block craft — so a player would have to close 300
	 * blocks a second to cross both thresholds inside one pass. What this cannot survive is an event
	 * that enters and leaves entirely between two passes, which needs about 200 blocks a tick of
	 * closing speed; nothing in the game moves that way, and if something does, this is the constant
	 * to look at.
	 */
	private static final int INTERVAL = 5;

	/** Minecraft's day is 24000 ticks; the sun is down between these. */
	private static final long DUSK = 12_000L;
	private static final long DAWN = 23_000L;

	public static void tick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) return;

		ServerWorld overworld = server.getOverworld();
		if (overworld == null) return;
		WorldEventState state = WorldEventState.get(overworld);
		D6EventRegistry registry = state.registry();
		if (registry.size() == 0) return;

		long now = overworld.getTime();
		boolean daylight = isDaylight(overworld);
		List<Vec3> observers = observers(server);

		D6EventRegistry.Change change = registry.update(observers, now, daylight);
		for (Long id : change.realised()) {
			DiagTrace.count("event.realised");
			D6WorldEvent event = registry.get(id);
			DiagTrace.record("event", "event " + id + " (" + (event == null ? "?" : event.type())
					+ ") needs entities");
		}
		for (Long ignored : change.released()) {
			DiagTrace.count("event.released");
		}

		// Purged after the update, so an event that ends this pass is reported as released first and
		// does not vanish from under whatever it spawned.
		List<D6WorldEvent> finished = registry.purgeFinished(now);
		for (D6WorldEvent event : finished) {
			DiagTrace.count("event.finished");
			remember(state, event, now);
		}

		if (!change.isEmpty() || !finished.isEmpty()) state.touch();
	}

	/**
	 * Turn an ended event into a mark on the world, if its kind leaves one.
	 *
	 * <p>The place is where the event <em>ended</em> rather than where it began, which matters for
	 * anything that moved: a missile is remembered where it landed, not where it was launched.
	 */
	private static void remember(WorldEventState state, D6WorldEvent event, long now) {
		EventAftermath.Trace trace = EventAftermath.forType(event.type());
		if (trace == null) {
			DiagTrace.record("event", "event " + event.id() + " (" + event.type()
					+ ") ended leaving nothing, as its kind does");
			return;
		}
		D6Consequence mark = state.consequences().add(new D6Consequence(
				trace.severity(), event.positionAt(now), trace.radius(),
				event.id(), event.type(), now));
		DiagTrace.count("event.remembered");
		DiagTrace.record("event", "event " + event.id() + " (" + event.type() + ") left "
				+ mark.severity() + " over " + Math.round(mark.radius()) + " blocks at " + mark.centre());
	}

	/**
	 * Where the players are.
	 *
	 * <p>Overworld only, matching where the registry lives. A player in the End is not an observer of
	 * an Overworld event, which is right, and is also the visible edge of the one-registry limitation
	 * named on {@link WorldEventState}.
	 */
	private static List<Vec3> observers(MinecraftServer server) {
		List<Vec3> positions = new ArrayList<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getWorld().getRegistryKey() != World.OVERWORLD) continue;
			Vec3d pos = player.getEyePos();
			positions.add(new Vec3(pos.x, pos.y, pos.z));
		}
		return positions;
	}

	private static boolean isDaylight(ServerWorld world) {
		long dayTime = Math.floorMod(world.getTimeOfDay(), 24_000L);
		return dayTime < DUSK || dayTime >= DAWN;
	}
}
