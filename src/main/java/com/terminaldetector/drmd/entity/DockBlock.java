package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** d6_dock — heal / energy / shield regen while colliding. */
public class DockBlock extends Block {
	public DockBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player)) return;
		if (player.age % 10 != 0) return;
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!data.isEnabled()) return;
		player.heal(12.5f);
		EnergySystem.add(data, 10f);
		data.setShield(Math.min(data.getShieldMax(), data.getShield() + 10f));
	}
}
