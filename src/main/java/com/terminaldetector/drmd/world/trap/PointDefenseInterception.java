package com.terminaldetector.drmd.world.trap;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.PhysicsTarget;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.projectile.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded stationary point defense. Ordinary missiles retain their normal immediate impacts;
 * only marked defensive rounds defer their own terminal side effects within this world tick. */
public final class PointDefenseInterception {
    public static final int MAX_CANDIDATES=96,MAX_DETAILED=32,MAX_ROUNDS=256,MAX_SOURCES=256;
    private static final double ROUND_SPEED=DescentMod.su(5000)/DescentMod.TICKS_PER_SECOND;
    private static final Map<ServerWorld,State> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean registered;
    public record Stats(int activeRounds,int cooldownSources,int candidates,int detailed,int blockRays,
                        int hullTests,int actorTests,int cleanupExamined,long emitted,long contacts,long refusals){}
    public record Receipt(UUID missile,UUID round,long worldTime,long bornAt,double fraction,Vec3d point,
                          double missileCover,double roundCover,boolean roundMovedFirst,int missileAge,int roundAge){}
    private static final class Budget {int rays=32,hulls=32,actors=32,detailed,candidates;}
    private static final class Frame {
        final long time;final Vec3d start,velocity;final Box box;final ProjectileEntity.DefenseContact contact;
        final double cover;boolean integrated;
        Frame(long time,Vec3d start,Vec3d velocity,Box box,ProjectileEntity.DefenseContact contact){
            this.time=time;this.start=start;this.velocity=velocity;this.box=box;this.contact=contact;
            cover=contact==null?Double.POSITIVE_INFINITY:fraction(start,start.add(velocity),contact.tracePoint());
        }
    }
    private static final class Round {
        final WeakReference<ProjectileEntity> entity;final UUID target;Frame frame;
        Round(ProjectileEntity e){entity=new WeakReference<>(e);target=e.pointDefenseSource().target();}
    }
    private static final class State {
        final Map<UUID,Round> rounds=new LinkedHashMap<>();
        final Map<UUID,Set<UUID>> targets=new HashMap<>();
        final Map<Long,Long> cooldowns=new LinkedHashMap<>();
        final Deque<Receipt> receipts=new ArrayDeque<>();
        long cleanupAt=Long.MIN_VALUE,emitted,contacts,refusals;
        int maxCandidates,maxDetailed,maxRays,maxHulls,maxActors,maxCleanup;
        Budget emission;
    }
    private record Trace(boolean admitted,ProjectileEntity.DefenseContact contact){}
    private PointDefenseInterception(){}

    /** Common mod initializer owns registration; the guard prevents duplicate lifecycle callbacks. */
    public static synchronized void register(){
        if(registered)return;registered=true;
        ServerTickEvents.END_WORLD_TICK.register(PointDefenseInterception::finishWorld);
        ServerWorldEvents.UNLOAD.register((server,world)->WORLDS.remove(world));
    }
    private static State state(ServerWorld world){return WORLDS.computeIfAbsent(world,w->new State());}
    public static Vec3d muzzle(BlockPos source){return Vec3d.ofCenter(source).add(0,.6,0);}
    public static Stats stats(ServerWorld world){var s=state(world);return new Stats(s.rounds.size(),s.cooldowns.size(),s.maxCandidates,s.maxDetailed,s.maxRays,s.maxHulls,s.maxActors,s.maxCleanup,s.emitted,s.contacts,s.refusals);}
    public static List<Receipt> receipts(ServerWorld world){return List.copyOf(state(world).receipts);}
    private static boolean finite(Vec3d v){return v!=null&&Double.isFinite(v.lengthSquared());}
    private static void metrics(State s,Budget b){s.maxCandidates=Math.max(s.maxCandidates,b.candidates);s.maxDetailed=Math.max(s.maxDetailed,b.detailed);
        s.maxRays=Math.max(s.maxRays,32-b.rays);s.maxHulls=Math.max(s.maxHulls,32-b.hulls);s.maxActors=Math.max(s.maxActors,32-b.actors);}
    private static void cleanup(State s,long now){
        if(s.cleanupAt!=Long.MIN_VALUE&&now-s.cleanupAt<5)return;s.cleanupAt=now;int examined=0;
        var it=s.cooldowns.entrySet().iterator();while(it.hasNext()&&examined<32){var entry=it.next();examined++;if(entry.getValue()<=now)it.remove();}
        s.maxCleanup=Math.max(s.maxCleanup,examined);
    }
    private static boolean sourceLoaded(ServerWorld world,BlockPos source){return registered&&world!=null&&source!=null&&world.getServer().isOnThread()
        &&WorldEditSafety.neighbourhoodLoaded(world,source)&&world.getWorldBorder().contains(source)
        &&world.shouldTickEntity(source)&&world.getBlockState(source).isOf(ModWorldBlocks.POINT_DEFENSE_TURRET);}
    private static boolean hostileMissile(ServerWorld world,ProjectileEntity missile){
        if(missile==null||!missile.isAlive()||missile.getWorld()!=world||!missile.isPointDefenseMissile()||missile.getOwnerUuid()==null)return false;
        var owner=world.getEntity(missile.getOwnerUuid());
        return owner instanceof LivingEntity living&&living.isAlive()&&living.getWorld()==world
            &&((DefenseTurrets.PointDefenseTurretBlock)ModWorldBlocks.POINT_DEFENSE_TURRET).acceptsMissileOwner(living);
    }

