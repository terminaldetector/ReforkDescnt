package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.diag.DiagTrace;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Carries entities through a linked portal pair — the world-facing half of {@link PortalCrossing},
 * which owns the arithmetic and is tested on its own.
 *
 * <p>Shared by both linked block families rather than written twice. A charged mirror is a one-block
 * face and a portal panel is several blocks across, but nothing else about travelling through them
 * differs, so the span is a parameter and the rest is common. Copying it would have let the two drift.
 *
 * <p><b>Why this exists at all:</b> both blocks already stored their partner and did nothing with it.
 * The travel was performed by an Immersive Portals {@code Portal} entity spawned in
 * {@code ImmPtlMirrorBridge}, so with that mod absent a linked pair was decorative and said so. This
 * is that behaviour without the dependency.
 *
 * <p><b>Same dimension only, deliberately.</b> Auto-linking produces nothing else, and a
 * cross-dimension hop is a different call with its own failure modes — chunk loading at the far end,
 * the player's own dimension-change packet. A cross-dimension link carries nobody yet, exactly as
 * before.
 */
public final class PortalTravel {
	private PortalTravel() {}

	/**
	 * Ticks a traveller is ignored for after arriving.
	 *
	 * <p>Not a rate limit — a correctness guard. A teleport leaves {@code prevX/prevY/prevZ} back at the
	 * portal that was entered, so the very next tick presents a step reaching from one end of the link
	 * to the other. That segment can cross the far portal's plane on its own and throw the traveller
	 * straight back, forever. Ignoring them briefly makes the stale step harmless without depending on
	 * exactly how any one entity type refreshes its previous position.
	 */
	private static final long ARRIVAL_COOLDOWN_TICKS = 10;

	/** Traveller → world time they may travel again. Pruned as it grows; never swept on a schedule. */
	private static final Map<UUID, Long> RECENT_ARRIVALS = new HashMap<>();
    private record Face(BlockPos source,Direction facing,BlockPos partner,Direction partnerFacing,double span,long tick) {}
    /** Current owner-thread faces only. Values contain no world/entity reference. */
    private static final Map<ServerWorld,Map<BlockPos,Face>> FACES=new WeakHashMap<>();
    private static final int MAX_PENDING=128,PENDING_TICKS=80;
    private static final double GATE_DEPTH=2,RETREAT_EPS=.05;
    private record Crossing(PortalTransform.Vec3 previous,PortalTransform.Vec3 end,PortalTransform.Vec3 hit,boolean leading) {}
    private record Pending(Face face,java.lang.ref.WeakReference<Entity> entity,Crossing crossing,PortalTransform.Vec3 heldAt,long until) {}
    private static final Map<ServerWorld,Map<UUID,Pending>> WAITING=new WeakHashMap<>();
    private static void rememberFace(ServerWorld world,BlockPos source,Direction facing,BlockPos partner,Direction partnerFacing,double span,long tick){
        var faces=FACES.computeIfAbsent(world,w->new HashMap<>());
        if(faces.size()>=4096){faces.values().removeIf(f->f.tick<tick);if(faces.size()>=4096&&!faces.containsKey(source))return;}
        faces.put(source.toImmutable(),new Face(source.toImmutable(),facing,partner.toImmutable(),partnerFacing,span,tick));
    }


