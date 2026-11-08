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
    private UUID target;private long nextSearch,prepared=Long.MIN_VALUE,lastFire=Long.MIN_VALUE;
    public static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{for(var body:BodySpatialIndex.loaded(w))if(body instanceof SkyUfoEntity ufo)ufo.fireAutonomousWeapons();});}
    public static boolean allowed(SkyUfoEntity b){return b.getWorld() instanceof ServerWorld w&&w.getServer().isOnThread()&&!b.isRemoved()&&b.hasPoweredReactor()&&b.autopilotEnabled()&&!UfoControlSystem.hasPilot(b)&&w.getDifficulty()!=Difficulty.PEACEFUL&&BodySpatialIndex.find(w,b.getId()).orElse(null)==b;}
    private static boolean eligible(SkyUfoEntity b,ServerPlayerEntity p){return p!=null&&p.getWorld()==b.getWorld()&&!p.isRemoved()&&p.isAlive()&&(p.interactionManager.getGameMode()==GameMode.SURVIVAL||p.interactionManager.getGameMode()==GameMode.ADVENTURE)&&!b.containsPos(p.getPos())&&p.getPos().squaredDistanceTo(b.physicsCentre())<=96*96;}
    public ServerPlayerEntity target(SkyUfoEntity b){return target==null?null:com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.players((ServerWorld)b.getWorld()).stream().filter(p->p.getUuid().equals(target)).findFirst().orElse(null);}
    public Vec3 prepare(SkyUfoEntity b){
        prepared=b.getWorld().getTime();if(!allowed(b)){target=null;return null;}
        var current=target(b);if(!eligible(b,current)){target=null;current=null;}
        if(current==null&&prepared>=nextSearch){nextSearch=prepared+10;current=com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.players((ServerWorld)b.getWorld()).stream().filter(p->eligible(b,p)).min(Comparator.<ServerPlayerEntity>comparingDouble(p->p.getPos().squaredDistanceTo(b.physicsCentre())).thenComparing(p->p.getUuid().toString())).orElse(null);target=current==null?null:current.getUuid();}
        if(current==null)return null;
        // Hold a battery choice long enough for finite-inertia hull aiming. Parallel
        // nose mounts cannot all converge on a small target by changing shot direction.
        if((prepared/80)%2==1)for(var slot:SkyUfoWeapons.ROCKETS)if(b.getLocalBlock(slot).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_ROCKET))return aim(b,slot,current,1.4,100);
        for(var slot:SkyUfoWeapons.LASERS)if(b.getLocalBlock(slot).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_LASER))return aim(b,slot,current,2.5,80);
        for(var slot:SkyUfoWeapons.ROCKETS)if(b.getLocalBlock(slot).isOf(com.terminaldetector.drmd.entity.ModWorldBlocks.SHIP_ROCKET))return aim(b,slot,current,1.4,100);
        return null;
    }
    public static Vec3 aim(BlockBodyEntity launcher,BlockPos slot,ServerPlayerEntity p,double speed,double life){
        var muzzle=SkyUfoWeapons.muzzle(launcher,slot);var r=p.getBoundingBox().getCenter().subtract(muzzle);var v=p.getVelocity().subtract(launcher.surfaceVelocityAt(muzzle).multiply(.05));
        return InterceptAim.solve(new Vec3(r.x,r.y,r.z),new Vec3(v.x,v.y,v.z),speed,life).map(InterceptAim.Solution::direction).orElse(null);
    }
    public boolean ready(SkyUfoEntity b){return allowed(b)&&prepared==b.getWorld().getTime()&&lastFire!=prepared&&b.hasSimulatedPhysics()&&!b.boardingMotionPaused()&&eligible(b,target(b));}
    public void fired(SkyUfoEntity b){lastFire=b.getWorld().getTime();}
    public static boolean clearShot(BlockBodyEntity launcher,BlockPos slot,ServerPlayerEntity target,double speed,double life){
        var from=SkyUfoWeapons.muzzle(launcher,slot);var f=launcher.rotation().rotate(new Vec3(0,0,1));var launch=new Vec3d(f.x(),f.y(),f.z()).multiply(speed).add(launcher.surfaceVelocityAt(from).multiply(.05));
        var relative=launch.subtract(target.getVelocity());var r=target.getBoundingBox().getCenter().subtract(from);double length=relative.lengthSquared();if(length<1e-12)return false;
        double t=r.dotProduct(relative)/length;if(t<=0||t>life)return false;var end=from.add(launch.multiply(t));
        if(!target.getBoundingBox().offset(target.getVelocity().multiply(t)).expand(.25).contains(end))return false;
        var world=launcher.getWorld();if(!WorldEditSafety.loadedSegment(world,from,end))return false;
        if(world.raycast(new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,launcher)).getType()!=HitResult.Type.MISS)return false;
        var area=new Box(from,end).expand(.3);var bodies=BodySpatialIndex.query(world,area);if(bodies.size()>32)return false;
        for(var b:bodies)if(b.physicsRaycast(from,end).isPresent())return false;
        for(var e:world.getOtherEntities(launcher,area,e->e instanceof LivingEntity&&e!=target&&e.isAlive()&&!e.isSpectator()))if(e.getBoundingBox().expand(.3).raycast(from,end).isPresent())return false;
        return true;
    }
}
