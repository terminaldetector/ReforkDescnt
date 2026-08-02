package com.terminaldetector.drmd.world.bombardment;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bomb bay / laser designator items for high-altitude bombardment.
 */
public final class BombardmentItems {
	/** Last laser mark per player. */
	private static final Map<UUID, BlockPos> LASER_MARKS = new ConcurrentHashMap<>();

	private BombardmentItems() {}

	public static BlockPos getLaserMark(UUID id) {
		return LASER_MARKS.get(id);
	}

	public static class BombBayItem extends Item {
		private final OrdnanceType ordnance;

		public BombBayItem(OrdnanceType ordnance, Settings settings) {
			super(settings.maxCount(16));
			this.ordnance = ordnance;
		}

		public OrdnanceType getOrdnance() {
			return ordnance;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)) {
				return TypedActionResult.fail(stack);
			}
			var bomb = com.terminaldetector.drmd.entity.ModEntities.AERIAL_BOMB.create(sw);
			if (bomb == null) return TypedActionResult.fail(stack);
			Vec3d drop = user.getPos().add(user.getRotationVec(1f).multiply(2)).add(0, -1.2, 0);
			bomb.refreshPositionAndAngles(drop.x, drop.y, drop.z, user.getYaw(), 90);
			bomb.setVelocity(user.getVelocity().add(0, -0.2, 0));
			bomb.configure(ordnance, user, LASER_MARKS.get(user.getUuid()));
			sw.spawnEntity(bomb);
			SmokeSystemTrail(user);
			if (!user.getAbilities().creativeMode) stack.decrement(1);
			user.sendMessage(Text.literal("§cOrdnance away: §f" + ordnance.name()
					+ " §7@ Y=" + (int) user.getY()), true);
			return TypedActionResult.success(stack);
		}

		private static void SmokeSystemTrail(PlayerEntity user) {
			com.terminaldetector.drmd.world.smoke.SmokeSystem.emit(
					user.getPos(), com.terminaldetector.drmd.world.smoke.SmokeSystem.Source.ENGINE, 0.5f, 0.3f, 20);
		}
	}

	public static class LaserDesignatorItem extends Item {
		public LaserDesignatorItem(Settings settings) {
			super(settings.maxCount(1));
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			Vec3d eye = user.getEyePos();
			Vec3d end = eye.add(user.getRotationVec(1f).multiply(256));
			BlockHitResult hit = world.raycast(new RaycastContext(eye, end,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos mark = hit.getBlockPos();
				LASER_MARKS.put(user.getUuid(), mark);
				user.sendMessage(Text.literal("§aLaser mark §f" + mark.toShortString()), true);
				if (world instanceof ServerWorld sw) {
					sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
							mark.getX() + 0.5, mark.getY() + 1.2, mark.getZ() + 0.5,
							16, 0.2, 0.4, 0.2, 0.02);
				}
			} else {
				user.sendMessage(Text.literal("§7No laser lock"), true);
			}
			return TypedActionResult.success(stack);
		}
	}
}
