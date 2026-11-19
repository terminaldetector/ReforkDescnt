package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.client.cloud.AltitudeCloudRenderer;
import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
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
    private static long lastEpoch=-1;private static String failed;
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
            if(domains.stream().noneMatch(d->COUNTS.getOrDefault(d,0)<8))return;
            for(int i=0;i<108;i++) {
                int location=GL20.glGetUniformLocation(program.getGlRef(),"OrbitalOpacity["+i+"]");
                require(location>=0,"actual linked vec4 orbital uniform missing "+i);float[] actual=new float[4];
                GL20.glGetUniformfv(program.getGlRef(),location,actual);
                for(int c=0;c<4;c++)require(Float.isFinite(actual[c])&&Math.abs(actual[c]-expected[i*4+c])<.000001,
                    "actual GPU orbital opacity differs at slot "+(i*4+c)+" expected="+expected[i*4+c]+" actual="+actual[c]);
            }
            var draw=new Draw(epoch,source,expected,Set.copyOf(domains),unique.size(),removed);
            if(semanticPass)semantic=draw;else colour=draw;
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
            lastEpoch=colour.epoch;require(RECEIPTS.size()<32,"orbital GPU receipt cap32");
            var eye=client.gameRenderer.getCamera().getPos();
            for(String domain:colour.domains)if(COUNTS.getOrDefault(domain,0)<8) {
                COUNTS.merge(domain,1,Integer::sum);
                if(SHOTS.add(domain)) {
                    var folder=client.runDirectory.toPath().resolve("orbital-mask-frames");Files.createDirectories(folder);
                    try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(folder.resolve(domain+".png"));}
                }
            }
            var row=new LinkedHashMap<String,Object>();row.put("epoch",lastEpoch);row.put("actualWorldTick",client.world.getTime());
            row.put("actualEye",List.of(eye.x,eye.y,eye.z));row.put("domains",colour.domains);row.put("currentMarks",colour.currentMarks);
            row.put("removedSourceMarks",colour.removedMarks);row.put("maskSlots",colour.source.orbitalKeys.size());row.put("actualQuads",colour.source.quads);
            row.put("actualGpuColourSemanticEqual",true);RECEIPTS.add(row);write();
        }catch(Throwable problem){fail(problem);}
    }
    private static void fail(Throwable problem){failed=problem.toString();problem.printStackTrace();try{write();}catch(Exception ignored){} }
    private static void write() throws Exception {
        var report=new LinkedHashMap<String,Object>();report.put("scope","passive actual GPU-input/ABI witness, screenshots; not physical flight, cold, per-section upload or pixel-segmentation proof");
        report.put("failure",failed);report.put("counts",COUNTS);report.put("observations",RECEIPTS);
        report.put("allThreeDomainsObserved",COUNTS.getOrDefault("NEAR_FULL_ZERO_PROXY",0)>=8&&COUNTS.getOrDefault("MID_PROXY_INPUT",0)>=8&&COUNTS.getOrDefault("FAR_PROXY_INPUT",0)>=8);
        Files.writeString(MinecraftClient.getInstance().runDirectory.toPath().resolve("orbital-mask-observations.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
