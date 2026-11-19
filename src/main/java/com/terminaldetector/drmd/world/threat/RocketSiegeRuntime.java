package com.terminaldetector.drmd.world.threat;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Authored, opt-in live path on the existing director and persisted SiegePlan protocol.
 * No automatic recon replacement, unseen base discovery, new faction or saved autostart.
 * Reloaded intent alone cannot re-arm NPCs: a live deployment transaction owns each pair. */
public final class RocketSiegeRuntime {
    private static final String MARKER="drmd.rocket_siege.v1",PLAN="drmd.rocket_siege.plan=";
    private static final Map<ServerWorld,LinkedHashMap<UUID,Pair>> WORLDS=new WeakHashMap<>();
    private static final int MAX_PAIRS=4,MAX_SIGHT=32;
    public record Deployment(UUID plan,UUID first,UUID second,long warningEnds){}
    public record Stats(int pairs,int actors,int sight,int shots){}
    private static final Map<ServerWorld,Stats> STATS=new WeakHashMap<>();
    private static final Map<ServerWorld,long[]> SIGHT=new WeakHashMap<>();
    private static boolean spendSight(ServerWorld world){var b=SIGHT.computeIfAbsent(world,w->new long[]{Long.MIN_VALUE,0});if(b[0]!=world.getTime()){b[0]=world.getTime();b[1]=0;}if(b[1]>=MAX_SIGHT)return false;b[1]++;return true;}
    private static final class Pair {
        final SiegePlan plan;final BlockPos site;final BlockState material;final ShapesInfantryEntity[] actors;
        final Map<UUID,SiegePlan.FirePermit> firing=new HashMap<>();final long[] nextMove={0,0};
        Pair(SiegePlan plan,BlockPos site,BlockState material,ShapesInfantryEntity first,ShapesInfantryEntity second){this.plan=plan;this.site=site.toImmutable();this.material=material;actors=new ShapesInfantryEntity[]{first,second};}
    }
    private RocketSiegeRuntime(){}
    public static boolean ordered(ShapesInfantryEntity mob){return mob.getCommandTags().stream().anyMatch(t->t.startsWith("drmd.rocket_siege."));}
    private static Pair pair(ShapesInfantryEntity mob){
        if(!(mob.getWorld() instanceof ServerWorld world)||!mob.getCommandTags().contains(MARKER))return null;
        UUID id=null;try{for(var tag:mob.getCommandTags())if(tag.startsWith(PLAN)){if(id!=null)return null;id=UUID.fromString(tag.substring(PLAN.length()));}}
        catch(IllegalArgumentException invalid){return null;}
        var map=WORLDS.get(world);var p=map==null?null:map.get(id);
        return p!=null&&(p.actors[0]==mob||p.actors[1]==mob)?p:null;
    }
    public static int actorCount(ServerWorld world){var map=WORLDS.get(world);if(map==null)return 0;int count=0;for(var p:map.values())for(var a:p.actors)if(a.isAlive()&&!a.isRemoved())count++;return count;}
    static boolean ownsParticipant(ServerWorld world,UUID participant){var map=WORLDS.get(world);return map!=null&&map.values().stream().anyMatch(p->p.plan.snapshot().participant().equals(participant));}
    public static Stats stats(ServerWorld world){return STATS.getOrDefault(world,new Stats(0,0,0,0));}
    static void clear(ServerWorld world){WORLDS.remove(world);STATS.remove(world);SIGHT.remove(world);}
    static void clearAll(){WORLDS.clear();STATS.clear();SIGHT.clear();}
    static void loaded(ShapesInfantryEntity mob){if(ordered(mob)&&pair(mob)==null)mob.discard();}
    private static ServerPlayerEntity owner(ServerWorld w,Pair p){var id=p.plan.snapshot().participant();return w.getServer().getPlayerManager().getPlayer(id);}
    private static boolean participant(ServerWorld w,Pair p){
        var player=owner(w,p);var state=SurvivalThreatState.get(w);var saved=state.siegePlan(p.plan.snapshot().id()).orElse(null);
        return player!=null&&SurvivalThreatDirector.eligible(w,player)&&SurvivalThreatDirector.enabled(w)&&!state.siegePlansReadOnly()
            &&saved!=null&&saved.participant().equals(player.getUuid())&&saved.actors().equals(p.plan.snapshot().actors())&&saved.world().equals(w.getRegistryKey().getValue().toString());
    }
    private static Vec3d point(ThreatProgress.Point p){return new Vec3d(p.x(),p.y(),p.z());}
    private static boolean inside(ServerWorld w,Pair p){var owner=owner(w,p);return owner!=null&&owner.getPos().squaredDistanceTo(point(p.plan.snapshot().origin().point()))<=160*160;}
    private static Optional<Vec3d> observed(ServerWorld world,Pair pair,ShapesInfantryEntity actor){
        if(!actor.isAlive()||actor.isRemoved()||actor.isAiDisabled()||actor.getWorld()!=world||!WorldEditSafety.neighbourhoodLoaded(world,pair.site))return Optional.empty();
        var material=world.getBlockState(pair.site);boolean matching=material.equals(pair.material)
            ||world.getBlockEntity(pair.site) instanceof com.terminaldetector.drmd.world.micro.CarvedBlockEntity carved&&carved.source().equals(pair.material)&&!carved.thermalReadOnly();
        if(!matching)return Optional.empty();var from=actor.getEyePos();var to=Vec3d.ofCenter(pair.site);
        if(from.squaredDistanceTo(to)>64*64||!WorldEditSafety.loadedSegment(world,from,to)||!spendSight(world))return Optional.empty();
        var hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(pair.site))return Optional.empty();
        var bodies=BodySpatialIndex.query(world,new Box(from,hit.getPos()).expand(.01));if(bodies.size()>32)return Optional.empty();
        for(var body:bodies)if(body.physicsRaycast(from,hit.getPos()).isPresent())return Optional.empty();return Optional.of(hit.getPos());
    }
    private static SiegePlan.Observation observation(ServerWorld w,Pair pair,ShapesInfantryEntity actor,Vec3d point){var origin=pair.plan.snapshot().origin();return new SiegePlan.Observation(origin.site(),actor.getUuid(),origin.participant(),origin.world(),w.getTime(),new ThreatProgress.Point(point.x,point.y,point.z),null,null);}
    static Optional<Deployment> deploy(ServerWorld world,ServerPlayerEntity player,BlockPos site,Vec3d first,Vec3d second){
        if(!world.getServer().isOnThread()||!SurvivalThreatDirector.eligible(world,player)||!SurvivalThreatDirector.enabled(world))return Optional.empty();
        var state=SurvivalThreatState.get(world);var progress=state.progress(player.getUuid()).orElse(null);var map=WORLDS.computeIfAbsent(world,w->new LinkedHashMap<>());
        if(!state.reconAvailable()||state.siegePlansReadOnly()||progress==null||progress.played()!=ThreatProgress.GRACE_TICKS
            ||progress.phase()!=ThreatProgress.Phase.QUIET||progress.pending()||state.rosterSize(player.getUuid())!=0
            ||state.actorCount()+actorCount(world)+2>SurvivalThreatState.MAX_ACTORS||map.size()>=MAX_PAIRS
            ||!WorldEditSafety.mayChange(world,site,player)||site.toCenterPos().squaredDistanceTo(player.getPos())>32*32)return Optional.empty();
        var source=world.getBlockState(site);if(source.isAir()||source.getHardness(world,site)<0||source.hasBlockEntity())return Optional.empty();
        var a=ModEntities.SHAPES_INFANTRY.create(world);var b=ModEntities.SHAPES_INFANTRY.create(world);if(a==null||b==null)return Optional.empty();
        var actors=List.of(a,b);int index=0;for(var actor:actors){var at=index++==0?first:second;
            if(!Double.isFinite(at.lengthSquared())||at.squaredDistanceTo(site.toCenterPos())<16*16||at.squaredDistanceTo(site.toCenterPos())>48*48
                ||!world.shouldTickEntity(BlockPos.ofFloored(at))||!WorldEditSafety.loadedSegment(world,at,site.toCenterPos())||!ActorFrames.canOccupy(actor,at,new Vec3d(0,1,0)))return Optional.empty();
            actor.refreshPositionAndAngles(at.x,at.y,at.z,0,0);actor.equipStack(EquipmentSlot.MAINHAND,new ItemStack(ModItems.ROCKET_DUAL));
            var box=actor.getBoundingBox();var hulls=BodySpatialIndex.query(world,box);if(hulls.size()>32)return Optional.empty();
            var shape=com.terminaldetector.drmd.physics.collision.OrientedBox.axisAligned(new com.terminaldetector.drmd.physics.collision.BodyBounds(
                new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(box.minX,box.minY,box.minZ),new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(box.maxX,box.maxY,box.maxZ)));
            if(hulls.stream().anyMatch(h->h.collisionQuery().contacts(shape,0).stream().anyMatch(contact->contact.penetration()>1e-6)))return Optional.empty();
            var floor=BlockPos.ofFloored(at.add(0,-.01,0));if(!WorldEditSafety.neighbourhoodLoaded(world,floor)||world.getBlockState(floor).getCollisionShape(world,floor).isEmpty())return Optional.empty();
        }
        if(a.getBoundingBox().intersects(b.getBoundingBox()))return Optional.empty();
        long now=world.getTime();var id=UUID.randomUUID();var siteId=UUID.randomUUID();var observed=new SiegePlan.Observation(siteId,a.getUuid(),player.getUuid(),world.getRegistryKey().getValue().toString(),now,
            new ThreatProgress.Point(site.getX()+.5,site.getY()+.5,site.getZ()+.5),null,null);
        var plan=new SiegePlan(new SiegePlan.Snapshot(id,player.getUuid(),observed.world(),SiegePlan.Phase.PLANNED,now,now,progress.played(),now,now,0,0,0,observed,observed,new SiegePlan.Bindings(a.getUuid(),b.getUuid()),List.of()));
        var pair=new Pair(plan,site,source,a,b);if(observed(world,pair,a).isEmpty()||observed(world,pair,b).isEmpty())return Optional.empty();
        plan.advance(now,0,true,true,null,false);for(var actor:actors){actor.addCommandTag(MARKER);actor.addCommandTag(PLAN+id);}
        map.put(id,pair);boolean committed=false;
        try{
            if(!world.spawnEntity(a)||!world.spawnEntity(b)||!state.putSiegePlan(plan.snapshot()))return Optional.empty();
            committed=true;player.sendMessage(Text.translatable("message.drmd.threat.rocket_warning",ThreatProgress.WARNING_TICKS/20),false);
            return Optional.of(new Deployment(id,a.getUuid(),b.getUuid(),plan.snapshot().warningEnds()));
        }finally{if(!committed){map.remove(id);for(var actor:actors)actor.discard();}}
    }
    static void tick(ServerWorld world){
        var map=WORLDS.get(world);if(map==null)return;long now=world.getTime();int sight=0,shots=0;
        for(var p:List.copyOf(map.values())){
            SiegePlan.Observation seen=null;
            if(participant(world,p)&&inside(world,p))for(var a:p.actors){if(sight>=MAX_SIGHT)break;sight++;var at=observed(world,p,a);if(at.isPresent()){seen=observation(world,p,a,at.get());break;}}
            p.plan.advance(now,0,participant(world,p),inside(world,p),seen,Arrays.stream(p.actors).noneMatch(a->a.isAlive()&&!a.isRemoved()));
            var snapshot=p.plan.snapshot();SurvivalThreatState.get(world).putSiegePlan(snapshot);shots+=snapshot.acks().size();
            if(snapshot.phase()==SiegePlan.Phase.COOLDOWN||snapshot.phase()==SiegePlan.Phase.CLOSED){for(var a:p.actors)if(!a.isRemoved())a.discard();}
            if(snapshot.phase()==SiegePlan.Phase.CLOSED){SurvivalThreatState.get(world).removeSiegePlan(snapshot.id());map.remove(snapshot.id());}
        }
        var budget=SIGHT.get(world);STATS.put(world,new Stats(map.size(),actorCount(world),budget==null?0:(int)Math.min(MAX_SIGHT,budget[1]),shots));
    }
    public static boolean leasesDespawn(ShapesInfantryEntity actor){var p=pair(actor);if(p==null||!actor.isAlive()||actor.isRemoved())return false;
        var s=p.plan.snapshot();long now=actor.getWorld().getTime();return now>=s.proposedAt()&&(s.phase()==SiegePlan.Phase.RETREATING?now<s.until():s.phase()!=SiegePlan.Phase.COOLDOWN&&s.phase()!=SiegePlan.Phase.CLOSED&&now<s.encounterEnds());}
    private static Vec3d muzzle(ShapesInfantryEntity a){return a.getPos().add(0,a.getHeight()*.72,0).add(a.getRotationVec(1).multiply(.35));}
    public static boolean current(ShapesInfantryEntity actor,SiegePlan.FirePermit permit){
        var p=pair(actor);if(p==null||permit==null||p.firing.get(actor.getUuid())!=permit||!(actor.getWorld() instanceof ServerWorld w)
            ||permit.at()!=w.getTime()||!participant(w,p)||!inside(w,p)||p.plan.snapshot().phase()!=SiegePlan.Phase.SIEGE)return false;
        var actual=observed(w,p,actor);if(actual.isEmpty())return false;var at=permit.point();var point=new Vec3d(at.x(),at.y(),at.z());var from=muzzle(actor);
        if(!WorldEditSafety.loadedSegment(w,from,point))return false;
        var owner=owner(w,p);if(owner==null||!HostileEnvironment.isHostileTarget(actor,owner)||actor.isTeammate(owner)||!WorldEditSafety.mayChange(w,p.site,owner)||!spendSight(w))return false;
        var hit=w.raycast(new RaycastContext(from,point.add(point.subtract(from).normalize().multiply(.001)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(p.site))return false;var bodies=BodySpatialIndex.query(w,new Box(from,point).expand(.01));if(bodies.size()>32)return false;
        for(var b:bodies)if(b.physicsRaycast(from,point).isPresent())return false;return true;
    }
    public static void tickActor(ShapesInfantryEntity actor){
        var p=pair(actor);if(p==null||actor.isAiDisabled()||!(actor.getWorld() instanceof ServerWorld world)){actor.getNavigation().stop();return;}
        var s=p.plan.snapshot();long now=world.getTime();int slot=p.actors[0]==actor?0:1;var known=point(s.known().point());
        if(s.phase()==SiegePlan.Phase.WARNING||s.phase()==SiegePlan.Phase.PLANNED){actor.getNavigation().stop();actor.getLookControl().lookAt(known.x,known.y,known.z);return;}
        if(s.phase()==SiegePlan.Phase.COOLDOWN||s.phase()==SiegePlan.Phase.CLOSED){actor.getNavigation().stop();return;}
        boolean retreat=s.phase()==SiegePlan.Phase.RETREATING;
        if(now>=p.nextMove[slot]){
            p.nextMove[slot]=now+20;var away=actor.getPos().subtract(known).multiply(1,0,1);if(away.lengthSquared()<1e-8)away=new Vec3d(slot==0?-1:1,0,0);
            var destination=retreat?actor.getPos().add(away.normalize().multiply(12)):known;
            if((retreat||s.phase()==SiegePlan.Phase.SEARCHING||actor.getPos().squaredDistanceTo(known)>32*32)&&WorldEditSafety.loadedSegment(world,actor.getPos(),destination))
                actor.getNavigation().startMovingTo(destination.x,destination.y,destination.z,1);
            else actor.getNavigation().stop();
        }
        if(retreat)return;actor.getLookControl().lookAt(known.x,known.y,known.z);if(s.phase()!=SiegePlan.Phase.SIEGE)return;
        if(slot==1&&now<s.warningEnds()+Math.max(1,Math.round(AiRole.RPG.weaponCooldown*20)/2))return;
        var at=observed(world,p,actor);if(at.isEmpty())return;
        var permit=p.plan.requestFire(slot,actor.getUuid(),now,observation(world,p,actor,at.get()),new SiegePlan.Readiness(true,true,actor.getWeaponEnergy()>=AiRole.RPG.weaponCost,actor.getAttackCooldownTicks()==0,true));
        if(permit.isEmpty())return;p.firing.put(actor.getUuid(),permit.get());
        try{var projectile=actor.tryFireSiege(permit.get());if(projectile!=null){
            if(!p.plan.acknowledge(permit.get(),projectile.getUuid(),now))p.plan.advance(now,0,false,false,null,false);
            SurvivalThreatState.get(world).putSiegePlan(p.plan.snapshot());
        }}finally{p.firing.remove(actor.getUuid());}
    }
}
