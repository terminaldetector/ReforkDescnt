package com.terminaldetector.drmd.world.event;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6EventRegistry;
import com.terminaldetector.drmd.d6.D6WorldEvent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/**
 * The world's events, kept across restarts.
 *
 * <p>Thin by design: {@link D6EventRegistry} holds the events and makes every decision, and this adds
 * only the two things that need Minecraft — where the registry lives and how it is written down. The
 * split is what keeps the decisions testable as arithmetic, so nothing here should grow logic.
 *
 * <p><b>Written as a keyed compound, not a list.</b> Each event goes under its own id, so a save with
 * a corrupt entry loses that event rather than everything after it, and so an event can be found in a
 * dumped save by the id the logs mention.
 *
 * <p><b>Lives on the Overworld</b>, as {@code ScarMapState} and {@code FactionMemory} do. A limitation
 * worth naming rather than hiding: an event is a place in a world, and one registry for the server
 * means the End cannot have events of its own. The fix is a registry per world when there is a reason
 * for one, and nothing here assumes otherwise.
 */
public class WorldEventState extends PersistentState {
	public static final String ID = "drmd_world_events";

	private final D6EventRegistry registry = new D6EventRegistry();
	private long nextId = 1;

	public static WorldEventState get(ServerWorld overworld) {
		PersistentStateManager mgr = overworld.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(WorldEventState::new, WorldEventState::fromNbt, null), ID);
	}

	public WorldEventState() {}

	public D6EventRegistry registry() {
		return registry;
	}

	/**
	 * Put an event into the world and give it an id.
	 *
	 * <p>The id is minted here rather than by the caller because it has to be unique across a save and
	 * survive a restart, and only the thing that persists can promise that.
	 */
	public long add(String type, Vec3 origin, Vec3 velocity, long startTick, int[] phases,
			double size, double brightness) {
		long id = nextId++;
		registry.add(new D6WorldEvent(id, type, origin, velocity, startTick, phases, size, brightness));
		markDirty();
		return id;
	}

	/** After anything that changes the registry from outside — the registry itself cannot mark us. */
	public void touch() {
		markDirty();
	}

	public static WorldEventState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		WorldEventState state = new WorldEventState();
		state.nextId = Math.max(1, nbt.getLong("nextId"));
		NbtCompound events = nbt.getCompound("events");
		for (String key : events.getKeys()) {
			try {
				NbtCompound e = events.getCompound(key);
				int[] phases = e.getIntArray("phases");
				// A stored event with no phases cannot be reconstructed, and the constructor would
				// throw for the whole load rather than for the one bad entry.
				if (phases.length == 0) continue;
				state.registry.add(new D6WorldEvent(
						Long.parseLong(key),
						e.getString("type"),
						new Vec3(e.getDouble("x"), e.getDouble("y"), e.getDouble("z")),
						new Vec3(e.getDouble("vx"), e.getDouble("vy"), e.getDouble("vz")),
						e.getLong("start"),
						phases,
						e.getDouble("size"),
						e.getDouble("brightness")));
			} catch (RuntimeException ignored) {
				// One unreadable event is not a reason to lose the rest, which is the whole point of
				// keying them separately.
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putLong("nextId", nextId);
		NbtCompound events = new NbtCompound();
		for (D6WorldEvent event : registry.events()) {
			NbtCompound e = new NbtCompound();
			e.putString("type", event.type());
			e.putDouble("x", event.origin().x());
			e.putDouble("y", event.origin().y());
			e.putDouble("z", event.origin().z());
			Vec3 velocity = event.velocityPerTick();
			e.putDouble("vx", velocity.x());
			e.putDouble("vy", velocity.y());
			e.putDouble("vz", velocity.z());
			e.putLong("start", event.startTick());
			int[] phases = new int[event.phaseCount()];
			for (int i = 0; i < phases.length; i++) phases[i] = event.phaseDuration(i);
			e.putIntArray("phases", phases);
			e.putDouble("size", event.size());
			e.putDouble("brightness", event.brightness());
			events.put(Long.toString(event.id()), e);
		}
		nbt.put("events", events);
		return nbt;
	}
}
