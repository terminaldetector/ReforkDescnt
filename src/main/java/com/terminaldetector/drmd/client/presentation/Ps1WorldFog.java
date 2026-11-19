package com.terminaldetector.drmd.client.presentation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** One actual main-world atmosphere; native overlays and nested views retain their own fog state. */
public final class Ps1WorldFog {
    public record State(float start,float end,float[] colour,FogShape shape){public State{colour=colour.clone();}public float[] colour(){return colour.clone();}}
    private static State nativeFog;
    private static Ps1FogRange range;
    private static Vec3d sky=Vec3d.ZERO;
    private static float density;
    private Ps1WorldFog(){}
    public static State capture(){return new State(RenderSystem.getShaderFogStart(),RenderSystem.getShaderFogEnd(),RenderSystem.getShaderFogColor(),RenderSystem.getShaderFogShape());}
    public static void restore(State s){var rgb=s.colour();RenderSystem.setShaderFogStart(s.start());RenderSystem.setShaderFogEnd(s.end());RenderSystem.setShaderFogColor(rgb[0],rgb[1],rgb[2],rgb[3]);RenderSystem.setShaderFogShape(s.shape());}
    public static void finishWorld(){if(nativeFog!=null)restore(nativeFog);nativeFog=null;range=null;density=0;sky=Vec3d.ZERO;}
    public static boolean active(){return nativeFog!=null&&range!=null&&ScenePresentation.inLowResolutionWorld()&&!OffscreenWorldView.busy();}
    public static State suspendForCloud(){if(!active())return null;var styled=capture();restore(nativeFog);return styled;}
    public static void resumeAfterCloud(State styled){if(styled!=null)restore(styled);}
    /** Called AFTER vanilla computes each actual fog mode. No camera/fog policy is guessed from the menu. */
    public static void apply(Camera camera,boolean protectedFog,float tickDelta){
        if(OffscreenWorldView.busy())return;
        var c=MinecraftClient.getInstance();float configured=DescentConfig.ps1FarFog.effective(DescentConfig.presentationProfile,ScenePresentation.inLowResolutionWorld(),false);
        if(protectedFog||configured<=0||c.world==null||c.world.getRegistryKey()!=World.OVERWORLD){
            if(nativeFog!=null){var rgb=nativeFog.colour();RenderSystem.setShaderFogColor(rgb[0],rgb[1],rgb[2],rgb[3]);}
            nativeFog=null;range=null;density=0;return;
        }
        var desired=Ps1FogRange.at(camera.getPos().y,configured,c.options.getClampedViewDistance()*16f);
        if(desired.isEmpty()){
            if(nativeFog!=null){var rgb=nativeFog.colour();RenderSystem.setShaderFogColor(rgb[0],rgb[1],rgb[2],rgb[3]);}
            nativeFog=null;range=null;density=0;return;
        }
        var computed=capture();
        // Vanilla computes sky and terrain ranges separately but does not reset RGB on each
        // applyFog call. Keep the original colour, not the RGB installed by our earlier sky call.
        nativeFog=nativeFog==null?computed:new State(computed.start(),computed.end(),nativeFog.colour(),computed.shape());
        range=desired.get();density=Ps1Atmosphere.density(camera.getPos().y,configured);
        sky=c.world.getSkyColor(camera.getPos(),tickDelta).multiply(com.terminaldetector.drmd.client.sky.Ps1SkyBackground.skyExposure());
        RenderSystem.setShaderFogStart(range.start());RenderSystem.setShaderFogEnd(range.end());
        RenderSystem.setShaderFogColor((float)sky.x,(float)sky.y,(float)sky.z,nativeFog.colour()[3]);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
    /** Every shader draw resets its fields, including Native/Debug/underwater/offscreen fallbacks. */
    public static void uniforms(ShaderProgram shader){
        boolean enabled=active();shader.getUniform("Ps1FarFogDensity").set(enabled?density:0);
        shader.getUniform("Ps1SkyFogMix").set(enabled?1f:0f);
        shader.getUniform("Ps1SkyFogColor").set(enabled?(float)sky.x:0,enabled?(float)sky.y:0,enabled?(float)sky.z:0);
        shader.getUniform("Ps1FogStart").set(enabled?range.start():0);
        shader.getUniform("Ps1FogEnd").set(enabled?range.end():1);
    }
    /** Cloud coverage/alpha belongs to its original renderer; only its resulting RGB gains the common atmosphere. */
    public static void cloudUniforms(ShaderProgram shader){
        var stock=active()?nativeFog:capture();
        shader.getUniform("CloudStockFogStart").set(stock.start());shader.getUniform("CloudStockFogEnd").set(stock.end());
        shader.getUniform("CloudStockFogColor").set(stock.colour()[0],stock.colour()[1],stock.colour()[2],stock.colour()[3]);
        shader.getUniform("CloudStockFogShape").set(stock.shape().getId());uniforms(shader);
    }
}
