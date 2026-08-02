package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
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

/** d6_dock — walk-through pad; heal / energy / shield while inside or on ПКМ. */
public class DockBlock extends Block {
	public DockBlock(Settings settings) {
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
		if (player.age % 10 != 0) return;
		recharge(player);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient) {
			recharge(player);
			player.sendMessage(Text.literal("§bDock §7— energy / shield / heal"), true);
		}
		return ActionResult.success(world.isClient);
	}

	private static void recharge(PlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		player.heal(12.5f);
		EnergySystem.add(data, 10f);
		data.setShield(Math.min(data.getShieldMax(), data.getShield() + 10f));
	}
}
