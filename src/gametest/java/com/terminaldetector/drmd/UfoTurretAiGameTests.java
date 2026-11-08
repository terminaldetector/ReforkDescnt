package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class UfoTurretAiGameTests implements FabricGameTest {
    private double motor(Fixture f){return BodyJointRegistry.get(f.c.getWorld()).find(f.module.jointId()).orElseThrow().speed();}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-pilot",tickLimit=170)
    public void actualPilotCaptureImmediatelyPreemptsAiWithoutErasingManualMotorCommands(TestContext c){admitted(c,f->{f.root.setAutopilot(true);c.waitAndRun(3,()->{try{
        c.assertTrue(Math.abs(motor(f))>.1,"real AI motor command before capture");var v=f.root.physics().linearVelocity();var l=f.root.physics().angularMomentum();var cv=f.head.physics().linearVelocity();var cl=f.head.physics().angularMomentum();
        f.target.setPosition(f.root.localToWorld(new Vec3d(3.5,.1,.5)));c.assertTrue(UfoControlSystem.claim(f.target,f.root)&&motor(f)==0,"actual console ownership retires the AI motor immediately");
        c.assertTrue(UfoControlSystem.receiveTurret(f.target,new UfoControlSystem.TurretInput(UfoControlSystem.epoch(f.target),0,-1))&&UfoControlSystem.drive(f.root),"actual pilot motor snapshot");f.module.aimAutonomous(f.root);
        c.assertTrue(motor(f)==-.6&&f.module.fireAutonomous(f.root)==0,"AI cannot overwrite an owned manual motor or leak a shot");
        c.assertTrue(v.equals(f.root.physics().linearVelocity())&&l.equals(f.root.physics().angularMomentum())&&cv.equals(f.head.physics().linearVelocity())&&cl.equals(f.head.physics().angularMomentum()),"authority changes preserve both bodies' actual momenta");
        c.assertTrue(UfoControlSystem.release(f.target)&&f.root.autopilotEnabled(),"release resumes the same assembly's previous AI mode");f.target.setPosition(f.root.localToWorld(new Vec3d(30,12,12)));f.target.setVelocity(Vec3d.ZERO);
        c.waitAndRun(3,()->{try{c.assertTrue(Math.abs(motor(f))>.1&&f.module.connected(f.root),"the same real motor resumes after release");}finally{f.close();}c.complete();});
    }catch(Throwable e){f.close();throw e;}});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-disable",tickLimit=190)
    public void explicitDisableRetiresTheMotorAndBothPhysicalWeapons(TestContext c){admitted(c,f->{f.root.setAutopilot(true);c.waitAndRun(3,()->{try{
        var v=f.root.physics().linearVelocity();var l=f.root.physics().angularMomentum();var cv=f.head.physics().linearVelocity();var cl=f.head.physics().angularMomentum();f.root.setAutopilot(false);
        c.assertTrue(motor(f)==0&&f.module.fireAutonomous(f.root)==0,"disabled autonomy has no persistent motor or firing command");c.assertTrue(v.equals(f.root.physics().linearVelocity())&&l.equals(f.root.physics().angularMomentum())&&cv.equals(f.head.physics().linearVelocity())&&cl.equals(f.head.physics().angularMomentum()),"disable cannot reset physical motion");
        c.waitAndRun(35,()->{try{c.assertTrue(motor(f)==0&&f.module.fireAutonomous(f.root)==0&&f.module.connected(f.root),"disabled joint stays physical and inactive");}finally{f.close();}c.complete();});
    }catch(Throwable e){f.close();throw e;}});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-creative",tickLimit=170)
    public void aCreativeModeChangeCannotReuseAStaleTargetToken(TestContext c){admitted(c,f->{f.root.setAutopilot(true);c.waitAndRun(3,()->{try{
        c.assertTrue(Math.abs(motor(f))>.1,"natural Survival target drives the real motor");f.target.changeGameMode(net.minecraft.world.GameMode.CREATIVE);f.module.aimAutonomous(f.root);c.assertTrue(motor(f)==0&&f.module.fireAutonomous(f.root)==0,"an old cached target cannot authorize tracking or fire after a mode change");
    }finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-copy",tickLimit=170)
    public void aSavedReferenceCopyCannotCommandOrDetachTheActualModule(TestContext c){admitted(c,f->{f.root.setAutopilot(true);c.waitAndRun(3,()->{try{
        var copy=SkyUfoTurret.read(f.module.write());double before=motor(f);boolean gravity=f.head.physicsGravity();var v=f.head.physics().linearVelocity();var l=f.head.physics().angularMomentum();copy.aim(f.root,-1);
        c.assertTrue(motor(f)==before,"serialized identity cannot grant an extra motor controller");copy.detach(f.root);copy.tick(f.root);c.assertTrue(f.head.physicsGravity()==gravity&&v.equals(f.head.physics().linearVelocity())&&l.equals(f.head.physics().angularMomentum())&&copy.fireAutonomous(f.root)==0,"a copied saved reference has no physical or firing authority");
    }finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-detach",tickLimit=170)
    public void breakingTheRealAnchorStopsAutonomyAndPreservesSeparationMotion(TestContext c){admitted(c,f->{f.root.setAutopilot(true);c.waitAndRun(3,()->{try{
        var at=f.head.massPosition();var q=f.head.rotation();var v=f.head.physics().linearVelocity();var l=f.head.physics().angularMomentum();c.assertTrue(f.root.setLocalBlock(SkyUfoTurret.ROOF,net.minecraft.block.Blocks.AIR.getDefaultState()),"real material anchor cut");f.module.tick(f.root);f.module.aimAutonomous(f.root);
        c.assertTrue(!f.module.connected(f.root)&&BodyJointRegistry.get(c.getWorld()).find(f.module.jointId()).isEmpty()&&f.head.physicsGravity()&&f.module.fireAutonomous(f.root)==0,"actual broken joint has no inherited autonomous authority");
        c.assertTrue(at.equals(f.head.massPosition())&&q.equals(f.head.rotation())&&v.equals(f.head.physics().linearVelocity())&&l.equals(f.head.physics().angularMomentum()),"separation keeps the real child state");
    }finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-guns",tickLimit=230)
    public void missingModuleGunCellsCannotCreateInvisibleShotsOrReplacementMaterial(TestContext c){Fixture[] live={null};var seen=new HashSet<UUID>();c.runAtEveryTick(()->{if(live[0]!=null)for(var s:live[0].shots())seen.add(s.getUuid());});admitted(c,f->{live[0]=f;
        f.head.setLocalBlock(SkyUfoTurret.LASER,net.minecraft.block.Blocks.AIR.getDefaultState());f.head.setLocalBlock(SkyUfoTurret.ROCKET,net.minecraft.block.Blocks.AIR.getDefaultState());f.root.setAutopilot(true);
        c.waitAndRun(80,()->{try{c.assertTrue(seen.isEmpty()&&f.head.cells().size()==3&&f.module.connected(f.root),"autonomy cannot invent missing weapons or respawn the module");}finally{live[0]=null;f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-independent",tickLimit=310)
    public void aDamagedCarrierStillUsesTheIndependentPhysicalYawMotor(TestContext c){Fixture[] live={null};var lasers=new HashSet<UUID>();var rockets=new HashSet<UUID>();c.runAtEveryTick(()->{if(live[0]!=null)for(var s:live[0].shots())(s.getMeshKind()==1?rockets:lasers).add(s.getUuid());});admitted(c,f->{live[0]=f;
        for(var p:SkyUfoThrusters.SLOTS)f.root.setLocalBlock(p,net.minecraft.block.Blocks.AIR.getDefaultState());for(var p:SkyUfoWeapons.LASERS)f.root.setLocalBlock(p,net.minecraft.block.Blocks.AIR.getDefaultState());for(var p:SkyUfoWeapons.ROCKETS)f.root.setLocalBlock(p,net.minecraft.block.Blocks.AIR.getDefaultState());
        var v=f.root.physics().linearVelocity();var l=f.root.physics().angularMomentum();f.root.applyThrusterWrench(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(1000,2000,3000),new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(3000,2000,1000));c.assertTrue(v.equals(f.root.physics().linearVelocity())&&l.equals(f.root.physics().angularMomentum()),"the actual engine command path supplies no impulse with every real motor missing");
        f.target.setPosition(f.root.localToWorld(new Vec3d(30,5,12)));f.target.setVelocity(Vec3d.ZERO);f.target.setInvulnerable(true);var q=f.root.rotation();f.root.setAutopilot(true);
        c.waitAndRun(180,()->{try{double rootTurn=2*Math.acos(Math.min(1,Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(q,f.root.rotation()))));double relative=2*Math.acos(Math.min(1,Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(f.root.rotation(),f.head.rotation()))));c.assertTrue(f.root.activeThrusters()==0&&Double.isFinite(rootTurn)&&relative>.5&&lasers.size()>=3&&rockets.size()>=1,"real motor fires relative to a freely reacting unpowered carrier: "+rootTurn+" / "+relative+" / "+lasers.size()+" / "+rockets.size());}finally{live[0]=null;f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-observed-motion",tickLimit=170)
    public void ordinaryPredictionUsesRealPacketMotionAndRejectsDiscontinuousTransfers(TestContext c){admitted(c,f->{
        f.target.setVelocity(.4,-.2,.1);SkyUfoCombat.observePlayers(c.getWorld());var start=f.target.getPos();c.waitAndRun(1,()->{try{
            var delta=new Vec3d(.2,.1,-.3);f.target.setPosition(start.add(delta));c.waitAndRun(1,()->{try{SkyUfoCombat.observePlayers(c.getWorld());c.assertTrue(SkyUfoCombat.targetVelocity(f.target).distanceTo(delta)<1e-8&&f.target.getVelocity().distanceTo(new Vec3d(.4,-.2,.1))<1e-8,"observed packet movement replaces stale prediction without editing physical velocity: "+SkyUfoCombat.targetVelocity(f.target));SkyUfoCombat.observePlayers(c.getWorld());c.assertTrue(SkyUfoCombat.targetVelocity(f.target).distanceTo(delta)<1e-8,"one frame is observed once");
            f.target.setPosition(f.target.getPos().add(100,0,0));f.target.setVelocity(Vec3d.ZERO);c.waitAndRun(1,()->{try{SkyUfoCombat.observePlayers(c.getWorld());c.assertTrue(SkyUfoCombat.targetVelocity(f.target).lengthSquared()==0,"a discontinuous transfer cannot become a target velocity");}finally{f.close();}c.complete();});
        }catch(Throwable e){f.close();throw e;}});}catch(Throwable e){f.close();throw e;}});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-power",tickLimit=170)
    public void reactorLossRemovesMotorPowerAndFiringWithoutResettingTheChild(TestContext c){admitted(c,f->{f.root.setAutopilot(true);c.waitAndRun(3,()->{try{
        var v=f.head.physics().linearVelocity();var l=f.head.physics().angularMomentum();c.assertTrue(f.root.setLocalBlock(new BlockPos(0,1,0),net.minecraft.block.Blocks.AIR.getDefaultState()),"real reactor damage");f.module.aimAutonomous(f.root);f.module.tick(f.root);var link=BodyJointRegistry.get(c.getWorld()).find(f.module.jointId()).orElseThrow();
        c.assertTrue(!f.root.hasPoweredReactor()&&link.speed()==0&&link.motorForce()==0&&f.module.fireAutonomous(f.root)==0&&f.head.passengerGravity().lengthSquared()==0,"a connected but unpowered module cannot receive hidden power or fire: power="+f.root.hasPoweredReactor()+" speed="+link.speed()+" force="+link.motorForce()+" gravity="+f.head.passengerGravity());c.assertTrue(v.equals(f.head.physics().linearVelocity())&&l.equals(f.head.physics().angularMomentum()),"power loss preserves material momentum");
    }finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-cover",tickLimit=250)
    public void actualCoverStopsTheModuleUntilTheSamePhysicalLaneReopens(TestContext c){Fixture[] live={null};var seen=new HashSet<UUID>();c.runAtEveryTick(()->{if(live[0]!=null)for(var s:live[0].shots())seen.add(s.getUuid());});admitted(c,f->{live[0]=f;
        for(var p:SkyUfoThrusters.SLOTS)f.root.setLocalBlock(p,net.minecraft.block.Blocks.AIR.getDefaultState());f.target.setPosition(f.root.localToWorld(new Vec3d(.5,5,34)));f.target.setVelocity(Vec3d.ZERO);f.target.setInvulnerable(true);
        c.assertTrue(SkyUfoCombat.clearShot(f.head,SkyUfoTurret.LASER,f.target,2.5,80),"actual unobstructed initial child barrel has a positive firing lane");var origin=BlockPos.ofFloored(SkyUfoWeapons.muzzle(f.head,SkyUfoTurret.LASER).add(0,0,12));var old=new HashMap<BlockPos,net.minecraft.block.BlockState>();for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++){var p=origin.add(x,y,0);old.put(p,c.getWorld().getBlockState(p));c.getWorld().setBlockState(p,net.minecraft.block.Blocks.BEDROCK.getDefaultState(),3);}f.root.setAutopilot(true);
        c.waitAndRun(40,()->{try{c.assertTrue(seen.isEmpty()&&f.module.connected(f.root),"real wall blocks both actual module guns for the complete window");old.forEach((p,s)->c.getWorld().setBlockState(p,s,3));c.waitAndRun(35,()->{try{c.assertTrue(!seen.isEmpty(),"same module naturally fires after the real wall is removed");}finally{live[0]=null;f.close();}c.complete();});}catch(Throwable e){old.forEach((p,s)->c.getWorld().setBlockState(p,s,3));live[0]=null;f.close();throw e;}});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-natural",tickLimit=400)
    public void aRealInstalledModuleAimsAndFiresAtANaturalElevatedSideTarget(TestContext c){
        Fixture[] live={null};var lasers=new HashSet<UUID>();var rockets=new HashSet<UUID>();var rootLasers=new HashSet<UUID>();var rootRockets=new HashSet<UUID>();double[] angle={0},anchor={0};int[] observed={0};
        c.runAtEveryTick(()->{var f=live[0];if(f==null)return;for(var shot:f.shots())(shot.getMeshKind()==1?rockets:lasers).add(shot.getUuid());for(var shot:c.getWorld().getEntitiesByClass(ProjectileEntity.class,f.root.getBoundingBox().expand(150),s->s.getOwnerEntity()==f.root))(shot.getMeshKind()==1?rootRockets:rootLasers).add(shot.getUuid());angle[0]=Math.max(angle[0],2*Math.acos(Math.min(1,Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(f.root.rotation(),f.head.rotation())))));anchor[0]=Math.max(anchor[0],f.root.localToWorld(new Vec3d(.5,4,.5)).distanceTo(f.head.localToWorld(new Vec3d(.5,0,.5))));
            if(++observed[0]%20==0)System.out.println("TURRET_AI tick="+observed[0]+" shots="+lasers.size()+"/"+rockets.size()+" rootShots="+rootLasers.size()+"/"+rootRockets.size()+" motor="+BodyJointRegistry.get(c.getWorld()).find(f.module.jointId()).map(BodyJointLink::speed)+" simulated="+f.head.hasSimulatedPhysics()+" paused="+f.head.boardingMotionPaused()+" rootQ="+f.root.rotation()+" headQ="+f.head.rotation()+" rootV="+f.root.physics().linearVelocity()+" rootW="+f.root.physics().angularVelocity()+" motors="+f.root.activeThrusters()+" cells="+f.root.cells().size()+"/"+f.head.cells().size()+" targetV="+SkyUfoCombat.targetVelocity(f.target)+" laser="+lane(f,SkyUfoTurret.LASER,2.5)+" rocket="+lane(f,SkyUfoTurret.ROCKET,1.4));});
        admitted(c,f->{live[0]=f;f.target.setInvulnerable(true);f.root.setAutopilot(true);
            c.waitAndRun(260,()->{try{
                c.assertTrue(f.root.autonomousTarget().orElse(null)==f.target,"normal carrier AI acquires the external Survival player");
                c.assertTrue(lasers.size()>=3&&rockets.size()>=1,"an actual unpiloted child module must emit both real batteries: "+lasers.size()+" / "+rockets.size());
                c.assertTrue(rootLasers.size()>=3&&rootRockets.size()>=1,"cooperation cannot starve the carrier batteries: "+rootLasers.size()+" / "+rootRockets.size());
                c.assertTrue(angle[0]>.25&&anchor[0]<.03&&f.module.connected(f.root),"real yaw motor turns while its physical joint remains connected: "+angle[0]+" / "+anchor[0]);
            }finally{f.close();live[0]=null;}c.complete();});
        });
    }
    private String lane(Fixture f,BlockPos slot,double speed){
        var from=SkyUfoWeapons.muzzle(f.head,slot);var d=f.head.rotation().rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,0,1));var velocity=new Vec3d(d.x(),d.y(),d.z()).multiply(speed).add(f.head.surfaceVelocityAt(from).multiply(.05));var relative=velocity.subtract(SkyUfoCombat.targetVelocity(f.target));double t=f.target.getBoundingBox().getCenter().subtract(from).dotProduct(relative)/relative.lengthSquared();var end=from.add(velocity.multiply(t));
        if(!f.target.getBoundingBox().offset(SkyUfoCombat.targetVelocity(f.target).multiply(t)).expand(.25).contains(end))return "angle:"+end.subtract(f.target.getBoundingBox().getCenter());
        if(!com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(f.c.getWorld(),from,end))return "unloaded";
        var wall=f.c.getWorld().raycast(new net.minecraft.world.RaycastContext(from,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,f.head));if(wall.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)return "world:"+wall.getPos();
        for(var b:BodySpatialIndex.query(f.c.getWorld(),new Box(from,end).expand(.3)))if(b.physicsRaycast(from,end).isPresent())return "body:"+b.getUuid();
        for(var e:f.c.getWorld().getOtherEntities(f.head,new Box(from,end).expand(.3),e->e instanceof net.minecraft.entity.LivingEntity&&e!=f.target&&e.isAlive()&&!e.isSpectator()))if(e.getBoundingBox().expand(.3).raycast(from,end).isPresent())return "living:"+e.getName().getString();
        return "clear";
    }
    private void admitted(TestContext c,java.util.function.Consumer<Fixture> action){
        var at=c.getAbsolutePos(new BlockPos(8,220,8));var chunks=new AiFixtureChunks(c.getWorld(),at,3);boolean[] started={false};int[] wait={0};var positions=new ArrayList<BlockPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)positions.add(at.add(16*x,0,16*z));
        c.runAtEveryTick(()->{if(started[0])return;if(!chunks.entityTicking(positions.toArray(BlockPos[]::new))){if(++wait[0]<80)return;started[0]=true;chunks.close();c.assertTrue(false,"module fixture needs real entity-ticking columns");return;}started[0]=true;
            var b=ModEntities.SKY_UFO.create(c.getWorld());b.setAutopilot(false);b.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);c.assertTrue(c.getWorld().spawnEntity(b),"real registered carrier");b.tick();
            var installer=c.createMockCreativeServerPlayerInWorld();installer.setPosition(b.localToWorld(new Vec3d(3.5,.1,.5)));DescentPlayerData.get(installer).setEnabled(false);installer.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));
            c.assertTrue(b.useLocalBlock(installer,UfoControlSystem.CONSOLE),"actual kit/console installation");var module=b.turretModule().orElseThrow();var head=module.body(b).orElseThrow();c.assertTrue(UfoControlSystem.release(installer),"real pilot releases the same physical assembly");installer.discard();
            var target=c.createMockCreativeServerPlayerInWorld();target.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);target.getAbilities().flying=false;target.setNoGravity(true);DescentPlayerData.get(target).setEnabled(false);target.setPosition(b.localToWorld(new Vec3d(30,12,12)));target.setVelocity(Vec3d.ZERO);
            var f=new Fixture(c,b,module,head,target,chunks);try{action.accept(f);}catch(Throwable e){f.close();throw e;}
        });
    }
    private record Fixture(TestContext c,SkyUfoEntity root,SkyUfoTurret module,BlockBodyEntity head,ServerPlayerEntity target,AiFixtureChunks chunks) implements AutoCloseable {
        List<ProjectileEntity> shots(){return c.getWorld().getEntitiesByClass(ProjectileEntity.class,root.getBoundingBox().expand(150),s->s.getOwnerEntity()==head);}
        public void close(){
            var ids=new HashSet<UUID>();ids.add(root.getUuid());ids.add(head.getUuid());var roster=root.writeNbt(new net.minecraft.nbt.NbtCompound()).getList("swarm",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);for(int i=0;i<roster.size();i++)if(roster.getCompound(i).containsUuid("id"))ids.add(roster.getCompound(i).getUuid("id"));
            var remove=new ArrayList<net.minecraft.entity.Entity>();for(var e:c.getWorld().iterateEntities())if(ids.contains(e.getUuid())||e instanceof ProjectileEntity shot&&shot.getOwnerEntity()!=null&&ids.contains(shot.getOwnerEntity().getUuid()))remove.add(e);for(var e:remove)e.discard();
            BodyJointRegistry.get(c.getWorld()).remove(module.jointId());UfoControlSystem.release(target);target.discard();head.discard();root.discard();chunks.close();
        }
    }
}
