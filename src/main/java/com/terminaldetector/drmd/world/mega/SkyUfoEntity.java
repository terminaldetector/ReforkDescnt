package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * XCOM-style airborne UFO — cruising sky structure (LLOD), burns surface
 * builds underneath, continually drops swarm drones.
 */
public class SkyUfoEntity extends Entity {
	public static final int SWARM_CAP = 12;

	private final List<UUID> swarm = new ArrayList<>();
	private UUID macroId;
	private float cruiseYaw;
	private int burnCd;
	private int spawnCd;

	public SkyUfoEntity(EntityType<? extends SkyUfoEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;

		cruiseYaw += 0.35f;
		double rad = Math.toRadians(cruiseYaw);
		Vec3d vel = new Vec3d(-Math.sin(rad) * 0.35, Math.sin(age * 0.02) * 0.04, Math.cos(rad) * 0.35);
		setVelocity(vel);
		velocityModified = true;
		setPosition(getPos().add(vel));
		setYaw(cruiseYaw);

		// Hold practical sky band
		double y = MathHelper.clamp(getY(), WorldRules.SKY_PRACTICAL_MIN + 10.0, WorldRules.SKY_PRACTICAL_MAX - 8.0);
		if (Math.abs(y - getY()) > 0.5) setPosition(getX(), y, getZ());

		if (macroId == null) macroId = UUID.randomUUID();
		MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.UFO, WorldRules.Layer.SKY_ARCHIPELAGO,
				getBlockPos(), 36, 10, 36, 0x55FFAA, "Sky UFO"));

		if (getWorld() instanceof ServerWorld sw) {
			if (burnCd > 0) burnCd--;
			else {
				burnGround(sw);
				burnCd = 35 + random.nextInt(25);
			}
			if (spawnCd > 0) spawnCd--;
			else {
				pruneSwarm(sw);
				if (swarm.size() < SWARM_CAP) {
					dropDrone(sw);
					spawnCd = 18;
				} else {
					spawnCd = 40;
				}
			}
			if (age % 12 == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY() - 1.5, getZ(), 6, 1.5, 0.3, 1.5, 0.01);
				sw.spawnParticles(ParticleTypes.FLAME, getX(), getY() - 2.0, getZ(), 4, 0.8, 0.4, 0.8, 0.02);
			}
		}
	}

	private void burnGround(ServerWorld world) {
		int sx = getBlockX();
		int sz = getBlockZ();
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz);
		BlockPos focus = new BlockPos(sx, top, sz);
		FireSystem.igniteBlast(world, focus, 10, 8);
		// Scorch / soft-break player-scale builds under the beam
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				if (dx * dx + dz * dz > 36) continue;
				if (random.nextInt(3) != 0) continue;
				int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx + dx, sz + dz);
				BlockPos p = new BlockPos(sx + dx, ty - 1, sz + dz);
				BlockState st = world.getBlockState(p);
				if (st.isAir() || st.getHardness(world, p) < 0) continue;
				float hard = st.getHardness(world, p);
				if (hard >= 0 && hard <= 3.5f && !st.isOf(Blocks.BEDROCK) && !st.isOf(Blocks.LODESTONE)) {
					if (random.nextBoolean()) {
						world.setBlockState(p, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
					} else if (hard <= 2.0f) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
						FireSystem.ignite(world, p, 2);
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
		Vec3d off = new Vec3d((random.nextDouble() - 0.5) * 10, -2 - random.nextDouble() * 4, (random.nextDouble() - 0.5) * 10);
		Vec3d p = getPos().add(off);
		drone.refreshPositionAndAngles(p.x, p.y, p.z, random.nextFloat() * 360, 0);
		AiRole[] roles = {AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.LASER, AiRole.MG, AiRole.SEEKER};
		drone.applyRole(roles[random.nextInt(roles.length)]);
		world.spawnEntity(drone);
		swarm.add(drone.getUuid());
	}

	@Override
	public void remove(RemovalReason reason) {
		if (macroId != null) MacroWorld.remove(macroId);
		super.remove(reason);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("macro")) macroId = nbt.getUuid("macro");
		cruiseYaw = nbt.getFloat("cruise");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (macroId != null) nbt.putUuid("macro", macroId);
		nbt.putFloat("cruise", cruiseYaw);
	}

	@Override
	public boolean isCollidable() {
		return false;
	}
}
