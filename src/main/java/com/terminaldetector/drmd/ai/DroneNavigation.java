package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.*;

/** Reactive route recovery for a physically blocked drone; unobstructed flight performs no geometry search. */
final class DroneNavigation {
    private static final boolean TRACE=Boolean.getBoolean("drmd.debugDroneNavigation");
    private static final Map<World,RouteWorkBudget> BUDGETS=new WeakHashMap<>();
    private final DroneEntity drone;
    private final Entity actor;
    private Vec3d previous,issued=Vec3d.ZERO,destination;
    private int stalled,index;
    private long retryAt,expires,visibilityAt;
    private Vec3d visibilityTarget,visibilityOrigin;
    private boolean blockedSight,sightRoute;
    private BoundedFlightPath search;
    private FlightRouteSpace space;
    private List<BoundedFlightPath.Cell> route=List.of();
    DroneNavigation(DroneEntity drone){this.drone=drone;actor=drone;}
    private RouteWorkBudget budget(){return BUDGETS.computeIfAbsent(actor.getWorld(),w->new RouteWorkBudget(16,48,8));}
    void stop(){budget().release(actor.getUuid());search=null;space=null;route=List.of();previous=null;issued=Vec3d.ZERO;stalled=0;visibilityTarget=null;sightRoute=false;}
    boolean occluded(Vec3d target){
        long tick=actor.getWorld().getTime();var origin=actor.getPos();
        if(visibilityTarget==null||tick<visibilityAt-4||tick>=visibilityAt||target.squaredDistanceTo(visibilityTarget)>4||origin.squaredDistanceTo(visibilityOrigin)>4){
            blockedSight=FlightRouteSpace.occluded(drone,target);visibilityAt=tick+4;visibilityTarget=target;visibilityOrigin=origin;
        }
        return blockedSight;
    }
    Vec3d steer(Vec3d velocity,Vec3d target){return steer(velocity,target,false);}
    Vec3d steer(Vec3d velocity,Vec3d target,boolean seekSight) {
        if(actor.getWorld().isClient)return velocity;
        var centre=actor.getBoundingBox().getCenter();long tick=actor.getWorld().getTime();
        if(sightRoute&&!seekSight)cancel(tick,0);
        if(previous!=null&&issued.lengthSquared()>1e-5) {
            double progress=centre.subtract(previous).dotProduct(issued.normalize());
            stalled=progress<issued.length()*.5?stalled+1:0;
        }
        if((search!=null||!route.isEmpty())&&(tick>expires||destination.squaredDistanceTo(target)>64))cancel(tick,2);
        Vec3d result=velocity;
        Vec3d separation=DroneSwarmAi.closeSeparation(drone);
        if(!route.isEmpty()) {
            while(index<route.size()&&space.point(route.get(index)).squaredDistanceTo(centre)<.0004)index++;
            // Accept the next waypoint only when the complete swept actor volume can reach it.
            // This avoids making several actors converge exactly on the same grid corner.
            for(int look=0;separation.lengthSquared()>1e-8&&look<2&&index+1<route.size();look++){
                if(!space.clearActual(space.point(route.get(index+1)).subtract(centre)))break;
                index++;
            }
            if(index>=route.size())cancel(tick,20);
            else {
                var delta=space.point(route.get(index)).subtract(centre);
                if(!space.clearActual(delta)){trace("blocked waypoint delta="+delta);cancel(tick,4);}
                else result=delta.normalize().multiply(Math.min(velocity.length(),delta.length()*.4));
            }
        }
        if(search!=null) {
            if(!budget().begin(actor.getUuid(),tick)){cancel(tick,4);previous=centre;issued=velocity;return velocity;}
            search.step(budget().take(actor.getUuid(),tick));
            if(search.status()==BoundedFlightPath.Status.FOUND) {
                route=search.path();trace("found "+route);index=0;search=null;budget().release(actor.getUuid());
            }else if(search.status()!=BoundedFlightPath.Status.SEARCHING){trace("search ended "+search.status()+" visited="+search.visitedCells());cancel(tick,40);}
            result=Vec3d.ZERO;
        }else if(route.isEmpty()&&(stalled>=3||seekSight)&&tick>=retryAt&&target.squaredDistanceTo(centre)>1) {
            if(budget().begin(actor.getUuid(),tick)) {
                destination=target;sightRoute=seekSight;space=new FlightRouteSpace(drone);
                var anchor=space.point(new BoundedFlightPath.Cell(0,0,0));
                if(!space.clearActual(anchor.subtract(centre))){cancel(tick,20);previous=centre;issued=velocity;return velocity;}
                var delta=target.subtract(anchor);if(delta.length()>24)delta=delta.normalize().multiply(24);
                expires=tick+200;
                var goal=new BoundedFlightPath.Cell((int)Math.round(delta.x),(int)Math.round(delta.y),(int)Math.round(delta.z));
                search=new BoundedFlightPath(new BoundedFlightPath.Cell(0,0,0),goal,32,512,space,seekSight?2:1);trace("begin goal="+goal);result=Vec3d.ZERO;
            }else retryAt=tick+2+(actor.getUuid().getLeastSignificantBits()&3);
        }
        if(search==null&&separation.lengthSquared()>1e-8&&velocity.lengthSquared()>1e-8){
            double speed=velocity.length();var adjusted=result.add(separation.multiply(Math.min(.12,speed*.4)));
            if(adjusted.lengthSquared()>speed*speed)adjusted=adjusted.normalize().multiply(speed);
            if(FlightRouteSpace.clearMovement(drone,adjusted))result=adjusted;
            else{var escape=separation.multiply(Math.min(.12,speed*.4));if(FlightRouteSpace.clearMovement(drone,escape))result=escape;}
        }
        previous=centre;issued=result;return result;
    }
    private void trace(String message){if(TRACE)com.terminaldetector.drmd.DescentMod.LOGGER.info("[drmd route] {} role={} pos={} stalled={}",message,drone.getRole().id,actor.getPos(),stalled);}
    private void cancel(long tick,int delay){trace("cancel delay="+delay);budget().release(actor.getUuid());search=null;route=List.of();space=null;sightRoute=false;retryAt=tick+delay;}
}
