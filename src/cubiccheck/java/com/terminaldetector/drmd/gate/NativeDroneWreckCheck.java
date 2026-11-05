package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.physics.*;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Actual lethal damage, server physics replacement and replicated articulated render parts. */
public final class NativeDroneWreckCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.droneWreck"),READ=Boolean.getBoolean("drmd.gate.droneWreckRead"),ENABLED=WRITE||READ;
    private static final Vec3d CENTRE=new Vec3d(48.5,2400,48.5);
    private static final List<FlightEnemyEntity> PARENTS=new ArrayList<>();
    private static final List<RagdollEntity> WRECKS=new ArrayList<>();
    private static volatile Set<UUID> liveIds=Set.of(),wreckIds=Set.of();
    private static final Set<UUID> LIVE_DRAWN=new HashSet<>(),WRECK_DRAWN=new HashSet<>();
    private static final Map<UUID,Vec3d> origins=new HashMap<>();
    private static volatile boolean located,spawned,ready,done,watching;
    private static volatile Map<UUID,FlightEnemyKind> kinds=Map.of();
    private static final Map<UUID,RagdollEntity> RESTORED=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<UUID> SAVED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean visualReady,observerReady;
    private static int expectedParts(FlightEnemyKind kind){return kind==FlightEnemyKind.ORB?1:kind==FlightEnemyKind.ROCKET?5:3;}
    private static float expectedScale(FlightEnemyKind kind){return kind==FlightEnemyKind.JET?2:1;}
    public static void saved(RagdollEntity r,net.minecraft.nbt.NbtCompound tag){
        if(!WRITE||!watching||entity(r).getWorld().isClient||!wreckIds.contains(entity(r).getUuid()))return;
        try{Files.writeString(file("drone-wreck-nbt/"+entity(r).getUuid()+".snbt"),tag.toString());SAVED.add(entity(r).getUuid());if(SAVED.size()==3)Files.writeString(file("drone-wreck-saved.json"),"{\"saved\":3,\"realWorldSerialization\":true}\n");}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(RagdollEntity r){
        if(!READ||entity(r).getWorld().isClient||!wreckIds.contains(entity(r).getUuid()))return;
        try{
            var before=net.minecraft.nbt.StringNbtReader.parse(Files.readString(file("drone-wreck-nbt/"+entity(r).getUuid()+".snbt")));var actual=entity(r).writeNbt(new net.minecraft.nbt.NbtCompound());
            var kind=kinds.get(entity(r).getUuid());require(r.flightHullKind()==kind&&r.hullScale()==expectedScale(kind)&&r.rig().segments().size()==expectedParts(kind),"actual saved authored rig restored before first tick");
            for(String key:new String[]{"life","hullVersion","flightHull","hullScale","ragdoll","Pos","Motion","NoGravity"})require(before.contains(key)&&same(before.get(key),actual.get(key)),"actual physical wreck component mismatch: "+key);
            RESTORED.put(entity(r).getUuid(),r);
        }catch(Exception e){throw new IllegalStateException("actual authored wreck reload",e);}
    }
    private static boolean same(net.minecraft.nbt.NbtElement a,net.minecraft.nbt.NbtElement b){
        if(a instanceof net.minecraft.nbt.AbstractNbtNumber x&&b instanceof net.minecraft.nbt.AbstractNbtNumber y)return Math.abs(x.doubleValue()-y.doubleValue())<1e-8;
        if(a instanceof net.minecraft.nbt.NbtCompound x&&b instanceof net.minecraft.nbt.NbtCompound y){if(!x.getKeys().equals(y.getKeys()))return false;for(var key:x.getKeys())if(!same(x.get(key),y.get(key)))return false;return true;}
        if(a instanceof net.minecraft.nbt.NbtList x&&b instanceof net.minecraft.nbt.NbtList y){if(x.size()!=y.size())return false;for(int i=0;i<x.size();i++)if(!same(x.get(i),y.get(i)))return false;return true;}
        return Objects.equals(a,b);
    }
    private static boolean requested;
    private static volatile int phase,age;
    private static int ticks,frames;
    private static Entity entity(Entity e){return e;}
    private static Path file(String s){return MinecraftClient.getInstance().runDirectory.toPath().resolve(s);}
    private static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    public static void register(){
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable death fixture only");
        if(READ)try{
            var meta=com.google.gson.JsonParser.parseString(Files.readString(file("drone-wreck-ids.json"))).getAsJsonObject();var map=new HashMap<UUID,FlightEnemyKind>();
            for(var e:meta.getAsJsonObject("wrecks").entrySet()){var kind=Arrays.stream(FlightEnemyKind.values()).filter(k->k.id.equals(e.getValue().getAsString())).findFirst().orElseThrow();map.put(UUID.fromString(e.getKey()),kind);}kinds=Map.copyOf(map);wreckIds=Set.copyOf(map.keySet());var parents=new HashSet<UUID>();for(var e:meta.getAsJsonArray("parents"))parents.add(UUID.fromString(e.getAsString()));liveIds=Set.copyOf(parents);require(map.size()==3&&parents.size()==3,"three actual saved wreck and parent identities");
        }catch(Exception e){throw new IllegalStateException(e);}
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(READ&&!observerReady&&!server.getPlayerManager().getPlayerList().isEmpty()){var p=server.getPlayerManager().getPlayerList().getFirst();FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();observerReady=true;}
            if(READ&&!spawned&&RESTORED.size()==3){WRECKS.addAll(RESTORED.values());for(var r:WRECKS)origins.put(entity(r).getUuid(),entity(r).getPos());phase=2;age=0;spawned=true;}
            if(!spawned||ready||done)return;
            if(phase==0){if(++age>=15)ready=true;return;}
            if(phase==2){age++;if(age<12)return;
                var w=server.getOverworld();require(WRECKS.size()==3,"three actual physics replacements");
                for(var d:WRECKS)require(w.getEntity(entity(d).getUuid())==d&&entity(d).age>=8&&d.flightHullKind()==kinds.get(entity(d).getUuid())&&d.hullScale()==expectedScale(d.flightHullKind())&&d.simulation().parts().size()==expectedParts(d.flightHullKind())&&entity(d).getPos().squaredDistanceTo(origins.get(entity(d).getUuid()))>1e-5,"real articulated wreck ticks and moves");
                ready=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<4800,"native wreck fixture timeout phase="+phase);com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;c.player.getAbilities().flying=true;
            if(!visualReady){visualReady=true;c.options.hudHidden=true;c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.getToastManager().clear();com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;}
            if(READ)return;
            if(!requested){requested=true;c.setScreen(null);c.options.hudHidden=true;c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.getToastManager().clear();c.inGameHud.getChatHud().clear(false);
                com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
                c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));p.teleport(p.getServerWorld(),CENTRE.x,CENTRE.y+4,CENTRE.z+13,Set.of(),180,18);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();p.getServerWorld().setTimeOfDay(6000);PhysicsProfileState.get(p.getServerWorld()).set(PhysicsProfile.EXPANDED);located=true;});return;
            }
            if(located&&!spawned&&phase==0&&loaded(c)){phase=1;c.getServer().execute(()->spawn(c.getServer().getOverworld()));}
        });
    }
    private static boolean loaded(MinecraftClient c){
        var b=BlockPos.ofFloored(CENTRE);var a=WorldAccess.of(c.world);
        for(int x=(b.getX()-12)>>4;x<=(b.getX()+12)>>4;x++)for(int y=(b.getY()-6)>>4;y<=(b.getY()+12)>>4;y++)for(int z=(b.getZ()-6)>>4;z<=(b.getZ()+16)>>4;z++)if(!a.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;return true;
    }
    private static void spawn(ServerWorld w){
        float[] yaw={90,0,35},pitch={0,-90,40},bank={0,0,75};int i=0;
        for(var kind:FlightEnemyKind.values()){var d=kind.create(w);if(kind==FlightEnemyKind.JET)((net.minecraft.entity.LivingEntity)d).getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE).setBaseValue(2);entity(d).calculateDimensions();var p=CENTRE.add((i-1)*4.5,0,0);entity(d).refreshPositionAndAngles(p.x,p.y,p.z,yaw[i],pitch[i]);d.setFlightRoll(bank[i]);((net.minecraft.entity.mob.MobEntity)d).setAiDisabled(true);((net.minecraft.entity.mob.MobEntity)d).setPersistent();require(w.spawnEntity(d),"real authored actor spawned");PARENTS.add(d);i++;}
        liveIds=Set.copyOf(PARENTS.stream().map(d->entity(d).getUuid()).toList());phase=age=0;spawned=true;
    }
    public static void drewLive(FlightEnemyEntity d){if(ENABLED&&!entity(d).isInvisible()&&liveIds.contains(entity(d).getUuid()))LIVE_DRAWN.add(entity(d).getUuid());}
    public static void drewWreck(RagdollEntity d){if(ENABLED&&wreckIds.contains(entity(d).getUuid()))WRECK_DRAWN.add(entity(d).getUuid());}
    private static void kill(ServerWorld w){
        var map=new HashMap<UUID,FlightEnemyKind>();
        for(var d:PARENTS){
            var centre=entity(d).getBoundingBox().getCenter();var forward=Vec3d.fromPolar(entity(d).getPitch(),entity(d).getYaw()).normalize();var up=FlightAttitude.upOf(forward,d.getFlightRoll()).normalize();var right=up.crossProduct(forward).normalize();
            var before=new HashSet<UUID>();for(var e:w.iterateEntities())if(e instanceof RagdollEntity)before.add(e.getUuid());
            require(((net.minecraft.entity.LivingEntity)d).damage(w.getDamageSources().genericKill(),Float.MAX_VALUE)&&!entity(d).isAlive(),"real lethal damage, not hidden live AI");
            var added=new ArrayList<RagdollEntity>();for(var e:w.iterateEntities())if(e instanceof RagdollEntity r&&!before.contains(e.getUuid()))added.add(r);require(added.size()==1,"one actual registered replacement");var r=added.getFirst();
            for(var p:r.simulation().parts()){
                var local=p.segment().restPosition();var expected=centre.add(right.multiply(local.x())).add(up.multiply(local.y())).add(forward.multiply(local.z()));var actual=p.body().position();var nose=p.body().rotation().rotate(new Vec3(0,0,1));var top=p.body().rotation().rotate(new Vec3(0,1,0));
                require(expected.squaredDistanceTo(actual.x(),actual.y(),actual.z())<1e-8&&forward.dotProduct(new Vec3d(nose.x(),nose.y(),nose.z()))>1-1e-6&&up.dotProduct(new Vec3d(top.x(),top.y(),top.z()))>1-1e-6,"all initial physical part axes/positions inherit the actual pitched/banked parent");
            }
            require(r.flightHullKind()==d.kind()&&r.hullScale()==expectedScale(d.kind())&&r.rig().segments().size()==expectedParts(d.kind()),"authored compound body and scale");map.put(entity(r).getUuid(),d.kind());
            WRECKS.add(r);origins.put(entity(r).getUuid(),entity(r).getPos());
        }
        kinds=Map.copyOf(map);wreckIds=Set.copyOf(WRECKS.stream().map(d->entity(d).getUuid()).toList());phase=2;age=0;
    }
    public static void rendered(){
        if(!ready||done)return;var c=MinecraftClient.getInstance();if(c.world==null||c.currentScreen!=null)return;
        if(phase==0){if(++frames<4||LIVE_DRAWN.size()!=3)return;capture(c,"drone-before-death.png");ready=false;phase=1;frames=0;c.getServer().execute(()->kill(c.getServer().getOverworld()));return;}
        if(phase!=2)return;var children=new ArrayList<RagdollEntity>();int visibleParents=0;
        for(var e:c.world.getEntities()){if(liveIds.contains(e.getUuid())&&!e.isInvisible())visibleParents++;if(e instanceof RagdollEntity r&&wreckIds.contains(e.getUuid()))children.add(r);}
        if(READ&&children.size()==3){var centre=Vec3d.ZERO;for(var r:children)centre=centre.add(entity(r).getPos());var aim=centre.multiply(1.0/3).subtract(c.player.getEyePos());float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));c.player.setYaw(yaw);c.player.prevYaw=yaw;c.player.setPitch(pitch);c.player.prevPitch=pitch;}
        if(children.size()!=3||visibleParents!=0||WRECK_DRAWN.size()!=3||++frames<4)return;
        for(var r:children)require(r.flightHullKind()==kinds.get(entity(r).getUuid())&&r.hullScale()==expectedScale(r.flightHullKind())&&r.rig().segments().size()==expectedParts(r.flightHullKind())&&r.currentRenderParts().size()==expectedParts(r.flightHullKind())&&r.simulation().parts().size()==expectedParts(r.flightHullKind())&&r.currentRenderParts().stream().allMatch(p->Double.isFinite(p.position().x())&&Double.isFinite(p.position().y())&&Double.isFinite(p.position().z())&&Math.abs(p.rotation().x()*p.rotation().x()+p.rotation().y()*p.rotation().y()+p.rotation().z()*p.rotation().z()+p.rotation().w()*p.rotation().w()-1)<1e-5),"finite replicated articulated render pose");
        capture(c,READ?"drone-wreck-reloaded.png":"drone-physical-wrecks.png");
        try{
            var marker=new LinkedHashMap<String,Object>();marker.put("actualLethalDamage",!READ);marker.put("actualRestart",READ);marker.put("initialPhysicalAxesChecked",READ?0:WRECKS.stream().mapToInt(r->r.rig().segments().size()).sum());marker.put("restoredBeforeFirstTick",READ?RESTORED.size():0);marker.put("clientParentsRendered",LIVE_DRAWN.size());marker.put("clientWrecksRendered",WRECK_DRAWN.size());marker.put("visibleDeadParents",visibleParents);marker.put("physicalWrecks",3);marker.put("partsByKind",Map.of("orb",1,"rocket",5,"jet",3));marker.put("scalesByKind",Map.of("orb",1,"rocket",1,"jet",2));marker.put("matchingTypedRigGeometry",true);
            Files.writeString(file(READ?"drone-wreck-reload-ok.json":"drone-wreck-ok.json"),new com.google.gson.Gson().toJson(marker)+"\n");done=true;
            if(READ){c.scheduleStop();return;}
            c.getServer().execute(()->{try{Files.createDirectories(file("drone-wreck-nbt"));var ids=new TreeMap<String,String>();for(var e:kinds.entrySet())ids.put(e.getKey().toString(),e.getValue().id);Files.writeString(file("drone-wreck-ids.json"),new com.google.gson.Gson().toJson(Map.of("wrecks",ids,"parents",liveIds.stream().map(UUID::toString).sorted().toList()))+"\n");watching=true;c.execute(c::scheduleStop);}catch(Exception e){throw new IllegalStateException(e);}});
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void capture(MinecraftClient c,String name){try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(file(name));}catch(Exception e){throw new IllegalStateException(e);}}
}
