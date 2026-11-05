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
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.droneWreck");
    private static final Vec3d CENTRE=new Vec3d(48.5,2400,48.5);
    private static final List<FlightEnemyEntity> PARENTS=new ArrayList<>();
    private static final List<RagdollEntity> WRECKS=new ArrayList<>();
    private static volatile Set<UUID> liveIds=Set.of(),wreckIds=Set.of();
    private static final Set<UUID> LIVE_DRAWN=new HashSet<>(),WRECK_DRAWN=new HashSet<>();
    private static final Map<UUID,Vec3d> origins=new HashMap<>();
    private static volatile boolean located,spawned,ready,done;
    private static boolean requested;
    private static volatile int phase,age;
    private static int ticks,frames;
    private static Entity entity(Entity e){return e;}
    private static Path file(String s){return MinecraftClient.getInstance().runDirectory.toPath().resolve(s);}
    private static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    public static void register(){
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable death fixture only");
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!spawned||ready||done)return;
            if(phase==0){if(++age>=15)ready=true;return;}
            if(phase==2){age++;if(age<12)return;
                var w=server.getOverworld();require(WRECKS.size()==3,"three actual physics replacements");
                for(var d:WRECKS)require(w.getEntity(entity(d).getUuid())==d&&entity(d).age>=8&&d.simulation().parts().size()==5&&entity(d).getPos().squaredDistanceTo(origins.get(entity(d).getUuid()))>1e-5,"real articulated wreck ticks and moves");
                ready=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<4800,"native wreck fixture timeout phase="+phase);com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;c.player.getAbilities().flying=true;
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
        for(var kind:FlightEnemyKind.values()){var d=kind.create(w);var p=CENTRE.add((i-1)*4.5,0,0);entity(d).refreshPositionAndAngles(p.x,p.y,p.z,yaw[i],pitch[i]);d.setFlightRoll(bank[i]);((net.minecraft.entity.mob.MobEntity)d).setAiDisabled(true);((net.minecraft.entity.mob.MobEntity)d).setPersistent();require(w.spawnEntity(d),"real authored actor spawned");PARENTS.add(d);i++;}
        liveIds=Set.copyOf(PARENTS.stream().map(d->entity(d).getUuid()).toList());phase=age=0;spawned=true;
    }
    public static void drewLive(FlightEnemyEntity d){if(ENABLED&&!entity(d).isInvisible()&&liveIds.contains(entity(d).getUuid()))LIVE_DRAWN.add(entity(d).getUuid());}
    public static void drewWreck(RagdollEntity d){if(ENABLED&&wreckIds.contains(entity(d).getUuid()))WRECK_DRAWN.add(entity(d).getUuid());}
    private static void kill(ServerWorld w){
        for(var d:PARENTS){
            var centre=entity(d).getBoundingBox().getCenter();var forward=Vec3d.fromPolar(entity(d).getPitch(),entity(d).getYaw()).normalize();var up=FlightAttitude.upOf(forward,d.getFlightRoll()).normalize();var right=up.crossProduct(forward).normalize();
            var before=new HashSet<UUID>();for(var e:w.iterateEntities())if(e instanceof RagdollEntity)before.add(e.getUuid());
            require(((net.minecraft.entity.LivingEntity)d).damage(w.getDamageSources().genericKill(),Float.MAX_VALUE)&&!entity(d).isAlive(),"real lethal damage, not hidden live AI");
            var added=new ArrayList<RagdollEntity>();for(var e:w.iterateEntities())if(e instanceof RagdollEntity r&&!before.contains(e.getUuid()))added.add(r);require(added.size()==1,"one actual registered replacement");var r=added.getFirst();
            for(var p:r.simulation().parts()){
                var local=p.segment().restPosition();var expected=centre.add(right.multiply(local.x())).add(up.multiply(local.y())).add(forward.multiply(local.z()));var actual=p.body().position();var nose=p.body().rotation().rotate(new Vec3(0,0,1));var top=p.body().rotation().rotate(new Vec3(0,1,0));
                require(expected.squaredDistanceTo(actual.x(),actual.y(),actual.z())<1e-8&&forward.dotProduct(new Vec3d(nose.x(),nose.y(),nose.z()))>1-1e-6&&up.dotProduct(new Vec3d(top.x(),top.y(),top.z()))>1-1e-6,"all initial physical part axes/positions inherit the actual pitched/banked parent");
            }
            WRECKS.add(r);origins.put(entity(r).getUuid(),entity(r).getPos());
        }
        wreckIds=Set.copyOf(WRECKS.stream().map(d->entity(d).getUuid()).toList());phase=2;age=0;
    }
    public static void rendered(){
        if(!ready||done)return;var c=MinecraftClient.getInstance();if(c.world==null||c.currentScreen!=null)return;
        if(phase==0){if(++frames<4||LIVE_DRAWN.size()!=3)return;capture(c,"drone-before-death.png");ready=false;phase=1;frames=0;c.getServer().execute(()->kill(c.getServer().getOverworld()));return;}
        if(phase!=2)return;var children=new ArrayList<RagdollEntity>();int visibleParents=0;
        for(var e:c.world.getEntities()){if(liveIds.contains(e.getUuid())&&!e.isInvisible())visibleParents++;if(e instanceof RagdollEntity r&&wreckIds.contains(e.getUuid()))children.add(r);}
        if(children.size()!=3||visibleParents!=0||WRECK_DRAWN.size()!=3||++frames<4)return;
        for(var r:children)require(r.currentRenderParts().size()==5&&r.currentRenderParts().stream().allMatch(p->Double.isFinite(p.position().x())&&Double.isFinite(p.position().y())&&Double.isFinite(p.position().z())&&Math.abs(p.rotation().x()*p.rotation().x()+p.rotation().y()*p.rotation().y()+p.rotation().z()*p.rotation().z()+p.rotation().w()*p.rotation().w()-1)<1e-5),"finite replicated articulated render pose");
        capture(c,"drone-physical-wrecks.png");
        try{Files.writeString(file("drone-wreck-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualLethalDamage",true,"initialPhysicalAxesChecked",15,"clientParentsRendered",LIVE_DRAWN.size(),"clientWrecksRendered",WRECK_DRAWN.size(),"visibleDeadParents",visibleParents,"physicalWrecks",3,"partsPerWreck",5,"matchingTypedRigGeometry",false))+"\n");done=true;c.scheduleStop();}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void capture(MinecraftClient c,String name){try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(file(name));}catch(Exception e){throw new IllegalStateException(e);}}
}
