package com.terminaldetector.drmd.gate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.planet.HorizonShader;
import com.terminaldetector.drmd.client.presentation.Ps1Atmosphere;
import com.terminaldetector.drmd.client.presentation.Ps1FarFogOptions;
import com.terminaldetector.drmd.client.presentation.Ps1WorldFog;
import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.fixture.city.CityBlueprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Samples actual main-world pixels during the unchanged native flight input/movement harness. */
final class NativeRingAscentWitness {
    private static final int[] HEIGHTS={155,320,450,700,1020,1300,1440};
    private static final BlockPos SURFACE_TILE=new BlockPos(197,68,255);
    private static final BlockPos RING_TILE=new BlockPos(192,1416,256);
    private static final int SIZE=5;
    private static final int MAX_SAMPLES=1024;
    private static final int[] PACK={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS};
    private record Pixels(int[] rgba,float[] depth) {}
    private static Pixels before;
    private static Map<String,Object> pending,firstFailure=Map.of(),firstUnreadySurface=Map.of(),admittedSurface=Map.of();
    private static int surfaceDrawAttempts,consecutiveSurfaceFrames;
    private static final List<Map<String,Object>> frames=new ArrayList<>(),landmarks=new ArrayList<>();
    private static int sampleAttempt,qualifiedRing,maxMissing,missing;
    private static double lastEye=Double.NaN,maxHeightGap;

    private NativeRingAscentWitness(){}

    static void configure(MinecraftClient c){
        c.options.getViewDistance().setValue(4);
        c.options.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
        c.options.getFov().setValue(70);
        DescentConfig.ps1SceneHeight=360;
        DescentConfig.ps1FarFog=new Ps1FarFogOptions(1);
    }

    static void beforeFrame(){before=null;pending=null;}

    private static void countDraw(boolean staging){
        if(staging){require(++surfaceDrawAttempts<=MAX_SAMPLES,"bounded stationary native surface draws");return;}
        require(++sampleAttempt<=MAX_SAMPLES,"bounded actual ascent world draws");
    }

    static void beforeHorizon(boolean staging){
        var c=MinecraftClient.getInstance();
        countDraw(staging);
        int w=c.getFramebuffer().textureWidth,h=c.getFramebuffer().textureHeight;
        before=read(c,w/2-SIZE/2,h/2-SIZE/2,SIZE,SIZE);
    }

    static void afterHorizon(Matrix4f view,boolean staging){
        if(before==null)return;
        var c=MinecraftClient.getInstance();
        var eye=c.gameRenderer.getCamera().getPos();
        int w=c.getFramebuffer().textureWidth,h=c.getFramebuffer().textureHeight;
        var after=read(c,w/2-SIZE/2,h/2-SIZE/2,SIZE,SIZE);
        int resident=0,horizon=0,uncovered=0;
        for(int i=0;i<before.depth.length;i++){
            require(Float.isFinite(before.depth[i])&&before.depth[i]==after.depth[i],"actual native depth changed during the ground LOD draw");
            if(before.depth[i]<.999995f) resident++;
            else if((before.rgba[i]&0xffffff)!=(after.rgba[i]&0xffffff))horizon++;
            else uncovered++;
        }
        int program=HorizonShader.program().getGlRef(),at=GL20.glGetUniformLocation(program,"Ps1FarFogDensity");
        require(at>=0,"actual linked PS1 distance-fog uniform");
        float[] density={0};GL20.glGetUniformfv(program,at,density);
        require(Math.abs(density[0]-Ps1Atmosphere.density(eye.y,1))<1e-5,"live world fog density differs from altitude curve");
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("eye",List.of(eye.x,eye.y,eye.z));
        row.put("residentPixels",resident);
        row.put("horizonPixels",horizon);
        row.put("uncoveredPixels",uncovered);
        row.put("actualFogDensity",density[0]);
        row.put("actualSceneFbo",c.getFramebuffer().fbo);
        if(eye.y>=1431&&eye.y<=1485)row.put("westRingDeck",ringDeck(c,view,eye));
        if(staging)row.putAll(surfacePixels(c,view,eye,before));
        pending=Map.copyOf(row);
        if(!staging&&uncovered>0&&firstFailure.isEmpty())firstFailure=pending;
    }

