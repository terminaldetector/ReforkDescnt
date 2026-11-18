package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.entity.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Read-only destination preflight. Only the mapped remainder is swept, never the space between portals. */
final class PortalDestinationCollision {
    record Shape(OrientedBox start,OrientedBox end) {}
    private PortalDestinationCollision(){}
    static Box box(BodyBounds bounds){return new Box(bounds.min().x(),bounds.min().y(),bounds.min().z(),bounds.max().x(),bounds.max().y(),bounds.max().z());}
    static BodyBounds bounds(Box b){return new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ));}
    static boolean available(ServerWorld world,Box area){
        if(!world.getServer().isOnThread()||!Double.isFinite(area.minX+area.minY+area.minZ+area.maxX+area.maxY+area.maxZ)
            ||area.minY<world.getBottomY()||area.maxY>world.getTopY()||!world.getWorldBorder().contains(area))return false;
        int x0=MathHelper.floor(area.minX-1)>>4,x1=MathHelper.floor(area.maxX+1)>>4;
        int y0=MathHelper.floor(area.minY-1)>>4,y1=MathHelper.floor(area.maxY+1)>>4;
        int z0=MathHelper.floor(area.minZ-1)>>4,z1=MathHelper.floor(area.maxZ+1)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>216)return false;
        var access=WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
            if(com.terminaldetector.drmd.world.access.CubicAvailability.withinHeight(world,new D6ChunkPos(x,y,z))&&!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    static boolean clear(ServerWorld world,Entity owner,List<Shape> destination,Vec3 remainder,Set<Entity> moving){
        for(var shapePair:destination){
            var start=shapePair.start();var end=shapePair.end();
            Box swept=box(start.bounds().union(end.bounds()));
            if(!available(world,swept))return false;
            for(var shape:world.getBlockCollisions(owner,swept.contract(1e-7)))for(var obstacle:shape.getBoundingBoxes())
                if(hit(start,end,remainder,OrientedBox.axisAligned(bounds(obstacle))))return false;
            for(var other:world.getOtherEntities(owner,swept,e->!moving.contains(e)&&!(e instanceof BlockBodyEntity)&&!e.isSpectator()
                    &&(e.isCollidable()||e instanceof LivingEntity||e instanceof com.terminaldetector.drmd.entity.RagdollEntity)))
                if(hit(start,end,remainder,OrientedBox.axisAligned(bounds(other.getBoundingBox()))))return false;
            for(var hull:BodySpatialIndex.query(world,swept))if(!moving.contains(hull))
                for(var local:hull.collider().query(OrientedBox.local(bounds(swept),hull.localCentre(),hull.massPosition(),hull.rotation().inverse()).bounds()))
                    if(hit(start,end,remainder,OrientedBox.local(local,hull.massPosition(),hull.localCentre(),hull.rotation())))return false;
        }
        return true;
    }
    private static boolean hit(OrientedBox start,OrientedBox end,Vec3 displacement,OrientedBox obstacle){
        if(end.contact(obstacle,0).filter(c->c.penetration()>1e-6).isPresent())return true;
        if(start.contact(obstacle,0).filter(c->c.penetration()>1e-6).isPresent())return true;
        return displacement.lengthSquared()>1e-14&&start.sweep(obstacle,displacement,new Vec3(0,0,0))
            .filter(c->c.fraction()<1-1e-7).isPresent();
    }
}
