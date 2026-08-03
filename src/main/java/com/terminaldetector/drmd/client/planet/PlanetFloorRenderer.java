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
 * The planet, seen from the End.
 *
 * <p>The point of the band at the top of the column is that it is the <em>upper</em> world, not a
 * separate one — so the ground has to be under it. It cannot be drawn where it actually is: nine
 * hundred blocks down is past the projection's far plane, and the chunks are not on the client
 * anyway. So the surface is drawn <em>scaled toward the camera</em>: every point is pulled in along
 * the line from the eye to it, by one factor for the whole field.
 *
 * <p>That factor is the whole trick, and it is why this is a map rather than the decorative shelf it
 * replaces. A uniform scale about the camera moves no point off its own sight line, so the picture
 * on screen is exactly the picture the real planet would make — the same coastline in the same
 * direction, at the same angle below the horizon. Only the sense of distance is compressed. Fly
 * toward a bay you can see and you arrive at that bay.
 *
 * <p>The field starts where the real chunks stop, so the two never draw the same ground, and it is
 * pulled in far enough to sit below the band's own islands rather than through them.
 */
public final class PlanetFloorRenderer {
	/**
	 * How far below the eye the surface is drawn.
	 *
	 * <p>Has to clear the whole End band from anywhere inside it: the band is 144 blocks deep, so
	 * this leaves the floor a good margin under its lowest island however high the ship is.
	 */
	private static final double FLOOR_DROP = 208.0;

	/** In the End dimension there is no ground below to be consistent with, so the drop is chosen. */
	private static final double END_VIRTUAL_DROP = 900.0;

	/** Fade in over the last stretch of climb toward the seam. */
	private static final double FADE_BAND = 96.0;

	/** Share of the far plane the field is allowed to fill. */
	private static final double CLIP_USE = 0.72;

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
			// Only from the band, ramping up over the last of the climb into it.
			double into = cam.y - (WorldLevels.ORBITAL_TOP - FADE_BAND);
			if (into <= 0) return;
			alpha = (float) MathHelper.clamp(into / FADE_BAND, 0.0, 1.0);
		}

		long seed = PlanetClientState.INSTANCE.seed();
		// In the column the map's Y is the world's Y, which is what keeps the projection honest.
		// In the End there is no shared vertical, so sea level is placed at a chosen depth.
		double yShift = endDim
				? (cam.y - END_VIRTUAL_DROP) - PlanetMap.SEA_LEVEL
				: 0.0;
		double groundY = PlanetMap.height(seed, cam.x, cam.z) + yShift;
		double drop = cam.y - groundY;
		if (drop < FLOOR_DROP * 1.2) return; // too low for the compression to make sense

		double scale = FLOOR_DROP / drop;
		double viewBlocks = mc.options.getClampedViewDistance() * 16.0;
		double clip = viewBlocks * 4.0;
		double outer = clip * CLIP_USE / scale;
		double inner = innerRadius(endDim, viewBlocks, clip, drop);

		PlanetSurfaceMesh mesh = PlanetClientState.INSTANCE.mesh(cam.x, cam.z, inner, outer);
		if (mesh.quads == 0) return;

		Matrix4f mat = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
		// s = scale * (vertex + shift - camera): one uniform scale about the eye, so every bearing
		// survives it. JOML applies these to the vertex in reverse order, translate first.
		mat.scale((float) scale);
		mat.translate((float) (mesh.originX - cam.x),
				(float) (yShift - cam.y),
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
	 */
	private static double innerRadius(boolean endDim, double viewBlocks, double clip, double drop) {
		if (endDim) return 0.0; // no Overworld ground below the End to collide with
		double byClip = clip * clip - drop * drop;
		if (byClip <= 0) return 0.0;
		return Math.min(viewBlocks, Math.sqrt(byClip));
	}
}
