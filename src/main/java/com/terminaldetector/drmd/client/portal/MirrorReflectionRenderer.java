package com.terminaldetector.drmd.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Phase R1a of the native portal-rendering plan ({@code spicy-jumping-anchor.md}) — a same-dimension
 * literal mirror, reflected by actually re-rendering the world from a moved-and-turned camera, not a
 * flat texture or a fake reversed model.
 *
 * <p>The reflection is rendered into an off-screen target ({@link MirrorFramebuffer}) and then blitted
 * back, <b>scissored to the mirror's own screen rectangle</b> ({@link MirrorScreenBounds}). Clipping
 * rather than texturing is the trick that keeps this shader-free: the blit is still full-screen, so
 * every pixel keeps the exact screen position it was rendered at, and there are no texture coordinates
 * to interpolate.
 *
 * <p><b>Why a rectangle and not the mirror's real outline</b> is a finding, not laziness. Both of
 * ImmPtl's masking strategies need infrastructure DRMD does not have. The stencil one needs a stencil
 * attachment on the main framebuffer, which vanilla lacks and ImmPtl gets from Porting Lib — another
 * mod (see {@link MirrorFramebuffer}). The framebuffer one composites through a <em>custom shader</em>
 * ({@code DrawFbInAreaShader}) that takes the screen size as a uniform and derives its texture
 * coordinates from {@code gl_FragCoord} — and it has to, because per-vertex screen-space UVs get
 * interpolated perspective-correctly across the quad and come out distorted. DRMD ships no custom
 * shaders at all, so that composite is its own new infrastructure. A scissor box needs neither.
 *
 * <p>Known gaps of the rectangle, named rather than hidden: seen head-on it nearly coincides with the
 * face, seen at a steep angle it is larger and the reflection spills past the mirror's edges; and the
 * blit ignores depth, so something between the eye and the mirror does not occlude it. Both close when
 * the shader composite lands.
 *
 * <p>{@code reflectedPositionMatrix} in {@link #renderReflection} — {@code WorldRenderer.render}'s
 * {@code positionMatrix} parameter — is built the same shape vanilla itself uses: ImmPtl's own real
 * (non-decompiled) {@code MixinGameRenderer.wrapCameraTransformation} wraps the exact vanilla call
 * {@code new Matrix4f().rotation(camera.rotation())} inside {@code GameRenderer.renderLevel}, one level
 * above {@code WorldRenderer.render} — Mojmap's {@code camera.rotation()} is Yarn's {@code getRotation()}
 * (this codebase's own {@code getPos}/{@code getYaw}/{@code getPitch} already establish that Yarn "get"
 * prefix on {@code Camera}). Calling {@code WorldRenderer.render} directly here means DRMD has to build
 * that matrix itself instead of going through {@code GameRenderer}, but the shape is now cross-checked
 * against real source, not guessed. Still genuinely unverified: everything downstream of that line —
 * whether the whole recursive-render call chain actually paints a correct reflection on a live client.
 * If a live test shows the reflected geometry in the wrong place instead of merely unmasked, that is
 * where to start.
 *
 * <p>No exceptions are caught here on purpose: swallowing an exception mid-render could leave GL state
 * (bound shader, matrix stack depth, blend/depth state) half-changed for every frame after, which is
 * worse than a loud, visible failure while this is still opt-in and off by default.
 */
public final class MirrorReflectionRenderer {
	private MirrorReflectionRenderer() {}

	/** Deliberately short — a close-range "stand in front of the mirror and check" distance, not a
	 *  real gameplay render distance; this is a diagnostic, not the finished feature. */
	private static final double BASE_RENDER_RANGE = 24.0;
	/** Covers {@link #BASE_RENDER_RANGE} (24 blocks) with margin against chunk-boundary rounding. */
	private static final int SCAN_CHUNK_RADIUS = 2;
	private static final int SCAN_PERIOD_TICKS = 20;
	/**
	 * Pixels the scissor box is grown by. A rectangle is already not the mirror's exact shape, so a
	 * couple of pixels of slack costs nothing and keeps rounding from biting a line off the edge.
	 */
	private static final int SCISSOR_PAD = 2;

	private static int recursionDepth = 0;
	private static int scanAge = SCAN_PERIOD_TICKS;
	private static List<MirrorScanner.MirrorFace> cachedMirrors = List.of();

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MirrorReflectionRenderer::onClientTick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(MirrorReflectionRenderer::onAfterTranslucent);
	}

	/** Mirrors are static placed blocks — a once-a-second rescan is imperceptible lag between placing
	 *  or breaking one and it dis/appearing from this system, same trade TerrainMap3d already makes. */
	private static void onClientTick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) {
			cachedMirrors = List.of();
			return;
		}
		if (++scanAge < SCAN_PERIOD_TICKS) return;
		scanAge = 0;
		cachedMirrors = MirrorScanner.findNearby(mc.world, mc.player.getBlockPos(), SCAN_CHUNK_RADIUS);
	}

	private static void onAfterTranslucent(WorldRenderContext context) {
		if (!DescentConfig.mirrorReflection) return;
		if (recursionDepth >= MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH) return;
		if (cachedMirrors.isEmpty()) return;

		Camera camera = context.camera();
		// Unconditional cast, same idiom as CameraMixin's own use of this accessor: CameraAccessor is a
		// hard (non-require=0) mixin DRMD's whole camera system already depends on, so if it hadn't
		// applied, CameraMixin itself would already have failed long before this code ever runs.
		CameraAccessor accessor = (CameraAccessor) camera;

		// Unmasked means only one reflection can actually be visible on screen at a time (each draws
		// over the whole frame) — the nearest qualifying mirror is the obvious, simplest choice, and is
		// diagnostic-only behaviour worth re-examining once the stencil mask lets several coexist.
		MirrorScanner.MirrorFace nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		Vec3d cameraPos = camera.getPos();
		for (MirrorScanner.MirrorFace mirror : cachedMirrors) {
			double distance = cameraPos.distanceTo(mirror.planePoint());
			if (!MirrorRenderGate.shouldRender(recursionDepth, MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH,
					distance, BASE_RENDER_RANGE)) {
				continue;
			}
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = mirror;
			}
		}
		if (nearest == null) return;

		renderReflection(context, accessor, camera, nearest);
	}

	private static void renderReflection(WorldRenderContext context, CameraAccessor accessor, Camera camera,
			MirrorScanner.MirrorFace mirror) {
		Vec3d originalPos = camera.getPos();
		float originalYaw = camera.getYaw();
		float originalPitch = camera.getPitch();

		PortalTransform.Vec3 normal = toPure(mirror.normal());
		PortalTransform.Vec3 planePoint = toPure(mirror.planePoint());
		PortalTransform.Vec3 lookDirection = PortalTransform.yawPitchToVector(originalYaw, originalPitch);

		PortalTransform.Vec3 reflectedPos = PortalTransform.reflectPoint(toPure(originalPos), planePoint, normal);
		PortalTransform.Vec3 reflectedLook = PortalTransform.reflectVector(lookDirection, normal);
		PortalTransform.YawPitch reflectedAngles = PortalTransform.vectorToYawPitch(reflectedLook);

		Framebuffer target = MirrorFramebuffer.get();
		if (target == null) return; // window has no area this frame — nothing to draw into

		// Where the mirror's own face lands on screen, measured with the OUTER camera and its matrices,
		// before anything is reflected. Computed up front rather than after the render: an invalid box
		// means there is nowhere to put the result, so bailing here also skips a whole world render.
		MirrorScreenBounds.Box box = screenBox(context, mirror, originalPos);
		if (!box.valid()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		recursionDepth++;
		try {
			accessor.drmd$invokeSetPos(fromPure(reflectedPos));
			accessor.drmd$invokeSetRotation((float) reflectedAngles.yawDegrees(), (float) reflectedAngles.pitchDegrees());

			// See the class doc comment: this reconstruction, not the scan or the recursion itself, is
			// the line to re-derive first if a live test shows a mis-oriented (not just unmasked) result.
			Matrix4f reflectedPositionMatrix = new Matrix4f().rotation(camera.getRotation());

			target.setClearColor(0f, 0f, 0f, 1f);
			target.clear(MinecraftClient.IS_SYSTEM_MAC);
			target.beginWrite(true);

			context.worldRenderer().render(
					context.tickCounter(),
					false, // renderBlockOutline: the outer view's own outline overlay, not meaningful reflected
					camera,
					context.gameRenderer(),
					context.lightmapTextureManager(),
					context.projectionMatrix(), // FOV/aspect/near/far — unchanged by moving the camera
					reflectedPositionMatrix);
		} finally {
			// Restore in the reverse order of setup, and unconditionally: leaving the camera at a
			// reflected pose or the off-screen target bound would corrupt every system that reads either
			// next frame, not just this mirror's own picture.
			target.endWrite();
			mc.getFramebuffer().beginWrite(true);
			accessor.drmd$invokeSetPos(originalPos);
			accessor.drmd$invokeSetRotation(originalYaw, originalPitch);
			recursionDepth--;
		}

		// Still a full-screen blit — but scissored to the mirror's own screen rectangle, so what lands on
		// screen is confined to (roughly) the mirror. Clipping rather than texturing is what keeps this
		// shader-free: every pixel keeps the screen position it was rendered at, so there are no texture
		// coordinates to interpolate and none of the distortion that forces ImmPtl into a custom shader.
		// Outside the try/finally: the camera and the bound target must be back to normal first.
		RenderSystem.enableScissor(box.x(), box.y(), box.width(), box.height());
		try {
			target.draw(MirrorFramebuffer.width(), MirrorFramebuffer.height());
		} finally {
			RenderSystem.disableScissor();
		}
		// draw() leaves the depth test disabled (it restores the depth *mask* and colour mask, but not
		// this), and we are still inside the world render — anything drawn after us in the same frame
		// would lose its depth sorting. Cheap to put back, and the alternative is a bug that would read
		// as "the mirror broke some unrelated renderer".
		RenderSystem.enableDepthTest();
	}

	/**
	 * The mirror face's pixel bounding box under the current (unreflected) view.
	 *
	 * <p>The world is drawn camera-relative, so the face centre is offset by the camera position before
	 * projecting, and the matrix is {@code projection × positionMatrix} — the same pair
	 * {@code WorldRenderer.render} itself is handed, taken straight off the context rather than rebuilt.
	 */
	private static MirrorScreenBounds.Box screenBox(WorldRenderContext context,
			MirrorScanner.MirrorFace mirror, Vec3d cameraPos) {
		Matrix4f viewProjection = new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());
		float[] m = new float[16];
		viewProjection.get(m);

		PortalTransform.Vec3 centre = new PortalTransform.Vec3(
				mirror.planePoint().x - cameraPos.x,
				mirror.planePoint().y - cameraPos.y,
				mirror.planePoint().z - cameraPos.z);
		PortalTransform.Vec3[] corners =
				MirrorScreenBounds.faceCorners(centre, toPure(mirror.normal()), 1.0);

		return MirrorScreenBounds.project(corners, m,
				MirrorFramebuffer.width(), MirrorFramebuffer.height(), SCISSOR_PAD);
	}

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}

	private static Vec3d fromPure(PortalTransform.Vec3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}
}
