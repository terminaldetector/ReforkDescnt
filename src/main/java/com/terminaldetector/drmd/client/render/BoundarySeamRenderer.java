package com.terminaldetector.drmd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.world.layer.LayerBridge;
import com.terminaldetector.drmd.world.layer.WorldLayer;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Client display hook at layer seams — fake block curtain, not a built parallelepiped.
 *
 * <p>When the pilot is near a {@link LayerBridge} teleport edge, draw a thin plate of coloured
 * quads at that Y so the boundary reads as a place. Worldgen does not fill three stacked volumes;
 * this is the visual only.
 */
public final class BoundarySeamRenderer {
	/** How close (blocks) before the curtain appears. */
	private static final double SHOW_DIST = 48.0;
	private static final int HALF = 24;
	private static final float CELL = 2f;

	private BoundarySeamRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(BoundarySeamRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		double py = mc.player.getY();
		int seamY = LayerBridge.nearestSeamY(py);
		if (seamY == Integer.MIN_VALUE) return;
		double dist = Math.abs(py - seamY);
		if (dist > SHOW_DIST) return;

		WorldLayer below = WorldLayer.at(seamY - 1);
		WorldLayer above = WorldLayer.at(seamY + 1);
		float alpha = (float) MathHelper.clamp(1.0 - dist / SHOW_DIST, 0.12, 0.55);
		if (DescentClientState.enabled) alpha *= 0.85f;

		Vec3d cam = ctx.camera().getPos();
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		int ox = MathHelper.floor(mc.player.getX() / CELL) * (int) CELL;
		int oz = MathHelper.floor(mc.player.getZ() / CELL) * (int) CELL;
		float y = (float) (seamY + 0.02 - cam.y);

		int argbBelow = below.hudColor;
		int argbAbove = above.hudColor;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		// Checker of "blocks" on the seam plane — reads as a boundary, not a solid cube world.
		for (int dx = -HALF; dx < HALF; dx += (int) CELL) {
			for (int dz = -HALF; dz < HALF; dz += (int) CELL) {
				boolean alt = (((dx + dz) / (int) CELL) & 1) == 0;
				int rgb = alt ? argbBelow : argbAbove;
				float r = ((rgb >> 16) & 0xFF) / 255f;
				float g = ((rgb >> 8) & 0xFF) / 255f;
				float b = (rgb & 0xFF) / 255f;
				float x0 = (float) (ox + dx - cam.x);
				float z0 = (float) (oz + dz - cam.z);
				float x1 = x0 + CELL * 0.92f;
				float z1 = z0 + CELL * 0.92f;
				quad(buf, mat, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, r, g, b, alpha);
			}
		}

		// Thin vertical rim so the teleport zone has a thickness cue.
		float rim = LayerBridge.SEAM_HALF;
		float y0 = (float) (seamY - rim - cam.y);
		float y1 = (float) (seamY + rim - cam.y);
		float rr = ((argbAbove >> 16) & 0xFF) / 255f;
		float gg = ((argbAbove >> 8) & 0xFF) / 255f;
		float bb = (argbAbove & 0xFF) / 255f;
		float aRim = alpha * 0.35f;
		for (int i = -HALF; i <= HALF; i += (int) CELL * 4) {
			float x = (float) (ox + i - cam.x);
			float z = (float) (oz - HALF - cam.z);
			quad(buf, mat, x, y0, z, x + 0.15f, y0, z, x + 0.15f, y1, z, x, y1, z, rr, gg, bb, aRim);
			z = (float) (oz + HALF - cam.z);
			quad(buf, mat, x, y0, z, x + 0.15f, y0, z, x + 0.15f, y1, z, x, y1, z, rr, gg, bb, aRim);
		}

		var built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
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
