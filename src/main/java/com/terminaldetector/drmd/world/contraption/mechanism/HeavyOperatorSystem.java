package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import java.lang.ref.WeakReference;

/** Opt-in heavy operator assembled from the same replaceable player mechanism blocks.
 * The existing joint solver turns the two axes; the existing weapon scheduler fires.
 * Versioned entity tags persist identities, not target coordinates or a new physics world. */
public final class HeavyOperatorSystem {
    public static final int CADENCE=5,MAX_ASSEMBLIES=8,MAX_BODY_VISITS=32,MAX_CANDIDATES=32,MAX_RAYS=32;
    public static final BlockPos CORE=new BlockPos(0,0,-1),YAW_DRIVE=BlockPos.ORIGIN,YAW_BASE=new BlockPos(0,1,0),
        YAW_CHILD=BlockPos.ORIGIN,PITCH_DRIVE=new BlockPos(0,1,0),PITCH_BASE=new BlockPos(1,1,0),PITCH_CHILD=BlockPos.ORIGIN,
        LASER=new BlockPos(0,0,2),LEFT=new BlockPos(-2,0,2),RIGHT=new BlockPos(2,0,2);
    private static final String ROOT="drmd.operator.v1",GUN="drmd.operator.gun.v1",YAW="drmd.operator.yaw=",BARREL="drmd.operator.barrel=",PARENT="drmd.operator.parent=";
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0);
    private static final Map<ServerWorld,Frame> FRAMES=new WeakHashMap<>();
    public record Assembly(BlockBodyEntity base,BlockBodyEntity yaw,BlockBodyEntity barrel) {
        public List<BlockBodyEntity> bodies(){return List.of(base,yaw,barrel);}
    }
    public record Stats(int visited,int assemblies,int candidates,int rays,int drives){}
    private record Permit(UUID root,UUID target,WeakReference<LivingEntity> actor,long tick){}
    private static final class Frame {
        long tick=Long.MIN_VALUE;int cursor,candidates,rays,drives;Stats stats=new Stats(0,0,0,0,0);
        final Map<UUID,Permit> permits=new HashMap<>();
    }
    private HeavyOperatorSystem(){}
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(HeavyOperatorSystem::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->FRAMES.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->FRAMES.clear());
    }
    public static Stats stats(ServerWorld world){var frame=FRAMES.get(world);return frame==null?new Stats(0,0,0,0,0):frame.stats;}
    private static UUID uuid(BlockBodyEntity body,String prefix){
        UUID id=null;try{for(String tag:body.getCommandTags())if(tag.startsWith(prefix)){if(id!=null)return null;id=UUID.fromString(tag.substring(prefix.length()));}}catch(IllegalArgumentException invalid){return null;}return id;
    }
    private static BlockBodyEntity body(ServerWorld world,UUID id){return id==null?null:BodySpatialIndex.find(world,id).filter(b->b.isAlive()).orElse(null);}
    private static boolean bearing(BlockBodyEntity b,BlockPos p,Direction direction){var s=b.getLocalBlock(p);return s.isOf(ModWorldBlocks.MECHANICAL_BEARING)&&s.get(MechanicalBearingBlock.FACING)==direction;}
    private static boolean joint(ServerWorld world,BlockBodyEntity a,BlockPos pa,BlockBodyEntity b,BlockPos pb){
        return BodyJointRegistry.get(world).forBody(a.getUuid()).stream().anyMatch(l->l.blockDriven()
            &&l.a().body().equals(a.getUuid())&&l.a().cell().equals(pa)&&l.b().body().equals(b.getUuid())&&l.b().cell().equals(pb));
    }
    private static boolean materialValid(ServerWorld world,Assembly a){
        return a!=null&&a.bodies().stream().allMatch(b->b.getWorld()==world&&!b.isRemoved()&&BodySpatialIndex.find(world,b.getUuid()).orElse(null)==b
                &&!b.controlCore().bound())
            &&a.base.getLocalBlock(CORE).isOf(ModWorldBlocks.SHIP_CONTROL_CORE)
            &&bearing(a.base,YAW_BASE,Direction.UP)&&bearing(a.yaw,YAW_CHILD,Direction.DOWN)
            &&bearing(a.yaw,PITCH_BASE,Direction.EAST)&&bearing(a.barrel,PITCH_CHILD,Direction.WEST)
            &&joint(world,a.base,YAW_BASE,a.yaw,YAW_CHILD)&&joint(world,a.yaw,PITCH_BASE,a.barrel,PITCH_CHILD);
    }
    private static boolean valid(ServerWorld world,Assembly a){return materialValid(world,a)&&a.bodies().stream().noneMatch(BlockBodyEntity::boardingMotionPaused);}
    private static Assembly resolve(ServerWorld world,BlockBodyEntity root){
        if(root==null||!root.getCommandTags().contains(ROOT)||root.getCommandTags().stream().anyMatch(t->t.startsWith("drmd.operator.")&&!t.equals(ROOT)&&!t.startsWith(YAW)&&!t.startsWith(BARREL)))return null;
        var yaw=body(world,uuid(root,YAW));var gun=body(world,uuid(root,BARREL));
        if(yaw==null||gun==null||yaw==gun||root==yaw||root==gun||!gun.getCommandTags().contains(GUN)
            ||gun.getCommandTags().stream().anyMatch(t->t.startsWith("drmd.operator.")&&!t.equals(GUN)&&!t.startsWith(PARENT))
            ||yaw.getCommandTags().stream().anyMatch(t->t.startsWith("drmd.operator.")&&!t.startsWith(PARENT))
            ||!root.getUuid().equals(uuid(yaw,PARENT))||!root.getUuid().equals(uuid(gun,PARENT)))return null;
        return new Assembly(root,yaw,gun);
    }
    /** Bind actual existing material and joints; no pose, velocity or cooldown is changed.
     * An operator command is the only automatic assembly author in this prototype. */
    public static boolean bind(ServerWorld world,Assembly a){
        // Authoring binds existing material only. Fresh contact-phase physics is
        // required separately by every runtime aim/fire admission, not before a
        // newly spawned assembly has had its first ordinary world tick.
        if(!world.getServer().isOnThread()||!materialValid(world,a)||a.bodies().stream().anyMatch(b->b.getCommandTags().stream().anyMatch(t->t.startsWith("drmd.operator."))))return false;
        if(BodySpatialIndex.loaded(world).stream().filter(b->b.getCommandTags().contains(ROOT)).limit(MAX_ASSEMBLIES).count()>=MAX_ASSEMBLIES)return false;
        a.base.addCommandTag(ROOT);a.base.addCommandTag(YAW+a.yaw.getUuid());a.base.addCommandTag(BARREL+a.barrel.getUuid());
        a.yaw.addCommandTag(PARENT+a.base.getUuid());a.barrel.addCommandTag(PARENT+a.base.getUuid());a.barrel.addCommandTag(GUN);return true;
    }
    private static BlockState bearing(Direction d){return ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,d);}
    private static BlockState drive(Direction d){return ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,d).with(Properties.POWER,0);}
    private static BlockBodyEntity create(ServerWorld world,Map<BlockPos,BlockState> cells,Vec3d at,Quat q,Vec3d v,Vec3d spin){
        var b=BlockBodyEntity.createLocal(world,cells.entrySet().stream().map(e->new BlockBodyEntity.Cell(e.getKey(),e.getValue())).toList(),at,q,v,spin);b.setPhysicsGravity(false);return b;
    }
    private static void clearAssemblyVolume(ServerWorld world,Vec3d at,Quat q){
        // A conservative authoring envelope includes the two connected components.
        // Read availability before shape queries; never turn an unknown cube into air.
        var bounds=com.terminaldetector.drmd.physics.collision.OrientedBox.local(
            new com.terminaldetector.drmd.physics.collision.BodyBounds(new Vec3(-2,-1,-2),new Vec3(5,4,3)),new Vec3(at.x,at.y,at.z),new Vec3(0,0,0),q).bounds();
        var box=new Box(bounds.min().x(),bounds.min().y(),bounds.min().z(),bounds.max().x(),bounds.max().y(),bounds.max().z());
        if(!world.getWorldBorder().contains(box)||box.minY<world.getBottomY()||box.maxY>world.getTopY())throw new IllegalArgumentException("operator outside world bounds");
        var access=WorldAccess.of(world);
        for(var p:BlockPos.iterate(BlockPos.ofFloored(box.minX,box.minY,box.minZ),BlockPos.ofFloored(box.maxX-1e-7,box.maxY-1e-7,box.maxZ-1e-7))){
            if(!WorldEditSafety.neighbourhoodLoaded(world,p))throw new IllegalArgumentException("operator volume unavailable");
            var state=access.readIfLoaded(p);if(state.isEmpty()||!state.get().getCollisionShape(world,p).isEmpty())throw new IllegalArgumentException("operator volume obstructed");
        }
        var actors=new ArrayList<net.minecraft.entity.Entity>();world.collectEntitiesByType(TypeFilter.instanceOf(net.minecraft.entity.Entity.class),box,e->e.isAlive()&&!e.isSpectator(),actors,1);
        if(!actors.isEmpty())throw new IllegalArgumentException("operator volume occupied");
    }
    /** Prototype authoring primitive. Every block remains normally editable/salvageable.
     * It is not a survival item grant, spawn weight or prefab turret recipe. */
    public static Assembly assemble(ServerWorld world,Vec3d at,Quat q,Vec3d velocity,Vec3d angularVelocity){
        if(!world.getServer().isOnThread()||!Double.isFinite(at.lengthSquared()+velocity.lengthSquared()+angularVelocity.lengthSquared()))throw new IllegalArgumentException("finite server assembly required");
        clearAssemblyVolume(world,at,q);
        var floor=new LinkedHashMap<BlockPos,BlockState>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)floor.put(new BlockPos(x,-1,z),Blocks.IRON_BLOCK.getDefaultState());
        floor.put(CORE,ModWorldBlocks.SHIP_CONTROL_CORE.getDefaultState());floor.put(YAW_DRIVE,drive(Direction.UP));floor.put(YAW_BASE,bearing(Direction.UP));
        for(var p:List.of(new BlockPos(-1,0,0),new BlockPos(1,0,0),new BlockPos(0,0,1)))floor.put(p,ModWorldBlocks.SHIP_THRUSTER.getDefaultState());
        var middle=new LinkedHashMap<BlockPos,BlockState>();middle.put(YAW_CHILD,bearing(Direction.DOWN));middle.put(PITCH_DRIVE,drive(Direction.EAST));middle.put(PITCH_BASE,bearing(Direction.EAST));
        var guns=new LinkedHashMap<BlockPos,BlockState>();guns.put(PITCH_CHILD,bearing(Direction.WEST));guns.put(new BlockPos(0,0,1),Blocks.IRON_BLOCK.getDefaultState());
        for(int x=-2;x<=2;x++)guns.put(new BlockPos(x,0,2),Blocks.IRON_BLOCK.getDefaultState());
        guns.put(LASER,ModWorldBlocks.SHIP_LASER.getDefaultState().with(Properties.FACING,Direction.SOUTH));
        for(var p:List.of(LEFT,RIGHT))guns.put(p,ModWorldBlocks.SHIP_ROCKET.getDefaultState().with(Properties.FACING,Direction.SOUTH));
        var made=new ArrayList<BlockBodyEntity>();
        try{
            for(var pair:List.of(Map.entry(floor,Vec3d.ZERO),Map.entry(middle,new Vec3d(0,2,0)),Map.entry(guns,new Vec3d(2,3,0)))){
                var offset=q.rotate(new Vec3(pair.getValue().x,pair.getValue().y,pair.getValue().z));var shift=new Vec3d(offset.x(),offset.y(),offset.z());
                made.add(create(world,pair.getKey(),at.add(shift),q,velocity.add(angularVelocity.crossProduct(shift)),angularVelocity));
            }
            var a=new Assembly(made.get(0),made.get(1),made.get(2));
            if(!MechanicalBearingSystem.connect(world,a.base,YAW_BASE,a.yaw,YAW_CHILD))throw new IllegalStateException("heavy operator yaw material joint refused");
            if(!MechanicalBearingSystem.connect(world,a.yaw,PITCH_BASE,a.barrel,PITCH_CHILD))throw new IllegalStateException("heavy operator pitch material joint refused");
            if(!bind(world,a))throw new IllegalStateException("heavy operator controller binding refused");
            return a;
        }catch(RuntimeException failure){for(var b:made){BodyJointRegistry.get(world).removeBody(b.getUuid());b.discard();}throw failure;}
    }
    private static boolean enemy(BlockBodyEntity owner,LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()
        &&target.getWorld()==owner.getWorld()&&HostileEnvironment.isHostileTarget(null,target)&&!owner.isTeammate(target);}
    private static boolean sight(ServerWorld world,Assembly a,Vec3d from,Vec3d to,Frame f){
        if(f.rays>=MAX_RAYS||!WorldEditSafety.loadedSegment(world,from,to))return false;f.rays++;
        if(world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,a.barrel)).getType()!=HitResult.Type.MISS)return false;
        var bodies=BodySpatialIndex.query(world,new Box(from,to).expand(.01));if(bodies.size()>32)return false;
        for(var b:bodies)if(b.physicsRaycast(from,to).isPresent())return false;return true;
    }
    private static void drive(BlockBodyEntity body,BlockPos cell,Direction facing,Vec3d axis,Vec3d forward,Vec3d desired,double angularSpeed,Frame f){
        var state=body.getLocalBlock(cell);if(!state.isOf(ModWorldBlocks.ROTARY_DRIVE)||state.get(MechanicalBearingBlock.FACING)!=facing)return;
        var a=forward.subtract(axis.multiply(forward.dotProduct(axis)));var b=desired.subtract(axis.multiply(desired.dotProduct(axis)));
        double angle=a.lengthSquared()<1e-8||b.lengthSquared()<1e-8?0:Math.atan2(axis.dotProduct(a.crossProduct(b)),a.dotProduct(b));
        // A zero-power drive coasts; it cannot brake a recoiling/free bearing.
        // Lead the angular error by four ordinary ticks using the real relative
        // velocity, then use only the original physical drive power/torque levels.
        double demand=angle-angularSpeed*.2;
        int power=Math.abs(demand)<.015?0:Math.abs(demand)<.15?5:Math.abs(demand)<.5?10:15;
        var next=state.with(Properties.POWER,power).with(Properties.INVERTED,demand<0);
        if(!state.equals(next)&&body.setLocalBlock(cell,next))f.drives++;
    }
    private static Vec3d axis(BlockBodyEntity b,Vec3 axis){var p=b.rotation().rotate(axis);return new Vec3d(p.x(),p.y(),p.z());}
    private static void stop(Assembly a,Frame f){if(a==null)return;for(var entry:List.of(Map.entry(a.base,YAW_DRIVE),Map.entry(a.yaw,PITCH_DRIVE))){var s=entry.getKey().getLocalBlock(entry.getValue());if(s.isOf(ModWorldBlocks.ROTARY_DRIVE)&&s.get(Properties.POWER)!=0&&entry.getKey().setLocalBlock(entry.getValue(),s.with(Properties.POWER,0)))f.drives++;}}
    private static void tick(ServerWorld world){
        long now=world.getTime();if(now%CADENCE!=0)return;var f=FRAMES.computeIfAbsent(world,w->new Frame());if(f.tick==now)return;
        f.tick=now;f.candidates=f.rays=f.drives=0;f.permits.clear();var bodies=BodySpatialIndex.loaded(world);int visited=0,assemblies=0;
        while(visited<Math.min(MAX_BODY_VISITS,bodies.size())&&assemblies<MAX_ASSEMBLIES){
            var base=bodies.get(Math.floorMod(f.cursor++,bodies.size()));visited++;if(!base.getCommandTags().contains(ROOT))continue;assemblies++;
            var a=resolve(world,base);if(!valid(world,a)){stop(a,f);continue;}
            if(f.candidates>=MAX_CANDIDATES){stop(a,f);continue;}
            var from=MountedWeaponEmitter.muzzle(a.barrel,LASER);var candidates=new ArrayList<LivingEntity>();
            world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),Box.of(from,96,96,96),e->enemy(base,e),candidates,MAX_CANDIDATES-f.candidates);
            f.candidates+=candidates.size();candidates.sort(Comparator.comparingDouble(e->e.squaredDistanceTo(from)));LivingEntity target=null;
            for(var candidate:candidates)if(candidate.squaredDistanceTo(from)<=48*48&&sight(world,a,from,candidate.getBoundingBox().getCenter(),f)){target=candidate;break;}
            if(target==null){stop(a,f);continue;}
            var desired=target.getBoundingBox().getCenter().subtract(from).normalize();var forward=MountedWeaponEmitter.direction(a.barrel,LASER);
            double yawSpeed=a.yaw.physics().angularVelocity().minus(a.base.physics().angularVelocity()).dot(a.base.rotation().rotate(UP));
            double pitchSpeed=a.barrel.physics().angularVelocity().minus(a.yaw.physics().angularVelocity()).dot(a.yaw.rotation().rotate(X));
            drive(a.base,YAW_DRIVE,Direction.UP,axis(a.base,UP),forward,desired,yawSpeed,f);drive(a.yaw,PITCH_DRIVE,Direction.EAST,axis(a.yaw,X),forward,desired,pitchSpeed,f);
            f.permits.put(a.barrel.getUuid(),new Permit(base.getUuid(),target.getUuid(),new WeakReference<>(target),now));
        }
        f.stats=new Stats(visited,assemblies,f.candidates,f.rays,f.drives);
    }
    /** Optional present zero owns an operator bank even when damaged/hidden/controlled;
     * it cannot fall through to ordinary autonomous lever fire. Player core leases win. */
    public static OptionalInt fireMask(BlockBodyEntity barrel){
        if(barrel.getCommandTags().stream().noneMatch(t->t.startsWith("drmd.operator.gun.")))return OptionalInt.empty();
        if(!barrel.getCommandTags().contains(GUN))return OptionalInt.of(0);
        if(!(barrel.getWorld() instanceof ServerWorld world))return OptionalInt.of(0);var f=FRAMES.get(world);var permit=f==null?null:f.permits.get(barrel.getUuid());long now=world.getTime();
        if(permit==null||now<permit.tick||now-permit.tick>=CADENCE)return OptionalInt.of(0);
        var a=resolve(world,body(world,permit.root));if(!valid(world,a)||a.barrel!=barrel)return OptionalInt.of(0);
        var target=permit.actor.get();if(target==null||!target.getUuid().equals(permit.target)||!enemy(a.base,target))return OptionalInt.of(0);
        var from=MountedWeaponEmitter.muzzle(barrel,LASER);var to=target.getBoundingBox().getCenter();
        if(from.squaredDistanceTo(to)>48*48||!sight(world,a,from,to,f))return OptionalInt.of(0);
        var forward=MountedWeaponEmitter.direction(barrel,LASER);var wanted=to.subtract(from);
        if(wanted.lengthSquared()<1e-8||forward.dotProduct(wanted.normalize())<Math.cos(Math.toRadians(2)))return OptionalInt.of(0);
        return OptionalInt.of(3);
    }
}
