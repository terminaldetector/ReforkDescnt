package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import com.terminaldetector.drmd.world.build.ConstructionMode;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import com.terminaldetector.drmd.world.gravity.GravityFields;
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

import java.util.UUID;

/**
 * Pyro GX transport — 6DoF while piloted (immune to gravity torches).
 * On dismount: walk when a gravity field / solid floor is present; keep free 6DoF in open air.
 */
public class PyroShipEntity extends PathAwareEntity {
	private boolean wasPiloted;
	private int landTicks;
	/** Last pilot to board — lets {@code FlightSystem.autoMountPyroShip} find this hull again on
	 *  rejoin instead of always minting a fresh one. Not exclusive ownership: boarding an unpiloted
	 *  ship (any owner, or none) claims it. */
	private UUID ownerUuid;

	public UUID getOwnerUuid() { return ownerUuid; }
	public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

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
			if (!data.isEnabled()) FlightSystem.enable(pilot, data);
			FootGravitySystem.clear(pilot.getUuid());
			Vec3d vel = data.getFlightVelocity();
			this.setVelocity(vel);
			this.velocityModified = true;
			this.setYaw(pilot.getYaw());
			this.setPitch(pilot.getPitch());
			this.bodyYaw = pilot.getYaw();
		} else if (isZeroGZone()) {
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
			DescentPlayerData data = DescentPlayerData.get(sp);
			if (isZeroGZone()) {
				LocalOrientation.setUp(sp.getUuid(), new Vec3d(0, 1, 0));
				FootGravitySystem.clear(sp.getUuid());
				ConstructionMode.set(sp, false);
				sp.sendMessage(Text.literal("§bPyro GX §7hangs in zero-g — board again anytime."), false);
				ModNetworking.syncPlayer(sp, data);
				return;
			}

			// Surface with a gravity field / solid floor → walk. Open air → keep free 6DoF
			// (disabling here made the cockpit/flight "fall off" after every exit).
			GravityFields.Sample field = GravityFields.sample(getWorld(), sp.getPos());
			if (field != null) {
				FlightSystem.disable(sp, data);
				data = DescentPlayerData.get(sp);
				FootGravitySystem.adoptAt(sp, sp.getPos());
				ConstructionMode.onShipLanded(sp);
				String axis = axisName(field.upDir());
				sp.sendMessage(Text.literal(
						"§aLocal gravity §f" + field.label() + " §7— walk with UP=" + axis), false);
				ModNetworking.syncPlayer(sp, data);
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
				FlightSystem.disable(sp, data);
				data = DescentPlayerData.get(sp);
				LocalOrientation.setFromDirection(sp.getUuid(), floor);
				if (!FootGravitySystem.isWorldUp(LocalOrientation.getUp(sp.getUuid()))) {
					FootGravitySystem.adoptClient(sp.getUuid(), LocalOrientation.getUp(sp.getUuid()));
					sp.setNoGravity(true);
				}
				ConstructionMode.onShipLanded(sp);
				sp.sendMessage(Text.literal("§7Local floor locked to §f" + floor.asString()), false);
				ModNetworking.syncPlayer(sp, data);
				return;
			}

			LocalOrientation.setUp(sp.getUuid(), new Vec3d(0, 1, 0));
			FootGravitySystem.clear(sp.getUuid());
			FlightSystem.enable(sp, data);
			sp.sendMessage(Text.literal("§b6DoF §7— free flight after Pyro exit."), false);
		}
	}

	private static String axisName(Vec3d up) {
		double ax = Math.abs(up.x), ay = Math.abs(up.y), az = Math.abs(up.z);
		if (ay >= ax && ay >= az) return up.y >= 0 ? "+Y (floor)" : "-Y (ceiling)";
		if (ax >= az) return up.x >= 0 ? "+X (wall)" : "-X (wall)";
		return up.z >= 0 ? "+Z (wall)" : "-Z (wall)";
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!getWorld().isClient && !hasPassengers()) {
			player.startRiding(this);
			if (player instanceof ServerPlayerEntity sp) {
				this.ownerUuid = sp.getUuid();
				DescentPlayerData data = DescentPlayerData.get(sp);
				data.setLastShipUuid(this.getUuid());
				com.terminaldetector.drmd.flight.FlightSystem.enable(sp, data);
				ConstructionMode.set(sp, false);
				LocalOrientation.setUp(sp.getUuid(), new Vec3d(0, 1, 0));
				sp.sendMessage(Text.literal("§bPyro GX §7— thrusters online (gravity torches ignored)"), false);
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
		if (ownerUuid != null) nbt.putUuid("owner", ownerUuid);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		ownerUuid = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
	}
}
