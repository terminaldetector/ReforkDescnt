package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.block.*;
import net.minecraft.util.math.*;

public final class CollapseGameTests implements FabricGameTest {
    @GameTest(templateName="drmd:collapse_lab",tickLimit=400)
    public void actualRocketCutsColumnAndGravityTipsItOntoFloor(TestContext ctx) {
        var world=ctx.getWorld();var root=ctx.getAbsolutePos(new BlockPos(12,2,12));
        for(BlockPos p:BlockPos.iterate(root.add(-7,-1,-7),root.add(7,12,10)))world.setBlockState(p,
            p.getY()==root.getY()-1?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState());
        for(int y=0;y<7;y++)world.setBlockState(root.up(y),Blocks.BRICKS.getDefaultState());
        var rocket=new ProjectileEntity(ModEntities.PROJECTILE,world);
        rocket.setDamageClass(DamageClass.EXPLOSIVE);rocket.setDirectDamage(100);rocket.setWorldBlast(true);
        rocket.setPosition(Vec3d.ofCenter(root).add(0,0,-5));rocket.setVelocity(0,0,1);world.spawnEntity(rocket);
        for(int i=0;i<7 && rocket.isAlive();i++)rocket.tick();
        var bodies=world.getEntitiesByClass(BlockBodyEntity.class,new Box(root).expand(18),e->e.physicsGravity());
        ctx.assertTrue(bodies.size()==1 && world.getBlockState(root.up()).isAir(),"real rocket detached the column");
        var body=bodies.getFirst();double oldY=body.getY();
        for(int i=0;i<120;i++)body.tick();
        ctx.assertTrue(body.getY()<oldY-.5,"centre fell under gravity");
        ctx.assertTrue(Math.abs(body.rotation().x())>.35,"column tipped, not just translated");
        ctx.assertTrue(body.physics().linearVelocity().length()<20.001,"bounded contact response");
        var saved=new net.minecraft.nbt.NbtCompound();body.writeNbt(saved);
        var copy=new BlockBodyEntity(ModEntities.BLOCK_BODY,world);copy.readNbt(saved);
        ctx.assertTrue(copy.physicsGravity() && copy.cells().size()==6,"gravity and geometry survive save");
        body.discard();copy.discard();ctx.complete();
    }
    @GameTest(templateName="drmd:collapse_lab")
    public void treeKeepsCanopyButAttachedStructureIsRejected(TestContext ctx) {
        var world=ctx.getWorld();var root=ctx.getAbsolutePos(new BlockPos(12,3,12));
        for(BlockPos p:BlockPos.iterate(root.add(-3,0,-3),root.add(3,9,3)))world.setBlockState(p,Blocks.AIR.getDefaultState());
        for(int y=0;y<5;y++)world.setBlockState(root.up(y),Blocks.OAK_LOG.getDefaultState());
        world.setBlockState(root.up(4).east(),Blocks.OAK_LEAVES.getDefaultState());
        var tree=StructureCollapse.rocketCut(world,root,Vec3d.ofCenter(root),new Vec3d(0,0,1),80);
        ctx.assertTrue(tree!=null && tree.cells().size()==5 && tree.physicsGravity(),"trunk and canopy become one physical tree");tree.discard();
        for(int y=0;y<5;y++)world.setBlockState(root.up(y),Blocks.BRICKS.getDefaultState());
        world.setBlockState(root.up(2).west(),Blocks.CHEST.getDefaultState());
        ctx.assertTrue(StructureCollapse.rocketCut(world,root,Vec3d.ofCenter(root),new Vec3d(1,0,0),80)==null,"attached structure rejected");
        ctx.assertTrue(world.getBlockState(root.up()).isOf(Blocks.BRICKS) && world.getBlockState(root.up(2).west()).isOf(Blocks.CHEST),"reject before mutation");ctx.complete();
    }
}