    static void residentSurfaceWithoutHorizon(Matrix4f view,boolean staging){
        if(pending!=null||before!=null)return;
        var c=MinecraftClient.getInstance();
        var eye=c.gameRenderer.getCamera().getPos();
        if(eye.y>=320||!ScenePresentation.inLowResolutionWorld())return;
        countDraw(staging);
        int w=c.getFramebuffer().textureWidth,h=c.getFramebuffer().textureHeight;
        var sample=read(c,w/2-SIZE/2,h/2-SIZE/2,SIZE,SIZE);
        int resident=0;
        for(float depth:sample.depth)if(depth<.999995f)resident++;
        var row=new LinkedHashMap<String,Object>(Map.of("eye",List.of(eye.x,eye.y,eye.z),"residentPixels",resident,"horizonPixels",0,
            "uncoveredPixels",SIZE*SIZE-resident,"actualSceneFbo",c.getFramebuffer().fbo,
            "nativeTerrainWithoutHorizon",true,"styledWorldFogActive",Ps1WorldFog.active()));
        if(staging)row.putAll(surfacePixels(c,view,eye,sample));
        pending=Map.copyOf(row);
        if(!staging&&resident!=SIZE*SIZE&&firstFailure.isEmpty())firstFailure=pending;
    }

    static boolean nativeSurfacePresent(net.minecraft.world.World world){
        return WorldAccess.of(world).readIfLoaded(SURFACE_TILE)
            .filter(state->state.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)).isPresent();
    }

    private static Map<String,Object> surfacePixels(MinecraftClient c,Matrix4f view,Vec3d eye,Pixels sample){
        var state=WorldAccess.of(c.world).readIfLoaded(SURFACE_TILE);
        var point=Vec3d.ofCenter(SURFACE_TILE).add(0,.5,0).subtract(eye);
        var clip=new Vector4f((float)point.x,(float)point.y,(float)point.z,1)
            .mul(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view));
        require(clip.isFinite()&&clip.w>0,"actual stationary grass upper face projects in front of the native camera");
        float expected=clip.z/clip.w*.5f+.5f;
        int matches=0;
        for(float depth:sample.depth)if(depth<.999995f&&Math.abs(depth-expected)<=.00002f)matches++;
        return Map.of("nativeSurfacePosition",List.of(SURFACE_TILE.getX(),SURFACE_TILE.getY(),SURFACE_TILE.getZ()),
            "nativeSurfaceMaterial",state.map(s->Registries.BLOCK.getId(s.getBlock()).toString()).orElse("unloaded"),
            "surfaceExpectedDepth",expected,"surfaceDepth",sample.depth,"surfacePixelsAtNativeDepth",matches);
    }

    static boolean admitStationarySurface(MinecraftClient c){
        if(pending==null){consecutiveSurfaceFrames=0;return false;}
        requireScene();
        if(!"minecraft:grass_block".equals(pending.get("nativeSurfaceMaterial"))
            ||!Integer.valueOf(SIZE*SIZE).equals(pending.get("surfacePixelsAtNativeDepth"))){
            if(firstUnreadySurface.isEmpty())firstUnreadySurface=pending;
            consecutiveSurfaceFrames=0;
            return false;
        }
        if(++consecutiveSurfaceFrames<3)return false;
        admittedSurface=pending;
        capture(c,"stationary-native-surface");
        return true;
    }

    private static Map<String,Object> ringDeck(MinecraftClient c,Matrix4f view,Vec3d eye){
        var state=WorldAccess.of(c.world).readIfLoaded(RING_TILE);
        if(state.isEmpty()||!Registries.BLOCK.getId(state.get().getBlock()).toString().equals(CityBlueprint.TILE))return Map.of();
        // The physical west tile is beside the clear flight line, sampled on its real upper face.
        var point=Vec3d.ofCenter(RING_TILE).add(0,.5,0).subtract(eye);
        var clip=new Vector4f((float)point.x,(float)point.y,(float)point.z,1)
            .mul(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view));
        if(!Float.isFinite(clip.w)||clip.w<=0||Math.abs(clip.x/clip.w)>.925f||Math.abs(clip.y/clip.w)>.925f)return Map.of();
        float expected=clip.z/clip.w*.5f+.5f;
        int x=(int)((clip.x/clip.w*.5f+.5f)*c.getFramebuffer().textureWidth);
        int y=(int)((clip.y/clip.w*.5f+.5f)*c.getFramebuffer().textureHeight);
        var sample=read(c,x-1,y-1,3,3);
        int matched=0;
        for(float d:sample.depth)if(d<.999995f&&Math.abs(d-expected)<=.00002f)matched++;
        return Map.of("block",List.of(RING_TILE.getX(),RING_TILE.getY(),RING_TILE.getZ()),"state",CityBlueprint.TILE,
            "projectedPixel",List.of(x,y),"expectedDepth",expected,"actualDepth",sample.depth,"matchingDrawnPixels",matched);
    }

    static boolean rendered(MinecraftClient c,Vec3d eye){
        if(pending==null){
            maxMissing=Math.max(maxMissing,++missing);
            throw new IllegalStateException("airborne frame has neither native surface depth nor an actual Horizon draw at "+eye);
        }
        requireScene();
        require((Integer)pending.get("uncoveredPixels")==0,"ground has an uncovered real scene pixel at "+pending);
        if(!Double.isFinite(lastEye)){
            double departureEye=((List<Double>)admittedSurface.get("eye")).get(1);
            require(eye.y>=departureEye-.3&&eye.y<=departureEye+4,"first covered flight frame must follow the admitted stationary surface");
        }else{
            require(eye.y>=lastEye-.3,"non-monotonic physical ascent");
            maxHeightGap=Math.max(maxHeightGap,eye.y-lastEye);
            require(maxHeightGap<=48,"unsampled altitude gap exceeds 48 blocks");
        }
        lastEye=eye.y;
        frames.add(pending);
        if(landmarks.size()<HEIGHTS.length&&eye.y>=HEIGHTS[landmarks.size()]){
            int height=HEIGHTS[landmarks.size()];
            require(eye.y<=height+48,"altitude checkpoint was bypassed");
            capture(c,"y"+height);
            landmarks.add(pending);
        }
        Object deck=pending.get("westRingDeck");
        if(deck instanceof Map<?,?> ring&&ring.get("matchingDrawnPixels") instanceof Integer n&&n>=3){
            if(++qualifiedRing==1)capture(c,"actual-west-ring-deck");
        }
        return landmarks.size()==HEIGHTS.length&&qualifiedRing>=3;
    }

    private static void requireScene(){
        var scene=ScenePresentation.stats();
        require(scene.sceneWidth()<scene.outputWidth()&&scene.sceneHeight()==360&&!scene.inWorldPass()
            &&scene.fallbackFrames()==0&&GL11.glGetError()==GL11.GL_NO_ERROR,"actual completed low-resolution world scene");
    }

    private static void capture(MinecraftClient c,String name){
        var folder=c.runDirectory.toPath().resolve("ring-ascent-frames");
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            Files.createDirectories(folder);image.writeTo(folder.resolve(name+".png"));
        }catch(java.io.IOException problem){throw new IllegalStateException("cannot preserve actual ascent frame",problem);}
    }

    private static Pixels read(MinecraftClient c,int x,int y,int width,int height){
        int fbo=c.getFramebuffer().fbo;
        require(ScenePresentation.inLowResolutionWorld()&&GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==fbo,
            "actual bound low-resolution world scene required");
        require(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)==0,"foreign pixel pack during native ascent");
        int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] pack=new int[PACK.length];
        for(int i=0;i<PACK.length;i++){pack[i]=GL11.glGetInteger(PACK[i]);GL11.glPixelStorei(PACK[i],i==0?4:0);}
        try(var stack=MemoryStack.stackPush()){
            int count=width*height;
            var rgba=stack.malloc(count*4);var z=stack.mallocFloat(count);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
            GL11.glReadPixels(x,y,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);
            GL11.glReadPixels(x,y,width,height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,z);
            var result=new Pixels(new int[count],new float[count]);
            for(int i=0;i<count;i++)result.rgba[i]=(Byte.toUnsignedInt(rgba.get(i*4))<<16)
                |(Byte.toUnsignedInt(rgba.get(i*4+1))<<8)|Byte.toUnsignedInt(rgba.get(i*4+2));
            z.get(result.depth);
            return result;
        }finally{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);
            for(int i=0;i<PACK.length;i++)GL11.glPixelStorei(PACK[i],pack[i]);
        }
    }

    private static void require(boolean valid,String why){if(!valid)throw new IllegalStateException(why);}

    static Map<String,Object> report(){
        return Map.ofEntries(Map.entry("actualCompletedWorldFrames",frames),Map.entry("altitudeCaptures",landmarks),
            Map.entry("actualDrawAttempts",sampleAttempt),Map.entry("maximumAltitudeGap",maxHeightGap),
            Map.entry("maximumMissingFrames",maxMissing),Map.entry("actualRingDeckFrames",qualifiedRing),
            Map.entry("firstUncoveredFrame",firstFailure),Map.entry("authoredWorldWrites",0),
            Map.entry("stationarySurfaceDrawAttempts",surfaceDrawAttempts),Map.entry("consecutiveStationarySurfaceFrames",consecutiveSurfaceFrames),
            Map.entry("firstUnreadyStationarySurface",firstUnreadySurface),Map.entry("admittedStationaryNativeSurface",admittedSurface));
    }
}
