package com.terminaldetector.drmd.gate;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.client.sky.SkyEnvelopeShader;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
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

/** One actual-world Native diagnostic. Readbacks never draw, supply LOD data, or replace a layer. */
public final class NativeSkyLayersCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.skyLayers");
    private static final BlockPos BASE=new BlockPos(8,2200,8);
    private static final double[][] POSES={{0,0,0},{0,-45,25},{90,45,90}};
    private static final String[] NAMES={"level","look-up-banked","look-down-quarter-roll"};
    private static final int STRIDE=4;
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static final List<Map<String,Object>> TRACE=new ArrayList<>();
    private static final List<Vector4f[]> QUADS=new ArrayList<>();
    private static final Map<String,NativeImage> IMAGES=new LinkedHashMap<>();
    private static volatile boolean located,ready,poseReady;
    private static volatile ServerPlayerEntity player;
    private static boolean requested,prepared,done,capturing,worldSky,orbitalScope;
    private static int pose,age,ticks,epoch,celestialPasses;
    private static Matrix4f worldView,worldProjection,envelopeTransform;
    private static float[] envelopeColour;
    private static int envelopeVertices;
    private static float[] envelopeDepth,preCockpitDepth;
    private static int[] envelopeViewport,envelopeScissor;
    private static boolean envelopeScissorEnabled,envelopeMainTarget,envelopeFarDepth,envelopeStateRestored;
    private static Map<String,Object> envelopeStateBefore;
    private static ShaderProgram envelopePreviousShader;
    private static Map<String,Object> mesh=Map.of(),uniforms=Map.of();
    private static Vec3d wantedForward,wantedUp;
    private NativeSkyLayersCheck(){}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeSkyLayersCheck::tick);}
    private static boolean live(){return ENABLED&&capturing&&!done&&!OffscreenWorldView.busy();}
    private static void require(boolean value,String message){if(!value){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("SKY_LAYERS_NATIVE "+message);}}
    private static boolean loaded(net.minecraft.world.World world){
        var access=WorldAccess.of(world);
        for(int x=-1;x<=1;x++)for(int y=(BASE.getY()-16)>>4;y<=(BASE.getY()+16)>>4;y++)for(int z=-1;z<=1;z++)if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void poseInput(MinecraftClient c){
        var p=POSES[pose];wantedForward=Vec3d.fromPolar((float)p[1],(float)p[0]);var right=wantedForward.crossProduct(new Vec3d(0,1,0)).normalize();var up=right.crossProduct(wantedForward).normalize();
        wantedUp=up.multiply(Math.cos(Math.toRadians(p[2]))).add(right.multiply(Math.sin(Math.toRadians(p[2]))));
        poseReady=false;age=0;
        // A single input per pose, through the existing attitude API, never a follow/teleport loop.
        ShipAttitudeClient.acceptPortalFrame(c.player,wantedForward,wantedUp);
        var forward=wantedForward;var rollUp=wantedUp;
        c.getServer().execute(()->{var data=DescentPlayerData.get(player);data.setShipAttitude((float)forward.x,(float)forward.y,(float)forward.z,(float)rollUp.x,(float)rollUp.y,(float)rollUp.z);data.setFlightVelocity(Vec3d.ZERO);player.setYaw((float)p[0]);player.setPitch((float)p[1]);poseReady=true;});
    }
    private static void tick(MinecraftClient c){
        if(done)return;require(++ticks<1800,"timeout pose="+pose+" located="+located+" ready="+ready);
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!requested){
            require(c.runDirectory.getName().equals("cubic-client-automation"),"disposable copied world required");requested=true;c.setScreen(null);
            require(DescentConfig.hybridRender&&DescentConfig.levelSky&&DescentConfig.planetFloor&&DescentConfig.presentationProfile==PresentationProfile.HYBRID_NATIVE,"Native and all existing sky/LOD layers must remain enabled");
            require(c.options.getClampedViewDistance()==4,"controlled render distance4");
            c.getServer().execute(()->{player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());player.stopRiding();player.changeGameMode(GameMode.CREATIVE);player.teleport(player.getServerWorld(),BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5,Set.of(),0,0);player.getServerWorld().setTimeOfDay(6000);FlightSystem.enableWithoutMount(player,DescentPlayerData.get(player));DescentPlayerData.get(player).setFlightVelocity(Vec3d.ZERO);located=true;});return;
        }
        if(located&&!prepared&&loaded(c.world)){prepared=true;c.getServer().execute(()->{require(CubicAvailability.backend(player.getServerWorld())==CubicAvailability.Backend.NATIVE_CUBIC,"actual cubic backend");ready=true;});}
        if(ready&&!poseReady&&wantedForward==null)poseInput(c);
        if(poseReady)age++;
    }
    private static void clearImages(){for(var image:IMAGES.values())image.close();IMAGES.clear();}
    public static void beforeFrame(){
        if(!ENABLED||done)return;epoch++;capturing=false;
        var c=MinecraftClient.getInstance();
        if(!ready||!poseReady||age<8||c.player==null||c.world==null||!DescentClientState.enabled||!ShipAttitudeClient.isPrimed())return;
        if(ShipAttitudeClient.get().forward().dotProduct(wantedForward)<.999||ShipAttitudeClient.get().up().dotProduct(wantedUp)<.999)return;
        clearImages();TRACE.clear();QUADS.clear();mesh=Map.of();uniforms=Map.of();worldView=worldProjection=envelopeTransform=null;envelopeColour=null;envelopeDepth=preCockpitDepth=null;envelopeViewport=envelopeScissor=null;envelopeVertices=celestialPasses=0;worldSky=orbitalScope=envelopeFarDepth=envelopeStateRestored=false;envelopeStateBefore=null;envelopePreviousShader=null;capturing=true;
    }
    private static float[] matrix(Matrix4f m){return m.get(new float[16]);}
    private static Map<String,Object> state(String stage){
        var c=MinecraftClient.getInstance();var d=new LinkedHashMap<String,Object>();int[] viewport=new int[4],scissor=new int[4],polygons=new int[2];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygons);
        d.put("stage",stage);d.put("sequence",TRACE.size());d.put("epoch",epoch);d.put("camera",List.of(c.gameRenderer.getCamera().getPos().x,c.gameRenderer.getCamera().getPos().y,c.gameRenderer.getCamera().getPos().z));d.put("player",List.of(c.player.getX(),c.player.getY(),c.player.getZ()));
        d.put("viewport",viewport);d.put("scissorEnabled",GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));d.put("scissorBox",scissor);d.put("polygonModes",polygons);d.put("mainFbo",c.getFramebuffer().fbo);d.put("drawFbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));d.put("readFbo",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
        d.put("depthTest",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));d.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));d.put("depthFunction",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));d.put("projection",matrix(RenderSystem.getProjectionMatrix()));d.put("modelView",matrix(RenderSystem.getModelViewMatrix()));
        d.put("cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));d.put("blend",GL11.glIsEnabled(GL11.GL_BLEND));d.put("blendFactors",List.of(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)));
        d.put("farDepthSkyShader",SkyEnvelopeShader.program()!=null&&RenderSystem.getShader()==SkyEnvelopeShader.program());
        d.put("farPlane",c.gameRenderer.getFarPlaneDistance());d.put("shaderColour",RenderSystem.getShaderColor().clone());d.put("fogColour",RenderSystem.getShaderFogColor().clone());d.put("fogStart",RenderSystem.getShaderFogStart());d.put("fogEnd",RenderSystem.getShaderFogEnd());d.put("horizonShader",RenderSystem.getShader()==HorizonShader.program());
        d.put("band",WorldLevels.at(c.gameRenderer.getCamera().getPos().y).name());d.put("intentionalVoidEnding",DescentClientState.isVoidEnding());d.put("seedReady",PlanetClientState.INSTANCE.hasSeed());d.put("mesh",mesh);d.put("horizonUniformInput",uniforms);
        try{d.put("bccCaveSkyFactor",Class.forName("dev.fallingcloud.bettercubicchunks.client.CaveSky").getMethod("factor").invoke(null));}catch(ReflectiveOperationException unavailable){d.put("bccCaveSkyFactor","unavailable:"+unavailable.getClass().getSimpleName());}
        return d;
    }
    private static int[] savePack(){int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},values=new int[4];for(int i=0;i<4;i++){values[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?4:0);}return values;}
    private static void restorePack(int[] values){int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS};for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],values[i]);}
    private static NativeImage screenshot(){
        var c=MinecraftClient.getInstance();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int[] pack=savePack();
        try{return ScreenshotRecorder.takeScreenshot(c.getFramebuffer());}finally{GlStateManager._activeTexture(active);RenderSystem.bindTexture(texture);restorePack(pack);}
    }
    private static float[] depthSamples(){
        var c=MinecraftClient.getInstance();int w=c.getFramebuffer().textureWidth,h=c.getFramebuffer().textureHeight,old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);var buffer=MemoryUtil.memAllocFloat(w*h);int[] pack=savePack();
        try{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,c.getFramebuffer().fbo);GL11.glReadPixels(0,0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,buffer);
            float[] result=new float[((w+STRIDE-1)/STRIDE)*((h+STRIDE-1)/STRIDE)];int at=0;
            for(int y=0;y<h;y+=STRIDE)for(int x=0;x<w;x+=STRIDE)result[at++]=buffer.get((h-1-y)*w+x);return result;
        }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);restorePack(pack);MemoryUtil.memFree(buffer);}
    }
    private static void capture(String name){if(live()){var previous=IMAGES.put(name,screenshot());if(previous!=null)previous.close();TRACE.add(state(name));}}
    public static void sky(boolean before,Matrix4f view,Matrix4f projection){
        if(!live())return;worldSky=before;
        if(before){worldView=new Matrix4f(view);worldProjection=new Matrix4f(projection);}
        capture(before?"vanilla-sky-before":"vanilla-sky-after");
    }
    public static void bright(boolean before){if(live()&&worldSky)capture(before?"vanilla-bright-before":"vanilla-bright-after");}
    public static void celestial(boolean before){if(live()&&worldSky){if(before)celestialPasses++;capture("celestial-"+celestialPasses+(before?"-before":"-after"));}}
    public static void brightMatrices(Matrix4f view,Matrix4f projection,ShaderProgram program){
        if(!live()||!worldSky)return;var d=state("actual-vanilla-bright-vbo");d.put("actualPassedView",matrix(view));d.put("actualPassedProjection",matrix(projection));d.put("positionFogShader",program==GameRenderer.getPositionProgram());TRACE.add(d);
    }
    public static void cockpit(boolean before){if(live()){if(before)preCockpitDepth=depthSamples();capture(before?"cockpit-before":"cockpit-after");}}
    public static void planet(boolean before){if(live())capture(before?"planet-function-before":"planet-function-after");}
    public static void prepare(PlanetSurfaceMesh m){
        if(!live())return;int zero=0;for(int colour:m.colours)if((colour&0xffffff)==0)zero++;
        mesh=Map.of("quads",m.quads,"origin",List.of(m.originX,m.originY,m.originZ),"innerRadius",m.innerRadius,"reach",m.reach,"zeroRgbQuads",zero);
    }
    public static void horizonUniform(float x,float y,float z,float inner,float reach){if(live())uniforms=Map.of("originDelta",List.of(x,y,z),"inner",inner,"reach",reach);}
    public static void horizon(boolean before,Matrix4f actualView){
        if(!live())return;capture(before?"lod-draw-before":"lod-draw-after");var d=state(before?"lod-matrix-before":"lod-matrix-after");d.put("actualVertexBufferView",matrix(actualView));TRACE.add(d);
    }
    public static void envelope(boolean before){
        if(!live())return;
        if(before){
            envelopeTransform=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());envelopeDepth=depthSamples();
            envelopeViewport=new int[4];envelopeScissor=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,envelopeViewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,envelopeScissor);envelopeScissorEnabled=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            envelopeMainTarget=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==MinecraftClient.getInstance().getFramebuffer().fbo;
            envelopeFarDepth=SkyEnvelopeShader.program()!=null&&RenderSystem.getShader()==SkyEnvelopeShader.program();
        }
        capture(before?"envelope-draw-before":"envelope-draw-after");
    }
    public static void orbitalScope(boolean before){
        if(!live())return;
        orbitalScope=before;
        if(before){
            envelopeStateBefore=state("envelope-function-before");envelopePreviousShader=RenderSystem.getShader();TRACE.add(envelopeStateBefore);
        }else{
            var after=state("envelope-function-after");var mismatches=new ArrayList<String>();
            for(var key:List.of("viewport","scissorEnabled","scissorBox","polygonModes","drawFbo","readFbo","depthTest","depthWrite","depthFunction","projection","modelView","cull","blend","blendFactors","shaderColour"))
                if(envelopeStateBefore==null||!Objects.deepEquals(envelopeStateBefore.get(key),after.get(key)))mismatches.add(key);
            if(RenderSystem.getShader()!=envelopePreviousShader)mismatches.add("shaderIdentity");
            envelopeStateRestored=mismatches.isEmpty();after.put("restorationMismatches",mismatches);TRACE.add(after);
        }
    }
    public static boolean orbitalDrawing(){return live()&&orbitalScope;}
    public static void envelopeQuad(Matrix4f mat,float[] positions,float r,float g,float b,float a){
        if(!live())return;var points=new Vector4f[4];for(int i=0;i<4;i++)points[i]=new Vector4f(positions[i*3],positions[i*3+1],positions[i*3+2],1).mul(mat);QUADS.add(points);envelopeVertices+=4;
        var colour=RenderSystem.getShaderColor();envelopeColour=new float[]{r*colour[0],g*colour[1],b*colour[2],a*colour[3]};
        var d=state("actual-envelope-quad");d.put("entryPosition",matrix(mat));d.put("sourceVertices",positions);d.put("vertexColour",new float[]{r,g,b,a});TRACE.add(d);
    }
    private static int distance(int a,int b){return Math.max(Math.abs((a&255)-(b&255)),Math.max(Math.abs(((a>>>8)&255)-((b>>>8)&255)),Math.abs(((a>>>16)&255)-((b>>>16)&255))));}
    private static int targetColour(){return ((int)Math.round(envelopeColour[0]*255))|((int)Math.round(envelopeColour[1]*255)<<8)|((int)Math.round(envelopeColour[2]*255)<<16);}
    private static List<double[][]> clippedEnvelope(List<float[][]> rawClips,List<float[][]> shaderClips){
        var result=new ArrayList<double[][]>();var c=MinecraftClient.getInstance();int w=c.getFramebuffer().textureWidth,h=c.getFramebuffer().textureHeight;
        for(var quad:QUADS){
            List<Vector4f> points=new ArrayList<>();float[][] raw=new float[4][4],actual=new float[4][4];
            for(int i=0;i<4;i++){var p=new Vector4f(quad[i]).mul(envelopeTransform);raw[i]=new float[]{p.x,p.y,p.z,p.w};if(envelopeFarDepth)p.z=p.w;actual[i]=new float[]{p.x,p.y,p.z,p.w};points.add(p);}rawClips.add(raw);shaderClips.add(actual);
            for(int plane=0;plane<6&&!points.isEmpty();plane++){
                var next=new ArrayList<Vector4f>();var a=points.getLast();float da=clipDistance(a,plane);
                for(var b:points){float db=clipDistance(b,plane);if((da>=0)!=(db>=0))next.add(new Vector4f(a).lerp(b,da/(da-db)));if(db>=0)next.add(new Vector4f(b));a=b;da=db;}points=next;
            }
            if(points.size()>=3){double[][] polygon=new double[points.size()][2];for(int i=0;i<points.size();i++){var p=points.get(i);polygon[i]=new double[]{envelopeViewport[0]+(p.x/p.w*.5+.5)*envelopeViewport[2],h-(envelopeViewport[1]+(p.y/p.w*.5+.5)*envelopeViewport[3])};}result.add(polygon);}
        }return result;
    }
    private static float clipDistance(Vector4f p,int plane){return switch(plane){case 0->p.w+p.x;case 1->p.w-p.x;case 2->p.w+p.y;case 3->p.w-p.y;case 4->p.w+p.z;default->p.w-p.z;};}
    private static boolean inside(double x,double y,double[][] polygon){
        double area=0;for(int i=0;i<polygon.length;i++)area+=polygon[i][0]*polygon[(i+1)%polygon.length][1]-polygon[(i+1)%polygon.length][0]*polygon[i][1];
        if(Math.abs(area)<1e-7)return false;
        for(int i=0;i<polygon.length;i++){var a=polygon[i];var b=polygon[(i+1)%polygon.length];if(Math.signum(area)*((b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]))<-.01)return false;}return true;
    }
    private static boolean changed(String before,String after,int x,int y){var a=IMAGES.get(before);var b=IMAGES.get(after);return a!=null&&b!=null&&distance(a.getColor(x,y),b.getColor(x,y))>2;}
    private static int sequence(String stage){
        for(int i=TRACE.size()-1;i>=0;i--)if(stage.equals(TRACE.get(i).get("stage")))return ((Number)TRACE.get(i).get("sequence")).intValue();
        return -1;
    }
    private static Map<String,Object> evaluate(){
        var report=new LinkedHashMap<String,Object>();var after=IMAGES.get("envelope-draw-after");var before=IMAGES.get("envelope-draw-before");
        boolean eligible=after!=null&&before!=null&&envelopeColour!=null&&envelopeColour[3]>=.999f&&worldView!=null&&worldProjection!=null&&WorldLevels.at(MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y)==WorldLevels.Level.END;
        report.put("eligibleOpaqueEndEnvelope",eligible);report.put("actualEnvelopeVertices",envelopeVertices);report.put("mesh",mesh);report.put("horizonUniformInput",uniforms);report.put("seedReady",PlanetClientState.INSTANCE.hasSeed());
        report.put("layersEnabled",DescentConfig.hybridRender&&DescentConfig.levelSky&&DescentConfig.planetFloor);report.put("profile",DescentConfig.presentationProfile.name());report.put("cockpitEnabled",DescentConfig.cockpit);
        report.put("expectedSkyDomain",true);report.put("expectedGroundDomain",pose!=1);report.put("envelopeStateRestored",envelopeStateRestored);
        if(!eligible){report.put("setupFailure","Actual opaque END envelope draw or main sky matrices unavailable");return report;}
        var raw=new ArrayList<float[][]>();var shaderClips=new ArrayList<float[][]>();var polygons=clippedEnvelope(raw,shaderClips);
        report.put("actualEnvelopeColour",envelopeColour);report.put("envelopeUnmodifiedClipVertices",raw);report.put("envelopeShaderClipVertices",shaderClips);report.put("actualFarDepthShader",envelopeFarDepth);report.put("clippedEnvelopePolygons",polygons);
        report.put("clipCoverageModel","CPU homogeneous clipping of captured vertices after actual bound shader's far-z rule when present; raw pre-rule clip coordinates retained. GPU snapshots remain authoritative.");
        boolean fullViewport=Arrays.equals(envelopeViewport,new int[]{0,0,after.getWidth(),after.getHeight()});
        report.put("envelopeMainTarget",envelopeMainTarget);report.put("envelopeViewport",envelopeViewport);report.put("envelopeFullViewport",fullViewport);report.put("envelopeScissorEnabled",envelopeScissorEnabled);report.put("envelopeScissor",envelopeScissor);
        int envSequence=sequence("envelope-draw-after"),lodSequence=sequence("lod-draw-after"),cockpitSequence=sequence("cockpit-before");
        boolean envelopeFirst=envSequence>=0&&lodSequence>envSequence;
        String retentionBefore=envelopeFirst?"lod-draw-after":"envelope-draw-before";
        String retentionAfter=envelopeFirst&&cockpitSequence>lodSequence?"cockpit-before":"envelope-draw-after";
        var initial=IMAGES.get(retentionBefore);var terminal=IMAGES.get(retentionAfter);
        var lodA=IMAGES.get("lod-draw-before");var lodB=IMAGES.get("lod-draw-after");
        boolean realLod=lodA!=null&&lodB!=null&&mesh.get("quads") instanceof Number quads&&quads.intValue()>0&&PlanetClientState.INSTANCE.hasSeed();
        boolean retentionEndpoint=initial!=null&&terminal!=null&&(!envelopeFirst||cockpitSequence>lodSequence);
        report.put("actualEnvelopeBeforeLod",envelopeFirst);report.put("envelopeDrawSequence",envSequence);report.put("lodDrawSequence",lodSequence);report.put("preCockpitSequence",cockpitSequence);report.put("realLodDrawReady",realLod);
        report.put("retentionStartStage",retentionBefore);report.put("retentionEndStage",retentionAfter);report.put("retentionEndpointReady",retentionEndpoint);
        var inverse=new Matrix4f(worldProjection).mul(worldView).invert();
        int sky=0,upper=0,nearExcluded=0,cockpitExcluded=0,celestialExcluded=0,bad=0,gaps=0,coveredBad=0,scissorBad=0,lodSamples=0,lodLost=0,lodSkyReplaced=0,at=0,target=targetColour();
        var examples=new ArrayList<Map<String,Object>>();var depths=preCockpitDepth!=null?preCockpitDepth:envelopeDepth;
        report.put("foregroundDepthStage",preCockpitDepth!=null?"cockpit-before":"envelope-draw-before");
        for(int y=0;y<after.getHeight();y+=STRIDE)for(int x=0;x<after.getWidth();x+=STRIDE){
            float depth=depths[at++];double nx=2*(x+.5)/after.getWidth()-1,ny=1-2*(y+.5)/after.getHeight();var near=new Vector4f((float)nx,(float)ny,-1,1).mul(inverse);var far=new Vector4f((float)nx,(float)ny,1,1).mul(inverse);near.div(near.w);far.div(far.w);var ray=far.sub(near);ray.normalize3();
            boolean cockpit=changed("cockpit-before","cockpit-after",x,y),nearGeometry=depth<.999995f,foreground=nearGeometry||cockpit,celestial=false;
            for(int i=1;i<=celestialPasses;i++)celestial|=changed("celestial-"+i+"-before","celestial-"+i+"-after",x,y);
            if(ray.y>.02){upper++;if(nearGeometry)nearExcluded++;if(cockpit)cockpitExcluded++;if(celestial)celestialExcluded++;}
            if(ray.y>.02&&!foreground&&!celestial){
                sky++;boolean covered=false;for(var polygon:polygons)covered|=inside(x+.5,y+.5,polygon);
                if(distance(after.getColor(x,y),target)>16){
                    bad++;if(covered)coveredBad++;else gaps++;
                    boolean scissored=envelopeScissorEnabled&&(x<envelopeScissor[0]||x>=envelopeScissor[0]+envelopeScissor[2]||after.getHeight()-1-y<envelopeScissor[1]||after.getHeight()-1-y>=envelopeScissor[1]+envelopeScissor[3]);if(scissored)scissorBad++;
                    if(examples.size()<64)examples.add(Map.of("x",x,"y",y,"beforeRgb",Integer.toUnsignedString(before.getColor(x,y),16),"afterRgb",Integer.toUnsignedString(after.getColor(x,y),16),"insideClippedEnvelope",covered,"outsideScissor",scissored));
                }
            }
            if(ray.y<-.02&&realLod&&retentionEndpoint&&!foreground&&distance(lodA.getColor(x,y),lodB.getColor(x,y))>8&&distance(lodB.getColor(x,y),initial.getColor(x,y))<=2){
                lodSamples++;
                if(distance(initial.getColor(x,y),terminal.getColor(x,y))>8){lodLost++;if(distance(terminal.getColor(x,y),target)<=16)lodSkyReplaced++;}
            }
        }
        report.put("upperWorldRayCandidates",upper);report.put("upperNearGeometryExcluded",nearExcluded);report.put("upperCockpitExcluded",cockpitExcluded);report.put("upperCelestialExcluded",celestialExcluded);report.put("actualCelestialPasses",celestialPasses);
        report.put("upperSkySamples",sky);report.put("unsatisfiedSkySamples",bad);report.put("outsideClippedEnvelopeGaps",gaps);report.put("coveredColourFailures",coveredBad);report.put("outsideScissorFailures",scissorBad);report.put("skyMismatchFraction",sky==0?1:(double)bad/sky);report.put("skyGateMeasured",sky>=2000);report.put("skyGatePass",sky>=2000&&(double)bad/sky<=.01&&envelopeMainTarget&&fullViewport);report.put("skyMismatchExamples",examples);
        boolean measured=realLod&&retentionEndpoint&&lodSamples>=200;
        boolean retained=measured&&(double)lodLost/lodSamples<=.01;
        report.put("isolatedLodRetentionSamples",lodSamples);report.put("lodLostAfterBackground",lodLost);report.put("lodReplacedByEnvelopeColour",lodSkyReplaced);
        report.put("lodReadiness",!realLod?"unavailable-no-real-LOD-draw":!retentionEndpoint?"unavailable-retention-endpoint":!measured?"unavailable-no-isolated-lower-LOD-domain":"measured");report.put("lodGateMeasured",measured);
        report.put("lodRetentionVerdict",measured?(retained?"pass":"fail"):"unmeasured");report.put("lodRetentionPass",retained);
        return report;
    }
    public static void rendered(){
        if(!live())return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null)return;
        capture("final");var result=evaluate();result.put("pose",NAMES[pose]);result.put("requestedYawPitchRoll",POSES[pose]);result.put("epoch",epoch);result.put("trace",new ArrayList<>(TRACE));result.put("glError",GL11.glGetError());
        try{
            var folder=c.runDirectory.toPath().resolve("sky-layer-frames").resolve(NAMES[pose]);Files.createDirectories(folder);
            for(var entry:IMAGES.entrySet())entry.getValue().writeTo(folder.resolve(entry.getKey()+".png"));
            Files.writeString(folder.resolve("attribution.json"),new com.google.gson.Gson().toJson(result));RESULTS.add(result);
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        capturing=false;clearImages();
        if(++pose<POSES.length){wantedForward=null;poseReady=false;age=0;return;}
        boolean anySky=false,anyLod=false,allSky=true,allLod=true,allGl=true,allOrder=true,allRestored=true,allReady=true;
        boolean layersUnchanged=true;int measuredGround=0;
        for(var r:RESULTS){
            boolean skyMeasured=Boolean.TRUE.equals(r.get("skyGateMeasured")),lodMeasured=Boolean.TRUE.equals(r.get("lodGateMeasured")),groundExpected=Boolean.TRUE.equals(r.get("expectedGroundDomain"));
            anySky|=skyMeasured;anyLod|=lodMeasured;
            allSky&=skyMeasured&&Boolean.TRUE.equals(r.get("skyGatePass"));
            if(groundExpected){allLod&=lodMeasured&&Boolean.TRUE.equals(r.get("lodRetentionPass"));if(lodMeasured)measuredGround++;}
            else if(lodMeasured)allLod&=Boolean.TRUE.equals(r.get("lodRetentionPass"));
            allGl&=((Number)r.get("glError")).intValue()==0;allOrder&=Boolean.TRUE.equals(r.get("actualEnvelopeBeforeLod"));allRestored&=Boolean.TRUE.equals(r.get("envelopeStateRestored"));allReady&=Boolean.TRUE.equals(r.get("realLodDrawReady"));
            layersUnchanged&=Boolean.TRUE.equals(r.get("layersEnabled"))&&Boolean.TRUE.equals(r.get("cockpitEnabled"))&&PresentationProfile.HYBRID_NATIVE.name().equals(r.get("profile"));
        }
        var report=new LinkedHashMap<String,Object>();report.put("allPoseFramesPreserved",true);report.put("anyMeasuredSkyDomain",anySky);report.put("anyMeasuredLodDomain",anyLod);report.put("expectedGroundDomainsMeasured",measuredGround);report.put("skyPass",allSky);report.put("lodRetentionPass",allLod&&measuredGround==2);report.put("allGlZero",allGl);report.put("allEnvelopeBeforeLod",allOrder);report.put("allEnvelopeStateRestored",allRestored);report.put("allActualLodDrawsReady",allReady);report.put("sourceLayersUnchanged",layersUnchanged);report.put("poses",RESULTS);report.put("thresholds",Map.of("minUpperSkySamples",2000,"maxSkyMismatchFraction",.01,"maxRgbDistance",16,"minLodSamples",200,"maxLodLossFraction",.01,"sampleStride",STRIDE));
        boolean passed=allSky&&allLod&&measuredGround==2&&allGl&&allOrder&&allRestored&&allReady&&layersUnchanged;
        try{Files.writeString(c.runDirectory.toPath().resolve(passed?"sky-layers-ok.json":"sky-layers-negative.json"),new com.google.gson.Gson().toJson(report));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        require(passed,"actual sky coverage/colour and independent LOD retention: sky="+allSky+", lod="+allLod+", measuredGround="+measuredGround+", order="+allOrder+", state="+allRestored+", ready="+allReady+", GL="+allGl+", layers="+layersUnchanged);done=true;c.scheduleStop();
    }
}
