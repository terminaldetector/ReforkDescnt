package com.terminaldetector.drmd.mixin.client;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** References are borrowed for a nested pass; none of the outer frame's targets is cleared or deleted. */
@Mixin(WorldRenderer.class)
public interface WorldViewTargetsAccess {
 @Accessor("transparencyPostProcessor") PostEffectProcessor drmd$transparency();
 @Accessor("transparencyPostProcessor") void drmd$transparency(PostEffectProcessor p);
 @Accessor("entityOutlinePostProcessor") PostEffectProcessor drmd$outlines();
 @Accessor("entityOutlinePostProcessor") void drmd$outlines(PostEffectProcessor p);
 @Accessor("translucentFramebuffer") Framebuffer drmd$translucent();
 @Accessor("translucentFramebuffer") void drmd$translucent(Framebuffer p);
 @Accessor("entityFramebuffer") Framebuffer drmd$entities();
 @Accessor("entityFramebuffer") void drmd$entities(Framebuffer p);
 @Accessor("particlesFramebuffer") Framebuffer drmd$particles();
 @Accessor("particlesFramebuffer") void drmd$particles(Framebuffer p);
 @Accessor("weatherFramebuffer") Framebuffer drmd$weather();
 @Accessor("weatherFramebuffer") void drmd$weather(Framebuffer p);
 @Accessor("cloudsFramebuffer") Framebuffer drmd$clouds();
 @Accessor("cloudsFramebuffer") void drmd$clouds(Framebuffer p);
 @Accessor("entityOutlinesFramebuffer") Framebuffer drmd$outlineTarget();
 @Accessor("entityOutlinesFramebuffer") void drmd$outlineTarget(Framebuffer p);
}
