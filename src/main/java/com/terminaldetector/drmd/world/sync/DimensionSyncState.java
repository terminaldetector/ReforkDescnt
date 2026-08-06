package com.terminaldetector.drmd.world.sync;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cross-dimension persistent sync state — lives on the Overworld, shared by End/orbit/column.
 * Facilities, meteor falls, and last detonation stamp survive restarts.
 */
public class DimensionSyncState extends PersistentState {
	public static final String ID = "drmd_dimension_sync";

	public static final class FacilityNbt {
		public UUID id;
		public BlockPos core;
		public String vitality;
		public boolean orbital;
		public String phase;
		public int ticksLeft;
		public BlockPos exit; // nullable
		public Identifier dim;

		public NbtCompound write() {
			NbtCompound n = new NbtCompound();
			n.putUuid("id", id);
			n.putInt("x", core.getX());
			n.putInt("y", core.getY());
			n.putInt("z", core.getZ());
			n.putString("vit", vitality);
			n.putBoolean("orb", orbital);
			n.putString("phase", phase);
			n.putInt("ticks", ticksLeft);
			if (exit != null) {
				n.putInt("ex", exit.getX());
				n.putInt("ey", exit.getY());
				n.putInt("ez", exit.getZ());
			}
			n.putString("dim", dim.toString());
			return n;
		}

		public static FacilityNbt read(NbtCompound n) {
			FacilityNbt f = new FacilityNbt();
			f.id = n.getUuid("id");
			f.core = new BlockPos(n.getInt("x"), n.getInt("y"), n.getInt("z"));
			f.vitality = n.getString("vit");
			f.orbital = n.getBoolean("orb");
			f.phase = n.getString("phase");
			f.ticksLeft = n.getInt("ticks");
			if (n.contains("ex")) {
				f.exit = new BlockPos(n.getInt("ex"), n.getInt("ey"), n.getInt("ez"));
			}
			f.dim = Identifier.tryParse(n.getString("dim"));
			if (f.dim == null) f.dim = Identifier.of("minecraft", "overworld");
			return f;
		}
	}

	public static final class FallNbt {
		public int x, z, ticks, intensity;

		public NbtCompound write() {
			NbtCompound n = new NbtCompound();
			n.putInt("x", x);
			n.putInt("z", z);
			n.putInt("t", ticks);
			n.putInt("i", intensity);
			return n;
		}

		public static FallNbt read(NbtCompound n) {
			FallNbt f = new FallNbt();
			f.x = n.getInt("x");
			f.z = n.getInt("z");
			f.ticks = n.getInt("t");
			f.intensity = n.getInt("i");
			return f;
		}
	}

	private final List<FacilityNbt> facilities = new ArrayList<>();
	private final List<FallNbt> falls = new ArrayList<>();
	private long lastDetonationGameTime = -1;
	private int lastDetonationX, lastDetonationY, lastDetonationZ;
	private boolean lastDetonationOrbital;

	public static DimensionSyncState get(ServerWorld overworld) {
		PersistentStateManager mgr = overworld.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(DimensionSyncState::new, DimensionSyncState::fromNbt, null), ID);
	}

	public DimensionSyncState() {}

	public static DimensionSyncState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		DimensionSyncState s = new DimensionSyncState();
		NbtList fac = nbt.getList("facilities", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < fac.size(); i++) s.facilities.add(FacilityNbt.read(fac.getCompound(i)));
		NbtList fl = nbt.getList("falls", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < fl.size(); i++) s.falls.add(FallNbt.read(fl.getCompound(i)));
		if (nbt.contains("lastDet")) {
			NbtCompound d = nbt.getCompound("lastDet");
			s.lastDetonationGameTime = d.getLong("t");
			s.lastDetonationX = d.getInt("x");
			s.lastDetonationY = d.getInt("y");
			s.lastDetonationZ = d.getInt("z");
			s.lastDetonationOrbital = d.getBoolean("orb");
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList fac = new NbtList();
		for (FacilityNbt f : facilities) fac.add(f.write());
		nbt.put("facilities", fac);
		NbtList fl = new NbtList();
		for (FallNbt f : falls) fl.add(f.write());
		nbt.put("falls", fl);
		if (lastDetonationGameTime >= 0) {
			NbtCompound d = new NbtCompound();
			d.putLong("t", lastDetonationGameTime);
			d.putInt("x", lastDetonationX);
			d.putInt("y", lastDetonationY);
			d.putInt("z", lastDetonationZ);
			d.putBoolean("orb", lastDetonationOrbital);
			nbt.put("lastDet", d);
		}
		return nbt;
	}

	public List<FacilityNbt> facilities() { return facilities; }
	public List<FallNbt> falls() { return falls; }

	public void replaceFacilities(List<FacilityNbt> next) {
		facilities.clear();
		facilities.addAll(next);
		markDirty();
	}

	public void replaceFalls(List<FallNbt> next) {
		falls.clear();
		falls.addAll(next);
		markDirty();
	}

	public void recordDetonation(long gameTime, BlockPos at, boolean orbital) {
		lastDetonationGameTime = gameTime;
		lastDetonationX = at.getX();
		lastDetonationY = at.getY();
		lastDetonationZ = at.getZ();
		lastDetonationOrbital = orbital;
		markDirty();
	}

	public long lastDetonationGameTime() { return lastDetonationGameTime; }
	public BlockPos lastDetonationPos() {
		return lastDetonationGameTime < 0 ? null
				: new BlockPos(lastDetonationX, lastDetonationY, lastDetonationZ);
	}
	public boolean lastDetonationOrbital() { return lastDetonationOrbital; }
}
