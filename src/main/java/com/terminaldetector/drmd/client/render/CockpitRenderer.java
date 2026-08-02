package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The Pyro's cockpit, as real geometry sitting in the world in front of the pilot.
 *
 * <p>Drawn in world space and re-anchored to the ship basis every frame rather than blitted flat
 * over the screen. That is the whole point: the frame has depth, so terrain occludes nothing of it,
 * parallax reads correctly against the horizon, and — because it is pinned to the hull axes rather
 * than to the screen — it stays put through a barrel roll instead of sliding around like a decal.
 *
 * <p>Geometry is generated rather than modelled. Minecraft's own shapes are boxes, so a hull built
 * from beams and panels sits in the game's visual language without needing an asset pipeline, and
 * it costs one buffer per frame.
 */
public final class CockpitRenderer {
	/** Distance from the eye to the canopy plane. */
	private static final double NEAR = 0.62;
	/** Half-width and half-height of the canopy opening at that distance. */
	private static final double HALF_W = 0.52;
	private static final double HALF_H = 0.34;
	/** Beam thickness. */
	private static final double BEAM = 0.035;

	private static final int HULL_R = 34, HULL_G = 40, HULL_B = 46;
	private static final int TRIM_R = 60, TRIM_G = 224, TRIM_B = 138;
	private static final int GLOW_R = 120, GLOW_G = 255, GLOW_B = 180;

