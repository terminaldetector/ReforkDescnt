package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.workshop.ClusterModule;
import com.terminaldetector.drmd.workshop.ConstructionRegistry;
import com.terminaldetector.drmd.workshop.WeaponClusters;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * First-person weapon view for existing DRMD guns.
 * Modes: cockpit cluster 3D meshes, held item 3D model, or off (vanilla hand).
 */
public final class WeaponViewRenderer {
	private WeaponViewRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
			if (mc.options.hudHidden) return;
			if (DescentClientState.weaponViewMode == WeaponViewMode.VANILLA) return;
			// Cockpit overlays are for 6DoF or when explicitly on ITEM_3D with a DRMD gun.
			ItemStack stack = mc.player.getMainHandStack();
			if (!(stack.getItem() instanceof DescentWeaponItem wep)) return;
			if (!DescentClientState.enabled && DescentClientState.weaponViewMode != WeaponViewMode.ITEM_3D) return;

			MatrixStack matrices = context.matrixStack();
			VertexConsumerProvider consumers = context.consumers();
			if (matrices == null || consumers == null) return;

			ShipBasis basis = shipBasis(context);
			float time = (mc.player.age + context.tickCounter().getTickDelta(false)) / 20f;
			float kick = DescentClientState.weaponUseHeld ? 0.04f : 0f;

