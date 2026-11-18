package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.*;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** One real Pyro, three completed world states, unchanged vanilla simulation and FPS option. */
public final class NativeAssetsPacingCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ps1AssetsPacing");
    private static final Vec3d START=new Vec3d(8.5,2159,8.5),SHIP=new Vec3d(8.5,2161,13.5);
    private record Physics(int tick,int age,List<Double> position,List<Double> velocity){}
    private static volatile UUID viewer,shipUuid;private static volatile int shipId=-1;private static volatile Physics live;
    private static Entity acceptedShip;private static int registrationChecks;private static volatile Map<String,Object> registration=Map.of();
    private static volatile String serverFailure;private static volatile boolean finished;private static boolean setup,active,captured,drawingFixture,modelThisFrame;
    private static int ticks,phase,frames,cubes,modelCalls,limitCalls,lastLimit=-1,baselineLimit=-1;
    private static boolean modelLowWorld;private static long boot,lastFrame;private static Physics startPhysics;
    private static final List<Long> INTERVALS=new ArrayList<>();private static final Set<Integer> LIMITS=new TreeSet<>();
    private static final List<Map<String,Object>> STATES=new ArrayList<>();
    private NativeAssetsPacingCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeAssetsPacingCheck::server);ClientTickEvents.END_CLIENT_TICK.register(NativeAssetsPacingCheck::tick);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static void server(MinecraftServer server){
        if(finished||viewer==null||serverFailure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(viewer);if(p==null)return;var w=p.getServerWorld();
            if(!setup){
                if(WorldAccess.of(w).backend()!=CubicAvailability.Backend.NATIVE_CUBIC)throw new IllegalStateException("actual native world required");
                setup=true;p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);p.setVelocity(Vec3d.ZERO);w.setTimeOfDay(6000);return;
            }
            if(shipId<0){
                if(!WorldAccess.of(w).isVolumeAvailable(D6ChunkPos.ofBlock(8,2161,13)))return;
                Entity ship=ModEntities.PYRO_SHIP.create(w);if(ship==null)throw new IllegalStateException("real Pyro creation failed");
                ship.setPosition(SHIP);ship.setYaw(0);ship.setPitch(0);ship.setVelocity(Vec3d.ZERO);
                if(!w.spawnEntity(ship))throw new IllegalStateException("real Pyro spawn refused");acceptedShip=ship;shipUuid=ship.getUuid();shipId=ship.getId();
            }
            // Accepted spawn and material FULL precede normal entity tracking/ticking admission.
            Entity ship=w.getEntity(shipUuid);registrationChecks++;
            registration=Map.of("tick",server.getTicks(),"checks",registrationChecks,"uuid",shipUuid.toString(),"id",shipId,
                    "uuidLookupSameIdentity",ship==acceptedShip,"idLookupSameIdentity",w.getEntityById(shipId)==acceptedShip,
                    "removed",acceptedShip.isRemoved(),"removalReason",String.valueOf(acceptedShip.getRemovalReason()),"acceptedAge",acceptedShip.age);
            if(acceptedShip.isRemoved())throw new IllegalStateException("accepted Pyro actually removed: "+acceptedShip.getRemovalReason());
            if(ship==null){if(registrationChecks>120)throw new IllegalStateException("normal Pyro tracking admission absent after120 server ticks");return;}
            if(ship!=acceptedShip||w.getEntityById(shipId)!=ship)throw new IllegalStateException("Pyro lookup identity changed");
            live=new Physics(server.getTicks(),ship.age,xyz(ship.getPos()),xyz(ship.getVelocity()));
        }catch(Throwable failure){serverFailure=failure.toString();}
    }
    private static void apply(){
        DescentConfig.presentationProfile=phase==1?PresentationProfile.PS1_HYBRID:PresentationProfile.HYBRID_NATIVE;
        DescentConfig.ps1Assets=phase==1?Ps1AssetProfile.LOW_POLY:Ps1AssetProfile.NATIVE;
        DescentConfig.ps1Pacing=new Ps1FramePacingOptions(phase==1?10:0);DescentConfig.ps1SceneHeight=360;
        frames=cubes=modelCalls=limitCalls=0;lastLimit=-1;lastFrame=0;INTERVALS.clear();LIMITS.clear();startPhysics=live;captured=false;
    }
    private static void tick(MinecraftClient c){
        if(finished)return;if(++ticks>1800||System.nanoTime()-boot>180_000_000_000L){finish("FAIL","finite deadline");return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(serverFailure!=null){finish("FAIL",serverFailure);return;}if(c.player==null||c.world==null||c.getServer()==null)return;
        DescentClient.markUserFlightChoice();
        if(viewer==null){viewer=c.player.getUuid();c.setScreen(null);c.options.hudHidden=false;c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);return;}
        if(!active){Entity tracked=c.world.getEntityById(shipId);if(live==null||tracked==null||!tracked.getUuid().equals(shipUuid)||c.player.getPos().distanceTo(START)>.02||c.currentScreen!=null||DescentClientState.enabled||!c.player.getAbilities().flying)return;active=true;apply();}
        else if(captured){phase++;apply();}
    }
    public static void actor(Entity ship,boolean before){drawingFixture=before&&active&&!finished&&ship.getId()==shipId&&ship.getUuid().equals(shipUuid)&&!OffscreenWorldView.busy();}
    public static void beforeFrame(){if(ENABLED)modelThisFrame=false;}
    /** Actual completed PyroShipModel.render; count its real baked cuboids, never blueprint metadata. */
    public static void model(ModelPart root){
        if(!drawingFixture)return;int[] count={0};root.forEachCuboid(new MatrixStack(),(matrix,path,index,cuboid)->count[0]++);
        cubes=count[0];modelCalls++;modelThisFrame=true;modelLowWorld=ScenePresentation.inLowResolutionWorld();
    }
    /** Return of the real private MinecraftClient FPS getter at its genuine main-loop call site. */
    public static void limit(int value){if(active&&!finished){lastLimit=value;limitCalls++;LIMITS.add(value);}}
    public static void rendered(){
        if(!ENABLED||!active||finished||captured)return;long now=System.nanoTime();if(lastFrame!=0&&INTERVALS.size()<32)INTERVALS.add(now-lastFrame);lastFrame=now;
        if(++frames<24||!modelThisFrame||limitCalls<1)return;var c=MinecraftClient.getInstance();
        var physics=live;if(physics==null||startPhysics==null||physics.tick<=startPhysics.tick||physics.age<=startPhysics.age)return;
        try{
            int wanted=phase==1?17:32;if(cubes!=wanted||modelLowWorld!=(phase==1)||c.currentScreen!=null)throw new IllegalStateException("actual completed world model/phase mismatch: "+cubes);
            if(phase==0){baselineLimit=lastLimit;if(baselineLimit<=10){finish("UNOBSERVED","native FPS baseline is not above the requested10 cap");return;}}
            if(lastLimit!=(phase==1?10:baselineLimit))throw new IllegalStateException("actual FPS getter did not cap/restore: "+lastLimit);
            String name=phase==0?"native-before":phase==1?"ps1-coarse-10fps":"native-restored";var dir=c.runDirectory.toPath().resolve("assets-pacing-frames");Files.createDirectories(dir);
            try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(dir.resolve(name+".png"));}
            var row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("actualCuboids",cubes);row.put("completedModelCalls",modelCalls);row.put("lowResolutionWorldAtModel",modelLowWorld);row.put("actualLimitReturn",lastLimit);row.put("allObservedLimits",List.copyOf(LIMITS));row.put("frameIntervalsNs",List.copyOf(INTERVALS));row.put("physicsBefore",startPhysics);row.put("physicsAfter",physics);row.put("scene",ScenePresentation.stats());row.put("clientPos",xyz(c.player.getPos()));row.put("cameraPos",xyz(c.gameRenderer.getCamera().getPos()));row.put("gl",GL11.glGetString(GL11.GL_VERSION));STATES.add(row);captured=true;
            if(phase==2)finish("PASS","actual32→17→32 model renders and10 FPS getter cap/Native restore; recorded timing is context, not hardware performance acceptance");
        }catch(Throwable failure){finish("FAIL",failure.toString());}
    }
    private static void finish(String status,String reason){
        if(finished)return;finished=true;var c=MinecraftClient.getInstance();
        try{Files.writeString(c.runDirectory.toPath().resolve("assets-pacing-outcome.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status",status,"reason",reason,"states",STATES,"phase",phase,"shipId",shipId,"initialPlayerTeleports",setup?1:0,"authoredBlocks",0,"noCameraOverride",true,"shipUuid",String.valueOf(shipUuid),"registration",registration))+"\n");}catch(Exception failure){failure.printStackTrace();}
        System.out.println("ASSETS_PACING_OUTCOME "+status+" "+reason);c.scheduleStop();
    }
}
