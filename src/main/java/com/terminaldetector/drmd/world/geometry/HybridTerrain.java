package com.terminaldetector.drmd.world.geometry;

import com.terminaldetector.drmd.world.micro.CarvedBlock;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import java.util.ArrayList;

/** Material/shape adapter shared by server collision and chunk-worker meshing. */
public final class HybridTerrain {
    private HybridTerrain() {}
    /** Immutable snapshot; a mesh worker must never retain a live block entity. */
    public record Data(BlockState source,long mask,boolean organic,java.util.List<com.terminaldetector.drmd.world.micro.ThermalBore> bores,int heat) {
        public Data(BlockState source,long mask,boolean organic){this(source,mask,organic,java.util.List.of(),0);}
        public Data {bores=java.util.List.copyOf(bores);if(bores.size()>2||heat<0||heat>3)throw new IllegalArgumentException("thermal render bounds");}
        public HybridMaterialPolicy material(){return HybridMaterialPolicy.of(organic,mask,heat,!bores.isEmpty(),false);}
        private HybridMesh.Block meshBlock(boolean occludes,boolean anchorEmpty){
            var policy=HybridMaterialPolicy.of(organic,mask,heat,!bores.isEmpty(),anchorEmpty);
            return new HybridMesh.Block(mask,policy.smooth(),policy.anchor(),occludes,bores,NaturalGeometry.logAxis(source));
        }
    }

    public static HybridMesh.Neighbourhood snapshot(BlockView world, BlockPos origin, Data self) {
        var blocks=new ArrayList<HybridMesh.Block>(27);
        for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) for (int x=-1;x<=1;x++) {
            BlockPos p=origin.add(x,y,z);
            BlockState state=world.getBlockState(p);
            if (x==0 && y==0 && z==0) {
                blocks.add(self.meshBlock(self.source.isOpaque(),true));
            } else if (state.getBlock() instanceof OrganicBlock organic) {
                blocks.add(new Data(organic.source(),MicroGrid.FULL,true).meshBlock(organic.source().isOpaque(),false));
            } else if (state.getBlock() instanceof CarvedBlock) {
                Object attachment=world.getBlockEntityRenderData(p);
                if (attachment instanceof Data data) {
                    boolean hides=data.source.isOpaque() || data.source.equals(self.source);
                    blocks.add(data.meshBlock(hides,false));
                } else blocks.add(new HybridMesh.Block(MicroGrid.FULL,false,true,true));
            } else if (NaturalGeometry.applies(state)) {
                blocks.add(new Data(state,MicroGrid.FULL,true).meshBlock(state.isOpaque() || state.equals(self.source),false));
            } else if (state.isAir()) blocks.add(HybridMesh.Block.AIR);
            else {
                boolean full=state.isFullCube(world,p);
                boolean hides=state.isOpaqueFullCube(world,p) || (full && state.equals(self.source));
                blocks.add(new HybridMesh.Block(full?MicroGrid.FULL:0,false,true,hides));
            }
        }
        return new HybridMesh.Neighbourhood(blocks);
    }
}
