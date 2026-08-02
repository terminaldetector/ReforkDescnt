package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
	private BlockPos hullCenter = BlockPos.ORIGIN;
	private BlockPos corePos = BlockPos.ORIGIN;
	private final Set<BlockPos> hullBlocks = new HashSet<>();
	private Box interior = new Box(0, 0, 0, 0, 0, 0);

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
		return interior;
	}

	public BlockPos getCorePos() {
		return corePos;
	}

	public boolean containsPos(Vec3d pos) {
		return hullReady && interior.contains(pos);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient || destroyed) return;
		if (!(getWorld() instanceof ServerWorld sw)) return;

		ACTIVE.put(getUuid(), this);

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
			if (!want.equals(hullCenter)) {
				relocateHull(sw, want);
			}
			moveCd = occupied ? MOVE_INTERVAL + 8 : MOVE_INTERVAL;
		}

		// Keep entity anchored to hull center
		setPosition(hullCenter.getX() + 0.5, hullCenter.getY() + 0.5, hullCenter.getZ() + 0.5);

		if (macroId == null) macroId = UUID.randomUUID();
		MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.UFO, WorldRules.Layer.SKY_ARCHIPELAGO,
				hullCenter, 28, 12, 28, 0x55FFAA, "Sky UFO"));

		// Core still present?
		if (hullReady && !sw.getBlockState(corePos).isOf(ModWorldBlocks.UNSTABLE_REACTOR)) {
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
		if (!hullReady) return false;
		return !sw.getEntitiesByClass(PlayerEntity.class, interior, PlayerEntity::isAlive).isEmpty();
	}

	private void materialize(ServerWorld sw, BlockPos at) {
		BlockPos center = at.toImmutable();
		SkyUfoHull.Built built = SkyUfoHull.build(sw, center);
		hullCenter = built.center();
		corePos = built.core();
		hullBlocks.clear();
		hullBlocks.addAll(built.blocks());
		interior = built.interior();
		hullReady = true;
		dataTracker.set(MATERIALIZED, true);
		CORE_INDEX.put(corePos.asLong(), getUuid());
		setPosition(hullCenter.getX() + 0.5, hullCenter.getY() + 0.5, hullCenter.getZ() + 0.5);
		for (ServerPlayerEntity p : sw.getPlayers()) {
			if (squaredDistanceTo(p) < 96 * 96) {
				p.sendMessage(Text.literal("§aSky UFO hull online §7— fly the bay, dump reactor on the core."), false);
			}
		}
	}

	private void relocateHull(ServerWorld sw, BlockPos newCenter) {
		if (!hullReady || destroyed) return;
		List<Entity> carry = SkyUfoHull.collectInterior(sw, interior, this);
		Vec3d delta = Vec3d.of(newCenter.subtract(hullCenter));
		CORE_INDEX.remove(corePos.asLong());
		suppressCoreNotify = true;
		try {
			SkyUfoHull.clear(sw, hullBlocks);
			SkyUfoHull.Built built = SkyUfoHull.build(sw, newCenter);
			hullCenter = built.center();
			corePos = built.core();
			hullBlocks.clear();
			hullBlocks.addAll(built.blocks());
			interior = built.interior();
		} finally {
			suppressCoreNotify = false;
		}
		CORE_INDEX.put(corePos.asLong(), getUuid());
		SkyUfoHull.shiftEntities(carry, delta);
	}

	private void burnGround(ServerWorld world) {
		int sx = hullCenter.getX();
		int sz = hullCenter.getZ();
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

	/** Reactor dump / bomb / core break — catastrophic hull failure. */
	public void destroyFromCore(ServerWorld sw, PlayerEntity culprit, String reason) {
		if (destroyed) return;
		destroyed = true;
		BlockPos epicenter = corePos;
		Vec3d epic = Vec3d.ofCenter(epicenter);
		if (culprit != null) {
			WeaponFx.explode(culprit, sw, epic, 180f, 9f, DamageClass.EXPLOSIVE, true);
		} else {
			sw.createExplosion(this, epic.x, epic.y, epic.z, 6.5f, true, World.ExplosionSourceType.TNT);
			SmokeSystem.emitExplosion(epic, 8f);
		}
		FireSystem.igniteBlast(sw, epicenter, 16, 10);
		sw.playSound(null, epicenter.getX() + 0.5, epicenter.getY() + 0.5, epicenter.getZ() + 0.5,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.5f, 0.55f);
		CORE_INDEX.remove(epicenter.asLong());
		SkyUfoHull.shatter(sw, hullBlocks, epicenter);
		hullReady = false;
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
			double d = ufo.corePos.getSquaredDistance(pos);
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
		if (ufo != null && (ufo.containsPos(Vec3d.ofCenter(pos)) || pos.isWithinDistance(ufo.corePos, 7))) {
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
		CORE_INDEX.remove(corePos.asLong());
		if (macroId != null) MacroWorld.remove(macroId);
		if (!destroyed && getWorld() instanceof ServerWorld sw && hullReady) {
			suppressCoreNotify = true;
			try {
				SkyUfoHull.clear(sw, hullBlocks);
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
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (macroId != null) nbt.putUuid("macro", macroId);
		nbt.putFloat("cruise", cruiseYaw);
		nbt.putBoolean("hull", hullReady);
	}

	@Override
	public boolean isCollidable() {
		return false; // hull blocks provide collision
	}
}