    public static boolean tryFire(ServerWorld world,BlockPos source){
        if(!sourceLoaded(world,source))return false;var s=state(world);long now=world.getTime();cleanup(s,now);
        if(s.emission!=null||s.rounds.size()>=MAX_ROUNDS||s.cooldowns.getOrDefault(source.asLong(),Long.MIN_VALUE)>now
            ||!s.cooldowns.containsKey(source.asLong())&&s.cooldowns.size()>=MAX_SOURCES){s.refusals++;return false;}
        var candidates=new ArrayList<ProjectileEntity>();
        world.collectEntitiesByType(TypeFilter.instanceOf(ProjectileEntity.class),new Box(source).expand(12),Entity::isAlive,candidates,MAX_CANDIDATES);
        var budget=new Budget();budget.candidates=candidates.size();s.emission=budget;
        try{
            var from=muzzle(source);
            for(var missile:candidates){
                if(budget.detailed>=MAX_DETAILED)break;budget.detailed++;
                if(!hostileMissile(world,missile)||!finite(missile.getVelocity()))continue;
                var offset=missile.getBoundingBox().getCenter().subtract(from);var velocity=missile.getVelocity();
                if(offset.lengthSquared()>12*12||velocity.lengthSquared()<1e-8||offset.dotProduct(velocity)>=0)continue;
                double approach=-offset.dotProduct(velocity)/velocity.lengthSquared();
                if(approach<=0||approach>16||!new Box(source).expand(missile.splashRadius()).raycast(missile.getPos(),missile.getPos().add(velocity.multiply(approach))).isPresent())continue;
                var lead=InterceptAim.solve(v(offset),v(velocity),ROUND_SPEED,9);
                if(lead.isEmpty())continue;var direction=mc(lead.get().direction());
                var aim=from.add(direction.multiply(ROUND_SPEED*lead.get().ticks()));
                var trace=trace(world,missile,from,aim,budget);
                if(!trace.admitted()||trace.contact()!=null)continue;
                var cfg=ProjectileFramework.config(ProjectileKind.KINETIC,null,from,direction);
                cfg.directDamage=4;cfg.life=.5f;cfg.inherit=0;cfg.colorR=255;cfg.colorG=178;cfg.colorB=51;cfg.visualScale=.65f;
                var shot=WeaponCore.fireAnchoredProjectile(world,source,cfg,missile.getUuid());
                if(shot==null)continue;
                s.cooldowns.put(source.asLong(),now+6);s.emitted++;return true;
            }
            return false;
        }finally{metrics(s,budget);s.emission=null;}
    }

