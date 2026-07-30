package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Colossal drone swarm — a world element, not a single boss.
 * Anchor entity that maintains a cloud of assault drones and a macro silhouette.
 */
public class DroneSwarmEntity extends Entity {
	public static final int SWARM_SIZE = 18;
	private final List<UUID> members = new ArrayList<>();
	private UUID macroId;
	private int spawnCooldown;

	public DroneSwarmEntity(EntityType<? extends DroneSwarmEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;

		// Slow drift of swarm center
		setVelocity(getVelocity().multiply(0.95).add(
				(random.nextDouble() - 0.5) * 0.02,
				(random.nextDouble() - 0.5) * 0.01,
				(random.nextDouble() - 0.5) * 0.02));
		velocityModified = true;
		setPosition(getPos().add(getVelocity()));

		if (macroId == null) {
			macroId = UUID.randomUUID();
			MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.SWARM, WorldRules.Layer.SKY_ARCHIPELAGO,
					getBlockPos(), 48, 24, 48, 0xCC3333, "Drone Swarm"));
		} else {
			MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.SWARM, WorldRules.Layer.SKY_ARCHIPELAGO,
					getBlockPos(), 48, 24, 48, 0xCC3333, "Drone Swarm"));
		}

		if (spawnCooldown > 0) {
			spawnCooldown--;
			return;
		}
		pruneMembers();
		if (members.size() < SWARM_SIZE && getWorld() instanceof ServerWorld sw) {
			spawnOne(sw);
			spawnCooldown = 20;
		}
	}

	private void pruneMembers() {
		if (!(getWorld() instanceof ServerWorld sw)) return;
		members.removeIf(id -> {
			Entity e = sw.getEntity(id);
			return e == null || !e.isAlive();
		});
	}

	private void spawnOne(ServerWorld sw) {
		DroneEntity drone = ModEntities.DRONE.create(sw);
		if (drone == null) return;
		Vec3d off = new Vec3d(
				(random.nextDouble() - 0.5) * 30,
				(random.nextDouble() - 0.5) * 16,
				(random.nextDouble() - 0.5) * 30);
		Vec3d p = getPos().add(off);
		drone.refreshPositionAndAngles(p.x, p.y, p.z, random.nextFloat() * 360, 0);
		AiRole[] roles = {AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.MG, AiRole.LASER};
		drone.applyRole(roles[random.nextInt(roles.length)]);
		sw.spawnEntity(drone);
		members.add(drone.getUuid());
	}

	@Override
	public void remove(RemovalReason reason) {
		if (macroId != null) MacroWorld.remove(macroId);
		super.remove(reason);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("macro")) macroId = nbt.getUuid("macro");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (macroId != null) nbt.putUuid("macro", macroId);
	}

	@Override
	public boolean isCollidable() {
		return false;
	}
}
