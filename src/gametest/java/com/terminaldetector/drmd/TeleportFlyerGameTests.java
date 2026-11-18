package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Natural production relay behavior and real physical counterplay. No target/state/commit/tick injection. */
public final class TeleportFlyerGameTests implements FabricGameTest {
    private static boolean warning(TeleportFlyerFixture r){
        if(r.actor.ability().phase()==TeleportAbilityState.Phase.WARN&&r.record().warningTick!=Long.MIN_VALUE)return true;
        r.c.assertTrue(r.ticks<80,"natural full-loadout flyer acquires own target and publishes actual admitted warning: "+r.state());return false;
    }
    private static void budgets(TeleportFlyerFixture r){var s=TeleportAbilityRelay.stats(r.world);
        r.c.assertTrue(s.pending()<=16&&s.maxPending()<=16&&s.maxGranted()<=8&&s.queries()<=48&&s.maxVolumes()<=64&&s.maxActors()<=33,"actual shared bounded END work: "+s);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-natural-warning-commit",tickLimit=300)
    public void defaultOwnTargetWarnsForTwentyTicksThenRelocatesAndResumesRealFlightFire(TestContext c){
        var rig=new TeleportFlyerFixture(c,0,false);rig.drive(r->{
            budgets(r);var p=r.record();
            if(r.actor.ability().phase()==TeleportAbilityState.Phase.WARN){
                c.assertTrue(p.warned!=null&&p.warned.equals(r.actor.lockedDestination())&&r.actor.displayedDestination().squaredDistanceTo(p.warned)<1e-10,"advertised absolute endpoint stays locked despite body motion");
                c.assertTrue(r.actor.teleportCount()==0,"no early pose commit during visible warning");
            }
            if(p.commitTick==Long.MIN_VALUE){c.assertTrue(r.ticks<110,"actual END commit occurs after natural warning: "+r.state());return;}
            c.assertTrue(p.commitTick-p.warningTick==20&&p.arrived.squaredDistanceTo(p.warned)<1e-10&&r.actor.teleportCount()==1,"one genuine same-entity admitted discontinuity after full20normal ticks");
            c.assertTrue(Math.abs(p.afterEnergy-(Math.min(100,p.beforeEnergy+1)-20))<1e-4,"one20energy debit after actual normal regen, no extra transaction cost");
            c.assertTrue(p.preCommitVelocity.squaredDistanceTo(p.commitVelocity)<1e-10,"ordinary braked control retains its current velocity across teleport");
            if(!(r.ownAcquired&&r.movingShot&&r.actualDamage&&r.actor.getPos().squaredDistanceTo(p.arrived)>.04)&&r.ticks<180)return;
            c.assertTrue(r.ownAcquired&&r.movingShot&&r.actualDamage&&r.actor.getPos().squaredDistanceTo(p.arrived)>.04,"finite recovery resumes real default motion/owned moving projectile/attributed health damage: "+r.state());
            c.assertTrue(p.dropped==0,"complete bounded actual phase evidence");System.out.println("U07_NATURAL_PASS "+r.state()+" warn="+p.warningTick+" commit="+p.commitTick+" energyBefore="+p.beforeEnergy+" after="+p.afterEnergy);r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-real-hit-interrupt",tickLimit=240)
    public void aRealMovingProjectileConsumesDefenseAndInterruptsRatherThanBeingEvaded(TestContext c){
        var rig=new TeleportFlyerFixture(c,512,false);var player=c.createMockCreativeServerPlayerInWorld();player.setPosition(rig.start.add(-8,0,0));rig.extra.add(player);
        ProjectileEntity[] round={null};float[] shield={0};boolean[] moved={false};
        rig.drive(r->{
            budgets(r);if(round[0]==null){if(!warning(r))return;shield[0]=r.actor.getDroneShield();var cfg=new WeaponCore.FireConfig();cfg.owner=player;cfg.pos=r.actor.getBoundingBox().getCenter().add(-4,0,0);cfg.dir=new Vec3d(1,0,0);cfg.speedIsSourceUnits=false;cfg.speed=2;cfg.directDamage=4;cfg.dmgClass=DamageClass.KINETIC;cfg.life=2;
                round[0]=WeaponCore.fireProjectile(cfg);c.assertTrue(round[0]!=null,"actual world projectile emitted");rig.extra.add(round[0]);return;}
            moved[0]|=round[0].age>0;
            if(r.actor.getDroneShield()==shield[0]&&r.ticks<80)return;
            c.assertTrue(moved[0]&&r.actor.getDroneShield()<shield[0]&&r.actor.isAlive()&&r.actor.teleportCount()==0
                &&r.actor.ability().phase()==TeleportAbilityState.Phase.RECOVERY&&r.actor.lockedDestination()==null,"actual physical hit consumes defense, cancels warning and cannot be used as teleport immunity: "+r.state());r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-material-exit",tickLimit=250)
    public void realMaterialPlacedAtTheWarnedExitRefusesWithoutEnergyOrRelocation(TestContext c){
        var rig=new TeleportFlyerFixture(c,1024,false);boolean[] placed={false};
        rig.drive(r->{
            budgets(r);if(!placed[0]){if(!warning(r))return;var point=BlockPos.ofFloored(r.actor.lockedDestination().add(0,.5,0));
                c.assertTrue(r.world.getBlockState(point).isAir(),"new endpoint obstruction begins as empty fixture air");r.block(point,Blocks.IRON_BLOCK.getDefaultState());placed[0]=true;
                c.assertTrue(r.actor.canSee(r.enemy),"this is endpoint blockage, not target concealment");return;}
            if(r.actor.ability().phase()!=TeleportAbilityState.Phase.RECOVERY&&r.ticks<100)return;
            c.assertTrue(r.actor.teleportCount()==0&&r.actor.getModEnergy()==100&&r.actor.ability().phase()==TeleportAbilityState.Phase.RECOVERY
                &&TeleportAbilityRelay.stats(r.world).lastAdmission().equals("TERRAIN"),"fresh full-volume terrain authority refuses the warned exit before cost/pose: "+r.state());r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-actor-cap-exit",tickLimit=250)
    public void thirtyThreeRealLivingOccupantsTriggerTheBoundedRefusalSentinel(TestContext c){
        var rig=new TeleportFlyerFixture(c,1536,false);boolean[] filled={false};
        rig.drive(r->{
            budgets(r);if(!filled[0]){if(!warning(r))return;var point=r.actor.lockedDestination();
                for(int i=0;i<33;i++){var stand=EntityType.ARMOR_STAND.create(r.world);stand.setPosition(point);stand.setNoGravity(true);c.assertTrue(r.world.spawnEntity(stand),"actual living occupancy fixture");r.extra.add(stand);}filled[0]=true;return;}
            if(r.actor.ability().phase()!=TeleportAbilityState.Phase.RECOVERY&&r.ticks<100)return;
            var s=TeleportAbilityRelay.stats(r.world);c.assertTrue(r.actor.teleportCount()==0&&r.actor.getModEnergy()==100&&s.maxActors()==33&&s.lastAdmission().equals("ACTOR_CAP"),"33rdcandidate is bounded refusal, never permission or an AIR fallback: "+r.state());r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-moving-oriented-exit",tickLimit=280)
    public void aRealRotatingMovingHullAtTheLockedExitIsCheckedInTheFreshEndPhase(TestContext c){
        var rig=new TeleportFlyerFixture(c,2048,true);BlockBodyEntity[] hull={null};Vec3d[] original={null};
        rig.drive(r->{
            budgets(r);if(hull[0]==null){if(!warning(r))return;double seconds=r.actor.ability().remaining()/20.0;var velocity=new Vec3d(.5,0,0);
                var centre=r.actor.lockedDestination().add(0,r.actor.getHeight()*.5,0).subtract(velocity.multiply(seconds));var q=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.7);var offset=q.rotate(new Vec3(.5,.5,.5));
                hull[0]=BlockBodyEntity.createLocal(r.world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),centre.add(-offset.x(),-offset.y(),-offset.z()),q,velocity,new Vec3d(0,.3,0));
                hull[0].setPhysicsGravity(false);original[0]=hull[0].getPos();r.extra.add(hull[0]);return;}
            if(r.actor.ability().phase()!=TeleportAbilityState.Phase.RECOVERY&&r.ticks<110)return;
            var s=TeleportAbilityRelay.stats(r.world);c.assertTrue(hull[0].hasSimulatedPhysics()&&hull[0].getPos().squaredDistanceTo(original[0])>.01,"actual hull advances under normal engine physics, no following rewrite");
            c.assertTrue(r.actor.teleportCount()==0&&r.actor.getModEnergy()==100&&s.lastAdmission().equals("HULL"),"current oriented material contacts, not stale pose or proxy AABB, prevent final teleport: "+r.state());r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-real-target-cover",tickLimit=240)
    public void realCoverConcealsTheTargetAndCancelsWithoutHiddenEndpointRetargeting(TestContext c){
        var rig=new TeleportFlyerFixture(c,2560,false);boolean[] enclosed={false};Vec3d[] marker={null};
        rig.drive(r->{
            budgets(r);if(!enclosed[0]){if(!warning(r))return;marker[0]=r.actor.lockedDestination();var centre=r.enemy.getBlockPos();var cells=new ArrayList<BlockPos>();
                for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=1;z++)if(Math.abs(x)==1||Math.abs(z)==1||y==3)cells.add(centre.add(x,y,z));
                c.assertTrue(cells.size()==33&&cells.size()<=48,"bounded33cell real target-cover event");
                for(var at:cells){c.assertTrue(com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(r.world,at,r.actor),"loaded protected fixture admission before target-cover state read");c.assertTrue(r.world.getBlockState(at).isAir()&&!new Box(at).intersects(r.enemy.getBoundingBox().contract(1e-6)),"no actor clipping or protected-state replacement");r.block(at,Blocks.BEDROCK.getDefaultState());}
                c.assertTrue(!r.actor.canSee(r.enemy),"actual material blocks target LOS");enclosed[0]=true;return;}
            if(r.actor.ability().phase()!=TeleportAbilityState.Phase.RECOVERY&&r.ticks<80)return;
            c.assertTrue(marker[0]!=null&&r.actor.teleportCount()==0&&r.actor.getModEnergy()==100&&r.actor.ability().phase()==TeleportAbilityState.Phase.RECOVERY&&r.actor.lockedDestination()==null,"lost own LOS cancels rather than tracing hidden current coordinates to another exit: "+r.state());r.complete();
        });
    }
}
