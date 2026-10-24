package com.terminaldetector.drmd.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** Unlit neutral-colour disks; no damage overlay, directional lighting or duplicate back faces. */
public final class ShotRenderLayers extends RenderLayer {
    private static net.minecraft.client.gl.ShaderProgram shader;
    public static final RenderLayer GLOW = of("drmd_shot", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 1536, false, true, MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(() -> shader))
                    .transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING)
                    .lightmap(DISABLE_LIGHTMAP).overlay(DISABLE_OVERLAY_COLOR)
                    .writeMaskState(COLOR_MASK).build(false));

    private ShotRenderLayers() {
        super("drmd_shot_phases", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS, 1536, false, true, () -> {}, () -> {});
    }

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Identifier.of("drmd", "shot_core"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                program -> shader = program));
    }
}
