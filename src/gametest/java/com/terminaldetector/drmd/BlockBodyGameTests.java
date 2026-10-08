package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class BlockBodyGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void movementStopsAtSolidTerrain(TestContext ctx) {
        var world=ctx.getWorld();BlockPos p=ctx.getAbsolutePos(new BlockPos(2,3,2));
        world.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(p.east(2),Blocks.OBSIDIAN.getDefaultState());
        var body=BlockBodyEntity.assemble(world,p,p);
        body.physics().applyImpulse(new Vec3(20,0,0),new Vec3(0,0,0));
        body.tick(); body.tick();
        ctx.assertTrue(body.getX()>p.getX()+.6,"body advanced under impulse");
        ctx.assertTrue(body.getX()<=p.getX()+1.500001,"body did not tunnel through wall");
        ctx.assertTrue(body.physics().linearVelocity().lengthSquared()<1e-9,"inelastic contact stopped motion");
        ctx.assertTrue(world.getBlockState(p.east(2)).isOf(Blocks.OBSIDIAN),"collision preserved terrain");
        body.discard();ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cubicPalettePreservesStatesBeyondVanillaHeight(TestContext ctx) {
        var volume=new com.terminaldetector.drmd.d6.D6Volume<net.minecraft.block.BlockState>(Blocks.AIR.getDefaultState());
        volume.set(-17,100000,-1,Blocks.IRON_BLOCK.getDefaultState());
        volume.set(16,-50000,15,Blocks.GLASS.getDefaultState());
        var decoded=com.terminaldetector.drmd.world.cubic.CubicBlockCodec.readVolume(
            com.terminaldetector.drmd.world.cubic.CubicBlockCodec.writeVolume(volume),2);
        ctx.assertTrue(decoded.get(-17,100000,-1).isOf(Blocks.IRON_BLOCK),"positive Y cube survived NBT");
        ctx.assertTrue(decoded.get(16,-50000,15).isOf(Blocks.GLASS),"negative Y cube survived NBT");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void assembleSaveReloadDisassemble(TestContext ctx) {
        var world=ctx.getWorld();
        BlockPos p=ctx.getAbsolutePos(new BlockPos(2,3,2));
        world.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(p.east(),Blocks.GLASS.getDefaultState());
        var body=BlockBodyEntity.assemble(world,p,p.east());
        ctx.assertTrue(world.getBlockState(p).isAir() && world.getBlockState(p.east()).isAir(),"source blocks removed");
        ctx.assertTrue(Math.abs(body.physics().mass()-2)<1e-9,"mass comes from blocks");
        body.physics().applyImpulse(new Vec3(2,0,0),new Vec3(0,1,0));
        var tag=new NbtCompound();body.writeNbt(tag);body.discard();
        var restored=new BlockBodyEntity(ModEntities.BLOCK_BODY,world);restored.readNbt(tag);
        ctx.assertTrue(restored.cells().size()==2,"shape survived NBT");
        ctx.assertTrue(Math.abs(restored.physics().linearVelocity().x()-1)<1e-9,"velocity survived NBT");
        ctx.assertTrue(Math.abs(restored.physics().angularMomentum().z()+2)<1e-9,"spin survived NBT");
        restored.disassemble();
        ctx.assertTrue(world.getBlockState(p).isOf(Blocks.IRON_BLOCK),"iron restored at original position");
        ctx.assertTrue(world.getBlockState(p.east()).isOf(Blocks.GLASS),"glass restored at original position");
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectInventoryBeforeRemovingAnyBlocks(TestContext ctx) {
        var world=ctx.getWorld();BlockPos p=ctx.getAbsolutePos(new BlockPos(2,3,2));
        world.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());world.setBlockState(p.east(),Blocks.CHEST.getDefaultState());
        boolean rejected=false;
        try { BlockBodyEntity.assemble(world,p,p.east()); } catch(IllegalArgumentException expected) { rejected=true; }
        ctx.assertTrue(rejected,"container rejected");
        ctx.assertTrue(world.getBlockState(p).isOf(Blocks.IRON_BLOCK),"earlier cells preserved");
        ctx.assertTrue(world.getBlockEntity(p.east())!=null,"inventory block entity preserved");ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void obstructedLandingKeepsBodyIntact(TestContext ctx) {
        var world=ctx.getWorld();BlockPos p=ctx.getAbsolutePos(new BlockPos(2,3,2));
        world.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());
        var body=BlockBodyEntity.assemble(world,p,p);
        world.setBlockState(p,Blocks.OBSIDIAN.getDefaultState());
        boolean rejected=false;
        try {body.disassemble();}catch(IllegalArgumentException expected){rejected=true;}
        ctx.assertTrue(rejected && !body.isRemoved(),"body retained after obstructed landing");
        ctx.assertTrue(world.getBlockState(p).isOf(Blocks.OBSIDIAN),"terrain preserved");
        body.discard();ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void localGravityPullsBodyOntoWall(TestContext ctx) {
        var world=ctx.getWorld();BlockPos p=ctx.getAbsolutePos(new BlockPos(7,4,7));
        BlockPos wall=p.west(3);world.setBlockState(wall,Blocks.OBSIDIAN.getDefaultState());
        world.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());
        var body=BlockBodyEntity.assemble(world,p,p);body.setPhysicsGravity(true);
        UUID fieldId=new UUID(0x626f6479L,wall.asLong());
        com.terminaldetector.drmd.world.gravity.GravityFields.put(
            new com.terminaldetector.drmd.world.gravity.GravityFields.Field(fieldId,world.getRegistryKey(),wall,
                new Vec3d(-1,0,0),8,1f,com.terminaldetector.drmd.world.gravity.FieldShape.SPHERE,"wall test",true));
        double oldX=body.getX();
        try {
            for(int i=0;i<100;i++)body.tick();
            ctx.assertTrue(body.getX()<oldX-1,"local gravity moved body sideways");
            ctx.assertTrue(body.getX()>=wall.getX()+1.49,"body settled on wall face without tunnelling");
            ctx.assertTrue(Math.abs(body.physics().linearVelocity().x())<.05,"wall contact cancelled inward velocity");
        } finally {
            com.terminaldetector.drmd.world.gravity.GravityFields.remove(fieldId);body.discard();
        }
        ctx.complete();
    }
}
