package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase R1a of the native portal-rendering plan ({@code spicy-jumping-anchor.md}) — a same-dimension
 * literal mirror, reflected by actually re-rendering the world from a moved-and-turned camera, not a
 * flat texture or a fake reversed model.
 *
 * <p>This file owns only what is specific to a mirror: finding them, deciding which are worth drawing,
 * and reflecting the camera through the face. The re-render itself — off-screen target, scissored blit,
 * camera save and restore, the single-view rule — is {@link OffscreenWorldView}, shared with the portal
 * view, which wants exactly the same thing from a differently moved camera.
 *
 * <p><b>Why a rectangle and not the mirror's real outline</b> is a finding, not laziness; the reasons
 * live with the code that does the clipping, in {@link OffscreenWorldView} and
 * {@code docs/PORTAL_RENDERING.md}. Its known gaps, named rather than hidden: seen head-on the box
 * nearly coincides with the face, seen at a steep angle it is larger and the reflection spills past the
 * mirror's edges; and the blit ignores depth, so something between the eye and the mirror does not
 * occlude it. The second is partly answered by {@link #hasLineOfSight}; both close properly when the
 * shader composite lands.
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
	/**
	 * Mirrors reflected per frame. Each one is a whole extra world render, so this — not the range gate —
	 * is what actually bounds the cost of walking into a room with several of them.
	 */
	private static final int MAX_MIRRORS_PER_FRAME = 2;

	private static int scanAge = SCAN_PERIOD_TICKS;
	private static List<MirrorScanner.MirrorFace> cachedMirrors = List.of();

	/** How many faces the last scan found — a fact the diagnostics report needs and a log line has not. */
	public static int scannedCount() {
		return cachedMirrors.size();
	}

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
		// Depth 0 only, and a correctness stop rather than a budget one — see OffscreenWorldView, which
		// owns the counter because the portal view shares the same single target. MirrorRenderGate still
		// models deeper layers and stays as-is; the missing piece is one target per layer, not a
		// different gate.
		if (OffscreenWorldView.busy()) return;

		Camera camera = context.camera();
		// Unconditional cast, same idiom as CameraMixin's own use of this accessor: CameraAccessor is a
		// hard (non-require=0) mixin DRMD's whole camera system already depends on, so if it hadn't
		// applied, CameraMixin itself would already have failed long before this code ever runs.
		CameraAccessor accessor = (CameraAccessor) camera;

		// Several mirrors can now be on screen at once — each blit is scissored to its own rectangle, so
		// they no longer overwrite each other the way full-screen draws did. Nearest first, capped:
		// every mirror costs a full world render, so the cap is the real budget, not the range gate.
		Vec3d cameraPos = camera.getPos();
		int inRange = 0;
		List<MirrorScanner.MirrorFace> visible = new ArrayList<>();
		for (MirrorScanner.MirrorFace mirror : cachedMirrors) {
			double distance = cameraPos.distanceTo(mirror.planePoint());
			if (!MirrorRenderGate.shouldRender(OffscreenWorldView.depth(), MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH,
					distance, BASE_RENDER_RANGE)) {
				continue;
			}
			inRange++;
			if (!hasLineOfSight(camera, mirror)) continue;
			visible.add(mirror);
		}
		visible.sort(Comparator.comparingDouble(m -> cameraPos.squaredDistanceTo(m.planePoint())));

		int drawn = 0;
		for (MirrorScanner.MirrorFace mirror : visible) {
			if (drawn >= MAX_MIRRORS_PER_FRAME) break;
			// Counts only mirrors that actually rendered: one whose box is off-screen costs nothing and
			// must not use up a slot a visible one behind it could have had.
			if (renderReflection(context, accessor, camera, mirror)) drawn++;
		}

		// A mirror is seen from either side, so the facing stage does not apply here and is reported as
		// passed rather than invented: the found count is handed in for it.
		PortalViewDiagnostics.report("mirror", drawn > 0
				? "drawing " + drawn + " of " + cachedMirrors.size()
				: PortalViewDiagnostics.whyNothingDrawn(
						cachedMirrors.size(), cachedMirrors.size(), inRange, visible.size()));
	}

	/** @return true when the reflection actually reached the screen, false when it was skipped. */
	private static boolean renderReflection(WorldRenderContext context, CameraAccessor accessor, Camera camera,
			MirrorScanner.MirrorFace mirror) {
		Vec3d originalPos = camera.getPos();

		PortalTransform.Vec3 normal = toPure(mirror.normal());
		PortalTransform.Vec3 planePoint = toPure(mirror.planePoint());
		PortalTransform.Vec3 lookDirection =
				PortalTransform.yawPitchToVector(camera.getYaw(), camera.getPitch());

		// A mirror flips handedness, which is the whole difference between this and the portal view:
		// the position is reflected through the plane rather than carried to another one.
		PortalTransform.Vec3 reflectedPos = PortalTransform.reflectPoint(toPure(originalPos), planePoint, normal);
		PortalTransform.Vec3 reflectedLook = PortalTransform.reflectVector(lookDirection, normal);
		PortalTransform.YawPitch reflectedAngles = PortalTransform.vectorToYawPitch(reflectedLook);

		// Where the mirror's own face lands on screen, measured with the OUTER camera and its matrices,
		// before anything is reflected. Computed up front rather than after the render: an invalid box
		// means there is nowhere to put the result, so bailing here also skips a whole world render.
		MirrorScreenBounds.Box box = screenBox(context, mirror, originalPos);
		if (!box.valid()) return false;

		// The mirror's own plane is also the clip plane: the reflected camera stands behind it, so
		// without this the reflection is a picture of the wall the mirror is mounted on. Keeping the
		// side the normal points at keeps the room and discards the mirror block and everything behind.
		return OffscreenWorldView.render(context, accessor, camera, fromPure(reflectedPos),
				(float) reflectedAngles.yawDegrees(), (float) reflectedAngles.pitchDegrees(),
				mirror.planePoint(), mirror.normal(), box);
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

	/**
	 * Whether anything solid stands between the eye and the mirror.
	 *
	 * <p>A partial answer to the blit ignoring depth, and worth having on its own: without it a mirror
	 * in the next room reflects <em>through</em> the wall, which is the most obviously wrong thing the
	 * scissor version can do. It does not help with something only partly covering the mirror — that
	 * needs the depth-aware shader composite — but it removes the whole-mirror-should-not-be-there case
	 * for the cost of one short raycast per candidate.
	 *
	 * <p>The ray naturally ends on the mirror's own block, so hitting that counts as clear line.
	 */
	private static boolean hasLineOfSight(Camera camera, MirrorScanner.MirrorFace mirror) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return false;
		BlockHitResult hit = mc.world.raycast(new RaycastContext(
				camera.getPos(), mirror.planePoint(),
				RaycastContext.ShapeType.VISUAL,
				RaycastContext.FluidHandling.NONE,
				mc.player));
		if (hit.getType() == HitResult.Type.MISS) return true;
		return hit.getBlockPos().equals(mirror.pos());
	}

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}

	private static Vec3d fromPure(PortalTransform.Vec3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}
}
