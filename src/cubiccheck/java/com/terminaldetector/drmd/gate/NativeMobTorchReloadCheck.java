package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in writer/reader of a real closed native world. Never ticks or reconstructs an actor. */
public final class NativeMobTorchReloadCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.mobTorchColdWrite");
    public static final boolean READ=Boolean.getBoolean("drmd.gate.mobTorchColdRead");
    public static final boolean ENABLED=WRITE||READ;
    private static final String[] LABELS={"C1","C2","C3","C4","C5","C6","C7"};
    private static final ActorPhysicsProfile.Family[] FAMILIES={ActorPhysicsProfile.Family.GROUND,
        ActorPhysicsProfile.Family.CLIMBER,ActorPhysicsProfile.Family.FLYER,
        ActorPhysicsProfile.Family.AQUATIC,ActorPhysicsProfile.Family.MECHANICAL,
        ActorPhysicsProfile.Family.SPECIAL,ActorPhysicsProfile.Family.ANCHORED};
    // Outside every real8m torch field: native Creative hover must not borrow a foot lease.
    private static final Vec3d OBSERVER=new Vec3d(36,2216,36);
    private static final Vec3d UP=new Vec3d(1,0,0);
    private static final String PROCESS=UUID.randomUUID().toString();
    private static final long PID=ProcessHandle.current().pid();
    private static final Map<UUID,Slot> SLOTS=new ConcurrentHashMap<>();
    private static final List<Map<String,Object>> ROWS=new ArrayList<>();
    private static volatile UUID viewer;
    private static volatile String failure,phase="BOOT",writerProcess;
    private static volatile long writerPid=-1;
    private static volatile boolean done,stopping;
    private static boolean staged,built;
    private static int ticks,serverTicks;
    private static long boot;

    private static final class Slot {
        final int index; final UUID uuid; final String type,javaClass,navigation,control,worldKey;
        final boolean noGravity; final BlockPos base,source;
        volatile NbtCompound saved;
        volatile MobEntity decoded,loaded;
        volatile Map<String,Object> decodeReceipt,loadReceipt;
        int forceCalls,firstSourceAge=-1,readyAge=-1;
        boolean admitted;
        Vec3d beforeForce;int forceAge;
        Map<String,Object> forceReceipt;
        Slot(int i,MobEntity actor){
            index=i;uuid=actor.getUuid();type=Registries.ENTITY_TYPE.getId(actor.getType()).toString();
            javaClass=actor.getClass().getName();navigation=actor.getNavigation().getClass().getName();
            control=actor.getMoveControl().getClass().getName();noGravity=actor.hasNoGravity();
            worldKey=actor.getWorld().getRegistryKey().getValue().toString();
            base=base(i);source=base.add(1,4,4);
        }
        Slot(int i,com.google.gson.JsonObject n){
            index=i;uuid=UUID.fromString(n.get("uuid").getAsString());type=n.get("type").getAsString();
            javaClass=n.get("javaClass").getAsString();navigation=n.get("navigation").getAsString();
            control=n.get("control").getAsString();noGravity=n.get("noGravity").getAsBoolean();
            worldKey=n.get("worldKey").getAsString();
            base=base(i);source=base.add(1,4,4);
        }
    }
    private NativeMobTorchReloadCheck(){}
    private static BlockPos base(int i){return new BlockPos(12+20*(i%3),2200,12+20*(i/3));}
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static List<Integer> xyz(BlockPos v){return List.of(v.getX(),v.getY(),v.getZ());}
    private static void write(String name,Object data)throws Exception{
        Files.createDirectories(file("mob-torch-cold"));
        Files.writeString(file("mob-torch-cold/"+name),new GsonBuilder().setPrettyPrinting().create().toJson(data)+"\n");
    }
    public static void register(){
        require(WRITE!=READ,"exactly one cold writer/reader profile");
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable native runtime only");
        boot=System.nanoTime();
        if(READ)try{
            var manifest=JsonParser.parseString(Files.readString(file("mob-torch-cold/manifest.json"))).getAsJsonObject();
            writerProcess=manifest.get("process").getAsString();require(!PROCESS.equals(writerProcess),"reader process nonce differs");
            writerPid=manifest.get("pid").getAsLong();require(PID!=writerPid,"reader is a different actual OS process");
            var actors=manifest.getAsJsonArray("actors");require(actors.size()==7,"seven native saved families");
            for(int i=0;i<7;i++){
                Slot s=new Slot(i,actors.get(i).getAsJsonObject());
                s.saved=StringNbtReader.parse(Files.readString(file("mob-torch-cold/"+s.uuid+".snbt")));
                require(s.saved.getUuid("UUID").equals(s.uuid),"actual serialized UUID matches manifest");SLOTS.put(s.uuid,s);
            }
        }catch(Exception error){throw new IllegalStateException("closed writer inputs required before reader bootstrap",error);}
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            if(WRITE){stopping=true;phase="NATIVE_SHUTDOWN_SAVE";}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{
            if(!WRITE||!done||failure!=null)return;
            try{require(SLOTS.size()==7&&SLOTS.values().stream().allMatch(s->s.saved!=null),"all seven native shutdown writeNbt calls observed");
                write("writer-closed.json",Map.of("status","PASS","process",PROCESS,"pid",PID,"nativeShutdownWrites",7,
                    "fixtureReadyOnlyBeforeStop",true,"serverStoppedCallback",true));
            }catch(Throwable error){failure=error.toString();try{write("writer-closed.json",Map.of("status","FAIL","reason",failure));}catch(Exception ignored){}}
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(!ENABLED||entity.getWorld().isClient||!SLOTS.containsKey(entity.getUuid()))return;
            try{
                Slot s=SLOTS.get(entity.getUuid());require(entity instanceof MobEntity,"native loaded mob type");
                MobEntity actor=(MobEntity)entity;
                if(WRITE){require(s.loaded==actor,"native writer ENTITY_LOAD receives the original spawned object");s.loadReceipt=basic(actor);return;}
                require(s.decoded==actor,"ENTITY_LOAD receives actual decoded object");
                require(s.loaded==null||s.loaded==actor,"no duplicate loaded object");
                require(!EntityGravitySystem.suppressesVanillaGravity(actor),"no previous process lease at native ENTITY_LOAD");
                s.loaded=actor;s.loadReceipt=basic(actor);flushLifecycle();
            }catch(Throwable error){failure=error.toString();}
        });
        ServerTickEvents.END_SERVER_TICK.register(NativeMobTorchReloadCheck::server);
        ClientTickEvents.END_CLIENT_TICK.register(NativeMobTorchReloadCheck::client);
    }

    /** Called only from the real Entity.writeNbt RETURN during actual server shutdown. */
    public static void saved(Entity entity,NbtCompound n){
        if(!WRITE||!stopping||entity.getWorld().isClient)return;
        Slot s=SLOTS.get(entity.getUuid());if(s==null)return;
        try{
            require(entity==s.loaded,"closing serialization must be the admitted live native object");
            require(n.getBoolean("NoGravity")==s.noGravity,"saved authored gravity flag");
            require(n.getBoolean("NoAI"),"disclosed fixture AI isolation serialized by native save");
            s.saved=n.copy();Files.writeString(file("mob-torch-cold/"+s.uuid+".snbt"),n.toString());
        }catch(Throwable error){failure=error.toString();}
    }
    /** Called from Entity.readNbt RETURN; no world query, writeNbt or actor tick here. */
    public static void loaded(Entity entity,NbtCompound n){
        if(!READ||entity.getWorld().isClient)return;
        Slot s=SLOTS.get(entity.getUuid());if(s==null)return;
        try{
            require(entity instanceof MobEntity,"actual native saved entity is a mob");MobEntity actor=(MobEntity)entity;
            require(s.decoded==null||s.decoded==actor,"saved UUID cannot decode into two objects");
            for(String key:new String[]{"UUID","Pos","Motion","NoGravity","NoAI","drmd:actor_frame"})
                require(java.util.Objects.equals(n.get(key),s.saved.get(key)),"raw closed-world NBT changed: "+LABELS[s.index]+" "+key);
            verifyIdentity(s,actor);
            var pos=n.getList("Pos",NbtElement.DOUBLE_TYPE);
            require(actor.getPos().squaredDistanceTo(new Vec3d(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2)))<=1e-20,"native pre-tick position matches saved Pos");
            require(!EntityGravitySystem.suppressesVanillaGravity(actor),"runtime force lease is absent before restored actor's first tick");
            var frame=n.getCompound("drmd:actor_frame");var expected=frame.getInt("version")==1?
                ActorFrameMath.decode(frame.getFloat("x"),frame.getFloat("y"),frame.getFloat("z")):Vec3d.ZERO;
            require(ActorFrames.up(actor).squaredDistanceTo(expected)<=1e-15,"saved frame is native-decoded independently of runtime lease");
            s.decoded=actor;s.decodeReceipt=basic(actor);flushLifecycle();
        }catch(Throwable error){failure=error.toString();}
    }
    public static void beforeForce(LivingEntity actor,Vec3d up,float strength,boolean forceOnly){
        if(!ENABLED||done)return;Slot s=SLOTS.get(actor.getUuid());
        if(s==null||s.loaded!=actor||s.forceCalls>=64)return;
        s.beforeForce=actor.getVelocity();s.forceAge=actor.age;
    }
    public static void afterForce(LivingEntity actor,Vec3d up,float strength,boolean forceOnly){
        if(!ENABLED||done)return;Slot s=SLOTS.get(actor.getUuid());
        if(s==null||s.loaded!=actor||s.beforeForce==null||s.forceAge!=actor.age||s.forceCalls>=64)return;
        Vec3d before=s.beforeForce;s.beforeForce=null;s.forceCalls++;
        var r=new LinkedHashMap<String,Object>();r.put("age",actor.age);r.put("before",xyz(before));r.put("after",xyz(actor.getVelocity()));
        r.put("strength",strength);r.put("up",xyz(up));r.put("forceOnly",forceOnly);
        r.put("actualOperator","EntityGravitySystem.applyMobPhysics HEAD/RETURN");
        boolean exact=!forceOnly||actor.getVelocity().squaredDistanceTo(before.add(up.negate().multiply(.06*strength)))<=1e-16;
        r.put("forceDeltaExact",exact);s.forceReceipt=r;
        if(!exact)failure="actual cold force-only native operator changed propulsion or sampled force: "+LABELS[s.index];
    }

    private static void client(MinecraftClient c){
        if(done)return;
        if(++ticks>2400||System.nanoTime()-boot>160_000_000_000L){finish("UNOBSERVED","finite native load/admission deadline");return;}
        if(failure!=null){finish("FAIL",failure);return;}
        if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)
            for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen p&&p.getTitle().getString().equals("Warning! Low disk space!"))
            for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Continue"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        DescentClient.markUserFlightChoice();if(viewer==null)viewer=c.player.getUuid();
    }
    private static void server(MinecraftServer server){
        if(done||viewer==null||failure!=null)return;
        try{
            serverTicks++;var p=server.getPlayerManager().getPlayer(viewer);if(p==null)return;ServerWorld w=p.getServerWorld();
            require(WorldAccess.of(w).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual native BCC world, not a column-world substitute");
            if(!staged){
                staged=true;phase="NATIVE_FULL_ADMISSION";FlightSystem.disable(p,DescentPlayerData.get(p));
                p.changeGameMode(GameMode.CREATIVE);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                server.getPlayerManager().setViewDistance(6);
                if(WRITE)server.setDifficulty(Difficulty.NORMAL,true);
                p.teleport(w,OBSERVER.x,OBSERVER.y,OBSERVER.z,Set.of(),0,0);return;
            }
            require(p.isCreative()&&!p.isSpectator()&&p.getAbilities().allowFlying&&p.getAbilities().flying
                &&!DescentPlayerData.get(p).isEnabled()&&!FootGravitySystem.isActive(p),"actual native Creative hover without a player gravity/flight override");
            // At most 32 cubes, no speculative getChunk, tickets or generation calls.
            if(!full(w,new Box(11,2199,11,61,2209,61),64))return;
            if(WRITE&&!built){
                phase="NATIVE_ENTITY_TICK_ADMISSION";boolean ticking=true;
                for(int i=0;i<7;i++)ticking&=w.shouldTickEntity(base(i).add(3,2,3));
                if(serverTicks%5==0&&ROWS.size()<280)ROWS.add(Map.of("phase",phase,"allSevenNativeEntityTicking",ticking,"serverTick",serverTicks));
                if(!ticking)return;
                build(w);built=true;phase="NATURAL_FIELD_CONTACT";return;
            }
            boolean all=SLOTS.size()==7;
            for(int i=0;i<7;i++){
                Slot s=slot(i);if(s==null||s.loaded==null){all=false;continue;}
                MobEntity actor=s.loaded;
                var byUuid=w.getEntity(s.uuid);var byId=w.getEntityById(actor.getId());boolean ticking=w.shouldTickEntity(actor.getBlockPos());
                boolean indexed=byUuid==actor&&byId==actor;
                if(serverTicks%5==0&&ROWS.size()<280){var row=basic(actor);row.put("familyId",LABELS[i]);row.put("phase","NATIVE_OBJECT_ADMISSION");
                    row.put("removed",actor.isRemoved());row.put("alive",actor.isAlive());row.put("sameWorld",actor.getWorld()==w);
                    row.put("uuidIndex",byUuid==null?"ABSENT":byUuid==actor?"SAME":"FOREIGN");row.put("idIndex",byId==null?"ABSENT":byId==actor?"SAME":"FOREIGN");
                    row.put("nativeEntityTicking",ticking);row.put("nativeEntityLoadObserved",s.loadReceipt!=null);ROWS.add(row);}
                require(!actor.isRemoved()&&actor.isAlive()&&actor.getWorld()==w
                    &&(byUuid==null||byUuid==actor)&&(byId==null||byId==actor),"original actor removed/dead/foreign-world or a foreign index object: "+LABELS[i]);
                if(!indexed||!ticking||s.loadReceipt==null){all=false;continue;}
                verifyIdentity(s,actor);
                if(!full(w,actor.getBoundingBox().expand(1),64)){all=false;continue;}
                var state=WorldAccess.of(w).readIfLoaded(s.source);if(state.isEmpty()){all=false;continue;}
                require(state.get().isOf(ModWorldBlocks.GRAVITY_TORCH)&&state.get().get(GravityTorchBlock.FACING)==Direction.EAST,"actual saved registered torch and FACING");
                GravityFields.Sample sample=GravityFields.sample(w,actor.getPos());
                boolean source=sample!=null&&sample.strength()>=.05f&&sample.upDir().dotProduct(UP)>=.999999
                    &&GravityFields.all().stream().anyMatch(field->sample.sourceId().equals(field.id())
                        &&w.getRegistryKey().equals(field.worldKey())&&s.source.equals(field.origin()));
                if(source&&s.firstSourceAge<0)s.firstSourceAge=actor.age;
                boolean ready=source&&eligible(s,actor,sample);
                if(source&&s.firstSourceAge>=0&&actor.age-s.firstSourceAge>80&&!s.admitted)
                    throw new IllegalStateException("actual loaded saved source failed cold eligibility within80 actor ticks: "+LABELS[i]);
                if(ready&&actor.age>=8&&s.firstSourceAge>=0&&actor.age-s.firstSourceAge>=8){s.admitted=true;if(s.readyAge<0)s.readyAge=actor.age;}
                all&=s.admitted&&ready;
                if(serverTicks%5==0&&ROWS.size()<280){var row=basic(actor);row.put("familyId",LABELS[i]);row.put("sourcePresent",source);
                    row.put("sourceAge",s.firstSourceAge);row.put("admitted",s.admitted);row.put("source",xyz(s.source));
                    row.put("strength",sample==null?null:sample.strength());row.put("sourceId",sample==null?null:sample.sourceId().toString());
                    row.put("forceCalls",s.forceCalls);row.put("force",s.forceReceipt);row.put("support",support(actor));ROWS.add(row);}
            }
            if(all){
                phase=WRITE?"WRITER_READY_TO_SAVE":"READER_NATIVE_COLD_QUALIFIED";
                if(WRITE){var actors=new ArrayList<Map<String,Object>>();for(int i=0;i<7;i++)actors.add(manifest(slot(i)));
                    write("manifest.json",Map.of("process",PROCESS,"pid",PID,"actors",actors,"setupAiDisabled",true,
                        "noGravityMutations",0,"ordinaryActorTicksOnly",true,"roomVolumeCap",64));
                    finish("READY_TO_SAVE","seven actual torch policies admitted; PASS requires later writer-closed receipt and process exit");
                }else finish("PASS","seven native entities/source policies re-admitted after an actual closed-world process restart");
            }
        }catch(Throwable error){failure=error.toString();finish("FAIL",failure);}
    }

    private static boolean eligible(Slot s,MobEntity a,GravityFields.Sample sample){
        var policy=ActorPhysicsProfile.gravityPolicy(a);boolean owned=EntityGravitySystem.suppressesVanillaGravity(a);
        if(s.index==6)return a instanceof ShulkerEntity shulker&&policy==ActorPhysicsProfile.GravityPolicy.NATIVE_CONSTRAINT
            &&shulker.getAttachedFace()==Direction.DOWN&&!owned&&!ActorFrames.active(a)&&s.forceCalls==0;
        if(!owned||a.hasVehicle())return false;
        if(s.index<=1){var support=support(a);return policy==ActorPhysicsProfile.GravityPolicy.FRAMED_GROUND
            &&ActorFrames.up(a).dotProduct(UP)>=.999999&&Boolean.TRUE.equals(support.get("valid"))
            &&a.getWorld().isSpaceEmpty(a,a.getBoundingBox().contract(1e-6));}
        // Rare C6 eligibility is concrete, not a NoGravity shortcut or a global boss claim.
        if(s.index==5&&(!(a instanceof WitherEntity)||policy!=ActorPhysicsProfile.GravityPolicy.FORCE_ONLY||!a.isAlive()))return false;
        if(s.index==3&&(!a.isTouchingWater()||!a.isSubmergedInWater()))return false;
        return policy==ActorPhysicsProfile.GravityPolicy.FORCE_ONLY&&!ActorFrames.active(a)
            &&s.forceCalls>0&&s.forceReceipt!=null&&Boolean.TRUE.equals(s.forceReceipt.get("forceOnly"))
            &&Boolean.TRUE.equals(s.forceReceipt.get("forceDeltaExact"))&&sample.strength()>=.05f;
    }
    private static Map<String,Object> support(MobEntity a){
        if(!ActorFrames.active(a))return Map.of("valid",false,"reason","native-unframed");
        Vec3d up=ActorFrames.up(a),from=a.getPos().add(up.multiply(.08)),to=from.subtract(up.multiply(.2));
        if(!(a.getWorld() instanceof ServerWorld w)||!full(w,new Box(from,to).expand(1),64))return Map.of("valid",false,"reason","UNKNOWN");
        var hit=w.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,a));
        if(hit.getType()!=HitResult.Type.BLOCK)return Map.of("valid",false,"reason","MISS");
        double gap=a.getPos().subtract(hit.getPos()).dotProduct(up),normal=Vec3d.of(hit.getSide().getVector()).dotProduct(up);
        return Map.of("valid",gap>=-1e-6&&gap<=.08&&normal>=.95,"gap",gap,"normalDot",normal,"block",xyz(hit.getBlockPos()));
    }
    private static boolean full(ServerWorld w,Box box,int cap){
        int x0=(int)Math.floor(box.minX)>>4,x1=(int)Math.floor(box.maxX)>>4;
        int y0=(int)Math.floor(box.minY)>>4,y1=(int)Math.floor(box.maxY)>>4;
        int z0=(int)Math.floor(box.minZ)>>4,z1=(int)Math.floor(box.maxZ)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>cap)return false;
        var access=WorldAccess.of(w);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static Slot slot(int i){return SLOTS.values().stream().filter(s->s.index==i).findFirst().orElse(null);}
    private static void build(ServerWorld w){
        require(SLOTS.isEmpty(),"writer starts with no owned fixture UUIDs");
        for(int i=0;i<7;i++){
            BlockPos b=base(i);
            for(BlockPos p:BlockPos.iterate(b,b.add(7,7,7))){
                int x=p.getX()-b.getX(),y=p.getY()-b.getY(),z=p.getZ()-b.getZ();
                boolean shell=x==0||x==7||y==0||y==7||z==0||z==7;
                w.setBlockState(p,shell?Blocks.OBSIDIAN.getDefaultState():i==3?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState(),2);
            }
            w.setBlockState(b.add(1,4,4),ModWorldBlocks.GRAVITY_TORCH.getDefaultState().with(GravityTorchBlock.FACING,Direction.EAST),3);
            EntityType<?> type=switch(i){case 0->EntityType.COW;case 1->EntityType.SPIDER;case 2->EntityType.BEE;
                case 3->EntityType.COD;case 4->ModEntities.ORB;case 5->EntityType.WITHER;default->EntityType.SHULKER;};
            Entity e=type.create(w);require(e instanceof MobEntity,"actual registered family entity");MobEntity a=(MobEntity)e;
            // Setup-only AI isolation is persisted and reported; never override NoGravity.
            a.setAiDisabled(true);a.setPersistent();
            // The ordinary spider is wider than one block. Its native AABB starts
            // just outside the wall, without modifying walls or forcing a local frame.
            double wallX = b.getX() + (i == 1 ? 1.02 + a.getWidth() * .5 : 1.5);
            a.setPosition(i<=1?new Vec3d(wallX,b.getY()+2,b.getZ()+2.5):
                new Vec3d(b.getX()+3.5,b.getY()+(i==6?1:2),b.getZ()+3.5));
            require(w.isSpaceEmpty(a,a.getBoundingBox().contract(1e-6)),"initial native body clear without a forced local frame: "+LABELS[i]);
            Slot s=new Slot(i,a);s.loaded=a;SLOTS.put(s.uuid,s);
            require(w.spawnEntity(a),"normal native family spawn accepted");
        }
    }
    private static void verifyIdentity(Slot s,MobEntity a){
        require(a.getClass().getName().equals(s.javaClass)&&Registries.ENTITY_TYPE.getId(a.getType()).toString().equals(s.type)
            &&a.getUuid().equals(s.uuid)&&a.getWorld().getRegistryKey().getValue().toString().equals(s.worldKey),"saved world/species/class/UUID identity: "+LABELS[s.index]);
        require(a.getNavigation().getClass().getName().equals(s.navigation)&&a.getMoveControl().getClass().getName().equals(s.control),"native controller family retained");
        require(a.hasNoGravity()==s.noGravity&&a.isAiDisabled()&&a.isPersistent(),"saved actual flag owners retained without observer override");
        require(ActorPhysicsProfile.family(a)==FAMILIES[s.index],"actual runtime C-family classification");
    }
    private static Map<String,Object> basic(MobEntity a){
        var r=new LinkedHashMap<String,Object>();r.put("uuid",a.getUuid().toString());r.put("id",a.getId());r.put("type",Registries.ENTITY_TYPE.getId(a.getType()).toString());
        r.put("javaClass",a.getClass().getName());r.put("age",a.age);r.put("position",xyz(a.getPos()));r.put("velocity",xyz(a.getVelocity()));
        r.put("up",xyz(ActorFrames.up(a)));r.put("noGravity",a.hasNoGravity());r.put("lease",EntityGravitySystem.suppressesVanillaGravity(a));
        r.put("worldKey",a.getWorld().getRegistryKey().getValue().toString());
        r.put("aiDisabled",a.isAiDisabled());r.put("persistent",a.isPersistent());r.put("navigation",a.getNavigation().getClass().getName());
        r.put("control",a.getMoveControl().getClass().getName());r.put("policy",ActorPhysicsProfile.gravityPolicy(a).name());
        r.put("thread",Thread.currentThread().getName());r.put("touchingWater",a.isTouchingWater());r.put("submerged",a.isSubmergedInWater());
        if(a instanceof ShulkerEntity shulker)r.put("attachment",shulker.getAttachedFace().name());return r;
    }
    private static Map<String,Object> manifest(Slot s){var r=new LinkedHashMap<String,Object>();
        r.put("index",s.index);r.put("familyId",LABELS[s.index]);r.put("uuid",s.uuid.toString());r.put("type",s.type);r.put("javaClass",s.javaClass);
        r.put("navigation",s.navigation);r.put("control",s.control);r.put("noGravity",s.noGravity);r.put("source",xyz(s.source));r.put("base",xyz(s.base));
        r.put("worldKey",s.worldKey);
        r.put("admittedAge",s.readyAge);return r;}
    private static synchronized void flushLifecycle()throws Exception{
        var rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<7;i++){Slot s=slot(i);if(s==null)continue;var r=manifest(s);r.put("nativeDecode",s.decodeReceipt);r.put("nativeEntityLoad",s.loadReceipt);rows.add(r);}
        write("reader-load-events.json",Map.of("readerProcess",PROCESS,"readerPid",PID,"writerProcess",writerProcess,"writerPid",writerPid,"actors",rows));
    }
    private static void finish(String status,String why){
        if(done)return;done=true;
        try{
            var actors=new ArrayList<Map<String,Object>>();for(int i=0;i<7;i++){Slot s=slot(i);if(s==null)continue;
                var r=manifest(s);r.put("decode",s.decodeReceipt);r.put("load",s.loadReceipt);r.put("force",s.forceReceipt);r.put("forceCalls",s.forceCalls);actors.add(r);}
            var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("reason",why);out.put("phase",phase);
            out.put("mode",WRITE?"WRITE":"READ");out.put("process",PROCESS);out.put("writerProcess",writerProcess);
            out.put("pid",PID);out.put("writerPid",writerPid);
            out.put("actors",actors);out.put("rows",ROWS);out.put("clientTicks",ticks);out.put("serverTicks",serverTicks);
            out.put("noGravityMutations",0);out.put("setupAiDisabled",true);out.put("manualActorTicks",0);out.put("forcedActorFrame",false);
            out.put("sourceBinding",System.getProperty("drmd.gate.mobTorchColdSource","UNBOUND"));
            out.put("observerMode","native Creative hover outside all8m source fields; not Spectator");
            out.put("observerPosition",xyz(OBSERVER));out.put("nativeEntityTickAdmissionRequired",true);out.put("nativeEntityLoadRequired",true);
            out.put("scope","seven explicit cold physics policies; autonomous AI, arbitrary species and full6DoF remain open");
            write(WRITE?"writer-ready.json":"reader-result.json",out);
        }catch(Exception error){error.printStackTrace();}
        System.out.println("MOB_TORCH_COLD_OUTCOME "+status+" "+why);MinecraftClient.getInstance().scheduleStop();
    }
}
