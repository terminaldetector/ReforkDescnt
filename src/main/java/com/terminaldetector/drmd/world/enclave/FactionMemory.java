package com.terminaldetector.drmd.world.enclave;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers player actions toward castes — engineers help suits; cult opens relics
 * if control systems stay intact.
 */
public class FactionMemory extends PersistentState {
	public static final String ID = "drmd_faction_memory";

	/** player → (origin id → reputation) */
	private final Map<UUID, Map<String, Integer>> rep = new HashMap<>();
	/** cult flag: player destroyed control systems */
	private final Map<UUID, Boolean> brokeControl = new HashMap<>();

	public static FactionMemory get(ServerWorld overworld) {
		PersistentStateManager mgr = overworld.getPersistentStateManager();
		return mgr.getOrCreate(new Type<>(FactionMemory::new, FactionMemory::fromNbt, null), ID);
	}

	public static FactionMemory of(ServerWorld any) {
		ServerWorld ow = any.getServer().getOverworld();
		return get(ow);
	}

	public FactionMemory() {}

	public static FactionMemory fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		FactionMemory s = new FactionMemory();
		NbtCompound players = nbt.getCompound("players");
		for (String key : players.getKeys()) {
			try {
				UUID id = UUID.fromString(key);
				NbtCompound p = players.getCompound(key);
				Map<String, Integer> map = new HashMap<>();
				NbtCompound r = p.getCompound("rep");
				for (String origin : r.getKeys()) {
					map.put(origin, r.getInt(origin));
				}
				s.rep.put(id, map);
				if (p.contains("broke")) s.brokeControl.put(id, p.getBoolean("broke"));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtCompound players = new NbtCompound();
		for (Map.Entry<UUID, Map<String, Integer>> e : rep.entrySet()) {
			NbtCompound p = new NbtCompound();
			NbtCompound r = new NbtCompound();
			for (Map.Entry<String, Integer> re : e.getValue().entrySet()) {
				r.putInt(re.getKey(), re.getValue());
			}
			p.put("rep", r);
			if (brokeControl.getOrDefault(e.getKey(), false)) {
				p.putBoolean("broke", true);
			}
			players.put(e.getKey().toString(), p);
		}
		for (Map.Entry<UUID, Boolean> e : brokeControl.entrySet()) {
			if (!e.getValue() || players.contains(e.getKey().toString())) continue;
			NbtCompound p = new NbtCompound();
			p.putBoolean("broke", true);
			players.put(e.getKey().toString(), p);
		}
		nbt.put("players", players);
		return nbt;
	}

	public int getRep(UUID player, EnclaveOrigin origin) {
		Map<String, Integer> m = rep.get(player);
		if (m == null) return 0;
		return m.getOrDefault(origin.id, 0);
	}

	public void addRep(UUID player, EnclaveOrigin origin, int delta) {
		Map<String, Integer> m = rep.computeIfAbsent(player, u -> new HashMap<>());
		m.put(origin.id, Math.max(-100, Math.min(100, m.getOrDefault(origin.id, 0) + delta)));
		markDirty();
	}

	public void markBrokeControl(UUID player) {
		brokeControl.put(player, true);
		addRep(player, EnclaveOrigin.CULTISTS, -25);
		markDirty();
	}

	public boolean brokeControl(UUID player) {
		return brokeControl.getOrDefault(player, false);
	}

	/** Cult opens ancient objects only if systems were not wrecked and rep is positive. */
	public boolean cultGrantsRelics(ServerPlayerEntity player) {
		return !brokeControl(player.getUuid()) && getRep(player.getUuid(), EnclaveOrigin.CULTISTS) >= 10;
	}

	/** Engineers help modernize the suit at modest positive rep. */
	public boolean engineersHelpSuit(ServerPlayerEntity player) {
		return getRep(player.getUuid(), EnclaveOrigin.ENGINEERS) >= 5;
	}
}
