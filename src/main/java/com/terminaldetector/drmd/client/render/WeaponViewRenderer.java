package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.workshop.ClusterModule;
import com.terminaldetector.drmd.workshop.ConstructionRegistry;
import com.terminaldetector.drmd.workshop.WeaponClusters;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * First-person Doom-style weapon view from construction clusters.
 * Port of d6_wepview.lua + d6_sck_bridge VElements pipeline.
 * Modules are drawn as colored boxes relative to the camera (no GMod models).
 */
public final class WeaponViewRenderer {
	private WeaponViewRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || mc.options.getPerspective().isFirstPerson() == false) return;
			if (!DescentClientState.enabled) return;
			if (mc.options.hudHidden) return;

			ItemStack stack = mc.player.getMainHandStack();
			if (!(stack.getItem() instanceof DescentWeaponItem wep)) return;

			List<ClusterModule> modules = ConstructionRegistry.getModules(wep.getDef().id);
			if (modules.isEmpty()) return;

			MatrixStack matrices = context.matrixStack();
			VertexConsumerProvider consumers = context.consumers();
			if (matrices == null || consumers == null) return;

			matrices.push();
			// Camera-relative: origin at eye, looking down -Z in view space via camera transform already applied.
			// WorldRenderEvents gives world space — convert eye offset manually.
			Vec3d cam = context.camera().getPos();
			float yaw = context.camera().getYaw();
			float pitch = context.camera().getPitch();
			float roll = DescentClientState.roll;

			Vec3d look = Vec3d.fromPolar(pitch, yaw);
			Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
			if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
			right = right.normalize();
			Vec3d up = right.crossProduct(look).normalize();
			double rollRad = Math.toRadians(roll);
			Vec3d rr = right.multiply(Math.cos(rollRad)).add(up.multiply(Math.sin(rollRad)));
			Vec3d uu = up.multiply(Math.cos(rollRad)).subtract(right.multiply(Math.sin(rollRad)));

			float time = (mc.player.age + context.tickCounter().getTickDelta(false)) / 20f;

			for (ClusterModule m : modules) {
				float fwd = m.posFwd / 16f;
				float rgt = m.posRgt / 16f;
				float upp = m.posUp / 16f;
				if (m.bobAmp > 0 && m.bobFreq > 0) {
					upp += (float) (Math.sin(time * m.bobFreq * Math.PI * 2) * m.bobAmp / 16f);
				}
				Vec3d pos = cam.add(look.multiply(fwd)).add(rr.multiply(rgt)).add(uu.multiply(upp));

				matrices.push();
				matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll + m.roll));
				if (m.spin != 0) {
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time * m.spin * 60f));
				}
				float s = 0.08f * m.scaleX;
				matrices.scale(s, s, s * (modelLength(m.model)));

				int[] rgb = colorForModel(m);
				int argb = (m.colorA << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
				drawBox(matrices, consumers, argb, context.camera().getPos() != null ? 15728880 : 15728880);
				matrices.pop();
			}
			matrices.pop();
		});
	}

	private static float modelLength(String model) {
		return switch (model) {
			case "nosegun" -> 2.2f;
			case "strider" -> 2.8f;
			case "gravy" -> 1.4f;
			default -> 1.8f; // barrel
		};
	}

	private static int[] colorForModel(ClusterModule m) {
		// Tint by cluster role, modulated by model type
		int[] base = WeaponClusters.COLORS[WeaponClusters.rank(m.cluster)];
		return switch (m.model) {
			case "gravy" -> new int[]{180, 200, 255};
			case "strider" -> new int[]{220, 160, 80};
			case "nosegun" -> new int[]{Math.min(255, base[0] + 40), base[1], base[2]};
			default -> base;
		};
	}

	private static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers, int argb, int light) {
		// Use debug/immediate filled quad via Minecraft's built-in debug renderer style
		var entry = matrices.peek();
		var vc = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getDebugFilledBox());
		float a = ((argb >> 24) & 255) / 255f;
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float x0 = -0.5f, y0 = -0.5f, z0 = -0.5f;
		float x1 = 0.5f, y1 = 0.5f, z1 = 0.5f;
		// 6 faces — compact
		quad(vc, entry, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a); // -Y
		quad(vc, entry, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a); // +Y
		quad(vc, entry, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a); // -Z
		quad(vc, entry, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a); // +Z
		quad(vc, entry, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a); // -X
		quad(vc, entry, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a); // +X
	}

	private static void quad(net.minecraft.client.render.VertexConsumer vc, MatrixStack.Entry entry,
							 float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3,
							 float r, float g, float b, float a) {
		vc.vertex(entry.getPositionMatrix(), x0, y0, z0).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x3, y3, z3).color(r, g, b, a);
	}
}
