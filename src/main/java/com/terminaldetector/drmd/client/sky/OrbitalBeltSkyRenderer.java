package com.terminaldetector.drmd.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

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
		SkyEnvelopeShader.register();
		// DescentClient registers this before PlanetFloor: the sky is background for distant
		// terrain too, whose colour intentionally does not populate the world depth buffer.
		WorldRenderEvents.BEFORE_ENTITIES.register(OrbitalBeltSkyRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
        // DH composites colour at AFTER_SETUP without populating Minecraft's depth. Its colour
        // must not be covered by a later far-depth envelope. The ordinary path keeps its phase.
        if(com.terminaldetector.drmd.world.compat.DistantHorizonsCompat.isPresent())return;
		if (!DescentConfig.hybridRender || !DescentConfig.levelSky) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		float tickDelta = ctx.tickCounter().getTickDelta(false);
		double y = ctx.camera().getPos().y;
		// BEFORE_ENTITIES has the terrain view; its object matrix stack is not available yet.
		Matrix4f mat = new Matrix4f(ctx.positionMatrix());
		paintOblivionEnvelope(ctx.camera(), mat, y, tickDelta);
	}

	/**
	 * Once the pilot is actually inside the End band, there's nothing else left to hide vanilla's sun
	 * and moon — it needs a skybox of its own. Its existing enclosing cube is rasterized at the
	 * far depth, independent of the terrain clipping distance. LEQUAL keeps already drawn real
	 * terrain in front; later distant terrain, entities and cockpit compose over this background.
	 */
    /** Actual caller hook is AFTER native sky and BEFORE setupTerrain's Fabric AFTER_SETUP/DH pass.
     * Same current-view target/matrices; no framebuffer/depth clear, copy or dependency API call. */
    public static void beforeExternalLods(net.minecraft.client.render.RenderTickCounter counter,net.minecraft.client.render.Camera camera,Matrix4f position) {
        if(!com.terminaldetector.drmd.world.compat.DistantHorizonsCompat.isPresent()
            ||!DescentConfig.hybridRender||!DescentConfig.levelSky)return;
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||mc.world.getRegistryKey()!=net.minecraft.world.World.OVERWORLD)return;
        paintOblivionEnvelope(camera,new Matrix4f(position),camera.getPos().y,counter.getTickDelta(false));
    }

	private static void paintOblivionEnvelope(net.minecraft.client.render.Camera camera, Matrix4f mat, double y, float tickDelta) {
		float envelope = envelopeAlpha(y);
		if (envelope < 0.02f || SkyEnvelopeShader.program() == null) return;
		Vec3d sky = MinecraftClient.getInstance().world.getSkyColor(camera.getPos(), tickDelta);

		boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
				blend = GL11.glIsEnabled(GL11.GL_BLEND), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		int function = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
				srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		var previous = RenderSystem.getShader();
		try {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.setShader(SkyEnvelopeShader::program);
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

		} finally {
			RenderSystem.depthMask(write);
			RenderSystem.depthFunc(function);
			if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
			if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
			RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
			if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
			RenderSystem.setShader(() -> previous);
		}
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
