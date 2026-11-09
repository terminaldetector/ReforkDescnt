package com.terminaldetector.drmd.world.contraption.mechanism;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** Existing ship weapon IDs gain a real local barrel axis. Legacy grids keep local +Z. */
public final class MountedWeaponBlock extends Block {
    public static final MapCodec<MountedWeaponBlock> CODEC=createCodec(MountedWeaponBlock::new);
    public MountedWeaponBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(Properties.FACING,Direction.SOUTH));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(Properties.FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext context){return getDefaultState().with(Properties.FACING,context.getSide());}
}
