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
 * Skybox orbital belt — engine-driven sky motion (Oblivion-style drift), not a built volume.
 *
 * <p>The three layers form one parallelepiped in scale; this hook paints the upper face in the
 * skybox and slowly rotates it with world time so the belt feels alive without loading chunks.
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
					"§a◉ ORBITAL BELT §7visible — §fthink about relocating your surface base§7 before vacuum."), false);
		}

		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		// Huge distant ring in sky space — camera-relative so it never loads chunks.
		// Slow yaw drift = Oblivion sky motion using the render clock, not worldgen.
		double radius = 220;
		double ringY = 40;
		int segs = 72;
		float bodyA = 0.35f * alpha;
		float lightA = 0.85f * alpha;
		double spin = (mc.world.getTime() + ctx.tickCounter().getTickDelta(false)) * 0.0018;

		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2 / segs + spin;
			double a1 = (i + 1) * Math.PI * 2 / segs + spin;
			float x0 = (float) (Math.cos(a0) * radius);
			float z0 = (float) (Math.sin(a0) * radius);
			float x1 = (float) (Math.cos(a1) * radius);
			float z1 = (float) (Math.sin(a1) * radius);
			float y0 = (float) ringY - 2.2f;
			float y1 = (float) ringY + 2.2f;
			// Dark structure
			quad(buf, mat, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, 0.05f, 0.07f, 0.09f, bodyA);
			// Green installation lights along the outer lip
			if (i % 2 == 0) {
				float lx = (x0 + x1) * 0.5f;
				float lz = (z0 + z1) * 0.5f;
				float s = 1.1f;
				quad(buf, mat,
						lx - s, y1, lz - s, lx + s, y1, lz - s,
						lx + s, y1 + 0.6f, lz + s, lx - s, y1 + 0.6f, lz + s,
						0.15f, 1f, 0.35f, lightA);
			}
		}

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
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
