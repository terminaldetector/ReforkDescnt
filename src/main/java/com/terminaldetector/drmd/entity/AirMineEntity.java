package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Air mine — drift 45, trigger 160, blast 120 (Source units). */
public class AirMineEntity extends HostileEntity {
	private float blipTimer = 1.2f;

	public AirMineEntity(EntityType<? extends AirMineEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 40)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24);
	}

	@Override
	protected void initGoals() {}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (getWorld().isClient) return;

		// Slow drift
		Vec3d drift = new Vec3d(
				(random.nextDouble() - 0.5) * DescentMod.su(45) / 20.0,
				(random.nextDouble() - 0.5) * DescentMod.su(20) / 20.0,
				(random.nextDouble() - 0.5) * DescentMod.su(45) / 20.0);
		setVelocity(getVelocity().multiply(0.9).add(drift));
		velocityModified = true;

		blipTimer -= 1f / 20f;
		double trigger = DescentMod.su(160);
		PlayerEntity nearest = getWorld().getClosestPlayer(this, trigger);
		if (nearest != null && squaredDistanceTo(nearest) < trigger * trigger) {
			detonate();
		} else if (blipTimer <= 0) {
			blipTimer = 1.2f;
			if (getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0.01);
			}
		}
	}

	private void detonate() {
		if (getWorld() instanceof ServerWorld sw) {
			WeaponCore.splashDamage(this, sw, getPos(), 120f, (float) DescentMod.su(160), DamageClass.EXPLOSIVE);
			sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 8, 0.5, 0.5, 0.5, 0.1);
		}
		discard();
	}
}
