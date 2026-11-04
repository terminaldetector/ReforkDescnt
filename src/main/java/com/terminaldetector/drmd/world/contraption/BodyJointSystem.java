package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import net.minecraft.server.world.ServerWorld;
import java.lang.ref.WeakReference;
import java.util.*;

/** Joint islands share forces/constraints/terrain substeps on existing BlockBody/D6 instances.
 * Runs in the hull contact world phase, before player contacts. No passenger pose is assigned. */
public final class BodyJointSystem {
    private record Frame(WeakReference<BlockBodyEntity> body,Vec3 torque,long tick){}
    public record Stats(int islands,int substeps,int paused,int broken){}
    private static final Map<ServerWorld,Map<UUID,Frame>> PENDING=new WeakHashMap<>();
    private static final Map<ServerWorld,Stats> LAST=new WeakHashMap<>();
    private BodyJointSystem(){}
    public static void defer(BlockBodyEntity body,Vec3 torque) {
        var world=(ServerWorld)body.getWorld();if(!world.getServer().isOnThread())throw new IllegalStateException("server-owned joint phase required");
        PENDING.computeIfAbsent(world,w->new HashMap<>()).put(body.getUuid(),new Frame(new WeakReference<>(body),torque,world.getTime()));
    }
    public static void cancelDeferred(BlockBodyEntity body) {
        if(body.getWorld() instanceof ServerWorld w){var frames=PENDING.get(w);if(frames!=null)frames.remove(body.getUuid());}
    }
    /** A cut after deferral transfers unintegrated material into this same physical phase.
     * Engine/controller torque stays with the original body; it is never duplicated on debris. */
    public static void materialChanged(BlockBodyEntity original,List<BlockBodyEntity> fragments) {
        if(!(original.getWorld() instanceof ServerWorld w))return;var frames=PENDING.get(w);var source=frames==null?null:frames.get(original.getUuid());
        if(source==null||source.tick!=w.getTime()||source.body.get()!=original)return;
        for(var fragment:fragments)frames.putIfAbsent(fragment.getUuid(),new Frame(new WeakReference<>(fragment),new Vec3(0,0,0),w.getTime()));
    }
    public static Stats stats(ServerWorld world){return LAST.getOrDefault(world,new Stats(0,0,0,0));}
    public static int solve(ServerWorld world) {
        if(!world.getServer().isOnThread())throw new IllegalStateException("server-owned joint phase required");
        var pending=PENDING.remove(world);if(pending==null||pending.isEmpty())return 0;
        var registry=BodyJointRegistry.get(world);var adjacency=new HashMap<UUID,List<BodyJointLink>>();
        for(var link:registry.links())if(registry.active(link.id(),world.getTime()))for(var id:List.of(link.a().body(),link.b().body()))adjacency.computeIfAbsent(id,k->new ArrayList<>()).add(link);
        var visited=new HashSet<UUID>();int islands=0,steps=0,paused=0,broken=0;
        for(var seed:pending.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            if(!visited.add(seed))continue;islands++;var ids=new LinkedHashSet<UUID>();var queue=new ArrayDeque<UUID>();var links=new LinkedHashMap<UUID,BodyJointLink>();queue.add(seed);
            while(!queue.isEmpty()) {
                var id=queue.removeFirst();ids.add(id);
                for(var link:adjacency.getOrDefault(id,List.of())){links.put(link.id(),link);for(var end:List.of(link.a().body(),link.b().body()))if(visited.add(end))queue.add(end);}
            }
            var bodies=new LinkedHashMap<UUID,BlockBodyEntity>();boolean ready=true;
            for(var id:ids) {
                var frame=pending.get(id);var body=frame==null?null:frame.body.get();
                if(body==null||body.isRemoved()||body.getWorld()!=world||frame.tick!=world.getTime()){ready=false;continue;}bodies.put(id,body);
            }
            if(!ready){bodies.values().forEach(BlockBodyEntity::pauseJointMotion);paused++;continue;}
            double remaining=.05;boolean stop=false;
            for(int step=0;step<512&&remaining>1e-10;step++) {
                double dt=Math.min(remaining,.05/16);var saved=new LinkedHashMap<UUID,D6PhysicsBody.Snapshot>();for(var e:bodies.entrySet())saved.put(e.getKey(),e.getValue().physics().snapshot());
                var brokenNow=new ArrayList<UUID>();boolean accepted=false;
                for(int attempt=0;attempt<8;attempt++) {
                    for(var e:bodies.entrySet())e.getValue().integrateJointForces(dt,pending.get(e.getKey()).torque);
                    var constraints=new LinkedHashMap<UUID,BodyJointConstraint>();
                    for(var link:links.values())if(registry.find(link.id()).isPresent()) {
                        var a=bodies.get(link.a().body());var b=bodies.get(link.b().body());constraints.put(link.id(),new BodyJointConstraint(a.physics(),b.physics(),link.bind(a,b),dt));
                    }
                    for(int pass=0;pass<32;pass++)for(var constraint:constraints.values())constraint.solve();
                    double rate=0;
                    for(var body:bodies.values())rate=Math.max(rate,Math.min(512,body.physics().linearVelocity().length())+Math.min(2,body.physics().angularVelocity().length())*body.radius());
                    if(!Double.isFinite(rate))throw new IllegalStateException("non-finite joint island velocity");
                    if(rate*dt>.150000001) {
                        for(var e:bodies.entrySet())e.getValue().restoreJointStep(saved.get(e.getKey()));dt=Math.min(dt*.5,.135/rate);continue;
                    }
                    accepted=true;for(var e:constraints.entrySet())if(e.getValue().broken())brokenNow.add(e.getKey());break;
                }
                if(!accepted)throw new IllegalStateException("joint corner travel retry budget exceeded");
                boolean available=true;for(var e:bodies.entrySet())if(!e.getValue().integrateJointPose(dt,saved.get(e.getKey())))available=false;
                if(!available) {
                    for(var e:bodies.entrySet()){e.getValue().restoreJointStep(saved.get(e.getKey()));e.getValue().pauseJointMotion();}paused++;stop=true;break;
                }
                for(var body:bodies.values())body.publishJointPose();
                for(var id:brokenNow)if(registry.remove(id))broken++;
                remaining-=dt;steps++;
            }
            if(!stop&&remaining>1e-10){bodies.values().forEach(BlockBodyEntity::pauseJointMotion);paused++;}
        }
        LAST.put(world,new Stats(islands,steps,paused,broken));return steps;
    }
}
