package com.terminaldetector.drmd.client.planet;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** Camera-current radial projection of world-anchored source vertices; GPU geometry stays cached. */
public final class HorizonShader {
    private static ShaderProgram program;
    private HorizonShader() {}
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context->context.register(
                Identifier.of("drmd","horizon_world"),VertexFormats.POSITION_COLOR,shader->program=shader));
    }
    public static ShaderProgram program() { return program; }
    public static void position(float x,float y,float z,float inner,float reach) {
        if(program==null) return;
        program.getUniform("FieldOriginDelta").set(x,y,z);
        program.getUniform("InnerRadius").set(inner);
        program.getUniform("FieldReach").set(reach);
        // The same cached program is reused: reset explicitly for Native/Debug/unsupported phases too.
        program.getUniform("Ps1FarFogDensity").set(DescentConfig.ps1FarFog.effective(
                DescentConfig.presentationProfile,ScenePresentation.inLowResolutionWorld(),OffscreenWorldView.busy()));
    }
}
