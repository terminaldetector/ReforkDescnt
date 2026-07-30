package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.ai.AiRole;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Creative/quick spawn egg for a specific drone role (10 combat loadouts).
 */
public class DroneSpawnEggItem extends Item {
	private final AiRole role;
	private final int primaryColor;
	private final int secondaryColor;

	public DroneSpawnEggItem(AiRole role, int primaryColor, int secondaryColor, Settings settings) {
		super(settings);
		this.role = role;
		this.primaryColor = primaryColor;
		this.secondaryColor = secondaryColor;
	}

	public AiRole getRole() { return role; }
	public int getPrimaryColor() { return primaryColor; }
	public int getSecondaryColor() { return secondaryColor; }

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		if (!(world instanceof ServerWorld sw)) return TypedActionResult.fail(stack);

		DroneEntity drone = ModEntities.DRONE.create(sw);
		if (drone == null) return TypedActionResult.fail(stack);
		Vec3d look = user.getRotationVec(1f);
		Vec3d pos = user.getPos().add(look.multiply(4)).add(0, 1, 0);
		drone.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), 0);
		drone.applyRole(role);
		sw.spawnEntity(drone);
		if (!user.getAbilities().creativeMode) stack.decrement(1);
		return TypedActionResult.success(stack);
	}
}
