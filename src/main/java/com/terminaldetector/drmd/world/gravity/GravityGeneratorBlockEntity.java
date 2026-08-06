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

/**
 * Block entity for {@link GravityGeneratorBlock} — torch rules, larger tunable radius.
 */
public class GravityGeneratorBlockEntity extends BlockEntity {
	private UUID fieldId = UUID.randomUUID();
	private float radius = GravityGeneratorBlock.DEFAULT_RADIUS;
	private float power = GravityGeneratorBlock.DEFAULT_POWER;
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
		// Keep generator strictly larger than the torch (8) across the whole range.
		radius = 16f + power * 16f; // 20 … 48
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
		if (world == null) return;
		Direction facing = getCachedState().get(GravityGeneratorBlock.FACING);
		// Same semantics as the torch: FACING is local up (out of mount face), gravity pulls opposite.
		Vec3d down = Vec3d.of(facing.getOpposite().getVector());
		GravityFields.put(new GravityFields.Field(
				fieldId, world.getRegistryKey(),
				pos, down, radius, power, shape, "Gravity Generator", true));
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
		radius = nbt.contains("radius") ? nbt.getFloat("radius") : GravityGeneratorBlock.DEFAULT_RADIUS;
		power = nbt.contains("power") ? nbt.getFloat("power") : GravityGeneratorBlock.DEFAULT_POWER;
		try {
			shape = FieldShape.valueOf(nbt.getString("shape"));
		} catch (Exception e) {
			shape = FieldShape.SPHERE;
		}
	}
}
