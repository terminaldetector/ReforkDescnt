package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual world restart, state validation before first tick, then autonomous reacquisition. */
public final class NativeDroneStateCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.droneStateWrite"),READ=Boolean.getBoolean("drmd.gate.droneStateRead");
    private static volatile Set<UUID> ids=Set.of();
    private static final Map<UUID,NbtCompound> SAVED=new ConcurrentHashMap<>();
    private static final Map<UUID,DroneEntity> LOADED=new ConcurrentHashMap<>();
    private static final Map<UUID,String> ROLES=new ConcurrentHashMap<>();
    private static final Map<UUID,String> TYPES=new ConcurrentHashMap<>();
    private static final Map<UUID,Vec3d> INITIAL=new HashMap<>();
    private static final Map<UUID,Integer> INITIAL_AGES=new HashMap<>();
    private static boolean observerReady,viewReady;
    private static long captureStart;
    private static final Set<UUID> DRAWN=new HashSet<>(),SHOOTERS=new HashSet<>();
    private static final Map<String,Integer> DAMAGING_ROLES=new TreeMap<>();
    private static Map<String,Integer> acceptedRoles=Map.of();
    private static volatile boolean ready,done;private static int ticks,age,frames,shots,moving,targets;
    private NativeDroneStateCheck(){}
    private static Entity entity(DroneEntity d){return d;}
    private static Path file(String s){return MinecraftClient.getInstance().runDirectory.toPath().resolve(s);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    public static void watch(Set<UUID> values) {
        require(values.size()==128,"128 persisted identities");ids=Set.copyOf(values);
        try{Files.createDirectories(file("drone-state-nbt"));Files.writeString(file("drone-state-ids.txt"),String.join("\n",ids.stream().map(UUID::toString).sorted().toList())+"\n");}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void saved(DroneEntity d,NbtCompound n) {
        if(!WRITE||entity(d).getWorld().isClient||!ids.contains(entity(d).getUuid()))return;
        try {
            Files.writeString(file("drone-state-nbt/"+entity(d).getUuid()+".snbt"),n.toString());SAVED.put(entity(d).getUuid(),n.copy());
            if(NativeSwarmBaselineCheck.TYPED)TYPES.put(entity(d).getUuid(),net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity(d).getType()).toString());
            if(SAVED.size()==128) {
                long bank=SAVED.values().stream().filter(t->Math.abs(t.getFloat("flightRoll"))>.1).count();
                long cooldown=SAVED.values().stream().filter(t->t.getFloat("attackCooldown")>0).count();
                long damaged=SAVED.values().stream().filter(t->t.getFloat("Health")<AiRole.fromId(t.getString("role")).hullHp).count();
                Files.writeString(file("drone-state-saved.json"),"{\"saved\":128,\"banked\":"+bank+",\"cooling\":"+cooldown+",\"damaged\":"+damaged+"}\n");
                if(NativeSwarmBaselineCheck.TYPED){require(TYPES.size()==128,"128 serialized actual type identities");Files.writeString(file("drone-state-types.json"),new com.google.gson.Gson().toJson(TYPES)+"\n");}
            }
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(DroneEntity d) {
        if(!READ||entity(d).getWorld().isClient||!ids.contains(entity(d).getUuid()))return;
        try {
            var before=StringNbtReader.parse(Files.readString(file("drone-state-nbt/"+entity(d).getUuid()+".snbt")));var actual=entity(d).writeNbt(new NbtCompound());
            if(NativeSwarmBaselineCheck.TYPED)require(d instanceof FlightEnemyEntity&&Objects.equals(TYPES.get(entity(d).getUuid()),net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity(d).getType()).toString())&&d.getRole()==((FlightEnemyEntity)d).kind().defaultRole,"actual typed class/id/loadout before first restored tick");
            for(String key:new String[]{"role","Health","shield","armor","modEnergy","attackCooldown","flightRoll","aiPhase","aiPhaseTime","Motion","Rotation","NoGravity"})
                require(before.contains(key)&&actual.contains(key)&&before.get(key).equals(actual.get(key)),"persisted drone component mismatch: "+key+" uuid="+entity(d).getUuid());
            require(before.getList("Pos",NbtElement.DOUBLE_TYPE).equals(actual.getList("Pos",NbtElement.DOUBLE_TYPE)),"persisted drone world position");
            ROLES.put(entity(d).getUuid(),before.getString("role"));LOADED.put(entity(d).getUuid(),d);
        }catch(Exception e){throw new IllegalStateException("actual drone state restore failed",e);}
    }
    public static void shot(WeaponCore.FireConfig cfg,ProjectileEntity projectile) {
        if(!READ||done||ready||projectile==null||!(cfg.owner instanceof DroneEntity d)||!ids.contains(entity(d).getUuid())||entity(d).getWorld().isClient)return;
        shots++;SHOOTERS.add(entity(d).getUuid());if(cfg.directDamage>0)DAMAGING_ROLES.merge(d.getRole().id,1,Integer::sum);
    }
    public static void register() {
        require(READ&&file("").getFileName().toString().equals("cubic-client-automation"),"disposable reader only");
        try{ids=Set.copyOf(Files.readAllLines(file("drone-state-ids.txt")).stream().filter(s->!s.isBlank()).map(UUID::fromString).toList());require(ids.size()==128,"exact saved set");}catch(Exception e){throw new IllegalStateException(e);}
        if(NativeSwarmBaselineCheck.TYPED)try{var json=com.google.gson.JsonParser.parseString(Files.readString(file("drone-state-types.json"))).getAsJsonObject();json.entrySet().forEach(e->TYPES.put(UUID.fromString(e.getKey()),e.getValue().getAsString()));require(TYPES.keySet().equals(ids),"exact128 saved type map");}catch(Exception e){throw new IllegalStateException(e);}
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!observerReady&&!s.getPlayerManager().getPlayerList().isEmpty()) {
                var p=s.getPlayerManager().getPlayerList().getFirst();
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,com.terminaldetector.drmd.DescentPlayerData.get(p));
                s.getPlayerManager().setViewDistance(8);
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();observerReady=true;
            }
            if(done||ready||LOADED.size()!=128)return;age++;
            if(age==30)for(var d:LOADED.values()){INITIAL.put(entity(d).getUuid(),entity(d).getPos());INITIAL_AGES.put(entity(d).getUuid(),entity(d).age);}
            if(age<100)return;moving=targets=0;int alive=0,active=0;
            for(var d:LOADED.values()) {
                if(entity(d).isAlive()&&!entity(d).isRemoved())alive++;
                if(entity(d).age>INITIAL_AGES.get(entity(d).getUuid()))active++;
                if(entity(d).getPos().squaredDistanceTo(INITIAL.get(entity(d).getUuid()))>.04)moving++;
                var target=((MobEntity)d).getTarget();if(target!=null&&HostileEnvironment.isHostileTarget(d,target))targets++;
            }
            if(age%20==0)System.out.println("DRONE_RELOAD alive="+alive+" active="+active+" moving="+moving+" targets="+targets+" shots="+shots+" attackRoles="+DAMAGING_ROLES);
            require(age<300,"autonomous drone resume timeout");
            if(alive==128&&active==128&&moving>=96&&targets>=96&&SHOOTERS.size()>=16&&DAMAGING_ROLES.size()>=2) {
                if(NativeSwarmBaselineCheck.TYPED&&(SHOOTERS.size()<96||DAMAGING_ROLES.size()!=3))return;
                var w=s.getOverworld();
                require(LOADED.values().stream().allMatch(d->w.getEntity(entity(d).getUuid())==d&&!((MobEntity)d).isAiDisabled()),"restored objects must be real registered active actors");
                require(w.getEntitiesByClass(DroneEntity.class,new Box(-128,2028,-128,140,2284,160),d->true).size()==128,"no duplicate local swarm after restart");
                acceptedRoles=Map.copyOf(DAMAGING_ROLES);require(acceptedRoles.values().stream().mapToInt(Integer::intValue).sum()==shots,"exact snapshot totals for this all-direct-damage fixture");ready=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<4800,"drone world reload timeout loaded="+LOADED.size()+" age="+age);
            com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.player!=null){c.player.getAbilities().flying=true;if(!viewReady){c.options.getViewDistance().setValue(8);c.options.sendClientSettings();viewReady=true;}}
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())
                if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        });
    }
    public static void drew(DroneEntity d){if(ids.contains(entity(d).getUuid()))DRAWN.add(entity(d).getUuid());}
    public static void rendered() {
        if(!ready||done)return;var c=MinecraftClient.getInstance();if(c.world==null||c.currentScreen!=null||++frames<3)return;
        var actors=new ArrayList<DroneEntity>();for(var e:c.world.getEntities())if(e instanceof DroneEntity d&&ids.contains(e.getUuid()))actors.add(d);
        if(c.player!=null&&!actors.isEmpty()) {
            Vec3d centre=Vec3d.ZERO;for(var d:actors)centre=centre.add(entity(d).getBoundingBox().getCenter());
            var aim=centre.multiply(1.0/actors.size()).subtract(c.player.getEyePos());
            float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));
            c.player.setYaw(yaw);c.player.prevYaw=yaw;c.player.setPitch(pitch);c.player.prevPitch=pitch;
            c.options.hudHidden=true;
        }
        if(captureStart==0)captureStart=System.nanoTime();
        if(frames%60==0)System.out.println("DRONE_RELOAD_VIEW synced="+actors.size()+" drawn="+DRAWN.size()+" camera="+c.player.getPos());
        long drawnKinds=actors.stream().filter(d->DRAWN.contains(entity(d).getUuid())&&d instanceof FlightEnemyEntity).map(d->((FlightEnemyEntity)d).kind()).distinct().count();
        if(actors.size()!=128||DRAWN.size()<32||(NativeSwarmBaselineCheck.TYPED&&drawnKinds!=3)) {
            if(System.nanoTime()-captureStart<15_000_000_000L)return;
            try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(file("drone-state-view-failed.png"));}catch(Exception e){throw new IllegalStateException(e);}
            throw new IllegalStateException("restored drone client visibility timeout: synced="+actors.size()+" drawn="+DRAWN.size()+" kinds="+drawnKinds);
        }
        require(actors.stream().allMatch(d->d.getRole().id.equals(ROLES.get(entity(d).getUuid()))),"client role metadata matches the saved identities");
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            if(NativeSwarmBaselineCheck.TYPED){
                var counts=new TreeMap<String,Integer>();var drawn=new TreeMap<String,Integer>();
                for(var d:actors){require(d instanceof FlightEnemyEntity&&Objects.equals(TYPES.get(entity(d).getUuid()),net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity(d).getType()).toString()),"client exact restored independent class/type");var kind=((FlightEnemyEntity)d).kind();counts.merge(kind.id,1,Integer::sum);if(DRAWN.contains(entity(d).getUuid()))drawn.merge(kind.id,1,Integer::sum);}
                require(counts.equals(Map.of("orb",43,"rocket",43,"jet",42))&&drawn.size()==3,"three independently rendered restored flight kinds");
                Files.writeString(file("typed-swarm-reload-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualRestart",true,"classesBeforeFirstTick",128,"clientKinds",counts,"renderedKinds",drawn,"active",128,"moving",moving,"naturalTargets",targets,"shooters",SHOOTERS.size(),"shotsByRole",acceptedRoles))+"\n");
            }
            NativeScheduleBudgetCheck.write("reload",false);
            image.writeTo(file("drone-state-reloaded.png"));
            Files.writeString(file("drone-state-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualRestart",true,"restoredComponents",128,"clientSynced",actors.size(),"renderedIdentities",DRAWN.size(),"moving",moving,"naturalTargets",targets,"shots",shots,"shooters",SHOOTERS.size(),"damagingRoles",acceptedRoles))+"\n");done=true;c.scheduleStop();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
