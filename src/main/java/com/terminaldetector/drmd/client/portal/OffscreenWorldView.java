package com.terminaldetector.drmd.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared native offscreen world view. Reflect or transform the camera, clip at the source/destination plane,
 * then composite depth-tested apertures with screen-space texture sampling. One target, no recursion. */
public final class OffscreenWorldView {
	private OffscreenWorldView() {}

	private static int depth = 0;

	private static Matrix4f frameProjection;
	private static Matrix4f framePosition;

	/**
	 * Capture the frame's own matrices before anything can overwrite them.
	 *
	 * <p>The second live report caught why one snapshot per listener is not enough. Both views listen on
	 * {@code AFTER_TRANSLUCENT}, and the listeners run in order: the mirror's runs first, renders a
	 * nested world, and that nested render re-enters Fabric's own mixin, which re-prepares the single
	 * shared {@code WorldRenderContext}. The portal's listener then runs in the same frame and reads
	 * back the mirror's matrices — which is how a clip plane was handed something with
	 * {@code m22 = 1.47} where a projection has −1.0001.
	 *
	 * <p>{@code START} fires immediately after that prepare, at the head of the world render, so this is
	 * the one moment in a frame when the context is guaranteed to describe the frame. Guarded on
	 * {@link #busy()} because a nested render fires {@code START} too, and its matrices are exactly the
	 * ones that must not be captured.
	 */
	public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.world!=lastWorld || !com.terminaldetector.drmd.client.config.DescentConfig.mirrorReflection
                    && !com.terminaldetector.drmd.client.config.DescentConfig.portalSeeThrough) {
                if(!busy()) {
                    MirrorFramebuffer.release();frameProjection=null;framePosition=null;lastWorld=mc.world;
                }
            }
        });
		WorldRenderEvents.START.register(context -> {
			if (busy()) return;
			frameProjection = new Matrix4f(context.projectionMatrix());
			framePosition = new Matrix4f(context.positionMatrix());
		});
	}

    private static net.minecraft.client.world.ClientWorld lastWorld;

	/** The frame's projection, or null before the first world render of a session. */
	public static Matrix4f frameProjection() {
		return frameProjection;
	}

	/** The frame's position matrix — the camera rotation the world is being drawn with. */
	public static Matrix4f framePosition() {
		return framePosition;
	}

	/** How many of these views are on the stack — 0 outside one. */
	public static int depth() {
		return depth;
	}

	/** True while a view is being rendered, so callers can decline to start another. */
	public static boolean busy() {
		return depth > 0;
	}

	/**
	 * Draw the transformed world, then composite the aperture polygons. The box is an early visibility gate.
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
			Vec3d clipPoint, Vec3d clipNormal, MirrorScreenBounds.Box box, Matrix4f viewMatrix, boolean reflected,
            java.util.List<PortalTransform.Vec3[]> apertures) {
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
		if(context.consumers() instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate immediate)immediate.draw();
        Framebuffer mainTarget=mc.getFramebuffer();
        Matrix4f savedProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var savedSorting=RenderSystem.getVertexSorting();
        var savedShader=RenderSystem.getShader();
        int originalWinding = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE);
		depth++;
		try {
			((com.terminaldetector.drmd.mixin.client.ViewFramebufferAccess)mc).drmd$framebuffer(target);
            accessor.drmd$invokeSetPos(viewPos);
			accessor.drmd$invokeSetRotation(viewYaw, viewPitch);

			Matrix4f positionMatrix = new Matrix4f(viewMatrix);
			if (reflected) org.lwjgl.opengl.GL11.glFrontFace(originalWinding == org.lwjgl.opengl.GL11.GL_CCW
				? org.lwjgl.opengl.GL11.GL_CW : org.lwjgl.opengl.GL11.GL_CCW);
			Matrix4f projection = clipped(outerProjection, positionMatrix, viewPos, clipPoint, clipNormal);
			// GameRenderer prepares visibility before the main render. A nested render must do
			// the same for its own camera; Sodium consumes this frustum when building its
			// visible section list, so reusing the outer one drops geometry behind the player.
			// Keep visibility conservative: vanilla expands this frustum around the camera.
			// An oblique near plane excludes that box and can make the expansion loop forever.
			// The actual draw still uses the oblique projection to clip at the aperture.
			context.worldRenderer().setupFrustum(viewPos, positionMatrix, outerProjection);

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
			org.lwjgl.opengl.GL11.glFrontFace(originalWinding);
			target.endWrite();
			((com.terminaldetector.drmd.mixin.client.ViewFramebufferAccess)mc).drmd$framebuffer(mainTarget);
            mainTarget.beginWrite(true);
			accessor.drmd$invokeSetPos(originalPos);
			accessor.drmd$invokeSetRotation(originalYaw, originalPitch);
			context.worldRenderer().setupFrustum(originalPos, framePosition, outerProjection);
			RenderSystem.setProjectionMatrix(savedProjection,savedSorting);
            RenderSystem.setShader(()->savedShader);
            depth--;
		}

        SurfaceComposite.draw(target, outerProjection, framePosition, originalPos, apertures);
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
