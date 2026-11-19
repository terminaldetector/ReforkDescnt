package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.micro.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import java.util.*;

public class MicroDamageGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void vanillaExplosionRefusesUnknownSamplingBeforeCollectingOrWriting(TestContext ctx) {
        var world=ctx.getWorld();var at=ctx.getAbsolutePos(new BlockPos(4,5,4));
        var far=new BlockPos(((at.getX()+8_000_000)>>4)*16+8,at.getY(),((at.getZ())>>4)*16+8);
        ctx.assertTrue(!world.isChunkLoaded(far),"unknown explosion control starts absent");
        var c=Vec3d.ofCenter(far);
        var explosion=new Explosion(world,null,c.x,c.y,c.z,2,false,Explosion.DestructionType.DESTROY);
        explosion.collectBlocksAndDamageEntities();explosion.affectWorld(false);
        ctx.assertTrue(explosion.getAffectedBlocks().isEmpty()&&!world.isChunkLoaded(far),"vanilla block/fluid sampling cannot materialize unknown storage");
        var selected=new Explosion(world,null,c.x,c.y,c.z,2,false,Explosion.DestructionType.DESTROY,new ArrayList<>(List.of(far)));
        selected.affectWorld(false);
        ctx.assertTrue(selected.getAffectedBlocks().isEmpty()&&!world.isChunkLoaded(far),"preselected targets cannot bypass live availability");
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void knownVanillaExplosionRetainsBedrockShieldingAndOrdinaryDestruction(TestContext ctx) {
        var world=ctx.getWorld();var at=ctx.getAbsolutePos(new BlockPos(4,6,4));
        for(var p:BlockPos.iterate(at.add(-3,-3,-3),at.add(3,3,3)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(var p:BlockPos.iterate(at.add(1,-3,-3),at.add(1,3,3)))world.setBlockState(p,Blocks.BEDROCK.getDefaultState(),2);
        var exposed=at.west();var shielded=at.east(2);
        world.setBlockState(exposed,Blocks.DIRT.getDefaultState(),2);world.setBlockState(shielded,Blocks.DIRT.getDefaultState(),2);
        var c=Vec3d.ofCenter(at);var explosion=new Explosion(world,null,c.x,c.y,c.z,2,false,Explosion.DestructionType.DESTROY);
        ctx.assertTrue(com.terminaldetector.drmd.world.access.ExplosionWorldAccess.canCollect(world,explosion),"positive fixture's actual sample envelope is FULL");
        explosion.collectBlocksAndDamageEntities();
        ctx.assertTrue(explosion.getAffectedBlocks().contains(exposed)&&!explosion.getAffectedBlocks().contains(shielded),"unchanged vanilla rays select exposed material and stop at bedrock");
        explosion.affectWorld(false);
        ctx.assertTrue(!world.getBlockState(exposed).isOf(Blocks.DIRT)&&world.getBlockState(shielded).isOf(Blocks.DIRT)
            &&world.getBlockState(at.east()).isOf(Blocks.BEDROCK),"loaded destruction works while shielding and unbreakable material remain");
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unknownDamageAndBlastQueriesNeverMaterializeRemoteColumns(TestContext ctx) {
        var world=ctx.getWorld();var base=ctx.getAbsolutePos(new BlockPos(4,4,4));
        var far=new BlockPos(((base.getX()+8_000_000)>>4)*16+8,base.getY(),((base.getZ())>>4)*16+8);
        var hit=far;var fracture=far.east(32);var blast=far.east(64);var replace=far.east(96);var restore=far.east(128);
        for(var p:List.of(hit,fracture,blast,replace,restore))ctx.assertTrue(!world.isChunkLoaded(p),"each damage control begins in an unavailable column");
        ctx.assertTrue(!BlockDamage.hit(world,hit,Vec3d.ofCenter(hit),12),"unknown impact cannot edit terrain");
        ctx.assertTrue(!BlockDamage.fractureExplosion(world,fracture,Vec3d.ofCenter(fracture.east(3)),2),"unknown explosion rim cannot be read as air");
        BlockDamage.blast(world,Vec3d.ofCenter(blast),1,12);
        ctx.assertTrue(CarvedBlock.replace(world,replace,Blocks.STONE.getDefaultState(),MicroGrid.FULL)==null,"unknown carve cannot create a block entity");
        CarvedBlock.restore(world,restore);
        for(var p:List.of(hit,fracture,blast,replace,restore))ctx.assertTrue(!world.isChunkLoaded(p),"ordinary damage APIs never acquire unknown columns");
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void loadedFluidAndWorkerDamagePreserveActualState(TestContext ctx) {
        var world=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(4,4,4));
        world.setBlockState(p,Blocks.STONE.getDefaultState());
        boolean refused=java.util.concurrent.CompletableFuture.supplyAsync(()->
            !BlockDamage.hit(world,p,Vec3d.ofCenter(p),12)
            && !BlockDamage.fractureExplosion(world,p,Vec3d.ofCenter(p.east(3)),2)
            && !BlockDamage.isDamageable(world,p,Blocks.STONE.getDefaultState())).join();
        ctx.assertTrue(refused && world.getBlockState(p).isOf(Blocks.STONE),"worker-thread callbacks cannot read or carve owned storage");
        world.setBlockState(p,Blocks.WATER.getDefaultState());
        ctx.assertTrue(!BlockDamage.hit(world,p,Vec3d.ofCenter(p),40)
            && !BlockDamage.fractureExplosion(world,p,Vec3d.ofCenter(p.east(3)),2)
            && world.getBlockState(p).isOf(Blocks.WATER),"known fluid stays a fluid instead of becoming a carved solid");
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explosionFracturesRimButDestroysCentre(TestContext ctx) {
        var world=ctx.getWorld(); var p=ctx.getAbsolutePos(new BlockPos(1,3,2));var core=p.east(3);
        world.setBlockState(p,Blocks.STONE.getDefaultState());world.setBlockState(core,Blocks.STONE.getDefaultState());
        Vec3d c=Vec3d.ofCenter(core);
        var blast=new Explosion(world,null,c.x,c.y,c.z,2,false,Explosion.DestructionType.DESTROY,
            new ArrayList<>(List.of(p,core)));
        blast.affectWorld(false);
        ctx.assertTrue(world.getBlockEntity(p) instanceof CarvedBlockEntity,"rim is fractured by the vanilla explosion path");
        var carved=(CarvedBlockEntity)world.getBlockEntity(p);
        ctx.assertTrue(MicroGrid.count(carved.mask())>0 && MicroGrid.count(carved.mask())<64,"partial quarter-cell damage");
        ctx.assertTrue(carved.source().isOf(Blocks.STONE),"original material retained");
        ctx.assertTrue(world.getBlockState(core).isAir(),"blast centre uses normal destruction");ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void keepExplosionNeverCarves(TestContext ctx) {
        var world=ctx.getWorld(); var p=ctx.getAbsolutePos(new BlockPos(1,3,2));var c=Vec3d.ofCenter(p.east(3));
        world.setBlockState(p,Blocks.STONE.getDefaultState());
        new Explosion(world,null,c.x,c.y,c.z,2,false,Explosion.DestructionType.KEEP,
            new ArrayList<>(List.of(p))).affectWorld(false);
        ctx.assertTrue(world.getBlockState(p).isOf(Blocks.STONE),"KEEP explosions preserve terrain");ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hitsPreserveUnsupportedInventory(TestContext ctx) {
        var world=ctx.getWorld(); var p=ctx.getAbsolutePos(new BlockPos(2,3,2));
        world.setBlockState(p,Blocks.CHEST.getDefaultState());
        var chest=(ChestBlockEntity)world.getBlockEntity(p);chest.setStack(0,new ItemStack(Items.DIAMOND,7));
        BlockDamage.hit(world,p,Vec3d.ofCenter(p),40);
        ctx.assertTrue(world.getBlockEntity(p)==chest && chest.getStack(0).getCount()==7,"generic carving cannot erase an inventory");ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void carvingUsesActualMaskAndRestoreClearsHistory(TestContext ctx) {
        var world=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(2,3,2));
        world.setBlockState(p,Blocks.STONE.getDefaultState());
        long before=MicroGrid.carve(MicroGrid.FULL,.95,.5,.5,.4);
        var be=CarvedBlock.replace(world,p,Blocks.STONE.getDefaultState(),before);
        MicroStore.get(world).clear(p); // independent BE/tunnel save without the old side map
        BlockDamage.hit(world,p,Vec3d.ofCenter(p).add(-.45,0,0),12);
        var after=(CarvedBlockEntity)world.getBlockEntity(p);
        ctx.assertTrue((after.mask() & ~before)==0,"subsequent hit must not refill cells absent from BE");
        ctx.assertTrue(MicroGrid.count(after.mask())<MicroGrid.count(before),"second hit deepens damage");
        CarvedBlock.restore(world,p);
        ctx.assertTrue(world.getBlockState(p).isOf(Blocks.STONE) && !MicroStore.get(world).isDamaged(p),"restoration clears stale damage");ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "loaded-fire-ignition")
    public void blastIgnitionCannotCreateUnknownColumnOrFireFocus(TestContext context) {
        var world = context.getWorld();
        var unknown = new BlockPos(1049376, 90, 1983920);
        var chunks = world.getChunkManager();
        int cx = unknown.getX() >> 4;
        int cz = unknown.getZ() >> 4;
        int count = com.terminaldetector.drmd.world.fire.FireSystem.focusCount();
        context.assertTrue(chunks.getWorldChunk(cx, cz) == null, "fire fixture begins with a genuinely unloaded column");
        com.terminaldetector.drmd.world.fire.FireSystem.ignite(world, unknown, 3);
        com.terminaldetector.drmd.world.fire.FireSystem.igniteBlast(world, unknown, 12, 3);
        context.assertTrue(chunks.getWorldChunk(cx, cz) == null
            && com.terminaldetector.drmd.world.fire.FireSystem.focusCount() == count,
            "direct and blast ignition do not materialize unknown terrain or record a ghost fire focus");
        context.complete();
    }
}
