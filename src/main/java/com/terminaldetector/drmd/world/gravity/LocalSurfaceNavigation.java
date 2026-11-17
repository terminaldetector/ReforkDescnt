package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.ai.BoundedFlightPath;
import com.terminaldetector.drmd.ai.RouteWorkBudget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.lang.ref.WeakReference;
import java.util.*;

/** First static, constant-UP surface chart. Native goals own requests and native controls own motion. */
public final class LocalSurfaceNavigation {
    public static final int RADIUS=12,NODE_LIMIT=256,MAX_SEARCHES=16,MAX_TRACKED=64;
    public static final int WORLD_POPS_PER_TICK=64,ACTOR_POPS_PER_TICK=16,REPLAN_TICKS=10;
    private static final int SEARCH_TICKS=40,PATH_TICKS=160,STALL_TICKS=20,IDLE_TICKS=80;
    private static final Map<ServerWorld,WorldState> WORLDS=new WeakHashMap<>();
    private static final class WorldState {
        final RouteWorkBudget budget=new RouteWorkBudget(MAX_SEARCHES,WORLD_POPS_PER_TICK,ACTOR_POPS_PER_TICK);
        final Map<UUID,Search> searches=new LinkedHashMap<>();long pruneTick=Long.MIN_VALUE;
        void prune(long now) {
            if(pruneTick==now)return;pruneTick=now;
            searches.entrySet().removeIf(e->{var s=e.getValue();var a=s.owner.get();
                boolean remove=a==null||a.isRemoved()||now<s.seen||now-s.seen>IDLE_TICKS;
                if(remove)budget.release(e.getKey());return remove;});
        }
    }
    private static final class Search {
        final WeakReference<SpiderEntity> owner;BlockPos target;Vec3d up,origin;
        BoundedFlightPath search;Space space;LocalSurfacePath ready;
        long seen,started,nextRequest,worked=Long.MIN_VALUE;
        Search(SpiderEntity actor,long now){owner=new WeakReference<>(actor);seen=now;}
    }
    private static final class Space implements BoundedFlightPath.Space {
        final WeakReference<SpiderEntity> owner;final Vec3d origin,up;
        final Map<BoundedFlightPath.Cell,Boolean> nodes=new HashMap<>();
        Space(SpiderEntity actor,Vec3d origin,Vec3d up){owner=new WeakReference<>(actor);this.origin=origin;this.up=up;}
        Vec3d point(BoundedFlightPath.Cell c){return origin.add(ActorFrameMath.toWorld(new Vec3d(c.x(),0,c.z()),up));}
        boolean node(BoundedFlightPath.Cell c) {
            if(c.y()!=0)return false;var cached=nodes.get(c);if(cached!=null)return cached;
            if(nodes.size()>=1024)return false;
            var actor=owner.get();boolean valid=actor!=null&&LocalSurfaceGeometry.clear(actor,point(c),point(c),up,true);
            nodes.put(c,valid);return valid;
        }
        @Override public boolean clear(BoundedFlightPath.Cell from,BoundedFlightPath.Cell to) {
            if(from.y()!=0||to.y()!=0||!node(to))return false;
            var actor=owner.get();return actor!=null&&LocalSurfaceGeometry.clear(actor,point(from),point(to),up,true);
        }
    }
    private LocalSurfaceNavigation() {}

