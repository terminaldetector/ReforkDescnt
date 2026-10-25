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
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void multiCellPlacementCannotOverwriteAnUnvalidatedNeighbour(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(4,5,4));player.setPosition(Vec3d.ofCenter(at.east(3)));
        world.setBlockState(at.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(at.up(),Blocks.BEDROCK.getDefaultState(),2);
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.OAK_DOOR,1));
        context.assertTrue(!AdaptivePlacement.placeState(world,player,at,Blocks.OAK_DOOR.getDefaultState(),true),"multi-cell footprint is not silently written");
        context.assertTrue(world.getBlockState(at.up()).isOf(Blocks.BEDROCK) && player.getOffHandStack().getCount()==1,"protected neighbour and item remain intact");
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.COMMAND_BLOCK,1));
        context.assertTrue(!AdaptivePlacement.placeState(world,player,at,Blocks.COMMAND_BLOCK.getDefaultState(),true),"survival cannot bypass vanilla operator placement");
        player.discard();context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void aClearDoorWritesBothHalvesAndConsumesOneItem(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(4,5,4));player.setPosition(Vec3d.ofCenter(at.east(3)));
        world.setBlockState(at.down(),Blocks.STONE.getDefaultState(),2);player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.OAK_DOOR,2));
        context.assertTrue(AdaptivePlacement.placeState(world,player,at,Blocks.OAK_DOOR.getDefaultState(),true),"clear complete door is supported");
        context.assertTrue(world.getBlockState(at).get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER
            && world.getBlockState(at.up()).isOf(Blocks.OAK_DOOR)
            && world.getBlockState(at.up()).get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER,"lower and upper states are complete");
        context.assertTrue(player.getOffHandStack().getCount()==1,"one door item pays for two cells");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bedFootAndHeadFollowAllFourFacingDirections(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(5,5,5));player.setPosition(Vec3d.ofCenter(at.up(3)));
        for(var facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}) {
            player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.RED_BED,1));
            var state=Blocks.RED_BED.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,facing);
            context.assertTrue(AdaptivePlacement.placeState(world,player,at,state,true),"bed facing is placeable: "+facing);
            context.assertTrue(world.getBlockState(at).get(net.minecraft.state.property.Properties.BED_PART)==net.minecraft.block.enums.BedPart.FOOT
                && world.getBlockState(at.offset(facing)).isOf(Blocks.RED_BED)
                && world.getBlockState(at.offset(facing)).get(net.minecraft.state.property.Properties.BED_PART)==net.minecraft.block.enums.BedPart.HEAD,"matching foot and head: "+facing);
            context.assertTrue(player.getOffHandStack().isEmpty(),"each complete bed consumes its one valid stack");
            world.setBlockState(at,Blocks.AIR.getDefaultState(),net.minecraft.block.Block.FORCE_STATE);
            world.setBlockState(at.offset(facing),Blocks.AIR.getDefaultState(),net.minecraft.block.Block.FORCE_STATE);
        }
        context.assertTrue(player.getOffHandStack().isEmpty(),"four complete beds consume four items");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void aTallPlantReceivesItsWholeFootprintFromOneItem(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(4,5,4));player.setPosition(Vec3d.ofCenter(at.east(3)));world.setBlockState(at.down(),Blocks.GRASS_BLOCK.getDefaultState(),2);
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.SUNFLOWER,1));
        context.assertTrue(AdaptivePlacement.placeState(world,player,at,Blocks.SUNFLOWER.getDefaultState(),true),"supported tall plant");
        context.assertTrue(world.getBlockState(at).isOf(Blocks.SUNFLOWER) && world.getBlockState(at.up()).isOf(Blocks.SUNFLOWER) && player.getOffHandStack().isEmpty(),"both parts from one material item");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void collisionOnlyAtTheBedHeadCannotLeaveAPlacedFoot(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(5,5,5));player.setPosition(Vec3d.ofCenter(at.north()));player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.RED_BED,1));
        context.assertTrue(!AdaptivePlacement.placeState(world,player,at,Blocks.RED_BED.getDefaultState(),true),"second-cell collision is rejected");
        context.assertTrue(world.getBlockState(at).isAir() && player.getOffHandStack().getCount()==1,"no partial foot or material charge");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void aMultiCellBatchCountsChangedCellsButChargesEachObjectOnce(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(4,5,4));player.setPosition(Vec3d.ofCenter(at.up(3)));
        world.setBlockState(at.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(at.east().down(),Blocks.STONE.getDefaultState(),2);
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.OAK_DOOR,2));
        int changed=AdaptivePlacement.placeMany(world,player,List.of(at,at.up(),at.east()),true);
        context.assertTrue(changed==4 && player.getOffHandStack().isEmpty(),"two objects/four cells/two items; overlapping candidate not charged");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeQuietWriteFailureRollsBackEveryAttemptedCell(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        var changes=List.of(new com.terminaldetector.drmd.world.build.PlacementTransaction.Change<>(at,world.getBlockState(at),Blocks.IRON_BLOCK.getDefaultState()),
            new com.terminaldetector.drmd.world.build.PlacementTransaction.Change<>(at.up(),world.getBlockState(at.up()),Blocks.IRON_BLOCK.getDefaultState()));
        var result=com.terminaldetector.drmd.world.build.PlacementTransaction.commit(changes,
            new com.terminaldetector.drmd.world.build.PlacementTransaction.Storage<BlockPos,net.minecraft.block.BlockState>() {
                public net.minecraft.block.BlockState read(BlockPos pos){return world.getBlockState(pos);}
                public boolean write(BlockPos pos,net.minecraft.block.BlockState state) {
                    if(pos.equals(at.up()) && state.isOf(Blocks.IRON_BLOCK))return false;
                    return world.setBlockState(pos,state,net.minecraft.block.Block.FORCE_STATE);
                }
            },()->true);
        context.assertTrue(result.status()==com.terminaldetector.drmd.world.build.PlacementTransaction.Status.ROLLED_BACK
            && world.getBlockState(at).isOf(Blocks.STONE) && world.getBlockState(at.up()).isAir(),"native backend restores original cells after second write fails");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void anUpperPartBeyondWorldHeightCannotPlaceOrConsumeTheLowerPart(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var column=context.getAbsolutePos(new BlockPos(4,5,4));var at=new BlockPos(column.getX(),world.getTopY()-1,column.getZ());
        player.setPosition(Vec3d.ofCenter(at.east(3)));player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.OAK_DOOR,1));
        context.assertTrue(!AdaptivePlacement.placeState(world,player,at,Blocks.OAK_DOOR.getDefaultState(),true)
            && player.getOffHandStack().getCount()==1 && world.getBlockState(at).isAir(),"complete height preflight precedes any write");player.discard();context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void previewPlanIsReadOnlyAndMatchesTheCommittedDoorFootprint(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var hit=context.getAbsolutePos(new BlockPos(4,4,4));player.setPosition(Vec3d.ofCenter(hit.east(3)));world.setBlockState(hit,Blocks.STONE.getDefaultState(),2);
        var tool=new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.BUILD_TOOL);player.setStackInHand(Hand.MAIN_HAND,tool);player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.OAK_DOOR,1));
        var proposal=com.terminaldetector.drmd.world.build.BuildToolItem.propose(world,player,tool,hit,Direction.UP,true);
        context.assertTrue(proposal.mode()==com.terminaldetector.drmd.world.build.BuildMode.SURFACE && proposal.plan().ready() && proposal.plan().cells().size()==2,"server-effective mode and full preview footprint");
        context.assertTrue(world.getBlockState(hit.up()).isAir() && world.getBlockState(hit.up(2)).isAir() && player.getOffHandStack().getCount()==1,"preview has no world/inventory writes");
        context.assertTrue(AdaptivePlacement.placeState(world,player,proposal.anchor(),proposal.plan().cells().getFirst().after(),false),"same proposal can be applied");
        for(var cell:proposal.plan().cells())context.assertTrue(world.getBlockState(cell.position()).equals(cell.after()),"actual cells match preview state");
        player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void constructionModeSynchronizesEvenWithoutALockedScaffold(TestContext context) {
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var payload=new com.terminaldetector.drmd.network.ModNetworking.ConstructionModePayload(true);
            com.terminaldetector.drmd.network.ModNetworking.ConstructionModePayload.CODEC.encode(buffer,payload);
            var decoded=com.terminaldetector.drmd.network.ModNetworking.ConstructionModePayload.CODEC.decode(buffer);
            context.assertTrue(decoded.active(),"adaptive mode metadata exists independently of any locked draft");
        } finally {buffer.release();}
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oversizedScaffoldFramesAreRejectedBeforeAllocation(TestContext context) {
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),context.getWorld().getRegistryManager());boolean rejected=false;
        try {
            buffer.writeBoolean(false);buffer.writeString("");buffer.writeVarInt(385);
            try{com.terminaldetector.drmd.network.ModNetworking.ScaffoldPayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}
        } finally {buffer.release();}
        context.assertTrue(rejected,"wire frame count is bounded before creating the positions list");context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explicitToolModesAreNotMaskedByAdaptiveDefaults(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        var hit=context.getAbsolutePos(new BlockPos(4,4,4));player.setPosition(Vec3d.ofCenter(hit.east(3)));player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.IRON_BLOCK,1));
        var tool=new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.BUILD_TOOL);
        for(var mode:com.terminaldetector.drmd.world.build.BuildMode.values()) {
            var nbt=new net.minecraft.nbt.NbtCompound();nbt.putString("mode",mode.name());
            tool.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(nbt));
            var persisted=tool.copy();
            var proposal=com.terminaldetector.drmd.world.build.BuildToolItem.propose(world,player,persisted,hit,Direction.UP,true);
            context.assertTrue(proposal.mode()==mode && !proposal.adaptiveDefault(),"explicit persisted "+mode+" wins over automatic surface default");
        }
        player.discard();context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void poweredTntIsChargedOnceWhenItImmediatelyBecomesAnEntity(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var at=context.getAbsolutePos(new BlockPos(4,5,4));player.setPosition(Vec3d.ofCenter(at.east(3)));
        world.setBlockState(at.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),2);player.setStackInHand(Hand.OFF_HAND,new ItemStack(Blocks.TNT,2));
        context.assertTrue(AdaptivePlacement.placeState(world,player,at,Blocks.TNT.getDefaultState(),true),"native TNT activation is a successful placement");
        var primed=world.getEntitiesByClass(net.minecraft.entity.TntEntity.class,new net.minecraft.util.math.Box(at).expand(2),entity->true);
        context.assertTrue(primed.size()==1 && player.getOffHandStack().getCount()==1,"one TNT entity from one paid item");
        for(var entity:primed)entity.discard();player.discard();context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void publishedScaffoldFrameFormatRemainsReadableWithoutAModeField(TestContext context) {
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var position=context.getAbsolutePos(new BlockPos(4,5,4));
            buffer.writeBoolean(true);buffer.writeString("line");buffer.writeVarInt(1);buffer.writeBlockPos(position);
            var decoded=com.terminaldetector.drmd.network.ModNetworking.ScaffoldPayload.CODEC.decode(buffer);
            context.assertTrue(decoded.active() && decoded.shapeId().equals("line") && decoded.positions().equals(List.of(position)),"existing scaffold channel has no inserted boolean");
        } finally {buffer.release();}
        context.complete();
    }

}
