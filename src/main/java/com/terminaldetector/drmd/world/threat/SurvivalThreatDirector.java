package com.terminaldetector.drmd.world.threat;

import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.OrbEntity;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Confirmed Survival attacks or naturally observed finite timed recon; existing drone AI owns combat.
 * This is independent of retired column generation. Full siege/economy is a separate stage. */
public final class SurvivalThreatDirector {
    public static final int INTERVAL=5,PLAYER_CHECKS=4,CANDIDATE_CHECKS=8,SIGHT_CHECKS=8,SPAWNS=1,PATROL_TTL=4_800;
    private static final String MARKER="drmd.threat",OWNER="drmd.threat.owner=",UNTIL="drmd.threat.until=",FACTION="drmd.threat.faction="+SurvivalThreatState.FACTION;
    public record Stats(int players,int roster,int candidates,int sight,int spawned){}
    private static final class Frame {
        long tick=Long.MIN_VALUE;int cursor,candidates,sight,spawned,roster;
        final WeakHashMap<ServerPlayerEntity,Integer> ages=new WeakHashMap<>();
        final Map<UUID,Integer> sites=new HashMap<>();final Set<UUID> admitting=new HashSet<>();
        Stats last=new Stats(0,0,0,0,0);
    }
    private record Tag(UUID owner,long expires){}
    private static final Map<ServerWorld,Frame> FRAMES=new WeakHashMap<>();
    private SurvivalThreatDirector(){}
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(SurvivalThreatDirector::tick);
        ServerEntityEvents.ENTITY_LOAD.register(SurvivalThreatDirector::loaded);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(!(entity.getWorld() instanceof ServerWorld world))return;
            if(entity instanceof ServerPlayerEntity p){cancel(world,p.getUuid());return;}
            if(!(entity instanceof DroneEntity drone))return;
            var state=SurvivalThreatState.get(world);
            if(!state.reconAvailable())return;
            if(state.hasActor(drone.getUuid())||tag(drone)!=null){retireActor(world,state,drone.getUuid(),world.getTime());return;}
            if(source.getAttacker() instanceof ServerPlayerEntity p)confirmedKill(world,p,drone);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->cancel(handler.player.getServerWorld(),handler.player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,from,to)->cancel(from,player.getUuid()));
        ServerWorldEvents.UNLOAD.register((server,world)->FRAMES.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->FRAMES.clear());
    }
    public static Stats stats(ServerWorld world){var f=FRAMES.get(world);return f==null?new Stats(0,0,0,0,0):f.last;}
    private static boolean eligible(ServerWorld world,ServerPlayerEntity p){
        return p.isAlive()&&!p.isRemoved()&&p.interactionManager.getGameMode()==GameMode.SURVIVAL&&!p.isCreative()&&!p.isSpectator()
            &&p.getServerWorld()==world&&p.networkHandler!=null&&world.getServer().getPlayerManager().getPlayer(p.getUuid())==p
            &&world.getEntity(p.getUuid())==p&&Double.isFinite(p.getPos().lengthSquared())&&Math.abs(p.getY())<=30_000_000
            &&world.getWorldBorder().contains(p.getBoundingBox());
    }
    private static ReconAdmission.Context context(ServerWorld world){
        var backend=WorldAccess.of(world).backend();return backend==CubicAvailability.Backend.UNSUPPORTED?null:
            new ReconAdmission.Context(world.getRegistryKey().getValue().toString(),world.getSeed(),backend.name(),world.getBottomY(),world.getTopY());
    }
    private static boolean enabled(ServerWorld w){return w.getDifficulty()!=Difficulty.PEACEFUL&&w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);}
    /** Public for server integrations; a nearest player or a merely damaged/live alias is not an alarm. */
    public static boolean confirmedKill(ServerWorld world,ServerPlayerEntity player,DroneEntity victim){
        if(!world.getServer().isOnThread()||!eligible(world,player)||!enabled(world)||player.getWorld()!=world||victim.getWorld()!=world
                ||victim.isAlive()||world.getEntity(victim.getUuid())!=victim||tag(victim)!=null)return false;
        var state=SurvivalThreatState.get(world);
        return state.reconAvailable()&&!state.hasActor(victim.getUuid())&&state.alarm(player.getUuid(),victim.getUuid(),point(victim.getPos()),world.getTime());
    }
    /** Exact saved patrol ownership only; ordinary drones never consult a global roster scan. */
    public static Vec3d retreatFrom(DroneEntity drone){
        if(drone==null||!(drone.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()
            ||!drone.isAlive()||drone.isRemoved()||!drone.getCommandTags().contains(MARKER))return null;
        var tag=tag(drone);if(tag==null||world.getEntity(drone.getUuid())!=drone)return null;
        var state=SurvivalThreatState.get(world);if(!state.reconAvailable())return null;
        var actor=state.actor(drone.getUuid());if(actor==null||!actor.owner().equals(tag.owner()))return null;
        var progress=state.progress(actor.owner()).orElse(null);
        return progress!=null&&progress.phase()==ThreatProgress.Phase.COOLDOWN?vector(progress.known()):null;
    }
    private static ThreatProgress.Point point(Vec3d p){return new ThreatProgress.Point(p.x,p.y,p.z);}
    private static Vec3d vector(ThreatProgress.Point p){return new Vec3d(p.x(),p.y(),p.z());}
    private static void cancel(ServerWorld world,UUID owner){if(world.getServer().isOnThread())SurvivalThreatState.get(world).cancel(owner,world.getTime());}
    private static Tag tag(Entity entity){
        if(!entity.getCommandTags().contains(MARKER)||!entity.getCommandTags().contains(FACTION))return null;
        UUID owner=null;Long until=null;
        try{for(var value:entity.getCommandTags()){
            if(value.startsWith(OWNER)){if(owner!=null)return null;owner=UUID.fromString(value.substring(OWNER.length()));}
            if(value.startsWith(UNTIL)){if(until!=null)return null;until=Long.parseLong(value.substring(UNTIL.length()));}
        }}catch(RuntimeException invalid){return null;}
        return owner==null||until==null||until<0?null:new Tag(owner,until);
    }
    private static void expiry(Entity entity,long until){entity.getCommandTags().removeIf(s->s.startsWith(UNTIL));entity.addCommandTag(UNTIL+until);}
    private static void loaded(Entity entity,ServerWorld world){
        if(!(entity instanceof DroneEntity))return;var tag=tag(entity);if(tag==null)return;
        var frame=FRAMES.get(world);if(frame!=null&&frame.admitting.contains(entity.getUuid()))return;
        var state=SurvivalThreatState.get(world);var actor=state.actor(entity.getUuid());
        if(!state.reconAvailable())return; // Unknown saved authority is not an empty/forgotten roster.
        // Forgotten/expired unloaded patrols cannot return outside the live roster's cap.
        if(actor==null||!actor.owner().equals(tag.owner)||actor.expires()<=world.getTime()){
            retireActor(world,state,entity.getUuid(),world.getTime());entity.discard();return;
        }
        if(state.scoutActor(entity.getUuid())&&!scoutType(entity)){retireActor(world,state,entity.getUuid(),world.getTime());entity.discard();return;}
        // Saved finite encounters, not Vanilla distance despawn, own this pair's retirement.
        // Timed unprovoked recon retains its existing lifecycle unchanged.
        if(!state.timedActor(entity.getUuid()))((DroneEntity)entity).setPersistent();
        if(tag.expires!=actor.expires())expiry(entity,actor.expires());
    }
    private static boolean loaded(ServerWorld world,Box area){
        var access=WorldAccess.of(world);
        for(int x=MathHelper.floor(area.minX)>>4;x<=MathHelper.floor(area.maxX)>>4;x++)
            for(int y=MathHelper.floor(area.minY)>>4;y<=MathHelper.floor(area.maxY)>>4;y++)
                for(int z=MathHelper.floor(area.minZ)>>4;z<=MathHelper.floor(area.maxZ)>>4;z++){
                    var cube=new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z);
                    if(CubicAvailability.withinHeight(world,cube)&&!access.isVolumeAvailable(cube))return false;
                }
        return true;
    }
    private static boolean sees(ServerWorld world,DroneEntity drone,ServerPlayerEntity player,Frame frame){
        if(frame.sight>=SIGHT_CHECKS||drone.getTarget()!=player||!drone.isAlive()||drone.isRemoved()||drone.isAiDisabled()
            ||drone.getWorld()!=world||world.getEntity(drone.getUuid())!=drone||!eligible(world,player)
            ||!HostileEnvironment.isHostileTarget(drone,player)||drone.isTeammate(player)||!drone.canTarget(player)||drone.squaredDistanceTo(player)>96*96)return false;
        frame.sight++;var from=drone.getEyePos();var to=player.getEyePos();
        if(!WorldEditSafety.loadedSegment(world,from,to)||world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,drone)).getType()!=HitResult.Type.MISS)return false;
        var bodies=BodySpatialIndex.query(world,new Box(from,to).expand(.01));if(bodies.size()>16)return false;
        for(var body:bodies)if(body.physicsRaycast(from,to).isPresent())return false;
        return true;
    }
    private static boolean scoutType(Entity entity){return entity instanceof OrbEntity orb&&orb.getRole()==AiRole.SUPPORT;}
    /** Event/load/cleanup transitions happen before the owner loop snapshots its
     * old phase. Commit first, then notify the same actual connected owner once. */
    private static void retireActor(ServerWorld world,SurvivalThreatState state,UUID id,long now){
        var actor=state.actor(id);if(actor==null)return;
        var before=state.progress(actor.owner()).map(ThreatProgress.Snapshot::phase).orElse(null);
        state.actorDied(id,now);
        var after=state.progress(actor.owner()).map(ThreatProgress.Snapshot::phase).orElse(null);
        if(before!=after&&after!=null){
            var owner=world.getServer().getPlayerManager().getPlayer(actor.owner());
            if(owner!=null&&eligible(world,owner))notice(owner,after);
        }
    }
    private static void cleanup(ServerWorld world,SurvivalThreatState state,Frame frame,long now){
        for(var entry:state.actors()){
            frame.roster++;var entity=world.getEntity(entry.getKey());
            if(entity!=null&&(!entity.isAlive()||entity.isRemoved())){retireActor(world,state,entry.getKey(),now);continue;}
            if(entity!=null&&state.scoutActor(entry.getKey())&&!scoutType(entity)){retireActor(world,state,entry.getKey(),now);entity.discard();continue;}
            if(now<entry.getValue().expires())continue;
            if(state.timedActor(entry.getKey())){retireActor(world,state,entry.getKey(),now);if(entity!=null)entity.discard();continue;}
            if(entity instanceof DroneEntity drone&&drone.getTarget() instanceof ServerPlayerEntity target&&eligible(world,target)
                    &&(frame.sight>=SIGHT_CHECKS||sees(world,drone,target,frame))){
                long until=ThreatProgress.deadline(now,200);state.extendActor(entry.getKey(),until);expiry(drone,until);continue;
            }
            if(entity!=null)entity.discard();state.removeActor(entry.getKey());
        }
    }
    public static void tick(ServerWorld world){
        if(!world.getServer().isOnThread())throw new IllegalStateException("Survival encounters belong to the world thread");
        long tick=world.getServer().getTicks();if(tick%INTERVAL!=0)return;
        var frame=FRAMES.computeIfAbsent(world,w->new Frame());if(frame.tick==tick)return;frame.tick=tick;
        frame.candidates=frame.sight=frame.spawned=frame.roster=0;var state=SurvivalThreatState.get(world);long now=world.getTime();
        if(!state.reconAvailable()){frame.last=new Stats(0,0,0,0,0);return;}
        cleanup(world,state,frame,now);var players=world.getPlayers();int checked=0;
        for(int i=0;i<Math.min(PLAYER_CHECKS,players.size());i++){
            var p=players.get(Math.floorMod(frame.cursor++,players.size()));checked++;
            if(!eligible(world,p)||!enabled(world)){state.cancel(p.getUuid(),now);frame.ages.remove(p);continue;}
            var progress=state.player(p.getUuid());if(progress==null)continue;
            var old=progress.snapshot();Integer age=frame.ages.put(p,p.age);int online=age==null?0:Math.max(0,p.age-age);
            var actualContext=context(world);var timed=state.reconProgress(p.getUuid()).filter(r->!r.closed()).orElse(null);
            if(timed!=null&&(!timed.context().equals(actualContext)||now<timed.started()||now>=timed.expires()
                    ||p.getPos().squaredDistanceTo(vector(timed.origin()))>160*160
                    ||!timed.observed()&&!state.hasActor(timed.scout())))state.retireTimed(p.getUuid(),now);
            ThreatProgress.Point observed=null;
            for(var actor:state.actors())if(actor.getValue().owner().equals(p.getUuid())){
                frame.roster++;var entity=world.getEntity(actor.getKey());
                if(entity instanceof DroneEntity drone&&sees(world,drone,p,frame)){
                    observed=point(p.getPos());
                    if(state.scoutActor(drone.getUuid())&&state.observeScout(p.getUuid(),drone.getUuid(),actualContext,observed,now))
                        expiry(drone,state.actor(drone.getUuid()).expires());
                    break;
                }
            }
            progress.advance(now,online,p.getPos().squaredDistanceTo(vector(progress.origin()))<=160*160,observed,state.rosterSize(p.getUuid())==0,!state.timed(p.getUuid()));
            if(progress.phase()==ThreatProgress.Phase.COOLDOWN){
                state.retireTimed(p.getUuid(),now);
                // The original encounter deadline also ends visible non-timed patrols. A cooldown
                // cannot leave an armed pair extending its own lifetime on every fresh sighting.
                state.cancel(p.getUuid(),now);
            }
            if(!old.equals(progress.snapshot()))state.markDirty();
            if(old.phase()!=progress.phase())notice(p,progress.phase());
            if(frame.spawned<SPAWNS&&frame.candidates<CANDIDATE_CHECKS){
                if(actualContext!=null&&state.canScout(p.getUuid()))spawn(world,p,progress,state,frame,now,true,actualContext);
                else if(state.canDeploy(p.getUuid()))spawn(world,p,progress,state,frame,now,false,actualContext);
            }
        }
        frame.last=new Stats(checked,frame.roster,frame.candidates,frame.sight,frame.spawned);
    }
    private static void notice(ServerPlayerEntity player,ThreatProgress.Phase phase){
        String key=switch(phase){case WARNING->"warning";case SEARCHING->"search";case HUNTING,SIEGE->"hunt";case COOLDOWN->"cooldown";case QUIET->null;};
        if(key!=null)feedback(player,key);
    }
    private static void feedback(ServerPlayerEntity player,String key){
        try{player.sendMessage(Text.translatable("message.drmd.threat."+key,Text.translatable("faction.drmd.hostile_machines")),false);}
        catch(RuntimeException lostFeedback){/* committed encounter state is authoritative; never retry it */}
    }
    private static boolean playerTooClose(ServerWorld world,Vec3d at){
        var nearby=new ArrayList<ServerPlayerEntity>(1);
        world.collectEntitiesByType(TypeFilter.instanceOf(ServerPlayerEntity.class),Box.of(at,48,48,48),
            p->p.isAlive()&&!p.isSpectator()&&p.getPos().squaredDistanceTo(at)<24*24,nearby,1);
        return !nearby.isEmpty();
    }
    private static void spawn(ServerWorld world,ServerPlayerEntity owner,ThreatProgress progress,SurvivalThreatState state,Frame frame,long now,
                              boolean scout,ReconAdmission.Context actualContext){
        int cursor=frame.sites.getOrDefault(owner.getUuid(),0);var origin=scout?owner.getPos():vector(progress.origin());
        while(frame.candidates<CANDIDATE_CHECKS){
            int index=cursor++&63;frame.candidates++;double angle=Math.PI*2*((index+(owner.getUuid().hashCode()&15))&15)/16;
            var at=origin.add(Math.cos(angle)*(40+(index%3)*8),4+(index%3)*2,Math.sin(angle)*(40+(index%3)*8));
            var pos=BlockPos.ofFloored(at);
            if(world.isOutOfHeightLimit(pos)||!world.shouldTickEntity(pos))continue;
            if(playerTooClose(world,at))continue;
            var drone=ModEntities.ORB.create(world);if(drone==null)continue;
            if(scout)drone.applyRole(AiRole.SUPPORT);
            drone.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
            var box=drone.getBoundingBox();if(world.isOutOfHeightLimit(BlockPos.ofFloored(box.minX,box.minY,box.minZ))
                    ||world.isOutOfHeightLimit(BlockPos.ofFloored(box.maxX,Math.nextDown(box.maxY),box.maxZ))
                    ||!world.getWorldBorder().contains(box)||!loaded(world,box.expand(1))||!world.isSpaceEmpty(drone,box))continue;
            var bounds=new BodyBounds(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ));var shape=OrientedBox.axisAligned(bounds);
            var bodies=BodySpatialIndex.query(world,box);if(bodies.size()>16||bodies.stream().anyMatch(b->b.collisionQuery().contacts(shape,0).stream().anyMatch(hit->hit.penetration()>1e-6)))continue;
            boolean timed=!scout&&state.timed(owner.getUuid());
            if(!scout&&!timed)drone.setPersistent();
            long expires=scout?ThreatProgress.deadline(now,ReconAdmission.UNSEEN_TICKS):timed?state.timedExpiry(owner.getUuid()):ThreatProgress.deadline(now,PATROL_TTL);
            if(!drone.addCommandTag(MARKER)||!drone.addCommandTag(FACTION)||!drone.addCommandTag(OWNER+owner.getUuid())||!drone.addCommandTag(UNTIL+expires))continue;
            if(scout&&!state.reserveScout(owner.getUuid(),drone.getUuid(),actualContext,point(origin),now)
                    ||timed&&!state.reserveTimedAssault(owner.getUuid(),drone.getUuid(),now))break;
            frame.admitting.add(drone.getUuid());
            boolean accepted=false;
            try{
                accepted=world.spawnEntity(drone)||world.getEntity(drone.getUuid())==drone;
                if(!accepted)continue;
                if(!scout&&!timed&&!state.addActor(owner.getUuid(),drone.getUuid(),expires)){drone.discard();continue;}
                frame.spawned++;break;
            }finally{
                if(!accepted&&world.getEntity(drone.getUuid())!=drone){
                    if(scout)state.rejectUnspawnedScout(owner.getUuid(),drone.getUuid());
                    else if(timed)state.removeActor(drone.getUuid()); // issued lifetime slot deliberately remains consumed
                }
                frame.admitting.remove(drone.getUuid());
                if(accepted&&scout)feedback(owner,"recon");
            }
        }
        frame.sites.put(owner.getUuid(),cursor&63);
    }
}
