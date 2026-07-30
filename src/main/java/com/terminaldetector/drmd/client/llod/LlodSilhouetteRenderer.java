package com.terminaldetector.drmd.client.llod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.world.llod.LlodLevel;
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
 * Draws distant megastructures as translucent voxel silhouettes (LLOD MEDIUM / SILHOUETTE).
 */
public final class LlodSilhouetteRenderer {
	private LlodSilhouetteRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(LlodSilhouetteRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		var list = LlodClientState.INSTANCE.entries();
		if (list.isEmpty()) return;

		Vec3d cam = ctx.camera().getPos();
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		for (LlodClientState.Entry e : list) {
			float a = e.level() == LlodLevel.MEDIUM ? 0.35f : 0.18f;
			float r = ((e.colorRgb() >> 16) & 0xFF) / 255f;
			float g = ((e.colorRgb() >> 8) & 0xFF) / 255f;
			float b = (e.colorRgb() & 0xFF) / 255f;
			float sx = Math.max(4f, e.radiusX());
			float sy = Math.max(4f, e.radiusY());
			float sz = Math.max(4f, e.radiusZ());
			if (e.level() == LlodLevel.SILHOUETTE) {
				sx *= 1.05f;
				sy *= 1.05f;
				sz *= 1.05f;
			}
			box(buf, mat,
					(float) (e.center().x - cam.x),
					(float) (e.center().y - cam.y),
					(float) (e.center().z - cam.z),
					sx, sy, sz, r, g, b, a);
		}

		var built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void box(BufferBuilder buf, Matrix4f mat,
							float cx, float cy, float cz,
							float rx, float ry, float rz,
							float red, float g, float b, float a) {
		float x0 = cx - rx, x1 = cx + rx;
		float y0 = cy - ry, y1 = cy + ry;
		float z0 = cz - rz, z1 = cz + rz;
		quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, red, g, b, a);
		quad(buf, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, red, g, b, a);
		quad(buf, mat, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, red, g, b, a);
		quad(buf, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, red, g, b, a);
		quad(buf, mat, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, red, g, b, a);
		quad(buf, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, red, g, b, a);
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
