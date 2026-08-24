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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Hides vanilla's sun/moon once the pilot is deep in the End band — the Core-band counterpart to
 * {@link CoreSkyDome} at the other end of the world column. The decorative orbital vista this file
 * used to paint here (a planet sphere, rings, a Starlink-style dash train, a distant Oblivion body)
 * was removed at the user's request: alpha-blended custom skybox paint read as clutter against the
 * game's actual voxel world, not a real place. What is real — the Klondike islands, the lunar base,
 * the orbit-junk macro structures — is generated as ordinary blocks elsewhere (see
 * {@code DescentSession}) and needs no help from this file to be visible.
 */
public final class OrbitalBeltSkyRenderer {
	private OrbitalBeltSkyRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(OrbitalBeltSkyRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.levelSky) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		float tickDelta = ctx.tickCounter().getTickDelta(false);
		double y = ctx.camera().getPos().y;
		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		paintOblivionEnvelope(ctx, mat, y, tickDelta);
	}

	/**
	 * Once the pilot is actually inside the End band, there's nothing else left to hide vanilla's sun
	 * and moon — it needs a skybox of its own. A large sky-coloured box enclosing the camera,
	 * <em>depth-tested</em> so it only shows through open sky and never paints over an island the
	 * pilot is flying past.
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
