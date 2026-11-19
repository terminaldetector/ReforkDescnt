package com.terminaldetector.drmd.client.cloud;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

final class AltitudeCloudShaders {
    static ShaderProgram occlusion,clouds,cloudsVanilla;
    private AltitudeCloudShaders(){}
    static void register(){CoreShaderRegistrationCallback.EVENT.register(context->{
        context.register(Identifier.of("drmd","horizon_cloud_occlusion"),VertexFormats.POSITION_TEXTURE_COLOR,p->occlusion=p);
        context.register(Identifier.of("drmd","altitude_clouds"),VertexFormats.POSITION_COLOR,p->clouds=p);
        context.register(Identifier.of("drmd","altitude_vanilla_clouds"),VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,p->cloudsVanilla=p);
    });}
}
