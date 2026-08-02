package com.terminaldetector.drmd.client.llod.planet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.llod.planet.PlanetCell;
import com.terminaldetector.drmd.world.llod.planet.PlanetVoxelMath;
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
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * End / orbit planetary floor — voxel map of explored Overworld + procedural fog-of-war.
 * Replaces empty void below End with a concrete terrain shelf.
 */
public final class PlanetFloorRenderer {
	private PlanetFloorRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PlanetFloorRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.planetFloor) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;

		boolean endDim = mc.world.getRegistryKey() == World.END;
		double y = ctx.camera().getPos().y;
		boolean highOrbit = mc.world.getRegistryKey() == World.OVERWORLD
				&& y >= WorldLevels.SKY_TOP - 20;
		if (!endDim && !highOrbit) return;

		var cells = PlanetMapClientState.INSTANCE.cells();
		if (cells.isEmpty()) return;

		Vec3d cam = ctx.camera().getPos();
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		long seed = PlanetMapClientState.INSTANCE.seed();

		// Floor plane: in End, sit well below islands; in OW orbit, below camera.
		float floorY = endDim ? (float) (cam.y - 48) : (float) (cam.y - Math.min(y - 80, 200));
		float scale = endDim ? 2.8f : 3.6f; // cell size on the visual shelf
		int originCx = PlanetMapClientState.INSTANCE.originCx();
		int originCz = PlanetMapClientState.INSTANCE.originCz();
		// Centre the shelf under the camera.
		double originX = cam.x - originCx * scale - scale * 0.5;
		double originZ = cam.z - originCz * scale - scale * 0.5;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		int drawn = 0;
		final int budget = 10_000;
		int perCell = Math.max(8, budget / Math.max(1, cells.size()));

		for (PlanetCell cell : cells) {
			var voxels = PlanetVoxelMath.expand(cell, seed, originX, originZ, floorY, scale, perCell);
			for (var v : voxels) {
				float a = ((v.argb() >>> 24) & 0xFF) / 255f;
				float r = ((v.argb() >>> 16) & 0xFF) / 255f;
				float g = ((v.argb() >>> 8) & 0xFF) / 255f;
				float b = (v.argb() & 0xFF) / 255f;
				// Subtle curvature toward horizon for planetary read.
				float dx = v.x() - (float) cam.x;
				float dz = v.z() - (float) cam.z;
				float bend = -0.00035f * (dx * dx + dz * dz);
				box(buf, mat,
						v.x() - (float) cam.x,
						v.y() + bend - (float) cam.y,
						v.z() - (float) cam.z,
						v.half(), v.half() * 0.85f, v.half(),
						r, g, b, a);
				if (++drawn >= budget) break;
			}
			if (drawn >= budget) break;
		}

		// Soft void shelf under everything so End never reads as infinite black.
		if (endDim) {
			float shelf = 180f;
			box(buf, mat, 0, floorY - 6f - (float) cam.y, 0, shelf, 2f, shelf,
					0.08f, 0.06f, 0.12f, 0.55f);
		}

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void box(BufferBuilder buf, Matrix4f mat,
							float cx, float cy, float cz,
							float rx, float ry, float rz,
							float r, float g, float b, float a) {
		float x0 = cx - rx, x1 = cx + rx;
		float y0 = cy, y1 = cy + Math.max(0.4f, ry);
		float z0 = cz - rz, z1 = cz + rz;
		a = MathHelper.clamp(a, 0f, 1f);
		quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
		quad(buf, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
		quad(buf, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, Math.min(1f, a * 1.15f));
		quad(buf, mat, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r * 0.65f, g * 0.65f, b * 0.65f, a);
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
