package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.ai.DroneSquadMailbox;
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
    public static final BlockPos BARREL_REACTOR=new BlockPos(1,0,1);
    public static final List<BlockPos> REACTION_PODS=List.of(new BlockPos(2,0,1),new BlockPos(1,1,1),new BlockPos(1,0,0));
    private static final String ROOT="drmd.operator.v1",GUN="drmd.operator.gun.v1",YAW="drmd.operator.yaw=",BARREL="drmd.operator.barrel=",PARENT="drmd.operator.parent=";
    private static final Vec3 UP=new Vec3(0,1,0),X=new Vec3(1,0,0);
    private static final Map<ServerWorld,Frame> FRAMES=new WeakHashMap<>();
    public record Assembly(BlockBodyEntity base,BlockBodyEntity yaw,BlockBodyEntity barrel) {
        public List<BlockBodyEntity> bodies(){return List.of(base,yaw,barrel);}
    }
    public record Stats(int visited,int assemblies,int candidates,int rays,int drives){}
    private record Permit(UUID root,UUID target,WeakReference<LivingEntity> actor,long tick){}
    private record Memory(UUID target,WeakReference<LivingEntity> actor,Vec3d point,Vec3d velocity,long observedAt){}
    private record Interest(UUID target,WeakReference<LivingEntity> actor,long observedAt){}
    private record Pods(long revision,List<BlockPos> cells,com.terminaldetector.drmd.d6.D6ThrusterMixer mixer){}
    private record BarrelPods(UUID barrel,Pods hardware){}
    private static final class Frame {
        long tick=Long.MIN_VALUE;int cursor,candidates,rays,drives;Stats stats=new Stats(0,0,0,0,0);
        final Map<UUID,Permit> permits=new HashMap<>();
        final Map<UUID,Memory> memories=new HashMap<>();
        final Map<UUID,Interest> interests=new HashMap<>();
        final Map<UUID,Pods> pods=new HashMap<>();
        final Map<UUID,BarrelPods> reactionPods=new HashMap<>();
        final Map<UUID,BlockPos> aimSlots=new HashMap<>();
        final Map<UUID,Set<BlockPos>> visibleSlots=new HashMap<>();
    }
    private HeavyOperatorSystem(){}
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(HeavyOperatorSystem::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->FRAMES.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->FRAMES.clear());
    }
    public static Stats stats(ServerWorld world){var frame=FRAMES.get(world);return frame==null?new Stats(0,0,0,0,0):frame.stats;}
    /** Read-only terminal/profiler inspection; no sensing, admission or state refresh. */
    public static Optional<BlockPos> aimingBank(BlockBodyEntity barrel){
        if(!(barrel.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return Optional.empty();
        var f=FRAMES.get(world);return f==null?Optional.empty():Optional.ofNullable(f.aimSlots.get(uuid(barrel,PARENT)));
    }
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
    /** A loaded nearby hostile only requests the existing bounded hull-volume lease.
     * Presence is not sight, a fire permit, or a hidden movement/aim coordinate. */
    public static boolean needsVolumeInterest(BlockBodyEntity root){
        if(!(root.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!root.getCommandTags().contains(ROOT))return false;
        var f=FRAMES.get(world);var interest=f==null?null:f.interests.get(root.getUuid());long now=world.getTime();
        if(interest==null||now<interest.observedAt||now-interest.observedAt>CADENCE+1)return false;
        var target=interest.actor.get();return enemy(root,target)&&target.getUuid().equals(interest.target)
            &&world.getEntity(target.getUuid())==target&&root.squaredDistanceTo(target)<=112*112&&materialValid(world,resolve(world,root));
    }
    /** Only real vector pods counter the common island recoil. The hinges cannot
     * remove an entire free island's angular momentum. Called AFTER each ordinary
     * or joint rollback snapshot; UNKNOWN rolls back these forces too. */
    public static void accumulateForces(BlockBodyEntity root){
        if(root.getCommandTags().contains(GUN)){accumulateBarrelForces(root);return;}
        if(!needsVolumeInterest(root))return;var world=(ServerWorld)root.getWorld();var f=FRAMES.get(world);
        var pods=f.pods.get(root.getUuid());
        if(pods==null||pods.revision!=root.shapeRevision()){
            var cells=root.cells().stream().filter(c->c.state().isOf(ModWorldBlocks.SHIP_THRUSTER)).map(BlockBodyEntity.Cell::pos).limit(17).sorted().toList();
            if(cells.size()<3||cells.size()>16){f.pods.remove(root.getUuid());return;}
            try{pods=new Pods(root.shapeRevision(),cells,new com.terminaldetector.drmd.d6.D6ThrusterMixer(cells.stream().map(p->new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.5)).toList(),BodyControlCore.FORCE_PER_MOTOR));f.pods.put(root.getUuid(),pods);}
            catch(IllegalArgumentException unsupported){f.pods.remove(root.getUuid());return;}
        }
        var physics=root.physics();Vec3 desiredVelocity=new Vec3(0,0,0),turnError=new Vec3(0,0,0);var memory=f.memories.get(root.getUuid());long now=world.getTime();
        var remembered=memory==null?null:memory.actor.get();var interest=f.interests.get(root.getUuid());
        if(memory!=null&&interest!=null&&memory.target.equals(interest.target)&&remembered==interest.actor.get()&&enemy(root,remembered)
            &&now>=memory.observedAt&&now-memory.observedAt<DroneSquadMailbox.TTL){
            var delta=new Vec3(memory.point.x,memory.point.y,memory.point.z).minus(physics.position());double distance=delta.length();
            if(distance>32)desiredVelocity=delta.scaled(Math.min(3,(distance-32)*.5)/distance);
            if(distance>1e-8){var forward=root.rotation().rotate(new Vec3(0,0,1));var desired=delta.scaled(1/distance);var cross=forward.cross(desired);double sine=cross.length(),cosine=MathHelper.clamp(forward.dot(desired),-1,1);
                if(sine>1e-8)turnError=cross.scaled(Math.atan2(sine,cosine)/sine);
                else if(cosine<0)turnError=root.rotation().rotate(UP).scaled(Math.PI);
            }
        }
        var force=desiredVelocity.minus(physics.linearVelocity()).scaled(physics.mass()*2);
        // Real vector pods turn the whole free island toward its own last sighting;
        // damping alone leaves a recoiling chassis permanently tilted behind its guns.
        var torque=physics.worldInertia().transform(turnError.minus(physics.angularVelocity()).scaled(8));var inverse=root.rotation().inverse();
        var selected=pods;
        for(var output:pods.mixer.mix(root.localCentre(),inverse.rotate(force),inverse.rotate(torque),i->root.getLocalBlock(selected.cells.get(i)).isOf(ModWorldBlocks.SHIP_THRUSTER)))
            physics.applyForceAtPoint(root.rotation().rotate(output.force()),root.rotation().rotate(output.localPoint().minus(root.localCentre())));
    }
    /** Actual reactor-gated vector hardware assists the SAME physical two-axis command.
     * Root thrusters cannot cancel free yaw recoil across a40Nm hinge by themselves.
     * No electric/fuel store is invented: reactor material and each300N pod are authority.
     * Called by the existing post-snapshot force phase, never an extra integration. */
    private static void accumulateBarrelForces(BlockBodyEntity barrel){
        if(!(barrel.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return;
        var root=body(world,uuid(barrel,PARENT));
        if(root==null||!needsVolumeInterest(root))return;
        var a=resolve(world,root);if(!materialValid(world,a)||a.barrel!=barrel
            ||!barrel.getLocalBlock(BARREL_REACTOR).isOf(ModWorldBlocks.UNSTABLE_REACTOR))return;
        var f=FRAMES.get(world);var memory=f.memories.get(root.getUuid());var interest=f.interests.get(root.getUuid());long now=world.getTime();
        var remembered=memory==null?null:memory.actor.get();
        if(memory==null||interest==null||!memory.target.equals(interest.target)||remembered!=interest.actor.get()
            ||!enemy(root,remembered)||now<memory.observedAt||now-memory.observedAt>=DroneSquadMailbox.TTL)return;
        var selected=f.aimSlots.get(root.getUuid());if(selected==null||!gun(barrel,selected))return;
        var desired=aimDirection(barrel,selected,memory.point,memory.velocity);if(desired==null)return;
        var cached=f.reactionPods.get(root.getUuid());
        if(cached==null||!cached.barrel.equals(barrel.getUuid())||cached.hardware.revision!=barrel.shapeRevision()){
            var cells=barrel.cells().stream().filter(c->c.state().isOf(ModWorldBlocks.SHIP_THRUSTER)).map(BlockBodyEntity.Cell::pos).limit(17).sorted().toList();
            if(cells.size()<3||cells.size()>16){f.reactionPods.remove(root.getUuid());return;}
            if(f.reactionPods.size()>=MAX_ASSEMBLIES&&!f.reactionPods.containsKey(root.getUuid()))return;
            try{cached=new BarrelPods(barrel.getUuid(),new Pods(barrel.shapeRevision(),cells,
                new com.terminaldetector.drmd.d6.D6ThrusterMixer(cells.stream().map(p->new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.5)).toList(),BodyControlCore.FORCE_PER_MOTOR)));
                f.reactionPods.put(root.getUuid(),cached);
            }catch(IllegalArgumentException unsupported){f.reactionPods.remove(root.getUuid());return;}
        }
        var forward=MountedWeaponEmitter.direction(barrel,selected);
        var baseSpin=root.physics().angularVelocity();
        var yawSpin=a.yaw.physics().angularVelocity();
        var barrelSpin=barrel.physics().angularVelocity();
        var yawAxis=root.rotation().rotate(UP);
        var pitchAxis=a.yaw.rotation().rotate(X);
        var command=rates(forward,desired,axis(root,UP),axis(a.yaw,X),new Vec3d(baseSpin.x(),baseSpin.y(),baseSpin.z()));

        // The pods drive around the actual bearings. Yaw carries both moving bodies,
        // so the gun's centre-of-mass inertia alone badly understates the shaft load.
        var yawPoint=bearingPoint(root,YAW_BASE,UP);
        var pitchPoint=bearingPoint(a.yaw,PITCH_BASE,X);
        double yawMoment=momentAbout(barrel,yawPoint,yawAxis)+momentAbout(a.yaw,yawPoint,yawAxis);
        double pitchMoment=momentAbout(barrel,pitchPoint,pitchAxis);
        var yawError=yawAxis.scaled(command.yaw-yawSpin.minus(baseSpin).dot(yawAxis));
        var pitchError=pitchAxis.scaled(command.pitch-barrelSpin.minus(yawSpin).dot(pitchAxis));
        var targetSpin=baseSpin.plus(yawAxis.scaled(command.yaw)).plus(pitchAxis.scaled(command.pitch));
        var otherError=targetSpin.minus(barrelSpin).minus(yawError).minus(pitchError);
        // Preserve the existing rate cap and the force limit of the actual mounts.
        var torque=barrel.physics().worldInertia().transform(otherError)
            .plus(yawError.scaled(yawMoment)).plus(pitchError.scaled(pitchMoment)).scaled(8);
        var hardware=cached.hardware;var localTorque=barrel.rotation().inverse().rotate(torque);
        for(var output:hardware.mixer.mix(barrel.localCentre(),new Vec3(0,0,0),localTorque,
            i->barrel.getLocalBlock(hardware.cells.get(i)).isOf(ModWorldBlocks.SHIP_THRUSTER)))
            barrel.physics().applyForceAtPoint(barrel.rotation().rotate(output.force()),barrel.rotation().rotate(output.localPoint().minus(barrel.localCentre())));
    }
    private static Vec3 bearingPoint(BlockBodyEntity body,BlockPos cell,Vec3 outward){
        var local=new Vec3(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5).plus(outward.scaled(.5));
        return body.physics().position().plus(body.rotation().rotate(local.minus(body.localCentre())));
    }
    private static double momentAbout(BlockBodyEntity body,Vec3 point,Vec3 axis){
        var physics=body.physics();
        var lever=physics.position().minus(point);
        double offsetInertia=physics.mass()*lever.cross(axis).lengthSquared();
        return axis.dot(physics.worldInertia().transform(axis))+offsetInertia;
    }
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
            new com.terminaldetector.drmd.physics.collision.BodyBounds(new Vec3(-2,-1,-2),new Vec3(5,5,3)),new Vec3(at.x,at.y,at.z),new Vec3(0,0,0),q).bounds();
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
        // The same three ordinary vector pods brace real shoulder recoil through
        // the existing mixer. Use the supported outer deck, rather than spend
        // their unchanged300N capacity on three near-centre moment arms.
        for(var p:List.of(new BlockPos(-2,0,-2),new BlockPos(2,0,-2),new BlockPos(0,0,2)))floor.put(p,ModWorldBlocks.SHIP_THRUSTER.getDefaultState());
        var middle=new LinkedHashMap<BlockPos,BlockState>();middle.put(YAW_CHILD,bearing(Direction.DOWN));middle.put(PITCH_DRIVE,drive(Direction.EAST));middle.put(PITCH_BASE,bearing(Direction.EAST));
        var guns=new LinkedHashMap<BlockPos,BlockState>();guns.put(PITCH_CHILD,bearing(Direction.WEST));guns.put(new BlockPos(0,0,1),Blocks.IRON_BLOCK.getDefaultState());
        for(int x=-2;x<=2;x++)guns.put(new BlockPos(x,0,2),Blocks.IRON_BLOCK.getDefaultState());
        guns.put(BARREL_REACTOR,ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState());
        for(var pod:REACTION_PODS)guns.put(pod,ModWorldBlocks.SHIP_THRUSTER.getDefaultState());
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
    private record AxisRates(double yaw,double pitch){}
    /** Differential two-axis aiming in the ACTUAL moving frames. Both columns
     * act on the same final barrel; treating their errors independently lets one
     * drive undo the other's work when a rolled/recoiling base turns beneath it. */
    private static AxisRates rates(Vec3d forward,Vec3d desired,Vec3d yawAxis,Vec3d pitchAxis,Vec3d baseAngularVelocity){
        var cross=forward.crossProduct(desired);double sine=cross.length(),cosine=MathHelper.clamp(forward.dotProduct(desired),-1,1);
        Vec3d wanted;
        if(sine>1e-8)wanted=cross.multiply(Math.atan2(sine,cosine)/sine).crossProduct(forward).multiply(3);
        else if(cosine<0){var axis=yawAxis.subtract(forward.multiply(yawAxis.dotProduct(forward)));if(axis.lengthSquared()<1e-8)axis=pitchAxis.subtract(forward.multiply(pitchAxis.dotProduct(forward)));wanted=axis.lengthSquared()>1e-8?axis.normalize().crossProduct(forward).multiply(Math.PI*3):Vec3d.ZERO;}
        else wanted=Vec3d.ZERO;
        var y=yawAxis.crossProduct(forward);var p=pitchAxis.crossProduct(forward);
        var rhs=wanted.subtract(baseAngularVelocity.crossProduct(forward));
        double yy=y.lengthSquared()+1e-5,yp=y.dotProduct(p),pp=p.lengthSquared()+1e-5,det=yy*pp-yp*yp;
        if(!Double.isFinite(det)||det<1e-12)return new AxisRates(0,0);
        double ry=rhs.dotProduct(y),rp=rhs.dotProduct(p),wy=(ry*pp-rp*yp)/det,wp=(rp*yy-ry*yp)/det;
        if(!Double.isFinite(wy)||!Double.isFinite(wp))return new AxisRates(0,0);
        double scale=Math.max(1,Math.max(Math.abs(wy),Math.abs(wp))/MechanicalBearingSystem.MAX_SPEED);
        return new AxisRates(wy/scale,wp/scale);
    }
    private static void drive(BlockBodyEntity body,BlockPos cell,Direction facing,double desiredSpeed,double angularSpeed,Frame f){
        var state=body.getLocalBlock(cell);if(!state.isOf(ModWorldBlocks.ROTARY_DRIVE)||state.get(MechanicalBearingBlock.FACING)!=facing)return;
        if(!Double.isFinite(desiredSpeed)||!Double.isFinite(angularSpeed))return;
        double speed=MathHelper.clamp(desiredSpeed,-MechanicalBearingSystem.MAX_SPEED,MechanicalBearingSystem.MAX_SPEED);
        // Zero POWER is unpowered coasting, not a zero-speed servo. A physical
        // minimum reverse request brakes the existing momentum near the goal.
        if(Math.abs(speed)<.02&&Math.abs(angularSpeed)>.01)speed=-Math.copySign(MechanicalBearingSystem.MAX_SPEED/15,angularSpeed);
        int power=Math.abs(speed)<1e-4?0:Math.max(1,Math.min(15,(int)Math.round(Math.abs(speed)*15/MechanicalBearingSystem.MAX_SPEED)));
        var next=state.with(Properties.POWER,power).with(Properties.INVERTED,speed<0);
        if(!state.equals(next)&&body.setLocalBlock(cell,next))f.drives++;
    }
    private static Vec3d axis(BlockBodyEntity b,Vec3 axis){var p=b.rotation().rotate(axis);return new Vec3d(p.x(),p.y(),p.z());}
    private static boolean gun(BlockBodyEntity body,BlockPos cell){return BodyMountedWeapons.isWeapon(body.getLocalBlock(cell));}
    private static Vec3d aimDirection(BlockBodyEntity barrel,BlockPos slot,Vec3d point,Vec3d targetVelocity){
        var cfg=MountedWeaponEmitter.config(barrel,barrel,slot);if(cfg==null)return null;
        var delta=point.subtract(cfg.pos);var relative=targetVelocity.subtract(cfg.inheritedVelocity);
        var aim=com.terminaldetector.drmd.weapon.core.InterceptAim.solve(new Vec3(delta.x,delta.y,delta.z),new Vec3(relative.x,relative.y,relative.z),cfg.speed,cfg.life*20);
        if(aim.isPresent()){var d=aim.get().direction();return new Vec3d(d.x(),d.y(),d.z());}
        return delta.lengthSquared()>1e-10?delta.normalize():null;
    }
    private static BlockPos selectGun(Assembly a,Frame f,Vec3d point,Vec3d targetVelocity,long now){
        var old=f.aimSlots.get(a.base.getUuid());var visible=f.visibleSlots.get(a.base.getUuid());
        if(old!=null&&gun(a.barrel,old)&&(visible==null||visible.contains(old))&&a.barrel.mountedWeapons().readyAt(old)<=now)return old;
        BlockPos best=null;
        double alignment=-Double.MAX_VALUE;
        long earliest=Long.MAX_VALUE;
        for(var slot:List.of(LASER,LEFT,RIGHT))if(gun(a.barrel,slot)&&(visible==null||visible.contains(slot))){
            var direction=aimDirection(a.barrel,slot,point,targetVelocity);if(direction==null)continue;
            long ready=a.barrel.mountedWeapons().readyAt(slot);double dot=MountedWeaponEmitter.direction(a.barrel,slot).dotProduct(direction);
            // A bank can be physically farther off-axis and still be next in line.
            // Alignment breaks ties between equally ready banks without starving one.
            if(best==null||ready<earliest||ready==earliest&&dot>alignment){
                best=slot;
                alignment=dot;
                earliest=ready;
            }
        }
        if(best!=null&&(f.aimSlots.size()<MAX_ASSEMBLIES||f.aimSlots.containsKey(a.base.getUuid())))f.aimSlots.put(a.base.getUuid(),best);return best;
    }
    private static void stop(Assembly a,Frame f){if(a==null)return;for(var entry:List.of(Map.entry(a.base,YAW_DRIVE),Map.entry(a.yaw,PITCH_DRIVE))){var s=entry.getKey().getLocalBlock(entry.getValue());if(s.isOf(ModWorldBlocks.ROTARY_DRIVE)&&s.get(Properties.POWER)!=0&&entry.getKey().setLocalBlock(entry.getValue(),s.with(Properties.POWER,0)))f.drives++;}}
    private static boolean aim(Assembly a,Frame f,Vec3d point,Vec3d targetVelocity,long now){
        var selected=selectGun(a,f,point,targetVelocity,now);if(selected==null){stop(a,f);return false;}
        var desired=aimDirection(a.barrel,selected,point,targetVelocity);if(desired==null){stop(a,f);return false;}
        var forward=MountedWeaponEmitter.direction(a.barrel,selected);
        double yawSpeed=a.yaw.physics().angularVelocity().minus(a.base.physics().angularVelocity()).dot(a.base.rotation().rotate(UP));
        double pitchSpeed=a.barrel.physics().angularVelocity().minus(a.yaw.physics().angularVelocity()).dot(a.yaw.rotation().rotate(X));
        var w=a.base.physics().angularVelocity();var command=rates(forward,desired,axis(a.base,UP),axis(a.yaw,X),new Vec3d(w.x(),w.y(),w.z()));
        drive(a.base,YAW_DRIVE,Direction.UP,command.yaw,yawSpeed,f);drive(a.yaw,PITCH_DRIVE,Direction.EAST,command.pitch,pitchSpeed,f);
        return true;
    }
    private static void tick(ServerWorld world){
        long now=world.getTime();var f=FRAMES.computeIfAbsent(world,w->new Frame());if(f.tick==now)return;
        boolean sense=now%CADENCE==0;f.tick=now;f.drives=0;
        if(sense){f.candidates=f.rays=0;f.permits.clear();f.visibleSlots.clear();}
        f.memories.entrySet().removeIf(e->{var root=body(world,e.getKey());return root==null||!root.getCommandTags().contains(ROOT)||now<e.getValue().observedAt||now-e.getValue().observedAt>=DroneSquadMailbox.TTL;});
        f.interests.entrySet().removeIf(e->{var root=body(world,e.getKey());return root==null||!root.getCommandTags().contains(ROOT)||now<e.getValue().observedAt||now-e.getValue().observedAt>CADENCE+1;});
        f.pods.keySet().removeIf(id->!f.interests.containsKey(id));
        f.reactionPods.keySet().removeIf(id->!f.interests.containsKey(id));
        f.aimSlots.keySet().removeIf(id->!f.interests.containsKey(id));
        if(!sense){
            // The bearings can sweep over eight degrees between sensor scans.
            // Refresh the drives from the bounded set of previously sighted targets.
            int refreshed=0;
            for(var entry:f.memories.entrySet()){
                var base=body(world,entry.getKey());var a=resolve(world,base);var memory=entry.getValue();
                var observed=memory.actor.get();
                if(!valid(world,a)||!enemy(base,observed)||world.getEntity(memory.target)!=observed){stop(a,f);continue;}
                if(aim(a,f,memory.point,memory.velocity,now))refreshed++;
            }
            f.stats=new Stats(f.memories.size(),refreshed,f.candidates,f.rays,f.drives);
            return;
        }
        var bodies=BodySpatialIndex.loaded(world);int visited=0,assemblies=0;
        while(visited<Math.min(MAX_BODY_VISITS,bodies.size())&&assemblies<MAX_ASSEMBLIES){
            var base=bodies.get(Math.floorMod(f.cursor++,bodies.size()));visited++;if(!base.getCommandTags().contains(ROOT))continue;assemblies++;
            var a=resolve(world,base);if(!materialValid(world,a)){f.interests.remove(base.getUuid());f.pods.remove(base.getUuid());stop(a,f);continue;}
            if(f.candidates>=MAX_CANDIDATES){stop(a,f);continue;}
            if(List.of(LASER,LEFT,RIGHT).stream().noneMatch(slot->gun(a.barrel,slot))){stop(a,f);continue;}
            // The query centre is only a bounded presence broadphase. Detection
            // belongs to each surviving REAL muzzle, never to a virtual sensor.
            var from=a.barrel.physicsCentre();var candidates=new ArrayList<LivingEntity>();
            world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),Box.of(from,192,192,192),e->enemy(base,e)&&e.squaredDistanceTo(from)<=96*96,candidates,MAX_CANDIDATES-f.candidates);
            f.candidates+=candidates.size();candidates.sort(Comparator.comparingDouble(e->e.squaredDistanceTo(from)));LivingEntity target=null;
            if(!candidates.isEmpty()&&(f.interests.size()<MAX_ASSEMBLIES||f.interests.containsKey(base.getUuid()))){var present=candidates.get(0);f.interests.put(base.getUuid(),new Interest(present.getUuid(),new WeakReference<>(present),now));}
            else f.interests.remove(base.getUuid());
            if(!valid(world,a)){stop(a,f);continue;}
            for(var candidate:candidates){
                var visible=new LinkedHashSet<BlockPos>();var point=candidate.getBoundingBox().getCenter();
                for(var slot:List.of(LASER,LEFT,RIGHT))if(gun(a.barrel,slot)){
                    var muzzle=MountedWeaponEmitter.muzzle(a.barrel,slot);
                    if(muzzle.squaredDistanceTo(point)<=48*48&&sight(world,a,muzzle,point,f))visible.add(slot);
                }
                if(!visible.isEmpty()){target=candidate;f.visibleSlots.put(base.getUuid(),Set.copyOf(visible));break;}
            }
            Vec3d point;
            if(target!=null){point=target.getBoundingBox().getCenter();
                if(f.interests.size()<MAX_ASSEMBLIES||f.interests.containsKey(base.getUuid()))f.interests.put(base.getUuid(),new Interest(target.getUuid(),new WeakReference<>(target),now));
                if(f.memories.size()<MAX_ASSEMBLIES||f.memories.containsKey(base.getUuid()))f.memories.put(base.getUuid(),new Memory(target.getUuid(),new WeakReference<>(target),point,target.getVelocity(),now));}
            else{var memory=f.memories.get(base.getUuid());var remembered=memory==null?null:memory.actor.get();
                if(memory==null||!enemy(base,remembered)||!remembered.getUuid().equals(memory.target)){stop(a,f);continue;}
                // Own hull/cover can briefly hide a recoiling muzzle. Drive only
                // toward its copied last observation; hidden actor XYZ cannot
                // move this aim point or authorize even one projectile.
                point=memory.point;
            }
            var velocity=target==null?f.memories.get(base.getUuid()).velocity:target.getVelocity();
            if(aim(a,f,point,velocity,now)&&target!=null)
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
        // This is bank ownership, not a cached shot verdict. The normal scheduler
        // checks each actually due cell through allowsFire below; idle cells do
        // not consume the global ray budget and a second gun cannot reuse a ray.
        return OptionalInt.of(3);
    }
    /** Revalidate each due material bank in the same normal emitter loop. One
     * laser direction cannot authorize offset rockets after another gun recoils. */
    public static boolean allowsFire(BlockBodyEntity barrel,BlockPos slot){
        if(!gun(barrel,slot)||!(barrel.getWorld() instanceof ServerWorld world))return false;
        var f=FRAMES.get(world);var permit=f==null?null:f.permits.get(barrel.getUuid());long now=world.getTime();
        if(permit==null||now<permit.tick||now-permit.tick>=CADENCE)return false;
        var a=resolve(world,body(world,permit.root));if(!valid(world,a)||a.barrel!=barrel)return false;
        // Both hinges serve one physical aim solution. A different bank must not
        // steal that turn and recoil before the selected, still-due gun aligns.
        // The normal scheduler retains every bank's own cooldown and actual shot.
        if(!slot.equals(f.aimSlots.get(a.base.getUuid())))return false;
        var target=permit.actor.get();if(!enemy(a.base,target)||!target.getUuid().equals(permit.target))return false;
        var from=MountedWeaponEmitter.muzzle(barrel,slot);var point=target.getBoundingBox().getCenter();
        if(from.squaredDistanceTo(point)>48*48)return false;
        var wanted=aimDirection(barrel,slot,point,target.getVelocity());
        // Selection grants no shot: the chosen real muzzle must still satisfy
        // its own current geometry, intercept direction and complete shot gate.
        return wanted!=null&&MountedWeaponEmitter.direction(barrel,slot).dotProduct(wanted)>=Math.cos(Math.toRadians(2))
            &&sight(world,a,from,point,f);
    }
}
