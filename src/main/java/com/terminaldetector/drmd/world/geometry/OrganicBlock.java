package com.terminaldetector.drmd.world.geometry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Intact organic material lives in the ordinary chunk palette, without a block entity. */
public final class OrganicBlock extends Block {
    public static final MapCodec<OrganicBlock> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
        BlockState.CODEC.fieldOf("source").forGetter(b -> b.source),
        OrganicBlock.<OrganicBlock>createSettingsCodec()).apply(i,OrganicBlock::new));
    private static final ConcurrentHashMap<HybridMesh.Neighbourhood,VoxelShape> SHAPES=new ConcurrentHashMap<>();
    private final BlockState source;
    public OrganicBlock(BlockState source,Settings settings) {super(settings);this.source=source;}
    public BlockState source() {return source;}
    @Override protected MapCodec<? extends Block> getCodec() {return CODEC;}
    private VoxelShape shape(BlockView world,BlockPos pos) {
        var key=HybridTerrain.snapshot(world,pos,new HybridTerrain.Data(source,-1,true));
        var cached=SHAPES.get(key);if(cached!=null)return cached;
        var shape=VoxelShapes.empty();
        for(var b:new HybridMesh(key).collisionBounds()) shape=VoxelShapes.union(shape,
            VoxelShapes.cuboid(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));
        shape=shape.simplify();if(SHAPES.size()<4096)SHAPES.putIfAbsent(key,shape);return shape;
    }
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return shape(w,p);}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return shape(w,p);}
    @Override public VoxelShape getCameraCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return shape(w,p);}
    @Override public float getAmbientOcclusionLightLevel(BlockState s,BlockView w,BlockPos p){return 1;}
    @Override public boolean isTransparent(BlockState s,BlockView w,BlockPos p){return true;}
    @Override public ItemStack getPickStack(net.minecraft.world.WorldView w,BlockPos p,BlockState s){return new ItemStack(source.getBlock());}
    @Override public List<ItemStack> getDroppedStacks(BlockState s,LootContextParameterSet.Builder builder){return source.getDroppedStacks(builder);}
}
