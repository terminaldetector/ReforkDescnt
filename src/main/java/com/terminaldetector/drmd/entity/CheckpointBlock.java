package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** d6_checkpoint — notifies when a 6DOF player touches it. */
public class CheckpointBlock extends Block {
	public CheckpointBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player)) return;
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!data.isEnabled()) return;
		if (player.age % 40 == 0) {
			player.sendMessage(Text.literal("§aDRMD Checkpoint saved"), true);
		}
	}
}
