package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Ordinary entity ticks only. No direct tick/detonate/clock setter or diagnostic-only main hooks. */
public final class UfoReactorEscapeGameTests implements FabricGameTest {
    private static final BlockPos CORE=new BlockPos(0,1,0);
    private static final class Fixture implements AutoCloseable {
        final TestContext c;final AiFixtureChunks chunks;final BlockPos at;final Set<UUID> preexisting;
        SkyUfoEntity body;ServerPlayerEntity player;boolean started,closed;int admission;
        Fixture(TestContext c){
            this.c=c;at=c.getAbsolutePos(new BlockPos(8,220,8));chunks=new AiFixtureChunks(c.getWorld(),at,1);
            preexisting=new HashSet<>();for(var b:BodySpatialIndex.query(c.getWorld(),Box.of(Vec3d.ofCenter(at),128,384,128)))preexisting.add(b.getUuid());
            body=ModEntities.SKY_UFO.create(c.getWorld());body.setAutopilot(false);
            body.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);
            c.assertTrue(c.getWorld().spawnEntity(body),"existing registered physical UFO admitted");
        }
        boolean breakCore(){return new BlockBodyWorldAccess(body).breakLocalBlock(CORE);}
        public void close(){
            if(closed)return;closed=true;
            try{
                if(player!=null){UfoControlSystem.release(player);player.discard();}
                var roster=body.writeNbt(new NbtCompound()).getList("swarm",NbtElement.COMPOUND_TYPE);
                for(int i=0;i<roster.size();i++)if(roster.getCompound(i).containsUuid("id")){
                    var drone=c.getWorld().getEntity(roster.getCompound(i).getUuid("id"));if(drone!=null)drone.discard();
                }
                // This class has distinct sequential batches and a private loaded fixture region.
                // Preserve every pre-existing hull; clean only bodies created during this episode.
                for(var b:BodySpatialIndex.query(c.getWorld(),Box.of(Vec3d.ofCenter(at),128,384,128)))
                    if(!preexisting.contains(b.getUuid()))b.discard();
                body.discard();
            }finally{chunks.close();}
        }
    }
    private static void admitted(TestContext c,java.util.function.Consumer<Fixture> action){
        var f=new Fixture(c);
        c.runAtEveryTick(()->{
            if(f.closed||f.started)return;
            try{
                c.assertTrue(++f.admission<=80,"bounded actual entity-ticking admission");
                if(!f.body.isMaterialized()||!f.chunks.entityTicking(f.body.getBlockPos())||f.body.boardingMotionPaused())return;
                f.started=true;action.accept(f);
            }catch(Throwable failure){f.close();throw failure;}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reactor-escape-clock",tickLimit=130)
    public void EscapeClockUsesActualEntityTicksAndCannotBeRetimedAfterDamage(TestContext c){admitted(c,f->{
        var b=f.body;c.assertTrue(b.configureReactorEscapeTicks(40),"intact reactor accepts bounded optional window");
        var point=b.localToWorld(new Vec3d(3.5,-.5,3.5));var velocity=b.surfaceVelocityAt(point);var q=b.rotation();
        c.assertTrue(f.breakCore(),"real local material destruction enters critical phase");
        int born=b.age;c.assertTrue(b.isCrashing()&&!b.hasPoweredReactor()&&b.reactorTicksRemaining()==40,"existing power-down and initial window");
        c.assertTrue(!b.configureReactorEscapeTicks(1200),"already critical clock cannot be silently restarted");
        c.assertTrue(point.distanceTo(b.localToWorld(new Vec3d(3.5,-.5,3.5)))<1e-8&&q.equals(b.rotation())
                &&velocity.distanceTo(b.surfaceVelocityAt(point))<1e-8,"critical entry retains material pose and contact-point velocity");
        c.waitAndRun(6,()->{try{
            c.assertTrue(b.age>born&&b.reactorTicksRemaining()==40-(b.age-born)&&!b.reactorDetonated(),"only ordinary body ticks consume the saved window");
        }finally{f.close();}c.complete();});
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reactor-optional-off",tickLimit=230)
    public void OptionalZeroWindowLeavesAnInertPhysicalWreckWithoutTimedBlast(TestContext c){admitted(c,f->{
        var b=f.body;c.assertTrue(b.configureReactorEscapeTicks(0)&&f.breakCore(),"explicitly disabled optional timed blast");int born=b.age;
        c.waitAndRun(115,()->{try{
            c.assertTrue(b.age-born>=100&&b.isCrashing()&&!b.reactorDetonated()&&b.reactorEscapeTicks()==0,"elapsed original100ticks cannot force a disabled timer");
            c.assertTrue(b.physicsGravity()&&b.passengerGravity().lengthSquared()==0,"basic physical power failure remains active without timed self-destruction");
        }finally{f.close();}c.complete();});
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reactor-resume",tickLimit=190)
    public void SavedCriticalCountdownResumesAndSpentWreckCannotReplayTheBlast(TestContext c){admitted(c,f->{
        c.assertTrue(f.body.configureReactorEscapeTicks(40)&&f.breakCore(),"normal local damage starts configured window");
        c.waitAndRun(8,()->{
            var before=f.body;int remaining=before.reactorTicksRemaining();var saved=before.writeNbt(new NbtCompound());
            before.discard();var restored=ModEntities.SKY_UFO.create(c.getWorld());restored.readNbt(saved);f.body=restored;
            c.assertTrue(restored.reactorTicksRemaining()==remaining&&remaining>0&&remaining<40&&!restored.reactorDetonated(),"entity NBT restores remaining duration without reset");
            c.assertTrue(c.getWorld().spawnEntity(restored),"same saved UUID returns as one actual physical hull");
            c.waitAndRun(remaining+4,()->{
                c.assertTrue(restored.reactorDetonated()&&restored.reactorTicksRemaining()==0,"ordinary resumed ticks reach loaded-volume detonation");
                long revision=restored.shapeRevision();var cells=restored.cells();var spent=restored.writeNbt(new NbtCompound());
                c.assertTrue(spent.getBoolean("detonated"),"one-shot guard is persisted in normal entity state");
                restored.discard();var wreck=ModEntities.SKY_UFO.create(c.getWorld());wreck.readNbt(spent);f.body=wreck;
                c.assertTrue(c.getWorld().spawnEntity(wreck),"spent hull restores without rebuilding the template");
                c.waitAndRun(6,()->{try{
                    c.assertTrue(wreck.reactorDetonated()&&wreck.shapeRevision()==revision&&wreck.cells().equals(cells),"ordinary spent ticks cannot repeat local blast or regenerate material");
                }finally{f.close();}c.complete();});
            });
        });
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reactor-volume",tickLimit=110)
    public void BlastAdmissionRefusesUnknownVolumesWithoutLoadingAColumn(TestContext c){admitted(c,f->{try{
        c.assertTrue(ReactorEscapeWindow.blastVolumeAvailable(c.getWorld(),f.body.localToWorld(Vec3d.ofCenter(CORE))),"positive loaded reactor fixture");
        var manager=c.getWorld().getChunkManager();int before=manager.getLoadedChunkCount();var far=new Vec3d(1000000,150,1000000);
        c.assertTrue(manager.getWorldChunk(1000000>>4,1000000>>4)==null,"negative volume starts unknown");
        c.assertTrue(!ReactorEscapeWindow.blastVolumeAvailable(c.getWorld(),far)&&manager.getWorldChunk(1000000>>4,1000000>>4)==null
                &&manager.getLoadedChunkCount()==before,"bounded preflight cannot generate a blast neighbourhood");
    }finally{f.close();}c.complete();});}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reactor-command",tickLimit=110)
    public void ExistingOperatorCommandConfiguresOnlyAnIntactNearbyReactor(TestContext c){admitted(c,f->{try{
        f.player=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(f.player).setEnabled(false);
        f.player.getAbilities().flying=true;f.player.setPosition(f.body.localToWorld(new Vec3d(2.5,1,2.5)));
        var manager=c.getWorld().getServer().getCommandManager();var source=f.player.getCommandSource();
        manager.executeWithPrefix(source.withLevel(0),"d6 mega uforeactordelay 2");
        c.assertTrue(f.body.reactorEscapeTicks()==100,"ordinary permission cannot reconfigure the reactor phase");
        manager.executeWithPrefix(source.withLevel(2),"d6 mega uforeactordelay 2");
        c.assertTrue(f.body.reactorEscapeTicks()==40,"actual OP2 command updates the existing nearby hull");
        c.assertTrue(!f.body.configureReactorEscapeTicks(-1)&&!f.body.configureReactorEscapeTicks(1201),"invalid configuration cannot escape the finite window");
        c.assertTrue(f.breakCore(),"real source material damage");
        manager.executeWithPrefix(source.withLevel(2),"d6 mega uforeactordelay 60");
        c.assertTrue(f.body.reactorEscapeTicks()==40,"same real command cannot rearm a critical reactor");
    }finally{f.close();}c.complete();});}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-reactor-real-sabotage",tickLimit=110)
    public void SabotageRequiresAnExposedPhysicalReactorAndOrdinaryPermission(TestContext c) {
        admitted(c, f -> {
            try {
                var world = c.getWorld();
                f.player = c.createMockCreativeServerPlayerInWorld();
                DescentPlayerData.get(f.player).setEnabled(false);
                f.player.setPosition(f.body.localToWorld(new Vec3d(3.5, .05, .5)));
                var eye = f.player.getEyePos();
                var core = f.body.localToWorld(Vec3d.ofCenter(CORE));
                c.assertTrue(f.body.containsPos(f.player.getPos()), "actual positioned player is within the physical hull");
                var target = BodyBlockInteraction.target(f.player, eye, core.subtract(eye), 5);
                c.assertTrue(target.isPresent() && target.get().body() == f.body
                    && target.get().hit().localBlock().equals(CORE), "unobstructed normal ray has the physical core as its first hit");
                var cover = BlockPos.ofFloored(eye.lerp(core, .45));
                var old = world.getBlockState(cover);
                c.assertTrue(old.isAir(), "external cover occupies a genuinely empty world cell within the ship");
                world.setBlockState(cover, Blocks.BEDROCK.getDefaultState(), 3);
                try {
                    c.assertTrue(!SkyUfoEntity.tryReactorDump(world, f.player) && f.body.hasPoweredReactor(),
                        "ordinary world cover blocks sabotage and leaves the actual core intact");
                } finally {
                    world.setBlockState(cover, old, 3);
                }
                f.player.getAbilities().allowModifyWorld = false;
                c.assertTrue(!SkyUfoEntity.tryReactorDump(world, f.player) && f.body.hasPoweredReactor(),
                    "a player without edit permission cannot bypass ordinary local interactions");
                f.player.getAbilities().allowModifyWorld = true;
                c.assertTrue(SkyUfoEntity.tryReactorDump(world, f.player) && f.body.isCrashing()
                    && f.body.reactorTicksRemaining() == f.body.reactorEscapeTicks()
                    && f.body.reactorFailureCause().equals("reactor dump"),
                    "normal sabotage destroys the reactor and starts the existing bounded evacuation window");
            } finally {
                f.close();
            }
            c.complete();
        });
    }
}
