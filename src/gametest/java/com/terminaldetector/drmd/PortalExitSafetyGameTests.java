package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.portal.PortalTravel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PortalExitSafetyGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clearFeetDoNotPermitAnExitWithTheHeadInsideAStoneBlock(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        var at=context.getAbsolutePos(new BlockPos(5,5,5));var origin=Vec3d.ofCenter(at.west(3));player.setPosition(origin);
        world.setBlockState(at,Blocks.AIR.getDefaultState(),2);world.setBlockState(at.up(),Blocks.STONE.getDefaultState(),2);
        context.assertTrue(!PortalTravel.exitIsClear(world,player,Vec3d.ofCenter(at)),"whole body is tested, not only the destination cell");
        context.assertTrue(PortalTravel.exitIsClear(world,player,Vec3d.ofCenter(at.east(2))),"clear destination is allowed");
        context.assertTrue(player.getPos().equals(origin),"preflight validation never moves the traveller");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void heightAndUnloadedExitRejectionsPreserveTheTraveller(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        var origin=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,5,5)));player.setPosition(origin);
        context.assertTrue(!PortalTravel.exitIsClear(world,player,new Vec3d(origin.x,world.getTopY()-1,origin.z)),"head cannot exit above the runtime height limit");
        context.assertTrue(!PortalTravel.exitIsClear(world,player,new Vec3d(500000,80,500000)),"unloaded exit is declined");
        context.assertTrue(!world.isChunkLoaded(new BlockPos(500000,80,500000)) && player.getPos().equals(origin),"no chunk generation or partial teleport");
        player.discard();context.complete();
    }
}
