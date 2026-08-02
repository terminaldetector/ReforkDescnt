package com.terminaldetector.drmd.world.dungeon;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.ModBlocks;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Descent-style reactor breach: destroy core → escape timer → procedural exit
 * (or dig / mines yourself). Timer expiry detonates the installation.
 */
public final class FacilityReactorFight {
	public enum Phase { ARMED, BREACH, ESCAPED, DETONATED }

	public static final class Facility {
		public final UUID id;
		public final BlockPos core;
		public final DungeonVitality vitality;
		public final boolean orbital;
		public Phase phase;
		public int ticksLeft;
		public BlockPos exit;

		Facility(UUID id, BlockPos core, DungeonVitality vitality, boolean orbital,
				Phase phase, int ticksLeft, BlockPos exit) {
			this.id = id;
			this.core = core;
			this.vitality = vitality;
			this.orbital = orbital;
			this.phase = phase;
			this.ticksLeft = ticksLeft;
			this.exit = exit;
		}
	}

	private static final int ESCAPE_TICKS = 20 * 90;
	private static final Map<UUID, Facility> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<Long, UUID> BY_CORE = new ConcurrentHashMap<>();

	private FacilityReactorFight() {}

	public static void register(ServerWorld world, BlockPos core, DungeonVitality vitality) {
		if (!vitality.hasLivingReactor) return;
		UUID id = UUID.nameUUIDFromBytes(("facility-" + core.toShortString()).getBytes());
		boolean orbital = WorldLevels.at(core.getY()) == WorldLevels.Level.ORBITAL
				|| WorldLevels.at(core.getY()) == WorldLevels.Level.END
				|| world.getRegistryKey() == World.END;
		Facility f = new Facility(id, core.toImmutable(), vitality, orbital, Phase.ARMED, 0, null);
		ACTIVE.put(id, f);
		BY_CORE.put(core.asLong(), id);
		DescentMod.LOGGER.info("Facility {} registered @ {} vitality={}",
				id, core.toShortString(), vitality);
	}

