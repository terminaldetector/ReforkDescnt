package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Ancient Reactor Keeper — colossal sentinel tied to industrial cores.
 * World element: orbits a point, pulses energy, sparingly engages.
 */
public class ReactorKeeperEntity extends HostileEntity {
	private UUID macroId;
	private Vec3d anchor = Vec3d.ZERO;
	private float orbitAngle;

	public ReactorKeeperEntity(EntityType<? extends ReactorKeeperEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 1500)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.25);
	}

	/** Pin orbit center (lunar / End micro-reactor arenas). */
	public void setAnchor(Vec3d a) {
		this.anchor = a;
	}

	@Override
	protected void initGoals() {}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (getWorld().isClient) return;

		if (anchor == Vec3d.ZERO) anchor = getPos();
		orbitAngle += 0.01f;
		double r = 12;
		Vec3d target = anchor.add(Math.cos(orbitAngle) * r, Math.sin(orbitAngle * 0.5) * 4, Math.sin(orbitAngle) * r);
		Vec3d delta = target.subtract(getPos()).multiply(0.05);
		setVelocity(delta);
		velocityModified = true;

		Vec3d look = anchor.subtract(getPos());
		if (look.lengthSquared() > 1.0e-4) {
			float yaw = (float) (MathHelper.atan2(-look.x, look.z) * (180.0 / Math.PI));
			float pitch = (float) (MathHelper.atan2(-look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * (180.0 / Math.PI));
			setYaw(yaw);
			setPitch(pitch);
		}

		if (age % 40 == 0 && getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 12, 1.5, 1.5, 1.5, 0.02);
		}

		if (macroId == null) {
			macroId = UUID.randomUUID();
		}
		MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.KEEPER, WorldRules.Layer.DEPTH_REACTORS,
				getBlockPos(), 20, 24, 20, 0x44DDFF, "Reactor Keeper"));

		PlayerEntity near = getWorld().getClosestPlayer(this, 32);
		if (near != null && squaredDistanceTo(near) < 20 * 20) {
			setTarget(near);
			if (age % 25 == 0 && getWorld() instanceof ServerWorld sw) {
				near.damage(sw.getDamageSources().mobAttack(this), 10f);
				sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
						near.getX(), near.getY() + 1, near.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (macroId != null) MacroWorld.remove(macroId);
		super.remove(reason);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putDouble("ax", anchor.x);
		nbt.putDouble("ay", anchor.y);
		nbt.putDouble("az", anchor.z);
		if (macroId != null) nbt.putUuid("macro", macroId);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		anchor = new Vec3d(nbt.getDouble("ax"), nbt.getDouble("ay"), nbt.getDouble("az"));
		if (nbt.containsUuid("macro")) macroId = nbt.getUuid("macro");
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}
}
