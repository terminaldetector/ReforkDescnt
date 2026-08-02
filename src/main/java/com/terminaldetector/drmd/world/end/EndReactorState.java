package com.terminaldetector.drmd.world.end;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/** Persistent End mega-reactor fight flags. */
public class EndReactorState extends PersistentState {
	public static final String ID = "drmd_end_reactor";

	public enum Phase { SHIELDED, EXPOSED, CRITICAL, DEFEATED }

	private boolean baseGenerated;
	private Phase phase = Phase.SHIELDED;

	public static EndReactorState get(ServerWorld world) {
		PersistentStateManager mgr = world.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(EndReactorState::new, EndReactorState::fromNbt, null), ID);
	}

	public EndReactorState() {}

	public static EndReactorState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		EndReactorState s = new EndReactorState();
		s.baseGenerated = nbt.getBoolean("base");
		try {
			s.phase = Phase.valueOf(nbt.getString("phase"));
		} catch (Exception e) {
			s.phase = Phase.SHIELDED;
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean("base", baseGenerated);
		nbt.putString("phase", phase.name());
		return nbt;
	}

	public boolean isBaseGenerated() { return baseGenerated; }
	public void setBaseGenerated(boolean v) { this.baseGenerated = v; markDirty(); }

	public Phase getPhase() { return phase; }
	public void setPhase(Phase p) { this.phase = p; markDirty(); }

	public boolean isDefeated() { return phase == Phase.DEFEATED; }
}
