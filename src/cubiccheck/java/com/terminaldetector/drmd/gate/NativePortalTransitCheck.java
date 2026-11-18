package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.nio.file.*;
import java.util.*;

/** One actual integrated-client held-forward transit. Only initial staging teleports/fixture writes. */
public final class NativePortalTransitCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.portalTransit");
    private static final BlockPos A=new BlockPos(6,2156,4),B=new BlockPos(20,2156,12),MARK=B.east(8);
    private static final Vec3d START=new Vec3d(6.5,2155.6,.5),F=new Vec3d(0,0,1),U=new Vec3d(-Math.sin(Math.PI/9),Math.cos(Math.PI/9),0);
    private record State(long tick,List<Double> p,List<Double> v,List<Double> f,List<Double> up,int epoch,boolean nativeFull){}
    private static volatile UUID playerId;private static volatile State live,beforeCross,afterCross;
    private static volatile boolean placed;private static volatile String serverFailure;
    private static boolean initialized,basis,jumped;private static volatile boolean finished;private static int ticks,phase,stageTicks,frames,initialTeleports;
    private static String capture;private static long boot;private static Vec3d previousClient;private static int initialEpoch;
    private static final List<State> SERVER=new ArrayList<>(); // owner-server writes; copied only after stop is requested
    private static final List<Map<String,Object>> CLIENT=new ArrayList<>(),FRAMES=new ArrayList<>();
    private NativePortalTransitCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativePortalTransitCheck::server);ClientTickEvents.END_CLIENT_TICK.register(NativePortalTransitCheck::tick);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static Vec3d vector(List<Double> v){return new Vec3d(v.get(0),v.get(1),v.get(2));}
    private static boolean loaded(World world){
        var access=WorldAccess.of(world);if(access.backend()!=CubicAvailability.Backend.NATIVE_CUBIC)return false;
        for(int x=0;x<=1;x++)for(int z=-1;z<=1;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,134,z)))return false;return true;
    }
    private static void server(MinecraftServer server){
        if(!ENABLED||finished||playerId==null||serverFailure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(playerId);if(p==null)return;var w=p.getServerWorld();var d=DescentPlayerData.get(p);
            if(!initialized){
                if(w.getBottomY()!=-16384||w.getHeight()!=32768||WorldAccess.of(w).backend()!=CubicAvailability.Backend.NATIVE_CUBIC)throw new IllegalStateException("actual native dimension required");
                initialized=true;p.changeGameMode(GameMode.CREATIVE);FlightSystem.enableWithoutMount(p,d);d.setFlightVelocity(Vec3d.ZERO);
                d.setShipAttitude(0,0,1,(float)U.x,(float)U.y,0);p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);initialTeleports++;ModNetworking.syncPlayer(p,d);initialEpoch=d.portalEpoch();
                System.out.println("PORTAL_TRANSIT_INITIAL_STAGING pos="+p.getPos()+" observerTeleports="+initialTeleports);return;
            }
            if(!placed){
                if(!loaded(w))return;
                for(var pos:List.of(A,B,MARK))if(!WorldAccess.of(w).readIfLoaded(pos).orElseThrow().isAir())throw new IllegalStateException("fixture requires actual loaded AIR at "+pos);
                w.setBlockState(A,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.NORTH).with(PortalPanelBlock.LINKED,true),2);
                w.setBlockState(B,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.EAST).with(PortalPanelBlock.LINKED,true),2);
                ((PortalPanelBlockEntity)w.getBlockEntity(A)).setLink(null,B,w.getRegistryKey());((PortalPanelBlockEntity)w.getBlockEntity(B)).setLink(null,A,w.getRegistryKey());
                for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){var pos=MARK.add(0,y,z);if(!WorldAccess.of(w).readIfLoaded(pos).orElseThrow().isAir())throw new IllegalStateException("marker requires loaded AIR");w.setBlockState(pos,Blocks.EMERALD_BLOCK.getDefaultState(),2);}
                w.setTimeOfDay(6000);placed=true;System.out.println("PORTAL_TRANSIT_AUTHORED blocks=11 nativeFULL=true");
            }
            var state=new State(server.getTicks(),xyz(p.getPos()),xyz(d.getFlightVelocity().multiply(.05)),xyz(d.shipForward(p)),xyz(d.shipUp(p)),d.portalEpoch(),loaded(w));live=state;
            if(SERVER.size()>=2048)throw new IllegalStateException("bounded server sample journal exhausted");synchronized(SERVER){SERVER.add(state);}
            if(afterCross==null&&p.getZ()<A.getZ()-.25&&d.getFlightVelocity().length()>1)beforeCross=state;
            if(afterCross==null&&d.portalEpoch()>initialEpoch&&p.getX()>B.getX()+.9&&Math.abs(p.getZ()-(B.getZ()+.5))<1){afterCross=state;System.out.println("PORTAL_TRANSIT_SERVER actual="+state);}
        }catch(Throwable error){serverFailure=error.toString();error.printStackTrace();}
    }
    private static void tick(MinecraftClient c){
        if(finished)return;
        if(++ticks>1800||System.nanoTime()-boot>180_000_000_000L){finish(c,"FAIL","finite180s/1800clienttick deadline phase="+phase);return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(serverFailure!=null){finish(c,"FAIL",serverFailure);return;}
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(playerId==null){
            if(!c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation")||!FabricLoader.getInstance().isModLoaded("bettercubicchunks")){finish(c,"FAIL","disposable actual native runtime required");return;}
            playerId=c.player.getUuid();c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.options.setPerspective(Perspective.FIRST_PERSON);
        }
        DescentClient.markUserFlightChoice();var authority=live;
        if(CLIENT.size()>=2048){finish(c,"FAIL","bounded client sample journal exhausted");return;}
        CLIENT.add(Map.of("tick",ticks,"phase",phase,"p",xyz(c.player.getPos()),"v",xyz(c.player.getVelocity()),"f",xyz(ShipAttitudeClient.get().forward()),"up",xyz(ShipAttitudeClient.get().up()),"epoch",ShipAttitudeClient.portalEpoch,"forwardHeld",c.options.forwardKey.isPressed()));
        if(phase==0){
            if(!placed||authority==null||!loaded(c.world)||!DescentClientState.enabled||c.player.getPos().distanceTo(START)>.4)return;
            if(!(c.world.getBlockEntity(A) instanceof PortalPanelBlockEntity a)||!(c.world.getBlockEntity(B) instanceof PortalPanelBlockEntity b)||!a.isLinked()||!B.equals(a.getLinkPartnerPos())||!b.isLinked()||!A.equals(b.getLinkPartnerPos())||!c.world.getBlockState(MARK).isOf(Blocks.EMERALD_BLOCK))return;
            if(!basis){ShipAttitudeClient.acceptPortalFrame(c.player,F,U);basis=true;stageTicks=0;}
            if(++stageTicks<5)return;capture="before";frames=0;phase=1;return;
        }
        if(phase==1&&capture==null){c.options.forwardKey.setPressed(true);previousClient=c.player.getPos();phase=2;stageTicks=0;System.out.println("PORTAL_TRANSIT_HELD_FORWARD_START");}
        if(phase==2){
            if(previousClient!=null&&c.player.getPos().distanceTo(previousClient)>8)jumped=true;previousClient=c.player.getPos();
            if(afterCross!=null&&ShipAttitudeClient.portalEpoch>initialEpoch&&c.player.getX()>B.getX()+.9){c.options.forwardKey.setPressed(false);phase=3;stageTicks=0;}
            else if(++stageTicks>240)finish(c,"FAIL","normal held-forward input did not transit actual linked panels");return;
        }
        if(phase==3&&++stageTicks>=12&&authority!=null){
            double error=c.player.getPos().distanceTo(vector(authority.p));
            if(error>.4||ShipAttitudeClient.get().forward().distanceTo(vector(authority.f))>.025||ShipAttitudeClient.get().up().distanceTo(vector(authority.up))>.025){if(stageTicks>80)finish(c,"FAIL","client/server pose-frame agreement error="+error);return;}
            if(beforeCross==null||afterCross==null||!jumped||!afterCross.nativeFull||vector(afterCross.v).x<=0||vector(afterCross.f).x<.98){finish(c,"FAIL","missing real pre/post authoritative crossing, velocity or client discontinuity witness");return;}
            capture="after";frames=0;phase=4;
        }
    }
    /** Called only from the genuine GameRenderer.render RETURN hook; no camera override exists. */
    public static void rendered(){
        if(!ENABLED||finished||capture==null||++frames<20)return;var c=MinecraftClient.getInstance();
        if(c.player==null||c.world==null||c.currentScreen!=null){finish(c,"FAIL","completed proof frame is not an active world");return;}
        String name=capture;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            var dir=c.runDirectory.toPath().resolve("portal-transit-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(name+".png"));
            FRAMES.add(Map.of("name",name,"completedFrames",frames,"width",image.getWidth(),"height",image.getHeight(),"clientPos",xyz(c.player.getPos()),"cameraPos",xyz(c.gameRenderer.getCamera().getPos()),"yaw",c.gameRenderer.getCamera().getYaw(),"pitch",c.gameRenderer.getCamera().getPitch()));
            System.out.println("PORTAL_TRANSIT_FRAME "+name+" client="+c.player.getPos()+" camera="+c.gameRenderer.getCamera().getPos());
            capture=null;if(name.equals("after"))finish(c,"PASS","actual native integrated held-forward transit with server agreement; PNGs are real context frames, not a pixel/whole-MP oracle");
        }catch(Throwable error){finish(c,"FAIL","completed PNG capture: "+error);}
    }
    private static void finish(MinecraftClient c,String status,String reason){
        if(finished)return;finished=true;c.options.forwardKey.setPressed(false);
        try{
            var data=new LinkedHashMap<String,Object>();data.put("status",status);data.put("reason",reason);data.put("observerInitialTeleports",initialTeleports);data.put("authoredBlocks",placed?11:0);data.put("noCameraOverride",true);data.put("clientJumpWitness",jumped);data.put("beforeCross",beforeCross);data.put("afterCross",afterCross);data.put("live",live);data.put("frames",FRAMES);data.put("client",CLIENT);
            synchronized(SERVER){data.put("server",List.copyOf(SERVER));}Files.writeString(c.runDirectory.toPath().resolve("portal-transit-outcome.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data)+"\n");
            System.out.println("PORTAL_TRANSIT_OUTCOME "+status+" "+reason);
        }catch(Exception error){error.printStackTrace();}c.scheduleStop();
    }
}
