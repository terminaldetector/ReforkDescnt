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
}
