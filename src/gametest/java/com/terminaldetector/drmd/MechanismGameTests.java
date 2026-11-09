package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Generic blocks/assembly and the real joint world phase, without SkyUfo or a turret kit. */
public final class MechanismGameTests implements FabricGameTest {
    private static final BlockPos DRIVE=new BlockPos(-1,0,0);
    private BlockState bearing(Direction direction){return ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,direction);}
    private BlockState drive(Direction direction,int power){return ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,direction).with(Properties.POWER,power);}
    private Vec3d origin(TestContext c){var p=c.getAbsolutePos(new BlockPos(8,150,8));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)c.getWorld().getChunk((p.getX()>>4)+x,(p.getZ()>>4)+z);return Vec3d.of(p);}
    private record Pair(TestContext c,BlockBodyEntity a,BlockBodyEntity b) implements AutoCloseable {
        BodyJointRegistry registry(){return BodyJointRegistry.get(c.getWorld());}
        BodyJointLink link(){return registry().forBody(a.getUuid()).getFirst();}
        void step(){a.tick();b.tick();BodyContactSystem.solve(c.getWorld());}
        public void close(){registry().removeBody(a.getUuid());registry().removeBody(b.getUuid());a.discard();b.discard();}
    }
    private Pair pair(TestContext c,Quat rotation){
        var at=origin(c);var offset=rotation.rotate(new Vec3(1,0,0));
        var a=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,bearing(Direction.EAST)),new BlockBodyEntity.Cell(DRIVE,drive(Direction.EAST,15))),at,rotation,Vec3d.ZERO,Vec3d.ZERO);
        var b=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,bearing(Direction.WEST)),new BlockBodyEntity.Cell(new BlockPos(1,0,0),Blocks.IRON_BLOCK.getDefaultState())),at.add(offset.x(),offset.y(),offset.z()),rotation,Vec3d.ZERO,Vec3d.ZERO);
        a.setPhysicsGravity(false);b.setPhysicsGravity(false);return new Pair(c,a,b);
    }
    private boolean connect(Pair f){return MechanicalBearingSystem.connect(f.c.getWorld(),f.a,BlockPos.ORIGIN,f.b,BlockPos.ORIGIN);}
    private Vec3d anchor(BlockBodyEntity body,BodyJointLink.Endpoint endpoint){return body.localToWorld(new Vec3d(endpoint.point().x(),endpoint.point().y(),endpoint.point().z()));}
    private ServerPlayerEntity player(TestContext c,Vec3d at){var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.setPosition(at);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));return p;}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms",tickLimit=80)
    public void separateBlocksConnectArbitraryBodiesWithoutPoseOrMomentumReset(TestContext c){
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),1.2);var f=pair(c,q);
        try{
            f.a.physics().withLinearVelocity(new Vec3(2,1,-.5)).withAngularVelocity(new Vec3(.1,.2,.3));
            var pa=f.a.getPos();var pb=f.b.getPos();var va=f.a.physics().linearVelocity();var spin=f.a.physics().angularMomentum();
            c.assertTrue(connect(f),"separate material bearing blocks connect on ordinary BlockBody instances");var link=f.link();
            c.assertTrue(link.blockDriven()&&link.collideConnected()&&link.speed()==.6&&link.motorForce()==40,"one separate drive supplies bounded speed/torque through the existing registry");
            c.assertTrue(pa.equals(f.a.getPos())&&pb.equals(f.b.getPos())&&va.equals(f.a.physics().linearVelocity())&&spin.equals(f.a.physics().angularMomentum()),"assembly cannot snap poses or reset world motion");
            c.assertTrue(!connect(f)&&f.registry().forBody(f.a.getUuid()).size()==1,"occupied bearing cannot receive a duplicate mechanical attachment");
        }finally{f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms",tickLimit=80)
    public void rolledMechanismActuallyRotatesInTheExistingPhysicalWorldPhase(TestContext c){
        var f=pair(c,ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),1.1));c.assertTrue(connect(f),"aligned rolled bearing accepted");
        c.waitAndRun(1,()->{try{
            var before=f.b.rotation();for(int i=0;i<40;i++)f.step();var link=f.link();
            c.assertTrue(Math.abs(ImmPtlQuaternions.dot(before,f.b.rotation()))<.995,"real child orientation changes under motor impulse");
            c.assertTrue(anchor(f.a,link.a()).distanceTo(anchor(f.b,link.b()))<.02,"rotating material contact points remain joined");
            c.assertTrue(BodyJointSystem.stats(c.getWorld()).paused()==0&&f.a.physics().angularVelocity().length()>.01,"reaction rotates the free parent; no hidden static carrier or pose controller");
        }finally{f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms",tickLimit=80)
    public void destroyedDriveCoastsAndReplacementBearingBreaksThePhysicalLink(TestContext c){
        var f=pair(c,Quat.IDENTITY);c.assertTrue(connect(f),"initial bearing connects");
        c.waitAndRun(1,()->{try{
            for(int i=0;i<10;i++)f.step();var id=f.link().id();
            c.assertTrue(f.a.setLocalBlock(DRIVE,Blocks.AIR.getDefaultState()),"actual drive material destroyed");f.step();
            var link=f.registry().find(id).orElseThrow();c.assertTrue(link.blockDriven()&&link.motorForce()==0&&link.speed()==0,"drive loss retires power without replacing passive attachment");
            c.assertTrue(f.b.physics().angularVelocity().length()>.01,"drive destruction does not erase physical momentum");
            c.assertTrue(f.b.setLocalBlock(BlockPos.ORIGIN,Blocks.STONE.getDefaultState()),"bearing replaced with ordinary material");f.step();
            c.assertTrue(f.registry().find(id).isEmpty()&&f.registry().collisionAllowed(f.a.getUuid(),f.b.getUuid()),"solid replacement cannot retain a magical bearing or collision exclusion");
        }finally{f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms")
    public void wrenchSelectsMaterialBearingsReversesAndDisconnectsWithoutUfoControl(TestContext c){
        var f=pair(c,Quat.IDENTITY);var p=player(c,f.a.localToWorld(new Vec3d(.5,-1,2)));
        try{
            c.assertTrue(f.a.useLocalBlock(p,BlockPos.ORIGIN)&&f.b.useLocalBlock(p,BlockPos.ORIGIN),"actual ordinary-body use adapter accepts two keyed player clicks");
            var id=f.link().id();p.setSneaking(true);c.assertTrue(f.a.useLocalBlock(p,DRIVE),"sneak-key changes the independent drive direction");
            c.assertTrue(f.registry().find(id).orElseThrow().speed()==-.6,"reverse updates the same persistent physical motor");
            var pose=f.b.getPos();var velocity=f.b.physics().linearVelocity();c.assertTrue(f.b.useLocalBlock(p,BlockPos.ORIGIN),"sneak-key releases actual bearing");
            c.assertTrue(f.registry().find(id).isEmpty()&&pose.equals(f.b.getPos())&&velocity.equals(f.b.physics().linearVelocity()),"disconnect preserves motion and restores ordinary independent bodies");
        }finally{p.discard();f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms")
    public void staleMaterialSelectionAndUnauthorizedInventoryUseCannotCreateJoints(TestContext c){
        var f=pair(c,Quat.IDENTITY);var p=player(c,f.a.localToWorld(new Vec3d(.5,-1,2)));
        try{
            c.assertTrue(f.a.useLocalBlock(p,BlockPos.ORIGIN),"first material selection");
            c.assertTrue(f.a.setLocalBlock(DRIVE,drive(Direction.EAST,10)),"intervening actual local edit changes revision");
            c.assertTrue(!f.b.useLocalBlock(p,BlockPos.ORIGIN)&&f.registry().forBody(f.a.getUuid()).isEmpty(),"stale first click cannot create a link on changed material");
            p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);c.assertTrue(!f.a.useLocalBlock(p,DRIVE),"empty hand cannot silently change drive state");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));p.changeGameMode(GameMode.SPECTATOR);
            c.assertTrue(!f.a.useLocalBlock(p,BlockPos.ORIGIN),"spectator cannot claim a mechanism");
        }finally{p.discard();f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms")
    public void separateSourcesCannotMultiplyTorqueAndOffDriveHasNoBrakeBudget(TestContext c){
        var f=pair(c,Quat.IDENTITY);try{
            c.assertTrue(connect(f),"initial drive links");var id=f.link().id();
            c.assertTrue(f.b.setLocalBlock(new BlockPos(1,0,0),drive(Direction.WEST,15)),"second drive occupies its own material block");
            var l=MechanicalBearingSystem.refresh(c.getWorld(),f.link(),f.a,f.b);c.assertTrue(l.motorForce()==0&&l.speed()==0,"ambiguous competing sources coast instead of adding power");
            c.assertTrue(f.b.setLocalBlock(new BlockPos(1,0,0),Blocks.IRON_BLOCK.getDefaultState()),"second source removed");
            c.assertTrue(f.a.setLocalBlock(DRIVE,drive(Direction.EAST,0)),"explicit off state");
            l=MechanicalBearingSystem.refresh(c.getWorld(),f.registry().find(id).orElseThrow(),f.a,f.b);c.assertTrue(l.motorForce()==0&&l.speed()==0,"off is genuinely unpowered, not an unlimited brake");
        }finally{f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms")
    public void alignmentAndCanonicalAuthorityRejectFakeOrDistantAssembly(TestContext c){
        var f=pair(c,Quat.IDENTITY);try{
            c.assertTrue(!MechanicalBearingSystem.connect(c.getWorld(),f.a,BlockPos.ORIGIN,f.a,BlockPos.ORIGIN),"one grid cannot hinge to itself");
            f.b.setPosition(f.b.getPos().add(.1,0,0));
            c.assertTrue(!connect(f)&&f.registry().forBody(f.a.getUuid()).isEmpty(),"distant anchors cannot be snapped together");
            f.b.setPosition(f.b.getPos().add(-.1,0,0));f.b.setLocalBlock(BlockPos.ORIGIN,bearing(Direction.UP));
            c.assertTrue(!connect(f),"orthogonal bearing faces cannot form an invented axis");
        }finally{f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms")
    public void registryRoundTripPreservesBlockDrivenFlagAndLegacyJointsRemainUnmanaged(TestContext c){
        var f=pair(c,Quat.IDENTITY);try{
            c.assertTrue(connect(f),"physical joint exists");var link=f.link();
            var saved=f.registry().writeNbt(new net.minecraft.nbt.NbtCompound(),c.getWorld().getRegistryManager());
            var restored=BodyJointRegistry.fromNbt(saved,c.getWorld().getRegistryManager());
            c.assertTrue(restored.find(link.id()).orElseThrow().equals(link),"actual registry codec retains identity, driver ownership and force");
            var old=link.write();old.remove("blockDriven");c.assertTrue(!BodyJointLink.read(old).blockDriven(),"operator/UFO legacy data cannot accidentally acquire generic block control");
            f.registry().configure(link.id(),-.2,20,100,200);c.assertTrue(f.link().blockDriven(),"configuration preserves the controller marker");
        }finally{f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms")
    public void worldWrenchAssemblesActualSelectedBlocksAndRejectsMixedBlockEntitySelectionAtomically(TestContext c){
        var at=BlockPos.ofFloored(origin(c));var w=c.getWorld();var p=player(c,Vec3d.of(at).add(.5,-1,2));
        var before=new HashSet<>(BodySpatialIndex.loaded(w));BlockBodyEntity made=null;
        try{
            w.setBlockState(at,bearing(Direction.EAST),2);w.setBlockState(at.add(1,0,0),drive(Direction.EAST,0),2);
            c.assertTrue(MechanicalBearingSystem.useWorld(p,at)&&MechanicalBearingSystem.useWorld(p,at.add(1,0,0)),"player tool selects and assembles real world material without an operator command");
            made=BodySpatialIndex.loaded(w).stream().filter(b->!before.contains(b)).findFirst().orElseThrow();
            c.assertTrue(w.getBlockState(at).isAir()&&w.getBlockState(at.add(1,0,0)).isAir()&&made.cells().size()==2,"world material moves into one actual compound body");
            c.assertTrue(made.getLocalBlock(BlockPos.ORIGIN).equals(bearing(Direction.EAST)),"local bearing orientation survives assembly");
            var bad=at.add(0,0,3);w.setBlockState(bad,Blocks.IRON_BLOCK.getDefaultState(),2);w.setBlockState(bad.add(1,0,0),Blocks.CHEST.getDefaultState(),2);
            p.setPosition(Vec3d.of(bad).add(.5,-1,2));c.assertTrue(MechanicalBearingSystem.useWorld(p,bad)&&!MechanicalBearingSystem.useWorld(p,bad.add(1,0,0)),"mixed block-entity selection is rejected before transformation");
            c.assertTrue(w.getBlockState(bad).isOf(Blocks.IRON_BLOCK)&&w.getBlockEntity(bad.add(1,0,0))!=null,"failed selection preserves every original world block and its entity");
        }finally{
            if(made!=null)made.discard();for(var cell:List.of(at,at.add(1,0,0),at.add(0,0,3),at.add(1,0,3)))w.setBlockState(cell,Blocks.AIR.getDefaultState(),2);p.discard();
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanisms",tickLimit=80)
    public void independentlyBuiltOrthogonalAxesFormOneReusableMovingMechanism(TestContext c){
        var f=pair(c,Quat.IDENTITY);var w=c.getWorld();
        f.b.setLocalBlock(new BlockPos(1,0,0),bearing(Direction.UP));f.b.setLocalBlock(new BlockPos(1,-1,0),drive(Direction.UP,10));
        var child=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,bearing(Direction.DOWN)),new BlockBodyEntity.Cell(new BlockPos(0,1,0),ModWorldBlocks.SHIP_LASER.getDefaultState())),f.b.localToWorld(new Vec3d(1,1,0)));child.setPhysicsGravity(false);
        c.assertTrue(connect(f)&&MechanicalBearingSystem.connect(w,f.b,new BlockPos(1,0,0),child,BlockPos.ORIGIN),"player-chosen orthogonal axes connect independent ordinary grids");
        c.waitAndRun(1,()->{try{
            var before=child.rotation();for(int i=0;i<25;i++){f.a.tick();f.b.tick();child.tick();BodyContactSystem.solve(w);}
            var links=f.registry().forBody(f.b.getUuid());c.assertTrue(links.size()==2&&links.stream().allMatch(BodyJointLink::blockDriven),"both axes retain distinct physical material links");
            c.assertTrue(BodyJointSystem.stats(w).islands()==1&&BodyJointSystem.stats(w).paused()==0,"three independently built bodies share the existing solver island");
            c.assertTrue(Math.abs(ImmPtlQuaternions.dot(before,child.rotation()))<.999,"weapon-carrying final component moves through actual joints");
            for(var link:links){var a=link.a().body().equals(f.a.getUuid())?f.a:f.b;var b=link.b().body().equals(child.getUuid())?child:f.b;c.assertTrue(anchor(a,link.a()).distanceTo(anchor(b,link.b()))<.025,"both orthogonal anchors stay attached");}
        }finally{f.registry().removeBody(child.getUuid());child.discard();f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mechanism-placement")
    public void survivalPlacementPacketOrientsSeparateDriveInTheActualHitLocalFrame(TestContext c){
        var f=pair(c,ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),.7));
        var p=player(c,f.a.localToWorld(new Vec3d(.5,-1,2.8)));p.changeGameMode(GameMode.SURVIVAL);
        try{
            var delta=f.a.localToWorld(new Vec3d(.5,.5,.5)).subtract(p.getEyePos()).normalize();
            var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(delta.x,delta.y,delta.z));p.setYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());
            p.setHeadYaw((float)angles.yawDegrees());
            c.assertTrue(p.getRotationVec(1).dotProduct(delta)>.999,"mock head yaw and body yaw both face the actual material target");
            var target=BodyBlockInteraction.target(p,p.getEyePos(),p.getRotationVec(1),p.getBlockInteractionRange()).orElseThrow();
            c.assertTrue(target.body()==f.a,"authoritative ray selects actual rolled carrier material");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.ROTARY_DRIVE,2));p.setSneaking(true);
            var hit=target.hit();var edit=new BodyBlockInteraction.Edit(f.a.getUuid(),hit.revision(),hit.localBlock(),BodyBlockInteraction.PLACE);
            c.assertTrue(BodyBlockInteraction.receive(p,edit),"normal validated placement packet installs a real independent drive block: hit="+hit+" current="+BodyBlockInteraction.target(p,p.getEyePos(),p.getRotationVec(1),p.getBlockInteractionRange())+" otherEntities="+BodySpatialIndex.otherEntities(c.getWorld(),f.a,f.a.getBoundingBox().expand(4),e->e.isAlive())+" player="+p.getBoundingBox());
            var placed=f.a.getLocalBlock(hit.localBlock().offset(hit.localFace()));
            c.assertTrue(placed.isOf(ModWorldBlocks.ROTARY_DRIVE)&&placed.get(MechanicalBearingBlock.FACING)==hit.localFace(),"drive axis follows local surface rather than global UP");
            c.assertTrue(p.getMainHandStack().getCount()==1,"Survival construction consumes exactly one held component");
            c.assertTrue(!BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(f.a.getUuid(),hit.revision(),hit.localBlock(),BodyBlockInteraction.USE)),"stale revision cannot use the changed mechanism through a forged packet");
        }finally{p.discard();f.close();}c.complete();
    }
}
