package com.terminaldetector.drmd.client.planet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.planet.PlanetMap;
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
 * The distant world, drawn as voxels: terrain past the chunk edge, and the things built on it.
 *
 * <p>Vanilla stops at the render distance and the projection stops at four times that, so from
 * altitude almost everything worth looking at is in neither. This draws it anyway, compressed
 * toward the eye by {@link HorizonProjection} — every point on its own sight line, so a coastline
 * lies in the direction the coastline really lies and a locator two kilometres out leaves the
 * silhouette it really would. Only depth is lost.
 *
 * <p>Height matters for when it runs. At sea level the map would sit next to real vanilla terrain
 * it does not match, hill for hill, right in front of the player. From the sky band up there is
 * nothing to compare it against and everything to gain: the ground reads as a map, which is what
 * it is. So it fades in across the climb out of the surface band and is fully on by the orbital.
 */
public final class PlanetFloorRenderer {
	/** Below this the real terrain owns the view; the map would only argue with it. */
	private static final double FADE_START = WorldLevels.SURFACE_TOP;
	/** Fully on by the top of the sky band. */
	private static final double FADE_FULL = WorldLevels.SKY_TOP;

	/** In the End there is no shared vertical, so the planet is placed at a chosen depth. */
	private static final double END_VIRTUAL_DROP = 900.0;

	private PlanetFloorRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PlanetFloorRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.planetFloor) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (!PlanetClientState.INSTANCE.hasSeed()) return;

		boolean endDim = mc.world.getRegistryKey() == World.END;
		boolean overworld = mc.world.getRegistryKey() == World.OVERWORLD;
		if (!endDim && !overworld) return;

		Vec3d cam = ctx.camera().getPos();
		float alpha = 1f;
		if (overworld) {
			double into = cam.y - FADE_START;
			if (into <= 0) return;
			alpha = (float) MathHelper.clamp(into / (FADE_FULL - FADE_START), 0.0, 1.0);
		}

		long seed = PlanetClientState.INSTANCE.seed();
		// In the column the eye is where it is and the map keeps the world's own heights. In the
		// End nothing ties the two verticals together, so the field is built for an eye hanging a
		// fixed distance above sea level and then hung under the camera wherever it is.
		double eyeY = endDim ? PlanetMap.SEA_LEVEL + END_VIRTUAL_DROP : cam.y;

		double viewBlocks = mc.options.getClampedViewDistance() * 16.0;
		double clip = viewBlocks * 4.0;
		double reach = clip * HorizonProjection.CLIP_USE;
		double inner = innerRadius(endDim, viewBlocks, clip, eyeY, seed, cam);

		PlanetSurfaceMesh mesh = PlanetClientState.INSTANCE.mesh(cam.x, eyeY, cam.z, inner, reach);
		if (mesh.quads == 0) return;

		Matrix4f mat = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
		// The field is a rigid body between rebuilds: shift it by the parallax the ship has flown
		// since it was built. Vertically that applies only in the column, where the map is pinned to
		// real heights — in the End it hangs from the camera and must not drift with it.
		mat.translate((float) (mesh.originX - cam.x),
				endDim ? 0f : (float) (mesh.originY - cam.y),
				(float) (mesh.originZ - cam.z));

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull(); // skirts are single quads seen from either side
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float[] pos = mesh.positions;
		int[] colours = mesh.colours;
		for (int q = 0; q < mesh.quads; q++) {
			int argb = colours[q];
			float a = ((argb >>> 24) & 0xFF) / 255f * alpha;
			if (a <= 0.01f) continue;
			float r = ((argb >> 16) & 0xFF) / 255f;
			float g = ((argb >> 8) & 0xFF) / 255f;
			float b = (argb & 0xFF) / 255f;
			int i = q * 12;
			buf.vertex(mat, pos[i], pos[i + 1], pos[i + 2]).color(r, g, b, a);
			buf.vertex(mat, pos[i + 3], pos[i + 4], pos[i + 5]).color(r, g, b, a);
			buf.vertex(mat, pos[i + 6], pos[i + 7], pos[i + 8]).color(r, g, b, a);
			buf.vertex(mat, pos[i + 9], pos[i + 10], pos[i + 11]).color(r, g, b, a);
		}
		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/**
	 * Horizontal radius out to which the game is still drawing the real ground.
	 *
	 * <p>Two limits, whichever bites first: chunks are only sent within the view distance, and
	 * nothing is drawn past the far plane at all. From the band the ground is usually past the far
	 * plane already, which gives zero — the map owns the whole view down. From lower, or with a very
	 * long view distance, real terrain reaches further and the map starts outside it.
	 *
	 * <p>Whatever it comes to, the compression is the identity inside it, so the map's first cells
	 * are drawn at their true distance and continue the real ground rather than stepping off it.
	 */
	private static double innerRadius(boolean endDim, double viewBlocks, double clip,
									  double eyeY, long seed, Vec3d cam) {
		if (endDim) return 0.0; // no Overworld ground below the End to collide with
		double drop = eyeY - PlanetMap.height(seed, cam.x, cam.z);
		double byClip = clip * clip - drop * drop;
		if (byClip <= 0) return 0.0;
		return Math.min(viewBlocks, Math.sqrt(byClip));
	}
}
