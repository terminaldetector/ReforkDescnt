package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.flight.FlightSystem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Placeholder Descent-like transport (Pyro-class scout).
 * Rideable hull that inherits 6DoF flight while piloted.
 */
public class PyroShipEntity extends PathAwareEntity {
	public PyroShipEntity(EntityType<? extends PyroShipEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 120)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.8)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 1.2)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
	}

	@Override
	protected void initGoals() {}

	@Override
	public void tick() {
		super.tick();
		this.setNoGravity(true);
		if (getWorld().isClient) return;

		if (getFirstPassenger() instanceof ServerPlayerEntity pilot) {
			DescentPlayerData data = DescentPlayerData.get(pilot);
			if (!data.isEnabled()) {
				data.setEnabled(true);
			}
			// Match ship to pilot flight velocity
			Vec3d vel = data.getFlightVelocity();
			this.setVelocity(vel);
			this.velocityModified = true;
			this.setYaw(pilot.getYaw());
			this.setPitch(pilot.getPitch());
			this.bodyYaw = pilot.getYaw();
		} else {
			// Idle drift
			this.setVelocity(getVelocity().multiply(0.92));
		}
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!getWorld().isClient && !hasPassengers()) {
			player.startRiding(this);
			if (player instanceof ServerPlayerEntity sp) {
				DescentPlayerData data = DescentPlayerData.get(sp);
				data.setEnabled(true);
				data.ensureInit();
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(net.minecraft.entity.Entity passenger) {
		return getPassengerList().isEmpty();
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(net.minecraft.entity.Entity passenger, net.minecraft.entity.EntityDimensions dimensions, float scaleFactor) {
		return new Vec3d(0, 0.35, 0);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean collidesWith(net.minecraft.entity.Entity other) {
		return true;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("hull", "pyro");
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
	}
}
