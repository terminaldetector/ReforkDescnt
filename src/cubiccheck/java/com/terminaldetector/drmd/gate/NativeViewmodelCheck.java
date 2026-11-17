package com.terminaldetector.drmd.gate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.portal.PortalViewDiagnostics;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import com.terminaldetector.drmd.client.input.DroneCombatInput;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.workshop.ConstructionRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL13;
import java.nio.file.*;
import java.util.*;

/** Observer only: actual gold strider boxes, their GPU flush, cockpit ordering and final pixels.
 * It neither moves the camera/model nor changes the cockpit, world, raster or depth policy. */
public final class NativeViewmodelCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.viewmodel");
    private static final int[][] FACES={{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}};
    private static final Identifier WHITE=Identifier.of("minecraft","textures/misc/white.png");
    private static final List<float[][]> BOXES=new ArrayList<>();
    private static final List<Map<String,Object>> TRACE=new ArrayList<>();
    private static Map<String,NativeImage> images=new LinkedHashMap<>(),nativeImages=new LinkedHashMap<>();
    private static List<double[][]> polygons=List.of(),nativePolygons=List.of();
    private static List<Map<String,Object>> nativeTrace=List.of();
    private static Map<String,Object> nativeCamera=Map.of();
    private static String nativeModules="";
    private static int nativePixels,epoch,overlay,flush;
    private static boolean pendingGold,inGoldFlush,nativeObserved,done;
    private NativeViewmodelCheck(){}
    private static boolean live(){return ENABLED&&!done&&NativePresentationCheck.ENABLED&&!OffscreenWorldView.busy();}
    public static void beforeFrame(){
        if(!ENABLED||done)return;
        close(images);images=new LinkedHashMap<>();BOXES.clear();TRACE.clear();polygons=List.of();pendingGold=false;inGoldFlush=false;overlay=flush=0;epoch++;
    }
    private static void close(Map<String,NativeImage> map){for(var image:map.values())image.close();map.clear();}
    private static float[] matrix(Matrix4f matrix){return matrix.get(new float[16]);}
    private static Map<String,Object> camera(){
        var c=MinecraftClient.getInstance();var camera=c.gameRenderer.getCamera();var p=camera.getPos();
        return Map.of("x",p.x,"y",p.y,"z",p.z,"yaw",camera.getYaw(),"pitch",camera.getPitch(),"width",c.getWindow().getFramebufferWidth(),"height",c.getWindow().getFramebufferHeight());
    }
    private static String modules(){
        var c=MinecraftClient.getInstance();if(!(DroneCombatInput.primary(c).getItem() instanceof DescentWeaponItem item))return "";
        var values=new ArrayList<List<Object>>();
        for(var m:ConstructionRegistry.getModules(item.getDef().id))values.add(Arrays.asList(m.cluster,m.model,m.posFwd,m.posRgt,m.posUp,m.scaleX,m.scaleY,m.scaleZ,m.pitch,m.yaw,m.roll,m.colorR,m.colorG,m.colorB,m.colorA,m.bobAmp,m.bobFreq,m.spin));
        return new com.google.gson.Gson().toJson(values);
    }
    private static Map<String,Object> state(String stage){
        var c=MinecraftClient.getInstance();var d=new LinkedHashMap<String,Object>();int[] viewport=new int[4],polygon=new int[2];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygon);
        d.put("epoch",epoch);d.put("profile",DescentConfig.presentationProfile.name());d.put("stage",stage);d.put("sequence",TRACE.size());
        d.put("mainFbo",c.getFramebuffer().fbo);d.put("drawFbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));d.put("readFbo",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));d.put("viewport",viewport);
        d.put("depthTest",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));d.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));d.put("depthFunction",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));d.put("polygonModes",polygon);
        d.put("projection",matrix(RenderSystem.getProjectionMatrix()));d.put("modelView",matrix(RenderSystem.getModelViewMatrix()));d.put("camera",camera());d.put("modules",modules());
        d.put("goldBoxCount",BOXES.size());d.put("inPresentationWorld",ScenePresentation.stats().inWorldPass());return d;
    }
    public static void box(MatrixStack matrices,int argb,int light){
        if(!live()||(argb&0xffffff)!=0xffd070)return;
        float[][] points=new float[8][4];
        for(int i=0;i<8;i++){
            var p=new Vector4f((i&1)==0?-.5f:.5f,(i&2)==0?-.5f:.5f,(i&4)==0?-.5f:.5f,1).mul(matrices.peek().getPositionMatrix());
            points[i]=new float[]{p.x,p.y,p.z,p.w};
        }
        BOXES.add(points);pendingGold=true;var d=state("actual-gold-box-emission");d.put("argb",Integer.toUnsignedString(argb,16));d.put("light",light);d.put("entryPosition",matrix(matrices.peek().getPositionMatrix()));d.put("transformedVertices",points);TRACE.add(d);
    }
    private static List<double[][]> project(){
        var c=MinecraftClient.getInstance();int width=c.getWindow().getFramebufferWidth(),height=c.getWindow().getFramebufferHeight();
        var combined=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());var result=new ArrayList<double[][]>();
        for(var box:BOXES){
            var vertices=new double[8][2];boolean visible=true;
            for(int i=0;i<8;i++){
                var p=new Vector4f(box[i][0],box[i][1],box[i][2],box[i][3]).mul(combined);
                if(p.w<=1e-6){visible=false;break;}vertices[i]=new double[]{(p.x/p.w*.5+.5)*width,(.5-p.y/p.w*.5)*height};
            }
            if(visible)for(var face:FACES)result.add(new double[][]{vertices[face[0]],vertices[face[1]],vertices[face[2]],vertices[face[3]]});
        }return result;
    }
    public static void beforeLayer(RenderLayer layer){
        if(!live()||!pendingGold||layer!=RenderLayer.getEntitySolid(WHITE))return;
        inGoldFlush=true;polygons=project();var d=state("actual-white-solid-gold-flush-before");d.put("projectedGoldFaces",polygons);TRACE.add(d);capture("gold-layer-before-"+(flush+1));
    }
    public static void afterLayer(RenderLayer layer){
        if(!live()||!inGoldFlush||layer!=RenderLayer.getEntitySolid(WHITE))return;
        inGoldFlush=false;pendingGold=false;capture("gold-layer-after-"+(++flush));
    }
    public static void beforeOverlay(String renderer){if(live()){overlay++;capture("overlay-"+overlay+"-before");var d=state("overlay-renderer-before");d.put("renderer",renderer);TRACE.add(d);}}
    public static void afterOverlay(String renderer){if(live()){capture("overlay-"+overlay+"-after");var d=state("overlay-renderer-after");d.put("renderer",renderer);TRACE.add(d);}}
    public static void cockpit(boolean before){
        if(!live()||ScenePresentation.stats().inWorldPass())return;
        var c=MinecraftClient.getInstance();if(!WeaponViewRenderer.replacesVanillaHand(c)||!DescentConfig.cockpit)return;
        capture(before?"cockpit-before":"cockpit-after");
    }
    private static boolean inside(double x,double y,double[][] p){
        double area=0;for(int i=0;i<4;i++)area+=p[i][0]*p[(i+1)%4][1]-p[(i+1)%4][0]*p[i][1];if(Math.abs(area)<1e-7)return false;
        for(int i=0;i<4;i++){
            var a=p[i];var b=p[(i+1)%4];double dx=b[0]-a[0],dy=b[1]-a[1];
            if(Math.signum(area)*(dx*(y-a[1])-dy*(x-a[0]))<-.8*Math.hypot(dx,dy))return false;
        }return true;
    }
    private static boolean gold(int color){int r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;return r>=130&&g>=90&&b<=145&&r>=g+15&&g>=b+15;}
    private static int count(NativeImage image,List<double[][]> faces){
        if(faces.isEmpty())return 0;double[] b=bounds(faces);int total=0;
        for(int y=Math.max(0,(int)Math.floor(b[1])-1);y<Math.min(image.getHeight(),(int)Math.ceil(b[3])+1);y++)for(int x=Math.max(0,(int)Math.floor(b[0])-1);x<Math.min(image.getWidth(),(int)Math.ceil(b[2])+1);x++){
            if(!gold(image.getColor(x,y)))continue;
            for(var face:faces)if(inside(x+.5,y+.5,face)){total++;break;}
        }return total;
    }
    private static double[] bounds(List<double[][]> faces){
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(var face:faces)for(var p:face){minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);}
        return new double[]{minX,minY,maxX,maxY};
    }
    private static Map<String,Object> depth(List<double[][]> faces){
        var result=new LinkedHashMap<String,Object>();if(faces.isEmpty())return result;
        var c=MinecraftClient.getInstance();var b=bounds(faces);int width=c.getWindow().getFramebufferWidth(),height=c.getWindow().getFramebufferHeight();
        int x=Math.max(0,(int)Math.floor(b[0])-1),y=Math.max(0,(int)Math.floor(b[1])-1),w=Math.min(width,(int)Math.ceil(b[2])+1)-x,h=Math.min(height,(int)Math.ceil(b[3])+1)-y;
        if(w<=0||h<=0||w>256||h>256){result.put("outOfBound",true);result.put("bounds",b);return result;}
        int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);var values=BufferUtils.createFloatBuffer(w*h);
        int[] packNames={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},pack=new int[4];
        for(int i=0;i<4;i++){pack[i]=GL11.glGetInteger(packNames[i]);GL11.glPixelStorei(packNames[i],i==0?4:0);}
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,c.getFramebuffer().fbo);GL11.glReadPixels(x,height-y-h,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,values);}
        finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);for(int i=0;i<4;i++)GL11.glPixelStorei(packNames[i],pack[i]);}
        float[] data=new float[w*h];values.get(data);result.put("x",x);result.put("y",y);result.put("width",w);result.put("height",h);result.put("valuesBottomUp",data);return result;
    }
    private static void capture(String name){
        if(!live())return;
        var c=MinecraftClient.getInstance();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] packNames={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},pack=new int[4];
        for(int i=0;i<4;i++)pack[i]=GL11.glGetInteger(packNames[i]);
        NativeImage image;
        try{for(int i=0;i<4;i++)GL11.glPixelStorei(packNames[i],i==0?4:0);image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer());}
        finally{GlStateManager._activeTexture(active);RenderSystem.bindTexture(bound);for(int i=0;i<4;i++)GL11.glPixelStorei(packNames[i],pack[i]);}
        var previous=images.put(name,image);if(previous!=null)previous.close();
        var faces=polygons.isEmpty()?nativePolygons:polygons;var d=state(name);d.put("goldPixels",count(image,faces));d.put("footprintSource",polygons.isEmpty()?"retained-native-actual-flush":"current-actual-flush");d.put("projectedGoldFaces",faces);d.put("depth",depth(faces));TRACE.add(d);
    }
    private static double projectionDelta(){
        if(polygons.size()!=nativePolygons.size()||polygons.isEmpty())return Double.POSITIVE_INFINITY;
        double maximum=0;for(int i=0;i<polygons.size();i++)for(int j=0;j<4;j++)maximum=Math.max(maximum,Math.hypot(polygons.get(i)[j][0]-nativePolygons.get(i)[j][0],polygons.get(i)[j][1]-nativePolygons.get(i)[j][1]));return maximum;
    }
    private static void require(boolean value,String message){if(!value){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("VIEWMODEL_NATIVE "+message);}}
    public static void rendered(){
        if(!live()||BOXES.isEmpty())return;
        var c=MinecraftClient.getInstance();if(c.currentScreen!=null||!WeaponViewRenderer.replacesVanillaHand(c))return;
        if(DescentConfig.presentationProfile==PresentationProfile.RENDER_DEBUG)return;
        var mirror=PortalViewDiagnostics.lastSummary("mirror");if(mirror==null||!mirror.startsWith("drawing"))return;
        capture("final");int pixels=count(images.get("final"),polygons);
        if(DescentConfig.presentationProfile==PresentationProfile.HYBRID_NATIVE){
            nativeObserved=true;nativePixels=pixels;nativePolygons=List.copyOf(polygons);nativeCamera=camera();nativeModules=modules();nativeTrace=new ArrayList<>(TRACE);
            close(nativeImages);nativeImages=images;images=new LinkedHashMap<>();return;
        }
        double delta=projectionDelta();boolean sameCamera=camera().equals(nativeCamera),sameModules=modules().equals(nativeModules);int required=Math.max(100,(int)Math.ceil(nativePixels*.75));
        boolean valid=nativeObserved&&nativePixels>=100&&sameCamera&&sameModules&&delta<=1&&pixels>=required;
        try{
            var folder=c.runDirectory.toPath().resolve(valid?"viewmodel-proof":"viewmodel-failure");Files.createDirectories(folder);
            for(var entry:nativeImages.entrySet())entry.getValue().writeTo(folder.resolve("native-"+entry.getKey()+".png"));
            for(var entry:images.entrySet())entry.getValue().writeTo(folder.resolve("ps1-"+entry.getKey()+".png"));
            var report=new LinkedHashMap<String,Object>();report.put("epoch",epoch);report.put("nativeObserved",nativeObserved);report.put("nativePixels",nativePixels);report.put("ps1Pixels",pixels);report.put("requiredPixels",required);report.put("sameCamera",sameCamera);report.put("sameModules",sameModules);report.put("maximumProjectedVertexDeltaPixels",Double.isFinite(delta)?delta:"unavailable");
            report.put("nativeCamera",nativeCamera);report.put("ps1Camera",camera());report.put("modules",nativeModules);report.put("nativeProjectedGoldFaces",nativePolygons);report.put("ps1ProjectedGoldFaces",polygons);report.put("nativeTrace",nativeTrace);report.put("ps1Trace",TRACE);
            report.put("thresholds",Map.of("minNativePixels",100,"minPs1Pixels",100,"minNativeFraction",.75,"maxProjectionDeltaPixels",1,"minR",130,"minG",90,"maxB",145,"minRgDelta",15,"minGbDelta",15));
            Files.writeString(folder.resolve("viewmodel-visibility.json"),new com.google.gson.Gson().toJson(report));
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        require(valid,"same actual gold viewmodel geometry must remain visible with cockpit: Native="+nativePixels+", PS1="+pixels+", required="+required+", projectionDelta="+delta+", camera="+sameCamera+", modules="+sameModules);
        done=true;
    }
}
