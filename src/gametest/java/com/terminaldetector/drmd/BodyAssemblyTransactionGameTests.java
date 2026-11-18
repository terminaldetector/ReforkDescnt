package com.terminaldetector.drmd;

import com.terminaldetector.drmd.assemblyprobe.AssemblyWriteProbe;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.BodyMountedWeapons;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.entity.ItemEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual source material and vanilla dependent blocks. The only injected fault
 * is an explicitly armed exact-cell write denial in the opt-in test mixin. */
public final class BodyAssemblyTransactionGameTests implements FabricGameTest {
    private static final BlockPos SUPPORT=BlockPos.ORIGIN,SECOND=new BlockPos(3,0,0);
    private static BlockState lever(Direction fromSupport,boolean powered){
        return Blocks.LEVER.getDefaultState().with(Properties.POWERED,powered)
            .with(Properties.BLOCK_FACE,fromSupport==Direction.UP?BlockFace.FLOOR:fromSupport==Direction.DOWN?BlockFace.CEILING:BlockFace.WALL)
            .with(Properties.HORIZONTAL_FACING,fromSupport.getAxis().isHorizontal()?fromSupport:Direction.NORTH);
    }
    private static final class Fixture implements AutoCloseable {
        final TestContext c;final AiFixtureChunks chunks;final BlockPos origin;
        final Map<BlockPos,BlockState> source=new LinkedHashMap<>(),outside=new LinkedHashMap<>();
        final Box area;final Set<UUID> originalBodies,originalItems;BlockBodyEntity body;
        Fixture(TestContext c,Map<BlockPos,BlockState> selected,Map<BlockPos,BlockState> extra){
            this.c=c;origin=c.getAbsolutePos(new BlockPos(8,220,8));chunks=new AiFixtureChunks(c.getWorld(),origin,1);
            selected.forEach((p,s)->source.put(origin.add(p),s));extra.forEach((p,s)->outside.put(origin.add(p),s));
            area=new Box(origin).expand(5);originalBodies=bodyIds();originalItems=itemIds();
            try{
                for(var p:scope())c.assertTrue(c.getWorld().getBlockState(p).isAir(),"transaction fixture requires original AIR");
                for(var entry:source.entrySet())c.assertTrue(c.getWorld().setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_LISTENERS),"actual selected part placed");
                for(var entry:outside.entrySet())c.assertTrue(c.getWorld().setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_LISTENERS),"actual external dependent part placed");
                for(var entry:source.entrySet())c.assertTrue(c.getWorld().getBlockState(entry.getKey()).equals(entry.getValue()),"actual complete selection is stable before assembly");
                for(var entry:outside.entrySet())c.assertTrue(entry.getValue().canPlaceAt(c.getWorld(),entry.getKey()),"external dependent block has real support before assembly");
                c.assertTrue(newItems().isEmpty(),"fixture setup itself may not generate a drop");
            }catch(RuntimeException|Error failure){close();throw failure;}
        }
        Set<BlockPos> scope(){var cells=new HashSet<>(source.keySet());cells.addAll(outside.keySet());return cells;}
        Set<UUID> bodyIds(){var ids=new HashSet<UUID>();for(var b:BodySpatialIndex.query(c.getWorld(),area))ids.add(b.getUuid());return ids;}
        Set<UUID> itemIds(){var ids=new HashSet<UUID>();for(var item:c.getWorld().getEntitiesByClass(ItemEntity.class,area,e->true))ids.add(item.getUuid());return ids;}
        List<ItemEntity> newItems(){return c.getWorld().getEntitiesByClass(ItemEntity.class,area,e->!originalItems.contains(e.getUuid()));}
        void assertOriginal(){
            for(var entry:source.entrySet())c.assertTrue(c.getWorld().getBlockState(entry.getKey()).equals(entry.getValue()),"exact selected state restored: "+entry.getKey());
            for(var entry:outside.entrySet())c.assertTrue(c.getWorld().getBlockState(entry.getKey()).equals(entry.getValue()),"external supported state retained: "+entry.getKey());
        }
        void assertMoved(){
            int minX=source.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int minY=source.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
            int minZ=source.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            var min=new BlockPos(minX,minY,minZ);
            for(var entry:source.entrySet())c.assertTrue(c.getWorld().getBlockState(entry.getKey()).isAir()
                &&body.getLocalBlock(entry.getKey().subtract(min)).equals(entry.getValue()),"each exact source state has only the physical owner");
            c.assertTrue(body.cells().size()==source.size(),"no extra physical material inserted");
        }
        void assertNoDuplicate(){c.assertTrue(bodyIds().equals(originalBodies)&&newItems().isEmpty(),"no retained body or item owner after refusal: bodies="+bodyIds()+" drops="+newItems().stream().map(e->e.getStack().toString()).toList());}
        void dump(String label,AssemblyWriteProbe.Session probe){System.out.println("ASSEMBLY_TX_PROOF "+label+" events="+probe.events()+" truncated="+probe.truncated()+" drops="+newItems().stream().map(e->e.getUuid()+":"+e.getStack()).toList());}
        @Override public void close(){
            if(body!=null)body.discard();
            // Only newly created objects in this exact fixture's area are owned here.
            for(var b:BodySpatialIndex.query(c.getWorld(),area))if(!originalBodies.contains(b.getUuid()))b.discard();
            for(var item:newItems())item.discard();
            try{
                for(var p:outside.keySet())c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState(),Block.FORCE_STATE);
                for(var p:source.keySet())c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState(),Block.FORCE_STATE);
            }finally{chunks.close();}
        }
    }
    private static Map<BlockPos,BlockState> pair(){var cells=new LinkedHashMap<BlockPos,BlockState>();cells.put(SUPPORT,Blocks.IRON_BLOCK.getDefaultState());cells.put(SECOND,Blocks.GOLD_BLOCK.getDefaultState());return cells;}
    private static RuntimeException denied(Fixture f){
        try{f.body=BlockBodyEntity.assembleSelection(f.c.getWorld(),f.source);throw new AssertionError("actual mid-commit refusal must not succeed");}
        catch(IllegalStateException expected){return expected;}
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="assembly-dependent-six")
    public void allSixRealLeverSupportsTransferInCallerOrderWithoutDrops(TestContext c){
        for(var side:Direction.values())for(boolean powered:new boolean[]{false,true}){
            var cells=new LinkedHashMap<BlockPos,BlockState>();cells.put(SUPPORT,Blocks.IRON_BLOCK.getDefaultState());cells.put(SUPPORT.offset(side),lever(side,powered));
            try(var f=new Fixture(c,cells,Map.of());var probe=AssemblyWriteProbe.open(c.getWorld(),f.scope(),null,null)){
                try{
                    var leverWorld=f.origin.offset(side);c.assertTrue(BodyMountedWeapons.support(leverWorld,c.getWorld().getBlockState(leverWorld)).equals(f.origin),"actual lever support matches the real selected block");
                    f.body=BlockBodyEntity.assembleSelection(c.getWorld(),f.source);
                    f.assertMoved();
                    c.assertTrue(f.body.cells().size()==2&&f.newItems().isEmpty()&&!probe.truncated(),"two real material cells, no drop/duplicate or truncated observation");
                    c.assertTrue(probe.events().stream().filter(e->e.stage().equals("HEAD")).count()>=2,"actual write hook observes both source transfers");
                    c.assertTrue(probe.events().stream().filter(e->e.stage().equals("HEAD")).allMatch(e->(e.flags()&Block.FORCE_STATE)!=0),"actual transfer writes are quiet, not merely a synthetic storage test");
                }finally{f.dump(side+"/powered="+powered,probe);}
            }
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="assembly-powered-multiple")
    public void multiplePoweredLeversOnOneActualGunDoNotDropEachOther(TestContext c){
        var cells=new LinkedHashMap<BlockPos,BlockState>();cells.put(SUPPORT,ModWorldBlocks.SHIP_LASER.getDefaultState());
        cells.put(SUPPORT.east(),lever(Direction.EAST,true));cells.put(SUPPORT.west(),lever(Direction.WEST,true));
        try(var f=new Fixture(c,cells,Map.of());var probe=AssemblyWriteProbe.open(c.getWorld(),f.scope(),null,null)){
            try{f.body=BlockBodyEntity.assembleSelection(c.getWorld(),f.source);f.assertMoved();c.assertTrue(f.body.cells().size()==3&&f.newItems().isEmpty()&&!probe.truncated(),"the caller's gun-first order transfers both powered levers without a dropped second owner");}
            finally{f.dump("two-powered-levers",probe);}
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="assembly-denied-torch")
    public void aRealMidCommitDenialRestoresSupportAndExternalTorchWithoutOwnersOrDrops(TestContext c){
        try(var f=new Fixture(c,pair(),Map.of(SUPPORT.up(),Blocks.TORCH.getDefaultState()));var probe=AssemblyWriteProbe.open(c.getWorld(),f.scope(),f.origin.add(SECOND),null)){
            try{
                var failure=denied(f);c.assertTrue(probe.clearDenied()&&!probe.restoreDenied(),"exact second AIR write was actually refused once");
                c.assertTrue(probe.events().stream().anyMatch(e->e.stage().equals("RETURN")&&e.cell().equals(f.origin)&&e.requested().contains("minecraft:air")&&e.observed().contains("minecraft:air")&&Boolean.TRUE.equals(e.result())),"the first real support changed before the injected mid-commit refusal");
                f.assertOriginal();f.assertNoDuplicate();c.assertTrue(failure.getSuppressed().length==0&&!probe.truncated(),"complete quiet rollback has no hidden restoration error");
            }finally{f.dump("midcommit-external-torch",probe);}
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="assembly-unsupported-torch")
    public void anUnsupportedSelectedTorchIsRejectedBeforeAnyOwnershipMutation(TestContext c){
        var cells=new LinkedHashMap<BlockPos,BlockState>();cells.put(SUPPORT,Blocks.IRON_BLOCK.getDefaultState());cells.put(SUPPORT.up(),Blocks.TORCH.getDefaultState());
        try(var f=new Fixture(c,cells,Map.of());var probe=AssemblyWriteProbe.open(c.getWorld(),f.scope(),null,null)){
            try{
                boolean rejected=false;try{f.body=BlockBodyEntity.assembleSelection(c.getWorld(),f.source);}catch(IllegalArgumentException expected){rejected=true;}
                c.assertTrue(rejected&&probe.events().isEmpty(),"unsupported selected functionality is refused before any world write");f.assertOriginal();f.assertNoDuplicate();
            }finally{f.dump("unsupported-selected-torch",probe);}
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="assembly-denied-rollback")
    public void failedRestorationIsReportedAndCannotRetainTheTemporaryBody(TestContext c){
        try(var f=new Fixture(c,pair(),Map.of());var probe=AssemblyWriteProbe.open(c.getWorld(),f.scope(),f.origin.add(SECOND),f.origin)){
            try{
                var failure=denied(f);c.assertTrue(probe.clearDenied()&&probe.restoreDenied(),"actual second clear and first restoration were each denied once");
                c.assertTrue(failure.getSuppressed().length>0&&c.getWorld().getBlockState(f.origin).isAir(),"failed restoration remains an explicit error and real incomplete state");
                c.assertTrue(c.getWorld().getBlockState(f.origin.add(SECOND)).isOf(Blocks.GOLD_BLOCK),"independent source material is retained despite the rollback denial");f.assertNoDuplicate();
            }finally{f.dump("rollback-refused",probe);}
        }c.complete();
    }
}
