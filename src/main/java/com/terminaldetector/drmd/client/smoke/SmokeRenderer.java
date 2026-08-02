package com.terminaldetector.drmd.client.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Smoke by distance band: far columns → large puffs → local blobs.
 */
public final class SmokeRenderer {
	private SmokeRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(SmokeRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		var clouds = SmokeSystem.visibleNear(mc.player.getPos(), 64);
		if (clouds.isEmpty()) return;

		Vec3d cam = ctx.camera().getPos();
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		int drawn = 0;
		for (SmokeSystem.Cloud c : clouds) {
			SmokeSystem.Band band = SmokeSystem.drawBand(c, cam);
			float a = switch (band) {
				case FAR -> 0.12f * c.density;
				case MID -> 0.18f * c.density;
				case NEAR -> 0.28f * c.density;
				case LOCAL -> 0.35f * c.density;
			};
			float r = ((c.colorRgb >> 16) & 0xFF) / 255f;
			float g = ((c.colorRgb >> 8) & 0xFF) / 255f;
			float b = (c.colorRgb & 0xFF) / 255f;
			float rad = c.radius * switch (band) {
				case FAR -> 2.2f;
				case MID -> 1.4f;
				default -> 1.0f;
			};
			int puffs = band == SmokeSystem.Band.FAR ? 1 : band == SmokeSystem.Band.MID ? 3 : 5;
			for (int i = 0; i < puffs; i++) {
				float ox = (i - puffs / 2f) * rad * 0.35f;
				box(buf, mat,
						(float) (c.pos.x + ox - cam.x),
						(float) (c.pos.y - cam.y),
						(float) (c.pos.z - cam.z),
						rad * (band == SmokeSystem.Band.FAR ? 1.5f : 0.7f),
						r, g, b, a);
				if (++drawn > 400) break;
			}
			if (drawn > 400) break;
		}
		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void box(BufferBuilder buf, Matrix4f mat, float cx, float cy, float cz,
							float r, float red, float g, float b, float a) {
		float x0 = cx - r, x1 = cx + r, y0 = cy - r * 0.6f, y1 = cy + r * 1.2f, z0 = cz - r, z1 = cz + r;
		quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, red, g, b, a);
		quad(buf, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, red, g, b, a);
		quad(buf, mat, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, red, g, b, a);
		quad(buf, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, red, g, b, a);
		quad(buf, mat, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, red, g, b, a);
		quad(buf, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, red, g, b, a);
	}

	private static void quad(BufferBuilder buf, Matrix4f mat,
							 float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3,
							 float r, float g, float b, float a) {
		buf.vertex(mat, x0, y0, z0).color(r, g, b, a);
		buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
		buf.vertex(mat, x2, y2, z2).color(r, g, b, a);
		buf.vertex(mat, x3, y3, z3).color(r, g, b, a);
	}
}
