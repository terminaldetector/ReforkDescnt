package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Fired objects, drawn the way Descent draws them — with one deliberate divergence for bolts, see
 * below.
 *
 * <p>The original splits weapons in two by {@code Weapon_info[].render_type} and nothing else
 * (WEAPON.H, LASER.C). {@code WEAPON_RENDER_BLOB} — every laser, plasma ball and fusion bolt —
 * becomes an object with {@code RT_LASER}, and {@code Laser_render} sends it to
 * {@code draw_object_blob}, which is one call to {@code g3_draw_bitmap}: a sprite pinned to the
 * camera at the object's position, sized by {@code blob_size}, never rotated to face any particular
 * way — the angled-streak look in the original's own art comes entirely from the bitmap texture, not
 * from any runtime orientation logic. {@code WEAPON_RENDER_POLYMODEL} — the missiles — becomes
 * {@code RT_POLYOBJ} and is drawn as a real model turned along its flight.
 *
 * <p>Here, there is no pre-drawn art, so the streak shape has to come from somewhere else. The first
 * attempt kept the billboard (camera-facing quad) and rotated it in-plane to match the round's
 * velocity as projected into the current camera's view space — correct in principle, but every frame's
 * angle depends on the live camera orientation as well as the round's own direction, and reads as
 * unsteady across a bolt's now much longer visible flight (see {@code docs/WEAPON_FX.md}'s speed fix).
 * {@code MESH_BOLT} now takes the same path {@code MESH_ROCKET}/{@code MESH_DRILL}/{@code MESH_MINE}
 * already do instead: a real body oriented in world space by {@link ModelOrientation#applyBasis}, the
 * same call verified across 1701 attitudes for the pilot model — its orientation is a pure function of
 * the round's own flight direction, nothing else, so it cannot wobble with the camera or with two
 * bolts from one volley converging at slightly different angles (deliberate — see
 * {@code DescentLaserFire}), the way the projected-billboard version could. Kept generously fat in
 * cross-section rather than a thin needle specifically so it stays legible even viewed end-on, which
 * is the one thing a true 3D body gives up relative to a billboard and the reason the original chose a
 * billboard in the first place.
 *
 * <p>{@code MESH_ORB} stays on the billboard path — a plasma ball reads as a round blob from any
 * angle, so it has no orientation to get wrong and no reason to leave the technique the original
 * itself uses. Rockets, drills and mines keep the model path they always had.
 */
public class ProjectileRenderer extends EntityRenderer<ProjectileEntity> {
	private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/misc/white.png");
	/** Packed full-bright lightmap coordinate — a shot has to read in a dark mine, not just outdoors. */
	private static final int FULL_BRIGHT = 15728880;

	public ProjectileRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(ProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		Vec3d vel = entity.getVelocity();
		int argb = 0xFF000000 | entity.getColorRgb();
		int mesh = entity.getMeshKind();
		float scale = entity.getVisualScale();

		if (mesh == ProjectileEntity.MESH_ORB) {
			renderBlob(scale, argb, matrices, consumers);
		} else if (mesh == ProjectileEntity.MESH_BOLT) {
			renderBolt(scale, argb, vel, matrices, consumers);
		} else {
			renderModel(entity, mesh, scale, argb, vel, tickDelta, matrices, consumers);
		}
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	// ------------------------------------------------------------------ WEAPON_RENDER_BLOB (orbs)

	/**
	 * The blob: a quad turned to face the camera — plasma and other {@code MESH_ORB} rounds only.
	 * {@code MESH_BOLT} moved to {@link #renderBolt}; see the class doc for why.
	 *
	 * <p>Descent's blob is a bitmap and the shape lives in the art. There is no art here, so the shape
	 * is built instead — a saturated outer glow with a near-white core inside it, which is what the
	 * original's sprites look like and what makes them read against both a lit wall and open space.
	 * All three layers go through the same translucent entity layer real geometry uses elsewhere in
	 * this mod ({@code RenderLayer.getEntityTranslucent}) rather than a debug-only one — see
	 * {@link #billboard} for why that swap matters. No elongation, no rotation: a ball reads as a ball
	 * from every angle, so unlike a bolt it has no direction to get right or wrong.
	 */
	private void renderBlob(float scale, int argb, MatrixStack matrices, VertexConsumerProvider consumers) {
		matrices.push();
		// Pin to the camera. This is the whole property draw_object_blob buys in the original: a round
		// object has no bad viewing angle, because it has no orientation of its own to be seen edge-on.
		matrices.multiply(this.dispatcher.getRotation());

		float half = 0.34f * Math.max(0.55f, scale);
		var entry = matrices.peek();
		VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		billboard(buf, entry, half * 2.3f, half * 2.3f, argb, 0.30f);
		billboard(buf, entry, half * 1.35f, half * 1.35f, argb, 0.65f);
		// The core reads as solid rather than as more glow: alpha 1.0 fully covers whatever is behind
		// it, so it stays legible in a mine and does not wash out over a sunlit field the way a purely
		// additive bolt would.
		billboard(buf, entry, half * 0.5f, half * 0.5f, whiten(argb), 1.0f);
		matrices.pop();
	}

	// ------------------------------------------------------------------- WEAPON_RENDER_BLOB (bolts)

	/**
	 * The bolt: a real body oriented along the round's own flight direction, same technique as
	 * {@link #renderModel} — see the class doc for why this departs from a camera-facing billboard.
	 *
	 * <p>Deliberately fat for its length (cross-section roughly a third of the total length) rather
	 * than a thin needle: a true 3D body foreshortens toward nothing when viewed end-on, which a
	 * billboard never does, so the one failure mode being traded in for a stable orientation is guarded
	 * against directly by never making the body thin enough for that foreshortening to read as
	 * "vanished" rather than "pointed at you."
	 */
	private void renderBolt(float scale, int argb, Vec3d vel,
							MatrixStack matrices, VertexConsumerProvider consumers) {
		matrices.push();
		if (vel.lengthSquared() > 1e-6) {
			Vec3d dir = vel.normalize();
			Vec3d ref = Math.abs(dir.y) > 0.99 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
			ModelOrientation.applyBasis(matrices, 180f, dir.negate(), ref);
		}

		float speed = (float) vel.length();
		float r = 0.15f * Math.max(0.55f, scale);
		// Half-length along the flight axis, from speed the same way the old billboard stretch was —
		// re-derived for the corrected per-tick speed (docs/WEAPON_FX.md), not matched against a source
		// number: Descent's own blob has no speed/distance scaling at all. Floored at 2.5x the radius so
		// a slow or small round never reads as a flat disc instead of a bolt; capped short, matching the
		// reference screenshot's own short bolts rather than the far longer billboard streak this
		// replaces.
		float len = MathHelper.clamp(speed * 0.3f, r * 2.5f, 1.4f);
		body(matrices, consumers, r * 1.7f, r * 1.7f, len, 0, argb);
		body(matrices, consumers, r * 0.75f, r * 0.75f, len * 0.85f, 0, whiten(argb));
		matrices.pop();
	}

	/**
	 * One camera-facing quad, centred on the round, {@code len} along X and {@code wide} along Y.
	 *
	 * <p>Used to go through {@code RenderLayer.getLightning()} for the additive brightening and
	 * {@code RenderLayer.getDebugQuads()} for the opaque core — both are special-purpose vanilla
	 * layers meant for exactly one built-in use each (the lightning bolt entity; the F3 debug
	 * overlay), not high-frequency gameplay geometry, and {@code CockpitRenderer} already found the
	 * debug one "often never appears under TLauncher / sodium-class pipelines." A round rendered that
	 * way is a shot that sometimes has no visible bolt and sometimes has one layer of it without the
	 * others, which reads as disconnected fragments rather than a coherent tracer. The standard
	 * translucent entity layer other hand-drawn geometry in this mod already uses successfully
	 * ({@code DroneSwarmRenderer}, {@code SkyUfoRenderer}) does not have that failure mode.
	 *
	 * <p>Emitted in both winding orders, same reasoning and same fix as {@link #quad}: this single
	 * quad went through the RenderLayer swap above but never got the double-winding treatment that
	 * shipped for the hand-built cube faces in {@code drawBox} — a gap, not a deliberate exception,
	 * since a billboard is exactly the same "hand-emitted quad through an unverified culling
	 * convention" risk as a cube face, just one face instead of six. Every bolt and orb goes through
	 * here, so a wrong-winding guess does not read as "one shot in ten looks off," it reads as "no
	 * projectiles" — the whole {@code MESH_BOLT}/{@code MESH_ORB} path draws nothing at all.
	 */
	private static void billboard(VertexConsumer vc, MatrixStack.Entry entry,
								  float len, float wide, int argb, float alpha) {
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		var m = entry.getPositionMatrix();
		blobVertex(vc, m, -len, -wide, r, g, b, alpha, 0, 0);
		blobVertex(vc, m, len, -wide, r, g, b, alpha, 1, 0);
		blobVertex(vc, m, len, wide, r, g, b, alpha, 1, 1);
		blobVertex(vc, m, -len, wide, r, g, b, alpha, 0, 1);
		blobVertex(vc, m, -len, -wide, r, g, b, alpha, 0, 0);
		blobVertex(vc, m, -len, wide, r, g, b, alpha, 0, 1);
		blobVertex(vc, m, len, wide, r, g, b, alpha, 1, 1);
		blobVertex(vc, m, len, -wide, r, g, b, alpha, 1, 0);
	}

	private static void blobVertex(VertexConsumer vc, org.joml.Matrix4f m, float x, float y,
								   float r, float g, float b, float a, float u, float v) {
		vc.vertex(m, x, y, 0).color(r, g, b, a).texture(u, v).overlay(0).light(FULL_BRIGHT).normal(0, 0, 1);
	}

	// ------------------------------------------------------------- WEAPON_RENDER_POLYMODEL

	/** Missiles, drill charges and mines: a body with a direction, turned along its flight. */
	private void renderModel(ProjectileEntity entity, int mesh, float scale, int argb, Vec3d vel,
							 float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
		matrices.push();
		// Point the round along its own velocity, built from a basis rather than from yaw and pitch.
		//
		// The yaw/pitch version came apart exactly where 6DoF puts you most often. Yaw was
		// atan2(vel.x, vel.z), so firing straight up or down left both arguments near zero and the
		// heading became noise — the round span on the spot. And the two Euler turns do not commute,
		// so anything launched from a banked ship sat crooked in the air. A forward/up basis has
		// neither problem; this is the same call the pilot model uses, verified across 1701
		// attitudes.
		if (vel.lengthSquared() > 1e-6) {
			Vec3d dir = vel.normalize();
			// The basis maps the model's -Z onto `forward`, and these meshes are built long along
			// +Z, so the direction goes in negated. Any reference that is not parallel to the round's
			// own axis will do for up — it is orthogonalised against it — and these shapes are
			// axially symmetric, so the roll it lands on does not read.
			Vec3d ref = Math.abs(dir.y) > 0.99 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
			ModelOrientation.applyBasis(matrices, 180f, dir.negate(), ref);
		}

		float s = 0.22f * scale;
		switch (mesh) {
			case ProjectileEntity.MESH_ROCKET -> {
				body(matrices, consumers, s * 0.7f, s * 0.7f, s * 2.4f, 0, argb);
				// Warhead cap, brighter than the casing so the nose is the part you track.
				body(matrices, consumers, s * 0.9f, s * 0.9f, s * 0.7f, s * 1.5f, brighten(argb));
			}
			case ProjectileEntity.MESH_DRILL -> {
				body(matrices, consumers, s * 0.55f, s * 0.55f, s * 2.8f, 0, argb);
				body(matrices, consumers, s * 0.95f, s * 0.95f, s * 0.7f, s * 1.2f, 0xFFFFAA33);
			}
			default -> {
				// Mine: faceted shell plus a pulsing core; the pulse rate reads as "armed".
				body(matrices, consumers, s * 1.5f, s * 1.5f, s * 1.5f, 0, argb);
				float pulse = 0.55f + 0.45f * MathHelper.sin((entity.age + tickDelta) * 0.45f);
				body(matrices, consumers, s * 2.1f * pulse, s * 0.35f, s * 0.35f, 0, brighten(argb));
				body(matrices, consumers, s * 0.35f, s * 2.1f * pulse, s * 0.35f, 0, brighten(argb));
				body(matrices, consumers, s * 0.35f, s * 0.35f, s * 2.1f * pulse, 0, brighten(argb));
			}
		}
		matrices.pop();

		// Exhaust. The original hangs a vclip off a missile's tail; this is the same idea — a blob at
		// the back, camera-facing, so a missile heading away from you is still a light and not a dot.
		if (mesh == ProjectileEntity.MESH_ROCKET) {
			matrices.push();
			matrices.multiply(this.dispatcher.getRotation());
			var entry = matrices.peek();
			VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
			billboard(vc, entry, s * 2.2f, s * 2.2f, 0xFFFF9944, 0.42f);
			billboard(vc, entry, s * 1.1f, s * 1.1f, 0xFFFFDDAA, 0.85f);
			matrices.pop();
		}
	}

	/** An axis-aligned box of the given half-extents, pushed {@code forward} down the round's nose. */
	private static void body(MatrixStack matrices, VertexConsumerProvider consumers,
							 float hx, float hy, float hz, float forward, int argb) {
		matrices.push();
		matrices.translate(0, 0, forward);
		drawBox(matrices, consumers, hx, hy, hz, argb);
		matrices.pop();
	}

	private static int brighten(int argb) {
		int a = (argb >> 24) & 255;
		int r = Math.min(255, ((argb >> 16) & 255) + 40);
		int g = Math.min(255, ((argb >> 8) & 255) + 40);
		int b = Math.min(255, (argb & 255) + 40);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Push a colour most of the way to white, keeping a trace of its hue. */
	private static int whiten(int argb) {
		int a = (argb >> 24) & 255;
		int r = 255 - (255 - ((argb >> 16) & 255)) / 4;
		int g = 255 - (255 - ((argb >> 8) & 255)) / 4;
		int b = 255 - (255 - (argb & 255)) / 4;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers,
								float hx, float hy, float hz, int argb) {
		var entry = matrices.peek();
		// Quads, into a layer built for them. This was RenderLayer.getDebugFilledBox() (a
		// TRIANGLE_STRIP layer — six quads' worth of vertices fed to a strip is not six faces, it is
		// a run of degenerate slivers), then RenderLayer.getDebugQuads() — still a debug-only layer,
		// and per CockpitRenderer's own finding, one that "often never appears under TLauncher /
		// sodium-class pipelines". RenderLayer.getEntitySolid is the layer MegaWormRenderer already
		// draws its own hand-built cubes through; a rocket casing is not different from a worm
		// segment as far as the renderer is concerned.
		//
		// Solid, and with each face's own outward normal. Recomputing the winding of the coordinates
		// below (cross product of each face's own edges) shows all six *are* wound consistently
		// outward — the "not wound consistently" claim this comment used to make was stale, left over
		// from before these coordinates were last touched. What's actually unresolved is the other
		// half of the question: whether getEntitySolid culls backfaces at all in this MC/Fabric
		// version's RenderPhase setup, and if so, which winding it treats as front — this project has
		// no decompiled Minecraft source and no live client available to check either way. See quad()
		// below for how that is made not to matter.
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE));
		float a = ((argb >> 24) & 255) / 255f;
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float x0 = -hx, y0 = -hy, z0 = -hz;
		float x1 = hx, y1 = hy, z1 = hz;
		quad(vc, entry, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a, 0, -1, 0);
		quad(vc, entry, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a, 0, 1, 0);
		quad(vc, entry, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a, 0, 0, -1);
		quad(vc, entry, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a, 0, 0, 1);
		quad(vc, entry, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a, -1, 0, 0);
		quad(vc, entry, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a, 1, 0, 0);
	}

	/**
	 * Emits each face in both winding orders. Whichever convention getEntitySolid actually culls
	 * against — if it culls at all, which this environment cannot check either way (see drawBox) —
	 * one ordering is front-facing and survives; the other is back-facing and gets dropped. If
	 * culling turns out to be off entirely, both orderings draw: same four corners, same colour,
	 * landing on identical depth, so the second pass paints over the first rather than showing as a
	 * seam. Either way the face is fully visible; there is no ordering under which it goes missing.
	 *
	 * <p>Doubles the vertex count per box — not a cost worth trading the certainty for at this scale
	 * (a handful of small boxes per round, never many rounds at once). A custom RenderLayer.of(...)
	 * with its own Cull phase forced off would only need one winding, but that API has no precedent
	 * anywhere in this codebase and could not be compiled or run here to get right; worth revisiting
	 * later against real CI, on its own, not as a precondition for the box rendering at all.
	 */
	private static void quad(VertexConsumer vc, MatrixStack.Entry entry,
							 float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3,
							 float r, float g, float b, float a, float nx, float ny, float nz) {
		var m = entry.getPositionMatrix();
		vc.vertex(m, x0, y0, z0).color(r, g, b, a).texture(0, 0).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x1, y1, z1).color(r, g, b, a).texture(1, 0).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x2, y2, z2).color(r, g, b, a).texture(1, 1).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x3, y3, z3).color(r, g, b, a).texture(0, 1).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x0, y0, z0).color(r, g, b, a).texture(0, 0).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x3, y3, z3).color(r, g, b, a).texture(0, 1).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x2, y2, z2).color(r, g, b, a).texture(1, 1).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
		vc.vertex(m, x1, y1, z1).color(r, g, b, a).texture(1, 0).overlay(0).light(FULL_BRIGHT).normal(nx, ny, nz);
	}

	@Override
	public Identifier getTexture(ProjectileEntity entity) {
		return TEXTURE;
	}
}
