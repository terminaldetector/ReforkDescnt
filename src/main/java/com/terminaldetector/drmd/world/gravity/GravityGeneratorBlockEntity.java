package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class GravityGeneratorBlockEntity extends BlockEntity {
	private UUID fieldId = UUID.randomUUID();
	private float radius = 24f;
	private float power = 1.0f;
	private FieldShape shape = FieldShape.SPHERE;

	public GravityGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GRAVITY_GENERATOR, pos, state);
	}

	public float getRadius() { return radius; }
	public float getPower() { return power; }
	public FieldShape getShape() { return shape; }

	public void cyclePower() {
		power += 0.25f;
		if (power > 2.0f) power = 0.25f;
		radius = 12f + power * 16f;
		markDirty();
		registerField();
	}

	public void cycleShape() {
		FieldShape[] v = FieldShape.values();
		shape = v[(shape.ordinal() + 1) % v.length];
		markDirty();
		registerField();
	}

	public void registerField() {
		Direction facing = getCachedState().get(GravityGeneratorBlock.FACING);
		// Facing points "down" into the gravity well
		Vec3d down = Vec3d.of(facing.getVector());
		GravityFields.put(new GravityFields.Field(
				fieldId, world != null ? world.getRegistryKey() : null,
				pos, down, radius, power, shape, "Generator"));
	}

	public void unregister() {
		GravityFields.remove(fieldId);
	}

	public static void tick(World world, BlockPos pos, BlockState state, GravityGeneratorBlockEntity be) {
		if (world.getTime() % 20 == 0) be.registerField();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putUuid("field", fieldId);
		nbt.putFloat("radius", radius);
		nbt.putFloat("power", power);
		nbt.putString("shape", shape.name());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		if (nbt.containsUuid("field")) fieldId = nbt.getUuid("field");
		radius = nbt.contains("radius") ? nbt.getFloat("radius") : 24f;
		power = nbt.contains("power") ? nbt.getFloat("power") : 1f;
		try {
			shape = FieldShape.valueOf(nbt.getString("shape"));
		} catch (Exception e) {
			shape = FieldShape.SPHERE;
		}
	}
}
