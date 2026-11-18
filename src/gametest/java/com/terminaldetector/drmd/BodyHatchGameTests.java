package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Unregistered source candidate: real local geometry/USE/atomic refusal, no client proof. */
public final class BodyHatchGameTests implements FabricGameTest {
    private static final BlockPos CELL=BlockPos.ORIGIN;
    private static BlockState hatch(boolean open) {
        return Blocks.OAK_TRAPDOOR.getDefaultState().with(Properties.OPEN,open);
    }
    private static final class Fixture implements AutoCloseable {
        final TestContext c; final AiFixtureChunks chunks; final BlockBodyEntity body;
        ServerPlayerEntity player;
        Fixture(TestContext c,BlockState state,Quat q) {
            this.c=c;var at=c.getAbsolutePos(new BlockPos(8,150,8));
            chunks=new AiFixtureChunks(c.getWorld(),at,0);
            body=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(CELL,state)),
                    Vec3d.of(at),q,new Vec3d(2,.25,-1),new Vec3d(.04,.03,.02));
        }
        ServerPlayerEntity player() {
            if(player==null){player=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(player).setEnabled(false);
                player.getAbilities().flying=true;player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);}
            aim();return player;
        }
        void aim() {
            var bounds=BodyLocalHatches.collisionBounds(CELL,body.getLocalBlock(CELL));
            Vec3 size=bounds.max().minus(bounds.min());
            Vec3 normal=size.x()<.25?new Vec3(1,0,0):size.y()<.25?new Vec3(0,1,0):new Vec3(0,0,1);
            Vec3 point=bounds.centre(),eye=point.plus(normal.scaled(3.4));
            var worldEye=body.localToWorld(new Vec3d(eye.x(),eye.y(),eye.z()));
            double height=player.getEyePos().y-player.getY();player.setPosition(worldEye.subtract(0,height,0));
            var target=body.localToWorld(new Vec3d(point.x(),point.y(),point.z()));var dir=target.subtract(player.getEyePos());
            var angles=PortalTransform.vectorToYawPitch(new Vec3(dir.x,dir.y,dir.z));
            player.setYaw((float)angles.yawDegrees());player.setPitch((float)angles.pitchDegrees());
        }
        BodyBlockInteraction.Edit edit(){return new BodyBlockInteraction.Edit(body.getUuid(),body.shapeRevision(),CELL,BodyBlockInteraction.USE);}
        public void close(){if(player!=null)player.discard();body.discard();chunks.close();}
    }
    private static void identical(TestContext c,BlockBodyEntity b,UUID id,Vec3 pos,Quat q,Vec3 v,Vec3 l,double mass,String why) {
        c.assertTrue(id.equals(b.getUuid())&&pos.minus(b.massPosition()).length()<1e-12&&q.equals(b.rotation())
                &&v.minus(b.physics().linearVelocity()).length()<1e-12&&l.minus(b.physics().angularMomentum()).length()<1e-12
                &&mass==b.physicsMass(),why);
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-hatch-use")
    public void ordinaryUseOpensRotatedMovingHatchWithoutReplacingPhysics(TestContext c) {
        try(var f=new Fixture(c,hatch(false),ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.45))){
            var b=f.body;var p=f.player();var old=f.edit();var id=b.getUuid();var pos=b.massPosition();var q=b.rotation();
            var v=b.physics().linearVelocity();var l=b.physics().angularMomentum();double mass=b.physicsMass();
            c.assertTrue(b.canUseLocalBlock(CELL,p.getMainHandStack()),"ordinary client routing recognizes the actual local hatch");
            c.assertTrue(BodyBlockInteraction.receive(p,old),"normal authoritative USE reaches real hatch");
            c.assertTrue(b.getLocalBlock(CELL).get(Properties.OPEN)&&b.shapeRevision()==old.revision()+1,"actual state/journal changes once");
            identical(c,b,id,pos,q,v,l,mass,"state toggle preserves UUID, pose, velocity, momentum and material mass");
            c.assertTrue(new BlockBodyWorldAccess(b).raycastBlockBody(b.localToWorld(new Vec3d(.5,-1,.5)),
                    b.localToWorld(new Vec3d(.5,2,.5))).isEmpty(),"open rotated aperture has no invisible full cube");
            c.assertTrue(!BodyBlockInteraction.receive(p,old),"stale revision cannot toggle a second time");
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-hatch-geometry")
    public void AllFacingAndHalfStatesUseThinCollisionAndOpenPassage(TestContext c) {
        for(var facing:Direction.Type.HORIZONTAL)for(var half:BlockHalf.values()){
            var state=hatch(false).with(Properties.HORIZONTAL_FACING,facing).with(Properties.BLOCK_HALF,half);
            try(var f=new Fixture(c,state,ImmPtlQuaternions.rotationByRadians(new Vec3(2,1,3).normalized(),.8))){
                var b=f.body;var access=new BlockBodyWorldAccess(b);
                c.assertTrue(access.raycastBlockBody(b.localToWorld(new Vec3d(.5,-1,.5)),b.localToWorld(new Vec3d(.5,2,.5))).isPresent(),"closed slab ray hit "+facing+half);
                var y=half==BlockHalf.BOTTOM?.1:.9;
                var actor=OrientedBox.local(new BodyBounds(new Vec3(.45,y-.04,.45),new Vec3(.55,y+.04,.55)),b.massPosition(),b.localCentre(),b.rotation());
                c.assertTrue(!access.queryBodyCollision(actor,0).isEmpty(),"real closed collider blocks "+facing+half);
                c.assertTrue(b.setLocalBlock(CELL,state.with(Properties.OPEN,true)),"real atomic state replacement");
                c.assertTrue(access.raycastBlockBody(b.localToWorld(new Vec3d(.5,-1,.5)),b.localToWorld(new Vec3d(.5,2,.5))).isEmpty()
                        &&access.queryBodyCollision(actor,0).isEmpty(),"open ray and physical aperture agree "+facing+half);
            }
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-hatch-occupied")
    public void ClosingRefusesAnActualLooseItemThenCommitsAfterClearance(TestContext c) {
        try(var f=new Fixture(c,hatch(true),Quat.IDENTITY)){
            var b=f.body;var p=f.player();var target=b.localToWorld(new Vec3d(.5,.09,.5));
            var item=new ItemEntity(c.getWorld(),target.x,target.y,target.z,new ItemStack(Items.STICK));
            c.assertTrue(c.getWorld().spawnEntity(item),"actual loose item admitted inside the proposed closed slab");
            try{
                var id=b.getUuid();var pos=b.massPosition();var q=b.rotation();var v=b.physics().linearVelocity();var l=b.physics().angularMomentum();
                long revision=b.shapeRevision();double mass=b.physicsMass();
                c.assertTrue(!BodyBlockInteraction.receive(p,f.edit())&&b.getLocalBlock(CELL).get(Properties.OPEN)
                        &&b.shapeRevision()==revision,"collision refusal happens before state/journal mutation");
                identical(c,b,id,pos,q,v,l,mass,"refused closure changes no physical state");
                item.discard();f.aim();
                c.assertTrue(BodyBlockInteraction.receive(p,f.edit())&&!b.getLocalBlock(CELL).get(Properties.OPEN)
                        &&b.shapeRevision()==revision+1,"cleared ordinary hatch closes without a replacement body");
            }finally{item.discard();}
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-hatch-auth")
    public void WrongCellAndAdventureModeCannotOpenTheHatch(TestContext c) {
        try(var f=new Fixture(c,hatch(false),Quat.IDENTITY)){
            var p=f.player();var b=f.body;long revision=b.shapeRevision();
            c.assertTrue(!BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(b.getUuid(),revision,new BlockPos(1,0,0),BodyBlockInteraction.USE)),"forged local cell cannot replace the real ray");
            p.changeGameMode(GameMode.ADVENTURE);
            c.assertTrue(!BodyBlockInteraction.receive(p,f.edit())&&!b.getLocalBlock(CELL).get(Properties.OPEN)
                    &&b.shapeRevision()==revision,"non-edit mode preserves hatch and revision");
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-hatch-save")
    public void OpenHatchOrientationThinColliderAndMomentumSurviveEntityNbt(TestContext c) {
        try(var f=new Fixture(c,hatch(false).with(Properties.HORIZONTAL_FACING,Direction.WEST).with(Properties.BLOCK_HALF,BlockHalf.TOP),
                ImmPtlQuaternions.rotationByRadians(new Vec3(1,3,2).normalized(),.35))){
            var b=f.body;c.assertTrue(BodyBlockInteraction.receive(f.player(),f.edit()),"real USE before save");
            var tag=b.writeNbt(new NbtCompound());var state=b.getLocalBlock(CELL);var velocity=b.physics().linearVelocity();var momentum=b.physics().angularMomentum();
            long revision=b.shapeRevision();var bounds=b.localBlockBounds().get(CELL);b.discard();
            var restored=new BlockBodyEntity(ModEntities.BLOCK_BODY,c.getWorld());
            try{
                restored.readNbt(tag);
                c.assertTrue(restored.getLocalBlock(CELL).equals(state)&&restored.shapeRevision()==revision
                        &&restored.localBlockBounds().get(CELL).equals(bounds),"actual OPEN/facing/half and thin collision rebuild from saved state");
                c.assertTrue(restored.physics().linearVelocity().minus(velocity).length()<1e-12
                        &&restored.physics().angularMomentum().minus(momentum).length()<1e-12,"save/reload keeps physical momentum");
            }finally{restored.discard();}
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-hatch-water")
    public void DryWorldHatchAssemblesButWaterloggedSourceIsPreservedOnRefusal(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,150,8));
        try(var chunks=new AiFixtureChunks(c.getWorld(),at,0)){
            c.assertTrue(c.getWorld().getBlockState(at).isAir(),"fixture needs actual AIR");
            var wet=hatch(false).with(Properties.WATERLOGGED,true);BlockBodyEntity b=null;
            try{
                c.getWorld().setBlockState(at,wet,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                boolean rejected=false;try{BlockBodyEntity.assembleSelection(c.getWorld(),Map.of(at,wet));}catch(IllegalArgumentException expected){rejected=true;}
                c.assertTrue(rejected&&c.getWorld().getBlockState(at).equals(wet),"unsupported contained water is never silently removed");
                var dry=hatch(false).with(Properties.HORIZONTAL_FACING,Direction.EAST);
                c.getWorld().setBlockState(at,dry,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                b=BlockBodyEntity.assembleSelection(c.getWorld(),Map.of(at,dry));
                c.assertTrue(c.getWorld().getBlockState(at).isAir()&&b.getLocalBlock(CELL).equals(dry),"actual dry source transfers state into one physical local grid");
            }finally{if(b!=null)b.discard();c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);}
        }c.complete();
    }
}