    /** Final phase is installed after body contacts, loose drops, flight and boarding motors. */
    public static void register(){
        // Registered after body contacts, loose drops, flight and boarding motors.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(PortalTravel::drain);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server,world)->{FACES.remove(world);COMPLETED.remove(world);WAITING.remove(world);});
    }
    /** A real ticking source may record crossings while its destination is still warming. */
    public static void noteSource(ServerWorld world,BlockPos source,Direction facing,BlockPos partner,double span){
        if(world.getServer().isOnThread()&&source!=null&&partner!=null&&facing!=null&&Double.isFinite(span)&&span>0&&span<=4)
            rememberFace(world,source,facing,partner,null,span,world.getServer().getTicks());
    }
    public static void cancelSource(ServerWorld world,BlockPos source){
        if(!world.getServer().isOnThread())return;
        var faces=FACES.get(world);if(faces!=null)faces.remove(source);
        var waiting=WAITING.get(world);if(waiting!=null)waiting.values().removeIf(p->p.face.source.equals(source));
    }
    public static void enqueue(ServerWorld world,BlockPos pos,Direction facing,BlockPos partner,Direction partnerFacing,double halfSpan){
        if(world.getServer().isOnThread())rememberFace(world,pos,facing,partner,partnerFacing,halfSpan,world.getServer().getTicks());
    }
    /** Direct synchronous API. Production block ticks enqueue and the final movement phase drains. */
    public static void carry(ServerWorld world,BlockPos pos,Direction facing,BlockPos partner,Direction partnerFacing,double halfSpan){
        if(!world.getServer().isOnThread())return;
        var face=new Face(pos.toImmutable(),facing,partner.toImmutable(),partnerFacing,halfSpan,world.getServer().getTicks());
        rememberFace(world,pos,facing,partner,partnerFacing,halfSpan,face.tick);process(world,java.util.List.of(face),false);
    }
    private record Completed(java.lang.ref.WeakReference<net.minecraft.entity.ItemEntity> item,Vec3d start,Vec3d end,long tick) {}
    private static final Map<ServerWorld,Map<UUID,Completed>> COMPLETED=new WeakHashMap<>();
    /** Records one actual completed deferred move; it never moves an item during another solver. */
    public static boolean afterDeferredMove(ServerWorld world,net.minecraft.entity.ItemEntity item,Vec3d actualStart){
        if(!world.getServer().isOnThread()||item.isRemoved()||item.getWorld()!=world||item.hasVehicle()
            ||!Double.isFinite(actualStart.lengthSquared()))return false;
        var map=COMPLETED.computeIfAbsent(world,w->new java.util.LinkedHashMap<>());long tick=world.getServer().getTicks();
        map.values().removeIf(p->p.tick!=tick||p.item.get()==null);
        if(map.size()>=128&&!map.containsKey(item.getUuid()))return false;
        map.put(item.getUuid(),new Completed(new java.lang.ref.WeakReference<>(item),actualStart,item.getPos(),tick));return true;
    }
    public static void drain(ServerWorld world){
        if(!world.getServer().isOnThread())return;long tick=world.getServer().getTicks();
        var observed=FACES.get(world);var offered=new java.util.LinkedHashMap<BlockPos,Face>();
        if(observed!=null)for(var face:java.util.List.copyOf(observed.values()))if(face.tick==tick)offered.put(face.source,face);
        var waiting=WAITING.get(world);
        if(waiting!=null){waiting.values().removeIf(p->!stillAtGate(world,p,tick));
            for(var p:waiting.values())offered.putIfAbsent(p.face.source,new Face(p.face.source,p.face.facing,p.face.partner,null,p.face.span,tick));}
        var usable=new java.util.ArrayList<Face>();
        for(var face:offered.values()){
            var resolved=resolveFace(world,face);
            if(resolved==null)cancelSource(world,face.source);else usable.add(resolved);
        }
        usable.sort(java.util.Comparator.comparingInt((Face f)->f.source.getX()).thenComparingInt(f->f.source.getY()).thenComparingInt(f->f.source.getZ()));process(world,usable,true);
        if(observed!=null)observed.values().removeIf(f->f.tick<=tick);COMPLETED.remove(world);
        if(waiting!=null&&waiting.isEmpty())WAITING.remove(world);
    }
    private static java.util.LinkedHashSet<Entity> candidates(ServerWorld world,Face face){
        Box reach=new Box(face.source).expand(face.span+33);var result=new java.util.LinkedHashSet<Entity>();
        for(var e:world.getEntitiesByClass(Entity.class,reach,e->!e.isSpectator()))result.add(e.getRootVehicle());
        result.addAll(com.terminaldetector.drmd.world.contraption.BodySpatialIndex.query(world,reach));
        for(var p:com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.players(world))if(!p.isSpectator())result.add(p.getRootVehicle());
        var completed=COMPLETED.get(world);if(completed!=null)for(var c:completed.values()){
            var item=c.item.get();if(item!=null&&c.tick==face.tick&&item.getWorld()==world&&!item.isRemoved())result.add(item);
        }
        var waiting=WAITING.get(world);if(waiting!=null)for(var pending:waiting.values())if(sameLink(pending.face,face)){
            var entity=pending.entity.get();if(entity!=null)result.add(entity);
        }
        return result;
    }
    private static void process(ServerWorld world,java.util.List<Face> faces,boolean pendingAllowed){
        var processed=new java.util.HashSet<UUID>();var refused=new java.util.HashMap<BlockPos,java.util.Set<UUID>>();
        // All coherent physical/riding groups go first across ALL faces, then independent actors.
        for(int phase=0;phase<2;phase++)for(var face:faces){
            var sourceState=com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(face.source);
            boolean gun=sourceState.isPresent()&&sourceState.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock
                &&sourceState.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.CHANNEL)>0;
            for(var entity:candidates(world,face)){
                boolean group=entity instanceof com.terminaldetector.drmd.world.contraption.BlockBodyEntity||entity.hasPassengers();
                if((phase==0)!=group||entity.hasVehicle()||processed.contains(entity.getUuid())||refused.getOrDefault(face.source,java.util.Set.of()).contains(entity.getUuid())||onCooldown(entity.getUuid(),face.tick))continue;
                var previous=new PortalTransform.Vec3(entity.prevX,entity.prevY,entity.prevZ);boolean solverComplete=false,genuine=false;
                if(entity instanceof com.terminaldetector.drmd.world.contraption.BlockBodyEntity body&&!body.boardingMotionPaused()){
                    var motion=body.boardingMotion();var localPivot=body.rotation().inverse().rotate(toPure(body.getPos()).minus(body.massPosition()));
                    previous=motion.position(0).plus(motion.rotation(0).rotate(localPivot));genuine=true;
                }
                if(entity instanceof net.minecraft.server.network.ServerPlayerEntity&&entity instanceof com.terminaldetector.drmd.world.portal.gun.PortalMotionAccess motion){var start=motion.drmd$portalMovementStart(face.tick);if(start!=null){previous=toPure(start);genuine=true;}}
                var completed=COMPLETED.get(world);var actual=completed==null?null:completed.get(entity.getUuid());
                if(actual!=null){if(actual.item.get()!=entity||!entity.getPos().equals(actual.end))continue;previous=toPure(actual.start);solverComplete=true;genuine=true;}
                if(pendingAllowed&&entity.age==0&&!genuine)continue;
                var result=attempt(world,entity,previous,face,gun,solverComplete,pendingAllowed);
                if(result!=null){
                    if(onCooldown(entity.getUuid(),face.tick))for(var member:result.members())processed.add(member.getUuid());
                    else{var blocked=refused.computeIfAbsent(face.source,k->new java.util.HashSet<>());for(var member:result.members())blocked.add(member.getUuid());}
                }
            }
        }
    }
    /** Null means stale/dead source; null target-facing means a genuine unresolved native exit. */
    private static Face resolveFace(ServerWorld world,Face f){
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);var a=access.readIfLoaded(f.source);if(a.isEmpty())return null;
        boolean panel=a.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock;
        boolean mirror=a.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock;
        if(panel){
            if(!a.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.LINKED)||a.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.FACING)!=f.facing
                ||!(world.getBlockEntity(f.source) instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity source)||!source.isLinked()||!f.partner.equals(source.getLinkPartnerPos())
                ||source.getLinkPartnerDim()!=null&&!source.getLinkPartnerDim().equals(world.getRegistryKey()))return null;
        }else if(mirror){
            if(!a.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.LINKED)||a.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.FACING)!=f.facing
                ||!(world.getBlockEntity(f.source) instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity source)||!source.isLinked()||!f.partner.equals(source.getLinkPartnerPos())
                ||source.getLinkPartnerDim()!=null&&!source.getLinkPartnerDim().equals(world.getRegistryKey()))return null;
        }else return null;
        var b=access.readIfLoaded(f.partner);if(b.isEmpty())return new Face(f.source,f.facing,f.partner,null,f.span,f.tick);
        if(panel&&b.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock
            &&b.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.LINKED)
            &&world.getBlockEntity(f.partner) instanceof com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity target
            &&target.isLinked()&&f.source.equals(target.getLinkPartnerPos())&&(target.getLinkPartnerDim()==null||target.getLinkPartnerDim().equals(world.getRegistryKey())))
            return new Face(f.source,f.facing,f.partner,b.get().get(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.FACING),f.span,f.tick);
        if(mirror&&b.get().getBlock() instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock
            &&b.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.LINKED)
            &&world.getBlockEntity(f.partner) instanceof com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity target
            &&target.isLinked()&&f.source.equals(target.getLinkPartnerPos())&&(target.getLinkPartnerDim()==null||target.getLinkPartnerDim().equals(world.getRegistryKey())))
            return new Face(f.source,f.facing,f.partner,b.get().get(com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock.FACING),f.span,f.tick);
        return null;
    }
    private static boolean sameLink(Face a,Face b){return a.source.equals(b.source)&&a.facing==b.facing&&a.partner.equals(b.partner)&&a.span==b.span;}
    private static PortalTransform.Vec3 plane(Face f){return toPure(Vec3d.ofCenter(f.source).add(Vec3d.of(f.facing.getVector()).multiply(.5)));}
    private static boolean stillAtGate(ServerWorld world,Pending pending,long tick){
        var entity=pending.entity.get();if(entity==null||entity.isRemoved()||!entity.isAlive()||entity.isSpectator()||entity.getWorld()!=world||entity.getRootVehicle()!=entity||tick>=pending.until||tick<pending.face.tick)return false;
        var current=toPure(entity.getPos());if(current.minus(pending.heldAt).lengthSquared()>GATE_DEPTH*GATE_DEPTH)return false;
        var n=toPure(Vec3d.of(pending.face.facing.getVector()));var reference=pending.crossing.leading?PortalTransfer.leading(entity,current,n):null;
        var point=pending.crossing.leading?(reference==null?null:reference.after()):current;if(point==null)return false;
        double depth=point.minus(plane(pending.face)).dot(n);
        return depth<=RETREAT_EPS&&depth>=-GATE_DEPTH&&PortalCrossing.withinFace(point,plane(pending.face),n,pending.face.span+.125);
    }
    private static void rememberCrossing(ServerWorld world,Entity entity,Face face,Crossing crossing){
        var pending=new Pending(face,new java.lang.ref.WeakReference<>(entity),crossing,toPure(entity.getPos()),face.tick+PENDING_TICKS);
        if(!stillAtGate(world,pending,face.tick))return;
        var waiting=WAITING.computeIfAbsent(world,w->new java.util.LinkedHashMap<>());
        if(waiting.size()<MAX_PENDING||waiting.containsKey(entity.getUuid()))waiting.putIfAbsent(entity.getUuid(),pending);
    }
    private static Crossing crossing(ServerWorld world,Entity entity,PortalTransform.Vec3 previous,Face f){
        var normal=Vec3d.of(f.facing.getVector());var face=Vec3d.ofCenter(f.source).add(normal.multiply(.5));var plane=toPure(face);var n=toPure(normal);var current=toPure(entity.getPos());
        var leading=PortalTransfer.leading(entity,previous,n);if(leading==null)return null;PortalTransform.Vec3 hit;boolean edge;
        if(PortalCrossing.crossedInward(leading.before(),leading.after(),plane,n)){hit=PortalCrossing.crossingPoint(leading.before(),leading.after(),plane,n);edge=true;}
        else if(PortalCrossing.crossedInward(previous,current,plane,n)){hit=PortalCrossing.crossingPoint(previous,current,plane,n);edge=false;}else return null;
        return hit!=null&&PortalCrossing.withinFace(hit,plane,n,f.span)&&faceIsOpenTo(world,entity,face,normal,hit)?new Crossing(previous,current,hit,edge):null;
    }
    private static PortalTransfer.Attempt attempt(ServerWorld world,Entity entity,PortalTransform.Vec3 previous,Face f,boolean gun,boolean solverComplete,boolean pendingAllowed){
        long now=world.getServer().getTicks();if(onCooldown(entity.getUuid(),now))return null;
        var waiting=WAITING.get(world);var stored=waiting==null?null:waiting.get(entity.getUuid());
        var pending=stored!=null&&sameLink(stored.face,f)?stored:null;
        if(pending!=null&&!stillAtGate(world,pending,now)){waiting.remove(entity.getUuid());pending=null;}
        var step=pending==null?crossing(world,entity,previous,f):pending.crossing;if(step==null)return null;
        if(f.partnerFacing==null){
            var group=PortalTransfer.gather(world,entity,id->onCooldown(id,now));
            if(pendingAllowed&&pending==null&&group.complete())rememberCrossing(world,entity,f,step);
            return new PortalTransfer.Attempt(group.members(),null);
        }
        var n=toPure(Vec3d.of(f.facing.getVector()));var current=toPure(entity.getPos());
        var edge=step.leading?PortalTransfer.leading(entity,current,n):null;if(step.leading&&edge==null)return null;
        var reference=step.leading?edge.after():current;
        var partnerNormal=Vec3d.of(f.partnerFacing.getVector());
        var transform=com.terminaldetector.drmd.d6.D6PortalTransform.of(plane(f),n,toPure(Vec3d.ofCenter(f.partner).add(partnerNormal.multiply(.5))),toPure(partnerNormal));
        var residual=transform.transformVelocity(reference.minus(step.hit));
        var attempt=pending==null?PortalTransfer.prepare(world,entity,transform,residual,step.hit,f.span,gun,now,id->onCooldown(id,now),solverComplete)
            :PortalTransfer.prepareCurrent(world,entity,transform,residual,step.hit,f.span,gun,now,id->onCooldown(id,now));
        if(attempt.accepted()==null||!attempt.accepted().apply()){
            if(pendingAllowed&&!PortalDestinationCollision.available(world,new Box(f.partner).expand(f.span+2))){if(pending==null)rememberCrossing(world,entity,f,step);}
            else if(waiting!=null)waiting.remove(entity.getUuid());
            DiagTrace.count("portal.blockedGroup");return attempt;
        }
        for(var member:attempt.members()){markArrived(member.getUuid(),now);if(waiting!=null)waiting.remove(member.getUuid());}
        DiagTrace.record("portal","carried group of "+attempt.members().size()+" through "+f.source+" to "+f.partner);DiagTrace.count("portal.carried");return attempt;
    }

    public static boolean exitIsClear(ServerWorld world,Entity traveller,Vec3d destination) {
        if(!Double.isFinite(destination.lengthSquared()))return false;
        Box body=traveller.getBoundingBox().offset(destination.subtract(traveller.getPos()));
        if(body.minY<world.getBottomY() || body.maxY>world.getTopY())return false;
        var min=BlockPos.ofFloored(body.minX,body.minY,body.minZ);
        var max=BlockPos.ofFloored(body.maxX-1e-7,body.maxY-1e-7,body.maxZ-1e-7);
        if(!world.getWorldBorder().contains(min) || !world.getWorldBorder().contains(max))return false;
        int x0=(min.getX()-1)>>4,x1=(max.getX()+1)>>4,z0=(min.getZ()-1)>>4,z1=(max.getZ()+1)>>4;
        int y0=(min.getY()-1)>>4,y1=(max.getY()+1)>>4;
        if((long)(x1-x0+1)*(z1-z0+1)*(y1-y0+1)>64)return false;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return world.isSpaceEmpty(traveller,body);
    }

	/**
	 * Whether the crossing point is on the open part of the portal plane, rather than through the wall
	 * beside it.
	 *
	 * <p><b>The gap this closes.</b> {@link PortalCrossing#withinFace} measures the <em>nominal</em>
	 * span — how far from the anchor a crossing may land — and nothing measured whether the plane is
	 * actually open that far. For a charged mirror the span is three quarters of a block, so the
	 * question never arose. For a portal panel it is two either side, a five-block plane of which one
	 * block is the panel and the other twenty-four are whatever happens to be there. Mounted on a wall,
	 * that plane continues straight through it, and anyone in the next room crossing it at the same
	 * depth was carried — through solid rock, from a portal they could not see.
	 *
	 * <p>One ray from the anchor's face to the crossing point settles it: if the plane is walled off
	 * between the two, they are not on the same face.
	 *
	 * <p>Both ends are nudged a little along the normal, onto the side the traveller came from. A ray
	 * running exactly along a block face grazes it unpredictably, and the approach side is by
	 * definition open — the traveller was standing in it.
	 *
	 * <p>Costs nothing in the common case: a crossing within a block of the anchor is on the anchor's
	 * own face by construction, which covers every mirror and the middle of every panel, and returns
	 * before any ray is cast.
	 */
	private static boolean faceIsOpenTo(ServerWorld world, Entity traveller, Vec3d faceCentre,
			Vec3d normal, PortalTransform.Vec3 hit) {
		Vec3d hitPoint = new Vec3d(hit.x(), hit.y(), hit.z());
		if (faceCentre.squaredDistanceTo(hitPoint) <= 1.0) return true;

		Vec3d offset = normal.multiply(APPROACH_NUDGE);
        if(!com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(world,faceCentre.add(offset),hitPoint.add(offset)))return false;
		BlockHitResult blocked = world.raycast(new RaycastContext(
				faceCentre.add(offset), hitPoint.add(offset),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, traveller));
		return blocked.getType() == HitResult.Type.MISS;
	}

	/** How far off the plane the reachability ray runs, in blocks — enough not to graze the face. */
	private static final double APPROACH_NUDGE = 0.05;

	private static boolean onCooldown(UUID id, long now) {
		Long until = RECENT_ARRIVALS.get(id);
        return until != null && now < until;
	}

	private static void markArrived(UUID id, long now) {
		// Bounded without a scheduled sweep: entries only ever expire, so clearing the stale ones
		// whenever the map grows keeps it the size of "travellers in flight" rather than of everyone
		// who has ever used a portal on this server.
		if (RECENT_ARRIVALS.size() > 64) {
			RECENT_ARRIVALS.entrySet().removeIf(e -> now >= e.getValue());
		}
		RECENT_ARRIVALS.put(id, now + ARRIVAL_COOLDOWN_TICKS);
	}

	public static void clear() { RECENT_ARRIVALS.clear();COMPLETED.clear();FACES.clear();WAITING.clear(); }

	private static Vec3d fromPure(PortalTransform.Vec3 v) { return new Vec3d(v.x(), v.y(), v.z()); }

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}
}
