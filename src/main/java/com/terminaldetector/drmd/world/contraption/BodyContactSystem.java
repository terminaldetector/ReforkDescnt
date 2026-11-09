package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.physics.collision.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/** One chronological hull/hull contact phase after free trajectories and before passengers.
 * All simultaneous contact normals share the existing D6 impulse solver. A collision changes
 * the remaining trajectory; it never discards the rest of the tick or replays its forces. */
public final class BodyContactSystem {
    public record Stats(int collisions,int testedPairs,boolean budgetPaused){}
    private record Pair(int a,int b){}
    private record Candidate(Frame a,Frame b,BodyPairQuery.Impact impact){}
    private record Touch(Frame a,Frame b,List<BodyPairQuery.Contact> contacts){}
    private record Pose(Vec3 position,Quat rotation){}
    private static final class Frame {
        final BlockBodyEntity body;BodyPairQuery.Motion motion;boolean dynamic;
        Frame(BlockBodyEntity b) {
            body=b;var m=b.boardingMotion();dynamic=!b.boardingMotionPaused();
            motion=new BodyPairQuery.Motion(b.collider(),b.localCentre(),m.position(0),m.rotation(0),m.position(1),m.rotation(1));
        }
        int id(){return body.getId();}
        void slice(double fraction){motion=new BodyPairQuery.Motion(motion.shape(),motion.centre(),motion.position(fraction),motion.rotation(fraction),motion.to(),motion.after());}
        void constant(){var p=body.massPosition();var q=body.rotation();motion=new BodyPairQuery.Motion(motion.shape(),motion.centre(),p,q,p,q);}
    }
    private static final Map<ServerWorld,Stats> LAST=new WeakHashMap<>();
    private static final int MAX_CONTACT_TIMES_PER_BODY=32;
    private BodyContactSystem(){}
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(BodyContactSystem::solve);
        ServerWorldEvents.UNLOAD.register((server,world)->LAST.remove(world));
    }
    public static Stats stats(ServerWorld world){return LAST.getOrDefault(world,new Stats(0,0,false));}
    public static int solve(ServerWorld world) {
        if(!world.getServer().isOnThread())throw new IllegalStateException("Hull contacts belong to the server world thread");
        BodyJointSystem.solve(world);var joints=BodyJointRegistry.get(world);
        var frames=new ArrayList<Frame>();for(var body:BodySpatialIndex.loaded(world))if(!body.cells().isEmpty())frames.add(new Frame(body));
        var byBody=new HashMap<UUID,Frame>();for(var frame:frames)byBody.put(frame.body.getUuid(),frame);
        var index=new AabbTree<Frame>();for(var frame:frames)update(index,frame);
        var changed=new LinkedHashSet<BlockBodyEntity>();var ignored=new HashSet<Pair>();var resolved=new HashSet<Pair>();
        int collisions=0,tested=0;double remaining=.05;boolean paused=false;
        int eventBudget=MAX_CONTACT_TIMES_PER_BODY*Math.max(1,frames.size());
        for(int event=0;event<eventBudget;event++) {
            Candidate first=null;
            for(var a:frames)for(var b:near(index,a)) {
                if(b.id()<=a.id()||!a.dynamic&&!b.dynamic||ignored.contains(new Pair(a.id(),b.id()))||!joints.collisionAllowed(a.body.getUuid(),b.body.getUuid()))continue;
                tested++;var hit=BodyPairQuery.impact(a.motion,b.motion);
                if(hit.isPresent()&&(first==null||hit.get().fraction()<first.impact.fraction()))first=new Candidate(a,b,hit.get());
            }
            if(first==null)break;
            double fraction=first.impact.fraction();
            remaining*=1-fraction;
            for(var frame:frames){frame.slice(fraction);update(index,frame);}
            var touching=new ArrayList<Touch>();var participants=new LinkedHashSet<Frame>();
            for(var a:frames)for(var b:near(index,a)) {
                if(b.id()<=a.id()||!a.dynamic&&!b.dynamic||ignored.contains(new Pair(a.id(),b.id()))||!joints.collisionAllowed(a.body.getUuid(),b.body.getUuid()))continue;
                var contacts=BodyPairQuery.contacts(a.motion,b.motion,0,true);
                if(!contacts.isEmpty()){touching.add(new Touch(a,b,contacts));participants.add(a);participants.add(b);}
            }
            if(touching.isEmpty()){ignored.add(new Pair(first.a.id(),first.b.id()));continue;}
            // Contact time belongs to the entire active mechanism, including members that
            // did not directly touch this pair. Rewind and continue the same material phase.
            var jointLinks=new LinkedHashMap<UUID,BodyJointLink>();var queue=new ArrayDeque<>(participants);
            while(!queue.isEmpty())for(var link:joints.forBody(queue.removeFirst().body.getUuid()))if(joints.active(link.id(),world.getTime())){
                var a=byBody.get(link.a().body());var b=byBody.get(link.b().body());if(a==null||b==null)continue;
                jointLinks.put(link.id(),link);if(participants.add(a))queue.add(a);if(participants.add(b))queue.add(b);
            }
            var originals=new LinkedHashMap<Frame,Pose>();boolean accepted=true;
            for(var frame:participants)if(frame.dynamic) {
                originals.put(frame,new Pose(frame.body.massPosition(),frame.body.rotation()));
                if(!frame.body.resolveContactPose(frame.motion.from(),frame.motion.before())){accepted=false;break;}
            }
            if(!accepted) {
                // A TOI inside unavailable terrain is not permission to tunnel through it.
                // Preserve the already valid poses; malformed embedded spawns remain guarded.
                for(var entry:originals.entrySet())entry.getKey().body.resolveContactPose(entry.getValue().position,entry.getValue().rotation);
                ignored.add(new Pair(first.a.id(),first.b.id()));continue;
            }
            var proxies=new IdentityHashMap<Frame,D6PhysicsBody>();
            for(var frame:participants)proxies.put(frame,frame.dynamic?frame.body.physics():new D6PhysicsBody().withPosition(frame.body.massPosition()).withRotation(frame.body.rotation()));
            var constraints=new ArrayList<BodyContactConstraint>();
            for(var touch:touching)for(var c:touch.contacts)constraints.add(new BodyContactConstraint(proxies.get(touch.a),proxies.get(touch.b),c.point(),c.normal(),.65));
            var passive=new LinkedHashMap<UUID,BodyJointConstraint>();var coupled=new LinkedHashMap<UUID,BlockBodyEntity>();
            for(var link:jointLinks.values()){
                var a=byBody.get(link.a().body());var b=byBody.get(link.b().body());if(!a.dynamic||!b.dynamic)continue;
                coupled.put(link.a().body(),a.body);coupled.put(link.b().body(),b.body);
                passive.put(link.id(),BodyJointConstraint.velocityOnly(proxies.get(a),proxies.get(b),link.bind(a.body,b.body),.05));
            }
            for(int pass=0;pass<32;pass++){for(var constraint:constraints)constraint.solve();for(var constraint:passive.values())constraint.solve();}
            for(var entry:passive.entrySet())if(entry.getValue().broken())joints.remove(entry.getKey());
            for(var touch:touching){var pair=new Pair(touch.a.id(),touch.b.id());
                // Curved, torque-free trajectories can immediately close the same resolved
                // touching faces again. A 0.1mm separation only on this zero-time recurrence
                // gives CCD a positive interval; ordinary first impacts retain their exact TOI.
                boolean recurring=resolved.contains(pair)&&fraction<1e-7;
                separate(touch,proxies.get(touch.a),proxies.get(touch.b),recurring?1e-4:0);resolved.add(pair);
            }
            var starts=new IdentityHashMap<Frame,Pose>();for(var frame:participants)if(frame.dynamic)starts.put(frame,new Pose(frame.body.massPosition(),frame.body.rotation()));
            if(!coupled.isEmpty()&&!BodyJointSystem.advanceContactRemainder(world,coupled,jointLinks.values(),remaining))paused=true;
            for(var frame:participants) {
                if(!frame.dynamic)continue;
                var start=starts.get(frame);
                if(!coupled.containsKey(frame.body.getUuid()))frame.body.advanceContactRemainder(remaining);frame.dynamic=!frame.body.boardingMotionPaused();
                frame.motion=new BodyPairQuery.Motion(frame.motion.shape(),frame.motion.centre(),start.position,start.rotation,frame.body.massPosition(),frame.body.rotation());
                changed.add(frame.body);update(index,frame);
            }
            collisions+=touching.size();
            if(remaining<1e-10)break;
            if(event==eventBudget-1) {
                // Bounded solver exhaustion must not allow an unresolved fast crossing.
                // Retain momentum at the last checked time instead of dropping candidates.
                paused=true;
                for(var frame:frames)if(frame.dynamic) {
                    frame.body.resolveContactPose(frame.motion.from(),frame.motion.before());changed.add(frame.body);frame.constant();
                }
            }
        }
        changed.forEach(BlockBodyEntity::publishPhysicsState);LAST.put(world,new Stats(collisions,tested,paused));return collisions;
    }
    private static List<Frame> near(AabbTree<Frame> index,Frame a) {
        return a.motion.sweptBounds().map(bounds->{var result=new ArrayList<>(index.query(bounds).values());result.sort(Comparator.comparingInt(Frame::id));return (List<Frame>)result;}).orElse(List.of());
    }
    private static void update(AabbTree<Frame> index,Frame frame){frame.motion.sweptBounds().ifPresentOrElse(b->index.update(frame,b),()->index.remove(frame));}
    private static void separate(Touch touch,D6PhysicsBody a,D6PhysicsBody b,double gap) {
        var contact=touch.contacts.stream().max(Comparator.comparingDouble(BodyPairQuery.Contact::penetration)).orElseThrow();
        if(contact.penetration()<=1e-7&&gap==0)return;
        double ia=touch.a.dynamic?1/Math.max(1e-9,a.mass()):0,ib=touch.b.dynamic?1/Math.max(1e-9,b.mass()):0;
        if(ia+ib==0)return;
        var delta=contact.normal().scaled(Math.min(.25,contact.penetration()+1e-7+gap)/(ia+ib));
        if(touch.a.dynamic)separate(touch.a.body,a,delta.scaled(ia));
        if(touch.b.dynamic)separate(touch.b.body,b,delta.scaled(-ib));
    }
    private static void separate(BlockBodyEntity body,D6PhysicsBody dynamics,Vec3 displacement){
        var before=body.massPosition();
        if(body.resolveContactPose(before.plus(displacement),body.rotation())){
            // A COM correction changes orbital momentum. Retain total world angular
            // momentum by moving the equal contribution into the body's spin.
            var moved=body.massPosition().minus(before);dynamics.withAngularMomentum(dynamics.angularMomentum().minus(moved.cross(dynamics.linearVelocity().scaled(dynamics.mass()))));
        }
    }
}
