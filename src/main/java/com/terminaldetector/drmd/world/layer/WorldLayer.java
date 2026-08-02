package com.terminaldetector.drmd.world.layer;

import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Narrative layers of the world parallelepiped (Core · Surface · Orbit/Oblivion).
 *
 * <p>Edges ({@code yMin}) are thin teleport seams ({@link LayerBridge}); clients paint the
 * boundary and sky through hooks — not by building solid volumes. End/Nether stay optional
 * Immersive-Portals hops.
 */
public enum WorldLayer {
	/** Planetary core — Nether band + deep abyss. Aggressive bases, tangled mines. */
	CORE("Core", WorldLevels.WORLD_BOTTOM, WorldLevels.NETHER_CEILING, 0xFF662222),
	/** Underground End branch — hi-tech complexes beside abandoned mines. */
	DUNGEON("Dungeon", WorldLevels.NETHER_CEILING, WorldLevels.INDUSTRIAL_TOP, 0xFF445566),
	/** Surface ruins, labs, cities — periodically scorched by UFO / scanners. */
	SURFACE("Surface", WorldLevels.INDUSTRIAL_TOP, WorldLevels.SURFACE_TOP, 0xFF558844),
	/** Floating anomalies, inverted villages, stations. Medium hostility. */
	ORBIT("Orbit", WorldLevels.SURFACE_TOP, WorldLevels.ORBITAL_TOP, 0xFF4477AA),
	/** Oblivion End — giant hostile base, reactor laser, final boss. */
	OBLIVION("Oblivion", WorldLevels.ORBITAL_TOP, WorldLevels.WORLD_TOP, 0xFFAA66FF);

	public final String label;
	public final int yMin;
	public final int yMax;
	public final int hudColor;

	WorldLayer(String label, int yMin, int yMax, int hudColor) {
		this.label = label;
		this.yMin = yMin;
		this.yMax = yMax;
		this.hudColor = hudColor;
	}

	public int midY() {
		return (yMin + yMax) / 2;
	}

	/** Soft blend band thickness at layer edges (HL2-style fade zone). */
	public static final int BLEND = 12;

	public static WorldLayer at(double y) {
		int yi = MathHelper.floor(y);
		for (WorldLayer layer : values()) {
			if (yi >= layer.yMin && yi < layer.yMax) return layer;
		}
		return yi < WorldLevels.WORLD_BOTTOM ? CORE : OBLIVION;
	}

	public static WorldLayer at(World world, double y) {
		if (world.getRegistryKey() == World.END) return OBLIVION;
		if (world.getRegistryKey() == World.NETHER) return CORE;
		return at(y);
	}

	public WorldLayer below() {
		int i = ordinal() - 1;
		return i < 0 ? this : values()[i];
	}

	public WorldLayer above() {
		int i = ordinal() + 1;
		return i >= values().length ? this : values()[i];
	}
}
