package com.terminaldetector.drmd.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashSet;
import java.util.Set;

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
	/** Locked at first stock seed when {@code DrmdServerConfig.WorldKind.INFINITE_MEGACITY} was chosen. */
	private boolean infiniteMegacity;
	/** Packed plate anchors already queued (x<<32|z). */
	private final Set<Long> megacitySeeded = new HashSet<>();
	private final Set<Long> technogenicSeeded = new HashSet<>();
	private final Set<Long> scorchedSeeded = new HashSet<>();
	private final Set<Long> ironGuildSeeded = new HashSet<>();
	/** Packed surface-event anchors (UFO / outpost / ruin) already queued. */
	private final Set<Long> surfaceEventSeeded = new HashSet<>();
	/** Packed infinite-megacity grid cells (cellX<<32|cellZ) already queued. */
	private final Set<Long> infiniteMegacityCellSeeded = new HashSet<>();

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
		s.infiniteMegacity = nbt.getBoolean("infiniteMegacity");
		readPacked(nbt, "megacitySeeded", s.megacitySeeded);
		readPacked(nbt, "technogenicSeeded", s.technogenicSeeded);
		readPacked(nbt, "scorchedSeeded", s.scorchedSeeded);
		readPacked(nbt, "ironGuildSeeded", s.ironGuildSeeded);
		readPacked(nbt, "surfaceEventSeeded", s.surfaceEventSeeded);
		readPacked(nbt, "infiniteMegacityCellSeeded", s.infiniteMegacityCellSeeded);
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean("spawnHubGenerated", spawnHubGenerated);
		nbt.putBoolean("stockSeeded", stockSeeded);
		nbt.putBoolean("psychedelic", psychedelic);
		nbt.putInt("psychedelicVariant", psychedelicVariant);
		nbt.putBoolean("infiniteMegacity", infiniteMegacity);
		writePacked(nbt, "megacitySeeded", megacitySeeded);
		writePacked(nbt, "technogenicSeeded", technogenicSeeded);
		writePacked(nbt, "scorchedSeeded", scorchedSeeded);
		writePacked(nbt, "ironGuildSeeded", ironGuildSeeded);
		writePacked(nbt, "surfaceEventSeeded", surfaceEventSeeded);
		writePacked(nbt, "infiniteMegacityCellSeeded", infiniteMegacityCellSeeded);
		return nbt;
	}

	private static void readPacked(NbtCompound nbt, String key, Set<Long> into) {
		if (!nbt.contains(key)) return;
		NbtList list = nbt.getList(key, net.minecraft.nbt.NbtElement.LONG_TYPE);
		for (int i = 0; i < list.size(); i++) {
			into.add(((NbtLong) list.get(i)).longValue());
		}
	}

	private static void writePacked(NbtCompound nbt, String key, Set<Long> from) {
		NbtList list = new NbtList();
		for (long p : from) list.add(NbtLong.of(p));
		nbt.put(key, list);
	}

	public boolean isSpawnHubGenerated() { return spawnHubGenerated; }
	public void setSpawnHubGenerated(boolean v) { this.spawnHubGenerated = v; markDirty(); }

	public boolean isStockSeeded() { return stockSeeded; }
	public void setStockSeeded(boolean v) { this.stockSeeded = v; markDirty(); }

	public boolean isPsychedelic() { return psychedelic; }
	public void setPsychedelic(boolean v) { this.psychedelic = v; markDirty(); }

	public int getPsychedelicVariant() { return psychedelicVariant; }
	public void setPsychedelicVariant(int v) { this.psychedelicVariant = v; markDirty(); }

	public boolean isInfiniteMegacity() { return infiniteMegacity; }
	public void setInfiniteMegacity(boolean v) { this.infiniteMegacity = v; markDirty(); }

	public boolean isInfiniteMegacityCellSeeded(int cellX, int cellZ) {
		return infiniteMegacityCellSeeded.contains(pack(cellX, cellZ));
	}

	public void markInfiniteMegacityCellSeeded(int cellX, int cellZ) {
		if (infiniteMegacityCellSeeded.add(pack(cellX, cellZ))) markDirty();
	}

	public boolean isMegacitySeeded(int x, int z) {
		return megacitySeeded.contains(pack(x, z));
	}

	public void markMegacitySeeded(int x, int z) {
		if (megacitySeeded.add(pack(x, z))) markDirty();
	}

	public boolean isTechnogenicSeeded(int x, int z) {
		return technogenicSeeded.contains(pack(x, z));
	}

	public void markTechnogenicSeeded(int x, int z) {
		if (technogenicSeeded.add(pack(x, z))) markDirty();
	}

	public boolean isScorchedSeeded(int x, int z) {
		return scorchedSeeded.contains(pack(x, z));
	}

	public void markScorchedSeeded(int x, int z) {
		if (scorchedSeeded.add(pack(x, z))) markDirty();
	}

	public boolean isIronGuildSeeded(int x, int z) {
		return ironGuildSeeded.contains(pack(x, z));
	}

	public void markIronGuildSeeded(int x, int z) {
		if (ironGuildSeeded.add(pack(x, z))) markDirty();
	}

	public boolean isSurfaceEventSeeded(int x, int z) {
		return surfaceEventSeeded.contains(pack(x, z));
	}

	public void markSurfaceEventSeeded(int x, int z) {
		if (surfaceEventSeeded.add(pack(x, z))) markDirty();
	}

	/**
	 * Drop plate/event "already queued" marks. The landmark queue is in-memory and wiped
	 * every boot — keeping these marks left empty plates forever after a restart.
	 */
	public void clearLandmarkSeedMarks() {
		boolean any = !megacitySeeded.isEmpty() || !technogenicSeeded.isEmpty()
				|| !scorchedSeeded.isEmpty() || !ironGuildSeeded.isEmpty()
				|| !surfaceEventSeeded.isEmpty() || !infiniteMegacityCellSeeded.isEmpty();
		megacitySeeded.clear();
		technogenicSeeded.clear();
		scorchedSeeded.clear();
		ironGuildSeeded.clear();
		surfaceEventSeeded.clear();
		infiniteMegacityCellSeeded.clear();
		if (any) markDirty();
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}
}
