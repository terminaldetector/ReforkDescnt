package com.terminaldetector.drmd.world.compat;

import com.terminaldetector.drmd.stock.SurfaceStockFixtureState;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fresh ordinary-profile STOCK controls, selected only by the dedicated focus helper. */
public final class NativeDeferredFluidStockGameTests implements FabricGameTest {
    private static void stock(TestContext c) {
        var world=c.getWorld();
        var actual=FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        var expected=System.getProperty("drmd.test.surfaceStock.directory","");
        c.assertTrue(!expected.isBlank()&&actual.equals(Path.of(expected).toAbsolutePath().normalize()),"dedicated fresh STOCK gate required");
        c.assertTrue(SurfaceStockFixtureState.ENABLED&&SurfaceStockFixtureState.selected&&!SurfaceStockFixtureState.enabled.contains(SurfaceStockFixtureState.LEGACY)&&SurfaceStockFixtureState.disabled.contains(SurfaceStockFixtureState.LEGACY),"actual ordinary profile selected before registry loading");
        c.assertTrue(CubicAvailability.backend(world)==CubicAvailability.Backend.STOCK&&world.getBottomY()==-64&&world.getTopY()==320,"actual ordinary STOCK backend/bounds required");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void ordinaryWaterContinuesWithoutNativePendingWork(TestContext c) {
        stock(c);var world=c.getWorld();
        for(int y=0;y<=5;y++)for(int x=2;x<=4;x++)for(int z=2;z<=4;z++) {
            var pos=c.getAbsolutePos(new BlockPos(x,y,z));
            world.setBlockState(pos,(y==0||y==5||x==2||x==4||z==2||z==4)?Blocks.GLASS.getDefaultState():Blocks.AIR.getDefaultState(),3);
        }
        var source=c.getAbsolutePos(new BlockPos(3,4,3));var target=c.getAbsolutePos(new BlockPos(3,1,3));
        world.setBlockState(source,Blocks.WATER.getDefaultState(),3);
        c.waitAndRun(40,()->{
            c.assertTrue(world.getFluidState(target).isIn(FluidTags.WATER),"real ordinary water must flow naturally to lower target");
            var state=NativeDeferredFluids.snapshot(world);
            c.assertTrue(state.pending()==0&&state.added()==0&&state.backpressureQueued()==0&&state.resumed()==0,"native compatibility is a no-op for STOCK");
            var file=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data").resolve(NativeDeferredFluids.STATE_ID+".dat");
            c.assertTrue(!Files.exists(file),"ordinary STOCK flow must not create a native pending-work file");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void waterAndLavaUseCanonicalFamiliesAndActualVanillaRates(TestContext c) {
        stock(c);var world=c.getWorld();
        c.assertTrue(NativeDeferredFluids.family((FlowableFluid)Fluids.WATER).equals("minecraft:water")&&NativeDeferredFluids.family((FlowableFluid)Fluids.FLOWING_WATER).equals("minecraft:water"),"still/flowing water retain one canonical family");
        c.assertTrue(NativeDeferredFluids.family((FlowableFluid)Fluids.LAVA).equals("minecraft:lava")&&NativeDeferredFluids.family((FlowableFluid)Fluids.FLOWING_LAVA).equals("minecraft:lava"),"lava identity must not become water");
        c.assertTrue(Fluids.WATER.getTickRate(world)==5&&Fluids.LAVA.getTickRate(world)==30,"actual normal Overworld rates are delegated, not one hardcoded water delay");
        c.assertTrue(!Fluids.WATER.matchesType(Fluids.LAVA)&&Fluids.WATER.matchesType(Fluids.FLOWING_WATER),"current replacement fluid must match the recorded family");c.complete();
    }
}
