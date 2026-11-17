package com.terminaldetector.drmd.gate;

import com.mojang.blaze3d.platform.GlStateManager;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Actual scene texture -> main world reference, sampled before native overlays. No GPU writes. */
public final class NativePs1ColorCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ps1Color");
    private static final int[][] BAYER={{0,8,2,10},{12,4,14,6},{3,11,1,9},{15,7,13,5}};
    private static final int[] LEVELS={256,32,32,256};
    private static final float[] DITHER={0,0,.5f,0};
    private static final String[] NAMES={"raw256","palette32","dither32","raw256-restored"};
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static NativeImage source,main;
    private static float[] sourceDepth,mainDepth;
    private static Map<String,Object> sourceState=Map.of(),mainState=Map.of();
    private static int stage,warmFrames,epoch,sourceEpoch=-1,mainEpoch=-1;
    private static boolean sampling,controlsAvailable,readbacksPreserved,initialDone,resizedDone;
    private NativePs1ColorCheck(){}

    private static Object field(Object owner,String name){
        try{Field f=(owner instanceof Class<?> c?c:owner.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class<?>?null:owner);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("PS1_COLOR observer field "+name,failure);}
    }
    private static int fixturePhase(){return (int)field(NativePresentationCheck.class,"phase");}
    private static boolean fixtureReady(){return Boolean.TRUE.equals(field(NativePresentationCheck.class,"ready"));}
    private static boolean live(){return ENABLED&&sampling&&!OffscreenWorldView.busy();}
    private static void clear(){if(source!=null)source.close();if(main!=null)main.close();source=main=null;sourceDepth=mainDepth=null;sourceEpoch=mainEpoch=-1;}
    private static boolean setChoices(int levels,float dither){
        // Deliberately tolerate absent not-yet-implemented fields in the first feature negative.
        try{
            var l=DescentConfig.class.getField("ps1ColorLevels");var d=DescentConfig.class.getField("ps1DitherStrength");
            l.setInt(null,levels);d.setFloat(null,dither);return l.getInt(null)==levels&&d.getFloat(null)==dither;
        }catch(NoSuchFieldException unavailable){return false;}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    public static void beforeFrame(){
        if(!ENABLED)return;clear();epoch++;sampling=false;
        if(!fixtureReady()||MinecraftClient.getInstance().currentScreen!=null)return;
        int phase=fixturePhase();
        if(phase==1&&!initialDone){controlsAvailable=setChoices(LEVELS[stage],DITHER[stage]);sampling=true;}
        else if(phase==4&&!resizedDone){controlsAvailable=setChoices(32,.5f);sampling=true;}
    }
    public static boolean shouldRecordViewmodel(){
        return !ENABLED||DescentConfig.presentationProfile!=PresentationProfile.PS1_HYBRID
                ||controlsAvailable&&fixturePhase()==1&&(stage==2||initialDone);
    }
    private static Map<String,Object> glState(){
        int[] viewport=new int[4],pack=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS};
        for(int i=0;i<names.length;i++)pack[i]=GL11.glGetInteger(names[i]);
        var r=new LinkedHashMap<String,Object>();r.put("epoch",epoch);r.put("fixturePhase",fixturePhase());r.put("profile",DescentConfig.presentationProfile.name());
        r.put("drawFbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));r.put("readFbo",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
        r.put("viewport",Arrays.toString(viewport));r.put("activeTexture",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));r.put("texture2d",GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));r.put("pack",Arrays.toString(pack));
        r.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));r.put("depthTest",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));r.put("depthFunction",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        r.put("glVersion",GL11.glGetString(GL11.GL_VERSION));r.put("glslVersion",GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
        return r;
    }
    private static int[] savePack(){int[] n={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},v=new int[4];for(int i=0;i<4;i++){v[i]=GL11.glGetInteger(n[i]);GL11.glPixelStorei(n[i],i==0?4:0);}return v;}
    private static void restorePack(int[] v){int[] n={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS};for(int i=0;i<4;i++)GL11.glPixelStorei(n[i],v[i]);}
    private static NativeImage image(Framebuffer f){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int[] pack=savePack();
        try{return ScreenshotRecorder.takeScreenshot(f);}finally{GlStateManager._activeTexture(active);GlStateManager._bindTexture(binding);restorePack(pack);}
    }
    private static float[] depth(Framebuffer f){
        int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);int[] pack=savePack();var b=MemoryUtil.memAllocFloat(f.textureWidth*f.textureHeight);
        try{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,f.fbo);GL11.glReadPixels(0,0,f.textureWidth,f.textureHeight,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,b);
            float[] result=new float[f.textureWidth*f.textureHeight];
            for(int y=0;y<f.textureHeight;y++)for(int x=0;x<f.textureWidth;x++)result[y*f.textureWidth+x]=b.get((f.textureHeight-1-y)*f.textureWidth+x);
            return result;
        }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);restorePack(pack);MemoryUtil.memFree(b);}
    }
    public static void source(Object targets){
        if(!live())return;var f=(Framebuffer)field(targets,"scene");var before=glState();
        source=image(f);sourceDepth=depth(f);sourceEpoch=epoch;sourceState=glState();readbacksPreserved=before.equals(sourceState);
    }
    public static void beforeOverlay(){
        if(!live()||source==null||main!=null)return;var f=MinecraftClient.getInstance().getFramebuffer();var before=glState();
        main=image(f);mainDepth=depth(f);mainEpoch=epoch;mainState=glState();readbacksPreserved&=before.equals(mainState);
    }
    private static int channel(int c,int channel){return (c>>>(channel*8))&255;}
    private static int reference(int c,int levels,float amount,int x,int glY){
        if(levels==256)return c;int result=c&0xff000000;
        double threshold=amount*((BAYER[glY&3][x&3]+.5)/16-.5);
        for(int ch=0;ch<3;ch++){
            double step=Math.floor(channel(c,ch)/255.0*(levels-1)+.5+threshold);
            int value=(int)Math.round(Math.max(0,Math.min(levels-1,step))/(levels-1)*255);
            result|=value<<(ch*8);
        }return result;
    }
    private static int distance(int a,int b){int max=0;for(int ch=0;ch<3;ch++)max=Math.max(max,Math.abs(channel(a,ch)-channel(b,ch)));return max;}
    private static Map<String,Object> evaluate(int levels,float amount){
        int w=source.getWidth(),h=source.getHeight(),ow=main.getWidth(),oh=main.getHeight();
        int failures=0,maxError=0,quantSensitive=0,ditherSensitive=0,finiteDepthFailures=0,badGroups=0;double maxDepth=0;
        var examples=new ArrayList<Map<String,Object>>();int factor=oh/h;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int input=source.getColor(x,y),expected=reference(input,levels,amount,x,h-1-y);
            int ox=(int)Math.floor((x+.5)*ow/w),oy=(int)Math.floor((y+.5)*oh/h),actual=main.getColor(ox,oy),error=distance(expected,actual);
            maxError=Math.max(maxError,error);if(error>1){failures++;if(examples.size()<64)examples.add(Map.of("sourceX",x,"sourceY",y,"inputAbgr",Integer.toUnsignedString(input,16),"expectedAbgr",Integer.toUnsignedString(expected,16),"actualAbgr",Integer.toUnsignedString(actual,16)));}
            int palette=reference(input,32,0,x,h-1-y),dither=reference(input,32,.5f,x,h-1-y);
            if(distance(input,palette)>1)quantSensitive++;if(distance(palette,dither)>1)ditherSensitive++;
            float a=sourceDepth[y*w+x],b=mainDepth[oy*ow+ox];
            if(!Float.isFinite(a)||!Float.isFinite(b)||a<0||a>1||b<0||b>1)finiteDepthFailures++;else maxDepth=Math.max(maxDepth,Math.abs((double)a-b));
            int group=main.getColor(x*factor,y*factor);boolean bad=false;
            for(int yy=0;yy<factor;yy++)for(int xx=0;xx<factor;xx++)bad|=distance(group,main.getColor(x*factor+xx,y*factor+yy))>0;
            if(bad)badGroups++;
        }
        int count=w*h;double ratio=1-(double)failures/count,grid=1-(double)badGroups/count;
        var d=new LinkedHashMap<String,Object>();d.put("epoch",epoch);d.put("sourceEpoch",sourceEpoch);d.put("mainEpoch",mainEpoch);d.put("requestedLevels",levels);d.put("requestedDither",amount);d.put("productionControlsPresentAndApplied",controlsAvailable);
        d.put("sourceSize",List.of(w,h));d.put("mainSize",List.of(ow,oh));d.put("pixelSamples",count);d.put("rgbAgreement",ratio);d.put("badRgbSamples",failures);d.put("maxChannelError",maxError);d.put("quantizationSensitiveSourcePixels",quantSensitive);d.put("ditherSensitiveSourcePixels",ditherSensitive);
        boolean mainTarget=Objects.equals(mainState.get("drawFbo"),MinecraftClient.getInstance().getFramebuffer().fbo),fullViewport=Arrays.toString(new int[]{0,0,ow,oh}).equals(mainState.get("viewport"));
        d.put("actualMainTarget",mainTarget);d.put("fullMainViewport",fullViewport);
        d.put("depthFiniteFailures",finiteDepthFailures);d.put("maximumNearestDepthDelta",maxDepth);d.put("worldNearestGridRatio",grid);d.put("readbacksPreservedGlState",readbacksPreserved);d.put("sourceState",sourceState);d.put("mainState",mainState);d.put("rgbMismatchExamples",examples);
        int gl=GL11.glGetError();d.put("glError",gl);
        boolean pass=ratio>=.995&&quantSensitive>=500&&ditherSensitive>=500&&finiteDepthFailures==0&&maxDepth<=1e-6&&grid>.97&&readbacksPreserved&&gl==0&&sourceEpoch==epoch&&mainEpoch==epoch&&ow==w*factor&&oh==h*factor&&mainTarget&&fullViewport&&!ScenePresentation.stats().inWorldPass();
        d.put("referencePass",pass);return d;
    }
    private static void floats(Path path,float[] data)throws java.io.IOException{
        var bytes=ByteBuffer.allocate(data.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float value:data)bytes.putFloat(value);Files.write(path,bytes.array());
    }
    /** True holds only test fixture phase advancement; it never cancels an actual world render. */
    public static boolean rendered(){
        if(!ENABLED||!sampling)return false;
        if(source==null||main==null||sourceEpoch!=epoch||mainEpoch!=epoch)return true;
        if(++warmFrames<3)return true;warmFrames=0;
        boolean resized=fixturePhase()==4;int levels=resized?32:LEVELS[stage];float amount=resized?.5f:DITHER[stage];String name=resized?"dither32-resized":NAMES[stage];
        var result=evaluate(levels,amount);result.put("scene",name);
        var c=MinecraftClient.getInstance();
        try(var finalImage=image(c.getFramebuffer())){
            Path out=c.runDirectory.toPath().resolve("ps1-color-reference").resolve(name);Files.createDirectories(out);
            source.writeTo(out.resolve("source.png"));main.writeTo(out.resolve("world-before-overlays.png"));finalImage.writeTo(out.resolve("final.png"));floats(out.resolve("source-depth.f32"),sourceDepth);floats(out.resolve("main-depth.f32"),mainDepth);
            Files.writeString(out.resolve("reference.json"),new com.google.gson.Gson().toJson(result));RESULTS.add(result);
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        if(resized){
            resizedDone=true;boolean pass=RESULTS.stream().allMatch(r->Boolean.TRUE.equals(r.get("referencePass")))&&controlsAvailable;writeSummary(c,pass);
            if(!pass){c.scheduleStop();throw new IllegalStateException("PS1_COLOR_NATIVE actual resized3x reference failed; source/main/final preserved");}
            return false;
        }
        if(++stage<LEVELS.length)return true;
        initialDone=true;boolean pass=RESULTS.stream().allMatch(r->Boolean.TRUE.equals(r.get("referencePass")))&&controlsAvailable;
        writeSummary(c,pass);
        if(!pass){c.scheduleStop();throw new IllegalStateException("PS1_COLOR_NATIVE actual pre-overlay palette/dither reference failed; all four source/main/final references preserved");}
        setChoices(32,.5f);return true;
    }
    private static void writeSummary(MinecraftClient c,boolean pass){
        var d=new LinkedHashMap<String,Object>();d.put("initialReferenceStatesPreserved",initialDone);d.put("resizedReferencePreserved",resizedDone);d.put("referencePass",pass);d.put("productionControlsPresent",controlsAvailable);d.put("states",RESULTS);
        d.put("thresholds",Map.of("minRgbAgreement",.995,"maxChannelError",1,"minSensitiveSourcePixels",500,"maxNearestDepthDelta",1e-6,"minWorldGridRatio",.97));
        try{Files.writeString(c.runDirectory.toPath().resolve(pass?"ps1-color-ok.json":"ps1-color-negative.json"),new com.google.gson.Gson().toJson(d));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
