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
    public record Data(BlockState source, long mask, boolean organic) {}

    public static HybridMesh.Neighbourhood snapshot(BlockView world, BlockPos origin, Data self) {
        var blocks=new ArrayList<HybridMesh.Block>(27);
        for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) for (int x=-1;x<=1;x++) {
            BlockPos p=origin.add(x,y,z);
            BlockState state=world.getBlockState(p);
            if (x==0 && y==0 && z==0) {
                blocks.add(new HybridMesh.Block(self.mask,self.organic,!self.organic,self.source.isOpaque()));
            } else if (state.getBlock() instanceof OrganicBlock organic) {
                blocks.add(new HybridMesh.Block(MicroGrid.FULL,true,false,organic.source().isOpaque()));
            } else if (state.getBlock() instanceof CarvedBlock) {
                Object attachment=world.getBlockEntityRenderData(p);
                if (attachment instanceof Data data) {
                    boolean hides=data.source.isOpaque() || data.source.equals(self.source);
                    blocks.add(new HybridMesh.Block(data.mask,data.organic,!data.organic && data.mask!=0,hides));
                } else blocks.add(new HybridMesh.Block(MicroGrid.FULL,false,true,true));
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
