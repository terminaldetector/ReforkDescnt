package com.terminaldetector.drmd.client.sky;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** The existing END sky cube at background depth; no terrain fog or additional sky layer. */
public final class SkyEnvelopeShader {
    private static ShaderProgram program;

    private SkyEnvelopeShader() {}

    public static void register() {
        Ps1SkyBackground.register();
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Identifier.of("drmd", "sky_envelope"), VertexFormats.POSITION_COLOR,
                shader -> program = shader));
    }

    public static ShaderProgram program() { return program; }
}
