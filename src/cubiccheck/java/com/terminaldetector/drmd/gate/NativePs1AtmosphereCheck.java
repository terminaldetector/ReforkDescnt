package com.terminaldetector.drmd.gate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.file.*;
import java.util.*;

/** One finite enabled-fog client. All readbacks observe the real world pass; no synthetic LOD data. */
public final class NativePs1AtmosphereCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ps1Atmosphere");
    private static final int[] ALTITUDES={600,1020,1580};
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static volatile ServerPlayerEntity player;
    private static volatile boolean admitted,authored,authoring;
    private static boolean started,done,armed,witness;
    private static int phase,ticks,phaseTicks,epoch,drawEpoch=-1,scans,stable,inputEpoch=-1,staleSourceFrames;
    private static float wantedInner,wantedReach;
    private static long readbackBytes;
    private static PlanetSurfaceMesh prepared,previous,drawMesh,rawMesh;
    private static NativeImage before,after,raw;
    private static float[] foreground;
    private static Map<String,Object> observed;
    private NativePs1AtmosphereCheck(){}
    private static int altitude(){return ALTITUDES[Math.min(2,phase/2)];}
    private static boolean enabledFog(){return phase==6||phase%2==1;}
    private static String name(){return phase==6?"native-restored":("y"+altitude()+(enabledFog()?"-fog":"-raw"));}
    private static void fail(String reason){
        if(done)return;done=true;var c=MinecraftClient.getInstance();
        try{
            if(RenderSystem.isOnRenderThread()){
                var folder=c.runDirectory.toPath().resolve("ps1-atmosphere-frames");Files.createDirectories(folder);
                if(before!=null)before.writeTo(folder.resolve("first-failure-before.png"));
                if(after!=null)after.writeTo(folder.resolve("first-failure-after.png"));
                long bytes=(long)c.getFramebuffer().textureWidth*c.getFramebuffer().textureHeight*4;
                if(c.world!=null&&readbackBytes+bytes<=64L*1024*1024)try(var image=screenshot()){image.writeTo(folder.resolve("first-failure-final.png"));}
            }
            write(Map.of("status","FAIL","reason",reason,"phase",phase,"scans",scans,"readbackBytes",readbackBytes,"states",RESULTS,
                "pendingState",observed==null?Map.of():observed,"rawSource",source(rawMesh),"preparedSource",source(prepared)));
        }catch(Exception failure){failure.printStackTrace();}
        c.scheduleStop();throw new IllegalStateException("PS1_ATMOSPHERE_NATIVE "+reason);
    }
    private static void require(boolean value,String why){if(!value)fail(why);}
    private static void write(Map<String,Object> data)throws java.io.IOException{
        var c=MinecraftClient.getInstance();Files.writeString(c.runDirectory.toPath().resolve("ps1-atmosphere-outcome.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
    }
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(NativePs1AtmosphereCheck::tick);
        HudRenderCallback.EVENT.register((draw,counter)->{if(!ENABLED||done)return;var m=draw.getMatrices();m.push();float scale=(float)(1/MinecraftClient.getInstance().getWindow().getScaleFactor());m.scale(scale,scale,1);draw.fill(17,20,18,40,0xff00ffff);m.pop();witness=true;});
    }
    private static List<BlockPos> wall(){
        var result=new ArrayList<BlockPos>();for(int x=11;x<=12;x++)for(int y=altitude()-2;y<=altitude()-1;y++)result.add(new BlockPos(x,y,14));return result;
    }
    private static void begin(MinecraftClient c){
        DescentConfig.presentationProfile=phase==6?PresentationProfile.HYBRID_NATIVE:PresentationProfile.PS1_HYBRID;
        DescentConfig.ps1SceneHeight=360;DescentConfig.ps1ColorLevels=256;DescentConfig.ps1DitherStrength=0;
        DescentConfig.ps1FarFog=new Ps1FarFogOptions(enabledFog()?1:0);
        phaseTicks=scans=stable=staleSourceFrames=0;previous=null;drawEpoch=-1;observed=null;
        if(phase<6&&phase%2==0){
            admitted=authored=authoring=false;
            c.getServer().execute(()->{
                player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var w=player.getServerWorld();
                require(CubicAvailability.backend(w)==CubicAvailability.Backend.NATIVE_CUBIC,"actual BCC backend");
                player.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(player,DescentPlayerData.get(player));
                player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(w,8.5,altitude(),8.5,Set.of(),0,20);player.setVelocity(Vec3d.ZERO);
                w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,c.getServer());w.setTimeOfDay(6000);admitted=true;
            });
        }
    }
    private static void tick(MinecraftClient c){
        if(done)return;if(++ticks>2200){fail("overall deadline");return;}
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!started){
            require(c.runDirectory.getName().equals("cubic-client-automation"),"disposable runtime only");started=true;c.setScreen(null);
            DescentConfig.hybridRender=DescentConfig.levelSky=DescentConfig.planetFloor=true;
            DescentConfig.cockpit=DescentConfig.weaponView=false;c.options.hudHidden=false;
            c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.getToastManager().clear();begin(c);return;
        }
        require(++phaseTicks<300,"phase deadline "+name());
        if(admitted&&!authored&&!authoring){
            authoring=true;c.getServer().execute(()->{
                var w=player.getServerWorld();var points=wall();
                boolean loaded=points.stream().allMatch(p->WorldEditSafety.neighbourhoodLoaded(w,p));
                if(!loaded){authoring=false;return;}
                require(points.stream().allMatch(p->WorldEditSafety.mayChange(w,p,player)&&w.getBlockState(p).isAir()),"all four cells editable and AIR before first write");
                for(var p:points)require(w.setBlockState(p,Blocks.WHITE_CONCRETE.getDefaultState(),2),"ordinary marker write");authored=true;
            });
        }
    }
    private static void clearFrame(){if(before!=null)before.close();if(after!=null)after.close();before=after=null;}
    public static void beforeFrame(){
        if(!ENABLED||done)return;epoch++;armed=witness=false;clearFrame();var c=MinecraftClient.getInstance();
        // Only a new altitude needs settling. ON follows its admitted OFF draw immediately;
        // waiting another twelve ticks would allow an unrelated async publication between them.
        if(!started||!admitted||!authored||(phase<6&&phase%2==0&&phaseTicks<12)||c.player==null||c.currentScreen!=null)return;
        if(!c.player.getAbilities().flying||Math.abs(c.player.getY()-altitude())>.02)return;
        if(wall().stream().anyMatch(p->!WorldEditSafety.neighbourhoodLoaded(c.world,p)||!c.world.getBlockState(p).isOf(Blocks.WHITE_CONCRETE)))return;
        armed=true;
    }
    public static void prepare(PlanetSurfaceMesh mesh){if(ENABLED&&!done)prepared=mesh;}
    public static void input(float inner,float reach){
        if(ENABLED&&!done&&!OffscreenWorldView.busy()){wantedInner=inner;wantedReach=reach;inputEpoch=epoch;}
    }
    private static Map<String,Object> source(PlanetSurfaceMesh mesh){
        return mesh==null?Map.of():Map.of("identity",System.identityHashCode(mesh),"origin",List.of(mesh.originX,mesh.originY,mesh.originZ),
            "quads",mesh.quads,"inner",mesh.innerRadius,"reach",mesh.reach,
            "positionsDiagnosticHash32",Arrays.hashCode(mesh.positions),"coloursDiagnosticHash32",Arrays.hashCode(mesh.colours));
    }
    private static void budget(long bytes){readbackBytes+=bytes;require(readbackBytes<=64L*1024*1024,"64MiB actual GPU readback cap");}
    private static NativeImage screenshot(){
        var c=MinecraftClient.getInstance();budget((long)c.getFramebuffer().textureWidth*c.getFramebuffer().textureHeight*4);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},saved=new int[4];
        for(int i=0;i<4;i++){saved[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?4:0);}
        try{return ScreenshotRecorder.takeScreenshot(c.getFramebuffer());}
        finally{com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);RenderSystem.bindTexture(texture);for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],saved[i]);}
    }
    private static float[] uniform(String name,int count){
        int program=HorizonShader.program().getGlRef(),at=GL20.glGetUniformLocation(program,name);require(at>=0,"actual active uniform "+name);
        float[] value=new float[count];GL20.glGetUniformfv(program,at,value);return value;
    }
    private static float[] witnessDepth(Matrix4f view){
        var c=MinecraftClient.getInstance();int width=c.getFramebuffer().textureWidth,height=c.getFramebuffer().textureHeight;
        var m=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view);var camera=c.gameRenderer.getCamera().getPos();
        var pixels=new ArrayList<int[]>();int x0=width,y0=height,x1=-1,y1=-1;
        for(var p:wall()){
            var clip=new Vector4f((float)(p.getX()+.5-camera.x),(float)(p.getY()+.5-camera.y),(float)(p.getZ()-camera.z),1).mul(m);
            if(clip.w<=0)return new float[0];int px=(int)((clip.x/clip.w*.5+.5)*width),py=(int)((clip.y/clip.w*.5+.5)*height);
            for(var d:new int[][]{{0,0},{-1,0},{1,0},{0,-1},{0,1}}){int x=px+d[0],y=py+d[1];if(x<0||y<0||x>=width||y>=height)return new float[0];pixels.add(new int[]{x,y});x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}
        }
        int w=x1-x0+1,h=y1-y0+1;require((long)w*h<=16384,"bounded actual projected marker ROI");budget((long)w*h*4);
        var data=MemoryUtil.memAllocFloat(w*h);int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},saved=new int[4];
        for(int i=0;i<4;i++){saved[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?4:0);}
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,c.getFramebuffer().fbo);GL11.glReadPixels(x0,y0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);float[] values=new float[pixels.size()];for(int i=0;i<values.length;i++){var p=pixels.get(i);values[i]=data.get((p[1]-y0)*w+p[0]-x0);}return values;}
        finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],saved[i]);MemoryUtil.memFree(data);}
    }
    public static void horizon(boolean entering,Matrix4f view){
        if(!ENABLED||!armed||done||OffscreenWorldView.busy()||prepared==null||prepared.quads<=0)return;
        if(entering){
            var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            if(inputEpoch!=epoch||prepared.stale(camera.x,camera.y,camera.z,wantedInner,wantedReach)){
                staleSourceFrames++;previous=null;stable=0;return;
            }
            // Admit only a current normal publisher result, not the stale field kept visible while
            // a new altitude/interest request is building. The connected ON draw retains EXACT identity.
            if(phase<6&&phase%2==0){if(prepared==previous)stable++;else{previous=prepared;stable=1;}if(stable<4)return;}
            require(++scans<=64,"independent opaque marker admission scans");foreground=witnessDepth(view);
            if(foreground.length<9||java.util.stream.IntStream.range(0,foreground.length).filter(i->foreground[i]<.999995f).count()<9)return;
            before=screenshot();drawMesh=prepared;drawEpoch=epoch;
        }else if(before!=null&&drawEpoch==epoch){
            after=screenshot();float[] retained=witnessDepth(view);require(retained.length==foreground.length,"same foreground samples");
            for(int i=0;i<retained.length;i++)require(Math.abs(retained[i]-foreground[i])<1e-6,"real opaque foreground depth retained");
            var c=MinecraftClient.getInstance();double eyeY=c.gameRenderer.getCamera().getPos().y;
            float density=uniform("Ps1FarFogDensity",1)[0],mix=uniform("Ps1SkyFogMix",1)[0];float[] sky=uniform("Ps1SkyFogColor",3);
            var expectedSky=c.world.getSkyColor(c.gameRenderer.getCamera().getPos(),c.getRenderTickCounter().getTickDelta(false));
            float expected=phase==6||!enabledFog()?0:Ps1Atmosphere.density(eyeY,1);
            require(Math.abs(density-expected)<1e-5&&mix==(expected>0?1:0),"actual altitude/profile GPU density");
            require(Math.abs(sky[0]-expectedSky.x)<1e-4&&Math.abs(sky[1]-expectedSky.y)<1e-4&&Math.abs(sky[2]-expectedSky.z)<1e-4,"actual shared sky target");
            int contribution=0;for(int y=0;y<after.getHeight();y++)for(int x=0;x<after.getWidth();x++)if((before.getColor(x,y)&0xffffff)!=(after.getColor(x,y)&0xffffff))contribution++;
            observed=new LinkedHashMap<>();observed.put("actualEyeY",eyeY);observed.put("density",density);observed.put("skyMix",mix);observed.put("skyColor",sky);observed.put("lodChangedPixels",contribution);observed.put("foregroundSamples",retained.length);observed.put("opaqueAdmissionScans",scans);observed.put("meshQuads",prepared.quads);observed.put("epoch",epoch);
            observed.put("source",source(drawMesh));observed.put("actualProjectionInput",List.of(wantedInner,wantedReach));
            observed.put("sourceInputEpoch",inputEpoch);observed.put("staleSourceFramesNotAdmitted",staleSourceFrames);
            require(contribution>=500,"real far-world pixel domain");require(GL11.glGetError()==0,"actual draw/readback GL error");
        }
    }
    public static void rendered(){
        if(!ENABLED||done||observed==null||drawEpoch!=epoch||after==null)return;var c=MinecraftClient.getInstance();
        try(var finalImage=screenshot()){
            var folder=c.runDirectory.toPath().resolve("ps1-atmosphere-frames");Files.createDirectories(folder);finalImage.writeTo(folder.resolve(name()+".png"));
            var scene=ScenePresentation.stats();require(!scene.inWorldPass()&&scene.fallbackFrames()==0,"completed world pass without fallback");
            require(c.getFramebuffer().textureWidth==c.getWindow().getFramebufferWidth(),"restored native framebuffer");
            require(scene.sceneWidth()==(phase==6?c.getFramebuffer().textureWidth:640)&&scene.sceneHeight()==(phase==6?c.getFramebuffer().textureHeight:360),"actual target dimensions");
            int hud=finalImage.getColor(17,25);require(witness&&(hud&255)<8&&((hud>>>8)&255)>247&&((hud>>>16)&255)>247,"one physical pixel HUD");
            if(phase<6&&phase%2==0){if(raw!=null)raw.close();raw=new NativeImage(after.getWidth(),after.getHeight(),false);raw.copyFrom(after);rawMesh=drawMesh;}
            if(phase<6&&phase%2==1){
                require(raw!=null&&rawMesh==drawMesh,"raw/styled pair uses the same actual geometry source");int changed=0;
                for(int y=0;y<after.getHeight();y++)for(int x=0;x<after.getWidth();x++)if((raw.getColor(x,y)&0xffffff)!=(after.getColor(x,y)&0xffffff))changed++;
                observed.put("fogChangedPixels",changed);require(changed>=500,"enabled fog has actual pixel effect");raw.close();raw=null;
            }
            int error=GL11.glGetError();require(error==0,"final native-target readback GL error");
            observed.put("scene",name());observed.put("hudPixel",true);observed.put("glError",error);RESULTS.add(observed);observed=null;
            if(++phase==7){done=true;clearFrame();write(Map.of("status","PASS","states",RESULTS,"ordinaryWorldWrites",12,"readbackBytes",readbackBytes,"fullAtmosphereOrVolumetricLodAccepted",false));c.scheduleStop();}
            else begin(c);
        }catch(java.io.IOException failure){fail(failure.toString());}
    }
}
