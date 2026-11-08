package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.weapon.core.InterceptAim;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Reference-UFO weapon controller; target intent never assigns physical poses or velocities. */
public final class SkyUfoCombat {
    private UUID target;private long nextSearch,prepared=Long.MIN_VALUE,lastFire=Long.MIN_VALUE,selectedSince;private boolean rocketBattery;private int burst;
    private boolean modulePhase,moduleAim;private int moduleLasers,moduleRockets;private long moduleSince;
    public static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{for(var body:BodySpatialIndex.loaded(w))if(body instanceof SkyUfoEntity ufo)ufo.fireAutonomousWeapons();});}
    public static boolean allowed(SkyUfoEntity b){return b.getWorld() instanceof ServerWorld w&&w.getServer().isOnThread()&&!b.isRemoved()&&b.hasPoweredReactor()&&b.autopilotEnabled()&&!UfoControlSystem.hasPilot(b)&&w.getDifficulty()!=Difficulty.PEACEFUL&&BodySpatialIndex.find(w,b.getId()).orElse(null)==b;}
    private static boolean eligible(SkyUfoEntity b,ServerPlayerEntity p){return p!=null&&p.getWorld()==b.getWorld()&&!p.isRemoved()&&p.isAlive()&&(p.interactionManager.getGameMode()==GameMode.SURVIVAL||p.interactionManager.getGameMode()==GameMode.ADVENTURE)&&!b.containsPos(p.getPos())&&p.getPos().squaredDistanceTo(b.physicsCentre())<=96*96;}
    public ServerPlayerEntity target(SkyUfoEntity b){return target==null?null:com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.players((ServerWorld)b.getWorld()).stream().filter(p->p.getUuid().equals(target)).findFirst().orElse(null);}
    public Vec3 prepare(SkyUfoEntity b){
        moduleAim=false;prepared=b.getWorld().getTime();if(!allowed(b)){target=null;return null;}
        var current=target(b);if(!eligible(b,current)){target=null;current=null;}
        if(current==null&&prepared>=nextSearch){nextSearch=prepared+10;current=com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.players((ServerWorld)b.getWorld()).stream().filter(p->eligible(b,p)).min(Comparator.<ServerPlayerEntity>comparingDouble(p->p.getPos().squaredDistanceTo(b.physicsCentre())).thenComparing(p->p.getUuid().toString())).orElse(null);target=current==null?null:current.getUuid();rocketBattery=false;modulePhase=false;burst=0;selectedSince=prepared;}
        if(current==null){modulePhase=false;return null;}
        var module=b.turretModule().filter(t->t.functional(b)).orElse(null);
        if(modulePhase&&(module==null||prepared-moduleSince>=160))endModule(prepared);
        if(modulePhase){var aim=module.cooperativeAim(b,current);if(aim!=null){moduleAim=true;return aim;}endModule(prepared);}
        // Complete an actual burst before asking the finite-inertia hull to aim
        // another parallel mount. A wall clock alone can starve a battery forever.
        boolean lasers=SkyUfoWeapons.LASERS.stream().anyMatch(p->b.getLocalBlock(p).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_LASER));
        boolean rockets=SkyUfoWeapons.ROCKETS.stream().anyMatch(p->b.getLocalBlock(p).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_ROCKET));
        if(prepared-selectedSince>=160){rocketBattery=!rocketBattery;burst=0;selectedSince=prepared;if(module!=null){startModule(prepared);moduleAim=true;return module.cooperativeAim(b,current);}}
        if(!lasers)rocketBattery=true;else if(!rockets)rocketBattery=false;
        var selected=preferredAim(b,rocketBattery,current);
        if(selected==null)selected=preferredAim(b,!rocketBattery,current);
        if(selected!=null||module==null)return selected;moduleAim=true;return module.cooperativeAim(b,current);
    }
    Vec3 aimReference(SkyUfoEntity root,Vec3 direction){
        var head=moduleAim?root.turretModule().flatMap(t->t.body(root)).orElse(null):null;
        return head==null?root.rotation().rotate(new Vec3(0,0,1)):com.terminaldetector.drmd.weapon.core.HingeAim.swingReference(direction,root.rotation().rotate(new Vec3(0,1,0)),head.rotation().rotate(new Vec3(0,0,1)));
    }
    private static Vec3 preferredAim(SkyUfoEntity b,boolean rockets,ServerPlayerEntity target){
        var slots=rockets?SkyUfoWeapons.ROCKETS:SkyUfoWeapons.LASERS;var block=rockets?com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_ROCKET:com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_LASER;double speed=rockets?1.4:2.5,life=rockets?100:80;Vec3 fallback=null;
        for(var slot:slots){
            if(!b.getLocalBlock(slot).isOf(block))continue;var solution=intercept(b,slot,target,speed,life);if(solution.isEmpty())continue;var s=solution.get();if(fallback==null)fallback=s.direction();
            var from=SkyUfoWeapons.muzzle(b,slot);var d=s.direction();var velocity=new Vec3d(d.x(),d.y(),d.z()).multiply(speed).add(b.surfaceVelocityAt(from).multiply(.05));
            // Intent may turn away from the current hull; actual firing still tests
            // its real barrel and includes the source collider. Prefer an open
            // partner over a gun whose ideal trajectory runs through terrain.
            if(clearLane(b,target,from,from.add(velocity.multiply(s.ticks())),false))return s.direction();
        }
        return fallback;
    }
    public static Vec3 aim(BlockBodyEntity launcher,BlockPos slot,ServerPlayerEntity p,double speed,double life){
        return intercept(launcher,slot,p,speed,life).map(InterceptAim.Solution::direction).orElse(null);
    }
    private static Optional<InterceptAim.Solution> intercept(BlockBodyEntity launcher,BlockPos slot,ServerPlayerEntity p,double speed,double life){
        var muzzle=SkyUfoWeapons.muzzle(launcher,slot);var r=p.getBoundingBox().getCenter().subtract(muzzle);var v=targetVelocity(p).subtract(launcher.surfaceVelocityAt(muzzle).multiply(.05));
        return InterceptAim.solve(new Vec3(r.x,r.y,r.z),new Vec3(v.x,v.y,v.z),speed,life);
    }
    /** Read contact-constrained world motion without changing the player's physics. */
    public static Vec3d targetVelocity(ServerPlayerEntity p){
        var state=com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.state(p);
        if(state!=null&&com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.drives(p)){var v=state.velocity().scaled(.05);return new Vec3d(v.x(),v.y(),v.z());}
        var v=p.getVelocity();
        // Vanilla leaves -0.0784 after an ordinary floor collision. Extrapolating
        // it predicts a stationary target through the floor and prevents firing.
        // Boarding, local gravity, jumps and free 6DoF retain their real XYZ motion.
        if(p.isOnGround()&&v.y<0&&!com.terminaldetector.drmd.DescentPlayerData.get(p).isEnabled()&&!com.terminaldetector.drmd.world.gravity.FootGravitySystem.isActive(p))return new Vec3d(v.x,0,v.z);
        return v;
    }
    public boolean ready(SkyUfoEntity b){return allowed(b)&&prepared==b.getWorld().getTime()&&lastFire!=prepared&&b.hasSimulatedPhysics()&&!b.boardingMotionPaused()&&eligible(b,target(b));}
    public void fired(SkyUfoEntity b){lastFire=b.getWorld().getTime();}
    void emitted(SkyUfoEntity b,boolean rockets,int count){if(!modulePhase&&count>0&&rockets==rocketBattery){burst+=count;if(burst>=(rockets?1:3)){rocketBattery=!rocketBattery;burst=0;selectedSince=b.getWorld().getTime();if(!rockets&&b.turretModule().filter(t->t.functional(b)).isPresent())startModule(selectedSince);}}}
    void moduleEmitted(SkyUfoEntity b,int mask){
        if(!modulePhase||mask==0)return;if((mask&1)!=0)moduleLasers++;if((mask&2)!=0)moduleRockets++;
        var child=b.turretModule().flatMap(t->t.body(b)).orElse(null);
        if(child==null||(moduleLasers>=3||!child.getLocalBlock(SkyUfoTurret.LASER).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_LASER))&&(moduleRockets>=1||!child.getLocalBlock(SkyUfoTurret.ROCKET).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_ROCKET)))endModule(b.getWorld().getTime());
    }
    private void startModule(long now){modulePhase=true;moduleLasers=0;moduleRockets=0;moduleSince=now;}
    private void endModule(long now){modulePhase=false;burst=0;selectedSince=now;}
    public static boolean clearShot(BlockBodyEntity launcher,BlockPos slot,ServerPlayerEntity target,double speed,double life){
        var from=SkyUfoWeapons.muzzle(launcher,slot);var f=launcher.rotation().rotate(new Vec3(0,0,1));var launch=new Vec3d(f.x(),f.y(),f.z()).multiply(speed).add(launcher.surfaceVelocityAt(from).multiply(.05));
        var velocity=targetVelocity(target);var relative=launch.subtract(velocity);var r=target.getBoundingBox().getCenter().subtract(from);double length=relative.lengthSquared();if(length<1e-12)return false;
        double t=r.dotProduct(relative)/length;if(t<=0||t>life)return false;var end=from.add(launch.multiply(t));
        if(!target.getBoundingBox().offset(velocity.multiply(t)).expand(.25).contains(end))return false;
        return clearLane(launcher,target,from,end,true);
    }
    private static boolean clearLane(BlockBodyEntity launcher,ServerPlayerEntity target,Vec3d from,Vec3d end,boolean includeSource){
        var world=launcher.getWorld();if(!WorldEditSafety.loadedSegment(world,from,end))return false;
        if(world.raycast(new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,launcher)).getType()!=HitResult.Type.MISS)return false;
        var area=new Box(from,end).expand(.3);var bodies=BodySpatialIndex.query(world,area);if(bodies.size()>32)return false;
        for(var b:bodies)if((includeSource||b!=launcher)&&b.physicsRaycast(from,end).isPresent())return false;
        for(var e:world.getOtherEntities(launcher,area,e->e instanceof LivingEntity&&e!=target&&e.isAlive()&&!e.isSpectator()))if(e.getBoundingBox().expand(.3).raycast(from,end).isPresent())return false;
        return true;
    }
}
