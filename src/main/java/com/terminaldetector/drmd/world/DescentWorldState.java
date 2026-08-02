package com.terminaldetector.drmd.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/**
 * Per-world Descent integration flags — spawn hub and stock megastructure seeding.
 */
public class DescentWorldState extends PersistentState {
	public static final String ID = "drmd_world";

	private boolean spawnHubGenerated;
	private boolean stockSeeded;
	/** Locked at first stock seed when psychedelicWorlds was enabled. */
	private boolean psychedelic;
	/** Fractal variant index baked into this world (0..N-1). */
	private int psychedelicVariant = -1;

	public static DescentWorldState get(ServerWorld world) {
		PersistentStateManager mgr = world.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(DescentWorldState::new, DescentWorldState::fromNbt, null), ID);
	}

	public DescentWorldState() {}

	public static DescentWorldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		DescentWorldState s = new DescentWorldState();
		s.spawnHubGenerated = nbt.getBoolean("spawnHubGenerated");
		s.stockSeeded = nbt.getBoolean("stockSeeded");
		s.psychedelic = nbt.getBoolean("psychedelic");
		s.psychedelicVariant = nbt.contains("psychedelicVariant") ? nbt.getInt("psychedelicVariant") : -1;
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean("spawnHubGenerated", spawnHubGenerated);
		nbt.putBoolean("stockSeeded", stockSeeded);
		nbt.putBoolean("psychedelic", psychedelic);
		nbt.putInt("psychedelicVariant", psychedelicVariant);
		return nbt;
	}

	public boolean isSpawnHubGenerated() { return spawnHubGenerated; }
	public void setSpawnHubGenerated(boolean v) { this.spawnHubGenerated = v; markDirty(); }

	public boolean isStockSeeded() { return stockSeeded; }
	public void setStockSeeded(boolean v) { this.stockSeeded = v; markDirty(); }

	public boolean isPsychedelic() { return psychedelic; }
	public void setPsychedelic(boolean v) { this.psychedelic = v; markDirty(); }

	public int getPsychedelicVariant() { return psychedelicVariant; }
	public void setPsychedelicVariant(int v) { this.psychedelicVariant = v; markDirty(); }
}
