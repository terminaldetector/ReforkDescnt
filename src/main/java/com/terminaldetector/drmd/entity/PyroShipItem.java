package com.terminaldetector.drmd.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Pyro GX — placeable Descent transport for survival craft + creative quick deploy.
 */
public class PyroShipItem extends Item {
	public PyroShipItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);

		Vec3d eye = user.getEyePos();
		Vec3d end = eye.add(user.getRotationVec(1f).multiply(8));
		var hit = world.raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		Vec3d spawnAt = hit.getType() == HitResult.Type.MISS
				? eye.add(user.getRotationVec(1f).multiply(3))
				: hit.getPos().add(0, 1.2, 0);

		return spawn(world, user, stack, spawnAt, user.getYaw())
				? TypedActionResult.success(stack)
				: TypedActionResult.fail(stack);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) return ActionResult.SUCCESS;
		BlockPos pos = context.getBlockPos().offset(context.getSide());
		PlayerEntity user = context.getPlayer();
		if (user == null) return ActionResult.FAIL;
		Vec3d at = Vec3d.ofBottomCenter(pos).add(0, 0.5, 0);
		return spawn(world, user, context.getStack(), at, user.getYaw())
				? ActionResult.SUCCESS
				: ActionResult.FAIL;
	}

	private boolean spawn(World world, PlayerEntity user, ItemStack stack, Vec3d at, float yaw) {
		if (!(world instanceof ServerWorld sw)) return false;
		PyroShipEntity ship = ModEntities.PYRO_SHIP.create(sw);
		if (ship == null) return false;
		ship.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
		sw.spawnEntity(ship);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.7f, 1.2f);
		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		return true;
	}
}