    /** Factory validation belongs to the real server block muzzle; target UUID conveys no hit authority. */
    public static boolean mayLaunch(ServerWorld world,BlockPos source,WeaponCore.FireConfig cfg,UUID target){
        if(!sourceLoaded(world,source)||cfg==null||target==null||cfg.owner!=null||cfg.dmgClass!=DamageClass.KINETIC
            ||cfg.homing||cfg.pilotGuided||cfg.pierceCount!=0||cfg.worldBlast||cfg.drillCarve||cfg.fusionPortalCharge
            ||cfg.fuse!=FuseType.IMPACT||cfg.armSeconds!=0||cfg.splashRadius!=0||cfg.splashDamage!=0
            ||cfg.igniteOnHit||!Float.isFinite(cfg.directDamage)||cfg.directDamage<=0
            ||!Float.isFinite(cfg.life)||cfg.life<=0||cfg.life>.5f||!finite(cfg.pos)||!finite(cfg.dir)||cfg.dir.lengthSquared()<1e-12
            ||cfg.pos.squaredDistanceTo(muzzle(source))>1e-12||!Float.isFinite(cfg.speed)||cfg.speed<=0
            ||!Float.isFinite(cfg.gravity)||cfg.gravity<0||cfg.gravity>1||!Float.isFinite(cfg.drag)||cfg.drag<0||cfg.drag>1)return false;
        var loaded=world.getEntity(target);
        double speed=cfg.speedIsSourceUnits?DescentMod.su(cfg.speed)/DescentMod.TICKS_PER_SECOND:cfg.speed;
        if(!Double.isFinite(speed)||speed<=0||speed>ROUND_SPEED+1e-9)return false;
        if(!(loaded instanceof ProjectileEntity missile)||!hostileMissile(world,missile)
            ||missile.getBoundingBox().getCenter().squaredDistanceTo(cfg.pos)>12*12)return false;
        var s=state(world);if(s.rounds.size()>=MAX_ROUNDS)return false;var b=s.emission==null?new Budget():s.emission;
        var exit=Vec3d.ofCenter(source).add(0,.501,0);var clearance=trace(world,null,exit,cfg.pos,b);
        if(s.emission==null)metrics(s,b);
        return clearance.admitted()&&clearance.contact()==null;
    }
    public static void track(ProjectileEntity shot){
        if(!(shot.getWorld() instanceof ServerWorld world)||shot.pointDefenseSource()==null||!registered)return;
        var s=state(world);if(s.rounds.containsKey(shot.getUuid())||s.rounds.size()>=MAX_ROUNDS)return;
        var r=new Round(shot);s.rounds.put(shot.getUuid(),r);s.targets.computeIfAbsent(r.target,k->new LinkedHashSet<>()).add(shot.getUuid());
    }
    public static boolean mayRestore(ProjectileEntity shot){
        if(!(shot.getWorld() instanceof ServerWorld world)||shot.pointDefenseSource()==null
            ||!sourceLoaded(world,shot.pointDefenseSource().anchor()))return false;
        var loaded=world.getEntity(shot.pointDefenseSource().target());
        return loaded instanceof ProjectileEntity missile&&hostileMissile(world,missile);
    }
    private static void remove(State s,UUID id){var r=s.rounds.remove(id);if(r==null)return;var set=s.targets.get(r.target);if(set!=null){set.remove(id);if(set.isEmpty())s.targets.remove(r.target);}}

