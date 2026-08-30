package com.terminaldetector.drmd.client.portal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * The off-screen target a mirror's reflected view is rendered into, before it is put on screen.
 *
 * <p>This is the framebuffer half of ImmPtl's two rendering strategies, and DRMD takes it rather than
 * the stencil one on purpose. {@code RendererUsingStencil} is ImmPtl's <em>default</em> and the better
 * technique, but it needs a stencil attachment on the main render target, which vanilla Minecraft does
 * not have: ImmPtl reaches it through a field Porting Lib injects
 * ({@code RenderTarget.port_lib$stencilEnabled}, read reflectively in {@code IPPortingLibCompat}), i.e.
 * it depends on another mod being installed. Adding that attachment ourselves means a mixin that
 * changes the main framebuffer's format for every frame the game draws — far too blunt an instrument
 * for a mirror, and unverifiable without a live client. {@code RendererUsingFrameBuffer} needs no such
 * thing, which is exactly why ImmPtl keeps it as its compatibility path.
 *
 * <p>One deliberate simplification against ImmPtl: it also swaps {@code Minecraft}'s own framebuffer
 * field while rendering ({@code IEMinecraftClient.ip_setFrameBuffer}), because vanilla re-binds the
 * main target itself under "fabulous" graphics and shader packs. DRMD has scoped both of those out
 * (see {@code spicy-jumping-anchor.md}'s deliberate scope cuts), so binding is enough here and no
 * mixin into the client is needed. Under fabulous graphics this is expected to degrade rather than
 * work — named as a known gap, not an oversight.
 */
public final class MirrorFramebuffer {
	private static Framebuffer framebuffer;
	private static int width;
	private static int height;

	private MirrorFramebuffer() {}

	/**
	 * The target, sized to the window. Created on first use and resized when the window changes —
	 * never deleted, since it lives as long as the client does and a mirror can come back into view
	 * at any time.
	 *
	 * @return the framebuffer, or {@code null} if the window has no area yet (minimised, or called
	 *         before the window is up) — the caller must treat that as "skip this frame" rather than
	 *         as an error.
	 */
	public static Framebuffer get() {
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = mc.getWindow().getFramebufferWidth();
		int h = mc.getWindow().getFramebufferHeight();
		if (w <= 0 || h <= 0) return null;

		if (framebuffer == null) {
			// useDepth: the reflected view is a full world render and needs its own depth buffer, or
			// near geometry would not occlude far geometry inside the reflection.
			framebuffer = new SimpleFramebuffer(w, h, true, MinecraftClient.IS_SYSTEM_MAC);
			width = w;
			height = h;
		} else if (w != width || h != height) {
			framebuffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
			width = w;
			height = h;
		}
		return framebuffer;
	}

	/** Width the target was last sized to — the blit needs it, and it is the window's, not the world's. */
	public static int width() {
		return width;
	}

	/** Height the target was last sized to. */
	public static int height() {
		return height;
	}
}
