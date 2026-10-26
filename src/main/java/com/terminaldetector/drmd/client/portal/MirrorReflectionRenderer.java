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

/** Reflect edge-connected surfaces with one world render each; composite their individual depth-tested apertures. */
public final class MirrorReflectionRenderer {
	private MirrorReflectionRenderer() {}

	/** Deliberately short — a close-range "stand in front of the mirror and check" distance, not a
	 *  real gameplay render distance; this is a diagnostic, not the finished feature. */
	private static final double BASE_RENDER_RANGE = 24.0;
	/** Covers {@link #BASE_RENDER_RANGE} (24 blocks) with margin against chunk-boundary rounding. */
	private static final int SCAN_CHUNK_RADIUS = 2;
	private static final int SCAN_PERIOD_TICKS = 5;
	/**
	 * Mirrors reflected per frame. Each one is a whole extra world render, so this — not the range gate —
	 * is what actually bounds the cost of walking into a room with several of them.
	 */
	private static final int MAX_MIRRORS_PER_FRAME = 2;

	private static int scanAge = SCAN_PERIOD_TICKS;
    private static net.minecraft.client.world.ClientWorld scannedWorld;
	private static List<MirrorSurface> cachedMirrors = List.of();

	/** How many joined surfaces the last scan found — a fact the diagnostics report needs and a log line has not. */
	public static int scannedCount() {
		return cachedMirrors.size();
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MirrorReflectionRenderer::onClientTick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(MirrorReflectionRenderer::onAfterTranslucent);
	}

	/** Mirrors are static placed blocks — a five-tick rescan is imperceptible lag between placing
	 *  or breaking one and it dis/appearing from this system, same trade TerrainMap3d already makes. */
	private static void onClientTick(MinecraftClient mc) {
        if(mc.world!=scannedWorld){cachedMirrors=List.of();scanAge=SCAN_PERIOD_TICKS;scannedWorld=mc.world;}
        if(!DescentConfig.mirrorReflection){cachedMirrors=List.of();scanAge=SCAN_PERIOD_TICKS;return;}
		if (mc.player == null || mc.world == null) {
			cachedMirrors = List.of();
			return;
		}
		if (++scanAge < SCAN_PERIOD_TICKS) return;
		scanAge = 0;
		cachedMirrors = MirrorSurface.join(MirrorScanner.findNearby(mc.world, mc.player.getBlockPos(), SCAN_CHUNK_RADIUS));
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
		// Taken from the frame's own capture, not from the context. Both views listen on the same event
		// and the listeners run in order, so by the time the second one reads the context, the first
		// one's nested render has already replaced it — see OffscreenWorldView.register.
		Matrix4f outerProjection = OffscreenWorldView.frameProjection();
		Matrix4f outerPosition = OffscreenWorldView.framePosition();
		if (outerProjection == null || outerPosition == null) return;

		// Several mirrors can now be on screen at once — each composite is masked by its depth-tested apertures, so
		// they no longer overwrite each other the way full-screen draws did. Nearest first, capped:
		// every mirror costs a full world render, so the cap is the real budget, not the range gate.
		Vec3d cameraPos = camera.getPos();
		int inRange = 0;
		List<MirrorSurface> visible = new ArrayList<>();
		for (MirrorSurface mirror : cachedMirrors) {
			double distance = Math.sqrt(mirror.squaredDistance(cameraPos));
			if (!MirrorRenderGate.shouldRender(OffscreenWorldView.depth(), MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH,
					distance, BASE_RENDER_RANGE)) {
				continue;
			}
			inRange++;
			if (!hasLineOfSight(camera, mirror)) continue;
			visible.add(mirror);
		}
		visible.sort(Comparator.comparingDouble(m -> m.squaredDistance(cameraPos)));

		int drawn = 0;
		for (MirrorSurface mirror : visible) {
			if (drawn >= MAX_MIRRORS_PER_FRAME) break;
			// Counts only mirrors that actually rendered: one whose box is off-screen costs nothing and
			// must not use up a slot a visible one behind it could have had.
			if (renderReflection(context, accessor, camera, outerProjection, outerPosition, mirror)) drawn++;
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
			Matrix4f outerProjection, Matrix4f outerPosition,
			MirrorSurface surface) {
        var mirror=surface.anchor();
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
		MirrorScreenBounds.Box box = screenBox(outerProjection, outerPosition, surface, originalPos);
		if (!box.valid()) return false;

		// The mirror's own plane is also the clip plane: the reflected camera stands behind it, so
		// without this the reflection is a picture of the wall the mirror is mounted on. Keeping the
		// side the normal points at keeps the room and discards the mirror block and everything behind.
		return OffscreenWorldView.render(context, accessor, camera, outerProjection, fromPure(reflectedPos),
				(float) reflectedAngles.yawDegrees(), (float) reflectedAngles.pitchDegrees(),
				mirror.planePoint(), mirror.normal(), box, PortalViewMatrix.reflected(outerPosition, normal), true, surface.apertures());
	}

	/**
	 * The mirror face's pixel bounding box under the current (unreflected) view.
	 *
	 * <p>The world is drawn camera-relative, so the face centre is offset by the camera position before
	 * projecting, and the matrix is {@code projection × positionMatrix} — the same pair
	 * {@code WorldRenderer.render} itself is handed, taken straight off the context rather than rebuilt.
	 */
    private static MirrorScreenBounds.Box screenBox(Matrix4f projection, Matrix4f view,
            MirrorSurface surface, Vec3d camera) {
        float[] matrix=new float[16];new Matrix4f(projection).mul(view).get(matrix);
        int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,x1=0,y1=0;
        for(var polygon:surface.apertures()) {
            var relative=new PortalTransform.Vec3[4];
            for(int i=0;i<4;i++)relative[i]=polygon[i].minus(toPure(camera));
            var box=MirrorScreenBounds.project(relative,matrix,MirrorFramebuffer.width(),MirrorFramebuffer.height(),0);
            if(!box.valid())continue;
            x0=Math.min(x0,box.x());y0=Math.min(y0,box.y());
            x1=Math.max(x1,box.x()+box.width());y1=Math.max(y1,box.y()+box.height());
        }
        return x1>x0 && y1>y0 ? new MirrorScreenBounds.Box(x0,y0,x1-x0,y1-y0,true) : MirrorScreenBounds.Box.INVALID;
    }

	/**
	 * Whether anything solid stands between the eye and the mirror.
	 *
	 * <p>An early rejection before paying for an offscreen render: a mirror
	 * in the next room reflects <em>through</em> the wall, which is the most obviously wrong thing the
	 * scissor version can do. It does not help with something only partly covering the mirror — that
	 * needs the depth-aware shader composite — but it removes the whole-mirror-should-not-be-there case
	 * for the cost of one short raycast per candidate.
	 *
	 * <p>The ray naturally ends on the mirror's own block, so hitting that counts as clear line.
	 */
    private static boolean hasLineOfSight(Camera camera, MirrorSurface surface) {
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.world==null || mc.player==null)return false;
        for(var pane:surface.panes()) {
            var hit=mc.world.raycast(new RaycastContext(camera.getPos(),pane.planePoint(),
                    RaycastContext.ShapeType.VISUAL,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pane.pos()))return true;
        }
        return false;
    }

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}

	private static Vec3d fromPure(PortalTransform.Vec3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}
}
