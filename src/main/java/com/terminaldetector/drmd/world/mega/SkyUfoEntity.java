package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import com.terminaldetector.drmd.world.structure.DestructionMode;
import com.terminaldetector.drmd.world.structure.StructureDelta;
import com.terminaldetector.drmd.world.structure.StructureInstance;
import com.terminaldetector.drmd.world.structure.StructureMover;
import com.terminaldetector.drmd.world.structure.StructureOccupants;
import com.terminaldetector.drmd.world.structure.StructureShockwave;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XCOM-style airborne UFO — a real enterable flying hull, not a distant prop.
 * Cruise while carrying interior entities; destroy by reactor dump / bomb / core break.
 *
 * <p>Placement/movement is delegated to {@code world.structure} infra ({@link StructureInstance} +
 * {@code StructureMover}/{@code StructureOccupants}) instead of the old from-scratch
 * clear-and-rebuild-every-move — see {@link SkyUfoHull} for the captured shape. Every public method here
 * kept its exact old signature; every external caller (weapons, worldgen, commands, the renderer) only
 * ever touched this stable surface, never the removed internal fields, so nothing outside this file and
 * {@code SkyUfoHull.java} needed to change.
 */
public class SkyUfoEntity extends Entity {
	public static final int SWARM_CAP = 12;
	public static final int MOVE_INTERVAL = 18;

