package com.terminaldetector.drmd.world.geometry;

import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import java.util.Map;
import java.util.HashMap;

/** Explicit material identity separates terrain from player-built blocks of the same material. */
public final class OrganicMaterials {
    private static final Map<Block,OrganicBlock> MATERIALS=new HashMap<>();
    private OrganicMaterials() {}
    public static void register() {
        for(Block source:new Block[]{Blocks.STONE,Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE,
            Blocks.DEEPSLATE,Blocks.TUFF,Blocks.CALCITE,Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.CLAY,
            Blocks.NETHERRACK,Blocks.END_STONE,Blocks.TERRACOTTA}) {
            var id=Identifier.of("drmd","organic_"+Registries.BLOCK.getId(source).getPath());
            var block=new OrganicBlock(source.getDefaultState(),AbstractBlock.Settings.copy(source).nonOpaque().dynamicBounds());
            Registry.register(Registries.BLOCK,id,block);MATERIALS.put(source,block);
        }
    }
    public static BlockState intact(BlockState source) {
        var block=MATERIALS.get(source.getBlock());
        // Non-default properties retain the BE adapter rather than silently losing orientation.
        return block!=null && source.equals(block.source())?block.getDefaultState():null;
    }
}
