package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import java.nio.file.*;
import java.util.*;

/** Native roof replacement test. It reads real client/server light across three Y cubes.
 * No lighting arrays, native metadata, camera framebuffer or chunk tickets are modified. */
public final class NativeTransparentSkylightCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.transparentSkylight");
    private static final BlockPos ROOF=new BlockPos(8,2208,8);
    private static final BlockPos[] PROBES={ROOF.down(),ROOF.down(17),ROOF.down(33)};
    private static final BlockState[] MATERIALS={Blocks.AIR.getDefaultState(),Blocks.IRON_BLOCK.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.TINTED_GLASS.getDefaultState(),Blocks.IRON_BLOCK.getDefaultState(),Blocks.AIR.getDefaultState()};
    private static final String[] SCENES={"open-sky","opaque-roof","glass-restored","tinted-glass-control","opaque-replacement-control","air-restored"};
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static volatile ServerPlayerEntity player;
    private static volatile int[] serverSky={-1,-1,-1};
    private static volatile boolean modePrepared,located,ready,applied,done;
    private static boolean requested,teleportRequested,setupRequested,capture;
    private static int ticks,age,frames;
    private static volatile int phase;
    private static int[] lastClient={-1,-1,-1};
    private static String failurePending;
    private static double baselineMean,opaqueMean;
    private static double baselineCandidate;
    private static int stableBaselineFrames,lastVisibleMarkers;
    private static double lastFov;
    private static List<Map<String,Object>> lastMarkerSamples=List.of();
    private static final BlockPos[] MARKERS={new BlockPos(6,2206,10),new BlockPos(10,2206,10),new BlockPos(6,2204,10),new BlockPos(10,2204,10)};
    private static final BlockState[] MARKER_STATES={Blocks.RED_CONCRETE.getDefaultState(),Blocks.BLUE_CONCRETE.getDefaultState(),Blocks.LIME_CONCRETE.getDefaultState(),Blocks.GOLD_BLOCK.getDefaultState()};
    private NativeTransparentSkylightCheck(){}
    private static Entity e(Entity entity){return entity;}

    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(NativeTransparentSkylightCheck::tick);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!ready||done)return;
            var world=player.getServerWorld();
            if(!loaded(world))return;
            serverSky=light(world);
            if(server.getTicks()%40==0)System.out.println("TRANSPARENT_SKY phase="+phase+" material="+MATERIALS[phase]+" server="+Arrays.toString(serverSky));
        });
    }
    private static boolean loaded(World world){
        var access=WorldAccess.of(world);
        for(int y=(ROOF.getY()-35)>>4;y<=(ROOF.getY()+2)>>4;y++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(0,y,0)))return false;
        return true;
    }
    private static int[] light(World world){var out=new int[PROBES.length];for(int i=0;i<out.length;i++)out[i]=world.getLightLevel(LightType.SKY,PROBES[i]);return out;}
    private static boolean matches(World world,BlockState state){
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(!world.getBlockState(ROOF.add(x,0,z)).equals(state))return false;
        return true;
    }
    private static BlockState wallState(BlockPos pos){
        for(int i=0;i<MARKERS.length;i++)if(MARKERS[i].equals(pos))return MARKER_STATES[i];
        return Blocks.IRON_BLOCK.getDefaultState();
    }
    private static boolean wallMatches(World world){
        for(int x=6;x<=10;x++)for(int y=2204;y<=2206;y++){
            var at=new BlockPos(x,y,10);if(!world.getBlockState(at).equals(wallState(at)))return false;
        }
        return true;
    }
    private static void roof(BlockState state){
        var world=player.getServerWorld();
        if(!loaded(world))throw new IllegalStateException("Native roof mutation requires all three actual FULL cubes");
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.setBlockState(ROOF.add(x,0,z),state,Block.NOTIFY_ALL);
        applied=true;
    }
    private static void setup(){
        var world=player.getServerWorld();
        if(!loaded(world)){setupRequested=false;return;}
        if(CubicAvailability.backend(world)!=CubicAvailability.Backend.NATIVE_CUBIC)throw new IllegalStateException("Actual BCC backend required");
        for(var p:PROBES)if(!world.getBlockState(p).isAir())throw new IllegalStateException("Sky sensor is not initially air: "+p);
        for(int x=6;x<=10;x++)for(int y=2204;y<=2206;y++){var at=new BlockPos(x,y,10);world.setBlockState(at,wallState(at),Block.NOTIFY_ALL);}
        roof(MATERIALS[0]);ready=true;
    }
    private static void tick(MinecraftClient client){
        if(done)return;
        if(++ticks>3000){fail("native skylight gate timeout");return;}
        DescentClient.markUserFlightChoice();
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null||client.world==null||client.getServer()==null)return;
        if(!requested){
            require(client.runDirectory.getName().equals("cubic-client-automation"),"disposable staged runtime only");requested=true;
            client.setScreen(null);client.options.hudHidden=true;client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            client.inGameHud.getChatHud().clear(false);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            DescentConfig.cockpit=false;DescentConfig.weaponView=false;
            client.getServer().execute(()->{
                player=client.getServer().getPlayerManager().getPlayer(e(client.player).getUuid());player.stopRiding();FlightSystem.disable(player,DescentPlayerData.get(player));
                player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.getServerWorld().setTimeOfDay(6000);modePrepared=true;
            });return;
        }
        // A saved 6DoF correction must be acknowledged before placing the fixture camera.
        if(modePrepared&&!located&&!teleportRequested&&!com.terminaldetector.drmd.client.DescentClientState.enabled&&!client.player.hasVehicle()){
            teleportRequested=true;client.getServer().execute(()->{player.teleport(player.getServerWorld(),8.5,2206,3.5,Set.of(),0,18);located=true;});
        }
        if(located&&!setupRequested&&loaded(client.world)){setupRequested=true;client.getServer().execute(NativeTransparentSkylightCheck::setup);}
        if(!ready||failurePending!=null||!applied||!loaded(client.world))return;
        if(capture){if(++age>400)pendingFailure("fresh rendered witness never agreed with the synchronized light state");return;}
        client.player.setYaw(0);client.player.setHeadYaw(0);client.player.setPitch(18);
        lastClient=light(client.world);age++;
        if(!matches(client.world,MATERIALS[phase])){if(age>400)pendingFailure("roof material never synchronized");return;}
        boolean shouldBeOpen=phase==0||phase==2||phase==5;
        boolean correct=shouldBeOpen?Arrays.stream(lastClient).allMatch(v->v==15)&&Arrays.stream(serverSky).allMatch(v->v==15):lastClient[0]<15&&serverSky[0]<15&&lastClient[0]==serverSky[0];
        if(!correct){if(age>400)pendingFailure("expected "+(shouldBeOpen?"full skylight through non-occluding material":"real shadow from opaque material")+" client="+Arrays.toString(lastClient)+" server="+Arrays.toString(serverSky));return;}
        capture=true;frames=0;
    }
    private static void pendingFailure(String why){failurePending=why;System.out.println("TRANSPARENT_SKY_FAILURE "+why);}
    private static void require(boolean value,String why){if(!value)fail(why);}
    private static void fail(String why){
        if(done)return;done=true;
        var client=MinecraftClient.getInstance();
        try{var report=new LinkedHashMap<String,Object>();report.put("phase",phase);report.put("scene",SCENES[Math.min(phase,SCENES.length-1)]);report.put("material",MATERIALS[Math.min(phase,MATERIALS.length-1)].toString());report.put("clientSky",lastClient);report.put("serverSky",serverSky);report.put("clientCameraPlayer",client.player==null?null:e(client.player).getPos().toString());report.put("serverCameraPlayer",player==null?null:e(player).getPos().toString());report.put("visibleAuthoredMarkers",lastVisibleMarkers);report.put("markerSamples",lastMarkerSamples);report.put("actualRenderFov",lastFov);report.put("stableBaselineFrames",stableBaselineFrames);report.put("baselineCandidate",baselineCandidate);report.put("reason",why);Files.writeString(client.runDirectory.toPath().resolve("transparent-skylight-failure.json"),new com.google.gson.Gson().toJson(report));}
        catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        finally{client.scheduleStop();}
        throw new IllegalStateException("TRANSPARENT_SKY_NATIVE "+why);
    }
    /** Invoked only after the actual GameRenderer frame, never from a simulation tick. */
    public static void rendered(double verticalFov){
        if(!ENABLED||!ready||done||!capture&&failurePending==null)return;
        var client=MinecraftClient.getInstance();if(client.currentScreen!=null)return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
            var folder=client.runDirectory.toPath().resolve("transparent-skylight-frames");Files.createDirectories(folder);
            if(failurePending!=null){image.writeTo(folder.resolve("failed-"+SCENES[phase]+".png"));fail(failurePending);return;}
            // A synchronized block state is insufficient: Sodium may not have installed its mesh.
            // Four differently colored authored corners, projected from the actual camera/FOV,
            // distinguish this wall from the nearly neutral sky/LOD that fooled the first run.
            lastVisibleMarkers=wallMatches(client.world)?visibleMarkers(image,verticalFov):0;
            if(lastVisibleMarkers!=MARKERS.length){frames=0;stableBaselineFrames=0;return;}
            if(++frames<3)return;
            double mean=0;int count=0;
            for(int y=image.getHeight()/2-25;y<image.getHeight()/2+25;y++)for(int x=image.getWidth()/2-50;x<image.getWidth()/2+50;x++){
                int value=image.getColor(x,y),r=value&255,g=value>>>8&255,b=value>>>16&255;
                if(r>25&&Math.abs(r-g)<50&&Math.abs(g-b)<50){mean+=(r+g+b)/3.0;count++;}
            }
            if(count<=4000){frames=0;return;}mean/=count;
            if(phase==0){
                if(stableBaselineFrames==0||Math.abs(mean-baselineCandidate)>Math.max(1,mean*.01))stableBaselineFrames=1;
                else stableBaselineFrames++;
                baselineCandidate=mean;
                if(stableBaselineFrames<3)return;
            }
            if(phase==1||phase==3||phase==4){if(mean>=baselineMean-1){frames=0;return;}}
            if(phase==2||phase==5){if(mean<=opaqueMean+1||Math.abs(mean-baselineMean)>=baselineMean*.15){frames=0;return;}}
            image.writeTo(folder.resolve(SCENES[phase]+".png"));
            if(phase==0)baselineMean=mean;if(phase==1)opaqueMean=mean;
            var row=new LinkedHashMap<String,Object>();row.put("scene",SCENES[phase]);row.put("material",MATERIALS[phase].toString());row.put("clientSky",lastClient.clone());row.put("serverSky",serverSky.clone());row.put("witnessPixels",count);row.put("witnessMean",mean);row.put("wallMaterialsAcknowledged",true);row.put("visibleAuthoredMarkers",lastVisibleMarkers);row.put("initialStableBaselineFrames",stableBaselineFrames);RESULTS.add(row);
            System.out.println("TRANSPARENT_SKY_FRAME "+row);
            if(phase+1==SCENES.length){done=true;var result=new LinkedHashMap<String,Object>();result.put("realNativeCubic",true);result.put("noLightArrayOrMetadataWritesByObserver",true);result.put("threeActualCubeSensors",Arrays.stream(PROBES).map(BlockPos::toShortString).toList());result.put("scenes",RESULTS);Files.writeString(client.runDirectory.toPath().resolve("transparent-skylight-ok.json"),new com.google.gson.Gson().toJson(result));client.scheduleStop();return;}
            phase++;
            applied=false;capture=false;age=0;frames=0;client.getServer().execute(()->roof(MATERIALS[phase]));
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static int visibleMarkers(net.minecraft.client.texture.NativeImage image,double verticalFov){
        lastFov=verticalFov;var samples=new ArrayList<Map<String,Object>>();lastMarkerSamples=samples;
        if(!Double.isFinite(verticalFov)||verticalFov<=0||verticalFov>=179)return 0;
        var camera=MinecraftClient.getInstance().gameRenderer.getCamera();
        double focal=image.getHeight()/(2*Math.tan(Math.toRadians(verticalFov)/2));int visible=0;
        var inverse=new org.joml.Quaternionf(camera.getRotation()).conjugate();
        for(int marker=0;marker<MARKERS.length;marker++){
            var delta=new Vec3d(MARKERS[marker].getX()+.5,MARKERS[marker].getY()+.5,MARKERS[marker].getZ()).subtract(camera.getPos());
            var view=inverse.transform(new org.joml.Vector3f((float)delta.x,(float)delta.y,(float)delta.z));
            if(view.z>=-.1)continue;
            int cx=(int)Math.round(image.getWidth()/2.0+view.x*focal/-view.z),cy=(int)Math.round(image.getHeight()/2.0-view.y*focal/-view.z),matching=0;
            if(cx<5||cy<5||cx+5>=image.getWidth()||cy+5>=image.getHeight())continue;
            for(int y=cy-5;y<=cy+5;y++)for(int x=cx-5;x<=cx+5;x++){
                int value=image.getColor(x,y),r=value&255,g=value>>>8&255,b=value>>>16&255;
                boolean match=switch(marker){case 0->r>35&&r>g*1.8&&r>b*1.4;case 1->b>35&&b>r*1.7&&b>g*1.3;case 2->g>35&&g>r*1.3&&g>b*1.8;default->g>35&&r>b*2&&g>b*2&&r>g*1.05&&r<g*1.7;};
                if(match)matching++;
            }
            samples.add(Map.of("marker",marker,"world",MARKERS[marker].toShortString(),"screenX",cx,"screenY",cy,"matchingPixels",matching,"samplePixels",121));
            if(matching>=90)visible++;
        }
        return visible;
    }
}
