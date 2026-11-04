package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Real registered UFO geometry and state; not a replacement decorative fixture. */
public final class PhysicalUfoGameTests implements FabricGameTest {
    private net.minecraft.server.network.ServerPlayerEntity pilot(TestContext c,SkyUfoEntity ufo) {
        var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.getAbilities().flying=false;
        p.setPosition(ufo.localToWorld(new Vec3d(3.5,.1,-1)));return p;
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-ray")
    public void actualLocalConsoleUseCapturesWithoutRespawnOrVelocityReset(TestContext c) {
        var b=create(c);var p=pilot(c,b);try {
            b.setAutopilot(true);b.physics().withLinearVelocity(new Vec3(9,2,-3)).withAngularVelocity(new Vec3(.1,.2,-.1));
            var id=b.getUuid();var position=b.massPosition();var v=b.physics().linearVelocity();var momentum=b.physics().angularMomentum();
            var aim=b.localToWorld(Vec3d.ofCenter(com.terminaldetector.drmd.world.mega.UfoControlSystem.CONSOLE)).subtract(p.getEyePos());
            var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(aim.x,aim.y,aim.z));p.setYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());
            c.assertTrue(BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(id,b.shapeRevision(),com.terminaldetector.drmd.world.mega.UfoControlSystem.CONSOLE,BodyBlockInteraction.USE)),"real authoritative local-block use ray must acquire the console");
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p)&&!b.autopilotEnabled(),"player ownership suppresses AI");
            c.assertTrue(id.equals(b.getUuid())&&position.equals(b.massPosition())&&v.equals(b.physics().linearVelocity())&&momentum.equals(b.physics().angularMomentum()),"capture preserves physical identity, position and both momenta");
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p)&&b.autopilotEnabled(),"release restores previous AI mode");
            c.assertTrue(v.equals(b.physics().linearVelocity())&&momentum.equals(b.physics().angularMomentum()),"release cannot stop the existing hull");
        } finally {com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);p.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-owner")
    public void consoleHasOneOwnerAndHeldUseIsIdempotent(TestContext c) {
        var b=create(c);var p=pilot(c,b);var other=pilot(c,b);try {
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b),"first pilot");int epoch=com.terminaldetector.drmd.world.mega.UfoControlSystem.epoch(p);
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b)&&com.terminaldetector.drmd.world.mega.UfoControlSystem.epoch(p)==epoch,"held use does not release/reset ownership");
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(other,b),"second pilot cannot steal an occupied console");
            com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(other,b),"released console can be captured again");
        } finally {com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);com.terminaldetector.drmd.world.mega.UfoControlSystem.release(other);p.discard();other.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-input")
    public void boundedPilotCommandsApplyOnceThroughTheExistingPhysicsBody(TestContext c) {
        var b=create(c);var p=pilot(c,b);try {
            var control=com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b);int epoch=com.terminaldetector.drmd.world.mega.UfoControlSystem.epoch(p);var before=b.physics().linearVelocity();
            c.assertTrue(control&&!com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,new com.terminaldetector.drmd.world.mega.UfoControlSystem.Input(epoch+1,0,1,0,0,0,0,0)),"stale owner frame rejected");
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,new com.terminaldetector.drmd.world.mega.UfoControlSystem.Input(epoch,0,Float.NaN,0,0,0,0,0)),"nonfinite thrust rejected");
            var input=new com.terminaldetector.drmd.world.mega.UfoControlSystem.Input(epoch,0,1,0,0,0,0,0);
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,input)&&before.equals(b.physics().linearVelocity()),"packet receipt alone never advances physics");
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,input),"duplicate rejected");com.terminaldetector.drmd.world.mega.UfoControlSystem.drive(b);var after=b.physics().linearVelocity();
            c.assertTrue(Math.abs(after.z()-before.z()-.25)<1e-8,"bounded thrust changes actual hull momentum");com.terminaldetector.drmd.world.mega.UfoControlSystem.drive(b);
            c.assertTrue(after.equals(b.physics().linearVelocity()),"a repeated same-tick drive cannot multiply thrust");
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,new com.terminaldetector.drmd.world.mega.UfoControlSystem.Input(epoch,4096,0,0,0,0,0,0)),"latest input may resume above a large pause/backpressure gap without a simulation backlog");
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,new com.terminaldetector.drmd.world.mega.UfoControlSystem.Input(epoch,1,1,0,0,0,0,0))&&after.equals(b.physics().linearVelocity()),"older snapshot cannot restore thrust or advance physics");
        } finally {com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);p.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-save")
    public void savingAPilotedHullDoesNotPersistAnAbsentConnectionOrDisableItsAI(TestContext c) {
        var b=create(c);var p=pilot(c,b);SkyUfoEntity copy=null;try {
            b.setAutopilot(true);com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b);var tag=b.writeNbt(new NbtCompound());
            c.assertTrue(tag.getBoolean("autopilot"),"save retains intended AI mode rather than transient takeover suppression");
            copy=ModEntities.SKY_UFO.create(c.getWorld());copy.readNbt(tag);copy.discard();
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p),"unspawned NBT clone cannot release the real hull's pilot");
        } finally {if(copy!=null)copy.discard();com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);p.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-damage")
    public void miningTheConsoleReleasesControlImmediately(TestContext c) {
        var b=create(c);var p=pilot(c,b);try {
            b.setAutopilot(true);c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b),"pilot acquired");
            c.assertTrue(b.setLocalBlock(com.terminaldetector.drmd.world.mega.UfoControlSystem.CONSOLE,Blocks.AIR.getDefaultState()),"actual console material removed");
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p)&&b.autopilotEnabled(),"material commit releases ownership and restores AI without a later polling tick");
        } finally {com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);p.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-exit")
    public void leavingTheHullReleasesAndOldInputsCannotControlARecapturedShip(TestContext c) {
        var b=create(c);var p=pilot(c,b);try {
            b.setAutopilot(true);c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b),"initial capture");
            int oldEpoch=com.terminaldetector.drmd.world.mega.UfoControlSystem.epoch(p);
            b.physics().withLinearVelocity(new Vec3(4,2,-3)).withAngularVelocity(new Vec3(.2,.1,-.3));
            var velocity=b.physics().linearVelocity();var momentum=b.physics().angularMomentum();
            p.setPosition(b.localToWorld(new Vec3d(30,8,30)));
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.drive(b)&&!com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p)&&b.autopilotEnabled(),"leaving the real local hull releases authority and restores AI");
            c.assertTrue(velocity.equals(b.physics().linearVelocity())&&momentum.equals(b.physics().angularMomentum()),"exit does not reset either hull momentum");
            p.setPosition(b.localToWorld(new Vec3d(3.5,.1,-1)));
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b),"returning pilot can capture again");
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.epoch(p)>oldEpoch,"recapture gets a fresh authority epoch");
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.receive(p,new com.terminaldetector.drmd.world.mega.UfoControlSystem.Input(oldEpoch,10000,1,0,0,0,0,1)),"late commands from the previous capture cannot drive the current ship");
        } finally {com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);p.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-control-mode")
    public void EnablingSuitFlightOrRemovingTheHullCannotLeaveAnOwner(TestContext c) {
        var b=create(c);var p=pilot(c,b);try {
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b),"capture before suit transition");
            DescentPlayerData.get(p).setEnabled(true);
            c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.drive(b)&&!com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p),"suit flight and ship piloting cannot own input simultaneously");
            DescentPlayerData.get(p).setEnabled(false);
            c.assertTrue(com.terminaldetector.drmd.world.mega.UfoControlSystem.claim(p,b),"capture after suit transition");
            b.discard();c.assertTrue(!com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p),"actual entity removal releases the pilot immediately");
        } finally {com.terminaldetector.drmd.world.mega.UfoControlSystem.release(p);p.discard();b.discard();}c.complete();
    }
    private static final DynamicBodyAttachment.Motion FREE=(p,q,d)->new DynamicBodyAttachment.Move(d,java.util.List.of());
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reload-walker")
    public void rejoiningWithSavedWalkingDoesNotArmSuitFlightOrResetMomentum(TestContext c) {
        var p=c.createMockCreativeServerPlayerInWorld();try {
            var d=DescentPlayerData.get(p);d.setSessionWelcomed(true);d.setEnabled(false);
            var saved=new NbtCompound();d.writeNbt(saved);d.readNbt(saved);
            var velocity=new Vec3d(.1,.2,-.3);p.setVelocity(velocity);p.setNoGravity(true);
            com.terminaldetector.drmd.world.base.DescentSession.onPlayerJoin(p);
            c.assertTrue(d.hasSavedFlightMode()&&!d.isEnabled()&&!p.hasNoGravity(),"a persisted walker remains available to the physical boarding motor");
            c.assertTrue(velocity.equals(p.getVelocity()),"restoration is not an H-off command and must preserve saved world momentum");
        } finally {p.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reload-flight")
    public void newPilotsAndSavedFlyingPilotsStillInitializeActualFreeFlight(TestContext c) {
        var p=c.createMockCreativeServerPlayerInWorld();try {
            var d=DescentPlayerData.get(p);d.setSessionWelcomed(true);
            c.assertTrue(!d.hasSavedFlightMode(),"new player has no persisted movement choice");
            com.terminaldetector.drmd.world.base.DescentSession.onPlayerJoin(p);
            c.assertTrue(d.isEnabled()&&p.hasNoGravity()&&!p.getAbilities().allowFlying,"new players still start in native 6DoF");
            var saved=new NbtCompound();d.writeNbt(saved);d.readNbt(saved);p.setNoGravity(false);
            com.terminaldetector.drmd.world.base.DescentSession.onPlayerJoin(p);
            c.assertTrue(d.hasSavedFlightMode()&&d.isEnabled()&&p.hasNoGravity(),"saved flight is reinitialized, including its actual gravity/ability state");
        } finally {p.discard();}c.complete();
    }
    private SkyUfoEntity create(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,190,8));var w=c.getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        var ufo=ModEntities.SKY_UFO.create(w);ufo.setAutopilot(false);ufo.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);
        c.assertTrue(w.spawnEntity(ufo),"spawn existing UFO registration");ufo.tick();return ufo;
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-load-admission")
    public void savedPassengerWaitsForTheRealHullAndHandshakeWithoutTeleportFollowing(TestContext c) {
        var original=create(c);var p=pilot(c,original);SkyUfoEntity loaded=null;try {
            var d=DescentPlayerData.get(p);original.physics().withLinearVelocity(new Vec3(3,0,0)).withAngularVelocity(new Vec3(0,.1,0));
            d.rememberBoarding(original.getUuid(),p.getWorld().getRegistryKey().getValue().toString(),original.rotation());
            var playerData=new NbtCompound();d.writeNbt(playerData);d.readNbt(playerData);
            c.assertTrue(d.pendingBoardingResume()!=null&&BoardingRestore.holds(p),"saved physical context admits a bounded load wait");
            var feet=p.getPos();p.travel(Vec3d.ZERO);c.assertTrue(feet.equals(p.getPos()),"pending travel cannot fall through a hull that has not loaded");
            var before=original.massPosition();original.tick();c.assertTrue(!before.equals(original.massPosition()),"a body that already simulates is never frozen by a returning passenger");
            var saved=original.writeNbt(new NbtCompound());original.discard();
            loaded=ModEntities.SKY_UFO.create(c.getWorld());loaded.readNbt(saved);c.assertTrue(c.getWorld().spawnEntity(loaded),"load the same real physical identity");
            var position=loaded.massPosition();var velocity=loaded.physics().linearVelocity();var momentum=loaded.physics().angularMomentum();var rotation=loaded.rotation();
            loaded.tick();
            c.assertTrue(!loaded.hasSimulatedPhysics()&&position.equals(loaded.massPosition())&&rotation.equals(loaded.rotation()),"newly loaded hull waits at its saved pose until physical passenger admission");
            c.assertTrue(velocity.equals(loaded.physics().linearVelocity())&&momentum.equals(loaded.physics().angularMomentum()),"load admission preserves both momenta");
            BoardingSystem.ready(p,com.terminaldetector.drmd.network.BoardingNetworking.PROTOCOL);BoardingSystem.tick(c.getWorld());
            c.assertTrue(BoardingSystem.drives(p)&&d.pendingBoardingResume()==null,"real local gravity/contact acquisition ends the wait");
            loaded.tick();c.assertTrue(loaded.hasSimulatedPhysics()&&!position.equals(loaded.massPosition()),"normal D6 integration resumes after acquisition");
        } finally {BoardingRestore.clear(p);p.discard();original.discard();if(loaded!=null)loaded.discard();BoardingSystem.tick(c.getWorld());}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-load-timeout",tickLimit=130)
    public void absentSavedHullCannotTrapAPlayerInALoadWait(TestContext c) {
        var p=c.createMockCreativeServerPlayerInWorld();var d=DescentPlayerData.get(p);d.setEnabled(false);p.getAbilities().flying=false;
        d.rememberBoarding(java.util.UUID.randomUUID(),p.getWorld().getRegistryKey().getValue().toString(),Quat.IDENTITY);
        var saved=new NbtCompound();d.writeNbt(saved);d.readNbt(saved);
        c.assertTrue(BoardingRestore.holds(p),"missing saved body initially waits");
        c.waitAndRun(101,()->{try {
            c.assertTrue(!BoardingRestore.holds(p)&&d.pendingBoardingResume()==null&&!BoardingPose.active(p),"missing body times out and releases ordinary world movement/pose");c.complete();
        }finally {BoardingRestore.clear(p);p.discard();}});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-load-cancel")
    public void flightChoiceAndDimensionMismatchCancelSavedBodyAdmission(TestContext c) {
        var p=c.createMockCreativeServerPlayerInWorld();var d=DescentPlayerData.get(p);try {
            d.setEnabled(false);d.rememberBoarding(java.util.UUID.randomUUID(),p.getWorld().getRegistryKey().getValue().toString(),Quat.IDENTITY);
            var saved=new NbtCompound();d.writeNbt(saved);d.readNbt(saved);d.setEnabled(true);
            c.assertTrue(!BoardingRestore.holds(p)&&d.pendingBoardingResume()==null,"choosing suit flight cancels pending physical admission immediately");
            d.setEnabled(false);d.rememberBoarding(java.util.UUID.randomUUID(),"minecraft:the_nether",Quat.IDENTITY);
            saved=new NbtCompound();d.writeNbt(saved);d.readNbt(saved);
            c.assertTrue(!BoardingRestore.holds(p)&&d.pendingBoardingResume()==null,"a saved contact cannot freeze a player in another dimension");
        } finally {BoardingRestore.clear(p);p.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-flight-ccd",tickLimit=60)
    public void freeFlightKernelStopsAtTheActualRotatedUfoDeck(TestContext c) {
        var ufo=create(c);try {
            var q=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));
            ufo.physics().withRotation(q);ufo.publishPhysicsState();
            var pos=ufo.localToWorld(new Vec3d(3,8,3));var velocity=q.rotate(new Vec3(0,-320,0));
            var s=new DynamicBodyAttachment.State(new Vec3(pos.x,pos.y,pos.z),velocity,false,-1,q,-1);
            var stopped=DynamicBodyAttachment.freeFlight(s,java.util.List.of(ufo.boardingMotion()),.6,1.8,.05,FREE);
            var local=ufo.worldToLocal(new Vec3d(stopped.feet().x(),stopped.feet().y(),stopped.feet().z()));
            c.assertTrue(local.y>=-1e-5,"fast flight cannot cross the real rotated deck material");
            c.assertTrue(stopped.velocity().minus(velocity).length()>100,"actual compound contact changes normal velocity");
            c.assertTrue(Math.abs(ImmPtlQuaternions.dot(stopped.frame(),q.normalized()))>1-1e-12&&stopped.gravityOwner()<0,"flight does not silently become a walking gravity motor");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-flight-entrance",tickLimit=60)
    public void freeFlightKernelPassesThroughTheActualUfoEntrance(TestContext c) {
        var ufo=create(c);try {
            var pos=ufo.localToWorld(new Vec3d(0,.1,-14));var velocity=new Vec3(0,0,240);
            var s=new DynamicBodyAttachment.State(new Vec3(pos.x,pos.y,pos.z),velocity,false,-1);
            var passed=DynamicBodyAttachment.freeFlight(s,java.util.List.of(ufo.boardingMotion()),.6,1.8,.05,FREE);
            var local=ufo.worldToLocal(new Vec3d(passed.feet().x(),passed.feet().y(),passed.feet().z()));
            c.assertTrue(local.z>-2.01,"actual hollow entrance stays open to the actor shape");
            c.assertTrue(passed.velocity().minus(velocity).length()<1e-8,"empty space cannot act as the UFO envelope collider");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-single-hull",tickLimit=60)
    public void existingRegistrationCreatesOnePhysicalGridWithoutWorldBlocks(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,190,8));var w=c.getWorld();var sentinel=at.add(11,0,11);w.setBlockState(sentinel,Blocks.GOLD_BLOCK.getDefaultState(),2);
        var before=BodySpatialIndex.loaded(w).size();var ufo=create(c);
        try {
            c.assertTrue(ufo instanceof BlockBodyEntity&&ufo.getType()==ModEntities.SKY_UFO,"existing ID is the physical body itself");
            c.assertTrue(ufo.cells().size()>500&&ufo.physicsMass()>500&&ufo.collider().boxes().size()>1,"real hollow material grid has aggregate mass and compound geometry");
            c.assertTrue(BodySpatialIndex.loaded(w).size()==before+1,"no second proxy/body entity or per-block rigid bodies");
            c.assertTrue(w.getBlockState(at).isAir()&&w.getBlockState(sentinel).isOf(Blocks.GOLD_BLOCK),"initialization does not materialize or erase world blocks");
        } finally {ufo.discard();}
        c.assertTrue(w.getBlockState(sentinel).isOf(Blocks.GOLD_BLOCK),"removing UFO cannot clear unrelated terrain");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-rotated-interior",tickLimit=60)
    public void actualDoorAndDeckRemainHollowAfterThreeAxisRotation(TestContext c) {
        var ufo=create(c);try {
            var q=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));ufo.physics().withRotation(q);ufo.publishPhysicsState();
            var access=new BlockBodyWorldAccess(ufo);var door=access.raycastBlockBody(ufo.localToWorld(new Vec3d(0,1,-14)),ufo.localToWorld(new Vec3d(0,1,-2)));
            c.assertTrue(door.isEmpty(),"an actual entrance must not become a solid entity AABB");
            var floor=access.raycastBlockBody(ufo.localToWorld(new Vec3d(3,2,3)),ufo.localToWorld(new Vec3d(3,-2,3))).orElseThrow();
            c.assertTrue(floor.localBlock().getY()==-1&&floor.worldNormal().dot(q.rotate(new Vec3(0,1,0)))>.999999,"deck normal follows full quaternion");
            c.assertTrue(ufo.containsPos(ufo.localToWorld(new Vec3d(3,.1,3))),"interior membership uses inverse body transform");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-local-damage",tickLimit=60)
    public void miningRealHullMaterialKeepsIdentityAndChangesMass(TestContext c) {
        var ufo=create(c);try {
            var id=ufo.getUuid();double mass=ufo.physicsMass();var pos=new BlockPos(3,-1,3);
            c.assertTrue(!ufo.getLocalBlock(pos).isAir()&&ufo.setLocalBlock(pos,Blocks.AIR.getDefaultState()),"remove actual local deck material");
            c.assertTrue(ufo.getUuid().equals(id)&&ufo.physicsMass()<mass&&ufo.getLocalBlock(pos).isAir(),"normal BlockBody edits update the same UFO and its mass");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-state",tickLimit=60)
    public void physicalSaveRestoresDamagedGridGravityAndMomentum(TestContext c) {
        var ufo=create(c);SkyUfoEntity copy=null;
        try {
            ufo.setLocalBlock(new BlockPos(3,-1,3),Blocks.AIR.getDefaultState());ufo.physics().withLinearVelocity(new Vec3(12,-3,5)).withAngularVelocity(new Vec3(.3,-.2,.1));ufo.publishPhysicsState();
            var n=ufo.writeNbt(new NbtCompound());copy=ModEntities.SKY_UFO.create(c.getWorld());copy.readNbt(n);
            c.assertTrue(copy.cells().equals(ufo.cells())&&copy.shapeRevision()==ufo.shapeRevision(),"saved real damaged grid is not regenerated from the template");
            c.assertTrue(copy.physics().linearVelocity().equals(ufo.physics().linearVelocity())&&copy.physics().angularMomentum().equals(ufo.physics().angularMomentum())&&copy.passengerGravity().equals(ufo.passengerGravity()),"physical velocity/momentum/gravity survive entity NBT");
        } finally {if(copy!=null)copy.discard();ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-reactor",tickLimit=60)
    public void localReactorFailureChangesForcesWithoutResettingMotion(TestContext c) {
        var ufo=create(c);try {
            ufo.physics().withLinearVelocity(new Vec3(9,2,-3)).withAngularVelocity(new Vec3(.1,.2,-.15));var material=new Vec3d(3.5,-.5,3.5);var position=ufo.localToWorld(material);var rotation=ufo.rotation();var velocity=ufo.physics().velocityAtPoint(new Vec3(position.x-ufo.massPosition().x(),position.y-ufo.massPosition().y(),position.z-ufo.massPosition().z()));
            ufo.destroyFromCore(c.getWorld(),null,"test local core");
            c.assertTrue(ufo.isCrashing()&&ufo.physicsGravity()&&ufo.passengerGravity().lengthSquared()==0,"power failure disables artificial gravity and enables actual fall");
            var after=ufo.localToWorld(material);var afterVelocity=ufo.physics().velocityAtPoint(new Vec3(after.x-ufo.massPosition().x(),after.y-ufo.massPosition().y(),after.z-ufo.massPosition().z()));
            c.assertTrue(after.distanceTo(position)<1e-8&&ufo.rotation().equals(rotation)&&afterVelocity.minus(velocity).length()<1e-8,"core transition preserves actual material point pose and velocity across its COM rebase");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-mined-reactor",tickLimit=60)
    public void ordinaryLocalCoreMiningImmediatelyPowersDownTheHullAndDetachedMaterial(TestContext c) {
        var before=BodySpatialIndex.loaded(c.getWorld()).stream().map(BlockBodyEntity::getUuid).collect(java.util.stream.Collectors.toSet());
        var ufo=create(c);
        try {
            c.assertTrue(new BlockBodyWorldAccess(ufo).breakLocalBlock(new BlockPos(0,1,0)),"actual local-grid mining removes reactor material");
            c.assertTrue(ufo.isCrashing()&&ufo.physicsGravity()&&ufo.passengerGravity().lengthSquared()==0,"material commit powers down the UFO without a later controller poll");
            var fragments=BodySpatialIndex.loaded(c.getWorld()).stream().filter(b->b!=ufo&&!before.contains(b.getUuid())).toList();
            c.assertTrue(!fragments.isEmpty()&&fragments.stream().allMatch(BlockBodyEntity::physicsGravity),"separated stabilizer material inherits the powered-down fall state");
        } finally {for(var b:BodySpatialIndex.loaded(c.getWorld()))if(!before.contains(b.getUuid()))b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-legacy-fall",tickLimit=60)
    public void legacyCrashingSaveMigratesToAPhysicalFallWithoutRecreatingWorldBlocks(TestContext c) {
        var original=create(c);SkyUfoEntity migrated=null;
        try {
            var old=original.writeNbt(new NbtCompound());old.remove("shape");old.remove("body");old.remove("physicalUfoSchema");old.putBoolean("crashing",true);
            migrated=ModEntities.SKY_UFO.create(c.getWorld());migrated.readNbt(old);migrated.setUuid(java.util.UUID.randomUUID());migrated.setAutopilot(false);
            c.assertTrue(c.getWorld().spawnEntity(migrated),"load legacy UFO as the same registered entity class");migrated.tick();
            c.assertTrue(migrated.isMaterialized()&&migrated.isCrashing()&&migrated.physicsGravity()&&migrated.passengerGravity().lengthSquared()==0,"legacy crash cannot turn into an artificially powered hovering wreck");
        } finally {if(migrated!=null)migrated.discard();original.discard();}c.complete();
    }
}
