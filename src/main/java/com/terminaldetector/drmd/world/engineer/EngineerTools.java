package com.terminaldetector.drmd.world.engineer;

import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.build.AdaptivePlacement;
import com.terminaldetector.drmd.world.build.ConstructionMode;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Specialized engineer tools replacing pickaxe workflow in 6DoF volume.
 */
public final class EngineerTools {
	private EngineerTools() {}

	public static class ConstructionLaserItem extends Item {
		public ConstructionLaserItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			if (user instanceof ServerPlayerEntity sp && !ConstructionMode.isActive(sp.getUuid())) {
				ConstructionMode.set(sp, true);
			}
			BlockPos placed = AdaptivePlacement.placeAlongLook(world, user, user.isSneaking() ? 32 : 12);
			if (placed != null) {
				beam(world, user, placed.toCenterPos(), new Vector3f(0.3f, 0.9f, 1f));
				world.playSound(null, placed, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.3f);
			}
			return TypedActionResult.success(stack);
		}
	}

	public static class RepairLaserItem extends Item {
		public RepairLaserItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			BlockHitResult hit = ray(world, user, 16);
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				BlockState st = world.getBlockState(pos);
				// "Repair": refresh cracked / damaged-looking blocks toward iron / stone solid
				if (st.isOf(Blocks.CRACKED_STONE_BRICKS) || st.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS)
						|| st.isOf(Blocks.CRACKED_NETHER_BRICKS)) {
					world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
				} else if (st.getHardness(world, pos) >= 0) {
					world.setBlockState(pos, st, Block.NOTIFY_ALL);
				}
				// Heal nearby living (ships / drones / players) lightly
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
						new net.minecraft.util.math.Box(pos).expand(3), LivingEntity::isAlive)) {
					e.heal(4f);
				}
				if (user instanceof ServerPlayerEntity sp) {
					DescentShieldBoost(sp);
				}
				beam(world, user, pos.toCenterPos(), new Vector3f(0.3f, 1f, 0.4f));
				world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.35f, 1.6f);
				user.sendMessage(Text.literal("§aRepair laser — structure refreshed"), true);
			}
			return TypedActionResult.success(stack);
		}

		private static void DescentShieldBoost(ServerPlayerEntity sp) {
			var data = com.terminaldetector.drmd.DescentPlayerData.get(sp);
			data.setShield(Math.min(data.getShieldMax(), data.getShield() + 8f));
		}
	}

	public static class MiningLaserItem extends Item {
		public MiningLaserItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			BlockHitResult hit = ray(world, user, 20);
			if (hit.getType() == HitResult.Type.BLOCK && world instanceof ServerWorld sw) {
				BlockPos pos = hit.getBlockPos();
				BlockState st = world.getBlockState(pos);
				if (!st.isAir() && st.getHardness(world, pos) >= 0 && st.getHardness(world, pos) < 50) {
					sw.breakBlock(pos, true, user);
					beam(world, user, pos.toCenterPos(), new Vector3f(1f, 0.55f, 0.15f));
					world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.4f, 1.8f);
				}
			}
			return TypedActionResult.success(stack);
		}
	}

	public static class GravityScannerItem extends Item {
		public GravityScannerItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			Vec3d up = LocalOrientation.getUp(user.getUuid());
			Vec3d down = LocalOrientation.gravityDir(user.getUuid());
			GravityFields.Sample sample = GravityFields.sample(world, user.getPos());
			String field = sample == null ? "none" : sample.label() + " p=" + String.format("%.2f", sample.maxPower());
			user.sendMessage(Text.literal(String.format(
					"§aGravity Scanner §7UP=(%.2f,%.2f,%.2f) DOWN=(%.2f,%.2f,%.2f) field=%s",
					up.x, up.y, up.z, down.x, down.y, down.z, field)), false);
			if (world instanceof ServerWorld sw) {
				Vec3d eye = user.getEyePos();
				for (int i = 1; i <= 12; i++) {
					Vec3d p = eye.add(down.multiply(i * 0.5));
					sw.spawnParticles(new DustParticleEffect(new Vector3f(0.2f, 1f, 0.5f), 1.2f),
							p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
				for (GravityFields.Field f : GravityFields.all()) {
					sw.spawnParticles(ParticleTypes.END_ROD,
							f.origin().getX() + 0.5, f.origin().getY() + 0.5, f.origin().getZ() + 0.5,
							8, 0.4, 0.4, 0.4, 0.02);
				}
			}
			return TypedActionResult.success(stack);
		}
	}

	private static BlockHitResult ray(World world, PlayerEntity user, double range) {
		Vec3d eye = user.getEyePos();
		Vec3d end = eye.add(user.getRotationVec(1f).multiply(range));
		return world.raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user));
	}

	private static void beam(World world, PlayerEntity user, Vec3d target, Vector3f color) {
		if (!(world instanceof ServerWorld sw)) return;
		Vec3d eye = user.getEyePos();
		Vec3d delta = target.subtract(eye);
		int n = Math.max(4, (int) (delta.length() * 2));
		for (int i = 0; i <= n; i++) {
			Vec3d p = eye.add(delta.multiply(i / (double) n));
			sw.spawnParticles(new DustParticleEffect(color, 0.9f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}
}
