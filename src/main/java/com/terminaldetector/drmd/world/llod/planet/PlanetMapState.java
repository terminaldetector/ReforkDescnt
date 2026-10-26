package com.terminaldetector.drmd.world.llod.planet;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent planetary map — explored Overworld cells, weather stamps, reactor scars.
 * Stored on the Overworld; End/orbit clients receive a viewport sync.
 */
public class PlanetMapState extends PersistentState {
	public static final String ID = "drmd_planet_map";
	/** Soft cap so NBT stays bounded. */
	public static final int MAX_CELLS = 12_000;

	private final LinkedHashMap<Long, PlanetCell> cells = new LinkedHashMap<>(256, 0.75f, true);

	public static PlanetMapState get(ServerWorld overworld) {
		PersistentStateManager mgr = overworld.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(PlanetMapState::new, PlanetMapState::fromNbt, null), ID);
	}

	public PlanetMapState() {}

	public static PlanetMapState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		PlanetMapState s = new PlanetMapState();
		NbtList list = nbt.getList("cells", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound c = list.getCompound(i);
			PlanetCell cell = new PlanetCell(
					c.getInt("cx"), c.getInt("cz"),
					c.getInt("h"), c.getInt("t"), c.getInt("f"));
			s.cells.put(cell.key(), cell);
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		for (PlanetCell cell : cells.values()) {
			NbtCompound c = new NbtCompound();
			c.putInt("cx", cell.cx);
			c.putInt("cz", cell.cz);
			c.putInt("h", cell.height);
			c.putInt("t", cell.tint);
			c.putInt("f", cell.flags);
			list.add(c);
		}
		nbt.put("cells", list);
		return nbt;
	}

	public PlanetCell get(int cx, int cz) {
		return cells.get(PlanetCell.key(cx, cz));
	}

	public void put(PlanetCell cell) {
		PlanetCell prev = cells.get(cell.key());
		PlanetCell merged = prev == null ? cell : prev.merge(cell);
		cells.put(merged.key(), merged);
		trim();
		markDirty();
	}

	public void explore(int cx, int cz, int height, int tint, int weatherFlags) {
		int flags = PlanetCell.F_EXPLORED | weatherFlags;
		PlanetCell prev = get(cx, cz);
		if (prev != null) flags |= prev.flags;
		put(new PlanetCell(cx, cz, height, tint, flags));
	}

	public void scar(int cx, int cz, int intensity) {
		PlanetCell prev = get(cx, cz);
		if (prev == null) {
			prev = PlanetVoxelMath.procedural(0, cx, cz).withFlags(PlanetCell.F_SCAR);
			// keep procedural height but mark scar+explored so it sticks on descend
			put(new PlanetCell(cx, cz, prev.height, 0x553322,
					PlanetCell.F_EXPLORED | PlanetCell.F_SCAR | (intensity > 1 ? PlanetCell.F_STORM : 0)));
			return;
		}
		put(prev.withFlags(prev.flags | PlanetCell.F_SCAR | PlanetCell.F_EXPLORED));
	}

	public void scarBlock(int blockX, int blockZ, int intensity) {
		scar(PlanetCell.cellOf(blockX), PlanetCell.cellOf(blockZ), intensity);
	}

	/** Viewport query for sync — explored preferred, fill procedural holes for fog-of-war. */
	public List<PlanetCell> viewport(int centerCx, int centerCz, int radius, long seed) {
		List<PlanetCell> out = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int cx = centerCx + dx;
				int cz = centerCz + dz;
				PlanetCell known = get(cx, cz);
				out.add(known != null ? known : PlanetVoxelMath.procedural(seed, cx, cz));
			}
		}
		return out;
	}

	public Collection<PlanetCell> allExplored() {
		return cells.values();
	}

	public int size() {
		return cells.size();
	}

	private void trim() {
		while (cells.size() > MAX_CELLS) {
			Map.Entry<Long, PlanetCell> eldest = cells.entrySet().iterator().next();
			cells.remove(eldest.getKey());
		}
	}
}
