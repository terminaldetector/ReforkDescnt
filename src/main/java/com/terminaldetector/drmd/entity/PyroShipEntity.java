package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.flight.CrashDamage;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Pyro GX transport — 6DoF while piloted (immune to gravity torches).
 * On dismount: walk when a gravity field / solid floor is present; keep free 6DoF in open air.
 *
 * <p>Extends {@link VehicleEntity}, not a mob base — it's piloted, not AI-driven, and never had any
 * AI running ({@code initGoals()} was always empty). {@link com.terminaldetector.drmd.entity
 * .LaserBarrierCartEntity} is this codebase's existing precedent for a non-mob rideable vehicle.
 */
public class PyroShipEntity extends VehicleEntity {
	private boolean wasPiloted;
	private int landTicks;
	/** Last pilot to board — lets {@code FlightSystem.autoMountPyroShip} find this hull again on
	 *  rejoin instead of always minting a fresh one. Not exclusive ownership: boarding an unpiloted
	 *  ship (any owner, or none) claims it. */
	private UUID ownerUuid;

	// Flight stats — same defaults DescentPlayerData used before this hull owned them; that class
	// keeps its own copies as the pilotless free-6DoF fallback (see FlightSystem.tick).
	private float accel = 4200f;
	private float drag = 2.1f;
	private float maxSpeed = 2200f;

	/** The ship's single propulsion hardpoint. An empty stack means no afterburner at all — see
	 *  {@link #getAfterburnerTier()} — not just a low tier; a bare hull has nothing to burn. */
	private ItemStack propulsionModule = ItemStack.EMPTY;

	// Four weapon hardpoints. A DefaultedList backs it (not a bare array) purely so NBT persistence
	// can reuse vanilla's own Inventories.writeNbt/readNbt rather than hand-rolling ItemStack<->NBT
	// encoding — this is not a vanilla Inventory, nothing here implements that interface or exposes
	// a scrollable inventory UI, it is four fixed, individually-addressed hardpoints.
	private final DefaultedList<ItemStack> weaponSlots =
			DefaultedList.ofSize(ShipWeaponSlot.values().length, ItemStack.EMPTY);

	public UUID getOwnerUuid() { return ownerUuid; }
	public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
	public float getAccel() { return accel; }
	public void setAccel(float accel) { this.accel = accel; }
	public float getDrag() { return drag; }
	public void setDrag(float drag) { this.drag = drag; }
	public float getMaxSpeed() { return maxSpeed; }
	public void setMaxSpeed(float maxSpeed) { this.maxSpeed = maxSpeed; }

	public ItemStack getPropulsionModule() { return propulsionModule; }
	public void setPropulsionModule(ItemStack propulsionModule) { this.propulsionModule = propulsionModule; }

	/** 0 (no afterburner available) if the propulsion slot is empty, otherwise the socketed
	 *  module's own tier — see {@link com.terminaldetector.drmd.flight.AcceleratorModuleItem}. Unlike
	 *  every other tier read in the mod, this is deliberately NOT clamped into 1..4: callers must
	 *  treat 0 as "unavailable," not silently floor it to tier 1 (see FlightSystem.tick's own guard). */
	public int getAfterburnerTier() {
		if (propulsionModule.getItem() instanceof com.terminaldetector.drmd.flight.AcceleratorModuleItem module) {
			return module.tier();
		}
		return 0;
	}

	public ItemStack getWeaponSlot(ShipWeaponSlot slot) {
		return weaponSlots.get(slot.ordinal());
	}

	public void setWeaponSlot(ShipWeaponSlot slot, ItemStack stack) {
		weaponSlots.set(slot.ordinal(), stack);
	}

	public PyroShipEntity(EntityType<? extends PyroShipEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
	}

	/** {@code VehicleEntity}'s own hook — the item this entity corresponds to for drop/give
	 *  purposes. Same idiom as {@link LaserBarrierCartEntity#asItem()}: the real placeable item
	 *  that spawns this entity, not a placeholder. */
	@Override
	public Item asItem() {
		return com.terminaldetector.drmd.weapon.items.ModItems.PYRO_GX;
	}

