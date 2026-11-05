package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Loaded-only swept clearance. Routes are advisory; normal entity/hull collisions remain authoritative. */
final class FlightRouteSpace implements BoundedFlightPath.Space {
    private final Entity actor;
    private final World world;
    private final Vec3d origin;
    private final Box initial;
    FlightRouteSpace(DroneEntity drone){actor=drone;world=actor.getWorld();var actual=actor.getBoundingBox();origin=Vec3d.ofCenter(BlockPos.ofFloored(actual.getCenter()));initial=actual.offset(origin.subtract(actual.getCenter()));}
    Vec3d point(BoundedFlightPath.Cell c){return origin.add(c.x(),c.y(),c.z());}
    @Override public boolean clear(BoundedFlightPath.Cell a,BoundedFlightPath.Cell b) {
        return clear(initial.offset(a.x(),a.y(),a.z()),point(b).subtract(point(a)));
    }
    boolean clearActual(Vec3d displacement){return clear(actor.getBoundingBox(),displacement);}
    private boolean clear(Box from,Vec3d delta) {
        if(!CubicAvailability.onWorldThread(world)||!Double.isFinite(delta.lengthSquared())||delta.lengthSquared()>16)return false;
        Box swept=from.union(from.offset(delta)).contract(1e-6),halo=swept.expand(1);
        if(swept.minY<world.getBottomY()||swept.maxY>world.getTopY()||!world.getWorldBorder().contains(swept))return false;
        var access=WorldAccess.of(world);
        for(int x=MathHelper.floor(halo.minX)>>4;x<=MathHelper.floor(halo.maxX)>>4;x++)
            for(int y=MathHelper.floor(halo.minY)>>4;y<=MathHelper.floor(halo.maxY)>>4;y++)
                for(int z=MathHelper.floor(halo.minZ)>>4;z<=MathHelper.floor(halo.maxZ)>>4;z++)
                    if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        if(world.getBlockCollisions(actor,swept).iterator().hasNext())return false;
        var bodies=BodySpatialIndex.query(world,swept);if(bodies.size()>32)return false;
        var centre=from.getCenter();var half=new Vec3(from.getLengthX()/2,from.getLengthY()/2,from.getLengthZ()/2);
        var box=new OrientedBox(new Vec3(centre.x,centre.y,centre.z),half,Quat.IDENTITY);
        var move=new Vec3(delta.x,delta.y,delta.z);
        for(var body:bodies)if(body.collisionQuery().sweep(box,move,new Vec3(0,0,0),true).isPresent())return false;
        return true;
    }
}
