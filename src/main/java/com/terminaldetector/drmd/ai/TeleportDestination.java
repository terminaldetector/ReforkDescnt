package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.physics.PhysicsTarget;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.entity.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import java.util.*;

/** Full-volume authority at the actual END phase. No AIR/isSpaceEmpty or stale-hull fallback. */
public final class TeleportDestination {
    public static final int MAX_VOLUMES=64,MAX_HULLS=32,MAX_ACTORS=32;
    public enum Reason { CLEAR,THREAD,BOUNDS,UNAVAILABLE,TERRAIN,HULL_CAP,STALE_HULL,HULL,ACTOR_CAP,ACTOR }
    public record Result(Reason reason,int volumes,int hulls,int actors){public boolean clear(){return reason==Reason.CLEAR;}}
    private TeleportDestination(){}
    public static Result query(ServerWorld world,Entity actor,Vec3d destination){
        if(!world.getServer().isOnThread()||!CubicAvailability.onWorldThread(world)||actor.getWorld()!=world)return new Result(Reason.THREAD,0,0,0);
        if(!Double.isFinite(destination.lengthSquared()))return new Result(Reason.BOUNDS,0,0,0);
        Box box=actor.getBoundingBox().offset(destination.subtract(actor.getPos())).contract(1e-7);
        if(!Double.isFinite(box.minX*box.minX+box.minY*box.minY+box.minZ*box.minZ+box.maxX*box.maxX+box.maxY*box.maxY+box.maxZ*box.maxZ))return new Result(Reason.BOUNDS,0,0,0);
        if(box.minY<world.getBottomY()||box.maxY>world.getTopY()||!world.getWorldBorder().contains(box))return new Result(Reason.BOUNDS,0,0,0);
        Box halo=box.expand(1);int x0=MathHelper.floor(halo.minX)>>4,x1=MathHelper.floor(halo.maxX)>>4;
        int y0=MathHelper.floor(halo.minY)>>4,y1=MathHelper.floor(halo.maxY)>>4,z0=MathHelper.floor(halo.minZ)>>4,z1=MathHelper.floor(halo.maxZ)>>4;
        long volume=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);if(volume>MAX_VOLUMES)return new Result(Reason.UNAVAILABLE,0,0,0);
        var access=WorldAccess.of(world);int checked=0;
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){
            checked++;if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return new Result(Reason.UNAVAILABLE,checked,0,0);
        }
        // Every collision-shape neighbour was admitted before this actual terrain query.
        if(world.getBlockCollisions(actor,box).iterator().hasNext())return new Result(Reason.TERRAIN,checked,0,0);
        var hulls=BodySpatialIndex.query(world,box);if(hulls.size()>MAX_HULLS)return new Result(Reason.HULL_CAP,checked,hulls.size(),0);
        var shape=OrientedBox.axisAligned(new BodyBounds(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)));
        for(var hull:hulls){
            if(hull.boardingMotionPaused()||hull.getWorld()!=world||hull.isRemoved()||world.getEntity(hull.getUuid())!=hull
                ||BodySpatialIndex.find(world,hull.getId()).orElse(null)!=hull)return new Result(Reason.STALE_HULL,checked,hulls.size(),0);
            if(hull.collisionQuery().contacts(shape,0).stream().anyMatch(hit->hit.penetration()>1e-7))return new Result(Reason.HULL,checked,hulls.size(),0);
        }
        var actors=new ArrayList<Entity>();world.collectEntitiesByType(TypeFilter.instanceOf(Entity.class),box,
            other->other!=actor&&!(other instanceof BlockBodyEntity)&&(other instanceof LivingEntity||other instanceof PhysicsTarget)
                &&other.isAlive()&&!other.isRemoved()&&!other.isSpectator(),actors,MAX_ACTORS+1);
        if(actors.size()>MAX_ACTORS)return new Result(Reason.ACTOR_CAP,checked,hulls.size(),actors.size());
        for(var other:actors)if(other.getBoundingBox().intersects(box))return new Result(Reason.ACTOR,checked,hulls.size(),actors.size());
        return new Result(Reason.CLEAR,checked,hulls.size(),actors.size());
    }
}
