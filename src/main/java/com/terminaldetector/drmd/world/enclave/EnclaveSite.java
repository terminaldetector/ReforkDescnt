package com.terminaldetector.drmd.world.enclave;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Procedural survivor enclave — not a Minecraft village.
 * Origin × machine attitude × tech × resources drive dialogue and quests.
 */
public final class EnclaveSite {
	public static final int CELL = 384;

	public final long seed;
	public final BlockPos anchor;
	public final EnclaveOrigin origin;
	public final MachineAttitude attitude;
	/** 0..3 */
	public final int techLevel;
	/** 0..3 scarce→stocked */
	public final int resourcePressure;
	/** 0..3 toward outsiders */
	public final int hostility;
	public final String nameKey;

	private EnclaveSite(
			long seed,
			BlockPos anchor,
			EnclaveOrigin origin,
			MachineAttitude attitude,
			int techLevel,
			int resourcePressure,
			int hostility,
			String nameKey
	) {
		this.seed = seed;
		this.anchor = anchor.toImmutable();
		this.origin = origin;
		this.attitude = attitude;
		this.techLevel = techLevel;
		this.resourcePressure = resourcePressure;
		this.hostility = hostility;
		this.nameKey = nameKey;
	}

	/** Deterministic site for the 384-block cell covering {@code near}. */
	public static EnclaveSite generate(long worldSeed, BlockPos near) {
		int cellX = Math.floorDiv(near.getX(), CELL);
		int cellZ = Math.floorDiv(near.getZ(), CELL);
		long seed = hash(worldSeed, cellX, cellZ);
		Random rng = Random.create(seed);

		EnclaveOrigin origin = EnclaveOrigin.fromSalt(seed);
		MachineAttitude attitude = MachineAttitude.fromSalt(seed ^ 0xA77L, origin);
		int tech = switch (origin) {
			case ENGINEERS -> 2 + rng.nextInt(2);
			case MILITARY, MINERS -> 1 + rng.nextInt(2);
			case CULTISTS, SCAVENGERS -> rng.nextInt(2);
		};
		tech = MathHelper.clamp(tech, 0, 3);
		int resources = rng.nextInt(4);
		int hostility = switch (attitude) {
			case HATE -> 2 + rng.nextInt(2);
			case USE -> 1;
			case WORSHIP -> rng.nextInt(2);
		};

		int ax = cellX * CELL + 64 + rng.nextInt(256);
		int az = cellZ * CELL + 64 + rng.nextInt(256);
		BlockPos anchor = new BlockPos(ax, 72, az);
		String nameKey = "enclave.drmd." + origin.id + "." + rng.nextInt(4);
		return new EnclaveSite(seed, anchor, origin, attitude, tech, resources, hostility, nameKey);
	}

	/** Force origin bias (e.g. engineer camp on an iron-guild plate). */
	public static EnclaveSite generateBiased(long worldSeed, BlockPos near, EnclaveOrigin prefer) {
		EnclaveSite base = generate(worldSeed, near);
		if (prefer == null || base.origin == prefer) return base;
		long seed = base.seed ^ prefer.ordinal() * 97L;
		Random rng = Random.create(seed);
		MachineAttitude attitude = MachineAttitude.fromSalt(seed ^ 0xB11L, prefer);
		int tech = prefer == EnclaveOrigin.ENGINEERS ? 2 + rng.nextInt(2) : base.techLevel;
		return new EnclaveSite(seed, base.anchor, prefer, attitude,
				MathHelper.clamp(tech, 0, 3), base.resourcePressure,
				base.hostility, "enclave.drmd." + prefer.id + "." + rng.nextInt(4));
	}

	public NbtCompound save() {
		NbtCompound tag = new NbtCompound();
		tag.putLong("seed", seed);
		tag.putInt("x", anchor.getX());
		tag.putInt("y", anchor.getY());
		tag.putInt("z", anchor.getZ());
		tag.putString("origin", origin.id);
		tag.putString("attitude", attitude.id);
		tag.putInt("tech", techLevel);
		tag.putInt("res", resourcePressure);
		tag.putInt("host", hostility);
		tag.putString("name", nameKey);
		return tag;
	}

	public static EnclaveSite load(NbtCompound tag) {
		return new EnclaveSite(
				tag.getLong("seed"),
				new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
				EnclaveOrigin.byId(tag.getString("origin")),
				MachineAttitude.byId(tag.getString("attitude")),
				tag.getInt("tech"),
				tag.getInt("res"),
				tag.getInt("host"),
				tag.getString("name")
		);
	}

	public String shortLabel() {
		return origin.labelEn + " · " + attitude.id + " · tech " + techLevel;
	}

	private static long hash(long seed, int cx, int cz) {
		long x = seed ^ 0xE4C1A7EL ^ ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
		x ^= x >>> 33;
		x *= 0xff51afd7ed558ccdL;
		x ^= x >>> 33;
		x *= 0xc4ceb9fe1a85ec53L;
		x ^= x >>> 33;
		return x;
	}
}
