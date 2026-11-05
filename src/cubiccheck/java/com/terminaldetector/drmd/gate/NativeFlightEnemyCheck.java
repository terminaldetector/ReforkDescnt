package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual typed factories, meshes, icon tints, automatic combat and same-world type restoration. */
public final class NativeFlightEnemyCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.flightTypesWrite"),READ=Boolean.getBoolean("drmd.gate.flightTypesRead"),ENABLED=WRITE||READ;
    private static final Vec3d CENTRE=new Vec3d(48.5,2400,48.5);
    private static final Map<UUID,String> IDS=new ConcurrentHashMap<>();
    private static final Map<UUID,FlightEnemyEntity> ACTORS=new ConcurrentHashMap<>();
    private static final Map<UUID,Vec3d> INITIAL=new HashMap<>();
    private static final Map<UUID,Integer> INITIAL_AGE=new HashMap<>();
    private static final Map<String,Integer> SHOTS=new TreeMap<>();
    private static final Set<UUID> DRAWN=new HashSet<>(),SAVED=ConcurrentHashMap.newKeySet();
    private static volatile boolean located,running,ready,watching,done;
    private static volatile Map<String,Object> accepted=Map.of();
    private static boolean requested,spawnRequested,activated,observerReady;
    private static int phase,ticks,age,frames;
    private static Entity target;
    private static Entity entity(DroneEntity d){return d;}
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    public static void saved(DroneEntity d,NbtCompound tag) {
        if(!WRITE||!watching||entity(d).getWorld().isClient||!IDS.containsKey(entity(d).getUuid()))return;
        try{Files.writeString(file("flight-types-nbt/"+entity(d).getUuid()+".snbt"),tag.toString());SAVED.add(entity(d).getUuid());
            if(SAVED.size()==3)Files.writeString(file("flight-types-saved.json"),"{\"saved\":3,\"realWorldSerialization\":true}\n");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(DroneEntity d) {
        if(!READ||entity(d).getWorld().isClient||!IDS.containsKey(entity(d).getUuid()))return;
        try {
            require(d instanceof FlightEnemyEntity,"loaded independent class, not generic substitute");var typed=(FlightEnemyEntity)d;
            String id=IDS.get(entity(d).getUuid());require(typed.kind().id.equals(id)&&Registries.ENTITY_TYPE.getId(entity(d).getType()).toString().equals("drmd:"+id),"exact saved type/class identity");
            var before=StringNbtReader.parse(Files.readString(file("flight-types-nbt/"+entity(d).getUuid()+".snbt")));var actual=entity(d).writeNbt(new NbtCompound());
            for(String key:new String[]{"role","Health","shield","armor","modEnergy","attackCooldown","flightRoll","aiPhase","aiPhaseTime","Motion","Rotation","NoGravity","Pos"})
                require(before.contains(key)&&Objects.equals(before.get(key),actual.get(key)),"typed world state mismatch: "+id+"/"+key);
            ACTORS.put(entity(d).getUuid(),typed);
        }catch(Exception e){throw new IllegalStateException("typed actual restart",e);}
    }
    public static void shot(WeaponCore.FireConfig cfg,ProjectileEntity projectile) {
        if(!ENABLED||!running||ready||done||projectile==null||!(cfg.owner instanceof FlightEnemyEntity d)||!IDS.containsKey(entity(d).getUuid())||entity(d).getWorld().isClient)return;
        if(d.kind()==FlightEnemyKind.ROCKET)require(projectile.getMeshKind()==ProjectileEntity.MESH_ROCKET,"rocket enemy launches actual missile objects");
        SHOTS.merge(d.kind().id,1,Integer::sum);
    }
    public static void register() {
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable typed scene only");
        if(READ)try{var json=com.google.gson.JsonParser.parseString(Files.readString(file("flight-types-ids.json"))).getAsJsonObject();json.entrySet().forEach(e->IDS.put(UUID.fromString(e.getKey()),e.getValue().getAsString()));require(IDS.size()==3,"three saved typed IDs");}catch(Exception e){throw new IllegalStateException(e);}
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(done||ready)return;
            if(READ&&!observerReady&&!s.getPlayerManager().getPlayerList().isEmpty()){
                var p=s.getPlayerManager().getPlayerList().getFirst();FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();observerReady=true;
            }
            if(READ&&!running&&ACTORS.size()==3){startMotion();running=true;}
            if(!running)return;
            if(WRITE&&phase==0){if(++age>=15)ready=true;return;}
            if(WRITE&&!activated){if(target==null||target.age<5||ACTORS.values().stream().anyMatch(d->entity(d).age<5))return;for(var d:ACTORS.values())((MobEntity)d).setAiDisabled(false);DroneSwarmAi.clear();activated=true;startMotion();}
            age++;if(age<100)return;
            int moving=0,active=0,targets=0;
            for(var d:ACTORS.values()) {
                var e=entity(d);require(e.isAlive()&&!e.isRemoved()&&s.getOverworld().getEntity(e.getUuid())==e,"real active typed object");
                require(d.getRole()==d.kind().defaultRole&&!((MobEntity)d).isAiDisabled(),"default authored combat remains active");
                if(e.getPos().squaredDistanceTo(INITIAL.get(e.getUuid()))>.04)moving++;
                if(e.age-INITIAL_AGE.get(e.getUuid())>=20)active++;
                var t=((MobEntity)d).getTarget();if(t!=null&&HostileEnvironment.isHostileTarget(d,t))targets++;
            }
            if(age%20==0)System.out.println("FLIGHT_TYPES active="+active+" moving="+moving+" targets="+targets+" shots="+SHOTS);
            require(age<300,"typed autonomous combat timeout");
            if(active==3&&moving==3&&targets==3&&SHOTS.size()==3) {
                accepted=Map.of("typedActors",3,"active",active,"moving",moving,"naturalTargets",targets,"shotsByKind",Map.copyOf(SHOTS),"actualRestart",READ,"noManualEntityTicks",true);DRAWN.clear();ready=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeFlightEnemyCheck::tick);
    }
    private static void startMotion(){age=0;INITIAL.clear();INITIAL_AGE.clear();for(var d:ACTORS.values()){INITIAL.put(entity(d).getUuid(),entity(d).getPos());INITIAL_AGE.put(entity(d).getUuid(),entity(d).age);}SHOTS.clear();}
    private static void tick(MinecraftClient c) {
        if(done)return;require(++ticks<4800,"typed scene timeout actors="+ACTORS.size()+" phase="+phase+" age="+age);com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;c.player.getAbilities().flying=true;
        if(READ)return;
        if(!requested){requested=true;c.setScreen(null);c.options.hudHidden=false;
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));p.teleport(c.getServer().getOverworld(),CENTRE.x,CENTRE.y+10,CENTRE.z+22,Set.of(),180,20);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();p.getServerWorld().setTimeOfDay(6000);located=true;});return;
        }
        if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(()->spawn(c));}
    }
    private static boolean loaded(MinecraftClient c) {
        var a=WorldAccess.of(c.world);var b=BlockPos.ofFloored(CENTRE);
        for(int x=(b.getX()-12)>>4;x<=(b.getX()+12)>>4;x++)for(int y=(b.getY()-4)>>4;y<=(b.getY()+16)>>4;y++)for(int z=(b.getZ()-16)>>4;z<=(b.getZ()+24)>>4;z++)if(!a.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void spawn(MinecraftClient c) {
        var s=c.getServer();var w=s.getOverworld();var p=s.getPlayerManager().getPlayer(c.player.getUuid());int index=0;
        for(var kind:FlightEnemyKind.values()) {
            var before=new HashSet<UUID>();for(var e:w.iterateEntities())if(e instanceof FlightEnemyEntity)before.add(e.getUuid());
            s.getCommandManager().executeWithPrefix(p.getCommandSource(),"6dof_spawn "+kind.id);
            var added=new ArrayList<FlightEnemyEntity>();for(var e:w.iterateEntities())if(e instanceof FlightEnemyEntity d&&!before.contains(e.getUuid()))added.add(d);
            require(added.size()==1,"real command creates one independent "+kind.id);var d=added.getFirst();require(d.kind()==kind&&d.getRole()==kind.defaultRole&&((LivingEntity)d).getHealth()==kind.defaultRole.hullHp&&!((MobEntity)d).isAiDisabled(),"full active authored default");
            IDS.put(entity(d).getUuid(),kind.id);ACTORS.put(entity(d).getUuid(),d);((MobEntity)d).setAiDisabled(true);((MobEntity)d).setPersistent();entity(d).setInvulnerable(true);
            float[] pitches={-25,35,-40},banks={50,-70,110},yaws={0,-35,35};var at=CENTRE.add((index-1)*4.5,0,0);
            entity(d).refreshPositionAndAngles(at.x,at.y,at.z,yaws[index],pitches[index]);d.setFlightRoll(banks[index]);
            var item=Registries.ITEM.get(Identifier.of("drmd","spawn_egg_"+kind.id));p.getInventory().setStack(index,new ItemStack(item));index++;
        }
        p.currentScreenHandler.sendContentUpdates();age=0;running=true;
    }
    public static void drew(FlightEnemyEntity d){if(ENABLED&&!entity(d).isInvisible()&&IDS.containsKey(entity(d).getUuid()))DRAWN.add(entity(d).getUuid());}
    public static void rendered() {
        if(!ready||done)return;var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null)return;
        var actors=new ArrayList<FlightEnemyEntity>();for(var e:c.world.getEntities())if(e instanceof FlightEnemyEntity d&&IDS.containsKey(e.getUuid()))actors.add(d);
        if(actors.size()!=3)return;
        for(var d:actors)require(d.kind().id.equals(IDS.get(entity(d).getUuid()))&&d.getRole()==d.kind().defaultRole,"client exact class/type/role metadata");
        var colors=((com.terminaldetector.drmd.gate.mixin.FlightItemColoursAccess)c).drmd$itemColours();int slot=0;
        for(var kind:FlightEnemyKind.values()) {
            var stack=c.player.getInventory().getStack(slot++);require(Registries.ITEM.getId(stack.getItem()).toString().equals("drmd:spawn_egg_"+kind.id),"real inventory stack retained");
            require(c.getItemRenderer().getModel(stack,c.world,c.player,0)!=c.getBakedModelManager().getMissingModel(),"actual bound spawn egg model");
            require((colors.getColor(stack,0)&0xffffff)==kind.primaryColour&&(colors.getColor(stack,1)&0xffffff)==kind.secondaryColour,"actual spawn egg tint provider");
        }
        if(++frames<4||DRAWN.size()!=3)return;
        String imageName=READ?"flight-enemy-reloaded.png":phase==0?"flight-enemy-poses.png":"flight-enemy-active.png";
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(file(imageName));}catch(Exception e){throw new IllegalStateException(e);}
        if(READ){try{Files.writeString(file("flight-enemy-reload-ok.json"),new com.google.gson.Gson().toJson(accepted)+"\n");done=true;c.scheduleStop();}catch(Exception e){throw new IllegalStateException(e);}return;}
        if(phase==0){ready=running=false;frames=0;DRAWN.clear();c.getServer().execute(()->{var w=c.getServer().getOverworld();var cow=EntityType.COW.create(w);cow.setPosition(CENTRE.add(0,0,-12));cow.setAiDisabled(true);cow.setNoGravity(true);cow.setPersistent();cow.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,-1,4,false,false));require(w.spawnEntity(cow),"targetable live cow");target=cow;phase=1;running=true;});return;}
        try{Files.writeString(file("flight-enemy-live-ok.json"),new com.google.gson.Gson().toJson(accepted)+"\n");}catch(Exception e){throw new IllegalStateException(e);}
        ready=running=false;done=true;c.getServer().execute(()->{try {
            for(var e:java.util.stream.StreamSupport.stream(c.getServer().getOverworld().iterateEntities().spliterator(),false).toList())if(e instanceof ProjectileEntity shot&&shot.getOwnerLiving() instanceof FlightEnemyEntity d&&IDS.containsKey(entity(d).getUuid()))e.discard();
            Files.createDirectories(file("flight-types-nbt"));Files.writeString(file("flight-types-ids.json"),new com.google.gson.Gson().toJson(IDS)+"\n");watching=true;c.execute(c::scheduleStop);
        }catch(Exception e){throw new IllegalStateException(e);}});
    }
}
