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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Mega Creature — sky worm. Not a boss: a landscape element.
 * Placeholder length ~80 blocks (prototype); design target 1000+.
 * Players can fly along its body for minutes exploring the surface.
 */
public class MegaWormEntity extends HostileEntity {
	public static final int SEGMENTS = 24;
	public static final float SEGMENT_SPACING = 3.2f;

	private UUID macroId;
	private float phase;

	public MegaWormEntity(EntityType<? extends MegaWormEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 2000)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 256)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.4);
	}

	@Override
	protected void initGoals() {}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (getWorld().isClient) {
			phase += 0.02f;
			return;
		}
		phase += 0.02f;
		// Slow serpentine cruise
		double yaw = Math.toRadians(getYaw());
		double pitch = Math.sin(phase) * 0.25;
		Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), pitch, Math.cos(yaw) * Math.cos(pitch));
		setVelocity(dir.multiply(0.25));
		setYaw(getYaw() + (float) Math.sin(phase * 0.5) * 0.4f);
		velocityModified = true;

		if (macroId == null) {
			registerMacro();
		} else {
			MacroEntry old = MacroWorld.get(macroId);
			if (old != null) {
				MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.WORM, WorldRules.Layer.SKY_ARCHIPELAGO,
						getBlockPos(), (int) (SEGMENTS * SEGMENT_SPACING), 8, 12, 0xAA6644, "Sky Worm"));
			}
		}

		// Mild aggro if player is very close to head
		PlayerEntity p = getWorld().getClosestPlayer(this, 12);
		if (p != null) setTarget(p);
	}

	private void registerMacro() {
		macroId = UUID.randomUUID();
		MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.WORM, WorldRules.Layer.SKY_ARCHIPELAGO,
				getBlockPos(), (int) (SEGMENTS * SEGMENT_SPACING), 8, 12, 0xAA6644, "Sky Worm"));
	}

	/** World positions of body segments for rendering / collision feel. */
	public Vec3d segmentPos(int index, float tickDelta) {
		float t = phase + index * 0.35f;
		double yaw = Math.toRadians(getYaw());
		Vec3d back = new Vec3d(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(index * SEGMENT_SPACING);
		Vec3d wave = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw)).multiply(Math.sin(t) * 2.5);
		Vec3d vert = new Vec3d(0, Math.sin(t * 0.7) * 1.5, 0);
		return getLerpedPos(tickDelta).add(back).add(wave).add(vert);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (macroId != null) MacroWorld.remove(macroId);
		super.remove(reason);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("phase", phase);
		if (macroId != null) nbt.putUuid("macro", macroId);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		phase = nbt.getFloat("phase");
		if (nbt.containsUuid("macro")) macroId = nbt.getUuid("macro");
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}
}
