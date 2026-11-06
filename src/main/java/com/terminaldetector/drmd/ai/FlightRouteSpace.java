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
    FlightRouteSpace(DroneEntity drone){
        actor=drone;world=actor.getWorld();var actual=actor.getBoundingBox();var centre=actual.getCenter();
        var aligned=Vec3d.ofCenter(BlockPos.ofFloored(centre));var chosen=aligned;
        // The nearest world-grid point may lie inside a wall even while the actual actor only touches it.
        // Choose a swept-clear neighbouring grid point; keep the existing grid and fail closed if none fits.
        if(!clear(actual,aligned.subtract(centre))){
            var choices=new java.util.ArrayList<Vec3d>(26);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0)choices.add(aligned.add(x,y,z));
            choices.sort(java.util.Comparator.<Vec3d>comparingDouble(p->p.squaredDistanceTo(centre)).thenComparingDouble(p->p.x).thenComparingDouble(p->p.y).thenComparingDouble(p->p.z));
            for(var candidate:choices)if(clear(actual,candidate.subtract(centre))){chosen=candidate;break;}
        }
        origin=chosen;initial=actual.offset(origin.subtract(centre));
    }
    Vec3d point(BoundedFlightPath.Cell c){return origin.add(c.x(),c.y(),c.z());}
    /** Local combat probe only. Unknown/distant volumes never initiate a search or load chunks. */
    static boolean occluded(DroneEntity drone,Vec3d target){
        Entity actor=drone;World world=actor.getWorld();var from=actor.getBoundingBox().getCenter();
        if(!CubicAvailability.onWorldThread(world)||!Double.isFinite(target.squaredDistanceTo(from))||target.squaredDistanceTo(from)>32*32)return false;
        var access=WorldAccess.of(world);
        if(com.terminaldetector.drmd.world.edit.LoadedCubeRay.fraction(from.x,from.y,from.z,target.x,target.y,target.z,(x,y,z)->access.isVolumeAvailable(new D6ChunkPos(x,y,z)))<1)return false;
        if(world.raycast(new net.minecraft.world.RaycastContext(from,target,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,actor)).getType()==net.minecraft.util.hit.HitResult.Type.BLOCK)return true;
        var bodies=BodySpatialIndex.query(world,new Box(from,target).expand(1e-6));if(bodies.size()>32)return false;
        for(var body:bodies)if(body.raycast(from,target).isPresent())return true;
        return false;
    }
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
