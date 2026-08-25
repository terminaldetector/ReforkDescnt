package com.terminaldetector.drmd.world.end.space;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Persisted "already built" marks for {@link EndSpaceRegions}' grid cells.
 *
 * <p>Deliberately permanent, never cleared — the same one-shot-forever shape as
 * {@code EndReactorState.baseGenerated}, not {@code DescentWorldState}'s megacity marks (those are
 * wiped every boot and rely on a separate physical re-entry guard inside the generator itself). A
 * Layer 2 tile has no such guard, so this mark has to be the permanent one: without it, a server
 * restart would let a previously-built tile be rediscovered on chunk-load and rebuilt on top of itself.
 */
public class EndSpaceState extends PersistentState {
	public static final String ID = "drmd_end_space";

	private final Set<Long> builtCells = new HashSet<>();

	public static EndSpaceState get(ServerWorld world) {
		PersistentStateManager mgr = world.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(EndSpaceState::new, EndSpaceState::fromNbt, null), ID);
	}

	public EndSpaceState() {}

	public static EndSpaceState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		EndSpaceState s = new EndSpaceState();
		if (nbt.contains("builtCells")) {
			NbtList list = nbt.getList("builtCells", NbtElement.LONG_TYPE);
			for (int i = 0; i < list.size(); i++) {
				s.builtCells.add(((NbtLong) list.get(i)).longValue());
			}
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		for (long packed : builtCells) list.add(NbtLong.of(packed));
		nbt.put("builtCells", list);
		return nbt;
	}

	public boolean isCellBuilt(int cellX, int cellZ) {
		return builtCells.contains(pack(cellX, cellZ));
	}

	public void markCellBuilt(int cellX, int cellZ) {
		if (builtCells.add(pack(cellX, cellZ))) markDirty();
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}
}
