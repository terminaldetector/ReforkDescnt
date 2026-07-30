package com.terminaldetector.drmd.client.llod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.world.llod.LlodLevel;
import com.terminaldetector.drmd.world.llod.VoxelLodMesh;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Voxel LLOD renderer:
 * LLOD0 silhouette → LLOD1 large forms → LLOD2 region → Chunk (skip).
 * Fog is disabled so far bands stay readable in 6DoF flight.
 */
public final class LlodSilhouetteRenderer {
	private static final Map<UUID, CachedMesh> CACHE = new HashMap<>();

	private record CachedMesh(LlodLevel level, long seed, List<VoxelLodMesh.Voxel> voxels) {}

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
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		// Critical: vanilla fog washes LLOD0/1 to sky color — push fog far for this pass.
		disableFogForPass();

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		int drawn = 0;
		final int frameBudget = 14_000;
		Set<UUID> alive = new HashSet<>(list.size() * 2);

		for (LlodClientState.Entry e : list) {
			if (!e.level().drawsVoxels()) continue;
			alive.add(e.id());
			List<VoxelLodMesh.Voxel> voxels = meshFor(e);
			float a = alpha(e.level());
			float r = ((e.colorRgb() >> 16) & 0xFF) / 255f;
			float g = ((e.colorRgb() >> 8) & 0xFF) / 255f;
			float b = (e.colorRgb() & 0xFF) / 255f;
			if (e.level() == LlodLevel.LLOD0) {
				r = Math.min(1f, r * 1.12f);
				g = Math.min(1f, g * 1.12f);
				b = Math.min(1f, b * 1.08f);
			}

			for (VoxelLodMesh.Voxel v : voxels) {
				float hx = v.half();
				// Slightly inflate far voxels so horizon forms read at range
				if (e.level() == LlodLevel.LLOD0) hx *= 1.35f;
				else if (e.level() == LlodLevel.LLOD1) hx *= 1.15f;
				box(buf, mat,
						(float) (v.x() - cam.x),
						(float) (v.y() - cam.y),
						(float) (v.z() - cam.z),
						hx, hx, hx,
						r, g, b, a);
				if (++drawn >= frameBudget) break;
			}
			if (drawn >= frameBudget) break;
		}

		var built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();

		CACHE.keySet().removeIf(id -> !alive.contains(id));
	}

	private static List<VoxelLodMesh.Voxel> meshFor(LlodClientState.Entry e) {
		CachedMesh cached = CACHE.get(e.id());
		if (cached != null && cached.level == e.level() && cached.seed == e.seed()) {
			return cached.voxels;
		}
		List<VoxelLodMesh.Voxel> built = VoxelLodMesh.build(
				e.kind(), e.center(), e.radiusX(), e.radiusY(), e.radiusZ(), e.level(), e.seed());
		CACHE.put(e.id(), new CachedMesh(e.level(), e.seed(), built));
		return built;
	}

	private static float alpha(LlodLevel level) {
		return switch (level) {
			case LLOD0 -> 0.62f;
			case LLOD1 -> 0.48f;
			case LLOD2 -> 0.36f;
			default -> 0.25f;
		};
	}

	/** Yarn 1.21.1 still exposes setShaderFog*; fall back quietly if remapped away. */
	private static void disableFogForPass() {
		try {
			RenderSystem.setShaderFogStart(1.0E10F);
			RenderSystem.setShaderFogEnd(1.0E10F);
		} catch (Throwable ignored) {
			// Renderer still draws; worst case far voxels tint with world fog.
		}
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
