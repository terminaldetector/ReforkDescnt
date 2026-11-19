package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import com.terminaldetector.drmd.world.gravity.EntityGravitySystem;
import com.terminaldetector.drmd.world.gravity.FieldShape;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

import java.util.*;
import java.util.function.BooleanSupplier;

/** First-entry physics ownership and independent native-floor-AI admission, not wall navigation proof. */
public final class ClimberFieldGravityGameTests implements FabricGameTest {
    private static final EntityType<?>[] TYPES={EntityType.SPIDER,EntityType.CAVE_SPIDER};
    private static final Direction[] AXES={Direction.EAST,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST};
    private static final int ORDINARY_TICKS=60;
    private static final double MINIMUM_PURSUIT=1;

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="climber-field-entry",tickLimit=180)
    public void firstEntryRetainsClimberPositionAndNativeOwnership(TestContext context) {
        var centre=context.getAbsolutePos(new BlockPos(6,80,6));
        var room=new Room(context,centre.add(-3,-3,-3),centre.add(3,3,3));
        watch(context,room,170,()->{
            if(!room.loaded())return false;
            room.build();
            for(var type:TYPES)for(boolean noGravity:new boolean[]{false,true}) {
                // An actual ordinary-floor field entry must be valid before wall cases are interpreted.
                var floor=spawnSpider(room,type,new Vec3d(centre.getX()+.5,room.min.getY()+1.02,centre.getZ()+.5),true,noGravity);
                UUID baseline=room.field(centre,new Vec3d(0,-1,0));
                var floorStart=floor.getPos();var navigation=floor.getNavigation();var control=floor.getMoveControl();
                EntityGravitySystem.tick(room.world.getServer());
                var support=room.world.raycast(new RaycastContext(floorStart.add(0,.1,0),floorStart.add(0,-.2,0),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,floor));
                emit("ORDINARY_FLOOR_ENTRY",floorStart,floor,Map.of("type",type.toString(),"externallyOwnedNoGravity",noGravity));
                context.assertTrue(support.getType()==HitResult.Type.BLOCK&&support.getSide()==Direction.UP
                    &&floor.getPos().distanceTo(floorStart)<1e-9&&floor.isOnGround()&&room.world.isSpaceEmpty(floor),
                    "ordinary-floor entry fixture must be valid before C2 wall diagnosis: "+type);
                context.assertTrue(floor.getNavigation()==navigation&&floor.getMoveControl()==control&&floor.hasNoGravity()==noGravity,
                    "ordinary-floor control retains native objects and saved gravity flag: "+type);
                room.removeField(baseline);floor.discard();

                var actor=spawnSpider(room,type,Vec3d.ofCenter(centre),true,noGravity);
                try {
                    var origin=actor.getPos();var nav=actor.getNavigation();var move=actor.getMoveControl();
                    double originalGravity=actor.getFinalGravity();actor.setYaw(37);actor.setPitch(-20);
                    for(var axis:AXES) {
                        var down=Vec3d.of(axis.getVector());var propulsion=new Vec3d(.17,.03,-.04);
                        actor.setOnGround(false);actor.setVelocity(propulsion);
                        var before=state(actor);UUID field=room.field(centre,down);
                        EntityGravitySystem.tick(room.world.getServer());
                        var details=new LinkedHashMap<String,Object>();details.put("type",type.toString());details.put("axis",axis.toString());
                        details.put("before",before);details.put("externallyOwnedNoGravity",noGravity);
                        // Unsupported orientation is diagnostic only; no absent frame/classifier assertion.
                        emit("FIRST_FIELD_ENTRY",origin,actor,details);
                        context.assertTrue(actor.getPos().distanceTo(origin)<1e-9,
                            "first C2 field pass must not teleport an airborne climber onto a standing surface: "+type+" "+axis);
                        var tangent=propulsion.subtract(down.multiply(propulsion.dotProduct(down)));
                        context.assertTrue(!actor.isOnGround()&&actor.isAiDisabled()&&actor.getVelocity().dotProduct(tangent)>1e-4
                            &&actor.getNavigation()==nav&&actor.getMoveControl()==move&&actor.hasNoGravity()==noGravity
                            &&room.world.isSpaceEmpty(actor),"field cannot manufacture contact, erase propulsion or replace native control: "+type+" "+axis);
                        room.removeField(field);
                        context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(actor)&&actor.hasNoGravity()==noGravity
                            &&Math.abs(actor.getFinalGravity()-originalGravity)<1e-9,"field release restores original acceleration ownership: "+type);
                    }
                } finally {actor.discard();}
            }
            return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="climber-floor-admission",tickLimit=320)
    public void ordinaryFloorNativePursuitAdmitsOneAssignedCowTarget(TestContext context) {
        var centre=context.getAbsolutePos(new BlockPos(6,80,6));
        var room=new Room(context,centre.add(-8,0,-8),centre.add(8,5,8));
        var actors=new ArrayList<Pursuit>();boolean[] spawned={false};
        watch(context,room,310,()->{
            if(!room.loaded())return false;
            room.build();
            if(!spawned[0]) {
                // Wait for propagated darkness at both real start/target columns before enabling AI.
                for(int x:new int[]{-3,3}) {
                    var eyes=new Vec3d(centre.getX()+x+.5,room.min.getY()+1.7,centre.getZ()-3.5);
                    if(!room.dark(eyes)||!room.dark(eyes.add(0,0,5)))return false;
                }
                int index=0;
                for(var type:TYPES) {
                    var start=new Vec3d(centre.getX()+(index++==0?-3:3)+.5,room.min.getY()+1.02,centre.getZ()-3.5);
                    var actor=spawnSpider(room,type,start,false,false);
                    CowEntity target=EntityType.COW.create(room.world);target.setAiDisabled(true);target.setNoGravity(true);target.setInvulnerable(true);
                    target.setPosition(start.add(0,0,5));room.spawn(target);
                    actor.setTarget(target); // one explicit fixture assignment; not Spider's natural player/golem target choice
                    actors.add(new Pursuit(actor,target,start));
                }
                spawned[0]=true;return false;
            }
            boolean ready=true;
            for(var pursuit:actors) {
                var actor=pursuit.actor;
                context.assertTrue(room.dark(actor.getEyePos()),"ordinary-floor admission requires genuinely dark eyes; do not diagnose bright-light aggression reset");
                if(actor.age>pursuit.lastAge) {
                    pursuit.maxStep=Math.max(pursuit.maxStep,actor.getPos().distanceTo(pursuit.lastPosition));
                    pursuit.lastPosition=actor.getPos();pursuit.lastAge=actor.age;
                    pursuit.navigatorActive|=!actor.getNavigation().isIdle();
                    pursuit.targetRetained|=actor.getTarget()==pursuit.target;
                }
                if(actor.age-pursuit.spawnAge<ORDINARY_TICKS)ready=false;
            }
            if(!ready)return false;
            for(var pursuit:actors) {
                var actor=pursuit.actor;
                double improvement=pursuit.initialDistance-actor.getPos().distanceTo(pursuit.target.getPos());
                emit("ORDINARY_FLOOR_AI_ADMISSION",pursuit.start,actor,Map.of(
                    "assignedCowTargetOnce",true,"ordinaryEntityTicks",actor.age-pursuit.spawnAge,
                    "navigatorObservedActive",pursuit.navigatorActive,"targetRetained",actor.getTarget()==pursuit.target,
                    "distanceImprovement",improvement,"maximumObservedStep",pursuit.maxStep,
                    "manualEntityTicks",false,"repeatedNavigationOrVelocityCommands",false,
                    "eyeLight",List.of(room.world.getLightLevel(LightType.SKY,BlockPos.ofFloored(actor.getEyePos())),
                        room.world.getLightLevel(LightType.BLOCK,BlockPos.ofFloored(actor.getEyePos()))),"noGlobalTimeWeatherWrites",true));
                context.assertTrue(actor.isAlive()&&!actor.isAiDisabled()&&pursuit.targetRetained&&actor.getTarget()==pursuit.target
                    &&pursuit.navigatorActive&&improvement>=MINIMUM_PURSUIT&&pursuit.maxStep<=1
                    &&actor.getNavigation()==pursuit.navigation&&actor.getMoveControl()==pursuit.control,
                    "ordinary-floor native Cow pursuit admission failed; wall/ceiling AI cannot be interpreted: "+actor.getType());
            }
            return true;
        });
    }

    private static SpiderEntity spawnSpider(Room room,EntityType<?> type,Vec3d at,boolean isolated,boolean noGravity) {
        SpiderEntity actor=(SpiderEntity)type.create(room.world);
        room.context.assertTrue(actor!=null&&!actor.isAiDisabled(),"native climber AI starts enabled: "+type);
        actor.setAiDisabled(isolated);actor.setNoGravity(noGravity);actor.setPersistent();actor.setPosition(at);room.spawn(actor);
        room.context.assertTrue(room.world.isSpaceEmpty(actor),"actual initial climber collision box is clear: "+type);
        return actor;
    }

    private static final class Pursuit {
        final SpiderEntity actor;final CowEntity target;final Vec3d start;final int spawnAge;
        final Object navigation,control;final double initialDistance;
        Vec3d lastPosition;int lastAge;double maxStep;boolean navigatorActive,targetRetained;
        Pursuit(SpiderEntity actor,CowEntity target,Vec3d start) {
            this.actor=actor;this.target=target;this.start=start;spawnAge=lastAge=actor.age;lastPosition=start;
            navigation=actor.getNavigation();control=actor.getMoveControl();initialDistance=start.distanceTo(target.getPos());
        }
    }

    private static void watch(TestContext context,Room room,int limit,BooleanSupplier scenario) {
        boolean[] finished={false};
        for(int i=1;i<limit;i++)context.runAtTick(i,()->{
            if(finished[0])return;
            try {
                if(scenario.getAsBoolean()){finished[0]=true;room.close();context.complete();}
            } catch(Throwable failure){finished[0]=true;room.close();throw failure;}
        });
        context.runAtTick(limit,()->{
            if(finished[0])return;finished[0]=true;
            try {context.assertTrue(false,"C2 fixture/admission timed out before its frozen ordinary-tick contract; no wall/ceiling inference");}
            finally {room.close();}
        });
    }

    /** Bounded test-only column tickets are restored; fixture writes require actual FULL cubes first. */
    private static final class Room implements AutoCloseable {
        final TestContext context;final ServerWorld world;final BlockPos min,max;
        final Set<Long> added=new HashSet<>();final List<Entity> actors=new ArrayList<>();final Set<UUID> fields=new HashSet<>();
        final List<Runnable> cleanup=new ArrayList<>();
        boolean built,closed;
        Room(TestContext context,BlockPos min,BlockPos max) {
            this.context=context;world=context.getWorld();this.min=min;this.max=max;
            for(int x=(min.getX()-1)>>4;x<=(max.getX()+1)>>4;x++)for(int z=(min.getZ()-1)>>4;z<=(max.getZ()+1)>>4;z++) {
                long key=ChunkPos.toLong(x,z);
                if(!world.getForcedChunks().contains(key)){world.setChunkForced(x,z,true);added.add(key);}
            }
        }
        boolean loaded() {
            var access=WorldAccess.of(world);
            for(int x=(min.getX()-1)>>4;x<=(max.getX()+1)>>4;x++)
                for(int y=(min.getY()-1)>>4;y<=(max.getY()+1)>>4;y++)
                    for(int z=(min.getZ()-1)>>4;z<=(max.getZ()+1)>>4;z++)
                        if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
            return true;
        }
        void build() {
            if(built)return;
            for(var at:BlockPos.iterate(min,max)) {
                boolean shell=at.getX()==min.getX()||at.getX()==max.getX()||at.getY()==min.getY()
                    ||at.getY()==max.getY()||at.getZ()==min.getZ()||at.getZ()==max.getZ();
                world.setBlockState(at,(shell?Blocks.OBSIDIAN:Blocks.AIR).getDefaultState(),2);
            }
            built=true;
        }
        boolean dark(Vec3d eye) {
            var at=BlockPos.ofFloored(eye);
            return world.getLightLevel(LightType.SKY,at)==0&&world.getLightLevel(LightType.BLOCK,at)==0;
        }
        void spawn(Entity actor) {actors.add(actor);context.assertTrue(world.spawnEntity(actor),"real world-owned fixture actor registered");}
        UUID field(BlockPos at,Vec3d down) {
            UUID id=UUID.randomUUID();fields.add(id);
            GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),at,down,32,1,FieldShape.SPHERE,"climber entry"));return id;
        }
        void removeField(UUID id) {GravityFields.remove(id);fields.remove(id);EntityGravitySystem.tick(world.getServer());}
        @Override public void close() {
            if(closed)return;closed=true;for(UUID id:fields)GravityFields.remove(id);for(var task:cleanup)task.run();for(Entity actor:actors)actor.discard();
            EntityGravitySystem.tick(world.getServer());
            if(built)for(var at:BlockPos.iterate(min,max))world.setBlockState(at,Blocks.AIR.getDefaultState(),2);
            for(long key:added)world.setChunkForced(ChunkPos.getPackedX(key),ChunkPos.getPackedZ(key),false);
        }
    }

    private static Map<String,Object> state(SpiderEntity actor) {
        var box=actor.getBoundingBox();var result=new LinkedHashMap<String,Object>();
        result.put("position",vector(actor.getPos()));result.put("bounds",List.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        result.put("eye",vector(actor.getEyePos()));result.put("velocity",vector(actor.getVelocity()));result.put("onGround",actor.isOnGround());
        result.put("noGravity",actor.hasNoGravity());result.put("nativeClimbing",actor.isClimbing());
        result.put("uuid",actor.getUuid().toString());result.put("aiDisabled",actor.isAiDisabled());
        result.put("worldOwnsEntity",actor.getWorld() instanceof ServerWorld world&&world.getEntityById(actor.getId())==actor);
        result.put("runtimeGravityLease",EntityGravitySystem.suppressesVanillaGravity(actor));
        result.put("navigation",actor.getNavigation().getClass().getName());result.put("navigationIdentity",System.identityHashCode(actor.getNavigation()));
        result.put("control",actor.getMoveControl().getClass().getName());result.put("controlIdentity",System.identityHashCode(actor.getMoveControl()));
        result.put("orientationAdapterSupportedDiagnosticOnly",ActorFrames.supported(actor));result.put("localUpDiagnosticOnly",vector(ActorFrames.up(actor)));
        return result;
    }
    private static List<Double> vector(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static void emit(String phase,Vec3d original,SpiderEntity actor,Map<String,Object> details) {
        var row=new LinkedHashMap<String,Object>();row.put("phase",phase);row.put("originalPosition",vector(original));row.put("actual",state(actor));row.putAll(details);
        System.out.println("CLIMBER_FIELD "+new com.google.gson.Gson().toJson(row));
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-ground-local-floor",tickLimit=320)
    public void nativeGroundWalkerGoalsRetainOrdinaryFloorProgress(TestContext c){groundWalkers(c,new Vec3d(0,1,0));}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-ground-local-wall",tickLimit=320)
    public void nativeGroundWalkerGoalsUseTheirActualWallFrame(TestContext c){groundWalkers(c,new Vec3d(1,0,0));}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-ground-local-ceiling",tickLimit=320)
    public void nativeGroundWalkerGoalsUseTheirActualCeilingFrame(TestContext c){groundWalkers(c,new Vec3d(0,-1,0));}

    private static final class GroundPursuit {
        final net.minecraft.entity.mob.MobEntity actor;final CowEntity target;final Vec3d start;final int spawnAge;
        final Object navigation,control;final double initialDistance;Vec3d last;int lastAge;double maxStep,maxGap;boolean pathObserved;
        GroundPursuit(net.minecraft.entity.mob.MobEntity a,CowEntity t){actor=a;target=t;start=last=a.getPos();spawnAge=lastAge=a.age;navigation=a.getNavigation();control=a.getMoveControl();initialDistance=start.distanceTo(t.getPos());}
    }
    private static void groundWalkers(TestContext c,Vec3d up) {
        var centre=c.getAbsolutePos(new BlockPos(6,80,6));var room=new Room(c,centre.add(-8,-8,-8),centre.add(8,8,8));
        var cases=new ArrayList<GroundPursuit>();boolean[] spawned={false};
        watch(c,room,310,()->{
            if(!room.loaded())return false;room.build();
            if(!spawned[0]) {
                room.field(centre,up.negate());int i=0;
                for(var type:new EntityType<?>[]{EntityType.ZOMBIE,EntityType.HUSK}) {
                    var actor=(net.minecraft.entity.mob.MobEntity)type.create(room.world);actor.setAiDisabled(true);actor.setPersistent();
                    var sideways=com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(i++==0?-3:3,0,-2.5),up);
                    var start=Vec3d.ofCenter(centre).subtract(up.multiply(7.48)).add(sideways);
                    actor.setPosition(start);actor.setYaw(0);actor.setPitch(0);
                    c.assertTrue(ActorFrames.trySetUp(actor,up),"admission: loaded in-place native ground frame before registration: "+type);
                    room.spawn(actor);
                    var target=EntityType.COW.create(room.world);target.setAiDisabled(true);target.setNoGravity(true);target.setInvulnerable(true);
                    target.setPosition(start.add(com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(0,0,5),up)));
                    c.assertTrue(ActorFrames.trySetUp(target,up),"admission: target's actual body shares this physical surface");room.spawn(target);
                    EntityGravitySystem.tick(room.world.getServer());
                    c.assertTrue(actor.getPos().equals(start)&&ActorFrames.up(actor).distanceTo(up)<1e-6&&groundSupport(c,actor,up)<=.08,
                        "admission: actual current FULL support, normal and clear body before enabling native AI");
                    actor.setAiDisabled(false);actor.setTarget(target); // one disclosed fixture target; no route/motor/velocity commands
                    cases.add(new GroundPursuit(actor,target));
                }
                spawned[0]=true;return false;
            }
            boolean complete=true;
            for(var r:cases) {
                var a=r.actor;if(a.age==r.lastAge){complete=false;continue;}
                int elapsed=a.age-r.spawnAge;
                double step=a.getPos().distanceTo(r.last)/Math.max(1,a.age-r.lastAge);r.maxStep=Math.max(r.maxStep,step);r.last=a.getPos();r.lastAge=a.age;
                r.maxGap=Math.max(r.maxGap,groundSupport(c,a,up));
                r.pathObserved|=a.getNavigation().getCurrentPath()!=null&&!a.getNavigation().isIdle();
                c.assertTrue(a.getTarget()==r.target&&!a.isAiDisabled()&&a.getNavigation()==r.navigation&&a.getMoveControl()==r.control
                    &&ActorFrames.up(a).distanceTo(up)<1e-6&&!a.hasNoGravity(),"real native goals/controllers/body frame and saved flags remain authoritative: "+a.getType());
                c.assertTrue(r.maxStep<=1,"no native ground actor may be teleported or phase more than one metre per ordinary actor tick");
                if(elapsed<ORDINARY_TICKS){complete=false;continue;}
                double progress=r.initialDistance-a.getPos().distanceTo(r.target.getPos());
                var row=new LinkedHashMap<String,Object>();row.put("type",a.getType().toString());row.put("up",vector(up));row.put("ageTicks",elapsed);row.put("progress",progress);row.put("maxStep",r.maxStep);row.put("maxGap",r.maxGap);row.put("nativePath",r.pathObserved);row.put("position",vector(a.getPos()));
                System.out.println("GROUND_LOCAL_NATIVE "+new com.google.gson.Gson().toJson(row));
                c.assertTrue(progress>=MINIMUM_PURSUIT&&r.pathObserved,"actual native ground goal must make at least1m supported progress in60 ordinary actor ticks: "+row);
            }
            return complete;
        });
    }
    private static double groundSupport(TestContext c,net.minecraft.entity.LivingEntity actor,Vec3d up) {
        c.assertTrue(c.getWorld().isSpaceEmpty(actor),"actual native ground body stays clear of loaded material");
        var hit=c.getWorld().raycast(new RaycastContext(actor.getPos().add(up.multiply(.04)),actor.getPos().subtract(up.multiply(1.6)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor));
        c.assertTrue(hit.getType()==HitResult.Type.BLOCK&&Vec3d.of(hit.getSide().getVector()).dotProduct(up)>=.95,"actual support normal must match local up, not an onGround flag");
        double gap=actor.getPos().subtract(hit.getPos()).dotProduct(up);
        c.assertTrue(gap>=-1e-6&&gap<=.08,"native ground goal must preserve actual physical support within the existing .08m skin: "+gap);
        return gap;
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-wall-navigation",tickLimit=220)
    public void shapesInfantryUsesItsNativeMotorForAnOffPlaneObservation(TestContext c) {
        var centre=c.getAbsolutePos(new BlockPos(6,80,6));
        var room=new Room(c,centre.add(-8,-8,-8),centre.add(8,8,8));
        var type=com.terminaldetector.drmd.entity.ModEntities.SHAPES_INFANTRY;
        var up=new Vec3d(1,0,0);
        var start=Vec3d.ofCenter(centre).subtract(up.multiply(7.48)).add(0,0,-2.5);
        var marker=start.add(2,0,5);
        var capture=new com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity[1];
        int[] age={0},lastCommand={-10};
        boolean[] path={false};
        watch(c,room,210,()->{
            if(!room.loaded())return false;
            room.build();
            if(capture[0]==null){
                room.field(centre,up.negate());
                var actor=type.create(room.world);
                actor.setPersistent();actor.setPosition(start);actor.setAiDisabled(true);
                c.assertTrue(ActorFrames.trySetUp(actor,up),"tall oriented body fits the real wall");
                room.spawn(actor);
                EntityGravitySystem.tick(room.world.getServer());
                c.assertTrue(up.equals(com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.cardinalPhysicsUp(actor)),
                    "actual Shapes movement controllers qualify for the cardinal surface motor");
                groundSupport(c,actor,up);
                actor.setAiDisabled(false);
                capture[0]=actor;age[0]=actor.age;
                return false;
            }
            var actor=capture[0];
            int elapsed=actor.age-age[0];
            if(elapsed-lastCommand[0]>=6){
                actor.navigateInfantry(marker);lastCommand[0]=elapsed;
            }
            path[0]|=com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.localMotor(actor);
            groundSupport(c,actor,up);
            if(elapsed<50&&!(path[0]&&actor.getZ()-start.z>1))return false;
            c.assertTrue(path[0]&&actor.getZ()-start.z>1,"actual wall route moves the tall infantry toward the copied marker");
            return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-c1-controller-admission",tickLimit=180)
    public void supportedNativeGroundControllersShareCardinalInputButCustomControllersRemainNative(TestContext c){
        var centre=c.getAbsolutePos(new BlockPos(6,80,6));var room=new Room(c,centre.add(-8,-8,-8),centre.add(8,8,8));
        watch(c,room,170,()->{
            if(!room.loaded())return false;room.build();var up=new Vec3d(1,0,0);room.field(centre,up.negate());
            var at=Vec3d.ofCenter(centre).subtract(up.multiply(7.48));
            for(var type:new EntityType<?>[]{EntityType.COW,EntityType.PIG,EntityType.SHEEP,EntityType.ZOMBIE,EntityType.HUSK,EntityType.SKELETON,EntityType.STRAY,EntityType.CREEPER}){
                var a=(net.minecraft.entity.mob.MobEntity)type.create(room.world);a.setAiDisabled(true);a.setPersistent();a.setPosition(at);
                c.assertTrue(ActorFrames.trySetUp(a,up),"actual native C1 frame admitted before registration: "+type);room.spawn(a);
                EntityGravitySystem.tick(room.world.getServer());
                c.assertTrue(com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.framed(a)
                    &&up.equals(com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.cardinalPhysicsUp(a)),"all supported exact native C1 controllers admit the same dry cardinal physical adapter: "+type);
                var input=new Vec3d(.2,0,.3);var output=com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.input(a,input);
                c.assertTrue(a.getNavigation().isIdle()&&Math.abs(output.dotProduct(up))<1e-12&&Math.abs(output.lengthSquared()-input.lengthSquared())<1e-12,
                    "genuine idle-navigation native locomotor input remains in the support tangent, including BowAttackGoal strafe: "+type);
                a.discard();
            }
            var custom=new net.minecraft.entity.passive.CowEntity(EntityType.COW,room.world){
                {moveControl=new net.minecraft.entity.ai.control.MoveControl(this){};}
            };
            custom.setAiDisabled(true);custom.setPosition(at);c.assertTrue(ActorFrames.trySetUp(custom,up),"custom-controller refusal control has a valid physical pose");room.spawn(custom);
            EntityGravitySystem.tick(room.world.getServer());var input=new Vec3d(.2,0,.3);
            c.assertTrue(!com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.framed(custom)
                &&com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.cardinalPhysicsUp(custom)==null
                &&com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation.input(custom,input).equals(input),"a custom control is never borrowed merely because its entity advertises a supported vanilla type");
            return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-c1-tempt-floor",tickLimit=320)
    public void passiveNativeTemptGoalsPursueTheActualFoodHoldingPlayerOnTheOrdinaryFloor(TestContext c){nativeGoals(c,new Vec3d(0,1,0),false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-c1-tempt-wall",tickLimit=320)
    public void passiveNativeTemptGoalsPursueTheActualFoodHoldingPlayerOnTheWall(TestContext c){nativeGoals(c,new Vec3d(1,0,0),false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-c1-tempt-ceiling",tickLimit=320)
    public void passiveNativeTemptGoalsPursueTheActualFoodHoldingPlayerOnTheCeiling(TestContext c){nativeGoals(c,new Vec3d(0,-1,0),false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-c1-bow-wall",tickLimit=320)
    public void nativeSkeletonAndStrayBowGoalsAcquireARealPlayerNavigateAndFireFromTheWall(TestContext c){nativeGoals(c,new Vec3d(1,0,0),true);}

    private static final class GoalProgress {
        final net.minecraft.entity.mob.MobEntity actor;final Object navigation,control;final Vec3d start;final int born;
        final double distance;int age,passiveReceipts;Vec3d last;boolean path,arrow,nativeTarget;double maxStep,maxGap;
        GoalProgress(net.minecraft.entity.mob.MobEntity a,net.minecraft.server.network.ServerPlayerEntity target){actor=a;navigation=a.getNavigation();control=a.getMoveControl();start=last=a.getPos();born=age=a.age;distance=a.distanceTo(target);}
    }
    private static void nativeGoals(TestContext c,Vec3d up,boolean ranged){
        var centre=c.getAbsolutePos(new BlockPos(6,80,6));var room=new Room(c,centre.add(-8,-8,-8),centre.add(8,8,8));
        var cases=new ArrayList<GoalProgress>();var pending=new ArrayList<net.minecraft.entity.mob.MobEntity>();NativeFoodPlayer[] user={null};boolean[] spawned={false};
        watch(c,room,310,()->{
            if(!room.loaded())return false;room.build();
            if(user[0]==null){
                room.field(centre,up.negate());var target=Vec3d.ofCenter(centre).subtract(up.multiply(6.8))
                    .add(com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(0,0,ranged?4.5:2.5),up));
                user[0]=nativeFoodPlayer(room,target);return false;
            }
            var p=user[0].player;
            c.assertTrue(p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!DescentPlayerData.get(p).isEnabled(),"real ordinary survival target retains native authority without a synthetic target override");
            if(!spawned[0]){
                if(!com.terminaldetector.drmd.world.gravity.FootGravitySystem.isActive(p)
                    ||com.terminaldetector.drmd.world.gravity.FootGravitySystem.getUp(p).distanceTo(up)>1e-6
                    ||!com.terminaldetector.drmd.world.gravity.FootGravitySystem.get(p).grounded())return false;
                if(ranged&&!room.dark(p.getEyePos()))return false;
                groundSupport(c,p,up);
                var types=ranged?new EntityType<?>[]{EntityType.SKELETON,EntityType.STRAY}:new EntityType<?>[]{EntityType.COW,EntityType.PIG,EntityType.SHEEP};
                int i=0;
                if(pending.isEmpty())for(var type:types){
                    var a=(net.minecraft.entity.mob.MobEntity)type.create(room.world);a.setAiDisabled(true);a.setPersistent();
                    double lateral=ranged?(i++==0?-3:3):(i++-1)*3;
                    var start=p.getPos().add(com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(lateral,0,ranged?-9:-5),up));
                    a.setPosition(start);c.assertTrue(ActorFrames.trySetUp(a,up),"normal native goal starts in a checked clear local frame: "+type);room.spawn(a);
                    if(ranged){a.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOW));}
                    c.assertTrue(groundSupport(c,a,up)<=.08,"actual native goal support admission");
                    pending.add(a);
                }
                if(ranged)for(var a:pending)if(!room.dark(a.getEyePos()))return false;
                for(var a:pending){groundSupport(c,a,up);a.setAiDisabled(false);cases.add(new GoalProgress(a,p));} // no setTarget, path, velocity, motor or manual actor tick
                spawned[0]=true;return false;
            }
            boolean complete=true;
            for(var r:cases){
                var a=r.actor;if(a.age==r.age){complete=false;continue;}
                r.maxStep=Math.max(r.maxStep,a.getPos().distanceTo(r.last)/Math.max(1,a.age-r.age));r.last=a.getPos();r.age=a.age;
                r.maxGap=Math.max(r.maxGap,groundSupport(c,a,up));r.path|=a.getNavigation().getCurrentPath()!=null&&!a.getNavigation().isIdle();r.nativeTarget|=a.getTarget()==p;
                c.assertTrue(a.isAlive()&&!a.isAiDisabled()&&!a.hasNoGravity()&&a.getNavigation()==r.navigation&&a.getMoveControl()==r.control
                    &&ActorFrames.up(a).distanceTo(up)<1e-6&&r.maxStep<=1,"native controller/AI/input/body ownership and finite ordinary movement remain intact: "+a.getType());
                if(!ranged&&up.y<-.5&&r.passiveReceipts++<64){
                    // Existing native state only: no extra world query, planner/AI call or fixture command.
                    var nav=a.getNavigation();var path=nav.getCurrentPath();var motor=a.getMoveControl();var trace=new LinkedHashMap<String,Object>();
                    trace.put("type",a.getType().toString());trace.put("age",a.age);trace.put("elapsed",a.age-r.born);trace.put("position",vector(a.getPos()));trace.put("velocity",vector(a.getVelocity()));
                    trace.put("playerAge",p.age);trace.put("playerPosition",vector(p.getPos()));trace.put("playerVelocity",vector(p.getVelocity()));trace.put("distance",a.distanceTo(p));trace.put("progress",r.distance-a.distanceTo(p));
                    trace.put("input",List.of(a.sidewaysSpeed,a.forwardSpeed));trace.put("yaw",a.getYaw());trace.put("pathSeen",r.path);trace.put("nativeIdle",nav.isIdle());
                    trace.put("pathClass",path==null?"ABSENT":path.getClass().getName());trace.put("pathIndex",path==null?-1:path.getCurrentNodeIndex());trace.put("pathLength",path==null?0:path.getLength());
                    trace.put("pathTarget",path==null?List.of():List.of(path.getTarget().getX(),path.getTarget().getY(),path.getTarget().getZ()));
                    trace.put("wanted",List.of(motor.getTargetX(),motor.getTargetY(),motor.getTargetZ()));trace.put("wantedSpeed",motor.getSpeed());trace.put("supportGap",r.maxGap);
                    trace.put("hurtTime",a.hurtTime);trace.put("nativeTarget",a.getTarget()==null?"ABSENT":a.getTarget().getUuid().toString());
                    System.out.println("C1_CEILING_NATIVE_PASSIVE "+new com.google.gson.Gson().toJson(trace));
                }
                if(ranged)for(var arrow:room.world.getEntitiesByClass(net.minecraft.entity.projectile.PersistentProjectileEntity.class,new Box(Vec3d.of(room.min),Vec3d.of(room.max.add(1,1,1))),v->v.getOwner()==a)){
                    if(arrow.age>0&&arrow.getVelocity().lengthSquared()>.01)r.arrow=true;
                }
                if(a.age-r.born<ORDINARY_TICKS){complete=false;continue;}
                double progress=r.distance-a.distanceTo(p);var row=new LinkedHashMap<String,Object>();
                row.put("type",a.getType().toString());row.put("up",vector(up));row.put("actualActorTicks",a.age-r.born);row.put("actualPlayerAge",p.age);row.put("progress",progress);
                row.put("nativePath",r.path);row.put("nativeTargetPlayer",r.nativeTarget);row.put("ordinaryOwnedArrow",r.arrow);row.put("maxStep",r.maxStep);row.put("maxGap",r.maxGap);
                row.put("targetAssignedByObserver",false);row.put("nativeFoodInHands",List.of(p.getMainHandStack().getItem().toString(),p.getOffHandStack().getItem().toString()));
                System.out.println("C1_NATIVE_GOAL "+new com.google.gson.Gson().toJson(row));
                c.assertTrue(progress>=1&&r.path&&(!ranged||r.nativeTarget&&r.arrow),"actual native food or ranged goal must deliver supported progress, plus real native ranged acquisition/projectile when applicable: "+row);
            }
            return complete;
        });
    }
    private record NativeFoodPlayer(net.minecraft.server.network.ServerPlayerEntity player,net.minecraft.network.ClientConnection connection,io.netty.channel.embedded.EmbeddedChannel channel){}
    private static NativeFoodPlayer nativeFoodPlayer(Room room,Vec3d position){
        var w=room.world;var data=net.minecraft.server.network.ConnectedClientData.createDefault(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"c1-native-goal"),false);
        var p=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,data.gameProfile(),data.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        w.getServer().getPlayerManager().onPlayerConnect(connection,p,data);
        room.cleanup.add(()->{com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());
            w.getServer().getNetworkIo().getConnections().remove(connection);w.getServer().getPlayerManager().remove(p);p.discard();channel.finishAndReleaseAll();});
        channel.runPendingTasks();channel.flushOutbound();int confirmations=0;
        for(var packet:List.copyOf(channel.outboundMessages()))if(packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket teleport){channel.writeInbound(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(teleport.getTeleportId()));confirmations++;}
        room.context.assertTrue(confirmations>0,"real native initial teleport confirmed before one-time player fixture positioning");
        p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().flying=false;p.sendAbilitiesUpdate();
        p.setPosition(position);p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.WHEAT));p.setStackInHand(net.minecraft.util.Hand.OFF_HAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.CARROT));
        if(!w.getServer().getNetworkIo().getConnections().contains(connection))w.getServer().getNetworkIo().getConnections().add(connection);
        return new NativeFoodPlayer(p,connection,channel);
    }

    private static final class MaterialActor {
        final SpiderEntity actor;final int species;final Vec3d up,x,z;int impulseAge=-1;Vec3d after,impulsePosition;
        MaterialActor(SpiderEntity actor,int species,Vec3d up){this.actor=actor;this.species=species;this.up=up;
            x=com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(1,0,0),up);z=com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(0,0,1),up);}
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="climber-native-material-parity",tickLimit=320)
    public void actualSlowSupportMaterialAndBothTangentsMatchTheNativeFloorOnWallsAndCeilings(TestContext c){
        var centre=c.getAbsolutePos(new BlockPos(6,80,6));var room=new Room(c,centre.add(-8,-8,-8),centre.add(8,8,8));
        var cases=new ArrayList<MaterialActor>();var changed=new ArrayList<BlockPos>();int[] phase={0};UUID[] field={null};
        double[][] ordinary=new double[2][2],slowFloor=new double[2][2];
        watch(c,room,310,()->{
            if(!room.loaded())return false;room.build();
            if(cases.isEmpty()){
                var up=phase[0]<2?new Vec3d(0,1,0):phase[0]==2?new Vec3d(1,0,0):new Vec3d(0,-1,0);
                for(var p:changed)room.world.setBlockState(p,Blocks.OBSIDIAN.getDefaultState(),2);changed.clear();
                var material=phase[0]==0?Blocks.OBSIDIAN:Blocks.SOUL_SAND;
                for(var p:BlockPos.iterate(room.min,room.max))if(up.y>.5?p.getY()==room.min.getY():up.x>.5?p.getX()==room.min.getX():p.getY()==room.max.getY()){
                    room.world.setBlockState(p,material.getDefaultState(),2);changed.add(p.toImmutable());
                }
                field[0]=room.field(centre,up.negate());int i=0;
                for(var type:TYPES){
                    var a=(SpiderEntity)type.create(room.world);a.setAiDisabled(true);a.setPersistent();
                    var startRay=Vec3d.ofCenter(centre).add(com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(i==0?-3:3,0,0),up));
                    var hit=room.world.raycast(new RaycastContext(startRay,startRay.subtract(up.multiply(8.5)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,a));
                    c.assertTrue(hit.getType()==HitResult.Type.BLOCK&&Vec3d.of(hit.getSide().getVector()).dotProduct(up)>=.95
                        &&room.world.getBlockState(hit.getBlockPos()).isOf(material),"actual loaded native support/material before one checked frame setup");
                    a.setPosition(hit.getPos().add(up.multiply(.02)));c.assertTrue(ActorFrames.trySetUp(a,up),"clear checked body against the actual material collider");room.spawn(a);
                    cases.add(new MaterialActor(a,i++,up));
                }
                return false;
            }
            boolean complete=true;
            for(var r:cases){
                var a=r.actor;groundSupport(c,a,r.up);
                boolean measuring=r.impulseAge>=0&&r.after==null;
                c.assertTrue(a.isAlive()&&a.isAiDisabled()!=measuring&&!a.hasNoGravity()&&ActorFrames.up(a).distanceTo(r.up)<1e-6,"ordinary native material probe retains its disclosed one-tick real-AI/frame/gravity ownership");
                if(a.age<3||!EntityGravitySystem.suppressesVanillaGravity(a)){complete=false;continue;}
                var expected=phase[0]==0?Blocks.OBSIDIAN:Blocks.SOUL_SAND;
                var actual=room.world.getBlockState(a.getVelocityAffectingPos());
                c.assertTrue(actual.isOf(expected),"actual native getVelocityAffectingPos must resolve the physical support, including the Entity.move multiplier caller: phase="+phase[0]+" "+a.getType()+" actual="+actual);
                if(r.impulseAge<0){
                    // NoAI suppresses native travel entirely. Enable exactly one real ordinary
                    // tick on vanilla's odd (age+id) fast-selector branch. No goals have ever
                    // run, so tickGoals(false) cannot start a random/new goal or locomotor input.
                    // ServerWorld increments age before tick; neither ticks nor RNG are invoked here.
                    if(((a.age+1+a.getId())&1)==0){complete=false;continue;}
                    r.impulseAge=a.age;r.impulsePosition=a.getPos();a.setAiDisabled(false);
                    a.setVelocity(r.x.add(r.z).multiply(.2));complete=false;continue;
                } // one disclosed external tangent impulse, never a pose follower
                if(a.age==r.impulseAge){complete=false;continue;}
                if(r.after==null){
                    c.assertTrue(a.age==r.impulseAge+1,"one actual ordinary actor tick resolves the material impulse before comparison");r.after=a.getVelocity();
                    c.assertTrue(((a.age+a.getId())&1)==1&&!a.isAiDisabled()&&a.getTarget()==null&&a.getNavigation().isIdle()
                        &&Math.abs(a.forwardSpeed)<1e-7&&Math.abs(a.sidewaysSpeed)<1e-7,
                        "the measured real native travel tick has no goal-selected target/path/locomotor input");
                    a.setAiDisabled(true); // the single native travel sample is complete; no second AI tick
                    c.assertTrue(a.getPos().distanceTo(r.impulsePosition)<=1,"actual ordinary material movement cannot teleport more than the frozen one-metre step bound");
                    double vx=r.after.dotProduct(r.x),vz=r.after.dotProduct(r.z);
                    var receipt=new LinkedHashMap<String,Object>();receipt.put("phase",phase[0]);receipt.put("species",a.getType().toString());receipt.put("up",vector(r.up));
                    receipt.put("nativeActorTick",a.age-r.impulseAge);receipt.put("sampleNativeAiDisabled",false);receipt.put("sampleNativeFastSelectorParity",(a.age+a.getId())&1);
                    receipt.put("actualMaterial",actual.toString());receipt.put("localTangents",List.of(vx,vz));receipt.put("normal",r.after.dotProduct(r.up));receipt.put("actualSupportGap",groundSupport(c,a,r.up));
                    System.out.println("CLIMBER_MATERIAL_NATIVE_PREASSERT "+new com.google.gson.Gson().toJson(receipt));
                    c.assertTrue(vx>0&&vz>0&&Math.abs(vx-vz)<1e-6,"both actual support tangents retain the same material factor, including the wall's world-Y tangent");
                    if(phase[0]==0){ordinary[r.species][0]=vx;ordinary[r.species][1]=vz;}
                    else if(phase[0]==1){slowFloor[r.species][0]=vx;slowFloor[r.species][1]=vz;
                        c.assertTrue(Math.abs(vx/ordinary[r.species][0]-(double).4f)<1e-5&&Math.abs(vz/ordinary[r.species][1]-(double).4f)<1e-5,"real native soul sand contributes its original .4 multiplier before the separate dry damping");}
                    else c.assertTrue(Math.abs(vx-slowFloor[r.species][0])<1e-6&&Math.abs(vz-slowFloor[r.species][1])<1e-6,"actual wall/ceiling material motion must match the ordinary native slow-floor control");
                    System.out.println("CLIMBER_MATERIAL_NATIVE "+new com.google.gson.Gson().toJson(Map.of("phase",phase[0],"species",a.getType().toString(),"up",vector(r.up),"nativeActorTick",a.age-r.impulseAge,
                        "actualMaterial",actual.toString(),"localTangents",List.of(vx,vz),"normal",r.after.dotProduct(r.up),"actualSupportGap",groundSupport(c,a,r.up))));
                }
            }
            if(!complete)return false;
            for(var r:cases)r.actor.discard();cases.clear();room.removeField(field[0]);field[0]=null;
            return ++phase[0]==4;
        });
    }
}
