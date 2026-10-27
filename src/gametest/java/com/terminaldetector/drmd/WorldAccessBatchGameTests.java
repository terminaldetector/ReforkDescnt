package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WorldAccessBatchGameTests implements FabricGameTest {
    private static ServerBlockEdits.Cell air(){return new ServerBlockEdits.Cell(Blocks.AIR.getDefaultState(),null);}
    private static ServerBlockEdits.Cell iron(){return new ServerBlockEdits.Cell(Blocks.IRON_BLOCK.getDefaultState(),null);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allCellsValidateBeforeAnyWorldMutation(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var a=context.getAbsolutePos(new BlockPos(3,4,3));var b=a.east();
        world.setBlockState(a,Blocks.AIR.getDefaultState(),2);world.setBlockState(b,Blocks.GOLD_BLOCK.getDefaultState(),2);
        var result=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(a,air(),iron()),new ServerBlockEdits.Edit(b,air(),iron())));
        context.assertTrue(result.status()==ServerBlockEdits.Status.CONFLICT && world.getBlockState(a).isAir()
            && world.getBlockState(b).isOf(Blocks.GOLD_BLOCK),"stale second cell cannot partially commit the first");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void anUnavailableCellCannotPullAColumnIntoMemory(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var a=context.getAbsolutePos(new BlockPos(3,4,3));var far=a.add(8_000_000,0,0);
        world.setBlockState(a,Blocks.AIR.getDefaultState(),2);
        var result=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(a,air(),iron()),new ServerBlockEdits.Edit(far,air(),iron())));
        context.assertTrue(result.status()==ServerBlockEdits.Status.UNAVAILABLE && world.getBlockState(a).isAir()
            && !world.isChunkLoaded(far),"all writes rejected without fetching remote storage");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void chestOwnershipMovesWithExactItemsAndNoScatteredDuplicate(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var a=context.getAbsolutePos(new BlockPos(3,4,3));var b=a.east();
        world.setBlockState(a,Blocks.CHEST.getDefaultState(),2);world.setBlockState(b,Blocks.AIR.getDefaultState(),2);
        ((ChestBlockEntity)world.getBlockEntity(a)).setStack(0,new ItemStack(Items.DIAMOND,37));
        var before=access.snapshotIfLoaded(a).orElseThrow();var moved=before.entityData();
        moved.putInt("x",b.getX());moved.putInt("y",b.getY());moved.putInt("z",b.getZ());
        int drops=world.getEntitiesByClass(ItemEntity.class,new Box(a).union(new Box(b)).expand(.25),e->true).size();
        var result=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(a,before,air()),
            new ServerBlockEdits.Edit(b,air(),new ServerBlockEdits.Cell(before.state(),moved))));
        context.assertTrue(result.committed() && result.changed()==2,"native state/NBT batch committed: "+result);
        context.assertTrue(world.getBlockState(a).isAir() && world.getBlockEntity(a)==null,"old owner removed");
        var chest=(ChestBlockEntity)world.getBlockEntity(b);
        context.assertTrue(chest!=null && chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount()==37,"exact contents at the new owner");
        context.assertTrue(drops==world.getEntitiesByClass(ItemEntity.class,new Box(a).union(new Box(b)).expand(.25),e->true).size(),"no duplicated scattered inventory");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wrongEntityTypeCannotProduceDummySavedData(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var at=context.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(at,Blocks.CHEST.getDefaultState(),2);var before=access.snapshotIfLoaded(at).orElseThrow();
        var bad=before.entityData();bad.putString("id","minecraft:furnace");
        var result=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(at,before,new ServerBlockEdits.Cell(before.state(),bad))));
        context.assertTrue(result.status()==ServerBlockEdits.Status.INVALID_NBT && access.snapshotIfLoaded(at).orElseThrow().equals(before),"type mismatch rejected before touching the chest");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void snapshotsCannotBeMutatedThroughReturnedNbt(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var at=context.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(at,Blocks.CHEST.getDefaultState(),2);var before=access.snapshotIfLoaded(at).orElseThrow();
        var escaped=before.entityData();escaped.putString("id","bad:mutated");
        context.assertTrue(before.entityData().getString("id").equals("minecraft:chest")
            && access.snapshotIfLoaded(at).orElseThrow().equals(before),"snapshot and live backend retain independent NBT");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reactiveBlockCallbacksNeedAnExplicitAdapter(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var at=context.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(at,Blocks.AIR.getDefaultState(),2);
        var result=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(at,air(),new ServerBlockEdits.Cell(Blocks.TNT.getDefaultState(),null))));
        context.assertTrue(result.status()==ServerBlockEdits.Status.UNSUPPORTED_BLOCK && world.getBlockState(at).isAir(),"reactive entity-spawning callbacks never run inside a rollbackable batch");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workerMutationIsRejectedWithoutSchedulingItLater(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);var at=context.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(at,Blocks.AIR.getDefaultState(),2);
        var result=CompletableFuture.supplyAsync(()->access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(at,air(),iron())))).join();
        context.assertTrue(result.status()==ServerBlockEdits.Status.REJECTED && world.getBlockState(at).isAir(),"world edits require direct server authority");context.complete();
    }
}