	private CockpitRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || mc.options == null) return;
			if (!DescentConfig.cockpit || !DescentClientState.enabled) return;
			if (!mc.options.getPerspective().isFirstPerson() || mc.options.hudHidden) return;
			if (!DescentClientState.attitudeValid || !ShipAttitudeClient.isPrimed()) return;

			MatrixStack matrices = context.matrixStack();
			VertexConsumerProvider consumers = context.consumers();
			if (matrices == null || consumers == null) return;

			var att = ShipAttitudeClient.get();
			Vec3d f = att.forward();
			Vec3d u = att.up();
			Vec3d r = att.right();

			Vec3d cam = context.camera().getPos();
			Vec3d eye = mc.player.getLerpedPos(context.tickCounter().getTickDelta(true))
					.add(0, mc.player.getStandingEyeHeight(), 0);

			matrices.push();
			matrices.translate(eye.x - cam.x, eye.y - cam.y, eye.z - cam.z);

			int alpha = (int) (MathHelper.clamp(DescentConfig.cockpitOpacity, 0.2f, 1f) * 255);
			VertexConsumer buf = consumers.getBuffer(RenderLayer.getDebugQuads());
			Matrix4f m = matrices.peek().getPositionMatrix();

			frame(buf, m, f, u, r, alpha);
			if (DescentConfig.cockpitInstruments) {
				dashboard(buf, m, f, u, r, alpha);
			}

			matrices.pop();
		});
	}

	/** Canopy surround: two side pillars, a brow, a sill, and the nose spine. */
	private static void frame(VertexConsumer buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, int a) {
		// Side pillars, swept slightly outward toward the nose so the opening is not a plain box.
		beam(buf, m, f, u, r, a, HULL_R, HULL_G, HULL_B,
				-HALF_W, -HALF_H, NEAR, -HALF_W - 0.05, HALF_H, NEAR + 0.10, BEAM);
		beam(buf, m, f, u, r, a, HULL_R, HULL_G, HULL_B,
				HALF_W, -HALF_H, NEAR, HALF_W + 0.05, HALF_H, NEAR + 0.10, BEAM);
		// Brow and sill.
		beam(buf, m, f, u, r, a, HULL_R, HULL_G, HULL_B,
				-HALF_W - 0.05, HALF_H, NEAR + 0.10, HALF_W + 0.05, HALF_H, NEAR + 0.10, BEAM);
		beam(buf, m, f, u, r, a, HULL_R, HULL_G, HULL_B,
				-HALF_W, -HALF_H, NEAR, HALF_W, -HALF_H, NEAR, BEAM * 1.4);
		// Neon trim along the sill — the cyberpunk read, and it doubles as a horizon reference.
		beam(buf, m, f, u, r, a, TRIM_R, TRIM_G, TRIM_B,
				-HALF_W + 0.02, -HALF_H + 0.055, NEAR + 0.005, HALF_W - 0.02, -HALF_H + 0.055, NEAR + 0.005, BEAM * 0.35);
		// Nose spine running away from the pilot, so the hull has length.
		beam(buf, m, f, u, r, a, HULL_R, HULL_G, HULL_B,
				0, -HALF_H - 0.02, NEAR, 0, -HALF_H - 0.06, NEAR + 0.55, BEAM * 0.9);
	}

	/** Instrument shelf below the sight line: thrust on the left, energy on the right. */
	private static void dashboard(VertexConsumer buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, int a) {
		double shelfY = -HALF_H - 0.055;
		beam(buf, m, f, u, r, a, HULL_R + 8, HULL_G + 8, HULL_B + 8,
				-0.34, shelfY, NEAR - 0.10, 0.34, shelfY, NEAR - 0.10, BEAM * 2.2);

		// Bars grow from the left edge of each gauge, so length reads as value at a glance.
		float thrust = MathHelper.clamp(DescentClientState.speed / 28f, 0f, 1f);
		float energy = DescentClientState.energyMax > 0
				? MathHelper.clamp(DescentClientState.energy / DescentClientState.energyMax, 0f, 1f) : 0f;
		float shield = DescentClientState.shieldMax > 0
				? MathHelper.clamp(DescentClientState.shield / DescentClientState.shieldMax, 0f, 1f) : 0f;

		gauge(buf, m, f, u, r, a, -0.30, shelfY + 0.018, thrust, 0.20, GLOW_R, GLOW_G, GLOW_B);
		gauge(buf, m, f, u, r, a, 0.10, shelfY + 0.018, energy, 0.20, 255, 210, 90);
		gauge(buf, m, f, u, r, a, -0.10, shelfY + 0.040, shield, 0.20, 90, 190, 255);
	}

	private static void gauge(VertexConsumer buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, int a,
							  double x, double y, float fill, double width, int cr, int cg, int cb) {
		// Track, then the filled part on top of it.
		beam(buf, m, f, u, r, (int) (a * 0.55), 18, 22, 26,
				x, y, NEAR - 0.115, x + width, y, NEAR - 0.115, BEAM * 0.45);
		if (fill > 0.01f) {
			beam(buf, m, f, u, r, a, cr, cg, cb,
					x, y, NEAR - 0.118, x + width * fill, y, NEAR - 0.118, BEAM * 0.45);
		}
	}

	/**
	 * A box between two points in ship-local coordinates.
	 *
	 * <p>Local axes are (right, up, forward), so every offset below reads as "so far to the pilot's
	 * right, so far above the sight line, so far toward the nose" regardless of how the hull is
	 * currently oriented in the world.
	 */
	private static void beam(VertexConsumer buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, int a,
							 int cr, int cg, int cb,
							 double x1, double y1, double z1, double x2, double y2, double z2, double t) {
		Vec3d p1 = local(f, u, r, x1, y1, z1);
		Vec3d p2 = local(f, u, r, x2, y2, z2);
		Vec3d axis = p2.subtract(p1);
		if (axis.lengthSquared() < 1e-9) return;
		axis = axis.normalize();
		// Any two perpendiculars will do for a beam's cross-section; pick the more stable one.
		Vec3d side = Math.abs(axis.dotProduct(u)) < 0.9 ? axis.crossProduct(u) : axis.crossProduct(r);
		side = side.normalize().multiply(t);
		Vec3d other = axis.crossProduct(side).normalize().multiply(t);

		Vec3d[] c = new Vec3d[8];
		for (int i = 0; i < 8; i++) {
			Vec3d base = (i & 1) == 0 ? p1 : p2;
			Vec3d s = side.multiply((i & 2) == 0 ? -1 : 1);
			Vec3d o = other.multiply((i & 4) == 0 ? -1 : 1);
			c[i] = base.add(s).add(o);
		}
		quad(buf, m, c[0], c[2], c[6], c[4], cr, cg, cb, a);
		quad(buf, m, c[1], c[5], c[7], c[3], cr, cg, cb, a);
		quad(buf, m, c[0], c[4], c[5], c[1], cr, cg, cb, a);
		quad(buf, m, c[2], c[3], c[7], c[6], cr, cg, cb, a);
		quad(buf, m, c[0], c[1], c[3], c[2], cr, cg, cb, a);
		quad(buf, m, c[4], c[6], c[7], c[5], cr, cg, cb, a);
	}

	private static Vec3d local(Vec3d f, Vec3d u, Vec3d r, double x, double y, double z) {
		return r.multiply(x).add(u.multiply(y)).add(f.multiply(z));
	}

	private static void quad(VertexConsumer buf, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
							 int cr, int cg, int cb, int alpha) {
		vertex(buf, m, a, cr, cg, cb, alpha);
		vertex(buf, m, b, cr, cg, cb, alpha);
		vertex(buf, m, c, cr, cg, cb, alpha);
		vertex(buf, m, d, cr, cg, cb, alpha);
	}

	private static void vertex(VertexConsumer buf, Matrix4f m, Vec3d p, int r, int g, int b, int a) {
		buf.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a);
	}
}
