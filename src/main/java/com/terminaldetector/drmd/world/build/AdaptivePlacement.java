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
		ItemStack off = player.getOffHandStack();
		if (off.getItem() instanceof BlockItem bi) return bi.getBlock();
		for (int i = 0; i < 9; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.getItem() instanceof BlockItem bi) return bi.getBlock();
		}
		return Blocks.IRON_BLOCK;
	}

	/** Ray place along look — used by Construction Laser. */
	public static BlockPos placeAlongLook(World world, PlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1f).multiply(range));
		var hit = world.raycast(new net.minecraft.world.RaycastContext(
				eye, end,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
			BlockPos air = BlockPos.ofFloored(eye.add(player.getRotationVec(1f).multiply(Math.min(4, range))));
			if (!world.getBlockState(air).isReplaceable()) return null;
			Block material = resolveMaterial(player);
			Direction face = Direction.getFacing(
					LocalOrientation.getUp(player.getUuid()).x,
					LocalOrientation.getUp(player.getUuid()).y,
					LocalOrientation.getUp(player.getUuid()).z);
			world.setBlockState(air, orient(material.getDefaultState(), face), Block.NOTIFY_ALL);
			return air;
		}
		return placeOnSurface(world, player, hit.getBlockPos(), hit.getSide());
	}
}
