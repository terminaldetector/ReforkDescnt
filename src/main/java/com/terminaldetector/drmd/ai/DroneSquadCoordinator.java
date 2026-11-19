package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.TypeFilter;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Own-LOS radio observations. Receivers get remembered coordinates, never a live enemy route. */
public final class DroneSquadCoordinator {
    public static final int MAX_ALLIES=32,MAX_CANDIDATES=96;
    private static final Map<ServerWorld,Frame> FRAMES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Frame {
        final DroneSquadMailbox mailbox=new DroneSquadMailbox();
        int maxAllies,maxCandidates;
        long publications,deliveries;
    }
    public record Stats(int mailboxes,int maxAllies,int maxCandidates,int cleanupExamined,long cleanupTick,long publications,long deliveries,long evictions){}
    private DroneSquadCoordinator(){}
    public static void clear(){FRAMES.clear();}
    /** Reuse current game allegiance rules; class membership alone is not an alliance declaration. */
    static boolean allies(DroneEntity first,DroneEntity second){return allies((LivingEntity)first,second);}
    private static boolean allies(LivingEntity first,LivingEntity second){
        return first!=second&&first.isAlive()&&!first.isRemoved()&&second.isAlive()&&!second.isRemoved()
            &&first.getWorld()==second.getWorld()&&HostileEnvironment.isAlly(first)&&HostileEnvironment.isAlly(second)
            &&!HostileEnvironment.isHostileTarget(first,second)&&!HostileEnvironment.isHostileTarget(second,first);
    }
    private static boolean enemy(MobEntity receiver,LivingEntity target){
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.getWorld()==receiver.getWorld()
            &&HostileEnvironment.isHostileTarget(receiver,target)&&!receiver.isTeammate(target)&&receiver.canTarget(target);
    }
    public static void tick(DroneEntity scout){
        if(!(scout.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return;
        long now=world.getTime();var frame=FRAMES.computeIfAbsent(world,w->new Frame());frame.mailbox.cleanup(now);
        if(!scout.isAlive()||scout.isRemoved()||scout.isAiDisabled()||Math.floorMod(scout.age,DroneSquadMailbox.CADENCE)!=Math.floorMod(scout.getUuid().hashCode(),DroneSquadMailbox.CADENCE))return;
        var target=scout.getTarget();if(!enemy(scout,target))return;
        // Only this observing scout reads current target geometry, after it acquired
        // its own target. Receivers below validate UUID state without reading its XYZ.
        var from=scout.getEyePos();var point=target.getEyePos();
        double range=Math.min(128,scout.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
        if(!Double.isFinite(point.lengthSquared())||point.squaredDistanceTo(from)>range*range
            ||!WorldEditSafety.loadedSegment(world,from,point))return;
        if(!scout.canSee(target)||world.raycast(new RaycastContext(from,point,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,scout)).getType()!=HitResult.Type.MISS)return;
        var bodies=BodySpatialIndex.query(world,new Box(from,point).expand(1e-6));if(bodies.size()>32)return;
        for(var body:bodies)if(body.physicsRaycast(from,point).isPresent())return;
        var nearby=DroneSwarmAi.coordinationNeighbours(scout);
        frame.maxAllies=Math.max(frame.maxAllies,nearby.ids().size());frame.maxCandidates=Math.max(frame.maxCandidates,nearby.examinedCandidates());frame.publications++;
        for(var id:nearby.ids()){
            var entity=world.getEntity(id);
            if(!(entity instanceof DroneEntity receiver)||!allies(scout,receiver)||!enemy(receiver,target))continue;
            if(frame.mailbox.offer(id,scout.getUuid(),target.getUuid(),point.x,point.y,point.z,now))frame.deliveries++;
        }
    }
    /** Ground infantry joins the existing world mailbox, not a second director or an unbounded scan. */
    public static void tick(ShapesInfantryEntity scout){
        if(!(scout.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return;
        long now=world.getTime();var frame=FRAMES.computeIfAbsent(world,w->new Frame());frame.mailbox.cleanup(now);
        if(!scout.isAlive()||scout.isRemoved()||scout.isAiDisabled()
            ||Math.floorMod(scout.age,DroneSquadMailbox.CADENCE)!=Math.floorMod(scout.getUuid().hashCode(),DroneSquadMailbox.CADENCE))return;
        var target=scout.getTarget();if(!enemy(scout,target))return;
        var point=target.getEyePos();double range=Math.min(128,scout.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
        if(!FlightRouteSpace.clearSight(scout,scout.getEyePos(),point,range)||!scout.canSee(target))return;
        var candidates=new ArrayList<LivingEntity>();
        world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),scout.getBoundingBox().expand(Math.min(32,range)),
            actor->actor!=scout&&actor.isAlive()&&!actor.isRemoved(),candidates,MAX_CANDIDATES);
        int admitted=0;frame.maxCandidates=Math.max(frame.maxCandidates,candidates.size());frame.publications++;
        for(var actor:candidates){
            if(!(actor instanceof DroneEntity||actor instanceof ShapesInfantryEntity)||!(actor instanceof MobEntity receiver)
                ||!allies(scout,receiver)||!enemy(receiver,target))continue;
            if(admitted++>=MAX_ALLIES)break;
            if(frame.mailbox.offer(receiver.getUuid(),scout.getUuid(),target.getUuid(),point.x,point.y,point.z,now))frame.deliveries++;
        }
        frame.maxAllies=Math.max(frame.maxAllies,Math.min(admitted,MAX_ALLIES));
    }
    /** No enemy position, box, velocity, block position or aim reads in this validation path. */
    public static boolean valid(DroneEntity receiver,DroneSquadMailbox.Message message){return valid((MobEntity)receiver,message);}
    public static boolean valid(MobEntity receiver,DroneSquadMailbox.Message message){
        if(!(receiver instanceof DroneEntity||receiver instanceof ShapesInfantryEntity))return false;
        if(!(receiver.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!receiver.isAlive()||receiver.isRemoved())return false;
        long now=world.getTime();if(now<message.observedAt()||now>=message.expiresAt()
            ||receiver instanceof DroneEntity drone&&message.observedAt()<=drone.portalHintFloor())return false;
        var source=world.getEntity(message.scout());var target=world.getEntity(message.target());
        return source instanceof LivingEntity scout&&(scout instanceof DroneEntity||scout instanceof ShapesInfantryEntity)
            &&allies(receiver,scout)&&target instanceof LivingEntity living&&enemy(receiver,living);
    }
    public static Optional<DroneSquadMailbox.Message> message(DroneEntity receiver){return message((MobEntity)receiver);}
    public static Optional<DroneSquadMailbox.Message> message(MobEntity receiver){
        if(!(receiver.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return Optional.empty();
        var frame=FRAMES.get(world);if(frame==null)return Optional.empty();
        return frame.mailbox.peek(receiver.getUuid(),world.getTime()).filter(value->valid(receiver,value));
    }
    public static Optional<DroneSquadMailbox.Message> message(ServerWorld world,UUID receiver){
        if(!world.getServer().isOnThread())return Optional.empty();
        var entity=world.getEntity(receiver);return entity instanceof MobEntity mob&&(mob instanceof DroneEntity||mob instanceof ShapesInfantryEntity)?message(mob):Optional.empty();
    }
    public static Stats stats(ServerWorld world){
        var frame=FRAMES.get(world);if(frame==null)return new Stats(0,0,0,0,Long.MIN_VALUE,0,0,0);
        var store=frame.mailbox.stats();return new Stats(store.size(),frame.maxAllies,frame.maxCandidates,store.cleanupExamined(),store.cleanupTick(),frame.publications,frame.deliveries,store.evictions());
    }
}
