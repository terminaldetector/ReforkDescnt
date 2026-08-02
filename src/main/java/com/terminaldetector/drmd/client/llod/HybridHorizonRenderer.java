package com.terminaldetector.drmd.client.llod;

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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Hybrid far-horizon LLOD — procedural land plates + cloud banks from altitude,
 * without loading distant chunks.
 *
 * <p>Complements {@link LlodSilhouetteRenderer} (macro objects). Chunks stay local;
 * this fills the orbit view with cheap heightfield proxies and cloud decks.
 */
public final class HybridHorizonRenderer {
	private HybridHorizonRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(HybridHorizonRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.hybridHorizon) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		double y = ctx.camera().getPos().y;
		// Only meaningful once you leave the dense surface play band.
		if (y < WorldLevels.INDUSTRIAL_TOP + 80) return;
		float altitudeFade = MathHelper.clamp((float) ((y - 120) / 220f), 0f, 1f);

		Vec3d cam = ctx.camera().getPos();
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		// Client worlds do not expose the server seed — hash dimension + spawn for stable plates.
		long seed = mc.world.getRegistryKey().getValue().hashCode() * 0x9E3779B97F4A7C15L
				^ ((long) mc.world.getSpawnPos().getX() << 32)
				^ (mc.world.getSpawnPos().getZ() & 0xffffffffL);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		int drawn = 0;
		final int budget = 2_400;

		// --- Distant land plates (seeded heightfield cells around camera XZ) ---
		int grid = y > WorldLevels.SKY_TOP ? 14 : 10;
		double cell = y > WorldLevels.ORBITAL_TOP - 80 ? 96 : 64;
		int cx = MathHelper.floor(cam.x / cell);
		int cz = MathHelper.floor(cam.z / cell);
		for (int ix = -grid; ix <= grid; ix++) {
			for (int iz = -grid; iz <= grid; iz++) {
				if (Math.abs(ix) < 2 && Math.abs(iz) < 2) continue; // near = real chunks
				int gx = cx + ix;
				int gz = cz + iz;
				double px = (gx + 0.5) * cell;
				double pz = (gz + 0.5) * cell;
				double dx = px - cam.x;
				double dz = pz - cam.z;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist < 180 || dist > cell * (grid + 0.5)) continue;

				float h = height(seed, gx, gz);
				float landY = 62f + h * 48f;
				// From orbit, compress toward a curved planetary read.
				if (y > WorldLevels.SKY_TOP) {
					landY = (float) (cam.y - Math.min(y - 40, 180) + h * 12f);
				}
				float half = (float) (cell * 0.48);
				float a = 0.22f * altitudeFade * MathHelper.clamp((float) (dist / 400f), 0.35f, 1f);
				float green = 0.28f + h * 0.25f;
				float blue = 0.18f + (1f - h) * 0.15f;
				box(buf, mat,
						(float) (px - cam.x), (float) (landY - cam.y), (float) (pz - cam.z),
						half, Math.max(2f, 4f + h * 10f), half,
						0.18f, green, blue, a);
				if (++drawn >= budget) break;
			}
			if (drawn >= budget) break;
		}

		// --- Cloud banks (viewed from above / thin air) ---
		if (y > 140 && drawn < budget) {
			int clouds = y > WorldLevels.SKY_TOP ? 28 : 18;
			for (int i = 0; i < clouds && drawn < budget; i++) {
				long s = seed ^ (i * 0x9E3779B97F4A7C15L);
				double ang = ((s >>> 11) & 1023) / 1023.0 * Math.PI * 2;
				double rad = 220 + ((s >>> 21) & 255);
				double px = cam.x + Math.cos(ang) * rad;
				double pz = cam.z + Math.sin(ang) * rad;
				float cloudY = 120f + ((s >>> 3) & 31);
				if (y > WorldLevels.SKY_TOP) cloudY = (float) (cam.y - 90 - ((s >>> 7) & 40));
				float rx = 28f + ((s >>> 15) & 31);
				float rz = 18f + ((s >>> 25) & 23);
				float a = 0.18f * altitudeFade;
				box(buf, mat,
						(float) (px - cam.x), (float) (cloudY - cam.y), (float) (pz - cam.z),
						rx, 3.5f, rz,
						0.85f, 0.88f, 0.95f, a);
				drawn++;
			}
		}

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/** Deterministic 0..1 height from world seed + cell. */
	static float height(long seed, int gx, int gz) {
		long h = seed ^ ((long) gx * 341873128712L) ^ ((long) gz * 132897987541L);
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		return ((h >>> 8) & 0xFFFF) / 65535f;
	}

	private static void box(BufferBuilder buf, Matrix4f mat,
							float cx, float cy, float cz,
							float rx, float ry, float rz,
							float r, float g, float b, float a) {
		float x0 = cx - rx, x1 = cx + rx;
		float y0 = cy, y1 = cy + ry;
		float z0 = cz - rz, z1 = cz + rz;
		quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
		quad(buf, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
		quad(buf, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a * 1.1f);
		quad(buf, mat, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r * 0.7f, g * 0.7f, b * 0.7f, a);
	}

	private static void quad(BufferBuilder buf, Matrix4f mat,
							 float x0, float y0, float z0,
							 float x1, float y1, float z1,
							 float x2, float y2, float z2,
							 float x3, float y3, float z3,
							 float r, float g, float b, float a) {
		buf.vertex(mat, x0, y0, z0).color(r, g, b, Math.min(1f, a));
		buf.vertex(mat, x1, y1, z1).color(r, g, b, Math.min(1f, a));
		buf.vertex(mat, x2, y2, z2).color(r, g, b, Math.min(1f, a));
		buf.vertex(mat, x3, y3, z3).color(r, g, b, Math.min(1f, a));
	}
}
