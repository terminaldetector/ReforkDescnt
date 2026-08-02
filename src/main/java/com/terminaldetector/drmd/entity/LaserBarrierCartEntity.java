package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.world.trap.LaserBeams;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Minecart cyclic laser barrier — rides powered rails and sweeps dual beams
 * (inward to ring centre + local UP) for 6DoF volume denial.
 */
public class LaserBarrierCartEntity extends AbstractMinecartEntity {
	private static final int BEAM_LEN = 10;
	private static final float BEAM_DMG = 4.5f;

	private double ringCenterX = Double.NaN;
	private double ringCenterZ = Double.NaN;
	private int beamCooldown;

	public LaserBarrierCartEntity(EntityType<? extends LaserBarrierCartEntity> type, World world) {
		super(type, world);
	}

	public void setRingCenter(double x, double z) {
		this.ringCenterX = x;
		this.ringCenterZ = z;
	}

	@Override
	public Type getMinecartType() {
		return Type.RIDEABLE;
	}

	@Override
	public Item asItem() {
		Item kit = ModWorldBlocks.CYCLIC_LASER_KIT;
		return kit != null ? kit : Items.MINECART;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;

		// Self-boost on powered rails so the loop never stalls (furnace-cart analogue).
		Vec3d vel = getVelocity();
		if (isOnRail()) {
			if (vel.horizontalLengthSquared() < 0.04) {
				Vec3d push = suggestTangent().multiply(0.18);
				setVelocity(vel.add(push));
				velocityModified = true;
			} else if (vel.horizontalLengthSquared() < 0.35) {
				setVelocity(vel.multiply(1.08, 1.0, 1.08));
				velocityModified = true;
			}
		}

		if (--beamCooldown > 0) return;
		beamCooldown = 4;
		if (!(getWorld() instanceof ServerWorld sw)) return;

		Vec3d from = getPos().add(0, 0.55, 0);
		Vec3d inward = inwardDir();
		Vec3d up = new Vec3d(0, 1, 0);
		LaserBeams.castRingPair(sw, from, inward, up, BEAM_LEN, BEAM_DMG);
		sw.spawnParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 2, 0.1, 0.1, 0.1, 0.01);
	}

	private Vec3d inwardDir() {
		if (!Double.isNaN(ringCenterX)) {
			Vec3d to = new Vec3d(ringCenterX - getX(), 0, ringCenterZ - getZ());
			if (to.lengthSquared() > 1e-4) return to.normalize();
		}
		// Fallback: beam opposite travel, then +Y only from castRingPair.
		Vec3d vel = getVelocity();
		if (vel.horizontalLengthSquared() > 1e-4) {
			return new Vec3d(-vel.x, 0, -vel.z).normalize();
		}
		return new Vec3d(1, 0, 0);
	}

	private Vec3d suggestTangent() {
		if (!Double.isNaN(ringCenterX)) {
			double dx = getX() - ringCenterX;
			double dz = getZ() - ringCenterZ;
			// Perpendicular for counter-clockwise circulation.
			Vec3d t = new Vec3d(-dz, 0, dx);
			if (t.lengthSquared() > 1e-4) return t.normalize();
		}
		return new Vec3d(1, 0, 0);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (!Double.isNaN(ringCenterX)) {
			nbt.putDouble("RingCX", ringCenterX);
			nbt.putDouble("RingCZ", ringCenterZ);
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("RingCX")) {
			ringCenterX = nbt.getDouble("RingCX");
			ringCenterZ = nbt.getDouble("RingCZ");
		}
	}
}
