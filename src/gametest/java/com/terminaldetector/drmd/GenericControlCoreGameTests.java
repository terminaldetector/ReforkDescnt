package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Unregistered first server contracts. No direct body.tick/drive/force invocation.
 * Propulsion fixtures use a floating Creative observer, explicitly isolating actuator
 * force from passenger friction; they do not claim boarding/client/cold-process proof. */
public final class GenericControlCoreGameTests implements FabricGameTest {
    static final BlockPos CORE = new BlockPos(2,1,0);
    private static final List<BlockPos> MOTORS = List.of(new BlockPos(0,1,0),new BlockPos(0,1,4),
            new BlockPos(4,1,0),new BlockPos(4,1,4));
    static final class Fixture implements AutoCloseable {
        final TestContext c; final AiFixtureChunks chunks;
        final List<ServerPlayerEntity> players = new ArrayList<>();
        final List<BlockBodyEntity> fragments = new ArrayList<>();
        final Map<BlockPos,BlockState> placed = new LinkedHashMap<>();
        BlockBodyEntity body; ServerPlayerEntity pilot; boolean closed;
        Fixture(TestContext c) {this(c,Map.of(),new BlockPos(8,190,8));}
        Fixture(TestContext c,Map<BlockPos,BlockState> extra,BlockPos relativeOrigin) {
            this.c=c;var at=c.getAbsolutePos(relativeOrigin);
            chunks=new AiFixtureChunks(c.getWorld(),at,1);
            var source=new LinkedHashMap<BlockPos,BlockState>();
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)source.put(at.add(x,0,z),Blocks.IRON_BLOCK.getDefaultState());
            for(var p:MOTORS)source.put(at.add(p),ModWorldBlocks.SHIP_THRUSTER.getDefaultState());
            source.put(at.add(CORE),ModWorldBlocks.SHIP_CONTROL_CORE.getDefaultState());
            try {
                for(var entry:extra.entrySet()) {
                    var p=entry.getKey();
                    c.assertTrue(p.getX()>=0&&p.getX()<5&&p.getY()>=0&&p.getY()<3&&p.getZ()>=0&&p.getZ()<5,"bounded extra fixture cells");
                    c.assertTrue(source.putIfAbsent(at.add(p),entry.getValue())==null,"extra fixture part cannot replace the base hull/core/engines");
                }
                for(var p:source.keySet())c.assertTrue(c.getWorld().getBlockState(p).isAir(),"test construction requires pre-existing AIR");
                source.forEach((p,state)->{
                    c.assertTrue(c.getWorld().setBlockState(p,state,Block.NOTIFY_LISTENERS),"actual fixture part placement succeeds");
                    placed.put(p,state);
                });
                body=BlockBodyEntity.assembleSelection(c.getWorld(),source);
                pilot=player(new Vec3d(2.5,2.2,2.5));
                c.assertTrue(BodySpatialIndex.find(c.getWorld(),body.getId()).orElse(null)==body,"actual spawned generic body is indexed");
                c.assertTrue(c.getWorld().canPlayerModifyAt(pilot,BlockPos.ofFloored(body.localToWorld(Vec3d.ofCenter(CORE)))),"positive fixture has actual modification permission");
            } catch(RuntimeException|Error failure) { close();throw failure; }
        }
        ServerPlayerEntity player(Vec3d local) {
            var p=c.createMockCreativeServerPlayerInWorld();players.add(p);
            DescentPlayerData.get(p).setEnabled(false);p.getAbilities().flying=true;
            p.setPosition(body.localToWorld(local));aim(p,body);return p;
        }
        public void close() {
            if(closed)return;closed=true;
            try {
                for(var p:players){UfoControlSystem.release(p);p.discard();}
                for(var b:fragments)b.discard();if(body!=null)body.discard();
                // Assembly consumes these cells. On a first admission/assembly failure,
                // remove only exact parts placed by this fixture into verified AIR.
                placed.forEach((p,state)->{if(c.getWorld().getBlockState(p).equals(state))
                    c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);});
            }finally{chunks.close();}
        }
    }
    private static void aim(ServerPlayerEntity p,BlockBodyEntity b) {
        var d=b.localToWorld(Vec3d.ofCenter(CORE)).subtract(p.getEyePos());
        var q=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(d.x,d.y,d.z));
        p.setYaw((float)q.yawDegrees());p.setPitch((float)q.pitchDegrees());
    }
    static boolean use(Fixture f) {
        aim(f.pilot,f.body);
        return BodyBlockInteraction.receive(f.pilot,new BodyBlockInteraction.Edit(f.body.getUuid(),f.body.shapeRevision(),CORE,BodyBlockInteraction.USE));
    }
    static UfoControlSystem.Input input(Fixture f,int sequence,float forward) {
        return new UfoControlSystem.Input(UfoControlSystem.epoch(f.pilot),sequence,forward,0,0,0,0,0);
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-use")
    public void actualUseClaimsTheGenericGridAndPreservesPhysicalState(TestContext c) {
        try(var f=new Fixture(c)) {
            var b=f.body;b.physics().withRotation(ImmPtlQuaternions.rotationByDegrees(new Vec3(0,1,0),37));b.publishPhysicsState();
            f.pilot.setPosition(b.localToWorld(new Vec3d(2.5,2.2,2.5)));
            b.physics().withLinearVelocity(new Vec3(4,2,-1)).withAngularVelocity(new Vec3(.03,.02,-.01));
            var id=b.getUuid();var q=b.rotation();var pos=b.massPosition();var v=b.physics().linearVelocity();var l=b.physics().angularMomentum();
            c.assertTrue(use(f)&&UfoControlSystem.controlsBody(f.pilot,b),"actual revision-bound local USE acquires the generic core");
            int epoch=UfoControlSystem.epoch(f.pilot);
            c.assertTrue(UfoControlSystem.claimCore(f.pilot,b,CORE)&&UfoControlSystem.epoch(f.pilot)==epoch,"same held claim is idempotent");
            var other=f.player(new Vec3d(3.5,2.2,2.5));
            c.assertTrue(BodyBlockInteraction.target(other,other.getEyePos(),BodyBlockInteraction.viewDirection(other),other.getBlockInteractionRange())
                .filter(hit->hit.body()==b&&hit.hit().localBlock().equals(CORE)).isPresent(),"second live player actually sees and reaches this core before the ownership refusal");
            c.assertTrue(!UfoControlSystem.claimCore(other,b,CORE),"second connection cannot steal the live lease");
            c.assertTrue(id.equals(b.getUuid())&&q.equals(b.rotation())&&pos.equals(b.massPosition())&&v.equals(b.physics().linearVelocity())&&l.equals(b.physics().angularMomentum()),"claim neither respawns nor resets pose or momentum");
            c.assertTrue(UfoControlSystem.release(f.pilot)&&v.equals(b.physics().linearVelocity())&&l.equals(b.physics().angularMomentum()),"release preserves physical momentum");
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-admission")
    public void ARealOccluderReachAndNonBuildingModeRefuseOwnership(TestContext c) {
        try(var f=new Fixture(c)) {
            var barrier=BlockPos.ofFloored(f.body.localToWorld(new Vec3d(2.5,2.5,1.5)));
            var old=c.getWorld().getBlockState(barrier);c.assertTrue(old.isAir(),"occluder fixture requires AIR outside the hull");
            try {c.assertTrue(c.getWorld().setBlockState(barrier,Blocks.GLASS.getDefaultState(),Block.NOTIFY_LISTENERS)
                    &&c.getWorld().getBlockState(barrier).isOf(Blocks.GLASS),"actual occluding glass is installed before the negative");
                c.assertTrue(!UfoControlSystem.claimCore(f.pilot,f.body,CORE),"actual world glass blocks the console ray");
            }finally{c.getWorld().setBlockState(barrier,old,Block.NOTIFY_LISTENERS);}
            f.pilot.setPosition(f.body.localToWorld(new Vec3d(7.4,2.2,7.4)));aim(f.pilot,f.body);
            c.assertTrue(!UfoControlSystem.claimCore(f.pilot,f.body,CORE),"console outside actual block reach is refused");
            f.pilot.setPosition(f.body.localToWorld(new Vec3d(2.5,2.2,2.5)));aim(f.pilot,f.body);
            f.pilot.changeGameMode(GameMode.ADVENTURE);
            c.assertTrue(!UfoControlSystem.claimCore(f.pilot,f.body,CORE),"non-building mode cannot bypass the actual USE policy");
            f.pilot.changeGameMode(GameMode.CREATIVE);f.pilot.getAbilities().flying=true;
            c.assertTrue(use(f),"unobstructed permitted real USE is the positive control");
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-material")
    public void removingAndReinsertingTheSameCoreFencesTheOldEpoch(TestContext c) {
        try(var f=new Fixture(c)) {
            c.assertTrue(use(f),"initial actual use");int epoch=UfoControlSystem.epoch(f.pilot);
            c.assertTrue(f.body.setLocalBlock(CORE,Blocks.AIR.getDefaultState()),"actual core removal commits");
            c.assertTrue(!UfoControlSystem.controls(f.pilot)&&!f.body.controlCore().bound(),"material commit releases the connection immediately");
            c.assertTrue(f.body.setLocalBlock(CORE,ModWorldBlocks.SHIP_CONTROL_CORE.getDefaultState()),"actual replacement, no automatic repair");
            aim(f.pilot,f.body);c.assertTrue(UfoControlSystem.claimCore(f.pilot,f.body,CORE),"permitted new core claim");
            c.assertTrue(UfoControlSystem.epoch(f.pilot)>epoch&&!UfoControlSystem.receive(f.pilot,new UfoControlSystem.Input(epoch,10000,1,0,0,0,0,0)),"reinserted same block cannot replay the old authority");
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-world-tick",tickLimit=100)
    public void ordinaryWorldTicksApplyThrustAndZeroInputDoesNotResetMomentum(TestContext c) {
        var f=new Fixture(c);int[] age={0},phase={0};long[] since={0};int[] born={0};Vec3[] stopped={null};
        c.runAtEveryTick(()->{
            if(f.closed)return;
            try {
                c.assertTrue(++age[0]<90,"bounded actual ticking admission/observation");c.getWorld().resetIdleTimeout();
                if(phase[0]==0) {
                    if(!f.chunks.entityTicking(f.body.getBlockPos()))return;
                    c.assertTrue(use(f),"real use after actual entity-ticking admission");var v=f.body.physics().linearVelocity();
                    c.assertTrue(UfoControlSystem.receive(f.pilot,input(f,0,1))&&v.equals(f.body.physics().linearVelocity()),"packet stores input without an immediate impulse");
                    since[0]=c.getWorld().getTime();born[0]=f.body.age;phase[0]=1;
                }else if(phase[0]==1&&c.getWorld().getTime()-since[0]>=3) {
                    c.assertTrue(f.body.age>born[0]&&f.body.physics().linearVelocity().z()>.1,"ordinary entity ticks, not a direct drive call, produce thrust");
                    var before=f.body.physics().linearVelocity();
                    c.assertTrue(UfoControlSystem.receive(f.pilot,input(f,1,0))&&before.equals(f.body.physics().linearVelocity()),"latest zero snapshot does not reset momentum on receipt");
                    born[0]=f.body.age;phase[0]=2;
                }else if(phase[0]==2&&f.body.age-born[0]>=2) {
                    // GameTest and body ordering in the current world tick is not assumed:
                    // two real body ticks admit the latest zero before the stable interval.
                    stopped[0]=f.body.physics().linearVelocity();born[0]=f.body.age;phase[0]=3;
                }else if(phase[0]==3&&f.body.age-born[0]>=2) {
                    c.assertTrue(stopped[0].minus(f.body.physics().linearVelocity()).length()<1e-8,"zero thrust retains momentum in the isolated contact-free fixture");
                    f.close();c.complete();
                }
            }catch(RuntimeException|Error failure){f.close();throw failure;}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-motor-loss",tickLimit=100)
    public void anUnsupportedMotorLayoutCannotCreateHiddenPropulsion(TestContext c) {
        var f=new Fixture(c);int[] age={0};long[] since={Long.MIN_VALUE};int[] born={0};
        try {c.assertTrue(f.body.setLocalBlocks(Map.of(MOTORS.get(0),Blocks.AIR.getDefaultState(),MOTORS.get(1),Blocks.AIR.getDefaultState())),"remove two actual engine blocks");}
        catch(RuntimeException|Error failure){f.close();throw failure;}
        c.runAtEveryTick(()->{
            if(f.closed)return;
            try {
                c.assertTrue(++age[0]<90,"bounded actual motor-loss observation");c.getWorld().resetIdleTimeout();
                if(since[0]==Long.MIN_VALUE) {
                    if(!f.chunks.entityTicking(f.body.getBlockPos()))return;
                    c.assertTrue(use(f)&&UfoControlSystem.receive(f.pilot,input(f,0,1)),"valid owner/input despite insufficient real engines");
                    since[0]=c.getWorld().getTime();born[0]=f.body.age;
                }else if(c.getWorld().getTime()-since[0]>=3) {
                    c.assertTrue(f.body.age>born[0]&&f.body.physics().linearVelocity().length()<1e-10,"real ticks cannot invent an invisible engine");
                    c.assertTrue(f.body.controlCore().layoutStatus().equals("REQUIRES_3_TO_16_MOTORS"),"unsupported layout is explicit");f.close();c.complete();
                }
            }catch(RuntimeException|Error failure){f.close();throw failure;}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fracture")
    public void aRealDetachedCoreKeepsMaterialVelocityButNotThePilotLease(TestContext c) {
        try(var f=new Fixture(c)) {
            c.assertTrue(use(f),"real pilot before fracture");f.body.physics().withLinearVelocity(new Vec3(2,1,-3)).withAngularVelocity(new Vec3(.02,.03,.04));
            var point=f.body.localToWorld(Vec3d.ofCenter(CORE));var velocity=f.body.surfaceVelocityAt(point);
            c.assertTrue(f.body.setLocalBlock(CORE.down(),Blocks.AIR.getDefaultState()),"cut the actual sole core support");
            var fragment=BodySpatialIndex.query(c.getWorld(),new Box(point,point).expand(.8)).stream().filter(b->b!=f.body&&b.cells().size()==1&&BodyControlCore.isCore(b.cells().getFirst().state())&&b.physicsCentre().squaredDistanceTo(point)<1e-10).findFirst().orElseThrow();f.fragments.add(fragment);
            c.assertTrue(!UfoControlSystem.controls(f.pilot)&&!UfoControlSystem.controlsBody(f.pilot,fragment)&&!fragment.controlCore().bound(),"material fracture does not copy network ownership");
            c.assertTrue(fragment.surfaceVelocityAt(point).subtract(velocity).length()<1e-8,"the real core fragment inherits its material-point velocity");
        }c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-nbt")
    public void normalBodyNbtPreservesPhysicsAndAnUnspawnedCloneCannotOwnTheCore(TestContext c) {
        try(var f=new Fixture(c)) {
            c.assertTrue(use(f),"actual owner before serialization");f.body.physics().withLinearVelocity(new Vec3(3,2,1)).withAngularVelocity(new Vec3(.03,.02,.01));
            var tag=f.body.writeNbt(new NbtCompound());var copy=new BlockBodyEntity(ModEntities.BLOCK_BODY,c.getWorld());
            try {copy.readNbt(tag);
                c.assertTrue(BodyControlCore.isCore(copy.getLocalBlock(CORE))&&copy.physics().linearVelocity().equals(f.body.physics().linearVelocity())&&copy.physics().angularMomentum().equals(f.body.physics().angularMomentum()),"ordinary registered block grid and physical state serialize");
                c.assertTrue(!copy.controlCore().bound()&&!UfoControlSystem.controlsBody(f.pilot,copy)&&!UfoControlSystem.claimCore(f.pilot,copy,CORE),"unspawned NBT clone has no connection or loaded-body authority");
                c.assertTrue(UfoControlSystem.controlsBody(f.pilot,f.body),"reading another object cannot release the real pilot");
            }finally{copy.discard();}
        }c.complete();
    }
}
