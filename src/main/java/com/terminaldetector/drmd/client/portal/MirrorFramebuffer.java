package com.terminaldetector.drmd.client.portal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

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
 * <p>OffscreenWorldView borrows Minecraft's main-target reference for each nested pass.
 * NestedViewTargets detaches the outer frame's Fabulous transparency and outline targets,
 * restores them without clearing/deleting them, then composites only the actual apertures.

 */
public final class MirrorFramebuffer {
	private static Framebuffer framebuffer;
	/** What the target was last sized to — only for deciding when it needs resizing. */
	private static int width;
	private static int height;
    private static final MirrorTargetGuard GUARD=new MirrorTargetGuard();
    private static String skipped="not requested";
    // Own the object before resize allocates attachments, so a partial driver failure is deletable.
    private static final class ReflectionTarget extends Framebuffer {ReflectionTarget(){super(true);}}

	private MirrorFramebuffer() {}

    public static void release() {
        if(framebuffer!=null){framebuffer.delete();framebuffer=null;}
        width=height=0;
        GUARD.reset();skipped="released";
    }

    public static String unavailableReason(){return skipped!=null?skipped:GUARD.reason();}

	/**
	 * The target, sized to the window. Created on first use and resized when the window changes —
	 * released on world changes or when both view features are OFF.
	 *
	 * @return the framebuffer, or {@code null} if the window has no area yet (minimised, or called
	 *         before the window is up) — the caller must treat that as "skip this frame" rather than
	 *         as an error.
	 */
	public static Framebuffer get() {
        if(!com.terminaldetector.drmd.client.config.DescentConfig.mirrorReflection
                && !com.terminaldetector.drmd.client.config.DescentConfig.portalSeeThrough){skipped="both view features disabled";return null;}
		MinecraftClient mc = MinecraftClient.getInstance();
        if(mc.getWindow().getFramebufferWidth()<=0||mc.getWindow().getFramebufferHeight()<=0){skipped="window reports no area";return null;}
        // The current outer target can be the PS1 scene. Projection/scissor coordinates
        // and the reflected target must use that same raster, never the UI/window size.
		int w = mc.getFramebuffer().textureWidth;
		int h = mc.getFramebuffer().textureHeight;
        skipped=null;var outer=mc.getFramebuffer();
        return GUARD.acquire(w,h,com.mojang.blaze3d.systems.RenderSystem.maxSupportedTextureSize(),()->{
            if(framebuffer==null)framebuffer=new ReflectionTarget();
            if(w!=width||h!=height){framebuffer.resize(w,h,MinecraftClient.IS_SYSTEM_MAC);width=w;height=h;}
            return framebuffer;
        },()->{
            var failed=framebuffer;framebuffer=null;width=height=0;
            if(failed!=null)failed.delete();
        },()->outer.beginWrite(true));
	}

	/**
	 * Width of the current outer scene target, read independently of the cached reflection target.
     * Normally this is the window raster; PS1 presentation supplies its smaller scene raster.
	 *
	 * <p>Reading the field instead was a deadlock, and a silent one. Callers measure a face on screen
	 * before deciding whether to render it, and that measurement needs a size; the field is only set by
	 * {@link #get()}, which is only reached after the measurement succeeds. Starting at zero, the
	 * measurement always failed, so {@code get()} was never called, so the field stayed zero — no mirror
	 * and no portal would ever have drawn, on any frame, with nothing in a log to say why. Asking the
	 * current outer target removes that ordering question, and the answer is the same one {@code get()} sizes
	 * the target to.
	 */
	public static int width() {
        var mc=MinecraftClient.getInstance();return mc.getWindow().getFramebufferWidth()<=0?0:mc.getFramebuffer().textureWidth;
	}

	/** Height the next render will use — see {@link #width()}. */
	public static int height() {
        var mc=MinecraftClient.getInstance();return mc.getWindow().getFramebufferHeight()<=0?0:mc.getFramebuffer().textureHeight;
	}
}