	private static final TrackedData<Boolean> MATERIALIZED =
			DataTracker.registerData(SkyUfoEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Active UFOs for bomb/reactor/core lookups. */
	private static final Map<UUID, SkyUfoEntity> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<Long, UUID> CORE_INDEX = new ConcurrentHashMap<>();

	private final List<UUID> swarm = new ArrayList<>();
	private UUID macroId;
	private float cruiseYaw;
	private int burnCd;
	private int spawnCd;
	private int moveCd;
	private boolean hullReady;
	private boolean destroyed;
	/** True while intentionally clearing/rebuilding hull — ignore reactor onStateReplaced. */
	private boolean suppressCoreNotify;
	private StructureInstance instance;

	/** How this hull dies once destroyFromCore fires — chosen once at materialize time. */
	private DestructionMode destructionMode = DestructionMode.SHOCKWAVE;
	private boolean crashing;
	private int crashTicks;
	private double crashFallAccumulator;
	private PlayerEntity crashCulprit;
	private String crashReason = "structural failure";

	public SkyUfoEntity(EntityType<? extends SkyUfoEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(MATERIALIZED, false);
	}

	public boolean isMaterialized() {
		return dataTracker.get(MATERIALIZED);
	}

	public Box getInterior() {
		return instance != null ? instance.interior() : new Box(0, 0, 0, 0, 0, 0);
	}

	public BlockPos getCorePos() {
		BlockPos core = instance != null ? instance.markerPos("core") : null;
		return core != null ? core : BlockPos.ORIGIN;
	}

	public boolean containsPos(Vec3d pos) {
		return hullReady && instance != null && instance.interior().contains(pos);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient || destroyed) return;
		if (!(getWorld() instanceof ServerWorld sw)) return;

		ACTIVE.put(getUuid(), this);

		if (crashing) {
			tickCrash(sw);
			return;
		}

		if (!hullReady) {
			materialize(sw, getBlockPos());
		}

		boolean occupied = hasOccupants(sw);
		cruiseYaw += occupied ? 0.12f : 0.35f;
		double rad = Math.toRadians(cruiseYaw);
		double speed = occupied ? 0.08 : 0.28;
		Vec3d vel = new Vec3d(-Math.sin(rad) * speed, Math.sin(age * 0.015) * 0.02, Math.cos(rad) * speed);
		setVelocity(vel);
		setYaw(cruiseYaw);

		double y = MathHelper.clamp(getY(), WorldRules.SKY_PRACTICAL_MIN + 14.0, WorldRules.SKY_PRACTICAL_MAX - 10.0);
		if (Math.abs(y - getY()) > 0.5) setPosition(getX(), y, getZ());

		// Grid-crawl hull so interior stays coherent Minecraft blocks
		if (moveCd > 0) moveCd--;
		else {
			BlockPos want = BlockPos.ofFloored(getX() + vel.x * MOVE_INTERVAL,
					getY() + vel.y * MOVE_INTERVAL, getZ() + vel.z * MOVE_INTERVAL);
			want = new BlockPos(want.getX(),
					MathHelper.clamp(want.getY(), WorldRules.SKY_PRACTICAL_MIN + 14, WorldRules.SKY_PRACTICAL_MAX - 10),
					want.getZ());
			if (!want.equals(instance.anchor())) {
				relocateHull(sw, want);
			}
			moveCd = occupied ? MOVE_INTERVAL + 8 : MOVE_INTERVAL;
		}

		// Keep entity anchored to hull center
		setPosition(instance.anchor().getX() + 0.5, instance.anchor().getY() + 0.5, instance.anchor().getZ() + 0.5);

		if (macroId == null) macroId = UUID.randomUUID();
		MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.UFO, WorldRules.Layer.SKY_ARCHIPELAGO,
				instance.anchor(), 28, 12, 28, 0x55FFAA, "Sky UFO"));

		// Core still present?
		if (hullReady && !sw.getBlockState(getCorePos()).isOf(ModWorldBlocks.UNSTABLE_REACTOR)) {
			destroyFromCore(sw, null, "core ruptured");
			return;
		}

		if (burnCd > 0) burnCd--;
		else {
			burnGround(sw);
			burnCd = 40 + random.nextInt(30);
		}
		if (spawnCd > 0) spawnCd--;
		else {
			pruneSwarm(sw);
			if (swarm.size() < SWARM_CAP) {
				dropDrone(sw);
				spawnCd = occupied ? 28 : 16;
			} else spawnCd = 40;
		}
		if (age % 14 == 0) {
			sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY() - 2.0, getZ(), 5, 1.2, 0.2, 1.2, 0.01);
			sw.spawnParticles(ParticleTypes.FLAME, getX(), getY() - 2.5, getZ(), 3, 0.6, 0.3, 0.6, 0.02);
		}
	}

	private boolean hasOccupants(ServerWorld sw) {
		if (!hullReady || instance == null) return false;
		return !sw.getEntitiesByClass(PlayerEntity.class, instance.interior(), PlayerEntity::isAlive).isEmpty();
	}

	private void materialize(ServerWorld sw, BlockPos at) {
		BlockPos center = at.toImmutable();
		instance = new StructureInstance(SkyUfoHull.TEMPLATE, center);
		StructureMover.place(sw, instance);
		hullReady = true;
		destructionMode = random.nextInt(2) == 0 ? DestructionMode.CRASH : DestructionMode.SHOCKWAVE;
		dataTracker.set(MATERIALIZED, true);
		BlockPos core = instance.markerPos("core");
		if (core != null) CORE_INDEX.put(core.asLong(), getUuid());
		setPosition(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
		for (ServerPlayerEntity p : sw.getPlayers()) {
			if (squaredDistanceTo(p) < 96 * 96) {
				p.sendMessage(Text.literal("§aSky UFO hull online §7— fly the bay, dump reactor on the core."), false);
			}
		}
	}

	private void relocateHull(ServerWorld sw, BlockPos newCenter) {
		if (!hullReady || destroyed || instance == null) return;
		BlockPos before = instance.anchor();
		List<Entity> carry = StructureOccupants.collect(sw, instance, this).stream()
				.filter(e -> !(e instanceof SkyUfoEntity))
				.toList();
		BlockPos oldCore = instance.markerPos("core");
		if (oldCore != null) CORE_INDEX.remove(oldCore.asLong());
		suppressCoreNotify = true;
		try {
			StructureMover.moveTo(sw, instance, newCenter);
		} finally {
			suppressCoreNotify = false;
		}
		BlockPos newCore = instance.markerPos("core");
		if (newCore != null) CORE_INDEX.put(newCore.asLong(), getUuid());
		StructureOccupants.shiftBy(carry, Vec3d.of(newCenter.subtract(before)));
	}

	/** Advances a CRASH-mode destruction by one tick: fall + carry occupants + detect ground contact. */
	private void tickCrash(ServerWorld sw) {
		if (instance == null) {
			finishDestruction(sw, crashCulprit, crashReason, getBlockPos());
			return;
		}
		BlockPos before = instance.anchor();
		List<Entity> carry = StructureOccupants.collect(sw, instance, this).stream()
				.filter(e -> !(e instanceof SkyUfoEntity))
				.toList();
		StructureMover.DescentTick result = StructureMover.tickCrashDescent(sw, instance, crashTicks, crashFallAccumulator);
		crashTicks++;
		crashFallAccumulator = result.remainder();
		if (!instance.anchor().equals(before)) {
			StructureOccupants.shiftBy(carry, Vec3d.of(instance.anchor().subtract(before)));
		}
		setPosition(instance.anchor().getX() + 0.5, instance.anchor().getY() + 0.5, instance.anchor().getZ() + 0.5);
		if (result.touchedGround()) {
			finishDestruction(sw, crashCulprit, crashReason, getCorePos());
		}
	}

	private void burnGround(ServerWorld world) {
		BlockPos anchor = instance.anchor();
		int sx = anchor.getX();
		int sz = anchor.getZ();
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz);
		BlockPos focus = new BlockPos(sx, top, sz);
		FireSystem.igniteBlast(world, focus, 8, 7);
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				if (dx * dx + dz * dz > 30) continue;
				if (random.nextInt(4) != 0) continue;
				int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx + dx, sz + dz);
				BlockPos p = new BlockPos(sx + dx, ty - 1, sz + dz);
				BlockState st = world.getBlockState(p);
				if (st.isAir() || st.getHardness(world, p) < 0) continue;
				float hard = st.getHardness(world, p);
				if (hard >= 0 && hard <= 3.5f && !st.isOf(Blocks.BEDROCK) && !st.isOf(Blocks.LODESTONE)) {
					if (hard <= 2.0f && random.nextBoolean()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
						FireSystem.ignite(world, p, 2);
					} else {
						world.setBlockState(p, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
					}
				}
			}
		}
	}

	private void pruneSwarm(ServerWorld world) {
		swarm.removeIf(id -> {
			Entity e = world.getEntity(id);
			return e == null || !e.isAlive();
		});
	}

	private void dropDrone(ServerWorld world) {
		DroneEntity drone = ModEntities.DRONE.create(world);
		if (drone == null) return;
		Vec3d off = new Vec3d((random.nextDouble() - 0.5) * 14, -3 - random.nextDouble() * 3, (random.nextDouble() - 0.5) * 14);
		Vec3d p = getPos().add(off);
		drone.refreshPositionAndAngles(p.x, p.y, p.z, random.nextFloat() * 360, 0);
		AiRole[] roles = {AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.LASER, AiRole.MG, AiRole.SEEKER};
		drone.applyRole(roles[random.nextInt(roles.length)]);
		world.spawnEntity(drone);
		swarm.add(drone.getUuid());
	}

	/**
	 * Reactor dump / bomb / core break — catastrophic hull failure. Picks a destruction outcome chosen at
	 * materialize time: {@link DestructionMode#SHOCKWAVE} detonates immediately (the same explosion/fire/
	 * sound sequence this method always ran, now via {@link StructureShockwave}); {@link DestructionMode#CRASH}
	 * instead falls to the ground over subsequent ticks ({@link #tickCrash}) before detonating a smaller
	 * impact shockwave.
	 */
	public void destroyFromCore(ServerWorld sw, PlayerEntity culprit, String reason) {
		if (destroyed || crashing) return;
		if (destructionMode == DestructionMode.CRASH && instance != null) {
			crashing = true;
			crashTicks = 0;
			crashFallAccumulator = 0;
			crashCulprit = culprit;
			crashReason = reason;
			for (ServerPlayerEntity p : sw.getPlayers()) {
				if (squaredDistanceTo(p) < 128 * 128) {
					p.sendMessage(Text.literal("§cSky UFO reactor failing §7— it's going down."), false);
				}
			}
			return;
		}
		finishDestruction(sw, culprit, reason, getCorePos());
	}

	private void finishDestruction(ServerWorld sw, PlayerEntity culprit, String reason, BlockPos epicenter) {
		if (destroyed) return;
		destroyed = true;
		Vec3d epic = Vec3d.ofCenter(epicenter);
		StructureShockwave.detonate(sw, epic, this, culprit, 9f, 6.5f, 8f, 16, 10);
		CORE_INDEX.remove(epicenter.asLong());
		if (instance != null) {
			Set<BlockPos> positions = new HashSet<>();
			for (StructureDelta.Cell c : instance.occupiedCells().keySet()) {
				positions.add(new BlockPos(c.x(), c.y(), c.z()));
			}
			SkyUfoHull.shatter(sw, positions, epicenter);
		}
		hullReady = false;
		crashing = false;
		dataTracker.set(MATERIALIZED, false);
		for (UUID id : swarm) {
			Entity e = sw.getEntity(id);
			if (e != null) e.discard();
		}
		swarm.clear();
		for (ServerPlayerEntity p : sw.getPlayers()) {
			p.sendMessage(Text.literal("§cSky UFO destroyed §7(" + reason + ") — fly clear of the debris."), false);
		}
		if (macroId != null) MacroWorld.remove(macroId);
		ACTIVE.remove(getUuid());
		discard();
	}

	// —— static hooks ——

	public static SkyUfoEntity findContaining(ServerWorld world, Vec3d pos) {
		for (SkyUfoEntity ufo : ACTIVE.values()) {
			if (ufo.getWorld() == world && !ufo.destroyed && ufo.containsPos(pos)) return ufo;
		}
		return null;
	}

	public static SkyUfoEntity findNear(ServerWorld world, BlockPos pos, double range) {
		double r2 = range * range;
		SkyUfoEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (SkyUfoEntity ufo : ACTIVE.values()) {
			if (ufo.getWorld() != world || ufo.destroyed) continue;
			double d = ufo.getCorePos().getSquaredDistance(pos);
			if (d < r2 && d < bestD) {
				bestD = d;
				best = ufo;
			}
		}
		return best;
	}

	public static void notifyCoreBroken(ServerWorld world, BlockPos pos) {
		UUID id = CORE_INDEX.remove(pos.asLong());
		if (id == null) {
			SkyUfoEntity near = findNear(world, pos, 4);
			if (near != null && !near.suppressCoreNotify) {
				near.destroyFromCore(world, null, "reactor core destroyed");
			}
			return;
		}
		SkyUfoEntity ufo = ACTIVE.get(id);
		if (ufo != null && !ufo.suppressCoreNotify) {
			ufo.destroyFromCore(world, null, "reactor core destroyed");
		}
	}

	public static void notifyBombDetonation(ServerWorld world, BlockPos pos, float power) {
		SkyUfoEntity ufo = findNear(world, pos, 6 + power);
		if (ufo != null && (ufo.containsPos(Vec3d.ofCenter(pos)) || pos.isWithinDistance(ufo.getCorePos(), 7))) {
			ufo.destroyFromCore(world, null, "ordnance impact");
		}
	}

	public static boolean tryReactorDump(ServerWorld world, PlayerEntity user) {
		SkyUfoEntity ufo = findContaining(world, user.getPos());
		if (ufo == null) {
			ufo = findNear(world, user.getBlockPos(), 5);
		}
		if (ufo == null) return false;
		ufo.destroyFromCore(world, user, "reactor dump");
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		ACTIVE.remove(getUuid());
		CORE_INDEX.remove(getCorePos().asLong());
		if (macroId != null) MacroWorld.remove(macroId);
		if (!destroyed && getWorld() instanceof ServerWorld sw && hullReady && instance != null) {
			suppressCoreNotify = true;
			try {
				StructureMover.clear(sw, instance);
			} finally {
				suppressCoreNotify = false;
			}
		}
		super.remove(reason);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("macro")) macroId = nbt.getUuid("macro");
		cruiseYaw = nbt.getFloat("cruise");
		hullReady = false; // rebuild on next tick
		crashing = nbt.getBoolean("crashing");
		crashTicks = nbt.getInt("crashTicks");
		if (nbt.contains("crashReason")) crashReason = nbt.getString("crashReason");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (macroId != null) nbt.putUuid("macro", macroId);
		nbt.putFloat("cruise", cruiseYaw);
		nbt.putBoolean("hull", hullReady);
		nbt.putBoolean("crashing", crashing);
		nbt.putInt("crashTicks", crashTicks);
		if (crashReason != null) nbt.putString("crashReason", crashReason);
	}

	@Override
	public boolean isCollidable() {
		return false; // hull blocks provide collision
	}
}
