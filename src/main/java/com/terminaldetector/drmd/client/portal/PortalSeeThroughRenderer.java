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
 * Draws the far side of a linked portal on its face — the second thing DRMD does with
 * {@link OffscreenWorldView}, and the reason that class exists separately from the mirror's.
 *
 * <p>The only real difference from {@code MirrorReflectionRenderer} is where the camera goes. A mirror
 * reflects it through the glass, which flips handedness; a portal <em>carries</em> it to the partner,
 * which does not. Standing a step in front of one end puts the virtual eye a step behind the other,
 * looking out through it — the same map {@code PortalCrossing.exitFor} uses to carry a traveller, minus
 * the clearance step, so what you see through a portal is where walking into it puts you.
 *
 * <p>Everything else — the off-screen target, the scissored blit, the clip plane at the destination,
 * the one-view-at-a-time rule — belongs to {@link OffscreenWorldView} and is shared.
 *
 * <p><b>Only from the front.</b> A portal is entered from its face, so it is looked through from its
 * face too. Without that check the back of a portal would show the destination as well, which is not
 * what a portal is and does not match what walking into the back of one does (nothing).
 *
 * <p>Off by default ({@link DescentConfig#portalSeeThrough}), like the mirror, and for the same reason:
 * CI cannot tell whether any of this paints the right picture. The diagnosis for a wrong result splits
 * the same four ways as the mirror's — see {@code docs/PORTAL_RENDERING.md} — with one addition
 * specific to a portal: a view that looks like the <em>back</em> of the destination block, rather than
 * the room in front of it, means the clip plane did not apply, so start at
 * {@link ObliqueNearPlane}.
 */
public final class PortalSeeThroughRenderer {
	private PortalSeeThroughRenderer() {}

	/** Same close-range diagnostic distance the mirror uses; not a gameplay render distance. */
	private static final double BASE_RENDER_RANGE = 24.0;
	private static final int SCAN_CHUNK_RADIUS = 2;
	private static final int SCAN_PERIOD_TICKS = 20;
	private static final int SCISSOR_PAD = 2;
	/**
	 * Portals drawn through per frame. Each is a whole extra world render, and this cap — not the
	 * distance gate — is what bounds the cost. With the mirror's own cap that is four extra renders in
	 * the worst frame, which is why both features default off.
	 */
	private static final int MAX_PORTALS_PER_FRAME = 2;

	private static int scanAge = SCAN_PERIOD_TICKS;
	private static List<MirrorScanner.PortalFace> cachedPortals = List.of();

	/** How many faces the last scan found — a fact the diagnostics report needs and a log line has not. */
	public static int scannedCount() {
		return cachedPortals.size();
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PortalSeeThroughRenderer::onClientTick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PortalSeeThroughRenderer::onAfterTranslucent);
	}

	/** Once a second, like the mirror scan: a link is placed and broken far more slowly than it is drawn. */
	private static void onClientTick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) {
			cachedPortals = List.of();
			return;
		}
		if (++scanAge < SCAN_PERIOD_TICKS) return;
		scanAge = 0;
		cachedPortals = MirrorScanner.findLinkedNearby(mc.world, mc.player.getBlockPos(), SCAN_CHUNK_RADIUS);
	}

	private static void onAfterTranslucent(WorldRenderContext context) {
		if (!DescentConfig.portalSeeThrough) return;
		// Shared with the mirror renderer, deliberately: both draw into the same single off-screen
		// target, so neither may start while the other is running. Checked before anything is counted,
		// so a nested frame never reports on the outer one's behalf.
		if (OffscreenWorldView.busy()) return;

		Camera camera = context.camera();
		CameraAccessor accessor = (CameraAccessor) camera;
		// Captured before anything nested runs. A nested WorldRenderer.render re-enters Fabric's own
		// mixin, which re-prepares the single shared WorldRenderContext with that call's arguments — so
		// from the second view onward the context describes the view, not the frame. Everything below
		// uses these copies.
		Matrix4f outerProjection = new Matrix4f(context.projectionMatrix());
		Matrix4f outerPosition = new Matrix4f(context.positionMatrix());
		Vec3d cameraPos = camera.getPos();

		int facing = 0;
		int inRange = 0;
		List<MirrorScanner.PortalFace> visible = new ArrayList<>();
		for (MirrorScanner.PortalFace portal : cachedPortals) {
			// In front of the face, not behind it: a portal is looked through the way it is entered.
			if (cameraPos.subtract(portal.planePoint()).dotProduct(portal.normal()) <= 0) continue;
			facing++;
			double distance = cameraPos.distanceTo(portal.planePoint());
			if (!MirrorRenderGate.shouldRender(OffscreenWorldView.depth(),
					MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH, distance, BASE_RENDER_RANGE)) {
				continue;
			}
			inRange++;
			if (!hasLineOfSight(camera, portal)) continue;
			visible.add(portal);
		}
		visible.sort(Comparator.comparingDouble(p -> cameraPos.squaredDistanceTo(p.planePoint())));

		int drawn = 0;
		for (MirrorScanner.PortalFace portal : visible) {
			if (drawn >= MAX_PORTALS_PER_FRAME) break;
			if (renderThrough(context, accessor, camera, outerProjection, outerPosition, portal)) drawn++;
		}

		PortalViewDiagnostics.report("portal", drawn > 0
				? "drawing " + drawn + " of " + cachedPortals.size()
				: PortalViewDiagnostics.whyNothingDrawn(cachedPortals.size(), facing, inRange, visible.size()));
	}

	/** @return true when the view actually reached the screen, false when it was skipped. */
	private static boolean renderThrough(WorldRenderContext context, CameraAccessor accessor, Camera camera,
			Matrix4f outerProjection, Matrix4f outerPosition,
			MirrorScanner.PortalFace portal) {
		Vec3d cameraPos = camera.getPos();

		PortalTransform.Vec3 srcPlane = toPure(portal.planePoint());
		PortalTransform.Vec3 srcNormal = toPure(portal.normal());
		PortalTransform.Vec3 dstPlane = toPure(portal.destPoint());
		PortalTransform.Vec3 dstNormal = toPure(portal.destNormal());

		// A step in front of this end becomes a step behind the other, looking out through it. Same
		// transform PortalCrossing.exitFor carries a traveller with, so the view agrees with the walk.
		PortalTransform.Vec3 movedPos =
				PortalTransform.transformPoint(toPure(cameraPos), srcPlane, srcNormal, dstPlane, dstNormal, 1.0);
		PortalTransform.Vec3 look = PortalTransform.yawPitchToVector(camera.getYaw(), camera.getPitch());
		PortalTransform.Vec3 movedLook = PortalTransform.cameraRotation(srcNormal, dstNormal).rotate(look);
		PortalTransform.YawPitch movedAngles = PortalTransform.vectorToYawPitch(movedLook);

		// This end's face on screen, measured with the outer camera before anything moves. Bailing here
		// also skips a whole world render, which is the expensive half.
		MirrorScreenBounds.Box box = screenBox(outerProjection, outerPosition, portal, cameraPos);
		if (!box.valid()) return false;

		// The destination's own plane is the clip plane: the moved camera stands behind it, so without
		// this the portal would show the back of the block it leads to and the wall around it.
		return OffscreenWorldView.render(context, accessor, camera, outerProjection, fromPure(movedPos),
				(float) movedAngles.yawDegrees(), (float) movedAngles.pitchDegrees(),
				portal.destPoint(), portal.destNormal(), box);
	}

	/** The portal face's pixel bounding box under the current view — the mirror's own method, at this face's size. */
	private static MirrorScreenBounds.Box screenBox(Matrix4f outerProjection, Matrix4f outerPosition,
			MirrorScanner.PortalFace portal, Vec3d cameraPos) {
		Matrix4f viewProjection = new Matrix4f(outerProjection).mul(outerPosition);
		float[] m = new float[16];
		viewProjection.get(m);

		PortalTransform.Vec3 centre = new PortalTransform.Vec3(
				portal.planePoint().x - cameraPos.x,
				portal.planePoint().y - cameraPos.y,
				portal.planePoint().z - cameraPos.z);
		PortalTransform.Vec3[] corners =
				MirrorScreenBounds.faceCorners(centre, toPure(portal.normal()), portal.faceSize());

		return MirrorScreenBounds.project(corners, m,
				MirrorFramebuffer.width(), MirrorFramebuffer.height(), SCISSOR_PAD);
	}

	/**
	 * Whether anything solid stands between the eye and the portal — the same short raycast the mirror
	 * uses, and needed for the same reason: the blit ignores depth, so a portal in the next room would
	 * otherwise show its destination straight through the wall.
	 */
	private static boolean hasLineOfSight(Camera camera, MirrorScanner.PortalFace portal) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return false;
		BlockHitResult hit = mc.world.raycast(new RaycastContext(
				camera.getPos(), portal.planePoint(),
				RaycastContext.ShapeType.VISUAL,
				RaycastContext.FluidHandling.NONE,
				mc.player));
		if (hit.getType() == HitResult.Type.MISS) return true;
		return hit.getBlockPos().equals(portal.pos());
	}

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}

	private static Vec3d fromPure(PortalTransform.Vec3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}
}
