package com.terminaldetector.drmd.world.scar;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.LinkedHashSet;

/**
 * Where reactor detonations burned the planet, in 32-block cells on the Overworld.
 *
 * <p>This is what is left of the planet voxel map. That map carried a height and a biome tint for
 * every cell a pilot had ever flown over, because the client drew the whole surface back as a floor
 * of voxels when you looked down from orbit — the far-view pipeline that is now Distant Horizons'
 * job. The scars were the one part of it the world itself reads: {@link ScarApplier} turns them into
 * real craters when the chunk loads, so orbital damage is still there when you descend.
 *
 * <p>The state ID and NBT shape are inherited rather than fresh, so a world saved by an earlier
 * build keeps the scars it already has. Cells that were merely explored are dropped on read.
 */
public class ScarMapState extends PersistentState {
	/** Inherited from the planet map this replaces — existing saves keep their scars. */
	public static final String ID = "drmd_planet_map";

	/** Cell edge in blocks. Two chunks: coarse enough that a crater field stays cheap to store. */
	public static final int CELL = 32;

	/** Soft cap so NBT stays bounded — oldest scars fall off first. */
	public static final int MAX_CELLS = 12_000;

	/** Legacy flag bit for a scarred cell in the planet-map NBT. */
	private static final int F_SCAR = 2;
	/** Legacy flag bits written back so an older build still reads these cells as scars. */
	private static final int F_EXPLORED_SCAR = 3;

	private final LinkedHashSet<Long> scars = new LinkedHashSet<>();

	public static ScarMapState get(ServerWorld overworld) {
		PersistentStateManager mgr = overworld.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(ScarMapState::new, ScarMapState::fromNbt, null), ID);
	}

	public ScarMapState() {}

	public static ScarMapState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		ScarMapState s = new ScarMapState();
		NbtList list = nbt.getList("cells", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound c = list.getCompound(i);
			// Absent flags means a cell written by this build, which only ever writes scars.
			if (c.contains("f") && (c.getInt("f") & F_SCAR) == 0) continue;
			s.scars.add(key(c.getInt("cx"), c.getInt("cz")));
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		for (long key : scars) {
			NbtCompound c = new NbtCompound();
			c.putInt("cx", cx(key));
			c.putInt("cz", cz(key));
			c.putInt("f", F_EXPLORED_SCAR);
			list.add(c);
		}
		nbt.put("cells", list);
		return nbt;
	}

	public boolean scarred(int cellX, int cellZ) {
		return scars.contains(key(cellX, cellZ));
	}

	public void scar(int cellX, int cellZ) {
		if (!scars.add(key(cellX, cellZ))) return;
		trim();
		markDirty();
	}

	public void scarBlock(int blockX, int blockZ) {
		scar(cellOf(blockX), cellOf(blockZ));
	}

	public int size() {
		return scars.size();
	}

	/** Cell keys, for the client snapshot. Insertion-ordered, so the oldest scars go first. */
	public java.util.Collection<Long> cells() {
		return java.util.Collections.unmodifiableCollection(scars);
	}

	public static int cellOf(int block) {
		return Math.floorDiv(block, CELL);
	}

	private static long key(int cellX, int cellZ) {
		return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
	}

	private static int cx(long key) {
		return (int) (key >> 32);
	}

	private static int cz(long key) {
		return (int) key;
	}

	private void trim() {
		while (scars.size() > MAX_CELLS) {
			var it = scars.iterator();
			it.next();
			it.remove();
		}
	}
}
