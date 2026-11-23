package com.terminaldetector.drmd.client.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.world.profile.WorldProfile;
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
import org.joml.Vector3f;

/** Soft view-aligned density slices for the existing smoke fields. */
public final class SmokeRenderer {
	private static final int SEGMENTS = 10;
	private static final float[] RING_X = new float[SEGMENTS + 1];
	private static final float[] RING_Y = new float[SEGMENTS + 1];

	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double azimuth = 2 * Math.PI * i / SEGMENTS;
			RING_X[i] = (float) Math.cos(azimuth);
			RING_Y[i] = (float) Math.sin(azimuth);
		}
	}

	private SmokeRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(SmokeRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || ctx.matrixStack() == null) return;
		Vec3d cam = ctx.camera().getPos();
		var profile = WorldProfile.get(mc.world);
		int budget = profile == null ? 48 : switch (profile.performance()) {
			case COMPACT -> 24;
			case BALANCED -> 48;
			case EXTENDED -> 64;
		};
		var clouds = SmokeSystem.visibleNear(cam, budget);
		if (clouds.isEmpty()) return;

		var right = new Vector3f(1, 0, 0).rotate(ctx.camera().getRotation());
		var up = new Vector3f(0, 1, 0).rotate(ctx.camera().getRotation());
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		try {
			BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
			for (int index = clouds.size() - 1; index >= 0; index--) {
				var cloud = clouds.get(index);
				var band = SmokeSystem.drawBand(cloud, cam);
				int puffs = switch (band) {
					case FAR, MID -> 1;
					case NEAR -> 2;
					case LOCAL -> 4;
				};
				int seed = cloud.id.hashCode();
				for (int i = 0; i < puffs; i++) {
					float angle = ((seed & 0xFF) * 0.02464f) + i * 2.39996f;
					float dispersion = cloud.radius * (i == 0 ? 0 : 0.38f);
					float x = (float) (cloud.pos.x - cam.x) + (float) Math.cos(angle) * dispersion;
					float y = (float) (cloud.pos.y - cam.y) + i * cloud.radius * 0.13f;
					float z = (float) (cloud.pos.z - cam.z) + (float) Math.sin(angle) * dispersion;
					float radius = cloud.radius * (puffs == 1 ? 1.18f : 0.82f - 0.055f * i);
					int opacity = Math.min(100, Math.round(cloud.density * (puffs == 1 ? 80 : 67)));
					puff(buf, mat, right, up, x, y, z, radius, cloud.colorRgb, opacity);
				}
			}
			var built = buf.endNullable();
			if (built != null) BufferRenderer.drawWithGlobalProgram(built);
		} finally {
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		}
	}

	private static void puff(BufferBuilder buf, Matrix4f mat, Vector3f right, Vector3f up,
							 float x, float y, float z, float radius, int rgb, int opacity) {
		int core = opacity << 24 | rgb;
		int feather = (opacity / 3) << 24 | rgb;
		for (int i = 0; i < SEGMENTS; i++) {
			buf.vertex(mat, x, y, z).color(core);
			vertex(buf, mat, right, up, x, y, z, radius * 0.58f, i, feather);
			vertex(buf, mat, right, up, x, y, z, radius * 0.58f, i + 1, feather);
			vertex(buf, mat, right, up, x, y, z, radius * 0.58f, i, feather);
			vertex(buf, mat, right, up, x, y, z, radius, i, rgb);
			vertex(buf, mat, right, up, x, y, z, radius, i + 1, rgb);
			vertex(buf, mat, right, up, x, y, z, radius * 0.58f, i, feather);
			vertex(buf, mat, right, up, x, y, z, radius, i + 1, rgb);
			vertex(buf, mat, right, up, x, y, z, radius * 0.58f, i + 1, feather);
		}
	}

	private static void vertex(BufferBuilder buf, Matrix4f mat, Vector3f right, Vector3f up,
							   float x, float y, float z, float radius, int vertex, int color) {
		float u = RING_X[vertex] * radius;
		float v = RING_Y[vertex] * radius;
		buf.vertex(mat, x + u * right.x + v * up.x, y + u * right.y + v * up.y,
				z + u * right.z + v * up.z).color(color);
	}
}
