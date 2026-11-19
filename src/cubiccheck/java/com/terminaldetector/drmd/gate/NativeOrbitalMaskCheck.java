package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.client.cloud.AltitudeCloudRenderer;
import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.opengl.GL20;
import java.nio.file.Files;
import java.util.*;

/** Passive GPU-input observer. Does not create scenes, move cameras/actors, progress or stop a driver. */
public final class NativeOrbitalMaskCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.orbitalMask");
    private record Draw(long epoch,PlanetSurfaceMesh source,float[] mask,Set<String> domains,int currentMarks,int removedMarks) {}
    private static Draw colour,semantic;
    private static final Map<String,Integer> COUNTS=new LinkedHashMap<>();
    private static final List<Map<String,Object>> RECEIPTS=new ArrayList<>();
    private static final Set<String> SHOTS=new HashSet<>();
    private static long lastEpoch=-1;private static volatile String failed;
    private record Region(String name,int x,int y,int width,int height,byte[] rgba,float[] depth) {}
    private record PixelPending(int stage,long epoch,PlanetSurfaceMesh source,List<Region> regions,int fullCubes,int totalCubes,double distance,int fbo) {}
    private record Pixels(int stage,long epoch,Map<String,Object> receipt) {}
    private static PixelPending pixelPending;
    private static Pixels pixels;
    private static final int[] CASE_FRAMES=new int[4];
    private static final List<Map<String,Object>> CASE_RECEIPTS=new ArrayList<>();
    private static volatile int fieldViewBits;
    private static volatile Map<String,Object> lastViewAdmission=Map.of();
    private static float inputInner,inputReach;private static long inputEpoch=-1,readbackBytes;
    private static int viewCase(){return NativeIntactAsteroidCheck.ENABLED?NativeIntactAsteroidCheck.viewCase():NativeOrbitalMissionCheck.fieldViewCase();}
    public static boolean fieldViewObserved(int stage){return stage>=0&&stage<4&&(fieldViewBits&(1<<stage))!=0;}
    public static boolean allFieldViewsObserved(){return fieldViewBits==15;}
    public static String fieldViewFailure(){return failed;}
    public static Map<String,Object> fieldViewDiagnostic(){return lastViewAdmission;}
    private static void admission(int stage,String reason,Object... values){
        var row=new LinkedHashMap<String,Object>();row.put("case",stage);row.put("reason",reason);
        var c=MinecraftClient.getInstance();if(c.world!=null){row.put("actualWorldTick",c.world.getTime());var eye=c.gameRenderer.getCamera().getPos();row.put("actualEye",List.of(eye.x,eye.y,eye.z));}
        for(int i=0;i<values.length;i+=2)row.put((String)values[i],values[i+1]);lastViewAdmission=Map.copyOf(row);
    }
    public static void input(float inner,float reach){if(ENABLED){inputInner=inner;inputReach=reach;inputEpoch=AltitudeCloudRenderer.stats().epoch();}}
    public static void beforeFrame(){pixelPending=null;pixels=null;}
    private static void budget(long bytes){readbackBytes+=bytes;require(readbackBytes<=64L*1024*1024,"orbital native readback64MiB cap");}
    /** Only the explicit post-success real-flight case is pixel-scanned; ordinary passive mode stays passive. */
    public static void beforeColour(PlanetSurfaceMesh source,org.joml.Matrix4f view){
        if(!ENABLED||failed!=null)return;int stage=viewCase();
        if(stage<0||CASE_FRAMES[stage]>=8)return;
        var c=MinecraftClient.getInstance();if(c.world==null||source==null||OffscreenWorldView.busy())return;
        try{
            long epoch=AltitudeCloudRenderer.stats().epoch();var eye=c.gameRenderer.getCamera().getPos();
            if(inputEpoch!=epoch||source.stale(eye.x,eye.y,eye.z,inputInner,inputReach)){admission(stage,"AWAITING_CURRENT_SOURCE", "inputEpoch",inputEpoch,"epoch",epoch);return;}
            boolean asteroid=NativeIntactAsteroidCheck.ENABLED;
            var centre=asteroid?NativeIntactAsteroidCheck.centre():NativeOrbitalMissionCheck.fieldViewStation();
            var marks=PlanetClientState.INSTANCE.landmarksSnapshot().stream().filter(m->HorizonOrbitalProxy.authored(m.kind())).toList();
            HorizonLandmark mark;
            if(asteroid){
                var selected=marks.stream().filter(NativeIntactAsteroidCheck::selected).toList();
                if(marks.size()!=3||selected.size()!=1){admission(stage,"AWAITING_THREE_ORIGINAL_CERTIFICATES_AND_OUTER_ASTEROID","actualAuthoredMarks",marks.size());return;}
                mark=selected.get(0);
            }else{
                if(marks.size()!=1){admission(stage,"AWAITING_ONE_CERTIFIED_STATION","actualAuthoredMarks",marks.size());return;}mark=marks.get(0);
                require(mark.kind()==com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.ORBITAL_STATION&&mark.x()==centre.x&&mark.y()==centre.y&&mark.z()==centre.z
                    &&mark.sizeX()==24&&mark.sizeY()==16&&mark.sizeZ()==20,"same one certified physical station survives actual mining");
            }
            int halfX=mark.sizeX()/2,halfY=mark.sizeY()/2,halfZ=mark.sizeZ()/2;
            if(source.orbitalKeys.stream().noneMatch(k->k.mark().equals(mark))){admission(stage,"AWAITING_CERTIFIED_SOURCE_KEYS","actualSlots",source.orbitalKeys.size());return;}
            var access=WorldAccess.of(c.world);int full=0,total=0;
            for(int x=Math.floorDiv(mark.x()-halfX,16);x<=Math.floorDiv(mark.x()+halfX-1,16);x++)
                for(int y=Math.floorDiv(mark.y()-halfY,16);y<=Math.floorDiv(mark.y()+halfY-1,16);y++)
                    for(int z=Math.floorDiv(mark.z()-halfZ,16);z<=Math.floorDiv(mark.z()+halfZ-1,16);z++) {
                        total++;if(access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))full++;
                    }
            require(total<=27,"actual station AABB cube cap27");
            boolean near=stage==0||stage==3;
            if(near?full!=total:full!=0){admission(stage,"AWAITING_NORMAL_NATIVE_RESIDENCY","actualFullAabbCubes",full,"aabbCubes",total);return;} // Never force an unload.
            double distance=eye.distanceTo(centre);int viewBlocks=c.options.getClampedViewDistance()*16;
            require(near?distance<=64:distance>viewBlocks+128&&((stage==1&&distance<=2048)||(stage==2&&distance>2048&&distance<8192)),"actual near/MID/FAR distances independent of ground Y");
            int[] viewport=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);
            require(viewport[0]==0&&viewport[1]==0&&viewport[2]==c.getFramebuffer().textureWidth&&viewport[3]==c.getFramebuffer().textureHeight,"actual orbital scene viewport matches projection target");
            int fbo=c.getFramebuffer().fbo;require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)==fbo,"actual current scene framebuffer owns orbital draw");
            List<Region> regions=new ArrayList<>();
            if(near){
                int[] opaque=project(view,asteroid?centre.add(.5,.5,-halfZ):centre.add(3.5,-3.5,-9),false,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
                int[] hole=asteroid?null:project(view,centre.add(.5,-1.5,.5),false,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
                if(opaque==null||(!asteroid&&hole==null)){admission(stage,"NEAR_TARGET_OUTSIDE_CURRENT_VIEW");return;}
                regions.add(read("opaque",opaque[0]-2,opaque[1]-2,5,5,fbo));
                if(!asteroid)regions.add(read("open-window",hole[0]-2,hole[1]-2,5,5,fbo));
                if(java.util.stream.IntStream.range(0,25).noneMatch(i->regions.get(0).depth[i]<.999995f)){admission(stage,"AWAITING_ACTUAL_OPAQUE_DEPTH","actualFullAabbCubes",full);return;}
                if(!asteroid&&java.util.stream.IntStream.range(0,25).anyMatch(i->regions.get(1).depth[i]<.999995f)){admission(stage,"EXPECTED_OPEN_WINDOW_HAS_NEAR_DEPTH","actualFullAabbCubes",full);return;}
            }else{
                int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE;
                for(int x:new int[]{-halfX,halfX})for(int y:new int[]{-halfY,halfY})for(int z:new int[]{-halfZ,halfZ}){
                    int[] at=project(view,centre.add(x,y,z),true,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);if(at==null){admission(stage,"DISTANT_STATION_OUTSIDE_CURRENT_VIEW");return;}
                    x0=Math.min(x0,at[0]);x1=Math.max(x1,at[0]);y0=Math.min(y0,at[1]);y1=Math.max(y1,at[1]);
                }
                int pad=asteroid?0:2; // The tiny asteroid witness owns only its actual projected footprint.
                if(asteroid){
                    // A station/other rock cannot stand in for the selected asteroid's tiny FAR footprint.
                    for(var other:marks)if(!other.equals(mark)){
                        int ox0=Integer.MAX_VALUE,oy0=Integer.MAX_VALUE,ox1=Integer.MIN_VALUE,oy1=Integer.MIN_VALUE;boolean projected=true;
                        for(int x:new int[]{-other.sizeX()/2,other.sizeX()/2})for(int y:new int[]{-other.sizeY()/2,other.sizeY()/2})for(int z:new int[]{-other.sizeZ()/2,other.sizeZ()/2}){
                            var at=project(view,new net.minecraft.util.math.Vec3d(other.x()+x,other.y()+y,other.z()+z),true,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
                            if(at==null){projected=false;continue;}ox0=Math.min(ox0,at[0]);ox1=Math.max(ox1,at[0]);oy0=Math.min(oy0,at[1]);oy1=Math.max(oy1,at[1]);
                        }
                        if(projected&&ox1>=x0-pad&&ox0<=x1+pad&&oy1>=y0-pad&&oy0<=y1+pad){admission(stage,"OTHER_CERTIFIED_OBJECT_OVERLAPS_SELECTED_ASTEROID_ROI");return;}
                    }
                }
                regions.add(read("authored-proxy",x0-pad,y0-pad,x1-x0+1+pad*2,y1-y0+1+pad*2,fbo));
                for(float depth:regions.get(0).depth)if(depth<.999995f){admission(stage,"DISTANT_PROXY_ROI_HAS_REAL_FOREGROUND","actualFullAabbCubes",full);return;}
            }
            admission(stage,"ACTUAL_PIXEL_INPUTS_ADMITTED","actualFullAabbCubes",full,"aabbCubes",total,"trueDistance",distance,"sceneFbo",fbo);
            pixelPending=new PixelPending(stage,epoch,source,List.copyOf(regions),full,total,distance,fbo);
        }catch(Throwable problem){fail(problem);}
    }
    private static int[] project(org.joml.Matrix4f view,net.minecraft.util.math.Vec3d world,boolean compressed,int width,int height){
        var eye=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();var vector=world.subtract(eye);
        double factor=compressed?HorizonProjection.factor(vector.x,vector.y,vector.z,inputInner,inputReach):1;
        var clip=new org.joml.Vector4f((float)(vector.x*factor),(float)(vector.y*factor),(float)(vector.z*factor),1)
            .mul(new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(view));
        if(!Float.isFinite(clip.w)||clip.w<=0||Math.abs(clip.x/clip.w)>.97||Math.abs(clip.y/clip.w)>.97)return null;
        return new int[]{(int)Math.floor((clip.x/clip.w*.5+.5)*width),(int)Math.floor((clip.y/clip.w*.5+.5)*height)};
    }
    private static Region read(String name,int x,int y,int width,int height,int fbo){
        var c=MinecraftClient.getInstance();require(width>0&&height>0&&(long)width*height<=4096&&x>=0&&y>=0
            &&x+width<=c.getFramebuffer().textureWidth&&y+height<=c.getFramebuffer().textureHeight,"bounded on-screen actual station ROI");
        require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING)==0,"no foreign pixel-pack buffer during native readback");
        int count=width*height;budget(count*8L);var rgba=org.lwjgl.system.MemoryUtil.memAlloc(count*4);var depth=org.lwjgl.system.MemoryUtil.memAllocFloat(count);
        int old=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] names={org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT,org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH,org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS,org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS},saved=new int[4];
        for(int i=0;i<4;i++){saved[i]=org.lwjgl.opengl.GL11.glGetInteger(names[i]);org.lwjgl.opengl.GL11.glPixelStorei(names[i],i==0?4:0);}
        try{
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,fbo);
            org.lwjgl.opengl.GL11.glReadPixels(x,y,width,height,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,rgba);
            org.lwjgl.opengl.GL11.glReadPixels(x,y,width,height,org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT,org.lwjgl.opengl.GL11.GL_FLOAT,depth);
            byte[] colour=new byte[count*4];rgba.get(colour);float[] z=new float[count];depth.get(z);return new Region(name,x,y,width,height,colour,z);
        }finally{org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,old);for(int i=0;i<4;i++)org.lwjgl.opengl.GL11.glPixelStorei(names[i],saved[i]);org.lwjgl.system.MemoryUtil.memFree(rgba);org.lwjgl.system.MemoryUtil.memFree(depth);}
    }
    private static void afterColourPixels(PlanetSurfaceMesh source,long epoch){
        var pending=pixelPending;pixelPending=null;if(pending==null||pending.epoch!=epoch||pending.source!=source)return;
        if(viewCase()!=pending.stage)return;
        require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)==pending.fbo,"same actual scene target after orbital colour draw");
        var regionRows=new ArrayList<Map<String,Object>>();boolean near=pending.stage==0||pending.stage==3;
        for(var before:pending.regions){var after=read(before.name,before.x,before.y,before.width,before.height,pending.fbo);int changed=0;
            for(int i=0;i<before.depth.length;i++){
                require(Math.abs(before.depth[i]-after.depth[i])<.000001,"orbital backdrop cannot alter native foreground/sky depth");
                if(before.rgba[i*4]!=after.rgba[i*4]||before.rgba[i*4+1]!=after.rgba[i*4+1]||before.rgba[i*4+2]!=after.rgba[i*4+2])changed++;
            }
            require(near?changed==0:changed>=(pending.stage==1?4:1),near?"near opaque/window pixels were painted by a far proxy/phantom surface":"actual MID/FAR authored station produced no colour pixels beyond residency");
            regionRows.add(Map.of("name",before.name,"rect",List.of(before.x,before.y,before.width,before.height),"changedPixels",changed,"depthUnchanged",true));
        }
        pixels=new Pixels(pending.stage,epoch,Map.of("case",pending.stage,"actualDistance",pending.distance,"clientFullAabbCubes",pending.fullCubes,
            "aabbCubes",pending.totalCubes,"actualSceneFbo",pending.fbo,"regions",regionRows));
    }
    private static void fieldViewReceipt(long epoch) throws Exception{
        if(pixels==null||pixels.epoch!=epoch)return;var pixel=pixels;pixels=null;
        if(CASE_FRAMES[pixel.stage]>=8)return;
        require(CASE_RECEIPTS.size()<32,"four native view cases x8 actual frames cap32");var receipt=new LinkedHashMap<String,Object>(pixel.receipt);var client=MinecraftClient.getInstance();var eye=client.gameRenderer.getCamera().getPos();
        receipt.put("epoch",epoch);receipt.put("actualWorldTick",client.world.getTime());receipt.put("actualEye",List.of(eye.x,eye.y,eye.z));
        receipt.put("colourSemanticSameSourceAndMask",true);receipt.put("sourceIdentity",System.identityHashCode(colour.source));receipt.put("sourceOrigin",List.of(colour.source.originX,colour.source.originY,colour.source.originZ));
        receipt.put("actualQuads",colour.source.quads);CASE_RECEIPTS.add(receipt);
        if(++CASE_FRAMES[pixel.stage]==8)fieldViewBits|=1<<pixel.stage;
        if(CASE_FRAMES[pixel.stage]==1){
            var c=MinecraftClient.getInstance();var folder=c.runDirectory.toPath().resolve("orbital-mask-frames");Files.createDirectories(folder);
            screenshot(folder.resolve((NativeIntactAsteroidCheck.ENABLED?"intact-asteroid-":"")+"physical-view-"+pixel.stage+".png"));
        }
    }
    private NativeOrbitalMaskCheck(){}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    public static void draw(PlanetSurfaceMesh source,VertexFormat format,ShaderProgram program,boolean semanticPass) {
        if(!ENABLED||failed!=null||source==null||source.orbitalKeys.isEmpty())return;
        var client=MinecraftClient.getInstance();if(client.world==null||client.player==null)return;
        if(com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy())return;
        try {
            require(WorldAccess.of(client.world).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"mask witness requires actual native cubic world");
            require(format==VertexFormats.POSITION_TEXTURE_COLOR,"drawn Horizon VBO has wrong actual UV0 layout");
            long epoch=AltitudeCloudRenderer.stats().epoch();
            var previous=semanticPass?semantic:colour;if(previous!=null&&previous.epoch==epoch)return;
            var eye=client.gameRenderer.getCamera().getPos();var expected=HorizonOrbitalView.sample(client,source,eye);
            var membership=PlanetClientState.INSTANCE.landmarksSnapshot();var domains=new HashSet<String>();
            var unique=new LinkedHashSet<HorizonLandmark>();int removed=0;
            for(var key:source.orbitalKeys)if(membership.contains(key.mark()))unique.add(key.mark());else removed++;
            for(var mark:unique) {
                double dx=mark.x()-eye.x,dy=mark.y()-eye.y,dz=mark.z()-eye.z,distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
                boolean zero=true,visible=false;
                for(int i=0;i<source.orbitalKeys.size();i++)if(source.orbitalKeys.get(i).mark().equals(mark)) {
                    zero&=expected[i]==0;visible|=expected[i]>.9f;
                }
                if(zero&&distance<=client.options.getClampedViewDistance()*16+32)domains.add("NEAR_FULL_ZERO_PROXY");
                if(visible&&distance>client.options.getClampedViewDistance()*16+32&&distance<=2048)domains.add("MID_PROXY_INPUT");
                if(visible&&distance>2048&&distance<HorizonProjection.MAX_TRUE_RADIUS)domains.add("FAR_PROXY_INPUT");
            }
            if(removed>0)domains.add("REMOVED_CATALOGUE_ZERO_PROXY");
            // Once enough frames of a category are observed, do not add unbounded GL queries/rows.
            if(domains.stream().noneMatch(d->COUNTS.getOrDefault(d,0)<8)
                &&(pixelPending==null||pixelPending.stage<0||CASE_FRAMES[pixelPending.stage]>=8)
                &&(pixels==null||CASE_FRAMES[pixels.stage]>=8))return;
            for(int i=0;i<108;i++) {
                int location=GL20.glGetUniformLocation(program.getGlRef(),"OrbitalOpacity["+i+"]");
                require(location>=0,"actual linked vec4 orbital uniform missing "+i);float[] actual=new float[4];
                GL20.glGetUniformfv(program.getGlRef(),location,actual);
                for(int c=0;c<4;c++)require(Float.isFinite(actual[c])&&Math.abs(actual[c]-expected[i*4+c])<.000001,
                    "actual GPU orbital opacity differs at slot "+(i*4+c)+" expected="+expected[i*4+c]+" actual="+actual[c]);
            }
            var draw=new Draw(epoch,source,expected,Set.copyOf(domains),unique.size(),removed);
            if(semanticPass)semantic=draw;else {colour=draw;afterColourPixels(source,epoch);}
        }catch(Throwable problem){fail(problem);}
    }
    /** Actual GameRenderer RETURN. Colour and semantic inputs must belong to the same frame/VBO. */
    public static void rendered() {
        if(!ENABLED||failed!=null||colour==null||semantic==null||colour.epoch==lastEpoch)return;
        var client=MinecraftClient.getInstance();if(client.world==null||client.player==null||client.currentScreen!=null)return;
        try {
            if(colour.epoch!=semantic.epoch)return;
            require(colour.source==semantic.source,"colour and cloud semantic used different authored source VBOs");
            require(Arrays.equals(colour.mask,semantic.mask),"colour and cloud semantic have different actual GPU masks");
            lastEpoch=colour.epoch;fieldViewReceipt(lastEpoch);
            if(colour.domains.stream().noneMatch(d->COUNTS.getOrDefault(d,0)<8)){write();return;}
            require(RECEIPTS.size()<32,"orbital GPU receipt cap32");
            var eye=client.gameRenderer.getCamera().getPos();
            for(String domain:colour.domains)if(COUNTS.getOrDefault(domain,0)<8) {
                COUNTS.merge(domain,1,Integer::sum);
                if(SHOTS.add(domain)) {
                    var folder=client.runDirectory.toPath().resolve("orbital-mask-frames");Files.createDirectories(folder);
                    screenshot(folder.resolve(domain+".png"));
                }
            }
            var row=new LinkedHashMap<String,Object>();row.put("epoch",lastEpoch);row.put("actualWorldTick",client.world.getTime());
            row.put("actualEye",List.of(eye.x,eye.y,eye.z));row.put("domains",colour.domains);row.put("currentMarks",colour.currentMarks);
            row.put("removedSourceMarks",colour.removedMarks);row.put("maskSlots",colour.source.orbitalKeys.size());row.put("actualQuads",colour.source.quads);
            row.put("actualGpuColourSemanticEqual",true);RECEIPTS.add(row);write();
        }catch(Throwable problem){fail(problem);}
    }
    private static void screenshot(java.nio.file.Path path) throws Exception {
        var c=MinecraftClient.getInstance();budget((long)c.getFramebuffer().textureWidth*c.getFramebuffer().textureHeight*4);
        int active=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE),texture=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        int[] names={org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT,org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH,org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS,org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS},saved=new int[4];
        for(int i=0;i<4;i++){saved[i]=org.lwjgl.opengl.GL11.glGetInteger(names[i]);org.lwjgl.opengl.GL11.glPixelStorei(names[i],i==0?4:0);}
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(path);}
        finally{com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);for(int i=0;i<4;i++)org.lwjgl.opengl.GL11.glPixelStorei(names[i],saved[i]);}
    }
    private static void fail(Throwable problem){failed=problem.toString();problem.printStackTrace();try{write();}catch(Exception ignored){} }
    private static void write() throws Exception {
        var report=new LinkedHashMap<String,Object>();report.put("scope","passive actual GPU-input/ABI witness; optional explicit physical mission extension adds real paired pixel/depth cases, not cold/global GPU-section completion");
        report.put("intactAsteroidMode",NativeIntactAsteroidCheck.ENABLED);report.put("failure",failed);report.put("physicalFieldViewFrames",CASE_FRAMES);report.put("allFourPhysicalFieldViewsObserved",fieldViewBits==15);report.put("physicalFieldViews",CASE_RECEIPTS);report.put("actualReadbackBytes",readbackBytes);report.put("lastFieldViewAdmission",lastViewAdmission);report.put("counts",COUNTS);report.put("observations",RECEIPTS);
        report.put("allThreeDomainsObserved",COUNTS.getOrDefault("NEAR_FULL_ZERO_PROXY",0)>=8&&COUNTS.getOrDefault("MID_PROXY_INPUT",0)>=8&&COUNTS.getOrDefault("FAR_PROXY_INPUT",0)>=8);
        Files.writeString(MinecraftClient.getInstance().runDirectory.toPath().resolve("orbital-mask-observations.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
