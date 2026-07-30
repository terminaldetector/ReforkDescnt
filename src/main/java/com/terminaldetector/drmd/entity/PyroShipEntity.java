package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import com.terminaldetector.drmd.world.build.ConstructionMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Pyro GX transport — 6DoF while piloted; zero-g hang in End / near-space when empty.
 */
public class PyroShipEntity extends PathAwareEntity {
	private boolean wasPiloted;
	private int landTicks;

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

	/** End dimension or near-space / end-space altitude bands. */
	public boolean isZeroGZone() {
		if (getWorld().getRegistryKey() == World.END) return true;
		AtmosphereBand band = AtmosphereBand.at(getY());
		if (band == AtmosphereBand.NEAR_SPACE) return true;
		return WorldRules.practicalLayer(getY()) == WorldRules.Layer.END_SPACE;
	}

	@Override
	protected void initGoals() {}

	@Override
	public void tick() {
		super.tick();
		this.setNoGravity(true);
		if (getWorld().isClient) return;

		if (getFirstPassenger() instanceof ServerPlayerEntity pilot) {
			wasPiloted = true;
			landTicks = 0;
			DescentPlayerData data = DescentPlayerData.get(pilot);
			if (!data.isEnabled()) data.setEnabled(true);
			Vec3d vel = data.getFlightVelocity();
			this.setVelocity(vel);
			this.velocityModified = true;
			this.setYaw(pilot.getYaw());
			this.setPitch(pilot.getPitch());
			this.bodyYaw = pilot.getYaw();
		} else if (isZeroGZone()) {
			// Hang in weightlessness — tiny damp so it drifts, never "lands"
			this.setVelocity(getVelocity().multiply(0.997));
			this.velocityModified = true;
			wasPiloted = false;
			landTicks = 0;
		} else {
			this.setVelocity(getVelocity().multiply(0.88));
			if (wasPiloted && getVelocity().lengthSquared() < 0.04) {
				landTicks++;
				if (landTicks > 8) wasPiloted = false;
			}
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient && passenger instanceof ServerPlayerEntity sp) {
			if (isZeroGZone()) {
				// Soft local up — no forced floor lock in End vacuum
				LocalOrientation.setUp(sp.getUuid(), new Vec3d(0, 1, 0));
				ConstructionMode.set(sp, false);
				sp.sendMessage(Text.literal("§bPyro GX §7hangs in zero-g — board again anytime."), false);
				return;
			}
			Direction floor = Direction.UP;
			boolean found = false;
			for (Direction d : Direction.values()) {
				BlockPos p = getBlockPos().offset(d.getOpposite());
				if (getWorld().getBlockState(p).isSolidBlock(getWorld(), p)) {
					floor = d;
					found = true;
					break;
				}
			}
			if (found) {
				LocalOrientation.setFromDirection(sp.getUuid(), floor);
				ConstructionMode.onShipLanded(sp);
				sp.sendMessage(Text.literal("§7Local floor locked to §f" + floor.asString()), false);
			} else {
				sp.sendMessage(Text.literal("§7Pyro GX drifting — no surface lock."), false);
			}
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
				ConstructionMode.set(sp, false);
				sp.sendMessage(Text.literal("§bPyro GX §7— Tab+H · 3D terrain map"), false);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty();
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, net.minecraft.entity.EntityDimensions dimensions, float scaleFactor) {
		return new Vec3d(0, 0.35, 0);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean collidesWith(Entity other) {
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
