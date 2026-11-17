package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.store.SurfaceIngest;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Real STOCK public-callback regression; selected by the dedicated focus helper. */
public final class SurfaceIngestHeightGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="surface-ingest-loaded-gold")
    public void loadedGoldUsesItsActualColourAndPhysicalTopFace(TestContext c){exercise(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="surface-ingest-loaded-demolition")
    public void loadedDemolitionAndRestoreUpdateActualSurface(TestContext c){exercise(c,true);}
    private static void exercise(TestContext c,boolean demolition){
        var world=c.getWorld();var origin=c.getAbsolutePos(new BlockPos(3,3,3));
        int cx=origin.getX()>>4,cz=origin.getZ()>>4;
        var high=new BlockPos((cx<<4)+8,240,(cz<<4)+8);var low=high.down(2);
        c.assertTrue(CubicAvailability.backend(world)==CubicAvailability.Backend.STOCK,"actual STOCK backend control");
        c.assertTrue(world.getBottomY()==-64&&world.getTopY()==320,"ordinary stock bounds; no focused tall-world override may leak");
        c.assertTrue(WorldAccess.of(world).isVolumeAvailable(D6ChunkPos.ofBlock(high.getX(),high.getY(),high.getZ())),"test uses an already FULL volume");
        var chunk=CubicAvailability.loadedColumn(world,cx,cz);c.assertTrue(chunk!=null,"already-ready actual chunk required");
        c.assertTrue(SurfaceIngest.store()!=null,"actual server SurfaceStore initialized");
        var oldHigh=world.getBlockState(high);var oldLow=world.getBlockState(low);
        c.assertTrue(oldHigh.isAir()&&oldLow.isAir(),"authored floating witnesses must start in actual loaded air");
        try{
            world.setBlockState(low,Blocks.LIME_CONCRETE.getDefaultState(),3);world.setBlockState(high,Blocks.GOLD_BLOCK.getDefaultState(),3);
            SurfaceIngest.onChunkLoad(world,chunk);
            assertCell(c,high,241,Blocks.GOLD_BLOCK.getDefaultState().getMapColor(world,high).color,"loaded GOLD");
            if(demolition){
                world.setBlockState(high,Blocks.AIR.getDefaultState(),3);SurfaceIngest.onChunkLoad(world,chunk);
                assertCell(c,high,239,Blocks.LIME_CONCRETE.getDefaultState().getMapColor(world,low).color,"loaded demolition to LIME");
                world.setBlockState(high,Blocks.GOLD_BLOCK.getDefaultState(),3);SurfaceIngest.onChunkLoad(world,chunk);
                assertCell(c,high,241,Blocks.GOLD_BLOCK.getDefaultState().getMapColor(world,high).color,"loaded GOLD restore");
            }
        }finally{world.setBlockState(high,oldHigh,3);world.setBlockState(low,oldLow,3);SurfaceIngest.onChunkLoad(world,chunk);}
        c.complete();
    }
    private static void assertCell(TestContext c,BlockPos at,int height,int colour,String stage){
        var store=SurfaceIngest.store();var actualH=store.heightAt(at.getX(),at.getZ(),0);var actualC=store.colourAt(at.getX(),at.getZ(),0);
        System.out.println("SURFACE_STOCK stage="+stage+" height="+actualH+" colour="+actualC);
        c.assertTrue(actualH==height&&actualC==colour,stage+" must use actual block colour and top face, got "+actualH+"/"+actualC+", expected "+height+"/"+colour);
    }
}
