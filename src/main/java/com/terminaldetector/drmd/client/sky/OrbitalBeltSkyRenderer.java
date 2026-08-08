package com.terminaldetector.drmd.client.sky;

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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Spark orbital vista in the skybox.
 *
 * <ul>
 *   <li><b>Altitude</b> — full planet + dark ring + neon-green halo (Orbit layer).</li>
 *   <li><b>Night on surface</b> — Starlink-style: thin bright train of the same ring + distant
 *       Oblivion object, visible against the dark sky without climbing.</li>
 * </ul>
 */
public final class OrbitalBeltSkyRenderer {
	private static boolean warned;

	private OrbitalBeltSkyRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(OrbitalBeltSkyRenderer::render);
	}

	public static void clearWarn() {
		warned = false;
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.levelSky) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		float tickDelta = ctx.tickCounter().getTickDelta(false);
		double y = ctx.camera().getPos().y;
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		// Independent of orbitalBeltSky below: this hides the wrong sun/moon, it isn't one of the
		// decorative extras that flag turns off.
		paintOblivionEnvelope(ctx, mat, y, tickDelta);

		if (!DescentConfig.orbitalBeltSky) return;
		float alt = altitudeAlpha(y);
		float night = nightFactor(mc.world, tickDelta);
		// Surface night: Starlink-thin visibility; altitude: full Spark vista.
		float starlink = night * MathHelper.clamp(
				1f - (float) ((y - WorldLevels.INDUSTRIAL_TOP) / (WorldLevels.SURFACE_TOP - WorldLevels.INDUSTRIAL_TOP + 40)),
				0.35f, 1f);
		if (y > WorldLevels.SURFACE_TOP - 20) starlink *= 0.35f; // altitude mode owns the bright ring
		float alpha = Math.max(alt, starlink * 0.65f);
		if (alpha < 0.02f) return;

		if (!warned && (alt > 0.4f || (night > 0.7f && y < WorldLevels.SURFACE_TOP))) {
			warned = true;
			mc.player.sendMessage(net.minecraft.text.Text.literal(
					night > 0.5f && alt < 0.3f
							? "§a◉ STARLINK RING §7+ §dOblivion §7visible in night skybox."
							: "§a◉ ORBIT RING §7— Spark vista · Oblivion above."), false);
		}

		double spin = (mc.world.getTime() + tickDelta) * 0.0009;
		boolean starlinkMode = alt < 0.35f && starlink > 0.15f;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		float cx = 0f;
		float cy = -95f;
		float cz = -40f;
		float planetR = 88f;

		if (!starlinkMode) {
			paintPlanet(buf, mat, cx, cy, cz, planetR, alpha, spin);
			paintRing(buf, mat, cx, cy, cz, planetR * 1.22f, planetR * 1.34f, 3.2f, spin,
					0.04f, 0.05f, 0.07f, 0.78f * alpha, 48);
			paintRing(buf, mat, cx, cy, cz, planetR * 1.36f, planetR * 1.48f, 1.6f, spin,
					0.20f, 1.0f, 0.28f, 0.72f * alpha, 64);
			paintRing(buf, mat, cx, cy, cz, planetR * 1.48f, planetR * 1.58f, 0.8f, spin,
					0.35f, 1.0f, 0.45f, 0.28f * alpha, 48);
		} else {
			// Starlink train — segmented bright dashes on a thin orbital arc across the night sky.
			paintStarlinkTrain(buf, mat, spin, starlink);
		}

		// Oblivion — distant skybox object (always at night; stronger high up).
		float oblA = MathHelper.clamp(night * 0.85f + alt * 0.55f, 0f, 1f) * alpha;
		if (oblA > 0.04f) {
			paintOblivion(buf, mat, spin, oblA, alt > 0.3f);
		}

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/**
	 * Once the pilot is actually inside the End band, the small distant "Oblivion object" above isn't
	 * enough to hide vanilla's sun and moon — it's a landmark meant to be seen from outside the band,
	 * not a skybox. Same fix as {@link CoreSkyDome} for the opposite end of the column: a large
	 * sky-coloured box enclosing the camera, <em>depth-tested</em> (unlike this file's other draws
	 * above) so it only shows through open sky and never paints over an island the pilot is flying
	 * past. Kept in this file rather than as its own class, unlike the Core case, because it has to
	 * hand off with the belt/Oblivion-object visuals painted just above rather than fight them for the
	 * same altitudes — one method deciding both is what keeps that handoff automatic; two independently
	 * gated renderers would each need to know about the other's alpha to avoid overpainting it.
	 */
	private static void paintOblivionEnvelope(WorldRenderContext ctx, Matrix4f mat, double y, float tickDelta) {
		float envelope = envelopeAlpha(y);
		if (envelope < 0.02f) return;
		Vec3d sky = MinecraftClient.getInstance().world.getSkyColor(ctx.camera().getPos(), tickDelta);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float r = (float) sky.x, g = (float) sky.y, b = (float) sky.z;
		float s = 256f;
		quad(buf, mat, -s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s, r, g, b, envelope);
		quad(buf, mat, -s, -s, s, s, -s, s, s, s, s, -s, s, s, r, g, b, envelope);
		quad(buf, mat, -s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s, r, g, b, envelope);
		quad(buf, mat, -s, s, -s, s, s, -s, s, s, s, -s, s, s, r, g, b, envelope);
		quad(buf, mat, -s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s, r, g, b, envelope);
		quad(buf, mat, s, -s, -s, s, -s, s, s, s, s, s, s, -s, r, g, b, envelope);
		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/** 0 at the Orbital ceiling, 1 a hundred blocks into the End band. */
	static float envelopeAlpha(double y) {
		if (y <= WorldLevels.ORBITAL_TOP) return 0f;
		double top = WorldLevels.ORBITAL_TOP + 100;
		if (y >= top) return 1f;
		float t = (float) ((y - WorldLevels.ORBITAL_TOP) / (top - WorldLevels.ORBITAL_TOP));
		return t * t * (3 - 2 * t);
	}

	/** 0 noon → 1 deep night (Minecraft sky angle). */
	static float nightFactor(ClientWorld world, float tickDelta) {
		float sky = world.getSkyAngle(tickDelta);
		float dayness = MathHelper.cos(sky * MathHelper.TAU);
		return MathHelper.clamp((-dayness + 0.12f) / 0.9f, 0f, 1f);
	}

	/** Starlink-style train of bright green segments along a high sky arc. */
	private static void paintStarlinkTrain(BufferBuilder buf, Matrix4f mat, double spin, float a) {
		int segs = 36;
		float radius = 180f;
		float elev = 55f; // degrees above horizon in sky space
		double tilt = Math.toRadians(elev);
		for (int i = 0; i < segs; i++) {
			// Skip every other → broken train like Starlink string of pearls
			if ((i & 1) == 0) continue;
			double a0 = spin * 2.2 + Math.PI * 2 * i / segs;
			double a1 = spin * 2.2 + Math.PI * 2 * (i + 0.55) / segs;
			float x0 = (float) (Math.cos(a0) * radius);
			float z0 = (float) (Math.sin(a0) * Math.cos(tilt) * radius);
			float y0 = (float) (Math.sin(tilt) * radius * 0.55 + Math.sin(a0) * 8);
			float x1 = (float) (Math.cos(a1) * radius);
			float z1 = (float) (Math.sin(a1) * Math.cos(tilt) * radius);
			float y1 = (float) (Math.sin(tilt) * radius * 0.55 + Math.sin(a1) * 8);
			float s = 1.35f;
			quad(buf, mat,
					x0 - s, y0, z0 - s, x0 + s, y0, z0 + s,
					x1 + s, y1, z1 + s, x1 - s, y1, z1 - s,
					0.45f, 1f, 0.55f, 0.75f * a);
			// Soft glow
			float g = 2.4f;
			quad(buf, mat,
					x0 - g, y0, z0 - g, x0 + g, y0, z0 + g,
					x1 + g, y1, z1 + g, x1 - g, y1, z1 - g,
					0.2f, 1f, 0.35f, 0.22f * a);
		}
	}

	/**
	 * Distant Oblivion mass — dark violet body + magenta rim, parked in the upper sky.
	 * Reads as the End/Oblivion object from the surface at night and grows with altitude.
	 */
	private static void paintOblivion(BufferBuilder buf, Matrix4f mat, double spin, float a, boolean near) {
		float ox = (float) (Math.cos(spin * 0.35 + 1.1) * (near ? 140 : 200));
		float oy = near ? 70f : 95f;
		float oz = (float) (Math.sin(spin * 0.35 + 1.1) * (near ? 140 : 200));
		float r = near ? 18f : 11f;
		int bands = near ? 8 : 6;
		for (int lat = 0; lat < bands; lat++) {
			double p0 = Math.PI * (-0.5 + (double) lat / bands);
			double p1 = Math.PI * (-0.5 + (double) (lat + 1) / bands);
			float y0 = (float) (Math.sin(p0) * r);
			float y1 = (float) (Math.sin(p1) * r);
			float rr0 = (float) (Math.cos(p0) * r);
			float rr1 = (float) (Math.cos(p1) * r);
			for (int lon = 0; lon < 10; lon++) {
				double b0 = Math.PI * 2 * lon / 10;
				double b1 = Math.PI * 2 * (lon + 1) / 10;
				float shade = 0.4f + 0.6f * (lat / (float) bands);
				quad(buf, mat,
						ox + (float) (Math.cos(b0) * rr0), oy + y0, oz + (float) (Math.sin(b0) * rr0),
						ox + (float) (Math.cos(b1) * rr0), oy + y0, oz + (float) (Math.sin(b1) * rr0),
						ox + (float) (Math.cos(b1) * rr1), oy + y1, oz + (float) (Math.sin(b1) * rr1),
						ox + (float) (Math.cos(b0) * rr1), oy + y1, oz + (float) (Math.sin(b0) * rr1),
						0.35f * shade, 0.08f * shade, 0.55f * shade, 0.88f * a);
			}
		}
		// Magenta corona
		float cr = r * 1.25f;
		paintRing(buf, mat, ox, oy, oz, cr * 0.9f, cr * 1.15f, near ? 1.2f : 0.7f, spin * 1.7,
				0.85f, 0.25f, 1f, 0.45f * a, 24);
	}

	private static void paintPlanet(BufferBuilder buf, Matrix4f mat,
									float cx, float cy, float cz, float r,
									float alpha, double spin) {
		int latBands = 12;
		int lonBands = 20;
		for (int lat = 0; lat < latBands; lat++) {
			double a0 = Math.PI * (-0.5 + (double) lat / latBands);
			double a1 = Math.PI * (-0.5 + (double) (lat + 1) / latBands);
			float y0 = (float) (Math.sin(a0) * r);
			float y1 = (float) (Math.sin(a1) * r);
			float rr0 = (float) (Math.cos(a0) * r);
			float rr1 = (float) (Math.cos(a1) * r);
			for (int lon = 0; lon < lonBands; lon++) {
				double b0 = spin + Math.PI * 2 * lon / lonBands;
				double b1 = spin + Math.PI * 2 * (lon + 1) / lonBands;
				float x00 = (float) (Math.cos(b0) * rr0);
				float z00 = (float) (Math.sin(b0) * rr0);
				float x01 = (float) (Math.cos(b1) * rr0);
				float z01 = (float) (Math.sin(b1) * rr0);
				float x10 = (float) (Math.cos(b0) * rr1);
				float z10 = (float) (Math.sin(b0) * rr1);
				float x11 = (float) (Math.cos(b1) * rr1);
				float z11 = (float) (Math.sin(b1) * rr1);
				float t = (lat + 0.5f) / latBands;
				float shade = 0.55f + 0.45f * (float) Math.sin(b0 * 3 + lat);
				float pr = 0.08f * shade;
				float pg = (0.22f + 0.18f * t) * shade;
				float pb = (0.38f + 0.25f * (1f - t)) * shade;
				quad(buf, mat,
						cx + x00, cy + y0, cz + z00,
						cx + x01, cy + y0, cz + z01,
						cx + x11, cy + y1, cz + z11,
						cx + x10, cy + y1, cz + z10,
						pr, pg, pb, 0.92f * alpha);
			}
		}
	}

	private static void paintRing(BufferBuilder buf, Matrix4f mat,
								  float cx, float cy, float cz,
								  float rInner, float rOuter, float halfH,
								  double spin,
								  float r, float g, float b, float a, int segs) {
		for (int i = 0; i < segs; i++) {
			double a0 = spin + Math.PI * 2 * i / segs;
			double a1 = spin + Math.PI * 2 * (i + 1) / segs;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			float y0 = cy - halfH;
			float y1 = cy + halfH;
			quad(buf, mat,
					cx + c0 * rInner, y1, cz + s0 * rInner,
					cx + c1 * rInner, y1, cz + s1 * rInner,
					cx + c1 * rOuter, y1, cz + s1 * rOuter,
					cx + c0 * rOuter, y1, cz + s0 * rOuter,
					r, g, b, a);
			quad(buf, mat,
					cx + c0 * rOuter, y0, cz + s0 * rOuter,
					cx + c1 * rOuter, y0, cz + s1 * rOuter,
					cx + c1 * rOuter, y1, cz + s1 * rOuter,
					cx + c0 * rOuter, y1, cz + s0 * rOuter,
					r * 0.85f, g * 0.85f, b * 0.85f, a * 0.9f);
		}
	}

	static float altitudeAlpha(double y) {
		if (y < WorldLevels.SURFACE_TOP - 60) return 0f;
		if (y >= WorldLevels.SKY_TOP) return 1f;
		float t = (float) ((y - (WorldLevels.SURFACE_TOP - 60))
				/ (WorldLevels.SKY_TOP - (WorldLevels.SURFACE_TOP - 60)));
		return MathHelper.clamp(t * t * (3 - 2 * t), 0f, 1f);
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
