package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** d6_checkpoint — walk-through pad; ПКМ or touch saves while 6DoF. */
public class CheckpointBlock extends Block {
	public CheckpointBlock(Settings settings) {
		super(settings.nonOpaque());
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.fullCube();
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player)) return;
		ping(player);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient) ping(player);
		return ActionResult.success(world.isClient);
	}

	private static void ping(PlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!data.isEnabled() && !player.isCreative()) {
			player.sendMessage(Text.literal("§8Checkpoint — включи 6DoF (H / ядро)"), true);
			return;
		}
		if (player.age % 20 == 0 || player.handSwinging) {
			player.sendMessage(Text.literal("§aDRMD Checkpoint saved"), true);
		}
	}
}
