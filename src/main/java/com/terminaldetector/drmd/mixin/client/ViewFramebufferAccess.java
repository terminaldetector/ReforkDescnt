package com.terminaldetector.drmd.mixin.client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MinecraftClient.class)
public interface ViewFramebufferAccess {
 @Mutable @Accessor("framebuffer") void drmd$framebuffer(Framebuffer framebuffer);
}
