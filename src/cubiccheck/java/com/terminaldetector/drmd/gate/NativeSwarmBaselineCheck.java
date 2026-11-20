package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.*;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Difficulty;
import java.nio.file.*;
import java.util.*;

/** Inherited 128/96 load baseline at ordinary and cubic altitude. Not Survival acceptance. */
public final class NativeSwarmBaselineCheck {
    public static final boolean TYPED=Boolean.getBoolean("drmd.gate.typedSwarm");
    private static final Map<String,Integer> TYPED_SHOTS=new TreeMap<>();
    private static Map<String,Object> acceptedTyped=Map.of();
    private static final int CAPACITY = Integer.getInteger("drmd.gate.swarmCapacity", 0);
    private static final boolean MEASURE_CAPACITY = CAPACITY != 0;
    private static final boolean TRACE_DRAWS = MEASURE_CAPACITY && Boolean.getBoolean("drmd.gate.traceSwarmDraws");
    private static boolean traceOpened, traceClosed;
    private static final int COUNT = MEASURE_CAPACITY ? CAPACITY : 128;
    private static final int MINIMUM_MOVING = MEASURE_CAPACITY ? Math.min(96, COUNT * 3 / 4) : 96;
    private static final List<FlightEnemyKind> LEGACY_TYPED_KINDS=List.of(FlightEnemyKind.ORB,FlightEnemyKind.ROCKET,FlightEnemyKind.JET);
    private static final List<DroneEntity> DRONES=new ArrayList<>();
    private static final List<Entity> PROJECTILES=new ArrayList<>();
    private static final Set<UUID> SHOOTERS=new HashSet<>(),DRAWN=new HashSet<>();
    private static volatile Set<UUID> expected=Set.of();
    private static final Map<UUID,Vec3d> initial=new HashMap<>();
    private static final Map<UUID,Integer> initialAge=new HashMap<>();
    private static final List<Long> WORK=new ArrayList<>(),FRAMES=new ArrayList<>();
    private static final List<Long> HEAP = new ArrayList<>();
    private static final List<com.terminaldetector.drmd.weapon.projectile.ProjectileTracerBudget.Sample> TRACERS=new ArrayList<>();
    private static long initialDetailed, initialCoast, detailed, coast;
    private static int measuredFullCubes;
    private static long initialRosterScans;
    private static DroneSwarmAi.IndexStats finalRosterStats;
    private static boolean capacityAdmitted;
    private static int capacityAdmissionTicks;
    private static final Map<UUID,Integer> capacityAdmissionAges=new HashMap<>();
    private static Map<String,Object> capacityAdmission=Map.of();
    private static final List<String> ROWS=new ArrayList<>();
    private static volatile boolean located,running,sampling,resultReady,done;
    private static volatile boolean abortPending;
    private static boolean requested,spawnRequested;private static volatile int stage,age;
    private static final boolean VERTICAL=Boolean.getBoolean("drmd.gate.verticalTargets");
    private static int ticks,frames,alive,moving,active,lookup,maxCandidates,shots,sampledShots,sampledShooters,indexed,targeting;
    private static long tickStart,sampleStart,lastFrame;private static double seconds;
    private static long captureStart;
    private static SwarmMeasurement profileWindow;
    @jdk.jfr.Name("drmd.SwarmMeasurement")
    @jdk.jfr.Label("Real swarm measurement")
    @jdk.jfr.Category({"DRMD","Swarm"})
    private static final class SwarmMeasurement extends jdk.jfr.Event {
        public int requested;
        public int serverStartTick,serverEndTick;
    }
    private static Entity cow;
    private NativeSwarmBaselineCheck(){}
    private static Entity entity(DroneEntity d){return d;}
    private static Vec3d centre(){return new Vec3d(12.5,stage==0&&!MEASURE_CAPACITY?207:2156,20.5);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    public static void register() {
        require(!TYPED||VERTICAL,"typed fixture uses autonomous vertical target acquisition");
        require(!MEASURE_CAPACITY || TYPED && !NativeDroneStateCheck.WRITE
            && Set.of(32, 128, 256, 512, 1024).contains(COUNT), "supported type-aware capacity without save fixture");
        ROWS.add("height,requested,alive,active_ticks,moving,server_lookup,client_synced,unique_rendered,shots,shooters,indexed,targeting,max_candidates,server_p50_ms,server_p95_ms,server_p99_ms,observed_tps,frames,frame_p95_ms,cleanup_remaining");
        ServerTickEvents.START_SERVER_TICK.register(s->{if(running)tickStart=System.nanoTime();});
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!running||resultReady||done)return;
            if(MEASURE_CAPACITY&&!capacityAdmitted&&!admitCapacity(s.getOverworld()))return;
            age++;
            if(age==35){initial.clear();initialAge.clear();for(var d:DRONES){initial.put(entity(d).getUuid(),entity(d).getPos());initialAge.put(entity(d).getUuid(),entity(d).age);}WORK.clear();sampleStart=System.nanoTime();sampling=true;
                initialDetailed = DRONES.stream().mapToLong(DroneEntity::steeringUpdates).sum();
                initialCoast = DRONES.stream().mapToLong(DroneEntity::coastingUpdates).sum();
                if (MEASURE_CAPACITY) initialRosterScans = DroneSwarmAi.indexStats(s.getOverworld()).fullWorldScans();
                if(MEASURE_CAPACITY&&Boolean.getBoolean("drmd.gate.profileSwarmJfr")){
                    profileWindow=new SwarmMeasurement();profileWindow.requested=COUNT;
                    profileWindow.serverStartTick=s.getTicks();profileWindow.begin();
                }
            }
            if(sampling&&age>35)WORK.add(System.nanoTime()-tickStart);
            if(age!=95)return;
            if(profileWindow!=null){profileWindow.serverEndTick=s.getTicks();profileWindow.end();profileWindow.commit();profileWindow=null;}
            sampling=false;seconds=(System.nanoTime()-sampleStart)/1e9;alive=moving=active=lookup=maxCandidates=targeting=0;indexed=Integer.MAX_VALUE;
            for(var d:DRONES)if(entity(d).isAlive()&&!entity(d).isRemoved()) {
                alive++;var id=entity(d).getUuid();if(entity(d).getPos().squaredDistanceTo(initial.get(id))>.04)moving++;
                if(entity(d).age-initialAge.get(id)>=20)active++;
                if(s.getOverworld().getEntity(id)==d)lookup++;
                var stats=DroneSwarmAi.queryStats(d);maxCandidates=Math.max(maxCandidates,stats.examinedCandidates());indexed=Math.min(indexed,stats.indexedDrones());
                var target=((MobEntity)d).getTarget();if(NativeDroneStateCheck.WRITE?target!=null&&HostileEnvironment.isHostileTarget(d,target):target==cow)targeting++;
            }
            sampledShots=shots;sampledShooters=SHOOTERS.size();
            if (MEASURE_CAPACITY) {
                finalRosterStats = DroneSwarmAi.indexStats(s.getOverworld());
                detailed = DRONES.stream().mapToLong(DroneEntity::steeringUpdates).sum() - initialDetailed;
                coast = DRONES.stream().mapToLong(DroneEntity::coastingUpdates).sum() - initialCoast;
                var centreCube = BlockPos.ofFloored(centre());
                WorldAccess access = WorldAccess.of(s.getOverworld());
                measuredFullCubes = 0;
                for (int x = -3; x <= 3; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                    if (access.isVolumeAvailable(new D6ChunkPos((centreCube.getX() >> 4) + x,
                            (centreCube.getY() >> 4) + y, (centreCube.getZ() >> 4) + z))) measuredFullCubes++;
                }
            }
            require(cow!=null&&cow.isAlive(),"the inherited invulnerable target must remain available throughout sampling");
            System.out.println("SWARM_BASELINE height="+centre().y+" alive="+alive+" active="+active+" moving="+moving+" shots="+sampledShots+" shooters="+sampledShooters+" lookup="+lookup);
            require(alive==COUNT&&active==COUNT&&moving>=MINIMUM_MOVING,"swarm must actually tick and move: alive="+alive+" active="+active+" moving="+moving);
            require(maxCandidates<=DroneSwarmAi.MAX_BOID_CANDIDATES,"bounded swarm neighbour work");
            require(indexed>=COUNT&&targeting>=MINIMUM_MOVING,"live drones must remain indexed and engaged: indexed="+indexed+" targeting="+targeting);
            require(sampledShots>0&&sampledShooters>=Math.min(16,COUNT/4),"AI must spawn real shots from multiple agents");
            if(TYPED && !MEASURE_CAPACITY){
                var kinds=new TreeMap<String,Map<String,Integer>>();
                for(var kind:LEGACY_TYPED_KINDS){
                    int count=0,live=0,moved=0,targets=0;
                    for(var d:DRONES){require(d instanceof FlightEnemyEntity,"independent flight class in typed swarm");var typed=(FlightEnemyEntity)d;if(typed.kind()!=kind)continue;
                        require(d.getRole()==kind.defaultRole,"authored role retained by typed swarm");count++;
                        if(entity(d).isAlive()&&entity(d).age-initialAge.get(entity(d).getUuid())>=20)live++;
                        if(entity(d).getPos().squaredDistanceTo(initial.get(entity(d).getUuid()))>.04)moved++;
                        var target=((MobEntity)d).getTarget();if(target!=null&&HostileEnvironment.isHostileTarget(d,target))targets++;
                    }
                    int wanted=kind==FlightEnemyKind.JET?42:43,fire=TYPED_SHOTS.getOrDefault(kind.id,0);
                    require(count==wanted&&live==wanted&&moved>=20&&targets>=20&&fire>0,"each real flight kind participates: "+kind+" count="+count+" active="+live+" moving="+moved+" targets="+targets+" shots="+fire);
                    kinds.put(kind.id,Map.of("count",count,"active",live,"moving",moved,"naturalTargets",targets,"shots",fire));
                }
                acceptedTyped=Map.of("kinds",Map.copyOf(kinds),"active",active,"moving",moving,"naturalTargets",targeting,"maxCandidates",maxCandidates,"noManualEntityTicks",true);
            }
            resultReady=true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeSwarmBaselineCheck::tick);
    }
    private static boolean loaded(MinecraftClient c) {
        var at=BlockPos.ofFloored(centre());var access=WorldAccess.of(c.world);
        int span = MEASURE_CAPACITY ? 3 : 2;
        for(int x=-span;x<=span;x++)for(int y=-2;y<=2;y++)for(int z=-3;z<=span;z++)if(!access.isVolumeAvailable(new D6ChunkPos((at.getX()>>4)+x,(at.getY()>>4)+y,(at.getZ()>>4)+z)))return false;
        return true;
    }
    private static void tick(MinecraftClient c) {
        if(done)return;require(++ticks<4800,"native 128-drone baseline timeout stage="+stage+" age="+age);
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())
            if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!requested) {
            require(c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"),"disposable runtime only");requested=true;c.setScreen(null);c.options.hudHidden=true;c.options.setPerspective(Perspective.FIRST_PERSON);
            if(TYPED){c.options.getViewDistance().setValue(8);if(MEASURE_CAPACITY)c.options.getSimulationDistance().setValue(8);c.options.sendClientSettings();}
            com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var d=DescentPlayerData.get(p);var at=centre();
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,d);
                if(TYPED)c.getServer().getPlayerManager().setViewDistance(8);
                if(MEASURE_CAPACITY)c.getServer().getPlayerManager().setSimulationDistance(8);
                p.teleport(p.getServerWorld(),at.x,at.y+(MEASURE_CAPACITY?22:TYPED?24:12),at.z+(MEASURE_CAPACITY?58:TYPED?48:6),Set.of(),180,MEASURE_CAPACITY?17:TYPED?26:40);p.setInvulnerable(true);
                d.setGravity(0);d.setDrag(0);d.setFlightVelocity(Vec3d.ZERO);com.terminaldetector.drmd.flight.FlightSystem.enableWithoutMount(p,d);
                c.getServer().setDifficulty(Difficulty.NORMAL,true);p.getServerWorld().setTimeOfDay(6000);located=true;
            });return;
        }
        if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;DRAWN.clear();FRAMES.clear();HEAP.clear();TRACERS.clear();lastFrame=0;c.getServer().execute(()->spawn(c.getServer().getOverworld()));}
    }
    private static void spawn(ServerWorld w) {
        System.out.println("SWARM_CONFIG seed="+w.getSeed()+" centre="+centre()+" difficulty="+w.getDifficulty()+" doMobSpawning="+w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_SPAWNING)+" roles="+(TYPED?"ORB,ROCKET,JET":"SUPPORT,ASSAULT,MG,INTERCEPTOR")+" target="+((NativeDroneStateCheck.WRITE||VERTICAL)?"targetable-resistance-cow":"invulnerable-invisible-cow")+" verticalTargets="+VERTICAL+" manualTargets="+!VERTICAL+" backgroundLOD=false manualDroneTicks=false");
        var at=centre();var target=EntityType.COW.create(w);require(target!=null,"real passive target");target.setPosition(at.add(0,VERTICAL?(stage==0?20:-20):0,VERTICAL?0:-30));target.setNoGravity(true);target.setInvulnerable(!(NativeDroneStateCheck.WRITE||VERTICAL));if(NativeDroneStateCheck.WRITE||VERTICAL)target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE,-1,4,false,false));target.setInvisible(!(NativeDroneStateCheck.WRITE||VERTICAL));target.setAiDisabled(true);target.setPersistent();require(w.spawnEntity(target),"target spawned");cow=target;
        DRONES.clear();PROJECTILES.clear();SHOOTERS.clear();TYPED_SHOTS.clear();shots=0;
        for(int i=0;i<COUNT;i++) {
            var d=TYPED?LEGACY_TYPED_KINDS.get(i%3).create(w):ModEntities.DRONE.create(w);require(d!=null,"registered drone created");
            entity(d).setPosition(MEASURE_CAPACITY ? at.add(((i & 15) - 7.5) * 3,
                (((i >> 8) & 3) - 1.5) * 4, (((i >> 4) & 15) - 7.5) * 3) : TYPED?at.add(((i&7)-3.5)*3,(((i>>3)&3)-1.5)*3,(((i>>5)&3)-1.5)*3):at.add(((i&15)-7.5)*1.2,(((i>>8)&3)-1.5)*1.2,(((i>>4)&15)-7.5)*1.2));
            if(!TYPED)d.applyRole(switch(i&3){case 0->AiRole.SUPPORT;case 1->AiRole.ASSAULT;case 2->AiRole.MG;default->AiRole.INTERCEPTOR;});
            ((MobEntity)d).setPersistent();entity(d).setInvulnerable(true);require(w.spawnEntity(d),"drone spawn accepted");if(!VERTICAL)((MobEntity)d).setTarget(target);DRONES.add(d);
        }
        expected=Set.copyOf(DRONES.stream().map(d->entity(d).getUuid()).toList());DroneSwarmAi.clear();age=0;captureStart=0;resultReady=false;
        capacityAdmitted=false;capacityAdmissionTicks=0;capacityAdmissionAges.clear();capacityAdmission=Map.of();running=true;
    }
    /** FULL client cubes are not a vanilla entity-ticking lease. Admit the real
     * native8/8 player simulation and two ordinary actor ticks before warmup;
     * never call tick(), setTarget(), or advance the measurement clock here. */
    private static boolean admitCapacity(ServerWorld world){
        int ticking=0,lookups=0,advanced=0;boolean targetReady=cow!=null&&cow.isAlive()&&!cow.isRemoved()
            &&world.getEntity(cow.getUuid())==cow&&world.shouldTickEntity(cow.getBlockPos());
        for(var drone:DRONES){
            Entity nativeEntity=entity(drone);
            if(world.shouldTickEntity(nativeEntity.getBlockPos()))ticking++;
            if(world.getEntity(nativeEntity.getUuid())==nativeEntity)lookups++;
            Integer before=capacityAdmissionAges.get(nativeEntity.getUuid());if(before!=null&&nativeEntity.age-before>=2)advanced++;
        }
        capacityAdmissionTicks++;
        var row=new LinkedHashMap<String,Object>();row.put("waitServerTicks",capacityAdmissionTicks);row.put("requested",COUNT);
        row.put("entityTicking",ticking);row.put("serverLookup",lookups);row.put("advancedTwoOrdinaryTicks",advanced);row.put("targetTicking",targetReady);
        row.put("serverViewDistance",world.getServer().getPlayerManager().getViewDistance());
        row.put("serverSimulationDistance",world.getServer().getPlayerManager().getSimulationDistance());
        capacityAdmission=Map.copyOf(row);
        if(capacityAdmissionTicks==1||capacityAdmissionTicks%20==0)System.out.println("SWARM_CAPACITY_ADMISSION "+new com.google.gson.Gson().toJson(row));
        require(capacityAdmissionTicks<200,"native8/8 ordinary entity-ticking premise was not observed: "+row);
        if(ticking!=COUNT||lookups!=COUNT||!targetReady){capacityAdmissionAges.clear();return false;}
        if(capacityAdmissionAges.isEmpty()){for(var drone:DRONES)capacityAdmissionAges.put(entity(drone).getUuid(),entity(drone).age);return false;}
        if(advanced!=COUNT)return false;
        capacityAdmitted=true;System.out.println("SWARM_CAPACITY_ADMITTED "+new com.google.gson.Gson().toJson(row));return true;
    }
    public static void shot(WeaponCore.FireConfig config,ProjectileEntity shot) {
        if(!running||done||shot==null||config.owner==null||!expected.contains(config.owner.getUuid()))return;
        PROJECTILES.add(shot);if(!resultReady){shots++;SHOOTERS.add(config.owner.getUuid());if(TYPED){require(config.owner instanceof FlightEnemyEntity,"typed real shooter");var d=(FlightEnemyEntity)config.owner;require(LEGACY_TYPED_KINDS.contains(d.kind()),"legacy typed swarm refuses unrelated new family");if(d.kind()==FlightEnemyKind.ROCKET)require(shot.getMeshKind()==ProjectileEntity.MESH_ROCKET,"actual rocket projectiles");TYPED_SHOTS.merge(d.kind().id,1,Integer::sum);}}
    }
    public static void drew(DroneEntity d){if(expected.contains(entity(d).getUuid()))DRAWN.add(entity(d).getUuid());}
    public static void rendered() {
        if(abortPending)throw new IllegalStateException("Deliberate typed-swarm renderer fault for shutdown recovery proof");
        if(done)return;
        boolean sampleThisRender = sampling;
        if (TRACE_DRAWS && (sampleThisRender || traceOpened && !traceClosed)) {
            require(org.lwjgl.opengl.GL.getCapabilities().GL_KHR_debug,
                "The marked benchmark requires native GL_KHR_debug");
            if (sampleThisRender) {
                // This callback is the return of the real GameRenderer.render. Some
                // GLFW builds resolve swap outside the tracing GL dispatch table.
                traceMarker(traceOpened ? "FRAME" : "BEGIN");
                traceOpened = true;
            } else if (resultReady && traceOpened && !traceClosed) {
                traceMarker("END");
                traceClosed = true;
            }
        }
        long now=System.nanoTime();if(sampleThisRender){if(lastFrame!=0)FRAMES.add(now-lastFrame);lastFrame=now;
            if (MEASURE_CAPACITY) {
                var vm = Runtime.getRuntime(); HEAP.add(vm.totalMemory() - vm.freeMemory());
                var dust = com.terminaldetector.drmd.weapon.projectile.ProjectileTracerBudget.CLIENT.sample();
                if(TRACERS.isEmpty()||TRACERS.getLast().tick()!=dust.tick())TRACERS.add(dust);
            }
        }else lastFrame=0;
        if(!resultReady)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||++frames<3)return;
        int synced=c.world.getEntitiesByClass(DroneEntity.class,new Box(centre().add(-128,-128,-128),centre().add(128,128,128)),d->expected.contains(entity(d).getUuid())).size();
        var visibleKinds=new HashSet<FlightEnemyKind>();
        var clientKinds=new TreeMap<String,Integer>();var renderedKinds=new TreeMap<String,Integer>();var clientClasses=new TreeMap<String,String>();
        if(TYPED)for(var e:c.world.getEntities())if(e instanceof FlightEnemyEntity d&&expected.contains(entity(d).getUuid())){
            clientKinds.merge(d.kind().id,1,Integer::sum);clientClasses.put(d.kind().id,d.getClass().getName());
            if(DRAWN.contains(entity(d).getUuid())){visibleKinds.add(d.kind());renderedKinds.merge(d.kind().id,1,Integer::sum);}
        }
        if(captureStart==0){captureStart=now;System.out.println("SWARM_CAMERA pos="+c.player.getPos()+" yaw="+c.player.getYaw()+" pitch="+c.player.getPitch()+" camera="+c.gameRenderer.getCamera().getRotation());}
        int requiredDrawn = MEASURE_CAPACITY ? Math.min(32, COUNT/2) : 32;
        if((synced!=COUNT||DRAWN.size()<requiredDrawn||(TYPED&&visibleKinds.size()!=3))&&now-captureStart<8_000_000_000L)return;
        require(FRAMES.size()>=2&&!WORK.isEmpty(),"real timing samples");
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            NativeScheduleBudgetCheck.write("height-"+(int)centre().y,true);
            image.writeTo(c.runDirectory.toPath().resolve("swarm-"+COUNT+"-height-"+(int)centre().y+".png"));
            require(synced==COUNT&&DRAWN.size()>=requiredDrawn,"all live actors must synchronize and models must render: synced="+synced+" drawn="+DRAWN.size());
            if(MEASURE_CAPACITY)require(visibleKinds.containsAll(LEGACY_TYPED_KINDS)&&visibleKinds.size()==3,"actual ORB/ROCKET/JET renderers all observed; "+renderedKinds+" classes="+clientClasses);
            if(TYPED && !MEASURE_CAPACITY){
                var counts=new TreeMap<String,Integer>();var drawn=new TreeMap<String,Integer>();
                for(var e:c.world.getEntities())if(e instanceof FlightEnemyEntity d&&expected.contains(entity(d).getUuid())){require(d.getRole()==d.kind().defaultRole,"typed client default role");counts.merge(d.kind().id,1,Integer::sum);if(DRAWN.contains(entity(d).getUuid()))drawn.merge(d.kind().id,1,Integer::sum);}
                require(counts.equals(Map.of("orb",43,"rocket",43,"jet",42))&&drawn.size()==3,"all independent client types and three real renderers");
                Files.writeString(c.runDirectory.toPath().resolve("typed-swarm-height-"+(int)centre().y+".json"),new com.google.gson.Gson().toJson(Map.of("server",acceptedTyped,"clientKinds",counts,"renderedKinds",drawn))+"\n");
            }
            String row=String.format(Locale.ROOT,"%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.3f,%d,%.4f,",(int)centre().y,COUNT,alive,active,moving,lookup,synced,DRAWN.size(),sampledShots,sampledShooters,indexed,targeting,maxCandidates,percentile(WORK,.5)/1e6,percentile(WORK,.95)/1e6,percentile(WORK,.99)/1e6,WORK.size()/seconds,FRAMES.size(),percentile(FRAMES,.95)/1e6);
            if (MEASURE_CAPACITY) {
                var values=new LinkedHashMap<String,Object>();
                values.put("requested",COUNT);values.put("live",alive);values.put("active",active);
                values.put("moving",moving);values.put("synced",synced);values.put("uniqueRendered",DRAWN.size());
                values.put("detailedSteeringUpdates",detailed);values.put("coastingUpdates",coast);values.put("shots",sampledShots);
                values.put("liveRoster",finalRosterStats);
                values.put("ordinaryNativeAdmission",capacityAdmission);
                values.put("clientKinds",clientKinds);values.put("renderedKinds",renderedKinds);values.put("actualClientClasses",clientClasses);
                values.put("fullWorldScansDuringMeasurement",finalRosterStats.fullWorldScans()-initialRosterScans);
                values.put("loadedCubeWindow",Map.of("size",List.of(7,9,9),"full",measuredFullCubes));
                values.put("serverTickNanos",WORK);values.put("frameNanos",FRAMES);values.put("javaHeapBytes",HEAP);
                values.put("cosmeticProjectileDust",TRACERS);
                values.put("maxCosmeticDustPerClientTick",com.terminaldetector.drmd.weapon.projectile.ProjectileTracerBudget.MAX_PARTICLES_PER_TICK);
                values.put("seconds",seconds);values.put("drawCalls",null);
                values.put("drawCallsReason","Entity model submissions and GL driver draw calls are different measurements");
                Files.writeString(c.runDirectory.toPath().resolve("swarm-capacity-"+COUNT+".json"),
                    new com.google.gson.GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(values)+"\n");
            }
            if(NativeDroneStateCheck.WRITE&&stage==1) {
                running=false;sampling=false;done=true;ROWS.add(row+128);
                Files.writeString(c.runDirectory.toPath().resolve("swarm-before-restart.csv"),String.join("\n",ROWS)+"\n");
                c.getServer().execute(()->{
                    PROJECTILES.forEach(Entity::discard);
                    for(int i=0;i<DRONES.size();i++){var d=DRONES.get(i);((LivingEntity)d).setHealth(((LivingEntity)d).getMaxHealth()*.63f);d.setFlightRoll((i&1)==0?25:-25);}
                    NativeDroneStateCheck.watch(expected);
                    if(Boolean.getBoolean("drmd.gate.abortAfterSwarm")){
                        NativeScheduleBudgetCheck.write("abort-before-stop",true);
                        c.execute(()->{try{Files.writeString(c.runDirectory.toPath().resolve("deliberate-swarm-abort.json"),"{\"deliberateRendererFault\":true,\"watchedSavedIdentities\":128}\n");abortPending=true;}catch(Exception e){throw new IllegalStateException(e);}});
                    }else c.execute(c::scheduleStop);
                });return;
            }
            resultReady=false;running=false;frames=0;c.getServer().execute(()->cleanup(c,row));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void cleanup(MinecraftClient c,String row) {
        DRONES.forEach(d->entity(d).discard());PROJECTILES.forEach(Entity::discard);if(cow!=null)cow.discard();
        long remaining=DRONES.stream().filter(d->!entity(d).isRemoved()).count();require(remaining==0,"fixture actors removed");ROWS.add(row+remaining);DRONES.clear();PROJECTILES.clear();expected=Set.of();DroneSwarmAi.clear();
        c.execute(()->{
            if(++stage<2 && !MEASURE_CAPACITY){located=false;requested=false;spawnRequested=false;return;}
            try{Files.writeString(c.runDirectory.toPath().resolve("swarm-baseline.csv"),String.join("\n",ROWS)+"\n");
                if (MEASURE_CAPACITY) {done=true;c.scheduleStop();return;}
                Files.writeString(c.runDirectory.toPath().resolve("swarm-baseline-ok.json"),"{\"naturalVerticalTargets\":"+VERTICAL+",\"heights\":[207,2156],\"actorsPerCase\":128,\"minimumMoving\":96,\"realTicksAndShots\":true,\"fullSurvivalOrPersistenceAcceptance\":false}\n");done=true;c.scheduleStop();}catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    private static void traceMarker(String boundary) {
        org.lwjgl.opengl.KHRDebug.glDebugMessageInsert(org.lwjgl.opengl.KHRDebug.GL_DEBUG_SOURCE_APPLICATION,
            org.lwjgl.opengl.KHRDebug.GL_DEBUG_TYPE_MARKER, COUNT, org.lwjgl.opengl.KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION,
            "DRMD_SWARM_" + boundary + "=" + COUNT);
    }
    private static double percentile(List<Long> values,double fraction){var sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(Math.max(0,(int)Math.ceil(fraction*sorted.size())-1));}
}
