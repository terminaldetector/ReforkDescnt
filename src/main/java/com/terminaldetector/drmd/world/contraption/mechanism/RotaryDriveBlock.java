package com.terminaldetector.drmd.world.contraption.mechanism;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;

/** A separate, bounded rotary actuator behind a bearing. Zero power coasts. */
public final class RotaryDriveBlock extends MechanicalBearingBlock {
    public static final MapCodec<RotaryDriveBlock> CODEC=createCodec(RotaryDriveBlock::new);
    public RotaryDriveBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(Properties.POWER,0).with(Properties.INVERTED,false));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(Properties.POWER,Properties.INVERTED);}
}
