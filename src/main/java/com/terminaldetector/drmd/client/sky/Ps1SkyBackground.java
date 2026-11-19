package com.terminaldetector.drmd.client.sky;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Existing far-depth sky cube BEFORE vanilla sky, so the sun/moon/stars draw normally over it. */
public final class Ps1SkyBackground {
    private static VertexBuffer buffer;
    private static Object world;
    private static long draws;
    private static java.lang.reflect.Method caveFactor;
    private static boolean lookedUp;
    private static float unavailableExposure=1;
    private Ps1SkyBackground(){}
    public static long completedDraws(){return draws;}
    public static void register(){
        ClientLifecycleEvents.CLIENT_STOPPING.register(c->release());
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(world!=c.world){release();world=c.world;}});
    }
    private static void release(){if(buffer!=null){buffer.close();buffer=null;}}
    public static float skyExposure(){
        if(!lookedUp){lookedUp=true;try{caveFactor=Class.forName("dev.fallingcloud.bettercubicchunks.client.CaveSky").getMethod("factor");}
            catch(ClassNotFoundException noCubic){return 1;}catch(ReflectiveOperationException unavailable){unavailableExposure=0;return 0;}}
        if(caveFactor==null)return unavailableExposure;
        try{float value=((Number)caveFactor.invoke(null)).floatValue();return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
        catch(ReflectiveOperationException unavailable){return 0;}
    }
    public static boolean protectedView(Camera camera,boolean foggy){
        var focus=camera.getFocusedEntity();
        return foggy||camera.getSubmersionType()!=CameraSubmersionType.NONE||focus==null||focus.getWorld()!=MinecraftClient.getInstance().world||skyExposure()<=0
            ||focus instanceof LivingEntity living&&(living.hasStatusEffect(StatusEffects.BLINDNESS)||living.hasStatusEffect(StatusEffects.DARKNESS));
    }
    public static void draw(Matrix4f view,Matrix4f projection,float delta,Camera camera,boolean foggy){
        var c=MinecraftClient.getInstance();
        if(!ScenePresentation.inLowResolutionWorld()||OffscreenWorldView.busy()||c.world==null||c.world.getRegistryKey()!=World.OVERWORLD
            ||DescentConfig.ps1FarFog.density()<=0||protectedView(camera,foggy)||SkyEnvelopeShader.program()==null)return;
        int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int[] polygon=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygon);float[] colour=RenderSystem.getShaderColor().clone();var shader=RenderSystem.getShader();
        try{
            if(buffer==null){
                var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);float s=256;
                float[][] points={{-s,-s,-s},{s,-s,-s},{s,s,-s},{-s,s,-s},{-s,-s,s},{s,-s,s},{s,s,s},{-s,s,s}};
                for(int[] face:new int[][]{{0,1,2,3},{4,5,6,7},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}})for(int i:face)b.vertex(points[i][0],points[i][1],points[i][2]).color(255,255,255,255);
                buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(b.end());
            }
            var sky=c.world.getSkyColor(camera.getPos(),delta);float exposure=skyExposure();
            RenderSystem.setShaderColor((float)sky.x*exposure,(float)sky.y*exposure,(float)sky.z*exposure,1);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            buffer.bind();buffer.draw(view,projection,SkyEnvelopeShader.program());draws++;
        }catch(RuntimeException failure){
            release();com.terminaldetector.drmd.diag.DiagProblems.record("sky","PS1 background unavailable: "+failure);
        }finally{
            GlStateManager._glBindVertexArray(vao);GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER,array);GlStateManager._glUseProgram(program);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(colour[0],colour[1],colour[2],colour[3]);RenderSystem.depthFunc(func);RenderSystem.depthMask(write);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(polygon[0]==polygon[1])GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygon[0]);else{GL11.glPolygonMode(GL11.GL_FRONT,polygon[0]);GL11.glPolygonMode(GL11.GL_BACK,polygon[1]);}
        }
    }
}
