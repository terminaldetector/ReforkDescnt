package com.terminaldetector.drmd.world.geometry;

import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Non-destructive adapter: vanilla states, growth, drops and saves retain their identity. */
public final class NaturalGeometry {
    private NaturalGeometry() {}
    private static final Set<Block> GROUND=Set.of(Blocks.STONE,Blocks.GRANITE,Blocks.DIORITE,
        Blocks.ANDESITE,Blocks.DEEPSLATE,Blocks.TUFF,Blocks.CALCITE,Blocks.DIRT,Blocks.GRASS_BLOCK,
        Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT,Blocks.PODZOL,Blocks.MYCELIUM,Blocks.CLAY,
        Blocks.SAND,Blocks.RED_SAND,Blocks.GRAVEL,Blocks.NETHERRACK,Blocks.END_STONE,
        Blocks.TERRACOTTA,Blocks.SNOW_BLOCK,Blocks.MOSS_BLOCK);
    private static final ConcurrentHashMap<HybridMesh.Neighbourhood,VoxelShape> SHAPES=new ConcurrentHashMap<>();
    public static boolean applies(BlockState state) {
        return GROUND.contains(state.getBlock()) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
    }
    public static VoxelShape shape(BlockState state,BlockView world,BlockPos pos) {
        var key=HybridTerrain.snapshot(world,pos,new HybridTerrain.Data(state,-1L,true));
        return shape(key);
    }
    public static VoxelShape shape(HybridMesh.Neighbourhood key) {
        var cached=SHAPES.get(key);if(cached!=null)return cached;
        var shape=BoundedHybridShape.from(new HybridMesh(key).collisionBounds());
        if(SHAPES.size()<4096)SHAPES.putIfAbsent(key,shape);
        return shape;
    }
}
