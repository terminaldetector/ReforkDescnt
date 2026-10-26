package com.terminaldetector.drmd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.build.ScaffoldClientState;
import com.terminaldetector.drmd.world.build.ConstructLaserItem;
import com.terminaldetector.drmd.world.build.ConstructLaserTier;
import com.terminaldetector.drmd.world.build.ConstructShape;
import com.terminaldetector.drmd.world.build.ConstructShapes;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * 6DoF construction scaffold — wireframe frame before commit, live aim ghost while holding a laser.
 */
public final class ConstructScaffoldRenderer {
	private ConstructScaffoldRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(ConstructScaffoldRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;

		ItemStack stack = mc.player.getMainHandStack();
		if (!(stack.getItem() instanceof ConstructLaserItem laser)) {
			if (!ScaffoldClientState.INSTANCE.active()) return;
		}

		Vec3d cam = ctx.camera().getPos();
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);
		RenderSystem.lineWidth(2.5f);

		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

		if (ScaffoldClientState.INSTANCE.active()) {
			Vector3f rgb = colorForShape(ScaffoldClientState.INSTANCE.shapeId());
			drawCells(buf, mat, cam, ScaffoldClientState.INSTANCE.positions(), rgb, 0.95f, 220);
		} else if (stack.getItem() instanceof ConstructLaserItem laser) {
			ConstructLaserTier tier = laser.tier();
			ConstructShape shape = ConstructLaserItem.shapeOf(stack, tier);
			if (shape.usesScaffold()) {
				List<BlockPos> ghost = ConstructShapes.resolve(mc.world, mc.player, tier, shape, false);
				drawCells(buf, mat, cam, ghost, tier.beamRgb, 0.45f, 160);
			}
		}

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.lineWidth(1f);
	}

	private static Vector3f colorForShape(String shapeId) {
		return switch (shapeId == null ? "" : shapeId) {
			case "line" -> new Vector3f(0.3f, 1f, 0.35f);
			case "wall", "frame" -> new Vector3f(1f, 0.9f, 0.25f);
			case "solid", "cylinder", "stream" -> new Vector3f(0.3f, 0.6f, 1f);
			default -> new Vector3f(0.85f, 0.35f, 1f);
		};
	}

	private static void drawCells(BufferBuilder buf, Matrix4f mat, Vec3d cam,
			List<BlockPos> cells, Vector3f rgb, float alpha, int cap) {
		if (cells == null || cells.isEmpty()) return;
		int n = Math.min(cells.size(), cap);
		// For dense presets, draw every Nth cell wireframe
		int step = n > 120 ? 2 : 1;
		for (int i = 0; i < n; i += step) {
			BlockPos p = cells.get(i);
			float x = (float) (p.getX() - cam.x);
			float y = (float) (p.getY() - cam.y);
			float z = (float) (p.getZ() - cam.z);
			wireBox(buf, mat, x, y, z, rgb.x, rgb.y, rgb.z, alpha);
		}
	}

	private static void wireBox(BufferBuilder buf, Matrix4f mat,
			float x, float y, float z, float r, float g, float b, float a) {
		float x1 = x + 1f, y1 = y + 1f, z1 = z + 1f;
		// bottom
		line(buf, mat, x, y, z, x1, y, z, r, g, b, a);
		line(buf, mat, x1, y, z, x1, y, z1, r, g, b, a);
		line(buf, mat, x1, y, z1, x, y, z1, r, g, b, a);
		line(buf, mat, x, y, z1, x, y, z, r, g, b, a);
		// top
		line(buf, mat, x, y1, z, x1, y1, z, r, g, b, a);
		line(buf, mat, x1, y1, z, x1, y1, z1, r, g, b, a);
		line(buf, mat, x1, y1, z1, x, y1, z1, r, g, b, a);
		line(buf, mat, x, y1, z1, x, y1, z, r, g, b, a);
		// uprights
		line(buf, mat, x, y, z, x, y1, z, r, g, b, a);
		line(buf, mat, x1, y, z, x1, y1, z, r, g, b, a);
		line(buf, mat, x1, y, z1, x1, y1, z1, r, g, b, a);
		line(buf, mat, x, y, z1, x, y1, z1, r, g, b, a);
	}

	private static void line(BufferBuilder buf, Matrix4f mat,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float r, float g, float b, float a) {
		buf.vertex(mat, x0, y0, z0).color(r, g, b, a);
		buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
	}
}