	/** End dimension or near-space / end-space altitude bands. */
	public boolean isZeroGZone() {
		if (getWorld().getRegistryKey() == World.END) return true;
		AtmosphereBand band = AtmosphereBand.at(getY());
		if (band == AtmosphereBand.NEAR_SPACE) return true;
		return WorldRules.practicalLayer(getY()) == WorldRules.Layer.END_SPACE;
	}

	@Override
	public void tick() {
		super.tick();
		this.setNoGravity(true);
		if (getWorld().isClient) return;

		if (getFirstPassenger() instanceof ServerPlayerEntity pilot) {
			wasPiloted = true;
			landTicks = 0;
			DescentPlayerData data = DescentPlayerData.get(pilot);
			// super.tick() (above) just swept the hull through the world using the velocity this
			// same method set last tick, so a collision flag here reflects a real impact at real
			// speed — checked before the target velocity for *this* tick overwrites it below.
			if (this.horizontalCollision || this.verticalCollision) {
				float crashDmg = CrashDamage.damageFor(data.getFlightVelocity().length());
				if (crashDmg > 0f) {
					pilot.damage(getWorld().getDamageSources().flyIntoWall(), crashDmg);
				}
			}
			if (!data.isEnabled()) FlightSystem.enable(pilot, data);
			FootGravitySystem.clear(pilot.getUuid());
			Vec3d vel = data.getFlightVelocity();
			this.setVelocity(vel);
			this.velocityModified = true;
			this.setYaw(pilot.getYaw());
			this.setPitch(pilot.getPitch());
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
		// Park in place, not coast — runs on both client (local prediction) and server
		// (authoritative) unconditionally, same reasoning as tick()'s own setNoGravity(true)
		// running before that method's client check: zeroing only inside the server-only guard
		// below would leave the client's own rendered copy drifting until the next sync packet.
		this.setVelocity(Vec3d.ZERO);
		this.velocityModified = true;
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
	public ActionResult interact(PlayerEntity player, Hand hand) {
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

	// writeCustomDataToNbt/readCustomDataFromNbt are abstract on Entity with no concrete
	// implementation anywhere in the VehicleEntity chain (confirmed by CI: "abstract method ...
	// cannot be accessed directly") — under the old PathAwareEntity/LivingEntity ancestry there
	// was one to call via super, but it only ever wrote LivingEntity-tier data (health, effects)
	// this ship never used anyway, so there's nothing lost by these two methods being the entity's
	// whole NBT contract now, not a super-augmented one.
	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString("hull", "pyro");
		if (ownerUuid != null) nbt.putUuid("owner", ownerUuid);
		nbt.putFloat("accel", accel);
		nbt.putFloat("drag", drag);
		nbt.putFloat("maxSpeed", maxSpeed);
		NbtCompound weapons = new NbtCompound();
		Inventories.writeNbt(weapons, weaponSlots, getWorld().getRegistryManager());
		nbt.put("weapons", weapons);
		// Single-slot round-trip reuses Inventories.writeNbt/readNbt (proven above for the four
		// weapon hardpoints) via a throwaway one-element list, rather than a second, separate
		// ItemStack<->NBT encoding for just this one field.
		NbtCompound propulsion = new NbtCompound();
		Inventories.writeNbt(propulsion, DefaultedList.copyOf(ItemStack.EMPTY, propulsionModule), getWorld().getRegistryManager());
		nbt.put("propulsion", propulsion);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		ownerUuid = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
		if (nbt.contains("accel")) accel = nbt.getFloat("accel");
		if (nbt.contains("drag")) drag = nbt.getFloat("drag");
		if (nbt.contains("maxSpeed")) maxSpeed = nbt.getFloat("maxSpeed");
		if (nbt.contains("weapons")) {
			Inventories.readNbt(nbt.getCompound("weapons"), weaponSlots, getWorld().getRegistryManager());
		}
		if (nbt.contains("propulsion")) {
			DefaultedList<ItemStack> tmp = DefaultedList.ofSize(1, ItemStack.EMPTY);
			Inventories.readNbt(nbt.getCompound("propulsion"), tmp, getWorld().getRegistryManager());
			propulsionModule = tmp.get(0);
		}
	}
}
