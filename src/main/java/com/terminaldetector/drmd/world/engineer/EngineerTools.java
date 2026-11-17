package com.terminaldetector.drmd.world.engineer;

import com.terminaldetector.drmd.weapon.core.WeaponCore;

import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BlockBodyWorldAccess;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.GameMode;
import org.joml.Vector3f;

/**
 * Specialized engineer tools replacing pickaxe workflow in 6DoF volume.
 * Mining laser = continuous drill / melt installation (hold use).
 */
public final class EngineerTools {
	private EngineerTools() {}

	/**
	 * @deprecated Use {@link com.terminaldetector.drmd.world.build.ConstructLaserItem} tiers.
	 * Kept as green-tier alias for old references.
	 */
	@Deprecated
	public static class ConstructionLaserItem extends com.terminaldetector.drmd.world.build.ConstructLaserItem {
		public ConstructionLaserItem(Settings settings) {
			super(settings, com.terminaldetector.drmd.world.build.ConstructLaserTier.GREEN);
		}
	}

	public static class RepairLaserItem extends Item {
		public RepairLaserItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)
					|| !user.isAlive() || user.isSpectator() || !user.getAbilities().allowModifyWorld
					|| !(sp.interactionManager.getGameMode()==GameMode.SURVIVAL || sp.interactionManager.getGameMode()==GameMode.CREATIVE)) return TypedActionResult.fail(stack);
			Vec3d eye=user.getEyePos(),direction=WeaponCore.aimDir(user);
			if (!Double.isFinite(direction.lengthSquared()) || direction.lengthSquared()<1e-12) return TypedActionResult.fail(stack);
			Vec3d end=WorldEditSafety.loadedEnd(world,eye,eye.add(direction.normalize().multiply(16)));
			if (eye.squaredDistanceTo(end)<1e-12) return TypedActionResult.fail(stack);
			BlockHitResult hit=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,user));
			Vec3d limit=hit.getType()==HitResult.Type.BLOCK?hit.getPos():end;
			var bodies=BodySpatialIndex.query(world,new net.minecraft.util.math.Box(eye,limit).expand(.01));
			if (bodies.size()>32) return TypedActionResult.fail(stack);
			BlockBodyEntity nearest=null;BlockBodyWorldAccess.BodyHit bodyHit=null;double fraction=1;
			for (var body:bodies) {
				var candidate=new BlockBodyWorldAccess(body).raycastBlockBody(eye,limit);
				if (candidate.isPresent() && candidate.get().fraction()<fraction-1e-7) {
					nearest=body;bodyHit=candidate.get();fraction=bodyHit.fraction();
				}
			}
			if (nearest!=null) {
				var point=bodyHit.worldPoint();Vec3d at=new Vec3d(point.x(),point.y(),point.z());
				// The nearest solid cell owns the result even if it has no damage;
				// never pass through an intact/unauthorized hull to repair another.
				if (!WorldEditSafety.loadedSegment(world,eye,at) || !WorldEditSafety.mayChange(sw,BlockPos.ofFloored(at),sp)
						|| !nearest.repairLocalMaterial(bodyHit.localBlock())) return TypedActionResult.fail(stack);
				feedback(world,user,at);return TypedActionResult.success(stack);
			}
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				if (!WorldEditSafety.mayChange(sw,pos,sp)) return TypedActionResult.fail(stack);
				BlockState st = world.getBlockState(pos);
				if (st.isOf(Blocks.CRACKED_STONE_BRICKS)) {
					world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
				} else if (st.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
					world.setBlockState(pos, Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
				} else if (st.isOf(Blocks.CRACKED_NETHER_BRICKS)) {
					world.setBlockState(pos, Blocks.NETHER_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
				} else if (st.getHardness(world, pos) >= 0) {
					world.setBlockState(pos, st, Block.NOTIFY_ALL);
				}
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
						new net.minecraft.util.math.Box(pos).expand(3), LivingEntity::isAlive)) {
					e.heal(4f);
				}
				DescentShieldBoost(sp);
				feedback(world,user,pos.toCenterPos());
			}
			return TypedActionResult.success(stack);
		}

		private static void feedback(World world,PlayerEntity user,Vec3d at) {
			beam(world,user,at,new Vector3f(0.3f,1f,0.4f));
			world.playSound(null,BlockPos.ofFloored(at),SoundEvents.BLOCK_ANVIL_USE,SoundCategory.BLOCKS,0.35f,1.6f);
			user.sendMessage(Text.literal("§aRepair laser — structure refreshed"),true);
		}

		private static void DescentShieldBoost(ServerPlayerEntity sp) {
			var data = com.terminaldetector.drmd.DescentPlayerData.get(sp);
			data.setShield(Math.min(data.getShieldMax(), data.getShield() + 8f));
		}
	}

	/**
	 * Drill laser — hold RMB to melt / carve a wide face along look.
	 * Sneak extends range and widens the cut (5³).
	 */
	public static class MiningLaserItem extends Item {
		public MiningLaserItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(user.getStackInHand(hand));
		}

		@Override
		public UseAction getUseAction(ItemStack stack) {
			return UseAction.BOW;
		}

		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
			if (world.isClient || !(user instanceof PlayerEntity player)) return;
			if (remainingUseTicks % 2 != 0) return;
			if (!(world instanceof ServerWorld sw)) return;

			double range = player.isSneaking() ? 40 : 26;
			BlockHitResult hit = ray(world, player, range);
			Vec3d eye = player.getEyePos();
			if (hit.getType() != HitResult.Type.BLOCK) {
				Vec3d end = eye.add(WeaponCore.aimDir(player).multiply(range));
				WeaponFx.beamDrill(sw, eye, end, false);
				return;
			}
			BlockPos pos = hit.getBlockPos();
			WeaponFx.beamDrill(sw, eye, hit.getPos(), false);
			int intensity = player.isSneaking() ? 3 : 2;
			boolean carved = WeaponFx.melt(sw, pos, intensity, player);
			if (!carved) {
				BlockState st = world.getBlockState(pos);
				if (!st.isAir() && st.getHardness(world, pos) >= 0 && st.getHardness(world, pos) < 50) {
					sw.breakBlock(pos, true, player);
				}
			}
			// Wider work face every pulse (sneak → 5³ mining head)
			if (remainingUseTicks % 4 == 0) {
				WeaponFx.drillCarve(sw, pos, player, player.isSneaking() ? 2 : 1);
			}
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS,
					0.25f, 1.6f + world.getRandom().nextFloat() * 0.4f);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			PlayerEntity user = context.getPlayer();
			if (user != null) {
				user.setCurrentHand(context.getHand());
				return ActionResult.CONSUME;
			}
			return ActionResult.PASS;
		}
	}

	/**
	 * Heavy tunnel laser — hold RMB to bore a walkable cylinder along look.
	 * Radius 2 (sneak 3), advances several blocks per pulse with industrial smoke.
	 */
	public static class TunnelLaserItem extends Item {
		public TunnelLaserItem(Settings settings) { super(settings.maxCount(1)); }

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(user.getStackInHand(hand));
		}

		@Override
		public UseAction getUseAction(ItemStack stack) {
			return UseAction.BOW;
		}

		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
			if (world.isClient || !(user instanceof PlayerEntity player)) return;
			if (remainingUseTicks % 3 != 0) return;
			if (!(world instanceof ServerWorld sw)) return;

			double range = 48;
			Vec3d eye = player.getEyePos();
			Vec3d dir = WeaponCore.aimDir(player);
			BlockHitResult hit = ray(world, player, range);
			Vec3d aimEnd = hit.getType() == HitResult.Type.BLOCK
					? hit.getPos()
					: eye.add(dir.multiply(range));
			WeaponFx.beamDrill(sw, eye, aimEnd, true);

			BlockPos origin = hit.getType() == HitResult.Type.BLOCK
					? hit.getBlockPos()
					: BlockPos.ofFloored(eye.add(dir.multiply(2.5)));
			int radius = player.isSneaking() ? 3 : 2;
			int length = player.isSneaking() ? 5 : 3;
			int carved = WeaponFx.drillTunnel(sw, origin, dir, player, radius, length,true);
			if (carved > 0) {
				player.sendMessage(Text.literal("§6Tunnel bore §f" + carved + " §7blocks"), true);
			}
			world.playSound(null, origin, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS,
					0.45f, 0.55f + world.getRandom().nextFloat() * 0.25f);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			PlayerEntity user = context.getPlayer();
			if (user != null) {
				user.setCurrentHand(context.getHand());
				return ActionResult.CONSUME;
			}
			return ActionResult.PASS;
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
		Vec3d end = eye.add(WeaponCore.aimDir(user).multiply(range));
        end=com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedEnd(world,eye,end);
		return world.raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user));
	}

	private static void beam(World world, PlayerEntity user, Vec3d target, Vector3f color) {
		if (!(world instanceof ServerWorld sw)) return;
		WeaponFx.beam(sw, user.getEyePos(), target, color, 0.9f);
	}
}
