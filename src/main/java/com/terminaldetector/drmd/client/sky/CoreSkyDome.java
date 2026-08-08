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
 * Hides the Overworld's sun and moon once the pilot is deep enough underground to be in the Core
 * band, rather than only tinting fog toward it ({@link LevelSky}).
 *
 * <p>{@code ClientWorldMixin.drmd$levelSky} tints {@code getSkyColor} — the flat colour behind
 * everything — but the sun and moon are separate textured quads vanilla draws regardless of that
 * colour, so under an open mantle ceiling they were still there: the ordinary sky showing through a
 * band that is meant to read as underground. There is no per-position hook into vanilla's own sky
 * geometry to cancel just those two quads without a raw Mixin into {@code WorldRenderer}'s internals
 * — this project has no decompiled Minecraft source and no live client to get that target right, and
 * a wrong Mixin target is a crash at startup, not a cosmetic miss.
 *
 * <p>Instead this paints a large sky-coloured box around the camera, enclosing it from every
 * direction (6DoF has no fixed "up" to skip one side of) — <em>with depth testing left on</em>,
 * unlike {@link OrbitalBeltSkyRenderer}'s own draws, so it only shows through where nothing nearer
 * (terrain, a cavern wall, an entity) has already claimed that pixel; it cannot paint over real
 * geometry the way a depth-ignoring quad would. Coloured from {@code world.getSkyColor} — already the
 * {@link LevelSky}-tinted value, the same one vanilla's own background used — so the box is invisible
 * as a shape and reads only as "no sun or moon here," not as a new object appearing.
 *
 * <p>Scoped to the lower column only (Industrial down through the Core). The upper Oblivion/End case
 * is the same problem but is handled inside {@link OrbitalBeltSkyRenderer} instead of here, because
 * that file already owns a competing set of sky visuals (the belt, the distant Oblivion object) up
 * there — a second independent renderer painting its own enclosing box over the same altitudes would
 * fight it for the same pixels instead of handing off.
 */
public final class CoreSkyDome {
	/** Half-extent of the enclosing box, comfortably past ordinary render/fog distance. */
	private static final float RADIUS = 256f;

	private CoreSkyDome() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(CoreSkyDome::render);
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.levelSky) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

		double y = ctx.camera().getPos().y;
		float alpha = lowerAlpha(y);
		if (alpha < 0.02f) return;

		float tickDelta = ctx.tickCounter().getTickDelta(false);
		Vec3d sky = mc.world.getSkyColor(ctx.camera().getPos(), tickDelta);

		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		box(buf, mat, RADIUS, (float) sky.x, (float) sky.y, (float) sky.z, alpha);
		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/** 0 at the Industrial plateau, 1 by the Abyss — the same underground threshold LevelSky's own fog leans into. */
	static float lowerAlpha(double y) {
		if (y >= WorldLevels.INDUSTRIAL_TOP) return 0f;
		if (y <= WorldLevels.ABYSS_TOP) return 1f;
		float t = (float) ((WorldLevels.INDUSTRIAL_TOP - y) / (WorldLevels.INDUSTRIAL_TOP - WorldLevels.ABYSS_TOP));
		return t * t * (3 - 2 * t);
	}

	private static void box(BufferBuilder buf, Matrix4f mat, float s, float r, float g, float b, float a) {
		quad(buf, mat, -s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s, r, g, b, a);
		quad(buf, mat, -s, -s, s, s, -s, s, s, s, s, -s, s, s, r, g, b, a);
		quad(buf, mat, -s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s, r, g, b, a);
		quad(buf, mat, -s, s, -s, s, s, -s, s, s, s, -s, s, s, r, g, b, a);
		quad(buf, mat, -s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s, r, g, b, a);
		quad(buf, mat, s, -s, -s, s, -s, s, s, s, s, s, s, -s, r, g, b, a);
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
