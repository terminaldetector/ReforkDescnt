package com.terminaldetector.drmd.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders the world a second time from a moved camera and puts the result on screen inside one
 * rectangle. The whole of "see somewhere else through a surface", with no opinion about why the camera
 * moved — a mirror reflects it, a portal carries it to its partner, and both want exactly this.
 *
 * <p>Extracted when the portal view arrived rather than copied into it. The dance below is short but
 * every step of it is load-bearing in a way that would not survive being maintained twice: the order of
 * the restores, the depth-test line at the end, the single shared depth counter.
 *
 * <p><b>The camera lands behind the surface, always.</b> A mirror reflects it through the glass, a
 * portal carries it behind its partner — so in both cases the surface's own block, and the wall it is
 * mounted on, stand between the camera and everything worth seeing. {@link ObliqueNearPlane} bends the
 * projection so its near plane <em>is</em> that surface, which is what turns the result into a picture
 * of somewhere else instead of a picture of a wall. Without it this machinery renders correctly and
 * shows nothing useful.
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
	 * @param outerProjection the frame's own projection, captured by the caller <b>before</b> any nested
	 *                        render. It cannot be read from the context here: calling
	 *                        {@code WorldRenderer.render} re-enters Fabric's own mixin on that method,
	 *                        which re-prepares the single shared {@code WorldRenderContext} with
	 *                        whatever this call passes. After the first nested view, the context's
	 *                        matrices describe that view rather than the frame — which is exactly how
	 *                        the second mirror in a frame ended up being handed a rotation matrix where
	 *                        it expected a projection.
	 * @param clipPoint  a point on the surface being seen through, in world space, or null for no
	 *                   clipping. Everything on the camera's own side of it is cut away — see
	 *                   {@link ObliqueNearPlane} for why that is what makes this a picture of somewhere
	 *                   else rather than of the wall the surface is mounted on.
	 * @param clipNormal that surface's outward normal, pointing at the side worth seeing.
	 * @return true when something actually reached the screen, false when it was skipped — the caller
	 *         should not spend one of its per-frame slots on a view that never drew.
	 */
	public static boolean render(WorldRenderContext context, CameraAccessor accessor, Camera camera,
			Matrix4f outerProjection, Vec3d viewPos, float viewYaw, float viewPitch,
			Vec3d clipPoint, Vec3d clipNormal, MirrorScreenBounds.Box box) {
		if (!box.valid()) return false;
		Framebuffer target = MirrorFramebuffer.get();
		if (target == null) {
			// Ordinarily a minimised window, but it is also one of the ways a mirror shows nothing at
			// all, so it is worth being able to rule out rather than assume.
			DiagProblems.record("portal", "no off-screen target this frame — the window reports no area");
			return false;
		}

		Vec3d originalPos = camera.getPos();
		float originalYaw = camera.getYaw();
		float originalPitch = camera.getPitch();

		MinecraftClient mc = MinecraftClient.getInstance();
		depth++;
		try {
			accessor.drmd$invokeSetPos(viewPos);
			accessor.drmd$invokeSetRotation(viewYaw, viewPitch);

			Matrix4f positionMatrix = new Matrix4f().rotation(camera.getRotation());
			Matrix4f projection = clipped(outerProjection, positionMatrix, viewPos, clipPoint, clipNormal);

			target.setClearColor(0f, 0f, 0f, 1f);
			target.clear(MinecraftClient.IS_SYSTEM_MAC);
			target.beginWrite(true);

			context.worldRenderer().render(
					context.tickCounter(),
					false, // renderBlockOutline: the outer view's own outline overlay, not meaningful here
					camera,
					context.gameRenderer(),
					context.lightmapTextureManager(),
					// Vanilla's own order, which is positionMatrix first and projectionMatrix second —
					// confirmed against Fabric's own mixin on this method, not from memory. Passing them
					// the other way round is what the first live diagnostics report caught.
					positionMatrix,
					projection);
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
		DiagTrace.count("view.drawn");
		// draw() leaves the depth test disabled (it restores the depth *mask* and the colour mask, but
		// not this), and we are still inside the world render — anything drawn after us in the same frame
		// would lose its depth sorting. Cheap to put back, and the alternative is a bug that reads as
		// "the mirror broke some unrelated renderer".
		RenderSystem.enableDepthTest();
		return true;
	}

	/**
	 * How far past the surface the clip plane is pushed, in blocks.
	 *
	 * <p>A millimetre, and not cosmetic. Left exactly on the surface, the surface's own front face lies
	 * on the plane and is kept or cut per pixel by float rounding — a shimmering line around the edge of
	 * every mirror. Pushing it into the kept side puts the face unambiguously on the discarded side,
	 * where it belongs: the point is to look through it, not at it.
	 */
	private static final double CLIP_NUDGE = 0.001;

	/**
	 * Vanilla's projection with its near plane moved onto {@code clipPoint}/{@code clipNormal}, or
	 * vanilla's own unchanged when there is no plane to apply or it cannot be expressed.
	 *
	 * <p>The world is drawn camera-relative — the camera sits at the origin and {@code positionMatrix}
	 * is a pure rotation — so view space is reached by rotating the offset from the camera, with no
	 * translation to undo.
	 */
	private static Matrix4f clipped(Matrix4f projection, Matrix4f positionMatrix, Vec3d viewPos,
			Vec3d clipPoint, Vec3d clipNormal) {
		if (clipPoint == null || clipNormal == null) return projection;

		Vector3f viewNormal = positionMatrix.transformDirection(
				new Vector3f((float) clipNormal.x, (float) clipNormal.y, (float) clipNormal.z));
		Vec3d nudged = clipPoint.add(clipNormal.normalize().multiply(CLIP_NUDGE));
		Vector3f viewPoint = positionMatrix.transformPosition(new Vector3f(
				(float) (nudged.x - viewPos.x),
				(float) (nudged.y - viewPos.y),
				(float) (nudged.z - viewPos.z)));

		PortalTransform.Vec3 normal = new PortalTransform.Vec3(viewNormal.x, viewNormal.y, viewNormal.z);
		PortalTransform.Vec3 point = new PortalTransform.Vec3(viewPoint.x, viewPoint.y, viewPoint.z);

		float[] source = new float[16];
		projection.get(source);
		float[] bent = ObliqueNearPlane.apply(source, normal, ObliqueNearPlane.offsetFor(normal, point));
		// Null means the plane could not be expressed against this projection. Rendering unclipped shows
		// the wall this exists to remove, which is wrong but visible and diagnosable; a guessed clip
		// plane hides the world instead.
		if (bent == null) {
			// This is diagnostic outcome five, the one that reads as "the back of the destination block
			// instead of the room". Recording it turns that from something to be guessed at into
			// something the report already says.
			DiagProblems.record("portal", "clip plane refused (" + ObliqueNearPlane.lastRefusal()
					+ ") — the view will show the surface's own block and the wall behind it");
			return projection;
		}
		return new Matrix4f().set(bent);
	}
}
