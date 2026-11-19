package com.terminaldetector.drmd.gate.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Route test cursor locations through Minecraft's GLFW callback without depending on Xvfb focus. */
@Mixin(Mouse.class)
public interface HudMouseInputProbe {
    @Invoker("onCursorPos")
    void drmd$probeCursorPos(long window, double x, double y);
}
