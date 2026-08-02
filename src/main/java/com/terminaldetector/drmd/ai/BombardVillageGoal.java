package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.bombardment.AerialBombEntity;
import com.terminaldetector.drmd.world.bombardment.OrdnanceType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Siege behaviour for artillery / RPG / seeker — climb over a village cluster and drop
 * aerial ordnance on purpose. GMod standoff roles did not have this; it uses the shared
 * {@link AerialBombEntity} bay so craters and smoke match player bombardment.
 */
public final class BombardVillageGoal extends Goal {
	private final DroneEntity drone;
	private BlockPos villageAim;
	private int bombCooldown;
	private int repathTicks;

	public BombardVillageGoal(DroneEntity drone) {
		this.drone = drone;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!drone.getRole().villageBomber) return false;
		if (bombCooldown > 0) {
			bombCooldown--;
			return false;
		}
		LivingEntity t = drone.getTarget();
		// Players take dogfight priority — leave village runs for CombatGoal.
		if (t instanceof PlayerEntity) return false;
		if (t instanceof VillagerEntity || t instanceof IronGolemEntity) {
			villageAim = t.getBlockPos();
			return true;
		}
		if (t != null) return false;
		villageAim = findVillageFocus();
		return villageAim != null;
	}

	@Override
	public boolean shouldContinue() {
		return drone.getRole().villageBomber && villageAim != null && bombCooldown <= 0;
	}

	@Override
	public void stop() {
		villageAim = null;
	}

	@Override
	public void tick() {
		if (villageAim == null) return;
		if (--repathTicks <= 0) {
			repathTicks = 40;
			BlockPos again = findVillageFocus();
			if (again != null) villageAim = again;
		}

		Vec3d aim = Vec3d.ofCenter(villageAim).add(0, 18, 0);
		Vec3d pos = drone.getPos();
		Vec3d to = aim.subtract(pos);
		double dist = to.length();
		Vec3d desire = dist > 1e-4 ? to.normalize() : new Vec3d(0, 1, 0);

		// Blend light swarm so bombers still fly as a cloud.
		desire = desire.add(DroneSwarmAi.compute(drone, drone.getTarget()).multiply(0.55));
		if (desire.lengthSquared() > 1e-6) desire = desire.normalize();

		double maxSpd = DescentMod.su(drone.getRole().speed) * 0.85;
		drone.setVelocity(desire.multiply(maxSpd / 20.0));
		drone.velocityModified = true;
		FlightAttitude.steer(drone, drone.getVelocity(), 7f, 1f / 20f);

		// Over the settlement and high enough — release.
		double horizontal = Math.sqrt(
				(pos.x - villageAim.getX()) * (pos.x - villageAim.getX())
						+ (pos.z - villageAim.getZ()) * (pos.z - villageAim.getZ()));
		if (horizontal < 14 && pos.y > villageAim.getY() + 10) {
			dropBomb();
			bombCooldown = 20 * 8; // 8s between sticks
			villageAim = null;
		} else if (drone.getTarget() instanceof LivingEntity living
				&& (living instanceof VillagerEntity || living instanceof IronGolemEntity)
				&& pos.distanceTo(living.getPos()) < DescentMod.su(900)) {
			drone.tryFireAt(living);
		}
	}

	private void dropBomb() {
		if (!(drone.getWorld() instanceof ServerWorld sw)) return;
		AerialBombEntity bomb = ModEntities.AERIAL_BOMB.create(sw);
		if (bomb == null) return;
		OrdnanceType type = switch (drone.getRole()) {
			case ARTILLERY -> OrdnanceType.HEAVY_CLUSTER;
			case SEEKER -> OrdnanceType.LASER_GUIDED;
			default -> OrdnanceType.CLUSTER;
		};
		// Body-down eject from drone attitude (360°-safe), not world −Y + pitch 90.
		Vec3d fwd = drone.getRotationVec(1f);
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(fwd);
		Vec3d down = fwd.crossProduct(right).normalize().multiply(-1);
		if (down.lengthSquared() < 1e-6) down = new Vec3d(0, -1, 0);
		Vec3d drop = drone.getPos().add(down.multiply(0.9));
		bomb.refreshPositionAndAngles(drop.x, drop.y, drop.z, 0f, 0f);
		Vec3d eject = drone.getVelocity().multiply(0.55).add(down.multiply(0.45)).add(fwd.multiply(0.2));
		bomb.setVelocity(eject);
		bomb.configure(type, drone, villageAim);
		sw.spawnEntity(bomb);
		com.terminaldetector.drmd.world.smoke.SmokeSystem.emit(
				drop, com.terminaldetector.drmd.world.smoke.SmokeSystem.Source.BOMB_TRAIL, 0.7f, 0.4f, 40,
				down.multiply(0.03));
	}

	private BlockPos findVillageFocus() {
		Box box = drone.getBoundingBox().expand(96, 48, 96);
		List<VillagerEntity> villagers = drone.getWorld().getEntitiesByClass(
				VillagerEntity.class, box, VillagerEntity::isAlive);
		if (villagers.isEmpty()) {
			List<IronGolemEntity> golems = drone.getWorld().getEntitiesByClass(
					IronGolemEntity.class, box, IronGolemEntity::isAlive);
			if (golems.isEmpty()) return null;
			return golems.get(drone.getRandom().nextInt(golems.size())).getBlockPos();
		}
		// Aim at the centroid of nearby villagers — the actual settlement, not one stray.
		double x = 0, y = 0, z = 0;
		int n = Math.min(villagers.size(), 12);
		for (int i = 0; i < n; i++) {
			VillagerEntity v = villagers.get(i);
			x += v.getX();
			y += v.getY();
			z += v.getZ();
		}
		return BlockPos.ofFloored(x / n, y / n, z / n);
	}
}
