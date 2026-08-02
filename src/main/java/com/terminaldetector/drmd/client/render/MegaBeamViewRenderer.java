package com.terminaldetector.drmd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

/**
 * First-person Mega Beam ribbon — white core + cyan sheath while holding fire.
 */
public final class MegaBeamViewRenderer {
	private MegaBeamViewRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(MegaBeamViewRenderer::render);
		// Clear sticky flag if player stops holding without onStoppedUsing.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !client.player.isUsingItem()
					|| !(client.player.getActiveItem().getItem() instanceof DescentWeaponItem wep)
					|| !"mega_laser".equals(wep.getDef().behavior)) {
				DescentClientState.megaBeamActive = false;
			}
		});
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		if (!DescentClientState.megaBeamActive && !isHoldingMega(mc)) return;
		if (!mc.options.getPerspective().isFirstPerson()) return;
		if (!DescentConfig.weaponView) return;

		Vec3d cam = ctx.camera().getPos();
		Vec3d look = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw());
		if (DescentClientState.attitudeValid) {
			look = com.terminaldetector.drmd.client.flight.ShipAttitudeClient.get().forward();
		}
		Vec3d from = cam.add(look.multiply(0.55));
		Vec3d probe = from.add(look.multiply(96));
		var hit = mc.world.raycast(new RaycastContext(from, probe,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
		Vec3d to = hit.getType() == HitResult.Type.MISS ? probe : hit.getPos();

		Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.depthMask(false);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
		right = right.normalize();
		Vec3d up = right.crossProduct(look).normalize();

		// Cyan sheath then white core — character-height thickness in view.
		beamQuad(buf, mat, cam, from, to, right, up, 0.42f, 0.20f, 0.85f, 1f, 0.55f);
		beamQuad(buf, mat, cam, from, to, right, up, 0.16f, 1f, 1f, 1f, 0.92f);

		var built = buf.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static boolean isHoldingMega(MinecraftClient mc) {
		if (mc.player == null || !mc.player.isUsingItem()) return false;
		var item = mc.player.getActiveItem().getItem();
		return item instanceof DescentWeaponItem wep && "mega_laser".equals(wep.getDef().behavior);
	}

	private static void beamQuad(BufferBuilder buf, Matrix4f mat, Vec3d cam,
								 Vec3d from, Vec3d to, Vec3d right, Vec3d up,
								 float radius, float r, float g, float b, float a) {
		Vec3d rr = right.multiply(radius);
		Vec3d uu = up.multiply(radius * 0.85);
		quad(buf, mat, cam,
				from.add(rr).add(uu), from.subtract(rr).add(uu),
				to.subtract(rr).add(uu), to.add(rr).add(uu), r, g, b, a);
		quad(buf, mat, cam,
				from.add(rr).subtract(uu), to.add(rr).subtract(uu),
				to.subtract(rr).subtract(uu), from.subtract(rr).subtract(uu), r, g, b, a * 0.9f);
	}

	private static void quad(BufferBuilder buf, Matrix4f mat, Vec3d cam,
							 Vec3d a, Vec3d b, Vec3d c, Vec3d d,
							 float r, float g, float bl, float alpha) {
		vert(buf, mat, cam, a, r, g, bl, alpha);
		vert(buf, mat, cam, b, r, g, bl, alpha);
		vert(buf, mat, cam, c, r, g, bl, alpha);
		vert(buf, mat, cam, d, r, g, bl, alpha);
	}

	private static void vert(BufferBuilder buf, Matrix4f mat, Vec3d cam, Vec3d p,
							 float r, float g, float b, float a) {
		buf.vertex(mat, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
				.color(r, g, b, a);
	}
}
