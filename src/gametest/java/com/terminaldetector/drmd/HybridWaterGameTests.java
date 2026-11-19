package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.geometry.*;
import com.terminaldetector.drmd.world.micro.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.fluid.*;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import java.util.List;
public class HybridWaterGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=300)
    public void normalWaterFlowsThroughTheCutAndDecaysWithoutDestroyingItsRetainedSolid(TestContext ctx){
        var w=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(3,4,2));
        for(int z=-2;z<=4;z++)for(int x=-1;x<=1;x++){
            w.setBlockState(p.add(x,-1,z),Blocks.IRON_BLOCK.getDefaultState());
            w.setBlockState(p.add(x,0,z),x==0?Blocks.AIR.getDefaultState():Blocks.GLASS.getDefaultState());
            w.setBlockState(p.add(x,1,z),Blocks.AIR.getDefaultState());
        }
        w.setBlockState(p.north(2),Blocks.GLASS.getDefaultState());w.setBlockState(p.south(4),Blocks.GLASS.getDefaultState());
        long mask=MicroGrid.build((x,y,z)->y==0);
        for(var at:List.of(p,p.south())){w.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState());ctx.assertTrue(CarvedBlock.replace(w,at,Blocks.IRON_BLOCK.getDefaultState(),mask)!=null,"real retained floor carve");}
        w.setBlockState(p.north(),Blocks.WATER.getDefaultState());
        ctx.runAtTick(100,()->{
            for(var at:List.of(p,p.south())){
                ctx.assertTrue(w.getBlockEntity(at)instanceof CarvedBlockEntity c&&c.mask()==mask&&c.source().isOf(Blocks.IRON_BLOCK),"normal flow preserves material/BE/mask");
                ctx.assertTrue(w.getBlockState(at).get(Properties.WATERLOGGED)&&!w.getFluidState(at).isStill(),"incoming stream retains actual flowing level, not an invented source");
            }
            ctx.assertTrue(w.getFluidState(p.south(2)).isIn(net.minecraft.registry.tag.FluidTags.WATER),"real vanilla flow exits the shaped passage");
            w.setBlockState(p.north(),Blocks.AIR.getDefaultState());
        });
        ctx.runAtTick(250,()->{
            for(var at:List.of(p,p.south()))ctx.assertTrue(w.getBlockEntity(at)instanceof CarvedBlockEntity c&&c.mask()==mask&&w.getFluidState(at).isEmpty(),"normal native fluid decay clears water and retains carved solid");
            ctx.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void RealWaterNeverPinsAnAnalyticBoreIntoANewFlatWall(TestContext ctx){
        var w=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(3,4,3));
        for(var q:BlockPos.iterate(p.add(-1,-1,-1),p.add(1,1,1)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());var bore=new ThermalBore(.5,-1,.5,.5,2,.5,.5);
        var be=CarvedBlock.replace(w,p,Blocks.IRON_BLOCK.getDefaultState(),bore.mask());ctx.assertTrue(be!=null,"nonempty actual cut");be.setThermal(new ThermalDrillCell(0,0,0,0,List.of(bore)));
        var a=HybridTerrain.snapshot(w,p,be.getRenderData());var meshA=new HybridMesh(a);var collision=w.getBlockState(p).getCollisionShape(w,p);
        w.setBlockState(p.east(),Blocks.WATER.getDefaultState());var b=HybridTerrain.snapshot(w,p,be.getRenderData());var meshB=new HybridMesh(b);
        ctx.assertTrue(a.equals(b),"ordinary water is empty physical space, not an engineered smoothing anchor");
        for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)for(int x=0;x<=4;x++)ctx.assertTrue(meshA.vertex(x,y,z).equals(meshB.vertex(x,y,z)),"water preserves exact rendered bore at every shared vertex");
        ctx.assertTrue(collision.getBoundingBoxes().equals(w.getBlockState(p).getCollisionShape(w,p).getBoundingBoxes()),"normal collision and visible bore stay coherent");
        ctx.complete();
    }
}
