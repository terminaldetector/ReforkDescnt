package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.*;
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
import net.minecraft.client.gl.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.*;

/** Three real world states; no renderer invocation, shader replacement or AI acceptance from the witness. */
public final class NativeMobGeometryCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.mobGeometry");
    private static final Vec3d START=new Vec3d(8.5,2159,8.5);
    private static final Identifier[] SKINS={Identifier.of("drmd","textures/entity/drone.png"),Identifier.of("drmd","textures/entity/spider_turret.png")};
    private static final Entity[] ACCEPTED=new Entity[2];private static final UUID[] UUIDS=new UUID[2];private static final int[] IDS={-1,-1};
    private record Actor(int id,UUID uuid,int age,List<Double> position){}
    private record View(int tick,List<Actor> actors){}
    private record Pending(VertexBuffer buffer,ShaderProgram shader,int actorMaterial,int sampler){}
    private static volatile UUID viewer;private static volatile View live;private static volatile boolean setup,finished;private static volatile String serverFailure;
    private static int ticks,phase,frames,admissionTicks;private static long boot;private static boolean active,captured;private static View started;
    private static final int[] MODELS=new int[2],CUBES=new int[2],ROUTES=new int[2],DRAWS=new int[2];
    private static final boolean[] MODEL_FRAME=new boolean[2];
    private static final long[] SELECTION_START=new long[2],DRAW_START=new long[2],FALLBACK_START=new long[2];
    private static final List<Map<String,Object>> STATES=new ArrayList<>();
    private static final Map<Integer,Map<String,Object>> GPU=new HashMap<>();private static Pending pending;private static String renderFailure;
    private NativeMobGeometryCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeMobGeometryCheck::server);ClientTickEvents.END_CLIENT_TICK.register(NativeMobGeometryCheck::tick);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static void server(MinecraftServer server){
        if(finished||viewer==null||serverFailure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(viewer);if(p==null)return;var w=p.getServerWorld();
            require(WorldAccess.of(w).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual native BCC world required");
            if(!setup){setup=true;p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);p.setVelocity(Vec3d.ZERO);return;}
            var actors=new ArrayList<Actor>();
            for(int i=0;i<2;i++){
                if(ACCEPTED[i]==null){
                    var at=i==0?START.add(-2,1.2,6):START.add(2,.2,6);
                    if(!WorldAccess.of(w).isVolumeAvailable(D6ChunkPos.ofBlock((int)Math.floor(at.x),(int)Math.floor(at.y),(int)Math.floor(at.z))))return;
                    Entity entity=i==0?ModEntities.DRONE.create(w):ModEntities.SPIDER_TURRET.create(w);require(entity instanceof MobEntity,"actual registered mob creation");
                    MobEntity mob=(MobEntity)entity;mob.setAiDisabled(true);entity.setNoGravity(true);entity.setPosition(at);entity.setVelocity(Vec3d.ZERO);entity.setYaw(180);
                    require(w.isSpaceEmpty(entity,entity.getBoundingBox()),"real visible mob fixture needs clear material");require(w.spawnEntity(entity),"normal world spawn accepted");ACCEPTED[i]=entity;UUIDS[i]=entity.getUuid();IDS[i]=entity.getId();
                }
                Entity entity=w.getEntity(UUIDS[i]);require(!ACCEPTED[i].isRemoved(),"accepted fixture mob was actually removed");
                if(entity==null){require(++admissionTicks<240,"normal native entity registration admission");return;}
                require(entity==ACCEPTED[i]&&w.getEntityById(IDS[i])==entity,"same accepted entity identity under UUID and id lookup");actors.add(new Actor(entity.getId(),entity.getUuid(),entity.age,xyz(entity.getPos())));
            }
            live=new View(server.getTicks(),List.copyOf(actors));
        }catch(Throwable error){serverFailure=error.toString();}
    }
    private static void apply(){
        DescentConfig.presentationProfile=phase==1?PresentationProfile.PS1_HYBRID:PresentationProfile.HYBRID_NATIVE;
        DescentConfig.ps1Geometry=new Ps1GeometryOptions(phase==1?2:0,phase==1?1:0);DescentConfig.ps1SceneHeight=360;
        // Keep the same real mesh across all states: this gate targets built-in shader flow.
        DescentConfig.ps1Assets=Ps1AssetProfile.NATIVE;DescentConfig.ps1Pacing=new Ps1FramePacingOptions(0);
        frames=0;captured=false;started=live;GPU.clear();pending=null;Arrays.fill(MODELS,0);Arrays.fill(ROUTES,0);Arrays.fill(DRAWS,0);
        for(int i=0;i<2;i++){SELECTION_START[i]=Ps1BodyGeometryLayer.stats(SKINS[i]).shaderSelections();DRAW_START[i]=Ps1BodyGeometryLayer.completedDraws(SKINS[i]);FALLBACK_START[i]=Ps1BodyGeometryLayer.stats(SKINS[i]).fallbacks();}
    }
    private static void tick(MinecraftClient c){
        if(finished)return;if(++ticks>3200||System.nanoTime()-boot>160_000_000_000L){finish("FAIL","finite160s admission/render deadline");return;}
        if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen p&&p.getTitle().getString().equals("Warning! Low disk space!"))for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Continue"))b.onPress();
        if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(serverFailure!=null){finish("FAIL",serverFailure);return;}if(c.player==null||c.world==null||c.getServer()==null)return;DescentClient.markUserFlightChoice();
        if(viewer==null){viewer=c.player.getUuid();c.options.hudHidden=false;return;}
        if(!active){
            if(live==null||c.currentScreen!=null||c.getOverlay()!=null||DescentClientState.enabled||c.player.getPos().distanceTo(START)>.03)return;
            for(int i=0;i<2;i++){Entity entity=c.world.getEntityById(IDS[i]);if(entity==null||!entity.getUuid().equals(UUIDS[i]))return;}
            active=true;apply();
        }else if(captured){phase++;apply();}
    }
    public static void beforeFrame(){if(ENABLED)Arrays.fill(MODEL_FRAME,false);}
    /** Injected immediately after the actual EntityModel.render invoke inside LivingEntityRenderer. */
    public static void model(Entity actor,EntityModel<?> model){
        if(!ENABLED||!active||finished||captured||OffscreenWorldView.busy())return;
        for(int i=0;i<2;i++)if(actor.getId()==IDS[i]&&actor.getUuid().equals(UUIDS[i]))try{
            var field=model.getClass().getDeclaredField("root");field.setAccessible(true);var root=(ModelPart)field.get(model);int[] count={0};root.forEachCuboid(new MatrixStack(),(m,path,index,cuboid)->count[0]++);
            MODELS[i]++;CUBES[i]=count[0];MODEL_FRAME[i]=true;
        }catch(ReflectiveOperationException error){renderFailure=error.toString();}
    }
    private static int material(Identifier texture){for(int i=0;i<2;i++)if(SKINS[i].equals(texture))return i;return -1;}
    public static void route(RenderLayer original,Identifier texture,RenderLayer result){
        if(!ENABLED||!active||finished||captured)return;int i=material(texture);if(i<0)return;
        boolean supported=original==RenderLayer.getEntityCutoutNoCull(texture);
        boolean expected=supported&&phase==1&&ScenePresentation.inLowResolutionWorld()&&!OffscreenWorldView.busy();
        if((original!=result)!=expected)renderFailure="actual mob material layer route leaked or failed: "+texture;
        if(supported&&!OffscreenWorldView.busy())ROUTES[i]++;
    }
    public static void draw(VertexBuffer buffer,ShaderProgram shader,boolean before){
        if(!ENABLED||!active||finished||captured||OffscreenWorldView.busy())return;
        var c=MinecraftClient.getInstance();
        if(before){
            pending=null;int sampler=RenderSystem.getShaderTexture(0);
            for(int i=0;i<2;i++)if(sampler==c.getTextureManager().getTexture(SKINS[i]).getGlId()){pending=new Pending(buffer,shader,i,sampler);return;}
        }else if(pending!=null&&pending.buffer==buffer&&pending.shader==shader){
            var p=pending;pending=null;var row=new LinkedHashMap<String,Object>();row.put("shader",shader.getName());row.put("program",shader.getGlRef());row.put("skin",SKINS[p.actorMaterial].toString());row.put("sampler0Texture",p.sampler);row.put("lowWorld",ScenePresentation.inLowResolutionWorld());row.put("snap",uniform(shader,"VertexSnapPixels"));row.put("affine",uniform(shader,"AffineAmount"));int error=GL11.glGetError();row.put("glError",error);if(error!=0)renderFailure="actual material draw GL error "+error;GPU.put(p.actorMaterial,row);DRAWS[p.actorMaterial]++;
        }
    }
    private static Float uniform(ShaderProgram shader,String name){int at=GL20.glGetUniformLocation(shader.getGlRef(),name);if(at<0)return null;float[] value={0};GL20.glGetUniformfv(shader.getGlRef(),at,value);return value[0];}
    public static void rendered(){
        if(!ENABLED||!active||finished||captured)return;if(renderFailure!=null){finish("FAIL",renderFailure);return;}if(++frames<8||!MODEL_FRAME[0]||!MODEL_FRAME[1]||GPU.size()!=2)return;
        if(live==null||started==null||live.tick<=started.tick)return;for(int i=0;i<2;i++)if(live.actors.get(i).age<=started.actors.get(i).age)return;
        try{
            var c=MinecraftClient.getInstance();require(c.currentScreen==null&&c.getOverlay()==null&&!OffscreenWorldView.busy()&&!ScenePresentation.inLowResolutionWorld(),"completed main frame, outside overlay/offscreen world domain");
            var materials=new ArrayList<Map<String,Object>>();
            for(int i=0;i<2;i++){
                var stats=Ps1BodyGeometryLayer.stats(SKINS[i]);long selected=stats.shaderSelections()-SELECTION_START[i],completed=Ps1BodyGeometryLayer.completedDraws(SKINS[i])-DRAW_START[i];var gpu=GPU.get(i);
                gpu.put("actualLayerStats",stats);require(stats.fallbacks()==FALLBACK_START[i],"no unobserved shader fallback");
                boolean custom=String.valueOf(gpu.get("shader")).contains("ps1_body_cutout");require(custom==(phase==1)&&((Number)gpu.get("glError")).intValue()==0,"actual completed skin shader and GL success");
                require(MODELS[i]>0&&CUBES[i]>0&&ROUTES[i]>0&&DRAWS[i]>0,"actual fixture model/material/draw callbacks");
                require(phase==1?selected>0&&completed>0&&stats.snapPixels()==2&&stats.affineAmount()==1:selected==0&&completed==0,"per-material PS1 shader selection and draw deltas; Native restoration");
                if(phase==1)require(Float.valueOf(2).equals(gpu.get("snap"))&&Float.valueOf(1).equals(gpu.get("affine"))&&Boolean.TRUE.equals(gpu.get("lowWorld")),"actual GPU snap2/affine1 in low-resolution world draw");
                materials.add(Map.of("uuid",UUIDS[i].toString(),"id",IDS[i],"skin",SKINS[i].toString(),"modelReturns",MODELS[i],"cuboids",CUBES[i],"routes",ROUTES[i],"drawReturns",DRAWS[i],"shaderSelectionsDelta",selected,"completedPs1DrawsDelta",completed,"gpu",gpu));
            }
            String name=phase==0?"native-before":phase==1?"ps1-snap2-affine1":"native-restored";var dir=c.runDirectory.toPath().resolve("mob-geometry-frames");Files.createDirectories(dir);require(GL11.glGetError()==0,"pre-PNG GL success");try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(dir.resolve(name+".png"));}require(GL11.glGetError()==0,"post-PNG GL success");
            STATES.add(Map.of("name",name,"materials",materials,"actualServer",live,"scene",ScenePresentation.stats(),"camera",xyz(c.gameRenderer.getCamera().getPos()),"assets","NATIVE","frames",frames));captured=true;
            if(phase==2)finish("PASS","actual scout/spider model and skin draw through Native→built-in PS1 shader→Native; AI and image beauty are not acceptance scopes");
        }catch(Throwable error){finish("FAIL",error.toString());}
    }
    private static void finish(String status,String why){
        if(finished)return;finished=true;var c=MinecraftClient.getInstance();
        try{var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("reason",why);out.put("states",STATES);out.put("phase",phase);out.put("initialTeleports",setup?1:0);out.put("visualFixtureAiDisabled",true);out.put("visualFixtureNoGravity",true);out.put("authoredWorldBlocks",0);out.put("server",live);out.put("screen",String.valueOf(c.currentScreen));out.put("glRenderer",GL11.glGetString(GL11.GL_RENDERER));out.put("sourceBinding",System.getProperty("drmd.gate.mobGeometrySource","UNBOUND"));Files.writeString(c.runDirectory.toPath().resolve("mob-geometry-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out)+"\n");}catch(Exception error){error.printStackTrace();}System.out.println("MOB_GEOMETRY_OUTCOME "+status+" "+why);c.scheduleStop();
    }
}