    /** No unbounded normal entity queries: admission and per-opportunity narrowphase share these budgets. */
    private static Trace trace(ServerWorld world,ProjectileEntity shot,Vec3d from,Vec3d to,Budget b){
        if(!finite(from)||!finite(to)||!WorldEditSafety.loadedSegment(world,from,to)||b.rays<=0)return new Trace(false,null);
        b.rays--;
        var context=shot==null?net.minecraft.block.ShapeContext.absent():net.minecraft.block.ShapeContext.of(shot);
        var block=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,context));
        ProjectileEntity.DefenseContact best=block.getType()==HitResult.Type.BLOCK?new ProjectileEntity.DefenseContact(block.getPos(),block.getPos(),null,0,block.getBlockPos(),block.getSide()):null;
        double nearest=best==null?Double.POSITIVE_INFINITY:from.squaredDistanceTo(best.tracePoint());
        var area=new Box(from,to).expand(.5);var bodies=BodySpatialIndex.query(world,area);
        if(bodies.size()>b.hulls)return new Trace(false,null);
        for(var body:bodies){b.hulls--;var hit=body.physicsProjectileRaycast(from,to);if(hit.isPresent()&&from.squaredDistanceTo(hit.get().tracePoint())<nearest){
            nearest=from.squaredDistanceTo(hit.get().tracePoint());best=new ProjectileEntity.DefenseContact(hit.get().tracePoint(),hit.get().materialPoint(),body.getUuid(),body.getId(),null,null);}}
        var actors=new ArrayList<Entity>();
        world.collectEntitiesByType(TypeFilter.instanceOf(Entity.class),area,e->e!=shot&&!(e instanceof BlockBodyEntity)&&(e instanceof LivingEntity||e instanceof PhysicsTarget)&&e.isAlive(),actors,b.actors+1);
        if(actors.size()>b.actors)return new Trace(false,null);
        for(var actor:actors){b.actors--;var hit=actor instanceof PhysicsTarget physical?physical.physicsProjectileRaycast(from,to)
            :actor.getBoundingBox().expand(.3).raycast(from,to).map(p->new PhysicsTarget.ProjectileHit(p,p));
            if(hit.isPresent()&&from.squaredDistanceTo(hit.get().tracePoint())<nearest){nearest=from.squaredDistanceTo(hit.get().tracePoint());best=new ProjectileEntity.DefenseContact(hit.get().tracePoint(),hit.get().materialPoint(),actor.getUuid(),actor.getId(),null,null);}}
        return new Trace(true,best);
    }

    private static Frame prepare(ServerWorld world,State s,Round r,ProjectileEntity shot,boolean ownTick){
        long now=world.getTime();if(r.frame!=null&&r.frame.time==now)return r.frame;
        if(!shot.isAlive()||!world.shouldTickEntity(shot.getBlockPos())||shot.remainingLifeTicks()<(ownTick?1:2))return null;
        var velocity=shot.pointDefenseStepVelocity();if(!finite(velocity)||velocity.lengthSquared()>256*256)return null;
        var budget=new Budget();var result=trace(world,shot,shot.getPos(),shot.getPos().add(velocity),budget);metrics(s,budget);
        if(!result.admitted()){s.refusals++;return null;}
        return r.frame=new Frame(now,shot.getPos(),velocity,shot.getBoundingBox(),result.contact());
    }
    public static void tickRound(ProjectileEntity shot){
        if(!(shot.getWorld() instanceof ServerWorld world))return;track(shot);var s=state(world);var r=s.rounds.get(shot.getUuid());
        if(r==null){shot.discard();return;}var frame=prepare(world,s,r,shot,true);
        if(frame==null){shot.discard();return;}
        if(!frame.integrated){shot.setVelocity(frame.velocity);shot.synchronizeVisualVelocity();
            shot.setPosition(frame.contact==null?frame.start.add(frame.velocity):frame.contact.tracePoint());frame.integrated=true;}
    }

    /** Invoked only with the incoming missile's actual computed movement and ordinary nearest hit. */
    public static boolean intercept(ProjectileEntity missile,Vec3d from,Vec3d to,HitResult ordinary){
        if(!(missile.getWorld() instanceof ServerWorld world)||!registered)return false;
        var s=WORLDS.get(world);if(s==null)return false;var associated=s.targets.get(missile.getUuid());if(associated==null)return false;
        if(!hostileMissile(world,missile)||!missile.isArmed()||!WorldEditSafety.loadedSegment(world,from,to))return false;
        double cover=ordinary==null?Double.POSITIVE_INFINITY:fraction(from,to,ordinary.getPos());
        Round winner=null;ProjectileEntity round=null;RelativeProjectileSweep.Contact earliest=null;
        for(var id:List.copyOf(associated)){
            var candidate=s.rounds.get(id);var entity=candidate==null?null:candidate.entity.get();
            if(entity==null||!entity.isAlive()||entity.getWorld()!=world||entity.pointDefenseSource()==null
                ||entity.pointDefenseSource().eligibleFrom()>world.getTime()||entity.pointDefenseSource().bornAt()>=world.getTime())continue;
            var frame=prepare(world,s,candidate,entity,false);if(frame==null)continue;
            var targetBox=missile.getBoundingBox();var contact=RelativeProjectileSweep.firstContact(v(frame.box.getCenter()),v(frame.velocity),half(frame.box),
                v(targetBox.getCenter()),v(to.subtract(from)),half(targetBox));
            if(contact.isEmpty()||contact.get().fraction()>=cover-1e-9||contact.get().fraction()>=frame.cover-1e-9)continue;
            if(earliest==null||contact.get().fraction()<earliest.fraction()){earliest=contact.get();winner=candidate;round=entity;}
        }
        if(earliest==null)return false;
        var frame=winner.frame;var point=mc(earliest.point());
        var receipt=new Receipt(missile.getUuid(),round.getUuid(),world.getTime(),round.pointDefenseSource().bornAt(),earliest.fraction(),point,cover,frame.cover,frame.integrated,missile.age,round.age);
        if(s.receipts.size()>=32)s.receipts.removeFirst();s.receipts.addLast(receipt);s.contacts++;
        // Real pair contact, original missile owner/fuse/blast/payload, exactly once.
        missile.pointDefensePairHit(round,point);
        round.pointDefensePairHit(missile,point);
        remove(s,round.getUuid());return true;
    }

    private static void finishWorld(ServerWorld world){
        var s=WORLDS.get(world);if(s==null)return;
        for(var entry:List.copyOf(s.rounds.entrySet())){var r=entry.getValue();var shot=r.entity.get();
            if(shot==null||!shot.isAlive()){remove(s,entry.getKey());continue;}
            if(r.frame!=null&&r.frame.time==world.getTime()&&r.frame.integrated&&r.frame.contact!=null)shot.pointDefenseOrdinaryHit(r.frame.contact);
            r.frame=null;if(!shot.isAlive())remove(s,entry.getKey());
        }
        cleanup(s,world.getTime());
    }
    private static double fraction(Vec3d a,Vec3d b,Vec3d p){var d=b.subtract(a);double n=d.lengthSquared();return n<1e-18?0:MathHelper.clamp(p.subtract(a).dotProduct(d)/n,0,1);}
    private static Vec3 v(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    private static Vec3d mc(Vec3 p){return new Vec3d(p.x(),p.y(),p.z());}
    private static Vec3 half(Box b){return new Vec3(b.getLengthX()*.5,b.getLengthY()*.5,b.getLengthZ()*.5);}
}
