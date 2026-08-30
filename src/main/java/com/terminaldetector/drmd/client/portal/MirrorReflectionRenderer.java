package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Phase R1a of the native portal-rendering plan ({@code spicy-jumping-anchor.md}) — a same-dimension
 * literal mirror, reflected by actually re-rendering the world from a moved-and-turned camera, not a
 * flat texture or a fake reversed model.
 *
 * <p><b>Deliberately unmasked for now.</b> The real ImmPtl technique stencil-masks the recursive render
 * to just the mirror's own screen silhouette ({@code RendererUsingStencil}, 7 GL state steps — see the
 * plan's R1a section). That is real, novel, first-ever-in-this-project GL state code stacked on top of
 * an already-uncertain camera/matrix reconstruction (see below) — shipping both at once would mean that
 * if the live client shows something wrong, there would be no way to tell which of the two broke it
 * without a debugger this sandbox doesn't have. So this first cut skips the mask entirely: when
 * {@link DescentConfig#mirrorReflection} is on and the nearest qualifying mirror is in range, its
 * reflection fills the <em>whole</em> screen for that frame, not just the mirror's own outline. That is
 * not the finished feature — it is a deliberately minimal, visually obvious way for a live client to
 * confirm or deny the one thing that can't be checked here: does reflecting the camera through
 * {@link PortalTransform} and recursively calling {@code WorldRenderer.render} actually produce a
 * correctly-oriented view of the world at all. The stencil mask is the next increment once that is
 * confirmed, not before.
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

		recursionDepth++;
		try {
			accessor.drmd$invokeSetPos(fromPure(reflectedPos));
			accessor.drmd$invokeSetRotation((float) reflectedAngles.yawDegrees(), (float) reflectedAngles.pitchDegrees());

			// See the class doc comment: this reconstruction, not the scan or the recursion itself, is
			// the line to re-derive first if a live test shows a mis-oriented (not just unmasked) result.
			Matrix4f reflectedPositionMatrix = new Matrix4f().rotation(camera.getRotation());

			context.worldRenderer().render(
					context.tickCounter(),
					false, // renderBlockOutline: the outer view's own outline overlay, not meaningful reflected
					camera,
					context.gameRenderer(),
					context.lightmapTextureManager(),
					context.projectionMatrix(), // FOV/aspect/near/far — unchanged by moving the camera
					reflectedPositionMatrix);
		} finally {
			accessor.drmd$invokeSetPos(originalPos);
			accessor.drmd$invokeSetRotation(originalYaw, originalPitch);
			recursionDepth--;
		}
	}

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}

	private static Vec3d fromPure(PortalTransform.Vec3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}
}
