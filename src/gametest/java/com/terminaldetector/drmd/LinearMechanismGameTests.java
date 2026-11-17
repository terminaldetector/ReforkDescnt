package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.util.*;

/** Player-built linear components exercise the existing world solver; no UFO kit or pose driver. */
public final class LinearMechanismGameTests implements FabricGameTest {
    private static final BlockPos DRIVE=new BlockPos(-1,0,0);
    private static BlockState bearing(Direction d){return ModWorldBlocks.SLIDING_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,d);}
    private static BlockState drive(Direction d,int power){return ModWorldBlocks.LINEAR_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,d).with(Properties.POWER,power);}
    private record Pair(TestContext c,BlockBodyEntity a,BlockBodyEntity b) implements AutoCloseable {
        BodyJointRegistry registry(){return BodyJointRegistry.get(c.getWorld());}
        BodyJointLink link(){return registry().forBody(a.getUuid()).getFirst();}
        void step(){a.tick();b.tick();BodyContactSystem.solve(c.getWorld());}
        public void close(){registry().removeBody(a.getUuid());registry().removeBody(b.getUuid());a.discard();b.discard();}
    }
    private Pair pair(TestContext c,Quat rotation){
        var p=c.getAbsolutePos(new BlockPos(8,150,8));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)c.getWorld().getChunk((p.getX()>>4)+x,(p.getZ()>>4)+z);
        var delta=rotation.rotate(new Vec3(1,0,0));var at=Vec3d.of(p);
        var a=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,bearing(Direction.EAST)),new BlockBodyEntity.Cell(DRIVE,drive(Direction.EAST,15))),at,rotation,Vec3d.ZERO,Vec3d.ZERO);
        var b=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,bearing(Direction.WEST)),new BlockBodyEntity.Cell(new BlockPos(1,0,0),Blocks.IRON_BLOCK.getDefaultState())),at.add(delta.x(),delta.y(),delta.z()),rotation,Vec3d.ZERO,Vec3d.ZERO);
        a.setPhysicsGravity(false);b.setPhysicsGravity(false);return new Pair(c,a,b);
    }
    private boolean connect(Pair f){return MechanicalBearingSystem.connect(f.c.getWorld(),f.a,BlockPos.ORIGIN,f.b,BlockPos.ORIGIN);}
    private Vec3 axis(Pair f){return f.a.rotation().rotate(f.link().a().frame().rotate(new Vec3(1,0,0)));}
    private Vec3 separation(Pair f){
        var l=f.link();var a=f.a.localToWorld(new Vec3d(l.a().point().x(),l.a().point().y(),l.a().point().z()));
        var b=f.b.localToWorld(new Vec3d(l.b().point().x(),l.b().point().y(),l.b().point().z()));return new Vec3(b.x-a.x,b.y-a.y,b.z-a.z);
    }
    private double offset(Pair f){return separation(f).dot(axis(f));}
    private ServerPlayerEntity player(Pair f){var p=f.c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.setPosition(f.a.localToWorld(new Vec3d(.5,-1,2)));p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));return p;}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="linear-admission",tickLimit=80)
    public void actualWrenchCreatesPersistentBoundedSliderWithoutResettingMotion(TestContext c){
        var f=pair(c,ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.8));var p=player(f);
        try{
            f.a.physics().withLinearVelocity(new Vec3(.2,.1,.3));f.b.physics().withLinearVelocity(new Vec3(.2,.1,.3));
            var pa=f.a.getPos();var pb=f.b.getPos();var va=f.a.physics().linearVelocity();var vb=f.b.physics().linearVelocity();
            c.assertTrue(f.a.useLocalBlock(p,BlockPos.ORIGIN)&&f.b.useLocalBlock(p,BlockPos.ORIGIN),"two actual wrench uses connect opposing linear material bearings");
            var l=f.link();c.assertTrue(l.blockDriven()&&l.kind()==BodyJointConstraint.Kind.SLIDER&&l.lower()==0&&l.upper()==2&&l.speed()==.6&&l.motorForce()==40,"real separate drive configures finite slider limits and force");
            c.assertTrue(pa.equals(f.a.getPos())&&pb.equals(f.b.getPos())&&va.equals(f.a.physics().linearVelocity())&&vb.equals(f.b.physics().linearVelocity()),"assembly preserves both poses and world velocities");
            var saved=f.registry().writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());var restored=BodyJointRegistry.fromNbt(saved,c.getWorld().getRegistryManager());
            c.assertTrue(restored.find(l.id()).orElseThrow().equals(l),"actual registry codec preserves slider axis, limits, identity and powered state");
            c.assertTrue(!connect(f),"occupied linear stage cannot create duplicate constraints");
        }finally{p.discard();f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="linear-physical-motion",tickLimit=80)
    public void rolledStagePhysicallyExtendsStopsAndRetractsAlongItsOwnAxis(TestContext c){
        var f=pair(c,ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),1.1));c.assertTrue(connect(f),"rolled linear stage admitted");
        c.waitAndRun(2,()->{try{
            var before=f.a.getPos();double max=0,min=0;
            for(int i=0;i<180;i++){
                f.step();double s=offset(f);max=Math.max(max,s);min=Math.min(min,s);
                c.assertTrue(s>=-.02&&s<=2.02,"actual solver enforces finite stroke during every extension step: "+s);
                c.assertTrue(separation(f).minus(axis(f).scaled(s)).length()<.03,"linear stage cannot drift across its guide axis");
            }
            c.assertTrue(max>1.8&&f.a.getPos().distanceTo(before)>.2,"finite motor physically extends and reacts against a free parent, without a hidden static body");
            c.assertTrue(Math.abs(ImmPtlQuaternions.dot(f.a.rotation(),f.b.rotation()))>.999,"slider locks relative rotation");
            c.assertTrue(f.a.setLocalBlock(DRIVE,drive(Direction.EAST,15).with(Properties.INVERTED,true)),"real drive reversal applied");
            for(int i=0;i<180;i++){f.step();double s=offset(f);c.assertTrue(s>=-.02&&s<=2.02,"retraction respects both material stops: "+s);}
            c.assertTrue(offset(f)<.2&&f.link().id()!=null,"same physical stage retracts without recreation");
        }finally{f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="linear-drive-ownership",tickLimit=80)
    public void WrongActuatorAndAmbiguousSourcesCannotPowerTheStage(TestContext c){
        var f=pair(c,Quat.IDENTITY);try{
            c.assertTrue(f.a.setLocalBlock(DRIVE,ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.EAST).with(Properties.POWER,15)),"rotary material placed behind linear guide");
            c.assertTrue(connect(f)&&f.link().motorForce()==0,"a rotary source cannot silently power a linear mechanism");
            c.assertTrue(f.a.setLocalBlock(DRIVE,drive(Direction.WEST,15)),"wrong-facing linear source placed");
            c.assertTrue(MechanicalBearingSystem.refresh(c.getWorld(),f.link(),f.a,f.b).motorForce()==0,"source orientation must match actual material guide");
            c.assertTrue(f.a.setLocalBlock(DRIVE,drive(Direction.EAST,15)),"matching source placed");
            c.assertTrue(MechanicalBearingSystem.refresh(c.getWorld(),f.link(),f.a,f.b).motorForce()==40,"one matching source restores finite force");
            c.assertTrue(f.b.setLocalBlock(new BlockPos(1,0,0),drive(Direction.WEST,15)),"second independent source placed");
            c.assertTrue(MechanicalBearingSystem.refresh(c.getWorld(),f.link(),f.a,f.b).motorForce()==0,"two sources coast rather than multiply force");
        }finally{f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="linear-coast-release",tickLimit=80)
    public void DriveRemovalAndWrenchReleasePreserveIndependentWorldMotion(TestContext c){
        var f=pair(c,Quat.IDENTITY);c.assertTrue(connect(f),"linear stage connects");
        c.waitAndRun(2,()->{var p=player(f);try{
            for(int i=0;i<20;i++)f.step();var id=f.link().id();
            c.assertTrue(f.b.physics().linearVelocity().length()>.01,"motor establishes actual child motion before release");
            c.assertTrue(f.a.setLocalBlock(DRIVE,Blocks.AIR.getDefaultState()),"real actuator destroyed");f.step();
            c.assertTrue(f.link().motorForce()==0&&f.b.physics().linearVelocity().length()>.01,"actuator loss coasts without deleting the passive slider or velocity");
            p.setPosition(f.b.localToWorld(new Vec3d(.5,-1,2)));p.setSneaking(true);
            var pa=f.a.getPos();var pb=f.b.getPos();var va=f.a.physics().linearVelocity();var vb=f.b.physics().linearVelocity();
            c.assertTrue(f.b.useLocalBlock(p,BlockPos.ORIGIN),"actual sneak-wrench use releases guide");
            c.assertTrue(f.registry().find(id).isEmpty()&&f.registry().collisionAllowed(f.a.getUuid(),f.b.getUuid())&&pa.equals(f.a.getPos())&&pb.equals(f.b.getPos())&&va.equals(f.a.physics().linearVelocity())&&vb.equals(f.b.physics().linearVelocity()),"detachment restores separate collisions and preserves exact physical state");
        }finally{p.discard();f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="linear-material-denial",tickLimit=80)
    public void MixedBearingTypesAndDestroyedSupportCannotBecomeHiddenJoints(TestContext c){
        var f=pair(c,Quat.IDENTITY);try{
            c.assertTrue(f.b.setLocalBlock(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.WEST)),"different bearing material installed");
            c.assertTrue(!connect(f)&&f.registry().forBody(f.a.getUuid()).isEmpty(),"linear and rotary material cannot create an ambiguous joint");
            c.assertTrue(f.b.setLocalBlock(BlockPos.ORIGIN,bearing(Direction.WEST))&&connect(f),"matching material can subsequently connect");var id=f.link().id();
            c.assertTrue(f.b.setLocalBlock(BlockPos.ORIGIN,Blocks.STONE.getDefaultState()),"actual bearing material replaced");
            c.assertTrue(MechanicalBearingSystem.refresh(c.getWorld(),f.link(),f.a,f.b)==null&&f.registry().find(id).isEmpty(),"destroyed guide cannot retain a magical connection");
        }finally{f.close();}c.complete();
    }
}
