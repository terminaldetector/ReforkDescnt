package com.terminaldetector.drmd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.sync.ClientSkyUfoMotionSync;
import com.terminaldetector.drmd.world.structure.StructureMotion;
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

import java.util.Set;

/**
 * Draws every Sky UFO currently reporting virtual-flight motion samples as one interpolated batch —
 * structured exactly like {@link com.terminaldetector.drmd.client.planet.PlanetFloorRenderer}: a
 * dedicated {@code WorldRenderEvents.AFTER_TRANSLUCENT} hook building its own camera-relative
 * {@code Matrix4f} and drawing through a raw {@link Tessellator} buffer, not a per-entity
 * {@code EntityRenderer}. That distinction matters here specifically: an {@code EntityRenderer}
 * receives a {@code MatrixStack} vanilla has already transformed through its own coarse
 * ({@code trackingTickInterval(2)}) interpolation, and fighting that from inside would be fragile —
 * building the transform fresh from {@link StructureMotion#interpolate} sidesteps it entirely.
 *
 * <p>Iterates {@link ClientSkyUfoMotionSync}'s own known ids rather than the client world's entity
 * list: a UFO only ever has a cached sample while it is actually broadcasting virtual-flight motion
 * (see {@code SkyUfoEntity.broadcastMotion}), so a live sample <em>is</em> the "draw it here" signal —
 * no separate entity lookup needed to ask whether it's virtual right now.
 */
public final class SkyUfoHullRenderer {
	private SkyUfoHullRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(SkyUfoHullRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		if (!DescentConfig.skyUfoVirtualHull) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;

		SkyUfoHullMesh mesh = SkyUfoHullMesh.get();
		if (mesh.quads == 0) return;

		Set<Integer> ids = ClientSkyUfoMotionSync.INSTANCE.entityIds();
		if (ids.isEmpty()) return;

		long localTick = mc.world.getTime();
		float tickDelta = ctx.tickCounter().getTickDelta(false);
		Vec3d cam = ctx.camera().getPos();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull(); // faces are already culled at bake time; this only guards winding
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		for (int id : ids) {
			StructureMotion.Sample s = ClientSkyUfoMotionSync.INSTANCE.sampleAt(id, localTick, tickDelta);
			if (s == null) continue;
			Matrix4f mat = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
			mat.translate((float) (s.x() - cam.x), (float) (s.y() - cam.y), (float) (s.z() - cam.z));
			// Sign/direction matches how SkyUfoEntity.setYaw(cruiseYaw) already orients the old
			// copper-beam cue — reasoned from that existing call, not verified against a live frame
			// (this sandbox cannot render one; flagged in this feature's plan as exactly this kind of
			// live-client-only unknown).
			mat.rotateY((float) -Math.toRadians(s.yaw()));
			emit(buf, mat, mesh);
		}

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void emit(BufferBuilder buf, Matrix4f mat, SkyUfoHullMesh mesh) {
		float[] pos = mesh.positions;
		int[] colours = mesh.colours;
		for (int q = 0; q < mesh.quads; q++) {
			int argb = colours[q];
			float a = ((argb >>> 24) & 0xFF) / 255f;
			float r = ((argb >> 16) & 0xFF) / 255f;
			float g = ((argb >> 8) & 0xFF) / 255f;
			float b = (argb & 0xFF) / 255f;
			int i = q * 12;
			buf.vertex(mat, pos[i], pos[i + 1], pos[i + 2]).color(r, g, b, a);
			buf.vertex(mat, pos[i + 3], pos[i + 4], pos[i + 5]).color(r, g, b, a);
			buf.vertex(mat, pos[i + 6], pos[i + 7], pos[i + 8]).color(r, g, b, a);
			buf.vertex(mat, pos[i + 9], pos[i + 10], pos[i + 11]).color(r, g, b, a);
		}
	}
}
