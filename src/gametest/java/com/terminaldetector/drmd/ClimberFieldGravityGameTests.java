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
            if(closed)return;closed=true;for(UUID id:fields)GravityFields.remove(id);for(Entity actor:actors)actor.discard();
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
}
