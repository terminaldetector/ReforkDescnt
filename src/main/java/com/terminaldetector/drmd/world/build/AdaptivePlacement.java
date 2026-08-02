package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
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
		if (!BlockInstall.placeOriented(world, player, at, face)) return null;
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
			Direction face = Direction.getFacing(
					LocalOrientation.getUp(player.getUuid()).x,
					LocalOrientation.getUp(player.getUuid()).y,
					LocalOrientation.getUp(player.getUuid()).z);
			if (!BlockInstall.placeOriented(world, player, air, face)) return null;
			return air;
		}
		return placeOnSurface(world, player, hit.getBlockPos(), hit.getSide());
	}
}
