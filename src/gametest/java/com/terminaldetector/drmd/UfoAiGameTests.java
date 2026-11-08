package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class UfoAiGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-grounded-lead",tickLimit=140)
    public void aGroundedTargetsResidualGravityCannotAimTheLaserIntoTheFloor(TestContext c){admitted(c,f->{
        var from=SkyUfoWeapons.muzzle(f.body,SkyUfoWeapons.LASERS.getFirst());double floor=Math.ceil(f.target.getY());double dy=floor+f.target.getHeight()*.5-from.y;
        c.assertTrue(f.body.resolveContactPose(f.body.massPosition().plus(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,dy,0)),f.body.rotation()),"initial floor fixture clears physical geometry");from=SkyUfoWeapons.muzzle(f.body,SkyUfoWeapons.LASERS.getFirst());
        f.target.setPosition(from.x,floor,from.z+32);f.target.setVelocity(0,-.0784000015258789,0);f.target.setOnGround(true);var at=BlockPos.ofFloored(f.target.getPos().add(0,-.1,0));var old=c.getWorld().getBlockState(at);
        try{c.getWorld().setBlockState(at,net.minecraft.block.Blocks.BEDROCK.getDefaultState(),3);c.assertTrue(SkyUfoCombat.clearShot(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80),"real grounded target remains reachable above its actual bedrock support despite vanilla residual gravity");}
        finally{c.getWorld().setBlockState(at,old,3);f.close();}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-battery-progress",tickLimit=340)
    public void physicalAimCanCompleteBothLaserAndRocketBursts(TestContext c){
        var lasers=new HashSet<UUID>();var rockets=new HashSet<UUID>();Fixture[] fixture={null};
        // Register the repeating observation before the test starts ticking. Adding
        // hundreds of callbacks inside GameTestState's callback-map iterator rehashes it.
        c.runAtEveryTick(()->{if(fixture[0]!=null)for(var shot:fixture[0].shots())(shot.getMeshKind()==1?rockets:lasers).add(shot.getUuid());});
        admitted(c,f->{fixture[0]=f;
        var q=new com.terminaldetector.drmd.client.portal.PortalTransform.Quat(-.4900137814472482,.19754841070792994,.18852243580489392,.82784081236475);c.assertTrue(f.body.resolveContactPose(f.body.massPosition(),q),"saved native fixture orientation clears real world geometry");f.target.setInvulnerable(true);f.body.setAutopilot(true);
        c.waitAndRun(210,()->{try{c.assertTrue(lasers.size()>=3&&rockets.size()>=1,"finite-inertia aiming cannot starve either real battery: "+lasers.size()+" / "+rockets.size());}finally{f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-source-launch",tickLimit=220)
    public void autonomousShotsDoNotDestroyTheirOwnRotatingGunCells(TestContext c){admitted(c,f->{
        f.body.physics().withAngularVelocity(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(.2,.3,.1));f.body.setAutopilot(true);
        c.waitAndRun(100,()->{try{for(var slot:SkyUfoWeapons.LASERS)c.assertTrue(f.body.getLocalBlock(slot).isOf(ModWorldBlocks.SHIP_LASER),"own autonomous launch cannot destroy laser mount "+slot);for(var slot:SkyUfoWeapons.ROCKETS)c.assertTrue(f.body.getLocalBlock(slot).isOf(ModWorldBlocks.SHIP_ROCKET),"own autonomous launch cannot destroy rocket mount "+slot);}finally{f.close();}c.complete();});
    });}
    private void admitted(TestContext c,java.util.function.Consumer<Fixture> action){
        var at=c.getAbsolutePos(new BlockPos(8,220,8));var chunks=new AiFixtureChunks(c.getWorld(),at,3);boolean[] started={false};int[] wait={0};
        var positions=new ArrayList<BlockPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)positions.add(at.add(16*x,0,16*z));
        c.runAtEveryTick(()->{if(started[0])return;if(!chunks.entityTicking(positions.toArray(BlockPos[]::new))){if(++wait[0]<80)return;started[0]=true;chunks.close();c.assertTrue(false,"AI fixture needs actual loaded entity-ticking columns");return;}started[0]=true;
            var b=ModEntities.SKY_UFO.create(c.getWorld());b.setAutopilot(false);b.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);c.assertTrue(c.getWorld().spawnEntity(b),"real physical UFO");b.tick();
            var p=c.createMockCreativeServerPlayerInWorld();p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.getAbilities().flying=false;p.setNoGravity(true);DescentPlayerData.get(p).setEnabled(false);p.setPosition(b.localToWorld(new Vec3d(-2.5,-.4,44)));p.setVelocity(Vec3d.ZERO);
            var f=new Fixture(c,b,p,chunks);try{action.accept(f);}catch(Throwable failure){f.close();throw failure;}
        });
    }
    private record Fixture(TestContext c,SkyUfoEntity body,ServerPlayerEntity target,AiFixtureChunks chunks) implements AutoCloseable{
        List<ProjectileEntity> shots(){return c.getWorld().getEntitiesByClass(ProjectileEntity.class,body.getBoundingBox().expand(120),s->{var n=s.writeNbt(new NbtCompound());return n.containsUuid("owner")&&n.getUuid("owner").equals(body.getUuid());});}
        public void close(){
            var ids=new HashSet<UUID>();ids.add(body.getUuid());var roster=body.writeNbt(new NbtCompound()).getList("swarm",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);for(int i=0;i<roster.size();i++)if(roster.getCompound(i).containsUuid("id"))ids.add(roster.getCompound(i).getUuid("id"));
            for(var s:c.getWorld().getEntitiesByClass(ProjectileEntity.class,body.getBoundingBox().expand(120),s->s.getOwnerEntity()!=null&&ids.contains(s.getOwnerEntity().getUuid())))s.discard();
            for(var id:ids){var e=c.getWorld().getEntity(id);if(e!=null)e.discard();}UfoControlSystem.release(target);target.discard();body.discard();chunks.close();
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-autonomous-fire",tickLimit=180)
    public void anUnpilotedDefaultHullFiresItsRealBatteriesAtASurvivalTarget(TestContext c){var seen=new HashSet<UUID>();Fixture[] fixture={null};c.runAtEveryTick(()->{if(fixture[0]!=null)for(var shot:fixture[0].shots())seen.add(shot.getUuid());});admitted(c,f->{fixture[0]=f;
        c.assertTrue(f.target.interactionManager.getGameMode()==net.minecraft.world.GameMode.SURVIVAL,"fixture must use the authoritative Survival game mode despite the mock creative override");f.body.setAutopilot(true);
        c.waitAndRun(35,()->{try{
            c.assertTrue(!UfoControlSystem.controls(f.target),"target cannot be a fabricated pilot");
            c.assertTrue(!seen.isEmpty(),"unpiloted default UFO must fire actual hull-owned projectiles before tick 35, including already impacted shots: allowed="+SkyUfoCombat.allowed(f.body)+" target="+f.body.autonomousTarget()+" difficulty="+c.getWorld().getDifficulty()+" paused="+f.body.boardingMotionPaused()+" p="+f.target.getPos()+" local="+f.body.worldToLocal(f.target.getPos())+" mode="+f.target.interactionManager.getGameMode()+" aim="+SkyUfoCombat.aim(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80)+" clear="+SkyUfoCombat.clearShot(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80));
        }finally{f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-source-save",tickLimit=140)
    public void aSavedProjectileRetainsThePhysicalOwnerAndCanDamageLivingTargets(TestContext c){admitted(c,f->{
        var pig=net.minecraft.entity.EntityType.PIG.create(c.getWorld());var at=f.body.localToWorld(new Vec3d(24,1,24));pig.setPosition(at);pig.setNoGravity(true);pig.setAiDisabled(true);c.assertTrue(c.getWorld().spawnEntity(pig),"real living target");
        ProjectileEntity original=null,copy=null;try{
            var cfg=new com.terminaldetector.drmd.weapon.core.WeaponCore.FireConfig();cfg.owner=f.body;cfg.pos=at.add(0,.4,-1);cfg.dir=new Vec3d(0,0,1);cfg.speedIsSourceUnits=false;cfg.speed=2.5f;cfg.directDamage=4;
            original=com.terminaldetector.drmd.weapon.core.WeaponCore.fireProjectile(cfg);c.assertTrue(original!=null&&original.getOwnerEntity()==f.body&&original.getOwnerLiving()==null,"physical body is the real owner without a proxy pilot");
            var tag=original.writeNbt(new NbtCompound());original.discard();copy=ModEntities.PROJECTILE.create(c.getWorld());copy.readNbt(tag);c.assertTrue(copy.getOwnerEntity()==f.body,"owner UUID resolves the actual loaded physical identity after NBT read");
            float before=pig.getHealth();copy.tick();c.assertTrue(pig.getHealth()<before&&pig.getRecentDamageSource()!=null&&pig.getRecentDamageSource().getAttacker()==f.body,"actual projectile impact damages living targets with the physical launcher as attacker");
        }finally{if(original!=null)original.discard();if(copy!=null)copy.discard();pig.discard();f.close();}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-cover",tickLimit=140)
    public void terrainPhysicalCoverAndBystandersBlockTheActualFiringLane(TestContext c){admitted(c,f->{
        var from=SkyUfoWeapons.muzzle(f.body,SkyUfoWeapons.LASERS.getFirst());var at=BlockPos.ofFloored(from.add(0,0,12));var old=c.getWorld().getBlockState(at);com.terminaldetector.drmd.world.contraption.BlockBodyEntity cover=null;net.minecraft.entity.passive.PigEntity bystander=null;
        try{
            c.assertTrue(SkyUfoCombat.clearShot(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80),"loaded unoccluded actual barrel reaches target");
            c.getWorld().setBlockState(at,net.minecraft.block.Blocks.BEDROCK.getDefaultState(),3);c.assertTrue(!SkyUfoCombat.clearShot(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80),"world wall prevents fire");c.getWorld().setBlockState(at,old,3);
            cover=com.terminaldetector.drmd.world.contraption.BlockBodyEntity.createLocal(c.getWorld(),List.of(new com.terminaldetector.drmd.world.contraption.BlockBodyEntity.Cell(BlockPos.ORIGIN,net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState())),from.add(-.5,-.5,12));
            c.assertTrue(!SkyUfoCombat.clearShot(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80),"a real physical hull blocks the firing lane");cover.discard();
            bystander=net.minecraft.entity.EntityType.PIG.create(c.getWorld());bystander.setPosition(from.add(0,-.4,12));bystander.setNoGravity(true);bystander.setAiDisabled(true);c.getWorld().spawnEntity(bystander);c.assertTrue(!SkyUfoCombat.clearShot(f.body,SkyUfoWeapons.LASERS.getFirst(),f.target,2.5,80),"intervening living bystander prevents fire");
        }finally{c.getWorld().setBlockState(at,old,3);if(cover!=null)cover.discard();if(bystander!=null)bystander.discard();f.close();}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-disabled",tickLimit=180)
    public void aDisabledAutopilotDoesNotFireAutonomously(TestContext c){admitted(c,f->{c.waitAndRun(35,()->{try{c.assertTrue(f.shots().isEmpty(),"unpiloted but explicitly disabled autopilot cannot fire");}finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-creative",tickLimit=180)
    public void creativePlayersAreNotAutonomousTargets(TestContext c){admitted(c,f->{f.target.changeGameMode(net.minecraft.world.GameMode.CREATIVE);f.body.setAutopilot(true);c.waitAndRun(35,()->{try{c.assertTrue(f.shots().isEmpty()&&f.body.autonomousTarget().isEmpty(),"creative player cannot be selected or fired at");}finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-damaged",tickLimit=180)
    public void missingRealWeaponCellsCannotCreateInvisibleAiFire(TestContext c){admitted(c,f->{for(var p:SkyUfoWeapons.LASERS)f.body.setLocalBlock(p,net.minecraft.block.Blocks.AIR.getDefaultState());for(var p:SkyUfoWeapons.ROCKETS)f.body.setLocalBlock(p,net.minecraft.block.Blocks.AIR.getDefaultState());f.body.setAutopilot(true);c.waitAndRun(35,()->{try{c.assertTrue(f.shots().isEmpty(),"destroyed physical guns have no autonomous substitute");}finally{f.close();}c.complete();});});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-pilot",tickLimit=180)
    public void actualPilotCapturePreemptsAiAndReleaseRestoresTheSameHull(TestContext c){var seen=new HashSet<UUID>();Fixture[] fixture={null};c.runAtEveryTick(()->{if(fixture[0]!=null)for(var shot:fixture[0].shots())seen.add(shot.getUuid());});admitted(c,f->{fixture[0]=f;
        f.body.setAutopilot(true);var id=f.body.getUuid();var velocity=f.body.physics().linearVelocity();var angular=f.body.physics().angularMomentum();f.target.setPosition(f.body.localToWorld(new Vec3d(3.5,.1,.5)));
        c.assertTrue(UfoControlSystem.claim(f.target,f.body)&&!SkyUfoCombat.allowed(f.body),"actual console ownership preempts AI immediately");f.body.fireAutonomousWeapons();c.assertTrue(f.shots().isEmpty(),"capture cannot leak a pending autonomous shot");
        c.assertTrue(UfoControlSystem.release(f.target)&&f.body.autopilotEnabled()&&id.equals(f.body.getUuid())&&velocity.equals(f.body.physics().linearVelocity())&&angular.equals(f.body.physics().angularMomentum()),"release restores AI without replacing the hull or resetting momentum");
        f.target.setPosition(f.body.localToWorld(new Vec3d(-2.5,-.4,44)));f.target.setVelocity(Vec3d.ZERO);c.waitAndRun(35,()->{try{c.assertTrue(!seen.isEmpty(),"same released hull resumes actual autonomous fire, including shots already impacted by tick 35");}finally{f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-spatial-aim",tickLimit=230)
    public void aHullUsesItsPhysicalThrustersToAimAtAnElevatedRearTarget(TestContext c){admitted(c,f->{
        f.target.setPosition(f.body.localToWorld(new Vec3d(20,24,-32)));var initial=f.body.rotation();var first=initial.rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,0,1));var wanted=f.target.getBoundingBox().getCenter().subtract(f.body.physicsCentre()).normalize();double before=first.x()*wanted.x+first.y()*wanted.y+first.z()*wanted.z;f.body.setAutopilot(true);
        c.waitAndRun(1,()->c.assertTrue(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(initial,f.body.rotation()))>.999,"one physical tick cannot snap the hull to its target"));
        c.waitAndRun(80,()->{try{var forward=f.body.rotation().rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,0,1));var goal=f.target.getBoundingBox().getCenter().subtract(f.body.physicsCentre()).normalize();double after=forward.x()*goal.x+forward.y()*goal.y+forward.z()*goal.z;c.assertTrue(after>before+.3,"finite physical engine torque improves the full XYZ aim toward a rear elevated target: "+before+" -> "+after);}finally{f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-stand-off",tickLimit=330)
    public void anAutonomousEngagementMaintainsSeparationInsteadOfRammingItsTarget(TestContext c){admitted(c,f->{
        f.target.setInvulnerable(true);f.body.setAutopilot(true);
        c.waitAndRun(200,()->{try{double distance=f.body.physicsCentre().distanceTo(f.target.getBoundingBox().getCenter());c.assertTrue(distance>=32&&distance<=42,"physical engagement settles around the stand-off rather than ramming: "+distance);}finally{f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-ai-source-return",tickLimit=140)
    public void aPhysicalOwnerDoesNotMakeItsReturningProjectileImmuneToTheHull(TestContext c){admitted(c,f->{
        var before=com.terminaldetector.drmd.world.contraption.BodySpatialIndex.loaded(c.getWorld()).stream().map(b->b.getUuid()).collect(java.util.stream.Collectors.toSet());ProjectileEntity shot=null;
        try{int cells=f.body.cells().size();var cfg=new com.terminaldetector.drmd.weapon.core.WeaponCore.FireConfig();cfg.owner=f.body;cfg.pos=SkyUfoWeapons.muzzle(f.body,SkyUfoWeapons.LASERS.getFirst()).add(0,0,4);cfg.dir=new Vec3d(0,0,-1);cfg.speedIsSourceUnits=false;cfg.speed=4;cfg.directDamage=200;shot=com.terminaldetector.drmd.weapon.core.WeaponCore.fireProjectile(cfg);for(int i=0;i<3&&!shot.isRemoved();i++)shot.tick();c.assertTrue(shot.isRemoved()&&f.body.cells().size()<cells,"real returning body-owned projectile hits and damages its source geometry");}
        finally{if(shot!=null)shot.discard();for(var b:com.terminaldetector.drmd.world.contraption.BodySpatialIndex.loaded(c.getWorld()))if(!before.contains(b.getUuid()))b.discard();f.close();}c.complete();
    });}
}
