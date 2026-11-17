package com.terminaldetector.drmd.client.presentation;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.*;

/** Quantize only the completed world texture; full-resolution overlays are drawn afterwards. */
final class Ps1ColorPass {
    private static ShaderProgram program;
    private Ps1ColorPass() {}

    static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Identifier.of("drmd", "ps1_colour"), VertexFormats.POSITION, shader -> program = shader));
    }

    static boolean draw(Framebuffer scene, Framebuffer output, Ps1ColorOptions options) {
        if (program == null) return false;
        var previous = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0), active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int binding0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(active);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.disableBlend();
            RenderSystem.setShader(() -> program);
            RenderSystem.setShaderTexture(0, scene.getColorAttachment());
            program.getUniform("SceneSize").set((float) scene.textureWidth, (float) scene.textureHeight);
            program.getUniform("OutputSize").set((float) output.textureWidth, (float) output.textureHeight);
            program.getUniform("ColorLevels").set((float) options.levels());
            program.getUniform("DitherStrength").set(options.ditherStrength());
            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            buffer.vertex(-1, -1, 0); buffer.vertex(1, -1, 0); buffer.vertex(1, 1, 0); buffer.vertex(-1, 1, 0);
            var built = buffer.endNullable();
            if (built == null) throw new IllegalStateException("Missing PS1 colour quad");
            BufferRenderer.drawWithGlobalProgram(built);
            return true;
        } finally {
            RenderSystem.setShaderTexture(0, texture);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(binding0);
            GlStateManager._activeTexture(active); GlStateManager._bindTexture(activeBinding);
            RenderSystem.depthMask(write);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previous);
            GlStateManager._glUseProgram(previousProgram);
        }
    }
}
