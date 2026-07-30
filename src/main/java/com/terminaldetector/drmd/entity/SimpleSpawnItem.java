package com.terminaldetector.drmd.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Spawn item for entities that are not {@link net.minecraft.entity.mob.MobEntity}
 * (vanilla SpawnEggItem requires MobEntity).
 */
public class SimpleSpawnItem extends Item {
	private final EntityType<?> type;
	private final double forward;

	public SimpleSpawnItem(EntityType<?> type, Settings settings) {
		this(type, 6.0, settings);
	}

	public SimpleSpawnItem(EntityType<?> type, double forward, Settings settings) {
		super(settings);
		this.type = type;
		this.forward = forward;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		if (!(world instanceof ServerWorld sw)) return TypedActionResult.fail(stack);

		Entity entity = type.create(sw);
		if (entity == null) return TypedActionResult.fail(stack);
		Vec3d pos = user.getPos().add(user.getRotationVec(1f).multiply(forward)).add(0, 1, 0);
		entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), 0);
		sw.spawnEntity(entity);
		if (!user.getAbilities().creativeMode) stack.decrement(1);
		return TypedActionResult.success(stack);
	}
}
