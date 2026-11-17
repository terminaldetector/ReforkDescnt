package com.terminaldetector.drmd.world.contraption.mechanism;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;

/** An opposing pair supplies the material axis of a bounded linear stage. */
public final class SlidingBearingBlock extends MechanicalBearingBlock {
    public static final MapCodec<SlidingBearingBlock> CODEC=createCodec(SlidingBearingBlock::new);
    public SlidingBearingBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
}
