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

	public static DescentWorldState get(ServerWorld world) {
		PersistentStateManager mgr = world.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(DescentWorldState::new, DescentWorldState::fromNbt, null), ID);
	}

	public DescentWorldState() {}

	public static DescentWorldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		DescentWorldState s = new DescentWorldState();
		s.spawnHubGenerated = nbt.getBoolean("spawnHubGenerated");
		s.stockSeeded = nbt.getBoolean("stockSeeded");
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean("spawnHubGenerated", spawnHubGenerated);
		nbt.putBoolean("stockSeeded", stockSeeded);
		return nbt;
	}

	public boolean isSpawnHubGenerated() { return spawnHubGenerated; }
	public void setSpawnHubGenerated(boolean v) { this.spawnHubGenerated = v; markDirty(); }

	public boolean isStockSeeded() { return stockSeeded; }
	public void setStockSeeded(boolean v) { this.stockSeeded = v; markDirty(); }
}
