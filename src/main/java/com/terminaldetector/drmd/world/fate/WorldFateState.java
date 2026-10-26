package com.terminaldetector.drmd.world.fate;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/**
 * Persistent world fate — lives on the Overworld, shared by the whole column / End.
 */
public class WorldFateState extends PersistentState {
	public static final String ID = "drmd_world_fate";

	private WorldFate fate = WorldFate.CONTINUING;
	private long fateGameTime = -1;
	private int decayTicks;

	public static WorldFateState get(ServerWorld overworld) {
		PersistentStateManager mgr = overworld.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(WorldFateState::new, WorldFateState::fromNbt, null), ID);
	}

	public WorldFateState() {}

	public static WorldFateState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		WorldFateState s = new WorldFateState();
		try {
			s.fate = WorldFate.valueOf(nbt.getString("fate"));
		} catch (Exception e) {
			s.fate = WorldFate.CONTINUING;
		}
		s.fateGameTime = nbt.contains("t") ? nbt.getLong("t") : -1;
		s.decayTicks = nbt.getInt("decay");
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putString("fate", fate.name());
		nbt.putLong("t", fateGameTime);
		nbt.putInt("decay", decayTicks);
		return nbt;
	}

	public WorldFate fate() { return fate; }
	public long fateGameTime() { return fateGameTime; }
	public int decayTicks() { return decayTicks; }

	public void setFate(WorldFate next, long gameTime) {
		if (this.fate == WorldFate.VOID && next != WorldFate.CONTINUING) return; // void is terminal in-play
		this.fate = next;
		this.fateGameTime = gameTime;
		if (next == WorldFate.CONTINUING) decayTicks = 0;
		markDirty();
	}

	public void bumpDecay() {
		decayTicks++;
		markDirty();
	}

	public boolean machinesSilent() {
		return fate == WorldFate.SILENCE || fate == WorldFate.VOID;
	}

	public boolean isVoid() {
		return fate == WorldFate.VOID;
	}
}