			matrices.push();
			if (DescentClientState.weaponViewMode == WeaponViewMode.ITEM_3D) {
				renderItemModel(mc, matrices, consumers, stack, basis, kick);
			} else {
				renderCockpitClusters(matrices, consumers, wep.getDef().id, basis, time, kick);
			}
			matrices.pop();
		});
	}

	private static void renderItemModel(MinecraftClient mc, MatrixStack matrices,
										VertexConsumerProvider consumers, ItemStack stack,
										ShipBasis basis, float kick) {
		Vec3d cam = basis.cam;
		// Doom-style lower-right placement in ship space
		float fwd = 0.55f - kick;
		float rgt = 0.28f;
		float upp = -0.22f;
		Vec3d pos = cam.add(basis.look.multiply(fwd)).add(basis.right.multiply(rgt)).add(basis.up.multiply(upp));

		matrices.push();
		matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
		applyBasis(matrices, basis);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8f));
		matrices.scale(0.85f, 0.85f, 0.85f);
		int light = 0x00F000F0;
		mc.getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, light,
				OverlayTexture.DEFAULT_UV, matrices, consumers, mc.world, 0);
		matrices.pop();
	}

	private static void renderCockpitClusters(MatrixStack matrices, VertexConsumerProvider consumers,
											 String weaponId, ShipBasis basis, float time, float kick) {
		List<ClusterModule> modules = ConstructionRegistry.getModules(weaponId);
		if (modules.isEmpty()) return;
		Vec3d cam = basis.cam;

		for (ClusterModule m : modules) {
			float fwd = m.posFwd / 16f - kick;
			float rgt = m.posRgt / 16f;
			float upp = m.posUp / 16f;
			if (m.bobAmp > 0 && m.bobFreq > 0) {
				upp += (float) (Math.sin(time * m.bobFreq * Math.PI * 2) * m.bobAmp / 16f);
			}
			Vec3d pos = cam.add(basis.look.multiply(fwd)).add(basis.right.multiply(rgt)).add(basis.up.multiply(upp));

			matrices.push();
			matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
			applyBasis(matrices, basis);
			if (Math.abs(m.pitch) > 0.01f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(m.pitch));
			if (Math.abs(m.yaw) > 0.01f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(m.yaw));
			if (Math.abs(m.roll) > 0.01f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(m.roll));
			if (m.spin != 0) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time * m.spin * 60f));
			}
			float s = 0.09f * Math.max(0.2f, m.scaleX);
			matrices.scale(s * m.scaleX, s * m.scaleY, s * m.scaleZ * modelLength(m.model));

			int[] rgb = colorForModel(m);
			int argb = (m.colorA << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
			drawModel3d(matrices, consumers, m.model, argb);
			matrices.pop();
		}
	}

	/** Multi-part meshes — not a single flat plane. */
	private static void drawModel3d(MatrixStack matrices, VertexConsumerProvider consumers, String model, int argb) {
		switch (model) {
			case "gravy" -> drawGravyClaw(matrices, consumers, argb);
			case "nosegun" -> drawNoseGun(matrices, consumers, argb);
			case "strider" -> drawStrider(matrices, consumers, argb);
			case "core" -> drawCore(matrices, consumers, argb);
			default -> drawBarrel(matrices, consumers, argb);
		}
	}

	private static void drawBarrel(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		// Receiver
		matrices.push();
		matrices.scale(1.1f, 1.0f, 0.7f);
		matrices.translate(0, 0, 0.15f);
		drawBox(matrices, consumers, darken(argb, 0.75f));
		matrices.pop();
		// Main tube
		matrices.push();
		matrices.scale(0.55f, 0.55f, 1.6f);
		matrices.translate(0, 0.1f, -0.15f);
		drawBox(matrices, consumers, argb);
		matrices.pop();
		// Muzzle tip
		matrices.push();
		matrices.scale(0.4f, 0.4f, 0.35f);
		matrices.translate(0, 0.1f, -1.55f);
		drawBox(matrices, consumers, lighten(argb, 1.15f));
		matrices.pop();
		// Under-rail
		matrices.push();
		matrices.scale(0.35f, 0.25f, 0.9f);
		matrices.translate(0, -0.9f, 0.1f);
		drawBox(matrices, consumers, darken(argb, 0.55f));
		matrices.pop();
	}

	private static void drawNoseGun(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		matrices.push();
		matrices.scale(1.3f, 1.1f, 0.9f);
		drawBox(matrices, consumers, darken(argb, 0.8f));
		matrices.pop();
		matrices.push();
		matrices.translate(0, 0, -0.65f);
		matrices.scale(0.85f, 0.85f, 1.2f);
		drawBox(matrices, consumers, argb);
		matrices.pop();
		matrices.push();
		matrices.translate(0, 0, -1.35f);
		matrices.scale(0.5f, 0.5f, 0.55f);
		drawBox(matrices, consumers, lighten(argb, 1.2f));
		matrices.pop();
		// Side intakes
		for (float side : new float[]{-0.7f, 0.7f}) {
			matrices.push();
			matrices.translate(side, 0.1f, -0.2f);
			matrices.scale(0.35f, 0.45f, 0.8f);
			drawBox(matrices, consumers, darken(argb, 0.6f));
			matrices.pop();
		}
	}

	private static void drawStrider(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		matrices.push();
		matrices.scale(1.4f, 0.7f, 1.8f);
		drawBox(matrices, consumers, darken(argb, 0.7f));
		matrices.pop();
		matrices.push();
		matrices.translate(0, 0.35f, -0.4f);
		matrices.scale(0.7f, 0.5f, 1.4f);
		drawBox(matrices, consumers, argb);
		matrices.pop();
		for (float x : new float[]{-0.55f, 0.55f}) {
			matrices.push();
			matrices.translate(x, -0.35f, 0.2f);
			matrices.scale(0.25f, 0.9f, 0.25f);
			drawBox(matrices, consumers, darken(argb, 0.5f));
			matrices.pop();
		}
	}

	private static void drawCore(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		drawBox(matrices, consumers, argb);
		matrices.push();
		matrices.scale(0.55f, 0.55f, 0.55f);
		matrices.translate(0, 0.9f, 0);
		drawBox(matrices, consumers, lighten(argb, 1.25f));
		matrices.pop();
	}

	private static void drawGravyClaw(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		drawBox(matrices, consumers, darken(argb, 0.85f));
		matrices.push();
		matrices.translate(-0.75f, 0.35f, -0.55f);
		matrices.scale(0.3f, 0.35f, 1.5f);
		drawBox(matrices, consumers, argb);
		matrices.pop();
		matrices.push();
		matrices.translate(0.75f, 0.35f, -0.55f);
		matrices.scale(0.3f, 0.35f, 1.5f);
		drawBox(matrices, consumers, argb);
		matrices.pop();
		matrices.push();
		matrices.translate(0f, -0.55f, -0.35f);
		matrices.scale(0.35f, 0.3f, 1.2f);
		drawBox(matrices, consumers, lighten(argb, 1.1f));
		matrices.pop();
		// Emitter orb
		matrices.push();
		matrices.translate(0, 0.2f, 0.55f);
		matrices.scale(0.45f, 0.45f, 0.45f);
		drawBox(matrices, consumers, 0xFFB4DCFF);
		matrices.pop();
	}

	private static float modelLength(String model) {
		return switch (model) {
			case "nosegun" -> 1.15f;
			case "strider" -> 1.25f;
			case "gravy" -> 1.0f;
			case "core" -> 0.9f;
			default -> 1.0f;
		};
	}

	private static int[] colorForModel(ClusterModule m) {
		int[] base = WeaponClusters.COLORS[WeaponClusters.rank(m.cluster)];
		return switch (m.model) {
			case "gravy" -> new int[]{180, 200, 255};
			case "strider" -> new int[]{220, 160, 80};
			case "nosegun" -> new int[]{Math.min(255, base[0] + 40), base[1], base[2]};
			case "core" -> new int[]{120, 220, 180};
			default -> base;
		};
	}

	private static int darken(int argb, float k) {
		int a = (argb >>> 24) & 255;
		int r = (int) (((argb >>> 16) & 255) * k);
		int g = (int) (((argb >>> 8) & 255) * k);
		int b = (int) ((argb & 255) * k);
		return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int lighten(int argb, float k) {
		return darken(argb, k);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		var entry = matrices.peek();
		var vc = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getDebugFilledBox());
		float a = ((argb >> 24) & 255) / 255f;
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float x0 = -0.5f, y0 = -0.5f, z0 = -0.5f;
		float x1 = 0.5f, y1 = 0.5f, z1 = 0.5f;
		quad(vc, entry, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
		quad(vc, entry, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		quad(vc, entry, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
		quad(vc, entry, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		quad(vc, entry, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
		quad(vc, entry, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
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

	private static void applyBasis(MatrixStack matrices, ShipBasis basis) {
		org.joml.Matrix4f m = new org.joml.Matrix4f(
				(float) basis.right.x, (float) basis.right.y, (float) basis.right.z, 0f,
				(float) basis.up.x, (float) basis.up.y, (float) basis.up.z, 0f,
				(float) -basis.look.x, (float) -basis.look.y, (float) -basis.look.z, 0f,
				0f, 0f, 0f, 1f);
		var entry = matrices.peek();
		entry.getPositionMatrix().mul(m);
		entry.getNormalMatrix().mul(m.normal(new org.joml.Matrix3f()));
	}

	private static ShipBasis shipBasis(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		Vec3d cam = context.camera().getPos();
		Vec3d look;
		Vec3d right;
		Vec3d up;
		if (DescentClientState.attitudeValid) {
			var att = com.terminaldetector.drmd.client.flight.ShipAttitudeClient.get();
			look = att.forward();
			up = att.up();
			right = att.right();
		} else {
			float yaw = context.camera().getYaw();
			float pitch = context.camera().getPitch();
			look = Vec3d.fromPolar(pitch, yaw);
			right = look.crossProduct(new Vec3d(0, 1, 0));
			if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
			right = right.normalize();
			up = right.crossProduct(look).normalize();
		}
		return new ShipBasis(cam, look, right, up);
	}

	private record ShipBasis(Vec3d cam, Vec3d look, Vec3d right, Vec3d up) {}
}
