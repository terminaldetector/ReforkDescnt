package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.build.AdaptivePlacement;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.List;

/** Actual inventory/world transactions, not a second implementation of the placement policy. */
public final class ConstructionPlacementGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void surfacePlacementConsumesExactlyOneAndCannotBuildWithoutMaterial(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(4,4,4));
        player.setPosition(Vec3d.ofCenter(base.up(4)));
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.IRON_BLOCK,2));
        for(int i=0;i<2;i++) {
            var hit=base.add(i,0,0);world.setBlockState(hit,Blocks.STONE.getDefaultState(),2);
            context.assertTrue(AdaptivePlacement.placeOnSurface(world,player,hit,Direction.UP)!=null,"surface transaction succeeded");
            context.assertTrue(player.getOffHandStack().getCount()==1-i,"exactly one actual offhand block consumed");
        }
        context.assertTrue(AdaptivePlacement.placeOnSurface(world,player,base.east(2),Direction.UP)==null,"no free iron fallback in survival");
        player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hotbarMaterialIsChargedAndRejectedTargetsDoNotConsumeIt(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));
        player.setPosition(Vec3d.ofCenter(at.up(3)));player.getInventory().setStack(1,new ItemStack(Blocks.STONE,2));
        world.setBlockState(at,Blocks.BEDROCK.getDefaultState(),2);
        context.assertTrue(!AdaptivePlacement.placeState(world,player,at,Blocks.STONE.getDefaultState(),false),"solid target rejected");
        context.assertTrue(player.getInventory().getStack(1).getCount()==2,"rejection preserves material");
        context.assertTrue(AdaptivePlacement.placeState(world,player,at.east(),Blocks.STONE.getDefaultState(),false),"hotbar fallback can build");
        context.assertTrue(player.getInventory().getStack(1).getCount()==1,"actual hotbar source charged once");
        var outside=new BlockPos(at.getX(),world.getTopY(),at.getZ());
        context.assertTrue(!AdaptivePlacement.placeState(world,player,outside,Blocks.STONE.getDefaultState(),false),"world boundary rejected");
        context.assertTrue(player.getInventory().getStack(1).getCount()==1,"boundary rejection does not consume");
        player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void batchDuplicatesAndExhaustionNeverDuplicateOrDoubleCharge(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(3,5,4));player.setPosition(Vec3d.ofCenter(at.up(3)));
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.IRON_BLOCK,2));
        int count=AdaptivePlacement.placeMany(context.getWorld(),player,List.of(at,at,at.east(),at.east(2)),true);
        context.assertTrue(count==2 && player.getOffHandStack().isEmpty(),"two unique placements consume two blocks");
        context.assertTrue(context.getWorld().getBlockState(at.east(2)).isAir(),"batch stops on empty material");
        player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void creativeKeepsInventoryButCannotPlaceInsideThePlayer(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        var at=context.getAbsolutePos(new BlockPos(4,5,4));player.setPosition(Vec3d.ofCenter(at));
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.IRON_BLOCK,3));
        context.assertTrue(!AdaptivePlacement.placeState(context.getWorld(),player,at,Blocks.IRON_BLOCK.getDefaultState(),true),"player collision rejected");
        context.assertTrue(AdaptivePlacement.placeState(context.getWorld(),player,at.east(2),Blocks.IRON_BLOCK.getDefaultState(),true),"clear creative cell placed");
        context.assertTrue(player.getOffHandStack().getCount()==3,"creative does not consume");
        player.discard();context.complete();
    }
}
