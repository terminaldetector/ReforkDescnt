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
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fired objects, drawn the way Descent draws them.
 *
 * <p>The original splits weapons in two by {@code Weapon_info[].render_type} and nothing else
 * (WEAPON.H, LASER.C). {@code WEAPON_RENDER_BLOB} — every laser, plasma ball and fusion bolt —
 * becomes an object with {@code RT_LASER}, and {@code Laser_render} sends it to
 * {@code draw_object_blob}, which is one call to {@code g3_draw_bitmap}: a sprite pinned to the
 * camera at the object's position, sized by {@code blob_size}. {@code WEAPON_RENDER_POLYMODEL} —
 * the missiles — becomes {@code RT_POLYOBJ} and is drawn as a real model turned along its flight.
 *
 * <p>That split is the reason a Descent firefight is readable. A bolt is a billboard, so there is no
 * angle you can see it from where it thins out or disappears; it is the same bright shape whether it
 * is coming at you or crossing in front of you. A missile is a model, because a missile is a thing
 * with a nose and a tail and you are meant to read which way it is pointing.
 *
 * <p>Here, bolts and orbs take the blob path and rockets, drills and mines take the model path.
 */
public class ProjectileRenderer extends EntityRenderer<ProjectileEntity> {
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

		if (mesh == ProjectileEntity.MESH_BOLT || mesh == ProjectileEntity.MESH_ORB) {
			renderBlob(entity, mesh, scale, argb, vel, matrices, consumers);
		} else {
			renderModel(entity, mesh, scale, argb, vel, tickDelta, matrices, consumers);
		}
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	// ------------------------------------------------------------------ WEAPON_RENDER_BLOB

	/**
	 * The blob: a quad turned to face the camera, elongated along the round's screen track.
	 *
	 * <p>Descent's blob is a bitmap and the bolt shape lives in the art. There is no art here, so the
	 * shape is built instead — a saturated outer glow with a near-white core inside it, which is what
	 * the original's bolt sprites look like and what makes them read against both a lit wall and open
	 * space. Both layers go through an additive pass, so they brighten whatever is behind them rather
	 * than flatly covering it.
	 *
	 * <p>The elongation is the one liberty taken. Descent's rounds move slowly enough to be discrete
	 * objects frame to frame; ours cross seventy blocks in a tick, so a round blob would be a dot in
	 * a different place each frame. Stretching it down its own screen track is what a round moving
	 * that fast would leave on a real sensor, and it is what makes the shot legible in the frame.
	 */
	private void renderBlob(ProjectileEntity entity, int mesh, float scale, int argb, Vec3d vel,
							MatrixStack matrices, VertexConsumerProvider consumers) {
		matrices.push();
		// Pin to the camera. This is the whole property draw_object_blob buys in the original: a bolt
		// has no bad viewing angle, because it has no orientation of its own to be seen edge-on.
		Quaternionf camera = this.dispatcher.getRotation();
		matrices.multiply(camera);

		// Lay the long axis along where the round is travelling *on screen*. Rotating the world
		// velocity by the camera's inverse puts it in view space; its x/y is the track.
		float spin = 0f;
		float speed = (float) vel.length();
		if (speed > 1e-4) {
			Vector3f view = new Vector3f((float) vel.x, (float) vel.y, (float) vel.z);
			camera.conjugate(new Quaternionf()).transform(view);
			if (view.x * view.x + view.y * view.y > 1e-8f) {
				spin = (float) Math.toDegrees(Math.atan2(view.y, view.x));
			}
		}
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));

		float half = (mesh == ProjectileEntity.MESH_ORB ? 0.34f : 0.26f) * Math.max(0.55f, scale);
		// A round crossing the screen leaves a track as long as the ground it covered.
		float length = half + (float) MathHelper.clamp(speed * 0.55, 0.0, 7.0);

		var entry = matrices.peek();
		// Glow additively, so the bolt brightens what is behind it the way the original's palette
		// does in a dark mine.
		VertexConsumer glow = consumers.getBuffer(RenderLayer.getLightning());
		billboard(glow, entry, length * 1.05f, half * 2.3f, argb, 0.30f);
		billboard(glow, entry, length, half * 1.35f, argb, 0.65f);
		// The core is drawn solid rather than additively, and that is deliberate: additive light adds
		// nothing to a background that is already bright, so an all-glow bolt is legible in a mine
		// and washes out over a sunlit field. The opaque core keeps the shot readable on any
		// background, which is the whole requirement.
		VertexConsumer core = consumers.getBuffer(RenderLayer.getDebugQuads());
		billboard(core, entry, length * 0.94f, half * 0.5f, whiten(argb), 1.0f);
		matrices.pop();
	}

	/** One camera-facing quad, centred on the round, {@code len} along X and {@code wide} along Y. */
	private static void billboard(VertexConsumer vc, MatrixStack.Entry entry,
								  float len, float wide, int argb, float alpha) {
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		var m = entry.getPositionMatrix();
		vc.vertex(m, -len, -wide, 0).color(r, g, b, alpha);
		vc.vertex(m, len, -wide, 0).color(r, g, b, alpha);
		vc.vertex(m, len, wide, 0).color(r, g, b, alpha);
		vc.vertex(m, -len, wide, 0).color(r, g, b, alpha);
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
			VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
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
		// Quads, into a layer that draws quads. This was RenderLayer.getDebugFilledBox(), which is a
		// TRIANGLE_STRIP layer — six quads' worth of vertices fed to a strip is not six faces, it is
		// a run of degenerate slivers, which is why the rounds had no visible body at all.
		//
		// Solid, and with culling off. These boxes are written by hand and their faces are not wound
		// consistently outward, so a culling layer would drop whichever ones happen to face away and
		// leave the body full of holes.
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getDebugQuads());
		float a = ((argb >> 24) & 255) / 255f;
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float x0 = -hx, y0 = -hy, z0 = -hz;
		float x1 = hx, y1 = hy, z1 = hz;
		quad(vc, entry, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
		quad(vc, entry, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		quad(vc, entry, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
		quad(vc, entry, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		quad(vc, entry, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
		quad(vc, entry, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
	}

	private static void quad(VertexConsumer vc, MatrixStack.Entry entry,
							 float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3,
							 float r, float g, float b, float a) {
		vc.vertex(entry.getPositionMatrix(), x0, y0, z0).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x3, y3, z3).color(r, g, b, a);
	}

	@Override
	public Identifier getTexture(ProjectileEntity entity) {
		return Identifier.of("minecraft", "textures/misc/white.png");
	}
}
