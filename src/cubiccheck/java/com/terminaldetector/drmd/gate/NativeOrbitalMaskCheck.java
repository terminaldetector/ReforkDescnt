package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.presentation.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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
    private record PixelPending(int stage,long epoch,PlanetSurfaceMesh source,List<Region> regions,int fullCubes,int totalCubes,double distance,int fbo,float[] view,float[] projection,HorizonLandmark selected) {}
    private record Pixels(int stage,long epoch,Map<String,Object> receipt,FogFrame fogFrame) {}
    private record FogFrame(PixelPending draw,Region after,Vec3d eye,float[] mask,float[] originDelta,
                            float[] pixelShift,float[] modulator,float outerRadius,float density,
                            float[] sky,float alpha,float start,float end,int width,int height) {}
    private static FogFrame fogBaseline;
    private static int fogFrames,fogRearms;
    private static Map<String,Object> fogPair=Map.of();
    private static boolean fogPairObserved;
    private static PixelPending pixelPending;
    private static Pixels pixels;
    private static Map<String,Object> firstPixelFailure=Map.of();
    private static final int[] CASE_FRAMES=new int[4];
    private static final List<Map<String,Object>> CASE_RECEIPTS=new ArrayList<>();
    private static volatile int fieldViewBits;
    private static volatile Map<String,Object> lastViewAdmission=Map.of();
    private static float inputInner,inputReach;private static long inputEpoch=-1,readbackBytes;
    private static int viewCase(){return NativeIntactAsteroidCheck.ENABLED?NativeIntactAsteroidCheck.viewCase():NativeOrbitalMissionCheck.fieldViewCase();}
    public static boolean fieldViewObserved(int stage){return stage>=0&&stage<4&&(fieldViewBits&(1<<stage))!=0;}
    public static boolean allFieldViewsObserved(){return fieldViewBits==15;}
    static boolean asteroidFogObserved(){return fogPairObserved;}
    private static boolean needsPixels(int stage){
        return stage>=0&&(CASE_FRAMES[stage]<8||(stage==2&&NativeIntactAsteroidCheck.fogProbe()&&!fogPairObserved));
    }
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
        if(!needsPixels(stage))return;
        var c=MinecraftClient.getInstance();if(c.world==null||source==null||OffscreenWorldView.busy())return;
        try{
            long epoch=AltitudeCloudRenderer.stats().epoch();var eye=c.gameRenderer.getCamera().getPos();
            if(inputEpoch!=epoch||source.stale(eye.x,eye.y,eye.z,inputInner,inputReach)){admission(stage,"AWAITING_CURRENT_SOURCE", "inputEpoch",inputEpoch,"epoch",epoch);return;}
            boolean donorReader=NativeIntactAsteroidCheck.ENABLED;
            boolean asteroid=donorReader&&!NativeIntactAsteroidCheck.STATION;
            var centre=donorReader?NativeIntactAsteroidCheck.centre():NativeOrbitalMissionCheck.fieldViewStation();
            var marks=PlanetClientState.INSTANCE.landmarksSnapshot().stream().filter(m->HorizonOrbitalProxy.authored(m.kind())).toList();
            HorizonLandmark mark;
            if(donorReader){
                var selected=marks.stream().filter(NativeIntactAsteroidCheck::selected).toList();
                if(marks.size()!=3||selected.size()!=1){admission(stage,"AWAITING_THREE_ORIGINAL_CERTIFICATES_AND_SELECTED_OBJECT","actualAuthoredMarks",marks.size());return;}
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
            List<Region> regions=new ArrayList<>();double skyRayMin=0;
            if(near){
                int[] opaque=project(view,asteroid?centre.add(.5,.5,-halfZ):centre.add(3.5,-3.5,-9),false,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
                int[] hole=asteroid?null:project(view,(NativeIntactAsteroidCheck.STATION?centre.add(.5,5.5,-8.5):centre.add(.5,-1.5,.5)),false,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
                if(opaque==null||(!asteroid&&hole==null)){admission(stage,"NEAR_TARGET_OUTSIDE_CURRENT_VIEW");return;}
                regions.add(read("opaque",opaque[0]-2,opaque[1]-2,5,5,fbo));
                if(!asteroid)regions.add(read("open-window",hole[0]-2,hole[1]-2,5,5,fbo));
                if(NativeIntactAsteroidCheck.STATION){
                    var inverse=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(view).invert();
                    skyRayMin=Double.POSITIVE_INFINITY;
                    for(int y=hole[1]-2;y<=hole[1]+2;y++)for(int x=hole[0]-2;x<=hole[0]+2;x++){
                        float nx=(x+.5f)/viewport[2]*2-1,ny=(y+.5f)/viewport[3]*2-1;
                        var a=new org.joml.Vector4f(nx,ny,-1,1).mul(inverse);var z=new org.joml.Vector4f(nx,ny,1,1).mul(inverse);
                        double dx=z.x/z.w-a.x/a.w,dy=z.y/z.w-a.y/a.w,dz=z.z/z.w-a.z/a.w;
                        skyRayMin=Math.min(skyRayMin,dy/Math.sqrt(dx*dx+dy*dy+dz*dz));
                    }
                    if(!Double.isFinite(skyRayMin)||skyRayMin<=0){admission(stage,"UPPER_WINDOW_DOES_NOT_HAVE25UPWARD_WORLD_RAYS","minWorldRayY",Double.isFinite(skyRayMin)?skyRayMin:0);return;}
                }
                if(java.util.stream.IntStream.range(0,25).noneMatch(i->regions.get(0).depth[i]<.999995f)){admission(stage,"AWAITING_ACTUAL_OPAQUE_DEPTH","actualFullAabbCubes",full);return;}
                if(!asteroid&&java.util.stream.IntStream.range(0,25).anyMatch(i->regions.get(1).depth[i]<.999995f)){admission(stage,"EXPECTED_OPEN_WINDOW_HAS_NEAR_DEPTH","actualFullAabbCubes",full);return;}
            }else{
                int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE;
                for(int x:new int[]{-halfX,halfX})for(int y:new int[]{-halfY,halfY})for(int z:new int[]{-halfZ,halfZ}){
                    int[] at=project(view,centre.add(x,y,z),true,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);if(at==null){admission(stage,"DISTANT_STATION_OUTSIDE_CURRENT_VIEW");return;}
                    x0=Math.min(x0,at[0]);x1=Math.max(x1,at[0]);y0=Math.min(y0,at[1]);y1=Math.max(y1,at[1]);
                }
                int pad=donorReader?0:2; // Each intact reader owns only its actual footprint; a nearby object cannot supply pixels.
                if(donorReader){
                    // A different certified object cannot stand in for the selected distant footprint.
                    for(var other:marks)if(!other.equals(mark)){
                        int ox0=Integer.MAX_VALUE,oy0=Integer.MAX_VALUE,ox1=Integer.MIN_VALUE,oy1=Integer.MIN_VALUE;boolean projected=true;
                        for(int x:new int[]{-other.sizeX()/2,other.sizeX()/2})for(int y:new int[]{-other.sizeY()/2,other.sizeY()/2})for(int z:new int[]{-other.sizeZ()/2,other.sizeZ()/2}){
                            var at=project(view,new net.minecraft.util.math.Vec3d(other.x()+x,other.y()+y,other.z()+z),true,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
                            if(at==null){projected=false;continue;}ox0=Math.min(ox0,at[0]);ox1=Math.max(ox1,at[0]);oy0=Math.min(oy0,at[1]);oy1=Math.max(oy1,at[1]);
                        }
                        if(projected&&ox1>=x0-pad&&ox0<=x1+pad&&oy1>=y0-pad&&oy0<=y1+pad){admission(stage,"OTHER_CERTIFIED_OBJECT_OVERLAPS_SELECTED_DONOR_ROI");return;}
                    }
                }
                regions.add(read("authored-proxy",x0-pad,y0-pad,x1-x0+1+pad*2,y1-y0+1+pad*2,fbo));
                for(float depth:regions.get(0).depth)if(depth<.999995f){admission(stage,"DISTANT_PROXY_ROI_HAS_REAL_FOREGROUND","actualFullAabbCubes",full);return;}
            }
            admission(stage,"ACTUAL_PIXEL_INPUTS_ADMITTED","actualFullAabbCubes",full,"aabbCubes",total,"trueDistance",distance,"sceneFbo",fbo,"upperSkyRayMinY",skyRayMin);
            pixelPending=new PixelPending(stage,epoch,source,List.copyOf(regions),full,total,distance,fbo,view.get(new float[16]),
                com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().get(new float[16]),mark);
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
    private static void afterColourPixels(PlanetSurfaceMesh source,long epoch,ShaderProgram program){
        var pending=pixelPending;pixelPending=null;if(pending==null||pending.epoch!=epoch||pending.source!=source)return;
        if(viewCase()!=pending.stage)return;
        require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)==pending.fbo,"same actual scene target after orbital colour draw");
        var regionRows=new ArrayList<Map<String,Object>>();var rawRows=new ArrayList<Map<String,Object>>();boolean near=pending.stage==0||pending.stage==3;
        boolean depthBad=false,pixelsBad=false;
        FogFrame fogFrame=null;
        for(var before:pending.regions){var after=read(before.name,before.x,before.y,before.width,before.height,pending.fbo);int changed=0,depthChanges=0;
            for(int i=0;i<before.depth.length;i++){
                if(!(Math.abs(before.depth[i]-after.depth[i])<.000001))depthChanges++;
                if(before.rgba[i*4]!=after.rgba[i*4]||before.rgba[i*4+1]!=after.rgba[i*4+1]||before.rgba[i*4+2]!=after.rgba[i*4+2])changed++;
            }
            depthBad|=depthChanges!=0;pixelsBad|=!(near?changed==0:changed>=(pending.stage==1?4:1));
            if(pending.stage==2&&NativeIntactAsteroidCheck.fogProbe())fogFrame=readFogFrame(pending,after,program);
            regionRows.add(Map.of("name",before.name,"rect",List.of(before.x,before.y,before.width,before.height),"changedPixels",changed,"depthUnchanged",depthChanges==0));
            rawRows.add(Map.of("name",before.name,"rect",List.of(before.x,before.y,before.width,before.height),"changedPixels",changed,"depthChanges",depthChanges,
                "beforeRgbaBottomLeftHex",java.util.HexFormat.of().formatHex(before.rgba),"afterRgbaBottomLeftHex",java.util.HexFormat.of().formatHex(after.rgba),
                "beforeDepth",before.depth,"afterDepth",after.depth));
        }
        if(depthBad||pixelsBad){
            preservePixelFailure(pending,rawRows);
            require(!depthBad,"orbital backdrop cannot alter native foreground/sky depth: "+regionRows);
            require(!pixelsBad,(near?"near opaque/window pixels changed in the WHOLE Horizon draw; layer attribution required: ":"actual MID/FAR authored station produced no colour pixels beyond residency: ")+regionRows);
        }
        pixels=new Pixels(pending.stage,epoch,Map.of("case",pending.stage,"actualDistance",pending.distance,"clientFullAabbCubes",pending.fullCubes,
            "aabbCubes",pending.totalCubes,"actualSceneFbo",pending.fbo,"regions",regionRows),fogFrame);
    }
    private static float[] uniform(ShaderProgram program,String name,int count){
        int ref=program.getGlRef(),at=GL20.glGetUniformLocation(ref,name);
        require(at>=0,"actual active field uniform "+name);
        float[] values=new float[count];GL20.glGetUniformfv(ref,at,values);
        for(float value:values)require(Float.isFinite(value),"non-finite actual field uniform "+name);
        return values;
    }

    private static FogFrame readFogFrame(PixelPending draw,Region after,ShaderProgram program){
        require(++fogFrames<=8,"at most eight extra distant ROI scans");
        var c=MinecraftClient.getInstance();var eye=c.gameRenderer.getCamera().getPos();
        int object=HorizonOrbitalPixelSnap.objects(draw.source.orbitalKeys).indexOf(draw.selected);
        require(object>=0,"selected asteroid has an actual uploaded pixel shift slot");
        var shift=uniform(program,"OrbitalPixelShift["+(object/2)+"]",4);
        var selectedShift=Arrays.copyOfRange(shift,object%2*2,object%2*2+2);
        require(Math.abs(selectedShift[0])+Math.abs(selectedShift[1])>0,"actual FAR asteroid snapped on the PS1 scene");
        float density=uniform(program,"Ps1FarFogDensity",1)[0];
        float skyMix=uniform(program,"Ps1SkyFogMix",1)[0];
        float start=uniform(program,"Ps1FogStart",1)[0],end=uniform(program,"Ps1FogEnd",1)[0];
        float[] sky=uniform(program,"Ps1SkyFogColor",3),fallback=uniform(program,"FogColor",4);
        if(fogBaseline==null){
            require(density==0&&skyMix==0&&start==0&&end==1&&Arrays.equals(sky,new float[3])
                &&!Ps1WorldFog.active(),"actual neutral world and colour shader fog");
        }else{
            var expected=Ps1FogRange.at(eye.y,2,c.options.getClampedViewDistance()*16f).orElseThrow();
            require(Math.abs(density-Ps1Atmosphere.density(eye.y,2))<1e-5&&skyMix==1&&start==expected.start()
                &&Math.abs(end-expected.end())<.001f&&fallback[3]>0&&Ps1WorldFog.active(),"actual enabled world and colour shader fog");
            var worldFog=com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();
            for(int i=0;i<3;i++)require(Math.abs(worldFog[i]-sky[i])<1e-4,"same actual native terrain and Horizon sky target");
        }
        var modulator=uniform(program,"ColorModulator",4);
        float outer=uniform(program,"FieldOuterRadius",1)[0];
        require(Arrays.equals(modulator,new float[]{1,1,1,1})&&draw.distance+16<outer*.55,"opaque Horizon source before any outer fade");
        return new FogFrame(draw,after,eye,colour.mask,uniform(program,"FieldOriginDelta",3),selectedShift,modulator,
            outer,density,sky,fallback[3],start,end,c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight);
    }

    private static boolean sameFogSource(FogFrame neutral,FogFrame styled){
        var a=neutral.draw;var b=styled.draw;
        return a.source==b.source&&a.fbo==b.fbo&&a.selected.equals(b.selected)&&neutral.eye.equals(styled.eye)
            &&neutral.width==styled.width&&neutral.height==styled.height&&neutral.outerRadius==styled.outerRadius
            &&Arrays.equals(a.view,b.view)&&Arrays.equals(a.projection,b.projection)
            &&Arrays.equals(neutral.pixelShift,styled.pixelShift)&&Arrays.equals(neutral.mask,styled.mask)
            &&Arrays.equals(neutral.originDelta,styled.originDelta)&&Arrays.equals(neutral.modulator,styled.modulator)
            &&neutral.after.x==styled.after.x&&neutral.after.y==styled.after.y
            &&neutral.after.width==styled.after.width&&neutral.after.height==styled.after.height;
    }

    /** Actual source face data from the uploaded mesh, at the same shifted sample centre as the colour draw. */
    private static int coveringAsteroidFaces(FogFrame frame,int x,int y,int rgb){
        var p=frame.draw;var source=p.source;
        var transform=new Matrix4f().set(p.projection).mul(new Matrix4f().set(p.view));
        double px=(x+.5)*2/frame.width-1,py=(y+.5)*2/frame.height-1;
        int faces=0;
        for(int q=0;q<source.quads;q++){
            int slot=source.orbitalMasks[q];
            if(slot<0||frame.mask[slot]!=1||!source.orbitalKeys.get(slot).mark().equals(p.selected)
                ||source.colours[q]!=(0xff000000|rgb))continue;
            var v=new Vector4f[4];
            for(int i=0;i<4;i++){
                int at=q*12+i*3;
                float vx=source.positions[at]+frame.originDelta[0],vy=source.positions[at+1]+frame.originDelta[1],vz=source.positions[at+2]+frame.originDelta[2];
                float scale=(float)HorizonProjection.factor(vx,vy,vz,inputInner,inputReach);
                v[i]=new Vector4f(vx*scale,vy*scale,vz*scale,1).mul(transform);
                require(v[i].w>0,"visible selected source face at positive clip W");
                v[i].set(v[i].x/v[i].w+frame.pixelShift[0],v[i].y/v[i].w+frame.pixelShift[1],0,1);
            }
            if(inFrontTriangle(v[0],v[1],v[2],px,py)||inFrontTriangle(v[2],v[3],v[0],px,py))faces++;
        }
        return faces;
    }

    private static double side(Vector4f a,Vector4f b,double x,double y){
        return (b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);
    }

    private static boolean inFrontTriangle(Vector4f a,Vector4f b,Vector4f c,double x,double y){
        return side(a,b,c.x,c.y)>0&&side(a,b,x,y)>=-1e-8&&side(b,c,x,y)>=-1e-8&&side(c,a,x,y)>=-1e-8;
    }

    private static List<Integer> rgb(Region region,int i){
        return List.of(Byte.toUnsignedInt(region.rgba[i*4]),Byte.toUnsignedInt(region.rgba[i*4+1]),Byte.toUnsignedInt(region.rgba[i*4+2]));
    }

    private static void receiveFogPair(FogFrame styled) throws Exception{
        if(fogBaseline==null){
            fogBaseline=styled;DescentConfig.ps1FarFog=new Ps1FarFogOptions(2);return;
        }
        var neutral=fogBaseline;
        if(!sameFogSource(neutral,styled)){
            fogBaseline=null;fogRearms++;DescentConfig.ps1FarFog=new Ps1FarFogOptions(0);
            admission(2,"AWAITING_SAME_SOURCE_FOG_PAIR","fogRearms",fogRearms,"fogScans",fogFrames);
            return;
        }
        var a=neutral.draw.regions.get(0);var b=styled.draw.regions.get(0);
        require(Arrays.equals(a.depth,neutral.after.depth)&&Arrays.equals(a.depth,b.depth)
            &&Arrays.equals(a.depth,styled.after.depth),"same exact real sky-depth samples in all four readbacks");
        var range=new Ps1FogRange(styled.start,styled.end);
        double minAmount=range.amount(styled.draw.distance-12,styled.alpha),maxAmount=range.amount(styled.draw.distance+12,styled.alpha);
        var rows=new ArrayList<Map<String,Object>>();
        boolean match=false;
        for(int i=0;i<a.depth.length;i++){
            int x=a.x+i%a.width,y=a.y+i/a.width;
            var raw=rgb(neutral.after,i);var hazy=rgb(styled.after,i);
            int packed=(raw.get(0)<<16)|(raw.get(1)<<8)|raw.get(2);
            int faces=coveringAsteroidFaces(neutral,x,y,packed);
            int delta=0;boolean agrees=true;
            for(int ch=0;ch<3;ch++){
                double toward=styled.sky[ch]*255-raw.get(ch);
                double p0=raw.get(ch)+toward*minAmount,p1=raw.get(ch)+toward*maxAmount;
                agrees&=hazy.get(ch)>=Math.min(p0,p1)-2&&hazy.get(ch)<=Math.max(p0,p1)+2;
                delta=Math.max(delta,Math.abs(hazy.get(ch)-raw.get(ch)));
            }
            boolean accepted=faces>0&&agrees&&delta>=4&&!raw.equals(rgb(a,i))&&!hazy.equals(rgb(b,i));
            match|=accepted;
            rows.add(Map.of("scenePixel",List.of(x,y),"neutralBackground",rgb(a,i),"neutralAfter",raw,
                "enabledBackground",rgb(b,i),"enabledAfter",hazy,"depth",a.depth[i],"coveringOpaqueAsteroidFaces",faces,
                "withinShaderFogAmount",agrees,"largestChannelDelta",delta,"witness",accepted));
        }
        var pair=new LinkedHashMap<String,Object>();
        pair.put("outcome",match?"PASS":"FAIL");pair.put("sameActualSourceFboPoseMatricesAndMasks",true);
        pair.put("sourceIdentity",System.identityHashCode(styled.draw.source));pair.put("fbo",styled.draw.fbo);
        pair.put("epochs",List.of(neutral.draw.epoch,styled.draw.epoch));pair.put("actualEye",List.of(styled.eye.x,styled.eye.y,styled.eye.z));
        pair.put("trueDistance",styled.draw.distance);pair.put("minimumAndMaximumAmount",List.of(minAmount,maxAmount));
        pair.put("actualFogDensity",List.of(neutral.density,styled.density));pair.put("actualFogRange",List.of(styled.start,styled.end));
        pair.put("actualSkyTarget",styled.sky);pair.put("actualFogAlpha",styled.alpha);pair.put("pixelShiftNdc",styled.pixelShift);
        pair.put("fogScans",fogFrames);pair.put("fogRearms",fogRearms);pair.put("pixels",rows);
        fogPair=Map.copyOf(pair);
        require(match,"selected covering FAR asteroid pixel did not acquire the actual shader haze: "+rows);
        var c=MinecraftClient.getInstance();var folder=c.runDirectory.toPath().resolve("orbital-mask-frames");
        screenshot(folder.resolve("intact-asteroid-far-enabled-fog.png"));
        fogPairObserved=true;
    }

    private static void preservePixelFailure(PixelPending p,List<Map<String,Object>> regions){
        if(!firstPixelFailure.isEmpty())return;
        var c=MinecraftClient.getInstance();var row=new LinkedHashMap<String,Object>();
        row.put("scope","first exact failed whole-Horizon RGB/depth pair; no inferred primitive ownership");
        row.put("case",p.stage);row.put("epoch",p.epoch);row.put("worldTick",c.world.getTime());row.put("sceneFbo",p.fbo);row.put("sceneSize",List.of(c.getFramebuffer().textureWidth,c.getFramebuffer().textureHeight));
        row.put("projection",p.projection);row.put("view",p.view);row.put("innerReach",List.of(inputInner,inputReach));row.put("selected",p.selected.toString());
        row.put("admission",lastViewAdmission);row.put("regions",regions);row.put("sourceIdentity",System.identityHashCode(p.source));row.put("sourceOrigin",List.of(p.source.originX,p.source.originY,p.source.originZ));row.put("sourceQuads",p.source.quads);
        row.put("untaggedGroundLegacyQuads",java.util.Arrays.stream(p.source.orbitalMasks).filter(i->i<0).count());
        var masks=new ArrayList<Map<String,Object>>();
        for(int i=0;i<p.source.orbitalKeys.size();i++){var key=p.source.orbitalKeys.get(i);masks.add(Map.of("slot",i,"mark",key.mark().toString(),"cube",List.of(key.cube().x(),key.cube().y(),key.cube().z()),"verifiedGpuOpacity",colour.mask[i]));}
        row.put("authoredGpuMasks",masks);
        var surface=new ArrayList<Map<String,Object>>();var mark=p.selected;
        for(int x=Math.floorDiv(mark.x()-mark.sizeX()/2,16);x<=Math.floorDiv(mark.x()+mark.sizeX()/2-1,16);x++)
            for(int z=Math.floorDiv(mark.z()-mark.sizeZ()/2,16);z<=Math.floorDiv(mark.z()+mark.sizeZ()/2-1,16);z++)
                surface.add(Map.of("chunk",List.of(x,z),"height",PlanetClientState.INSTANCE.observedHeight(x*16.+8,z*16.+8,0),"colour",PlanetClientState.INSTANCE.observedColour(x*16.+8,z*16.+8,0)));
        row.put("actualCurrentClientL0TopSamples",surface);firstPixelFailure=Map.copyOf(row);
        try{Files.writeString(c.runDirectory.toPath().resolve("orbital-first-pixel-failure.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(firstPixelFailure));}
        catch(java.io.IOException problem){throw new IllegalStateException("could not preserve actual pixel failure",problem);}
    }
    private static void fieldViewReceipt(long epoch) throws Exception{
        if(pixels==null||pixels.epoch!=epoch)return;var pixel=pixels;pixels=null;
        if(pixel.fogFrame!=null){receiveFogPair(pixel.fogFrame);return;}
        if(CASE_FRAMES[pixel.stage]>=8)return;
        require(CASE_RECEIPTS.size()<32,"four native view cases x8 actual frames cap32");var receipt=new LinkedHashMap<String,Object>(pixel.receipt);var client=MinecraftClient.getInstance();var eye=client.gameRenderer.getCamera().getPos();
        receipt.put("epoch",epoch);receipt.put("actualWorldTick",client.world.getTime());receipt.put("actualEye",List.of(eye.x,eye.y,eye.z));
        receipt.put("colourSemanticSameSourceAndMask",true);receipt.put("sourceIdentity",System.identityHashCode(colour.source));receipt.put("sourceOrigin",List.of(colour.source.originX,colour.source.originY,colour.source.originZ));
        receipt.put("actualQuads",colour.source.quads);CASE_RECEIPTS.add(receipt);
        if(++CASE_FRAMES[pixel.stage]==8)fieldViewBits|=1<<pixel.stage;
        if(CASE_FRAMES[pixel.stage]==1){
            var c=MinecraftClient.getInstance();var folder=c.runDirectory.toPath().resolve("orbital-mask-frames");Files.createDirectories(folder);
            screenshot(folder.resolve((NativeIntactAsteroidCheck.ENABLED?(NativeIntactAsteroidCheck.STATION?"intact-station-":"intact-asteroid-"):"")+"physical-view-"+pixel.stage+".png"));
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
                &&(pixelPending==null||!needsPixels(pixelPending.stage))
                &&(pixels==null||!needsPixels(pixels.stage)))return;
            for(int i=0;i<108;i++) {
                int location=GL20.glGetUniformLocation(program.getGlRef(),"OrbitalOpacity["+i+"]");
                require(location>=0,"actual linked vec4 orbital uniform missing "+i);float[] actual=new float[4];
                GL20.glGetUniformfv(program.getGlRef(),location,actual);
                for(int c=0;c<4;c++)require(Float.isFinite(actual[c])&&Math.abs(actual[c]-expected[i*4+c])<.000001,
                    "actual GPU orbital opacity differs at slot "+(i*4+c)+" expected="+expected[i*4+c]+" actual="+actual[c]);
            }
            var draw=new Draw(epoch,source,expected,Set.copyOf(domains),unique.size(),removed);
            if(semanticPass)semantic=draw;else {colour=draw;afterColourPixels(source,epoch,program);}
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
        report.put("intactAsteroidMode",NativeIntactAsteroidCheck.ENABLED&&!NativeIntactAsteroidCheck.STATION);report.put("intactStationMode",NativeIntactAsteroidCheck.STATION);report.put("failure",failed);report.put("firstPixelFailure",firstPixelFailure);report.put("physicalFieldViewFrames",CASE_FRAMES);report.put("allFourPhysicalFieldViewsObserved",fieldViewBits==15);report.put("physicalFieldViews",CASE_RECEIPTS);report.put("actualReadbackBytes",readbackBytes);report.put("lastFieldViewAdmission",lastViewAdmission);report.put("counts",COUNTS);report.put("observations",RECEIPTS);
        report.put("intactAsteroidFogPair",fogPair);
        report.put("allThreeDomainsObserved",COUNTS.getOrDefault("NEAR_FULL_ZERO_PROXY",0)>=8&&COUNTS.getOrDefault("MID_PROXY_INPUT",0)>=8&&COUNTS.getOrDefault("FAR_PROXY_INPUT",0)>=8);
        Files.writeString(MinecraftClient.getInstance().runDirectory.toPath().resolve("orbital-mask-observations.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
