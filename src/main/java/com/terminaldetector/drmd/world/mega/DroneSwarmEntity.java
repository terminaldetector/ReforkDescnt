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
	/** Blocks from the anchor beyond which a member gets pulled back into the cloud. */
	private static final double COHESION_RADIUS = 46.0;
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
		// The anchor faces its own drift, so the macro silhouette turns with the cloud.
		com.terminaldetector.drmd.ai.FlightAttitude.steerEntity(this, getVelocity(), 2f, 1f / 20f);

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
		if (getWorld() instanceof ServerWorld sw) {
			holdFormation(sw);
			if (members.size() < SWARM_SIZE) {
				spawnOne(sw);
				spawnCooldown = 20;
			}
		}
	}

	/**
	 * Cohesion pass — members that wander past the cloud radius get nudged back toward the anchor.
	 *
	 * <p>Their own combat AI keeps full authority inside the radius; this only stops a swarm from
	 * dissolving into a line of stragglers when individuals chase a target across the map.
	 */
	private void holdFormation(ServerWorld sw) {
		if (age % 10 != 0) return;
		Vec3d centre = getPos();
		for (UUID id : members) {
			Entity e = sw.getEntity(id);
			if (e == null || !e.isAlive()) continue;
			Vec3d offset = centre.subtract(e.getPos());
			double dist = offset.length();
			if (dist < COHESION_RADIUS || dist < 1e-3) continue;
			double pull = Math.min(0.22, (dist - COHESION_RADIUS) * 0.01);
			Vec3d nudge = offset.multiply(pull / dist);
			e.setVelocity(e.getVelocity().add(nudge));
			e.velocityModified = true;
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
		Vec3d off = new Vec3d(
				(random.nextDouble() - 0.5) * 30,
				(random.nextDouble() - 0.5) * 16,
				(random.nextDouble() - 0.5) * 30);
		Vec3d p = getPos().add(off);

		// Roughly a quarter of the cloud are scanners — they hold standoff and lob rocket
		// salvos while the drone roles press in, so the swarm fights at two ranges at once.
		if (random.nextInt(4) == 0) {
			var scanner = ModEntities.SCANNER.create(sw);
			if (scanner == null) return;
			scanner.refreshPositionAndAngles(p.x, p.y, p.z, random.nextFloat() * 360, 0);
			sw.spawnEntity(scanner);
			members.add(scanner.getUuid());
			return;
		}

		DroneEntity drone = ModEntities.DRONE.create(sw);
		if (drone == null) return;
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
