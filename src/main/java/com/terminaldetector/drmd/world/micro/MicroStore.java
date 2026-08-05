package com.terminaldetector.drmd.world.micro;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which blocks are not whole any more, and how much of each is left.
 *
 * <p>Sparse on purpose: an undamaged block is not in here at all, and the world is almost entirely
 * undamaged blocks. An entry is a position and a long, so a battlefield of ten thousand chewed
 * blocks costs a hundred and sixty kilobytes — which is what makes it affordable for every block to
 * be damageable rather than a chosen few.
 *
 * <p>A block that reaches zero leaves the map at the same moment it leaves the world, so this never
 * accumulates the history of everything that has ever been destroyed.
 */
public class MicroStore extends PersistentState {
	public static final String ID = "drmd_microblocks";

	/** Ceiling on tracked blocks, so a runaway explosion cannot grow the save without bound. */
	public static final int MAX_ENTRIES = 262_144;

	private final Map<Long, Long> masks = new ConcurrentHashMap<>();

	public static MicroStore get(ServerWorld world) {
		return world.getPersistentStateManager()
				.getOrCreate(new Type<>(MicroStore::new, MicroStore::fromNbt, null), ID);
	}

	public MicroStore() {}

	public static MicroStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		MicroStore s = new MicroStore();
		long[] flat = nbt.getLongArray("masks");
		// Pairs of (position, mask). An odd tail means a truncated save, not a half-entry.
		for (int i = 0; i + 1 < flat.length; i += 2) {
			if (flat[i + 1] == MicroGrid.FULL || flat[i + 1] == MicroGrid.EMPTY) continue;
			s.masks.put(flat[i], flat[i + 1]);
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		long[] flat = new long[masks.size() * 2];
		int i = 0;
		for (Map.Entry<Long, Long> entry : masks.entrySet()) {
			flat[i++] = entry.getKey();
			flat[i++] = entry.getValue();
		}
		nbt.putLongArray("masks", flat);
		return nbt;
	}

	/** What is left of the block at a position — {@link MicroGrid#FULL} for anything untouched. */
	public long mask(BlockPos pos) {
		Long stored = masks.get(pos.asLong());
		return stored == null ? MicroGrid.FULL : stored;
	}

	public boolean isDamaged(BlockPos pos) {
		return masks.containsKey(pos.asLong());
	}

	/**
	 * Record what is left.
	 *
	 * <p>Whole and gone are both absences: a block back to full has nothing to remember, and one at
	 * zero is about to stop existing. Either way the entry goes, which is what keeps the map the
	 * size of the damage rather than the size of the history.
	 */
	public void set(BlockPos pos, long mask) {
		long key = pos.asLong();
		if (mask == MicroGrid.FULL || mask == MicroGrid.EMPTY) {
			if (masks.remove(key) != null) markDirty();
			return;
		}
		if (!masks.containsKey(key) && masks.size() >= MAX_ENTRIES) return;
		Long previous = masks.put(key, mask);
		if (previous == null || previous != mask) markDirty();
	}

	public void clear(BlockPos pos) {
		if (masks.remove(pos.asLong()) != null) markDirty();
	}

	public int size() {
		return masks.size();
	}
}
