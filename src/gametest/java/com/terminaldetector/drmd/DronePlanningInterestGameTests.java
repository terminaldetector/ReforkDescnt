package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;
import java.util.function.Consumer;

/** Real ordinary goal ticks, exact XYZ presence and a genuine shield-only projectile impact. */
public final class DronePlanningInterestGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;final AiFixtureChunks lease;final DroneEntity drone;final CowEntity cow;final ServerPlayerEntity p;final EmbeddedChannel channel;
        final Vec3d at;final long began;boolean closed;
        Rig(TestContext c,AiFixtureChunks lease,Vec3d at){
            this.c=c;this.lease=lease;this.at=at;began=c.getWorld().getTime();var w=c.getWorld();
            var client=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"interest-presence"),false);p=new ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);w.getServer().getPlayerManager().onPlayerConnect(connection,p,client);
            p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);DescentPlayerData.get(p).setEnabled(false);p.setPosition(at.add(0,-180,0));
            drone=ModEntities.ORB.create(w);cow=EntityType.COW.create(w);c.assertTrue(drone!=null&&cow!=null,"actual registered actors");
            drone.applyRole(AiRole.ASSAULT);drone.setPosition(at);drone.setPersistent();cow.setPosition(at.add(52,0,0));cow.setAiDisabled(true);cow.setNoGravity(true);
            c.assertTrue(w.spawnEntity(drone)&&w.spawnEntity(cow),"natural target and flyer admitted without target assignment");
        }
        boolean coasting(){return drone.getTarget()==cow&&drone.coastingUpdates()>0&&drone.steeringUpdates()>0&&DroneFlightDetail.planningTier(drone)==DroneFlightDetail.PlanningTier.REMOTE;}
        void counters(){var s=DroneSwarmAi.indexStats(c.getWorld());c.assertTrue(s.fullWorldScans()==1&&s.snapshotBuilds()>0&&s.indexedSamples()>0,"one actual bootstrap scan and subsequent event-fed per-tick live positions; "+s);}
        @Override public void close(){if(closed)return;closed=true;for(var shot:c.getWorld().getEntitiesByClass(ProjectileEntity.class,drone.getBoundingBox().expand(100),e->drone.getUuid().equals(e.getOwnerUuid())||p.getUuid().equals(e.getOwnerUuid())))shot.discard();drone.discard();cow.discard();c.getWorld().getServer().getPlayerManager().remove(p);p.discard();channel.finishAndReleaseAll();lease.close();}
        void done(){close();c.complete();}
    }
    private static void admitted(TestContext c,Consumer<Rig> tick){
        var at=c.getAbsolutePos(new BlockPos(8,220,8));var lease=new AiFixtureChunks(c.getWorld(),at.add(24,0,0),3);Rig[] rig={null};int[] wait={0};
        c.runAtEveryTick(()->{try{
            if(rig[0]==null){if(!lease.entityTicking(at,at.add(52,0,0),at.add(0,-180,0))){if(++wait[0]<80)return;lease.close();c.assertTrue(false,"actual entity-ticking XYZ fixture required");return;}rig[0]=new Rig(c,lease,Vec3d.of(at));}
            var r=rig[0];if(r.closed)return;c.getWorld().resetIdleTimeout();c.assertTrue(c.getWorld().getTime()-r.began<120,"real remote planning/wake outcome deadline");tick.accept(r);
        }catch(Throwable failure){if(rig[0]!=null)rig[0].close();else lease.close();throw failure;}});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-interest-vertical",tickLimit=220)
    public void remotePhysicalCoastingWakesForAnActualSurvivalTargetEnteringFortyBlocksAbove(TestContext c){
        int[] phase={0};long[] steering={0};Vec3d[] initial={null};
        admitted(c,r->{
            if(phase[0]==0){if(!r.coasting())return;r.counters();c.assertTrue(r.drone.getPos().distanceTo(r.at)>.01,"far planning reduction retains genuine physical travel");
                steering[0]=r.drone.steeringUpdates();r.p.setPosition(r.drone.getPos().add(0,40,0));initial[0]=r.drone.getPos();phase[0]=1;return;}
            c.assertTrue(DroneFlightDetail.planningTier(r.drone)==DroneFlightDetail.PlanningTier.FULL,"pure vertical proximity wakes immediately; no horizontal-only interest band");
            if(r.drone.getTarget()!=r.p||r.drone.steeringUpdates()<=steering[0])return;
            c.assertTrue(r.p.isAlive()&&r.drone.getPos().distanceTo(initial[0])>.001,"normal3D target selector and ordinary steering actually run after wake");r.counters();r.done();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-interest-shield",tickLimit=220)
    public void anActualShieldAbsorbedRoundWakesRemotePlanningWithoutChangingHullOrVanillaHurtTime(TestContext c){
        int[] phase={0};float[] shield={0},health={0};long[] steering={0};UUID[] incoming={null};
        admitted(c,r->{
            if(phase[0]==0){if(!r.coasting())return;r.counters();shield[0]=r.drone.getDroneShield();health[0]=r.drone.getHealth();steering[0]=r.drone.steeringUpdates();
                c.assertTrue(shield[0]>=1&&r.drone.hurtTime==0,"genuine untouched shield prerequisite");var centre=r.drone.getBoundingBox().getCenter();var from=centre.add(0,0,3);var velocity=r.drone.getVelocity();
                var aim=InterceptAim.solve(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(centre.x-from.x,centre.y-from.y,centre.z-from.z),new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(velocity.x,velocity.y,velocity.z),4,40).orElseThrow();
                var cfg=new WeaponCore.FireConfig();cfg.owner=r.p;cfg.pos=from;var d=aim.direction();cfg.dir=new Vec3d(d.x(),d.y(),d.z());cfg.speedIsSourceUnits=false;cfg.speed=4;cfg.life=2;cfg.directDamage=1;cfg.dmgClass=DamageClass.KINETIC;
                var shot=WeaponCore.fireProjectile(cfg);c.assertTrue(shot!=null,"one real projectile, no direct damage/wake call");incoming[0]=shot.getUuid();phase[0]=1;return;
            }
            if(r.drone.getDroneShield()>=shield[0])return;
            c.assertTrue(r.drone.getHealth()==health[0]&&r.drone.hurtTime==0,"actual shield absorption leaves hull/vanilla hurt cadence unchanged");
            c.assertTrue(DroneFlightDetail.planningTier(r.drone)==DroneFlightDetail.PlanningTier.FULL&&r.drone.planningWakeUntil()>c.getWorld().getTime(),"real shield impact wakes planning even though vanilla hurtTime remained0");
            if(r.drone.steeringUpdates()<=steering[0])return;c.assertTrue(c.getWorld().getEntity(incoming[0])==null||c.getWorld().getEntity(incoming[0]).isRemoved(),"actual round terminates on its real material target");r.counters();r.done();
        });
    }
}
