package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Adaptive placement — block faces the chosen plane / local normal (no world up).
 */
public final class AdaptivePlacement {
	private AdaptivePlacement() {}

	public static BlockPos placeOnSurface(World world, PlayerEntity player, BlockPos hit, Direction face) {
		BlockPos at = hit.offset(face);
		if (!world.getBlockState(at).isReplaceable()) return null;
		Block material = resolveMaterial(player);
		BlockState state = orient(material.getDefaultState(), face);
		world.setBlockState(at, state, Block.NOTIFY_ALL);
		// Snap local UP so subsequent builds keep this floor
		LocalOrientation.setFromDirection(player.getUuid(), face);
		return at;
	}

	public static BlockState orient(BlockState state, Direction face) {
		if (state.contains(Properties.FACING)) {
			return state.with(Properties.FACING, face);
		}
		if (state.contains(Properties.HORIZONTAL_FACING)) {
			Direction h = face.getAxis().isHorizontal() ? face : Direction.NORTH;
			return state.with(Properties.HORIZONTAL_FACING, h);
		}
		return state;
	}

	public static Block resolveMaterial(PlayerEntity player) {
		return resolveMaterial(player, false);
	}

	/**
	 * @param leftHandOnly when true (construction laser), only the off-hand stack is used —
	 *                     no hotbar fallback and no iron default.
	 */
	public static Block resolveMaterial(PlayerEntity player, boolean leftHandOnly) {
		ItemStack off = player.getOffHandStack();
		if (off.getItem() instanceof BlockItem bi) return bi.getBlock();
		if (leftHandOnly) return null;
		for (int i = 0; i < 9; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.getItem() instanceof BlockItem bi) return bi.getBlock();
		}
		return Blocks.IRON_BLOCK;
	}

	/** Ray place along look — used by Construction Laser (left-hand block, any orientation). */
	public static BlockPos placeAlongLook(World world, PlayerEntity player, double range) {
		return placeAlongLook(world, player, range, false);
	}

	public static BlockPos placeAlongLook(World world, PlayerEntity player, double range, boolean leftHandOnly) {
		Block material = resolveMaterial(player, leftHandOnly);
		if (material == null) return null;
		// Ship-forward aim when 6DoF is on so builds follow cockpit orientation, not vanilla pitch clamp.
		Vec3d eye = player.getEyePos();
		Vec3d aim = com.terminaldetector.drmd.DescentPlayerData.get(player).isEnabled()
				? com.terminaldetector.drmd.weapon.core.WeaponCore.aimDir(player)
				: player.getRotationVec(1f);
		Vec3d end = eye.add(aim.multiply(range));
		var hit = world.raycast(new net.minecraft.world.RaycastContext(
				eye, end,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
		Direction face = Direction.getFacing(
				LocalOrientation.getUp(player.getUuid()).x,
				LocalOrientation.getUp(player.getUuid()).y,
				LocalOrientation.getUp(player.getUuid()).z);
		if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
			BlockPos air = BlockPos.ofFloored(eye.add(aim.multiply(Math.min(4, range))));
			if (!world.getBlockState(air).isReplaceable()) return null;
			world.setBlockState(air, orient(material.getDefaultState(), face), Block.NOTIFY_ALL);
			consumeOneOffhand(player, leftHandOnly);
			return air;
		}
		BlockPos placed = placeOnSurface(world, player, hit.getBlockPos(), hit.getSide(), material);
		if (placed != null) consumeOneOffhand(player, leftHandOnly);
		return placed;
	}

	private static BlockPos placeOnSurface(World world, PlayerEntity player, BlockPos hit, Direction face, Block material) {
		BlockPos at = hit.offset(face);
		if (!world.getBlockState(at).isReplaceable()) return null;
		world.setBlockState(at, orient(material.getDefaultState(), face), Block.NOTIFY_ALL);
		LocalOrientation.setFromDirection(player.getUuid(), face);
		return at;
	}

	private static void consumeOneOffhand(PlayerEntity player, boolean leftHandOnly) {
		if (!leftHandOnly || player.getAbilities().creativeMode) return;
		ItemStack off = player.getOffHandStack();
		if (off.getItem() instanceof BlockItem) off.decrement(1);
	}

	/**
	 * Place many blocks from a scaffold / template. Returns count actually placed.
	 * Consumes one off-hand block per placement when {@code leftHandOnly}.
	 */
	public static int placeMany(World world, PlayerEntity player, java.util.List<BlockPos> cells,
			boolean leftHandOnly) {
		Block material = resolveMaterial(player, leftHandOnly);
		if (material == null || cells == null || cells.isEmpty()) return 0;
		Direction face = Direction.getFacing(
				LocalOrientation.getUp(player.getUuid()).x,
				LocalOrientation.getUp(player.getUuid()).y,
				LocalOrientation.getUp(player.getUuid()).z);
		int placed = 0;
		for (BlockPos at : cells) {
			if (!world.getBlockState(at).isReplaceable()) continue;
			if (leftHandOnly && !player.getAbilities().creativeMode) {
				ItemStack off = player.getOffHandStack();
				if (!(off.getItem() instanceof BlockItem) || off.isEmpty()) break;
			}
			world.setBlockState(at, orient(material.getDefaultState(), face), Block.NOTIFY_ALL);
			consumeOneOffhand(player, leftHandOnly);
			placed++;
		}
		return placed;
	}
}