	public static void onReactorBroken(ServerWorld world, BlockPos pos) {
		UUID id = BY_CORE.remove(pos.asLong());
		if (id == null) {
			for (var e : BY_CORE.entrySet()) {
				BlockPos c = BlockPos.fromLong(e.getKey());
				if (c.isWithinDistance(pos, 4)) {
					id = e.getValue();
					BY_CORE.remove(e.getKey());
					break;
				}
			}
		}
		if (id == null) return;
		Facility f = ACTIVE.get(id);
		if (f == null || f.phase != Phase.ARMED) return;

		BlockPos exit = carveProceduralExit(world, f.core, world.getRandom());
		f.phase = Phase.BREACH;
		f.ticksLeft = ESCAPE_TICKS;
		f.exit = exit;

		SmokeSystem.emitExplosion(Vec3d.ofCenter(f.core), 4f);
		FireSystem.igniteBlast(world, f.core, 6, 4);
		world.playSound(null, f.core.getX() + 0.5, f.core.getY() + 0.5, f.core.getZ() + 0.5,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.2f, 0.6f);

		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(Vec3d.ofCenter(f.core)) > 80 * 80) continue;
			p.sendMessage(Text.literal(
					"§cREACTOR BREACH §7— escape timer §f" + (ESCAPE_TICKS / 20)
							+ "s§7. Find the §aexit§7, dig out, or plant mines."), false);
			if (exit != null) {
				p.sendMessage(Text.literal("§8Procedural exit marked near "
						+ exit.toShortString()), true);
			}
		}
	}

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (Facility f : ACTIVE.values()) {
			if (f.phase != Phase.BREACH) continue;
			ServerWorld host = resolveHost(server, f.core);
			f.ticksLeft--;

			if (f.exit != null) {
				for (ServerPlayerEntity p : host.getPlayers()) {
					if (p.squaredDistanceTo(Vec3d.ofCenter(f.exit)) < 9) {
						f.phase = Phase.ESCAPED;
						p.sendMessage(Text.literal("§aEscaped reactor facility"), false);
						BY_CORE.values().removeIf(id -> id.equals(f.id));
						return;
					}
				}
			}

			if (f.ticksLeft % 40 == 0) {
				SmokeSystem.emit(Vec3d.ofCenter(f.core), SmokeSystem.Source.REACTOR, 3.5f, 0.9f, 80);
				host.spawnParticles(ParticleTypes.LARGE_SMOKE,
						f.core.getX() + 0.5, f.core.getY() + 1, f.core.getZ() + 0.5,
						24, 2, 1.5, 2, 0.02);
				for (ServerPlayerEntity p : host.getPlayers()) {
					if (p.squaredDistanceTo(Vec3d.ofCenter(f.core)) > 96 * 96) continue;
					p.sendMessage(Text.literal("§cReactor critical §f" + (f.ticksLeft / 20) + "s"), true);
				}
			}

			if (f.ticksLeft <= 0) {
				detonate(host, f);
			}
		}
	}

	private static ServerWorld resolveHost(MinecraftServer server, BlockPos core) {
		for (ServerWorld w : server.getWorlds()) {
			if (w.isChunkLoaded(core.getX() >> 4, core.getZ() >> 4)) return w;
		}
		return server.getOverworld();
	}

	private static void detonate(ServerWorld world, Facility f) {
		BlockPos core = f.core;
		SmokeSystem.emitExplosion(Vec3d.ofCenter(core), 10f);
		FireSystem.igniteBlast(world, core, 12, 8);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -18; x <= 18; x++) {
			for (int y = -12; y <= 12; y++) {
				for (int z = -18; z <= 18; z++) {
					if (x * x + y * y + z * z > 22 * 22) continue;
					m.set(core.getX() + x, core.getY() + y, core.getZ() + z);
					if (!world.isInBuildLimit(m)) continue;
					if (world.getBlockState(m).isAir()) continue;
					if (world.getRandom().nextFloat() < 0.55f) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (world.getRandom().nextFloat() < 0.35f) {
						world.setBlockState(m, Blocks.BLACKSTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(m, Blocks.BASALT.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		world.createExplosion(null, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5,
				8f, true, World.ExplosionSourceType.TNT);

		ReactorAftermath.onFacilityDetonation(world, core, f.orbital);
		f.phase = Phase.DETONATED;

		for (ServerPlayerEntity p : world.getPlayers()) {
			p.sendMessage(Text.literal(f.orbital
					? "§4ORBITAL REACTOR DETONATION §7— station breaking up; debris falling planetward."
					: "§4REACTOR DETONATION §7— facility destroyed. Ash / smoke field active."), false);
		}
	}

	public static BlockPos carveProceduralExit(ServerWorld world, BlockPos core, Random random) {
		Direction dir = Direction.Type.HORIZONTAL.random(random);
		if (random.nextBoolean()) dir = Direction.UP;
		BlockPos.Mutable cur = core.mutableCopy();
		BlockPos exit = null;
		for (int i = 0; i < 48; i++) {
			cur.move(dir);
			for (int ox = -2; ox <= 2; ox++) {
				for (int oy = -2; oy <= 2; oy++) {
					for (int oz = -2; oz <= 2; oz++) {
						if (ox * ox + oy * oy + oz * oz > 5) continue;
						BlockPos p = cur.add(ox, oy, oz);
						if (world.isInBuildLimit(p)) {
							world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
			exit = cur.toImmutable();
			if (i > 20 && world.getBlockState(cur.offset(dir, 3)).isAir()) break;
		}
		if (exit != null) {
			world.setBlockState(exit, ModBlocks.NAV_NODE.getDefaultState(), Block.NOTIFY_ALL);
			world.setBlockState(exit.offset(dir.getOpposite()),
					Blocks.LIME_CONCRETE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		return exit;
	}

	public static void clear() {
		ACTIVE.clear();
		BY_CORE.clear();
	}

	public static int activeBreaches() {
		int n = 0;
		for (Facility f : ACTIVE.values()) if (f.phase == Phase.BREACH) n++;
		return n;
	}
}
