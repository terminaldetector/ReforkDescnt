package com.terminaldetector.drmd.client.cloud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.mixin.client.WorldViewTargetsAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.*;

/** High clouds keep their native cached source; a separate true-ray witness orders distant LOD. */
public final class AltitudeCloudRenderer {
    public record Stats(long epoch,int witnessFbo,int witnessTexture,int width,int height,int ownerMainFbo,
                        boolean valid,boolean hasFarSource,long farDraws,long replacements,long fallbacks,String lastFallback,
                        double referenceY,double eyeY,float altitudeWeight,int focusedEntityId,boolean thirdPerson,String focusStatus,float depthCeiling){}
    private static FarFieldOcclusionTarget target;
    private static Matrix4f worldView,worldProjection;
    private static Quaternionf viewRotation;
    private static Vec3d camera;
    private static double viewerAltitude;
    private static double eyeAltitude;
    private static float selectedWeight;
    private static float selectedDepthCeiling=1;
    private static int focusedEntityId=-1;
    private static boolean thirdPerson;
    private static String focusStatus="UNOBSERVED";
    private static Object world;
    private static boolean valid,hasFarSource,delegated,failed,logged;
    private static int ownerMain,scopeDepth;
    private static final boolean[] scopes=new boolean[8];
    private static final com.terminaldetector.drmd.client.presentation.Ps1WorldFog.State[] cloudFog=new com.terminaldetector.drmd.client.presentation.Ps1WorldFog.State[8];
    private static long epoch,farDraws,replacements,fallbacks;
    private static String lastFallback="none";
    private AltitudeCloudRenderer(){}
    public static Stats stats(){return new Stats(epoch,target==null?0:target.framebuffer,target==null?0:target.texture,target==null?0:target.width,target==null?0:target.height,ownerMain,valid,hasFarSource,farDraws,replacements,fallbacks,lastFallback,viewerAltitude,eyeAltitude,selectedWeight,focusedEntityId,thirdPerson,focusStatus,selectedDepthCeiling);}
    public static void register(){
        AltitudeCloudShaders.register();
        WorldRenderEvents.START.register(ctx->{if(!OffscreenWorldView.busy()){epoch++;valid=false;hasFarSource=false;scopeDepth=0;failed=false;}});
        WorldRenderEvents.BEFORE_ENTITIES.register(AltitudeCloudRenderer::beginView);
        WorldRenderEvents.END.register(ctx->{if(!OffscreenWorldView.busy())scopeDepth=0;});
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(world!=null&&world!=c.world)release();});
        ClientLifecycleEvents.CLIENT_STOPPING.register(c->release());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){public Identifier getFabricId(){return Identifier.of("drmd","altitude_cloud_targets");}public void reload(ResourceManager resources){MinecraftClient.getInstance().execute(AltitudeCloudRenderer::release);}});
    }
    private static void release(){if(target!=null){target.close();target=null;}valid=hasFarSource=delegated=failed=logged=false;worldView=worldProjection=null;viewRotation=null;camera=null;world=null;scopeDepth=0;viewerAltitude=eyeAltitude=selectedWeight=0;selectedDepthCeiling=1;focusedEntityId=-1;focusStatus="UNOBSERVED";}
    private static boolean eligible(MinecraftClient c){
        if(OffscreenWorldView.busy()||c.world==null||c.world.getRegistryKey()!=World.OVERWORLD||!DescentConfig.cloudPolicy.enabled())return false;
        var actualCamera=c.gameRenderer.getCamera();net.minecraft.entity.Entity focus=actualCamera.getFocusedEntity();
        if(focus==null||focus.isRemoved()||focus.getWorld()!=c.world){focusStatus="UNSUPPORTED focused view";if(!failed)fallback("no valid focused entity in current world",null);return false;}
        var anchor=new AltitudeCloudPolicy.ViewAltitude(focus.getY(),actualCamera.getPos().y);
        if(!anchor.valid()){focusStatus="NONFINITE focused view";if(!failed)fallback("nonfinite focused reference/eye",null);return false;}
        viewerAltitude=anchor.referenceY();eyeAltitude=anchor.eyeY();selectedWeight=anchor.weight(DescentConfig.cloudPolicy);if(selectedWeight==0)selectedDepthCeiling=1;focusedEntityId=focus.getId();thirdPerson=actualCamera.isThirdPerson();focusStatus="OBSERVED Camera.getFocusedEntity / Entity.getY";return selectedWeight>0;
    }
    private static void fallback(String reason,RuntimeException failure){valid=false;failed=true;fallbacks++;lastFallback=reason;if(!logged){logged=true;DescentMod.LOGGER.warn("Altitude cloud extension uses original renderer: {}",reason);if(failure!=null)DescentMod.LOGGER.debug("Altitude cloud extension first failure",failure);}}
    private static void beginView(WorldRenderContext ctx){
        var c=MinecraftClient.getInstance();if(!eligible(c))return;
        world=c.world;camera=new Vec3d(ctx.camera().getPos().x,ctx.camera().getPos().y,ctx.camera().getPos().z);viewRotation=new Quaternionf(ctx.camera().getRotation());worldView=new Matrix4f(ctx.positionMatrix());worldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());var main=c.getFramebuffer();ownerMain=main.fbo;
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=ownerMain){fallback("main world target ownership unavailable",null);return;}
        if(AltitudeCloudShaders.clouds==null||AltitudeCloudShaders.cloudsVanilla==null||AltitudeCloudShaders.occlusion==null){fallback("cloud shaders unavailable",null);return;}
        try{
            if(!AltitudeCloudPolicy.acceptsTarget(main.textureWidth,main.textureHeight,RenderSystem.maxSupportedTextureSize())){fallback("semantic target exceeds96MiB or GPU limit",null);return;}
            if(target==null||target.width!=main.textureWidth||target.height!=main.textureHeight){var old=target;target=null;if(old!=null)old.close();target=new FarFieldOcclusionTarget(main.textureWidth,main.textureHeight);}
            try(var saved=new FarFieldOcclusionTarget.State()){target.begin();}valid=true;
        }catch(RuntimeException failure){fallback("semantic target unavailable: "+failure,failure);}
    }
    /** Called only after the existing colour draw prepared and used this exact cached VBO. */
    public static void captureFar(PlanetSurfaceMesh source,Matrix4f view,float x,float y,float z,float inner,float reach,float[] orbitalMask){
        var c=MinecraftClient.getInstance();if(!valid||failed||!eligible(c))return;
        if(hasFarSource){fallback("more than one main-world semantic draw",null);return;}
        if(source.quads>160_000){fallback("far source exceeds existing VBO budget",null);return;}
        try(var saved=new FarFieldOcclusionTarget.State()){
            target.begin();RenderSystem.setShader(()->AltitudeCloudShaders.occlusion);var shader=AltitudeCloudShaders.occlusion;
            shader.getUniform("FieldOriginDelta").set(x,y,z);shader.getUniform("InnerRadius").set(inner);shader.getUniform("FieldReach").set(reach);shader.getUniform("InverseProjection").set(new Matrix4f(worldProjection).invert());shader.getUniform("CloudTargetSize").set((float)target.width,(float)target.height);shader.getUniform("MaxRayDistance").set(AltitudeCloudPolicy.MAX_RAY_DISTANCE);
            shader.getUniform("FieldOuterRadius").set(HorizonExtent.outerRadius());
            com.terminaldetector.drmd.client.planet.HorizonShader.orbitalMask(shader,orbitalMask);
            HorizonVertexBuffer.drawSemantic(view,worldProjection,shader);hasFarSource=true;farDraws++;
        }catch(RuntimeException failure){fallback("far witness draw rejected: "+failure,failure);}
    }
    public static void enterCloudScope(boolean sodium){if(OffscreenWorldView.busy())return;if(scopeDepth>=scopes.length){fallback("cloud scope nesting exceeds bound",null);return;}cloudFog[scopeDepth]=com.terminaldetector.drmd.client.presentation.Ps1WorldFog.suspendForCloud();scopes[scopeDepth++]=sodium;}
    public static void leaveCloudScope(){if(!OffscreenWorldView.busy()&&scopeDepth>0){int at=--scopeDepth;com.terminaldetector.drmd.client.presentation.Ps1WorldFog.resumeAfterCloud(cloudFog[at]);cloudFog[at]=null;}}
    /** Return true only after executing the single replacement draw; caller cancels the outer call. */
    public static boolean drawCloud(VertexBuffer buffer,Matrix4f view,Matrix4f projection,ShaderProgram original){
        var c=MinecraftClient.getInstance();if(delegated||scopeDepth==0||original==null||!original.getName().contains("cloud"))return false;
        if(!eligible(c))return com.terminaldetector.drmd.client.presentation.Ps1WorldFog.active()&&drawNearFog(buffer,view,projection);
        if(!valid||failed||target==null||world!=c.world||ownerMain!=c.getFramebuffer().fbo){if(!failed)fallback("no matching current-view semantic witness",null);return false;}
        var main=c.getFramebuffer();int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);var clouds=((WorldViewTargetsAccess)c.worldRenderer).drmd$clouds();boolean matching=draw==ownerMain||clouds!=null&&draw==clouds.fbo&&clouds.textureWidth==target.width&&clouds.textureHeight==target.height;
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(!matching||main.textureWidth!=target.width||main.textureHeight!=target.height||viewport[0]!=0||viewport[1]!=0||viewport[2]!=target.width||viewport[3]!=target.height||c.gameRenderer.getCamera().getPos().squaredDistanceTo(camera)>.000001||Math.abs(c.gameRenderer.getCamera().getRotation().dot(viewRotation))<.999999){fallback("cloud target/viewport/camera does not own current witness",null);return false;}
        var format=buffer.getVertexFormat();var layout=CloudRenderCompatibility.layout(format.getVertexSizeByte(),
                format.has(VertexFormatElement.POSITION)?format.getOffset(VertexFormatElement.POSITION):-1,
                format.has(VertexFormatElement.UV_0)?format.getOffset(VertexFormatElement.UV_0):-1,
                format.has(VertexFormatElement.COLOR)?format.getOffset(VertexFormatElement.COLOR):-1,
                format.has(VertexFormatElement.NORMAL)?format.getOffset(VertexFormatElement.NORMAL):-1,format.getElements().size());
        if(layout==null){fallback("unsupported native cloud vertex format",null);return false;}
        float near=projection.m32()/(projection.m22()-1),far=projection.m32()/(projection.m22()+1);if(!(Float.isFinite(near)&&Float.isFinite(far)&&near>0&&far>near&&Math.abs(projection.m23()+1)<.0001&&Math.abs(projection.m33())<.0001)){fallback("unsupported native cloud projection",null);return false;}
        var policy=DescentConfig.cloudPolicy;float weight=policy.weight(viewerAltitude),extendedFar=policy.expandedFar(far,weight);var extended=new Matrix4f(projection).m22(-(extendedFar+near)/(extendedFar-near)).m32(-2*extendedFar*near/(extendedFar-near));
        delegated=true;
        try(var saved=new FarFieldOcclusionTarget.State()){
            saved.plainSampler(CloudRenderCompatibility.farSurfaceUnit(layout));var shader=layout==CloudRenderCompatibility.Layout.VANILLA_TEXTURED?AltitudeCloudShaders.cloudsVanilla:AltitudeCloudShaders.clouds;RenderSystem.setShader(()->shader);shader.addSampler("FarSurface",target.texture);shader.getUniform("HasFarSurface").set(1);shader.getUniform("OriginalProjection").set(projection);shader.getUniform("WorldFromView").set(new Matrix4f(worldView).invert());shader.getUniform("AltitudeWeight").set(weight);shader.getUniform("CloudRange").set(policy.range());shader.getUniform("StockCloudMode").set(scopes[scopeDepth-1]?1:0);selectedDepthCeiling=AltitudeCloudPolicy.farDepthCeiling(saved.depthFunc==GL11.GL_LESS);shader.getUniform("DepthCeiling").set(selectedDepthCeiling);
            com.terminaldetector.drmd.client.presentation.Ps1WorldFog.cloudUniforms(shader);
            buffer.draw(view,extended,shader);replacements++;return true;
        }finally{delegated=false;}
    }
    /** Low-altitude PS1 colour uses the original VBO/projection and alpha, without allocating a far witness. */
    private static boolean drawNearFog(VertexBuffer buffer,Matrix4f view,Matrix4f projection){
        var format=buffer.getVertexFormat();var layout=CloudRenderCompatibility.layout(format.getVertexSizeByte(),
            format.has(VertexFormatElement.POSITION)?format.getOffset(VertexFormatElement.POSITION):-1,
            format.has(VertexFormatElement.UV_0)?format.getOffset(VertexFormatElement.UV_0):-1,
            format.has(VertexFormatElement.COLOR)?format.getOffset(VertexFormatElement.COLOR):-1,
            format.has(VertexFormatElement.NORMAL)?format.getOffset(VertexFormatElement.NORMAL):-1,format.getElements().size());
        if(layout==null||AltitudeCloudShaders.clouds==null||AltitudeCloudShaders.cloudsVanilla==null)return false;
        delegated=true;
        try(var saved=new FarFieldOcclusionTarget.State()){
            var shader=layout==CloudRenderCompatibility.Layout.VANILLA_TEXTURED?AltitudeCloudShaders.cloudsVanilla:AltitudeCloudShaders.clouds;
            RenderSystem.setShader(()->shader);shader.addSampler("FarSurface",0);shader.getUniform("HasFarSurface").set(0);shader.getUniform("OriginalProjection").set(projection);
            shader.getUniform("WorldFromView").set(new Matrix4f());shader.getUniform("AltitudeWeight").set(0f);shader.getUniform("CloudRange").set(DescentConfig.cloudPolicy.range());
            shader.getUniform("StockCloudMode").set(scopes[scopeDepth-1]?1:0);shader.getUniform("DepthCeiling").set(1f);
            com.terminaldetector.drmd.client.presentation.Ps1WorldFog.cloudUniforms(shader);buffer.draw(view,projection,shader);return true;
        }finally{delegated=false;}
    }
}
