package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.network.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** External entry, real free-flight authority, H-off boarding and roof CCD on the registered UFO. */
public final class NativeFlightBodyCheck {
    private static final BlockPos AT=new BlockPos(8,2156,16);
    private static boolean started,prepared,finished,capturing,flightStarted;
    private static volatile boolean ready,roofReady;private static volatile Vec3d local,serverFeet,bodyStart,bodyNow;
    private static volatile int serverEpoch=-1,roofContacts;private static volatile boolean walking;
    private static int ticks,age,bodyId=-1,frames,draws,samples,stable;private static volatile int phase;private static double maxError,walkStart;
    private static String scene;private static final List<String> scenes=new ArrayList<>();
    private static net.minecraft.entity.Entity entity(BlockBodyEntity b){return b;}
    private NativeFlightBodyCheck(){}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeFlightBodyCheck::tick);}
    private static void require(boolean value,String why){if(!value){finished=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    public static void drew(BlockBodyEntity b){if(entity(b).getId()==bodyId&&b instanceof SkyUfoEntity)draws++;}
    public static void received(FlightBodyNetworking.State s) {
        if(finished||!s.active())return;var predicted=FlightBodyClient.predictedState(s.acknowledgment());
        if(predicted!=null){samples++;maxError=Math.max(maxError,predicted.feet().minus(new Vec3(s.x(),s.y(),s.z())).length());}
    }
    private static boolean loaded(MinecraftClient c) {
        var a=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
        for(int x=-1;x<=1;x++)for(int y=(AT.getY()>>4)-1;y<=(AT.getY()>>4)+1;y++)for(int z=-1;z<=2;z++)
            if(!a.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void tick(MinecraftClient c) {
        if(finished)return;if(++ticks>1800)require(false,"external UFO flight timeout: phase="+phase+" age="+age+" local="+local+" client="+(c.player==null?null:c.player.getPos())+" authority="+FlightBodyClient.active()+" serverEpoch="+serverEpoch);
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())
            if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null) {
            if(ticks%200==0)System.out.println("FLIGHT_BODY_STARTUP screen="+(c.currentScreen==null?null:c.currentScreen.getClass().getName()+" title="+c.currentScreen.getTitle()+" children="+c.currentScreen.children().stream().filter(child->child instanceof net.minecraft.client.gui.widget.ButtonWidget).map(child->((net.minecraft.client.gui.widget.ButtonWidget)child).getMessage().getString()).toList()));
            return;
        }
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(!started) {
            require(c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"),"disposable native runtime only");started=true;
            c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.inGameHud.getChatHud().clear(false);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.setInvulnerable(true);p.getAbilities().flying=true;p.getAbilities().allowFlying=true;p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),8,AT.getY()+5,2,Set.of(),0,0);});return;
        }
        if(!prepared&&c.player.getY()>AT.getY()-20&&loaded(c)){prepared=true;c.getServer().execute(()->setup(c));return;}
        if(!ready||capturing||!(c.world.getEntityById(bodyId) instanceof BlockBodyEntity))return;
        age++;
        if(phase==0&&FlightBodyClient.active()&&serverEpoch>0&&local!=null) {
            require(local.z< -11,"fixture must begin outside the actual UFO, not inside");
            c.options.forwardKey.setPressed(true);flightStarted=true;request("external-approach");phase=1;age=0;
        } else if(phase==1&&local!=null&&local.z> -4) {
            c.options.forwardKey.setPressed(false);require(local.z<0&&local.y>-.1,"entry must pass through the real side bay");
            require(bodyNow.distanceTo(bodyStart)>.2,"UFO must remain moving during the approach");request("moving-bay-entry");phase=2;age=0;
        } else if(phase==2&&age>12) {
            ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));phase=3;age=0;
        } else if(phase==3) {
            if(walking&&BoardingClient.drives(c.player)&&BoardingClient.state().grounded())stable++;else stable=0;
            if(stable>=12){walkStart=local.x;c.options.leftKey.setPressed(true);request("flight-to-boarding");phase=4;age=0;}
        } else if(phase==4) {
            if(age>=9)c.options.leftKey.setPressed(false);
            if(age>=24){require(walking&&local.x-walkStart>.5,"ordinary keys must walk inside the moving UFO");request("boarded-walking");phase=5;age=0;}
        } else if(phase==5&&age>4) {
            c.getServer().execute(()->roof(c));phase=6;age=0;
        } else if(phase==6&&roofReady&&com.terminaldetector.drmd.client.DescentClientState.enabled&&FlightBodyClient.active()) {
            com.terminaldetector.drmd.client.input.DescentKeybinds.descend.setPressed(true);phase=7;age=0;
        } else if(phase==7&&roofContacts>=8) {
            com.terminaldetector.drmd.client.input.DescentKeybinds.descend.setPressed(false);
            require(local.y>1&&local.y<10,"descending suit must remain outside real roof material");
            require(samples>=25&&maxError<.8,"free-flight prediction mismatch: samples="+samples+" max="+maxError);
            require(draws>=40&&flightStarted,"actual UFO render/input proof missing");request("rotating-roof-contact");phase=8;
        }
    }
    private static void configure(net.minecraft.server.network.ServerPlayerEntity p) {
        var d=DescentPlayerData.get(p);d.setAccel(400);d.setMaxSpeed(400);d.setDrag(0);d.setGravity(0);d.setGravityFactor(0);d.setFlightAssist(true);d.setFlightVelocity(Vec3d.ZERO);
        com.terminaldetector.drmd.flight.FlightSystem.enableWithoutMount(p,d);ModNetworking.syncPlayer(p,d);
    }
    private static void setup(MinecraftClient c) {
        var w=c.getServer().getOverworld();var ufo=com.terminaldetector.drmd.entity.ModEntities.SKY_UFO.create(w);ufo.setAutopilot(false);
        entity(ufo).refreshPositionAndAngles(AT.getX(),AT.getY(),AT.getZ(),0,0);require(w.spawnEntity(ufo),"spawn real UFO");entity(ufo).tick();bodyId=entity(ufo).getId();
        ufo.physics().withLinearVelocity(new Vec3(.5,0,0)).withAngularVelocity(new Vec3(0,.08,0));ufo.setPassengerGravity(new Vec3(0,-32,0));ufo.publishPhysicsState();bodyStart=entity(ufo).getPos();
        var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.teleport(w,AT.getX(),AT.getY()+.1,AT.getZ()-14,Set.of(),0,0);configure(p);w.setTimeOfDay(6000);ready=true;
        ServerTickEvents.END_WORLD_TICK.register(world->{
            if(world!=w||finished)return;local=ufo.worldToLocal(p.getPos());serverFeet=p.getPos();bodyNow=entity(ufo).getPos();serverEpoch=FlightBodySystem.epoch(p);walking=BoardingSystem.drives(p);
            if(phase>=7&&DescentPlayerData.get(p).isEnabled()&&local.y>1) {
                var velocity=DescentPlayerData.get(p).getFlightVelocity();
                if(ufo.boardingMotion().query(1).contacts(DynamicBodyAttachment.box(BoardingSystem.pure(p.getPos()),p.getWidth(),p.getHeight()),.03).stream().anyMatch(contact->contact.normal().y()>.3)&&velocity.y> -2)roofContacts++;
            }
            if(w.getTime()%20==0)System.out.println("FLIGHT_BODY_TRACE phase="+phase+" local="+local+" p="+p.getPos()+" v="+DescentPlayerData.get(p).getFlightVelocity()+" lease="+serverEpoch+" ack="+FlightBodySystem.acknowledgment(p)+" clientPending="+FlightBodyClient.pending()+" maxError="+maxError);
        });
    }
    private static void roof(MinecraftClient c) {
        var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var ufo=(SkyUfoEntity)BodySpatialIndex.find(p.getWorld(),bodyId).orElseThrow();
        var at=ufo.localToWorld(new Vec3d(3,9,3));p.teleport(p.getServerWorld(),at.x,at.y,at.z,Set.of(),0,0);
        ufo.physics().withAngularVelocity(new Vec3(.1,.08,.1));configure(p);roofReady=true;
    }
    private static void request(String s){scene=s;capturing=true;frames=0;}
    public static void rendered() {
        if(!capturing||finished)return;var c=MinecraftClient.getInstance();if(c.player==null||c.currentScreen!=null||++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            var out=c.runDirectory.toPath().resolve("flight-body-frames");Files.createDirectories(out);image.writeTo(out.resolve(scene+".png"));scenes.add(scene);capturing=false;
            System.out.println("FLIGHT_BODY_CLIENT "+scene+" PASS local="+local);
            if(phase==8) {
                Files.writeString(c.runDirectory.toPath().resolve("flight-body-ok.json"),"{\"actualUfo\":true,\"externalEntry\":true,\"actualThrust\":true,\"walkingHandoff\":true,\"roofContacts\":"+roofContacts+",\"teleportFollow\":false,\"renderedFrames\":"+draws+",\"predictionSamples\":"+samples+",\"maxPredictionError\":"+maxError+",\"scenes\":[\""+String.join("\",\"",scenes)+"\"]}\n");
                finished=true;c.scheduleStop();
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
