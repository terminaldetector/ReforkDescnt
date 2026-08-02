package com.terminaldetector.drmd.workshop;

import com.terminaldetector.drmd.DescentMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Construction registry — port of D6_SCKBridge.
 * Stores per-weapon visual/muzzle overrides; defaults come from DefaultLayouts.
 * Persists under world/data/drmd/visual/<class>.nbt
 */
public final class ConstructionRegistry {
	private static final Map<String, List<ClusterModule>> OVERRIDES = new ConcurrentHashMap<>();
	private static Path saveDir;

	private ConstructionRegistry() {}

	public static void bootstrap(MinecraftServer server) {
		saveDir = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("drmd").resolve("visual");
		try {
			Files.createDirectories(saveDir);
			loadAll();
		} catch (IOException e) {
			DescentMod.LOGGER.error("Failed to init construction save dir", e);
		}
		DescentMod.LOGGER.info("Construction registry ready ({} overrides)", OVERRIDES.size());
	}

	public static boolean hasOverride(String weaponId) {
		return OVERRIDES.containsKey(normalize(weaponId));
	}

	/** Effective modules: override if present, else default layout. */
	public static List<ClusterModule> getModules(String weaponId) {
		String id = normalize(weaponId);
		List<ClusterModule> over = OVERRIDES.get(id);
		if (over != null && !over.isEmpty()) {
			List<ClusterModule> copy = new ArrayList<>(over.size());
			for (ClusterModule m : over) copy.add(m.copy());
			return copy;
		}
		return DefaultLayouts.forWeapon(id);
	}

	public static Map<Integer, WeaponClusters.MuzzleOffset> getMuzzles(String weaponId) {
		return WeaponClusters.extractMuzzles(getModules(weaponId));
	}

	public static WeaponClusters.MuzzleOffset getMuzzle(String weaponId, int idx) {
		Map<Integer, WeaponClusters.MuzzleOffset> map = getMuzzles(weaponId);
		WeaponClusters.MuzzleOffset o = map.get(idx);
		if (o != null) return o;
		// Fallback first available
		if (!map.isEmpty()) return map.values().iterator().next();
		return new WeaponClusters.MuzzleOffset(14, 2, -2);
	}

	public static void setOverride(String weaponId, List<ClusterModule> modules) {
		String id = normalize(weaponId);
		if (modules == null || modules.isEmpty()) {
			OVERRIDES.remove(id);
			deleteFile(id);
			return;
		}
		List<ClusterModule> copy = new ArrayList<>(modules.size());
		for (ClusterModule m : modules) copy.add(m.copy());
		WeaponClusters.sort(copy);
		OVERRIDES.put(id, copy);
		save(id, copy);
	}

	public static void clearOverride(String weaponId) {
		setOverride(weaponId, null);
	}

	public static Map<String, List<ClusterModule>> allOverrides() {
		return new LinkedHashMap<>(OVERRIDES);
	}

	public static String normalize(String weaponId) {
		if (weaponId == null) return "mg";
		String id = weaponId;
		if (id.startsWith("weapon_d6_")) id = id.substring("weapon_d6_".length());
		if (id.startsWith("drmd:weapon_d6_")) id = id.substring("drmd:weapon_d6_".length());
		return id;
	}

	private static void save(String id, List<ClusterModule> modules) {
		if (saveDir == null) return;
		try {
			NbtCompound root = new NbtCompound();
			root.put("modules", ClusterModule.listToNbt(modules));
			NbtIo.writeCompressed(root, saveDir.resolve(id + ".nbt"));
		} catch (IOException e) {
			DescentMod.LOGGER.error("Failed to save construction for {}", id, e);
		}
	}

	private static void deleteFile(String id) {
		if (saveDir == null) return;
		try {
			Files.deleteIfExists(saveDir.resolve(id + ".nbt"));
		} catch (IOException ignored) {}
	}

	private static void loadAll() throws IOException {
		OVERRIDES.clear();
		if (saveDir == null || !Files.isDirectory(saveDir)) return;
		try (var stream = Files.list(saveDir)) {
			stream.filter(p -> p.getFileName().toString().endsWith(".nbt")).forEach(p -> {
				String id = p.getFileName().toString().replace(".nbt", "");
				try {
					NbtCompound root = NbtIo.readCompressed(p, NbtSizeTracker.ofUnlimitedBytes());
					NbtList list = root.getList("modules", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
					List<ClusterModule> mods = ClusterModule.listFromNbt(list);
					WeaponClusters.sort(mods);
					OVERRIDES.put(id, mods);
				} catch (Exception e) {
					DescentMod.LOGGER.warn("Bad construction file {}", p);
				}
			});
		}
	}
}
