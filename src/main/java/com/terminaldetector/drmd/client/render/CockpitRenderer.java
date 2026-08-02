package com.terminaldetector.drmd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * First-person cockpit frame — immediate Tessellator quads in camera space.
 *
 * <p>Previously used {@code RenderLayer.getDebugQuads()} through the world consumer, which often
 * never appears under TLauncher / sodium-class pipelines, and after an eye→camera retarget the
 * frame could sit at the wrong origin. This path subtracts nothing extra (offsets are already
 * camera-relative) and draws with {@code PositionColor}, as the other world-space overlays do.
 */
public final class CockpitRenderer {
	private static final double NEAR = 0.55;
	private static final double HALF_W = 0.48;
	private static final double HALF_H = 0.30;
	private static final double BEAM = 0.032;

	private CockpitRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || mc.options == null) return;
			if (!DescentConfig.cockpit || !DescentClientState.enabled) return;
			if (!mc.options.getPerspective().isFirstPerson() || mc.options.hudHidden) return;

			// Never hard-gate on attitude — prime if cold so the frame is not permanently missing.
			if (!ShipAttitudeClient.isPrimed() || !DescentClientState.attitudeValid) {
				ShipAttitudeClient.resetFromPlayer(mc.player);
			}
			if (!ShipAttitudeClient.isPrimed()) return;

			var matrices = context.matrixStack();
			if (matrices == null) return;

			var att = ShipAttitudeClient.get();
			Vec3d f = att.forward();
			Vec3d u = att.up();
			Vec3d r = att.right();
			float a = MathHelper.clamp(DescentConfig.cockpitOpacity, 0.2f, 1f);

			matrices.push();
			Matrix4f m = matrices.peek().getPositionMatrix();

			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.depthMask(false);
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);

			Tessellator tess = Tessellator.getInstance();
			BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

			frame(buf, m, f, u, r, a);
			if (DescentConfig.cockpitInstruments) {
				dashboard(buf, m, f, u, r, a);
			}

			var built = buf.endNullable();
			if (built != null) {
				BufferRenderer.drawWithGlobalProgram(built);
			}

			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
			matrices.pop();
		});
	}

	private static void frame(BufferBuilder buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, float a) {
		beam(buf, m, f, u, r, a, 0.13f, 0.16f, 0.18f,
				-HALF_W, -HALF_H, NEAR, -HALF_W - 0.05, HALF_H, NEAR + 0.10, BEAM);
		beam(buf, m, f, u, r, a, 0.13f, 0.16f, 0.18f,
				HALF_W, -HALF_H, NEAR, HALF_W + 0.05, HALF_H, NEAR + 0.10, BEAM);
		beam(buf, m, f, u, r, a, 0.13f, 0.16f, 0.18f,
				-HALF_W - 0.05, HALF_H, NEAR + 0.10, HALF_W + 0.05, HALF_H, NEAR + 0.10, BEAM);
		beam(buf, m, f, u, r, a, 0.13f, 0.16f, 0.18f,
				-HALF_W, -HALF_H, NEAR, HALF_W, -HALF_H, NEAR, BEAM * 1.4);
		beam(buf, m, f, u, r, a, 0.24f, 0.88f, 0.54f,
				-HALF_W + 0.02, -HALF_H + 0.055, NEAR + 0.005, HALF_W - 0.02, -HALF_H + 0.055, NEAR + 0.005, BEAM * 0.35);
		beam(buf, m, f, u, r, a, 0.13f, 0.16f, 0.18f,
				0, -HALF_H - 0.02, NEAR, 0, -HALF_H - 0.06, NEAR + 0.55, BEAM * 0.9);
	}

	private static void dashboard(BufferBuilder buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, float a) {
		double shelfY = -HALF_H - 0.055;
		beam(buf, m, f, u, r, a, 0.16f, 0.18f, 0.20f,
				-0.34, shelfY, NEAR - 0.10, 0.34, shelfY, NEAR - 0.10, BEAM * 2.2);

		float thrust = MathHelper.clamp(DescentClientState.speed / 28f, 0f, 1f);
		float energy = DescentClientState.energyMax > 0
				? MathHelper.clamp(DescentClientState.energy / DescentClientState.energyMax, 0f, 1f) : 0f;
		float shield = DescentClientState.shieldMax > 0
				? MathHelper.clamp(DescentClientState.shield / DescentClientState.shieldMax, 0f, 1f) : 0f;

		gauge(buf, m, f, u, r, a, -0.30, shelfY + 0.018, thrust, 0.20, 0.47f, 1f, 0.71f);
		gauge(buf, m, f, u, r, a, 0.10, shelfY + 0.018, energy, 0.20, 1f, 0.82f, 0.35f);
		gauge(buf, m, f, u, r, a, -0.10, shelfY + 0.040, shield, 0.20, 0.35f, 0.75f, 1f);
	}

	private static void gauge(BufferBuilder buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, float a,
							  double x, double y, float fill, double width, float cr, float cg, float cb) {
		beam(buf, m, f, u, r, a * 0.55f, 0.07f, 0.09f, 0.10f,
				x, y, NEAR - 0.115, x + width, y, NEAR - 0.115, BEAM * 0.45);
		if (fill > 0.01f) {
			beam(buf, m, f, u, r, a, cr, cg, cb,
					x, y, NEAR - 0.118, x + width * fill, y, NEAR - 0.118, BEAM * 0.45);
		}
	}

	private static void beam(BufferBuilder buf, Matrix4f m, Vec3d f, Vec3d u, Vec3d r, float a,
							 float cr, float cg, float cb,
							 double x1, double y1, double z1, double x2, double y2, double z2, double t) {
		Vec3d p1 = local(f, u, r, x1, y1, z1);
		Vec3d p2 = local(f, u, r, x2, y2, z2);
		Vec3d axis = p2.subtract(p1);
		if (axis.lengthSquared() < 1e-9) return;
		axis = axis.normalize();
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

	private static void quad(BufferBuilder buf, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
							 float cr, float cg, float cb, float alpha) {
		vertex(buf, m, a, cr, cg, cb, alpha);
		vertex(buf, m, b, cr, cg, cb, alpha);
		vertex(buf, m, c, cr, cg, cb, alpha);
		vertex(buf, m, d, cr, cg, cb, alpha);
	}

	private static void vertex(BufferBuilder buf, Matrix4f m, Vec3d p, float r, float g, float b, float a) {
		buf.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a);
	}
}
