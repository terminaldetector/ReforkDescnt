package com.terminaldetector.drmd.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders the world a second time from a moved camera and puts the result on screen inside one
 * rectangle. The whole of "see somewhere else through a surface", with no opinion about why the camera
 * moved — a mirror reflects it, a portal carries it to its partner, and both want exactly this.
 *
 * <p>Extracted when the portal view arrived rather than copied into it. The dance below is short but
 * every step of it is load-bearing in a way that would not survive being maintained twice: the order of
 * the restores, the depth-test line at the end, the single shared depth counter.
 *
 * <p><b>The scissor is the trick that keeps this shader-free.</b> The result is blitted back
 * <em>full-screen</em> and only the clip is narrowed, so every pixel keeps the exact screen position it
 * was rendered at and no texture coordinates exist to interpolate. Texturing a quad instead is what
 * forces ImmPtl into its own {@code DrawFbInAreaShader}: per-vertex screen-space UVs get interpolated
 * perspective-correctly across the quad and come out distorted. Its other strategy, a stencil mask,
 * needs a stencil attachment vanilla's main framebuffer does not have and ImmPtl gets from Porting Lib
 * — another mod. See {@code docs/PORTAL_RENDERING.md}.
 *
 * <p><b>One view at a time, and that is correctness, not budget.</b> The re-render draws into the single
 * {@link MirrorFramebuffer}, and the callbacks that start these views fire again inside it — so a nested
 * view would bind that same target while its own blit reads from it. Sampling a framebuffer bound for
 * writing is undefined in GL, not merely slow. What is missing for nesting is one target per layer, not
 * a cleverer gate, so {@link #busy()} is shared by every caller rather than counted per feature.
 *
 * <p>{@code positionMatrix} is rebuilt here as {@code new Matrix4f().rotation(camera.getRotation())},
 * the same shape vanilla itself uses: ImmPtl's real (non-decompiled)
 * {@code MixinGameRenderer.wrapCameraTransformation} wraps exactly that call inside
 * {@code GameRenderer.renderLevel}, one level above {@code WorldRenderer.render}. Calling the world
 * renderer directly means building it here instead of going through {@code GameRenderer} — cross-checked
 * against real source, but still the first line to re-derive if a live test shows a view in the wrong
 * <em>orientation</em> rather than merely in the wrong place.
 *
 * <p>No exceptions are caught: swallowing one mid-render could leave GL state — bound shader, matrix
 * stack depth, blend and depth state — half-changed for every frame after, which is worse than a loud
 * failure while this is still opt-in and off by default.
 */
public final class OffscreenWorldView {
	private OffscreenWorldView() {}

	private static int depth = 0;

	/** How many of these views are on the stack — 0 outside one. */
	public static int depth() {
		return depth;
	}

	/** True while a view is being rendered, so callers can decline to start another. */
	public static boolean busy() {
		return depth > 0;
	}

	/**
	 * Draw the world from {@code viewPos}/{@code viewYaw}/{@code viewPitch}, clipped to {@code box}.
	 *
	 * @return true when something actually reached the screen, false when it was skipped — the caller
	 *         should not spend one of its per-frame slots on a view that never drew.
	 */
	public static boolean render(WorldRenderContext context, CameraAccessor accessor, Camera camera,
			Vec3d viewPos, float viewYaw, float viewPitch, MirrorScreenBounds.Box box) {
		if (!box.valid()) return false;
		Framebuffer target = MirrorFramebuffer.get();
		if (target == null) return false; // window has no area this frame — nothing to draw into

		Vec3d originalPos = camera.getPos();
		float originalYaw = camera.getYaw();
		float originalPitch = camera.getPitch();

		MinecraftClient mc = MinecraftClient.getInstance();
		depth++;
		try {
			accessor.drmd$invokeSetPos(viewPos);
			accessor.drmd$invokeSetRotation(viewYaw, viewPitch);

			Matrix4f positionMatrix = new Matrix4f().rotation(camera.getRotation());

			target.setClearColor(0f, 0f, 0f, 1f);
			target.clear(MinecraftClient.IS_SYSTEM_MAC);
			target.beginWrite(true);

			context.worldRenderer().render(
					context.tickCounter(),
					false, // renderBlockOutline: the outer view's own outline overlay, not meaningful here
					camera,
					context.gameRenderer(),
					context.lightmapTextureManager(),
					context.projectionMatrix(), // FOV/aspect/near/far — unchanged by moving the camera
					positionMatrix);
		} finally {
			// Restore in the reverse order of setup, and unconditionally: leaving the camera moved or the
			// off-screen target bound would corrupt every system that reads either next frame, not just
			// this one view's picture.
			target.endWrite();
			mc.getFramebuffer().beginWrite(true);
			accessor.drmd$invokeSetPos(originalPos);
			accessor.drmd$invokeSetRotation(originalYaw, originalPitch);
			depth--;
		}

		// Outside the try/finally: the camera and the bound target must be back to normal first.
		RenderSystem.enableScissor(box.x(), box.y(), box.width(), box.height());
		try {
			target.draw(MirrorFramebuffer.width(), MirrorFramebuffer.height());
		} finally {
			RenderSystem.disableScissor();
		}
		// draw() leaves the depth test disabled (it restores the depth *mask* and the colour mask, but
		// not this), and we are still inside the world render — anything drawn after us in the same frame
		// would lose its depth sorting. Cheap to put back, and the alternative is a bug that reads as
		// "the mirror broke some unrelated renderer".
		RenderSystem.enableDepthTest();
		return true;
	}
}
