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
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-ai-natural",tickLimit=400)
    public void aRealInstalledModuleAimsAndFiresAtANaturalElevatedSideTarget(TestContext c){
        Fixture[] live={null};var lasers=new HashSet<UUID>();var rockets=new HashSet<UUID>();var rootLasers=new HashSet<UUID>();var rootRockets=new HashSet<UUID>();double[] angle={0},anchor={0};int[] observed={0};
        c.runAtEveryTick(()->{var f=live[0];if(f==null)return;for(var shot:f.shots())(shot.getMeshKind()==1?rockets:lasers).add(shot.getUuid());for(var shot:c.getWorld().getEntitiesByClass(ProjectileEntity.class,f.root.getBoundingBox().expand(150),s->s.getOwnerEntity()==f.root))(shot.getMeshKind()==1?rootRockets:rootLasers).add(shot.getUuid());angle[0]=Math.max(angle[0],2*Math.acos(Math.min(1,Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(f.root.rotation(),f.head.rotation())))));anchor[0]=Math.max(anchor[0],f.root.localToWorld(new Vec3d(.5,4,.5)).distanceTo(f.head.localToWorld(new Vec3d(.5,0,.5))));
            if(++observed[0]%20==0)System.out.println("TURRET_AI tick="+observed[0]+" shots="+lasers.size()+"/"+rockets.size()+" rootShots="+rootLasers.size()+"/"+rootRockets.size()+" motor="+BodyJointRegistry.get(c.getWorld()).find(f.module.jointId()).map(BodyJointLink::speed)+" simulated="+f.head.hasSimulatedPhysics()+" paused="+f.head.boardingMotionPaused()+" rootQ="+f.root.rotation()+" headQ="+f.head.rotation()+" target="+f.root.autonomousTarget()+" laserAim="+SkyUfoCombat.aim(f.head,SkyUfoTurret.LASER,f.target,2.5,80)+" laserClear="+SkyUfoCombat.clearShot(f.head,SkyUfoTurret.LASER,f.target,2.5,80)+" rocketClear="+SkyUfoCombat.clearShot(f.head,SkyUfoTurret.ROCKET,f.target,1.4,100));});
        admitted(c,f->{live[0]=f;f.target.setInvulnerable(true);f.root.setAutopilot(true);
            c.waitAndRun(260,()->{try{
                c.assertTrue(f.root.autonomousTarget().orElse(null)==f.target,"normal carrier AI acquires the external Survival player");
                c.assertTrue(lasers.size()>=3&&rockets.size()>=1,"an actual unpiloted child module must emit both real batteries: "+lasers.size()+" / "+rockets.size());
                c.assertTrue(rootLasers.size()>=3&&rootRockets.size()>=1,"cooperation cannot starve the carrier batteries: "+rootLasers.size()+" / "+rootRockets.size());
                c.assertTrue(angle[0]>.25&&anchor[0]<.03&&f.module.connected(f.root),"real yaw motor turns while its physical joint remains connected: "+angle[0]+" / "+anchor[0]);
            }finally{f.close();live[0]=null;}c.complete();});
        });
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
