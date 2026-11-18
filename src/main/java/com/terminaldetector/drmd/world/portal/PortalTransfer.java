package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.bombardment.AerialBombEntity;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.world.gravity.*;
import com.terminaldetector.drmd.world.portal.gun.PortalMotionAccess;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Predicate;

/** One immutable admission followed by one server-thread commit for a portal-connected physical group. */
final class PortalTransfer {
    private static final int MAX_MEMBERS=256,MAX_BODIES=128,MAX_SHAPES=32768;
    private static final Vec3 ZERO=new Vec3(0,0,0);
    record Attempt(List<Entity> members,Plan accepted) {}
    private record Actor(Entity entity,Vec3 before,Vec3 velocity,Vec3 rawVelocity,float yaw,float pitch,Entity vehicle,
                         List<Entity> passengers,DynamicBodyAttachment.State boarding,FootGravitySystem.State foot,Vec3 actorUp,
                         Vec3 forward,Vec3 headForward,Vec3 flightForward,Vec3 flightUp,Vec3 flightVelocity,boolean flight) {}
    private record Hull(BlockBodyEntity entity,D6PhysicsBody.Snapshot before,Vec3 velocity,Vec3 momentum,Vec3 pivot,long revision,List<BodyBounds> shapes,Entity vehicle,List<Entity> passengers) {}
    private PortalTransfer(){}
    record Membership(List<Entity> members,boolean complete) {}
    static Membership gather(ServerWorld world,Entity root,Predicate<UUID> cooling){
        if(!world.getServer().isOnThread())return new Membership(List.of(root),false);
        var members=new LinkedHashMap<UUID,Entity>();var queue=new ArrayDeque<Entity>();queue.add(root.getRootVehicle());
        Map<UUID,BlockBodyEntity> indexed=null;var registry=BodyJointRegistry.get(world);
        while(!queue.isEmpty()){
            Entity entity=queue.removeFirst();if(members.containsKey(entity.getUuid()))continue;
            if(members.size()>=MAX_MEMBERS||entity.isRemoved()||entity.getWorld()!=world||cooling.test(entity.getUuid()))return refusedGroup(members,entity);
            members.put(entity.getUuid(),entity);queue.addAll(entity.getPassengerList());
            if(entity instanceof BlockBodyEntity body){
                if(indexed==null){indexed=new HashMap<>();for(var candidate:BodySpatialIndex.loaded(world))indexed.put(candidate.getUuid(),candidate);}
                if(indexed.get(body.getUuid())!=body)return refusedGroup(members,body);
                for(var link:registry.forBody(body.getUuid()))for(var endpoint:List.of(link.a(),link.b())){
                    var connected=indexed.get(endpoint.body());if(connected==null)return refusedGroup(members,body);
                    if(!members.containsKey(connected.getUuid()))queue.add(connected.getRootVehicle());
                }
            }
        }
        var bodyIds=new HashSet<Integer>();for(var e:members.values())if(e instanceof BlockBodyEntity b)bodyIds.add(b.getId());
        if(bodyIds.size()>MAX_BODIES)return new Membership(List.copyOf(members.values()),false);
        if(!bodyIds.isEmpty())for(var p:BoardingSystem.players(world)){
            var s=BoardingSystem.state(p);
            if(s!=null&&(bodyIds.contains(s.support())||bodyIds.contains(s.gravityOwner()))){
                if(p.hasVehicle()&&!members.containsKey(p.getRootVehicle().getUuid()))return new Membership(List.copyOf(members.values()),false);
                if(members.size()>=MAX_MEMBERS)return new Membership(List.copyOf(members.values()),false);members.put(p.getUuid(),p);
                var riding=new ArrayDeque<Entity>(p.getPassengerList());
                while(!riding.isEmpty()){
                    var passenger=riding.removeFirst();if(members.containsKey(passenger.getUuid()))continue;
                    if(members.size()>=MAX_MEMBERS||passenger.isRemoved()||passenger.getWorld()!=world||passenger instanceof BlockBodyEntity)return new Membership(List.copyOf(members.values()),false);
                    members.put(passenger.getUuid(),passenger);riding.addAll(passenger.getPassengerList());
                }
            }
        }
        // Spatial candidates are admitted only by a real carrier field or material support contact.
        if(!bodyIds.isEmpty()&&!occupants(world,members,bodyIds))return new Membership(List.copyOf(members.values()),false);
        return new Membership(List.copyOf(members.values()),true);
    }
    private static Membership refusedGroup(Map<UUID,Entity> members,Entity last){members.putIfAbsent(last.getUuid(),last);return new Membership(List.copyOf(members.values()),false);}
    static Attempt prepare(ServerWorld world,Entity root,D6PortalTransform base,Vec3 remainder,Vec3 hit,double halfSpan,
                           boolean strictAperture,long tick,Predicate<UUID> cooling,boolean solverComplete){
        return prepare(world,root,base,remainder,hit,halfSpan,strictAperture,tick,cooling,solverComplete,false);
    }
    static Attempt prepareCurrent(ServerWorld world,Entity root,D6PortalTransform base,Vec3 remainder,Vec3 hit,double halfSpan,
                           boolean strictAperture,long tick,Predicate<UUID> cooling){
        return prepare(world,root,base,remainder,hit,halfSpan,strictAperture,tick,cooling,true,true);
    }
    private static Attempt prepare(ServerWorld world,Entity root,D6PortalTransform base,Vec3 remainder,Vec3 hit,double halfSpan,
                           boolean strictAperture,long tick,Predicate<UUID> cooling,boolean solverComplete,boolean currentVelocity){
        if(!world.getServer().isOnThread()||base.scale()!=1)return new Attempt(List.of(root),null);
        var gathered=gather(world,root,cooling);if(!gathered.complete())return new Attempt(gathered.members(),null);
        var members=new LinkedHashMap<UUID,Entity>();for(var e:gathered.members())members.put(e.getUuid(),e);
        var bodyIds=new HashSet<Integer>();for(var e:members.values())if(e instanceof BlockBodyEntity b)bodyIds.add(b.getId());
        var registry=BodyJointRegistry.get(world);
        var actors=new ArrayList<Actor>();var hulls=new ArrayList<Hull>();int count=0,blocks=0;
        for(var e:members.values()){
            if(cooling.test(e.getUuid()))return new Attempt(List.copyOf(members.values()),null);
            if(e instanceof BlockBodyEntity b){blocks+=b.cells().size();if(blocks>MAX_SHAPES)return new Attempt(List.copyOf(members.values()),null);var boxes=b.collider().boxes();count+=boxes.size();hulls.add(new Hull(b,b.physics().snapshot(),b.physics().linearVelocity(),b.physics().angularMomentum(),pure(b.getPos()),b.shapeRevision(),boxes,b.getVehicle(),List.copyOf(b.getPassengerList())));}
            else{actors.add(capture(e,tick,solverComplete||!bodyIds.isEmpty(),currentVelocity));count++;}
            if(count>MAX_SHAPES)return new Attempt(List.copyOf(members.values()),null);
        }
        // Project real geometry; radius alone rejects a long narrow hull that fits a portal head-on.
        if(strictAperture||!hulls.isEmpty()||members.size()>1){
            for(var h:hulls)for(var local:h.shapes())if(!fits(OrientedBox.local(local,h.entity.massPosition(),h.entity.localCentre(),h.entity.rotation()),base,halfSpan-.125,base.rotation().inverse().rotate(remainder)))return new Attempt(List.copyOf(members.values()),null);
            for(var a:actors)if(!fits(currentBox(a),base,halfSpan-.125,base.rotation().inverse().rotate(remainder)))return new Attempt(List.copyOf(members.values()),null);
        }
        // A leading-edge crossing extracts the entire group in front of the destination plane.
        // This is ONE shared translation: joint anchors and rider-relative positions cannot diverge.
        double clearance=PortalCrossing.EXIT_CLEARANCE;
        for(var h:hulls)for(var local:h.shapes()){
            var q=base.transformOrientation(h.entity.rotation());var com=base.transformPoint(h.entity.massPosition());
            clearance=Math.max(clearance,.01-minimum(OrientedBox.local(local,com.minus(remainder),h.entity.localCentre(),q),base.destPoint(),base.destNormal()));
        }
        for(var a:actors)clearance=Math.max(clearance,.01-minimum(destinationBox(a,base,base.transformPoint(a.before).minus(remainder)),base.destPoint(),base.destNormal()));
        var transform=D6PortalTransform.of(base.sourcePoint(),base.sourceNormal(),base.destPoint().plus(base.destNormal().scaled(clearance)),base.destNormal());
        var plan=new Plan(world,List.copyOf(members.values()),actors,hulls,bodyIds,transform,remainder,registry);
        return new Attempt(plan.members,plan.validate()?plan:null);
    }
    private static boolean occupants(ServerWorld world,Map<UUID,Entity> members,Set<Integer> bodyIds){
        var carriers=members.values().stream().filter(BlockBodyEntity.class::isInstance).map(BlockBodyEntity.class::cast).toList();
        var candidates=new LinkedHashMap<UUID,Entity>();
        for(var body:carriers){
            var regions=new ArrayList<Box>();var field=body.passengerField(body.passengerGravity());
            if(field.enabled())regions.add(PortalDestinationCollision.box(OrientedBox.local(field.localVolume(),body.massPosition(),body.localCentre(),body.rotation()).bounds()));
            var coarse=body.collider().bounds();if(coarse.isEmpty())return false;
            Box envelope=PortalDestinationCollision.box(OrientedBox.local(coarse.get(),body.massPosition(),body.localCentre(),body.rotation()).bounds()).expand(.12);
            if(PortalDestinationCollision.available(world,envelope))regions.add(envelope);
            else for(var local:body.collider().boxes())regions.add(PortalDestinationCollision.box(OrientedBox.local(local,body.massPosition(),body.localCentre(),body.rotation()).bounds()).expand(.12));
            for(var region:regions){
                if(!PortalDestinationCollision.available(world,region))return false;
                for(var candidate:world.getOtherEntities(body,region,e->!e.isSpectator()&&!(e instanceof BlockBodyEntity)
                        &&(e instanceof ItemEntity||e instanceof LivingEntity||e instanceof com.terminaldetector.drmd.entity.RagdollEntity))){
                    if(members.containsKey(candidate.getUuid()))continue;
                    if(candidates.size()>=1024)return false;candidates.put(candidate.getUuid(),candidate);
                    var rideRoot=candidate.getRootVehicle();if(rideRoot instanceof BlockBodyEntity&& !members.containsKey(rideRoot.getUuid()))continue;
                    if(candidate instanceof ServerPlayerEntity p){var state=BoardingSystem.state(p);if(state!=null
                        &&(state.support()>=0||state.gravityOwner()>=0)&&!bodyIds.contains(state.support())&&!bodyIds.contains(state.gravityOwner()))continue;}
                    boolean belongs=field.at(pure(candidate.getBoundingBox().getCenter()),body.massPosition(),body.localCentre(),body.rotation()).isPresent();
                    if(!belongs&&(candidate instanceof ItemEntity||candidate instanceof LivingEntity)){
                        OrientedBox actor=candidate instanceof net.minecraft.entity.player.PlayerEntity p&&BoardingPose.active(p)
                            ?PassengerFrame.box(pure(p.getPos()),p.getWidth(),p.getHeight(),BoardingPose.frame(p))
                            :OrientedBox.axisAligned(PortalDestinationCollision.bounds(candidate.getBoundingBox()));
                        Vec3 up=pure(ActorFrames.up(candidate));if(up.lengthSquared()<.5)up=new Vec3(0,1,0);
                        for(var contact:body.collisionQuery().contacts(actor,.04))if(contact.normal().dot(up)>.6&&contact.penetration()<.12
                            &&pure(candidate.getVelocity()).scaled(20).minus(pure(body.surfaceVelocityAt(mc(contact.point())))).dot(contact.normal())<=.15){belongs=true;break;}
                    }
                    if(!belongs)continue;
                    var riding=new ArrayDeque<Entity>();riding.add(rideRoot);
                    while(!riding.isEmpty()){
                        var e=riding.removeFirst();if(members.containsKey(e.getUuid()))continue;
                        if(members.size()>=MAX_MEMBERS||e.getWorld()!=world||e.isRemoved()||e instanceof BlockBodyEntity)return false;
                        members.put(e.getUuid(),e);riding.addAll(e.getPassengerList());
                    }
                }
            }
        }
        return true;
    }
    private static Attempt refused(Map<UUID,Entity> members,Entity last){members.putIfAbsent(last.getUuid(),last);return new Attempt(List.copyOf(members.values()),null);}
    record Leading(Vec3 before,Vec3 after) {}
    static Leading leading(Entity root,Vec3 previous,Vec3 normal){
        double before=Double.POSITIVE_INFINITY,after=Double.POSITIVE_INFINITY;
        Vec3 now=pure(root.getPos());var queue=new ArrayDeque<Entity>();queue.add(root);var seen=new HashSet<UUID>();
        while(!queue.isEmpty()){
            var e=queue.removeFirst();if(!seen.add(e.getUuid()))continue;if(seen.size()>MAX_MEMBERS)return null;queue.addAll(e.getPassengerList());
            if(e instanceof BlockBodyEntity body){
                var motion=body.boardingMotion();var oldQ=motion.rotation(0);var oldCom=motion.position(0);
                // A paused hull has no integrated history; retain the supplied real entity segment.
                if(body.boardingMotionPaused())oldCom=body.massPosition().plus(previous.minus(now));
                for(var local:body.collider().boxes()){
                    after=Math.min(after,minimum(OrientedBox.local(local,body.massPosition(),body.localCentre(),body.rotation()),now,normal));
                    before=Math.min(before,minimum(OrientedBox.local(local,oldCom,body.localCentre(),oldQ),previous,normal));
                }
            }else{
                var shape=e instanceof net.minecraft.entity.player.PlayerEntity p&&BoardingPose.active(p)
                    ?PassengerFrame.box(pure(p.getPos()),p.getWidth(),p.getHeight(),BoardingPose.frame(p))
                    :OrientedBox.axisAligned(PortalDestinationCollision.bounds(e.getBoundingBox()));
                double support=minimum(shape,now,normal);after=Math.min(after,support);before=Math.min(before,support);
            }
        }
        return Double.isFinite(before+after)?new Leading(previous.plus(normal.scaled(before)),now.plus(normal.scaled(after))):null;
    }
    private static Actor capture(Entity e,long tick,boolean rawItems,boolean currentVelocity){
        DynamicBodyAttachment.State boarding=e instanceof ServerPlayerEntity p&&!DescentPlayerData.get(p).isEnabled()?BoardingSystem.state(p):null;
        var data=e instanceof ServerPlayerEntity p?DescentPlayerData.get(p):null;boolean flight=data!=null&&data.isEnabled();
        var foot=e instanceof ServerPlayerEntity p&&!flight&&FootGravitySystem.isActive(p)?FootGravitySystem.get(p):null;
        Vec3 velocity=flight?pure(data.getFlightVelocity()).scaled(.05):boarding!=null?boarding.velocity().scaled(.05):currentVelocity||rawItems&&e instanceof ItemEntity?pure(e.getVelocity()):pure(((PortalMotionAccess)e).drmd$portalVelocity(tick));
        return new Actor(e,pure(e.getPos()),velocity,pure(e.getVelocity()),e.getYaw(),e.getPitch(),e.getVehicle(),List.copyOf(e.getPassengerList()),boarding,foot,pure(foot==null?ActorFrames.up(e):foot.up()),pure(e instanceof net.minecraft.entity.player.PlayerEntity?e.getRotationVec(1):e.getRotationVector()),pure(e instanceof com.terminaldetector.drmd.entity.DroneEntity drone?Vec3d.fromPolar(drone.getPitch(),drone.getHeadYaw()):e.getRotationVec(1)),data==null?ZERO:pure(data.shipForward((ServerPlayerEntity)e)),data==null?ZERO:pure(data.shipUp((ServerPlayerEntity)e)),data==null?ZERO:pure(data.getFlightVelocity()),flight);
    }
    private static OrientedBox currentBox(Actor a){
        if(a.boarding!=null)return PassengerFrame.box(a.before,a.entity.getWidth(),a.entity.getHeight(),a.boarding.frame());
        return OrientedBox.axisAligned(PortalDestinationCollision.bounds(a.entity.getBoundingBox()));
    }
    private static OrientedBox destinationBox(Actor a,D6PortalTransform t,Vec3 feet){
        if(a.boarding!=null)return PassengerFrame.box(feet,a.entity.getWidth(),a.entity.getHeight(),t.transformOrientation(a.boarding.frame()));
        if(a.foot!=null)return OrientedBox.axisAligned(PortalDestinationCollision.bounds(GravityMount.standingBox(mc(feet),mc(t.rotation().rotate(a.actorUp)),a.entity.getWidth()/2.0,a.entity.getHeight())));
        if(a.actorUp.lengthSquared()>.5&&a.entity instanceof LivingEntity living)return OrientedBox.axisAligned(PortalDestinationCollision.bounds(ActorFrames.body(living,mc(feet),mc(t.rotation().rotate(a.actorUp)))));
        if(a.entity instanceof com.terminaldetector.drmd.entity.RagdollEntity){var old=PortalDestinationCollision.bounds(a.entity.getBoundingBox());return new OrientedBox(feet.plus(t.rotation().rotate(old.centre().minus(a.before))),old.half(),t.rotation());}
        var b=a.entity.getBoundingBox().offset(mc(feet.minus(a.before)));return OrientedBox.axisAligned(PortalDestinationCollision.bounds(b));
    }
    private static boolean fits(OrientedBox shape,D6PortalTransform t,double span,Vec3 remainder){for(var corner:corners(shape))if(!PortalCrossing.withinFace(corner.minus(remainder),t.sourcePoint(),t.sourceNormal(),span))return false;return true;}
    private static double minimum(OrientedBox shape,Vec3 point,Vec3 normal){return shape.supportCentre(normal.scaled(-1)).minus(point).dot(normal);}
    private static List<Vec3> corners(OrientedBox box){var out=new ArrayList<Vec3>(8);for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1})out.add(box.centre().plus(box.basis(0).scaled(x*box.half().x())).plus(box.basis(1).scaled(y*box.half().y())).plus(box.basis(2).scaled(z*box.half().z())));return out;}
    static Vec3 pure(Vec3d v){return new Vec3(v.x,v.y,v.z);}
    static Vec3d mc(Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}

    static final class Plan {
        final ServerWorld world;final List<Entity> members;final List<Actor> actors;final List<Hull> hulls;final Set<Integer> bodyIds;final D6PortalTransform transform;final Vec3 remainder;final BodyJointRegistry registry;final Map<UUID,List<BodyJointLink>> topology=new LinkedHashMap<>();final Map<com.terminaldetector.drmd.entity.RagdollEntity,com.terminaldetector.drmd.entity.RagdollEntity.PreparedPortalTransfer> ragdolls=new LinkedHashMap<>();final Map<com.terminaldetector.drmd.entity.DroneEntity,com.terminaldetector.drmd.ai.DronePortalState.Prepared> drones=new LinkedHashMap<>();final Map<AerialBombEntity,AerialBombEntity.PreparedPortalFrame> ordnanceFrames=new LinkedHashMap<>();boolean applied;
        Plan(ServerWorld world,List<Entity> members,List<Actor> actors,List<Hull> hulls,Set<Integer> bodyIds,D6PortalTransform transform,Vec3 remainder,BodyJointRegistry registry){this.world=world;this.members=members;this.actors=actors;this.hulls=hulls;this.bodyIds=Set.copyOf(bodyIds);this.transform=transform;this.remainder=remainder;this.registry=registry;for(var h:hulls)topology.put(h.entity.getUuid(),registry.forBody(h.entity.getUuid()));}
        boolean validate(){
            var moving=Set.copyOf(members);
            for(var h:hulls){var shapes=new ArrayList<PortalDestinationCollision.Shape>();var q=transform.transformOrientation(h.entity.rotation());var com=transform.transformPoint(h.entity.massPosition());
                var pivot=transform.transformPoint(h.pivot);var proxy=new Box(pivot.x()-.0001,pivot.y()-.0001,pivot.z()-.0001,pivot.x()+.0001,pivot.y()+.0001,pivot.z()+.0001).stretch(mc(remainder.scaled(-1)));
                if(!PortalDestinationCollision.available(world,proxy))return false;
                for(var local:h.shapes)shapes.add(new PortalDestinationCollision.Shape(OrientedBox.local(local,com.minus(remainder),h.entity.localCentre(),q),OrientedBox.local(local,com,h.entity.localCentre(),q)));
                if(!PortalDestinationCollision.clear(world,h.entity,shapes,remainder,moving))return false;
            }
            for(var a:actors){
                if(a.entity instanceof com.terminaldetector.drmd.entity.DroneEntity drone&&drone.isAlive()){
                    var prepared=drones.get(drone);if(prepared==null){var token=com.terminaldetector.drmd.ai.DronePortalState.prepare(drone,transform);if(token.isEmpty())return false;prepared=token.get();drones.put(drone,prepared);}if(!prepared.current())return false;
                }
                if(a.entity instanceof AerialBombEntity bomb&&bomb.requiresPortalFrame()){
                    var prepared=ordnanceFrames.get(bomb);if(prepared==null){var token=bomb.preparePortalFrame(transform);if(token.isEmpty())return false;prepared=token.get();ordnanceFrames.put(bomb,prepared);}if(!prepared.current())return false;
                }
                if(a.entity instanceof com.terminaldetector.drmd.entity.RagdollEntity ragdoll){
                    var token=ragdolls.get(ragdoll);if(token==null){var proposed=ragdoll.preparePortalTransfer(transform,remainder);if(proposed.isEmpty())return false;token=proposed.get();ragdolls.put(ragdoll,token);}
                    if(!token.current())return false;var shapes=new ArrayList<PortalDestinationCollision.Shape>();
                    for(int i=0;i<token.destinationPartBounds().size();i++)shapes.add(new PortalDestinationCollision.Shape(OrientedBox.axisAligned(PortalDestinationCollision.bounds(token.sweepStartPartBounds().get(i))),OrientedBox.axisAligned(PortalDestinationCollision.bounds(token.destinationPartBounds().get(i)))));
                    if(!PortalDestinationCollision.clear(world,a.entity,shapes,remainder,moving))return false;
                }else{var feet=transform.transformPoint(a.before);var end=destinationBox(a,transform,feet);var start=destinationBox(a,transform,feet.minus(remainder));
                    if(!PortalDestinationCollision.clear(world,a.entity,List.of(new PortalDestinationCollision.Shape(start,end)),remainder,moving))return false;
                }
            }
            return true;
        }
        boolean current(){
            if(applied||!world.getServer().isOnThread())return false;
            for(var entry:topology.entrySet())if(!registry.forBody(entry.getKey()).equals(entry.getValue()))return false;
            for(var h:hulls)if(h.entity.isRemoved()||h.entity.getWorld()!=world||h.entity.shapeRevision()!=h.revision||(!h.entity.physics().matches(h.before))||!pure(h.entity.getPos()).equals(h.pivot)||h.entity.getVehicle()!=h.vehicle||!h.entity.getPassengerList().equals(h.passengers))return false;
            for(var a:actors){
                if(a.entity.isRemoved()||a.entity.getWorld()!=world||!pure(a.entity.getPos()).equals(a.before)||!pure(a.entity.getVelocity()).equals(a.rawVelocity)||a.entity.getVehicle()!=a.vehicle||!a.entity.getPassengerList().equals(a.passengers)||a.entity.getYaw()!=a.yaw||a.entity.getPitch()!=a.pitch)return false;
                if(a.entity instanceof ServerPlayerEntity p){var d=DescentPlayerData.get(p);if(!Objects.equals(FootGravitySystem.isActive(p)&&!a.flight?FootGravitySystem.get(p):null,a.foot)||d.isEnabled()!=a.flight||!pure(d.getFlightVelocity()).equals(a.flightVelocity)||!pure(d.shipForward(p)).equals(a.flightForward)||!pure(d.shipUp(p)).equals(a.flightUp)||!a.flight&&!Objects.equals(BoardingSystem.state(p),a.boarding))return false;}
            }
            for(var token:ragdolls.values())if(!token.current())return false;
            for(var token:drones.values())if(!token.current())return false;
            for(var token:ordnanceFrames.values())if(!token.current())return false;
            return true;
        }
        boolean apply(){
            if(!current()||!validate())return false;applied=true;
            // Hull physics, pose tracking and broadphase have exactly one authority.
            for(var h:hulls){BodyJointSystem.portalTransfer(h.entity,transform);h.entity.crossPortal(transform);h.entity.setVelocity(h.entity.surfaceVelocityAt(h.entity.getPos()).multiply(.05));h.entity.velocityModified=true;}
            registry.portalMoved(hulls.stream().map(Hull::entity).toList());
            for(var a:actors)if(!(a.entity instanceof ServerPlayerEntity)){
                if(a.entity instanceof com.terminaldetector.drmd.entity.RagdollEntity ragdoll){if(!ragdolls.get(ragdoll).apply())throw new IllegalStateException("validated same-thread ragdoll portal state changed during commit");}
                else applyActor(a);
            }
            PortalEntitySync.publish(members);
            for(var a:actors)if(a.entity instanceof ServerPlayerEntity)applyActor(a);
            return true;
        }
        private void applyActor(Actor a){
            var e=a.entity;var feet=transform.transformPoint(a.before);Vec3 forward=transform.rotation().rotate(a.forward),head=transform.rotation().rotate(a.headForward);
            Vec3 movedUp=transform.rotation().rotate(a.actorUp);var boardingFrame=a.boarding==null?null:transform.transformOrientation(a.boarding.frame());
            if(boardingFrame!=null){forward=boardingFrame.inverse().rotate(forward);head=boardingFrame.inverse().rotate(head);}
            else if(a.foot==null&&movedUp.lengthSquared()>.5){forward=pure(ActorFrameMath.toLocal(mc(forward),mc(movedUp)));head=pure(ActorFrameMath.toLocal(mc(head),mc(movedUp)));}
            float yaw=(float)Math.toDegrees(Math.atan2(-forward.x(),forward.z())),pitch=(float)-Math.toDegrees(Math.atan2(forward.y(),Math.hypot(forward.x(),forward.z())));
            var drone=e instanceof com.terminaldetector.drmd.entity.DroneEntity d?drones.get(d):null;if(drone!=null){yaw=drone.destinationYaw();pitch=drone.destinationPitch();}
            if(e instanceof ServerPlayerEntity p){
                FlightBodySystem.discontinuity(p);var data=DescentPlayerData.get(p);var f=transform.rotation().rotate(a.flightForward);var u=transform.rotation().rotate(a.flightUp);var v=transform.rotation().rotate(a.flightVelocity);
                data.setShipAttitude((float)f.x(),(float)f.y(),(float)f.z(),(float)u.x(),(float)u.y(),(float)u.z());data.setFlightVelocity(mc(v));
                com.terminaldetector.drmd.world.LocalOrientation.setUp(p.getUuid(),mc(transform.rotation().rotate(pure(com.terminaldetector.drmd.world.LocalOrientation.getUp(p.getUuid())))));
                if(a.foot!=null)FootGravitySystem.adoptServer(p.getUuid(),mc(movedUp),a.foot.label());
                if(p.networkHandler!=null)p.networkHandler.requestTeleport(feet.x(),feet.y(),feet.z(),yaw,pitch,Set.of());else p.refreshPositionAndAngles(feet.x(),feet.y(),feet.z(),yaw,pitch);
                if(a.boarding!=null)BoardingSystem.portalTransfer(p,a.boarding,transform,bodyIds);
                if(p.networkHandler!=null&&ServerPlayNetworking.canSend(p,com.terminaldetector.drmd.network.ModNetworking.PortalFramePayload.ID))ServerPlayNetworking.send(p,new com.terminaldetector.drmd.network.ModNetworking.PortalFramePayload(data.nextPortalEpoch(),mc(f),mc(u),mc(transform.transformVelocity(a.velocity))));
            }else e.refreshPositionAndAngles(feet.x(),feet.y(),feet.z(),yaw,pitch);
            if(a.foot!=null){e.calculateDimensions();if(e instanceof ServerPlayerEntity p&&p.networkHandler!=null&&ServerPlayNetworking.canSend(p,com.terminaldetector.drmd.network.ModNetworking.SyncPayload.ID))com.terminaldetector.drmd.network.ModNetworking.syncPlayer(p,DescentPlayerData.get(p));}
            if(a.foot==null&&movedUp.lengthSquared()>.5)((ActorFrameAccess)e).drmd$setActorUp(mc(movedUp));
            if(e instanceof LivingEntity living&&!(e instanceof net.minecraft.entity.player.PlayerEntity)){living.bodyYaw=yaw;living.prevBodyYaw=yaw;living.setHeadYaw((float)Math.toDegrees(Math.atan2(-head.x(),head.z())));living.prevHeadYaw=living.getHeadYaw();}
            e.setVelocity(mc(transform.transformVelocity(a.velocity)));e.velocityModified=true;e.fallDistance=0;
            if(drone!=null)drone.commit();
            var ordnance=e instanceof AerialBombEntity bomb?ordnanceFrames.get(bomb):null;if(ordnance!=null)ordnance.commit();
        }
    }
}
