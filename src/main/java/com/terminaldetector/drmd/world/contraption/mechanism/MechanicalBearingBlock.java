package com.terminaldetector.drmd.world.contraption.mechanism;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** FACING is the outward material attachment axis, in the containing grid. */
public class MechanicalBearingBlock extends Block {
    public static final DirectionProperty FACING=Properties.FACING;
    public static final MapCodec<MechanicalBearingBlock> CODEC=createCodec(MechanicalBearingBlock::new);
    public MechanicalBearingBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext context){return getDefaultState().with(FACING,context.getSide());}
}
