package com.terminaldetector.drmd.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Spark-style orbital vista — planet sphere + dark solid ring + neon-green outer halo.
 *
 * <p>Camera-relative skybox (no chunk load). This is the “кольцо вокруг планеты”, not techno-junk
 * at R=2048. World connection stays on {@code LayerBridge} / {@code SeamWarmup}; this is display.
 */
public final class OrbitalBeltSkyRenderer {
	private static boolean warned;

	private OrbitalBeltSkyRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(OrbitalBeltSkyRenderer::render);
	}

	public static void clearWarn() {
		warned = false;
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.levelSky || !DescentConfig.orbitalBeltSky) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		double y = ctx.camera().getPos().y;
		float alpha = beltAlpha(y);
		if (alpha < 0.02f) return;

		if (!warned && y >= WorldLevels.SURFACE_TOP - 40) {
			warned = true;
			mc.player.sendMessage(net.minecraft.text.Text.literal(
					"§a◉ ORBIT RING §7— Spark vista · Klondike islands ahead · relocate before vacuum."), false);
		}

		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		double spin = (mc.world.getTime() + ctx.tickCounter().getTickDelta(false)) * 0.0009;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		// Planet centre below the camera — reads as the world globe under the ring.
		float cx = 0f;
		float cy = -95f;
		float cz = -40f;
		float planetR = 88f;

		paintPlanet(buf, mat, cx, cy, cz, planetR, alpha, spin);
		// Dark solid ring (Spark inner band)
		paintRing(buf, mat, cx, cy, cz, planetR * 1.22f, planetR * 1.34f, 3.2f, spin,
				0.04f, 0.05f, 0.07f, 0.78f * alpha, 48);
		// Neon-green outer halo
		paintRing(buf, mat, cx, cy, cz, planetR * 1.36f, planetR * 1.48f, 1.6f, spin,
				0.20f, 1.0f, 0.28f, 0.72f * alpha, 64);
		// Soft glow rim just outside the green
		paintRing(buf, mat, cx, cy, cz, planetR * 1.48f, planetR * 1.58f, 0.8f, spin,
				0.35f, 1.0f, 0.45f, 0.28f * alpha, 48);

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void paintPlanet(BufferBuilder buf, Matrix4f mat,
									float cx, float cy, float cz, float r,
									float alpha, double spin) {
		int latBands = 12;
		int lonBands = 20;
		for (int lat = 0; lat < latBands; lat++) {
			double a0 = Math.PI * (-0.5 + (double) lat / latBands);
			double a1 = Math.PI * (-0.5 + (double) (lat + 1) / latBands);
			float y0 = (float) (Math.sin(a0) * r);
			float y1 = (float) (Math.sin(a1) * r);
			float rr0 = (float) (Math.cos(a0) * r);
			float rr1 = (float) (Math.cos(a1) * r);
			for (int lon = 0; lon < lonBands; lon++) {
				double b0 = spin + Math.PI * 2 * lon / lonBands;
				double b1 = spin + Math.PI * 2 * (lon + 1) / lonBands;
				float x00 = (float) (Math.cos(b0) * rr0);
				float z00 = (float) (Math.sin(b0) * rr0);
				float x01 = (float) (Math.cos(b1) * rr0);
				float z01 = (float) (Math.sin(b1) * rr0);
				float x10 = (float) (Math.cos(b0) * rr1);
				float z10 = (float) (Math.sin(b0) * rr1);
				float x11 = (float) (Math.cos(b1) * rr1);
				float z11 = (float) (Math.sin(b1) * rr1);
				// Ocean / cloud bands — cooler dark teal planet like Spark reference
				float t = (lat + 0.5f) / latBands;
				float shade = 0.55f + 0.45f * (float) Math.sin(b0 * 3 + lat);
				float pr = 0.08f * shade;
				float pg = (0.22f + 0.18f * t) * shade;
				float pb = (0.38f + 0.25f * (1f - t)) * shade;
				float a = 0.92f * alpha;
				quad(buf, mat,
						cx + x00, cy + y0, cz + z00,
						cx + x01, cy + y0, cz + z01,
						cx + x11, cy + y1, cz + z11,
						cx + x10, cy + y1, cz + z10,
						pr, pg, pb, a);
			}
		}
	}

	private static void paintRing(BufferBuilder buf, Matrix4f mat,
								  float cx, float cy, float cz,
								  float rInner, float rOuter, float halfH,
								  double spin,
								  float r, float g, float b, float a, int segs) {
		for (int i = 0; i < segs; i++) {
			double a0 = spin + Math.PI * 2 * i / segs;
			double a1 = spin + Math.PI * 2 * (i + 1) / segs;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			float y0 = cy - halfH;
			float y1 = cy + halfH;
			// Top face
			quad(buf, mat,
					cx + c0 * rInner, y1, cz + s0 * rInner,
					cx + c1 * rInner, y1, cz + s1 * rInner,
					cx + c1 * rOuter, y1, cz + s1 * rOuter,
					cx + c0 * rOuter, y1, cz + s0 * rOuter,
					r, g, b, a);
			// Outer wall
			quad(buf, mat,
					cx + c0 * rOuter, y0, cz + s0 * rOuter,
					cx + c1 * rOuter, y0, cz + s1 * rOuter,
					cx + c1 * rOuter, y1, cz + s1 * rOuter,
					cx + c0 * rOuter, y1, cz + s0 * rOuter,
					r * 0.85f, g * 0.85f, b * 0.85f, a * 0.9f);
		}
	}

	/** 0 at mid-surface, ramps through sky, full in orbital. */
	static float beltAlpha(double y) {
		if (y < WorldLevels.SURFACE_TOP - 60) return 0f;
		if (y >= WorldLevels.SKY_TOP) return 1f;
		float t = (float) ((y - (WorldLevels.SURFACE_TOP - 60))
				/ (WorldLevels.SKY_TOP - (WorldLevels.SURFACE_TOP - 60)));
		return MathHelper.clamp(t * t * (3 - 2 * t), 0f, 1f);
	}

	private static void quad(BufferBuilder buf, Matrix4f mat,
							 float x0, float y0, float z0,
							 float x1, float y1, float z1,
							 float x2, float y2, float z2,
							 float x3, float y3, float z3,
							 float r, float g, float b, float a) {
		buf.vertex(mat, x0, y0, z0).color(r, g, b, a);
		buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
		buf.vertex(mat, x2, y2, z2).color(r, g, b, a);
		buf.vertex(mat, x3, y3, z3).color(r, g, b, a);
	}
}
