package com.terminaldetector.drmd.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;

/** Decorative rotating reactor core marker for Descent-like reactor rooms. */
public class ReactorDisplayEntity extends Entity {
	public ReactorDisplayEntity(EntityType<? extends ReactorDisplayEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		// visual spin handled in model
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {}

	@Override
	public void onSpawnPacket(EntitySpawnS2CPacket packet) {
		super.onSpawnPacket(packet);
	}

	@Override
	public boolean isCollidable() {
		return false;
	}
}