    public static boolean framed(Entity entity) {
        return entity instanceof SpiderEntity actor&&actor.getWorld() instanceof ServerWorld world
            &&world.getServer().isOnThread()&&world.getEntityById(actor.getId())==actor&&actor.isAlive()
            &&actor.getPose()==EntityPose.STANDING&&!actor.hasVehicle()&&!actor.isTouchingWater()&&!actor.isInLava()
            &&ActorFrames.active(actor)&&ActorFrames.up(actor).dotProduct(ActorFrameMath.WORLD_UP)<.99999;
    }
    private static boolean active(SpiderEntity actor) {
        if(!framed(actor)||!EntityGravitySystem.suppressesVanillaGravity(actor))return false;
        var field=GravityFields.sample(actor.getWorld(),actor.getPos());
        return field!=null&&field.strength()>=.05f&&field.upDir().normalize().distanceTo(ActorFrames.up(actor))<1e-6;
    }
    public static boolean localMotor(MobEntity actor) {
        return actor instanceof SpiderEntity spider&&active(spider)
            &&spider.getNavigation().getCurrentPath() instanceof LocalSurfacePath p&&p.matches(ActorFrames.up(spider));
    }
    public static Vec3d localWanted(MobEntity actor,Vec3d worldWanted) {
        return ActorFrameMath.toLocal(worldWanted.subtract(actor.getPos()),ActorFrames.up(actor));
    }
    public static Vec3d input(Entity actor,Vec3d ordinaryDelta) {
        return actor instanceof MobEntity mob&&localMotor(mob)?ActorFrameMath.toWorld(ordinaryDelta,ActorFrames.up(actor)):ordinaryDelta;
    }
    public static boolean contact(SpiderEntity actor,Vec3d up){return LocalSurfaceGeometry.supported(actor,actor.getPos(),up);}

    public static Path request(SpiderEntity actor,Set<BlockPos> targets,float followRange) {
        if(!active(actor)||targets.isEmpty()||targets.size()>16||!Float.isFinite(followRange)||followRange<=0)return null;
        var world=(ServerWorld)actor.getWorld();long now=world.getTime();
        var state=WORLDS.computeIfAbsent(world,w->new WorldState());state.prune(now);
        var target=targets.stream().min(Comparator.<BlockPos>comparingDouble(p->actor.getPos().squaredDistanceTo(Vec3d.ofCenter(p)))
            .thenComparingLong(BlockPos::asLong)).orElseThrow().toImmutable();
        if(actor.getNavigation().getCurrentPath() instanceof LocalSurfacePath path&&path.getTarget().equals(target)
            &&path.matches(ActorFrames.up(actor))&&!path.isFinished())return path;
        var search=state.searches.get(actor.getUuid());
        if(search!=null&&search.owner.get()!=actor){state.budget.release(actor.getUuid());state.searches.remove(actor.getUuid());search=null;}
        if(search==null) {
            if(state.searches.size()>=MAX_TRACKED)return null;
            search=new Search(actor,now);state.searches.put(actor.getUuid(),search);
        }
        search.seen=now;
        if(!target.equals(search.target)||search.up==null||search.up.distanceTo(ActorFrames.up(actor))>=1e-6
            ||search.origin==null||actor.getPos().squaredDistanceTo(search.origin)>.25) {
            search.search=null;search.ready=null;state.budget.release(actor.getUuid());
        }
        if(search.ready!=null&&!search.ready.isFinished())return search.ready;
        if(search.search==null) {
            if(now<search.nextRequest)return null;
            search.ready=null;search.nextRequest=now+REPLAN_TICKS;search.target=target;search.up=ActorFrames.up(actor);search.origin=actor.getPos();
            var delta=ActorFrameMath.toLocal(Vec3d.ofCenter(target).subtract(search.origin),search.up);
            if(Math.abs(delta.y)>1||delta.x*delta.x+delta.z*delta.z>Math.pow(Math.min(RADIUS,followRange),2)
                ||!LocalSurfaceGeometry.supported(actor,search.origin,search.up))return null;
            var goal=new BoundedFlightPath.Cell((int)Math.round(delta.x),0,(int)Math.round(delta.z));
            if(Math.abs(goal.x())>RADIUS||Math.abs(goal.z())>RADIUS||!state.budget.begin(actor.getUuid(),now))return null;
            search.space=new Space(actor,search.origin,search.up);
            if(!search.space.node(new BoundedFlightPath.Cell(0,0,0))||!search.space.node(goal)) {state.budget.release(actor.getUuid());return null;}
            search.search=new BoundedFlightPath(new BoundedFlightPath.Cell(0,0,0),goal,RADIUS,NODE_LIMIT,search.space);
            search.started=now;search.worked=Long.MIN_VALUE;
        }
        work(actor,state,search,now);return search.ready;
    }

    public static void pending(SpiderEntity actor) {
        var world=(ServerWorld)actor.getWorld();var state=WORLDS.get(world);if(state==null)return;
        long now=world.getTime();state.prune(now);var search=state.searches.get(actor.getUuid());
        if(search!=null&&search.owner.get()==actor) {
            if(search.search!=null) {search.seen=now;work(actor,state,search,now);}
            else if(actor.getNavigation().getCurrentPath() instanceof LocalSurfacePath p&&!p.isFinished())search.seen=now;
        }
    }
    private static void work(SpiderEntity actor,WorldState state,Search search,long now) {
        if(search.worked==now||search.search==null)return;search.worked=now;
        if(!active(actor)||now-search.started>SEARCH_TICKS||actor.getPos().squaredDistanceTo(search.origin)>.25
            ||ActorFrames.up(actor).distanceTo(search.up)>=1e-6) {invalidate(actor);return;}
        if(!state.budget.begin(actor.getUuid(),now))return;
        search.search.step(state.budget.take(actor.getUuid(),now));
        if(search.search.status()==BoundedFlightPath.Status.FOUND) {
            search.ready=new LocalSurfacePath(search.search.path().stream().map(search.space::point).toList(),search.up,search.target,now);
            search.search=null;state.budget.release(actor.getUuid());
        } else if(search.search.status()!=BoundedFlightPath.Status.SEARCHING) {search.search=null;state.budget.release(actor.getUuid());}
    }
    public static boolean follow(SpiderEntity actor,LocalSurfacePath path,double speed) {
        var world=(ServerWorld)actor.getWorld();long now=world.getTime();var feet=actor.getPos();
        var state=WORLDS.computeIfAbsent(world,w->new WorldState());state.prune(now);
        var lease=state.searches.get(actor.getUuid());
        if(lease==null) {
            if(state.searches.size()>=MAX_TRACKED)return false;
            lease=new Search(actor,now);lease.ready=path;state.searches.put(actor.getUuid(),lease);
        }
        if(lease.owner.get()!=actor)return false;
        lease.seen=now;
        if(!active(actor)||!path.matches(ActorFrames.up(actor))||now-path.createdAt>PATH_TICKS
            ||!LocalSurfaceGeometry.supported(actor,feet,path.up)){invalidate(actor);return false;}
        for(int i=0;i<4&&!path.isFinished()&&feet.distanceTo(path.getNodePosition(actor))<.18;i++) {
            path.next();path.progressAt=now;path.progressPosition=feet;
        }
        if(path.isFinished())return true;
        var waypoint=path.getNodePosition(actor);
        if(!LocalSurfaceGeometry.clear(actor,feet,waypoint,path.up,false)){invalidate(actor);return false;}
        if(feet.squaredDistanceTo(path.progressPosition)>.0025){path.progressPosition=feet;path.progressAt=now;}
        if(now-path.progressAt>STALL_TICKS){invalidate(actor);return false;}
        actor.getMoveControl().moveTo(waypoint.x,waypoint.y,waypoint.z,speed);return true;
    }
    private static void invalidate(SpiderEntity actor) {
        var state=WORLDS.get(actor.getWorld());if(state==null)return;var search=state.searches.get(actor.getUuid());
        if(search!=null&&search.owner.get()==actor) {search.search=null;search.space=null;search.ready=null;search.nextRequest=actor.getWorld().getTime()+REPLAN_TICKS;}
        state.budget.release(actor.getUuid());
    }
    public static void stop(MobEntity actor) {
        if(!(actor instanceof SpiderEntity)||!(actor.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return;
        var state=WORLDS.get(world);if(state==null)return;
        var s=state.searches.get(actor.getUuid());
        if(s!=null&&s.owner.get()==actor) {
            s.search=null;s.space=null;s.ready=null;s.seen=world.getTime();
            // Preserve the bounded cooldown even if a native goal stops and restarts in one tick.
            s.nextRequest=Math.max(s.nextRequest,s.seen+REPLAN_TICKS);state.budget.release(actor.getUuid());
        }
    }
}
