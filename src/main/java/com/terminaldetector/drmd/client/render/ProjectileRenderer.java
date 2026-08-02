package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Lightweight 3D projectile silhouettes (bolt / rocket / orb / drill) —
 * reuses debug filled box path like WeaponViewRenderer (no external meshes).
 */
public class ProjectileRenderer extends EntityRenderer<ProjectileEntity> {
	public ProjectileRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(ProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		Vec3d vel = entity.getVelocity();
		float bodyYaw = yaw;
		float bodyPitch = 0;
		if (vel.lengthSquared() > 1e-6) {
			bodyYaw = (float) (MathHelper.atan2(vel.x, vel.z) * (180F / Math.PI));
			bodyPitch = (float) (MathHelper.atan2(vel.y, Math.sqrt(vel.x * vel.x + vel.z * vel.z)) * (180F / Math.PI));
		}
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-bodyPitch));

		float s = 0.12f * entity.getVisualScale();
		int argb = 0xFF000000 | entity.getColorRgb();
		int mesh = entity.getMeshKind();
		switch (mesh) {
			case ProjectileEntity.MESH_ROCKET -> {
				matrices.push();
				matrices.scale(s * 0.7f, s * 0.7f, s * 2.4f);
				drawBox(matrices, consumers, argb);
				matrices.pop();
				matrices.push();
				matrices.translate(0, 0, -s * 1.4f);
				matrices.scale(s * 1.1f, s * 1.1f, s * 0.6f);
				drawBox(matrices, consumers, brighten(argb));
				matrices.pop();
			}
			case ProjectileEntity.MESH_ORB -> {
				matrices.scale(s * 1.6f, s * 1.6f, s * 1.6f);
				drawBox(matrices, consumers, argb);
			}
			case ProjectileEntity.MESH_DRILL -> {
				matrices.push();
				matrices.scale(s * 0.55f, s * 0.55f, s * 2.8f);
				drawBox(matrices, consumers, argb);
				matrices.pop();
				matrices.push();
				matrices.translate(0, 0, s * 1.2f);
				matrices.scale(s * 0.95f, s * 0.95f, s * 0.7f);
				drawBox(matrices, consumers, 0xFFFFAA33);
				matrices.pop();
			}
			case ProjectileEntity.MESH_MINE -> {
				// Faceted shell plus a pulsing core; the pulse rate reads as "armed".
				matrices.push();
				matrices.scale(s * 1.5f, s * 1.5f, s * 1.5f);
				drawBox(matrices, consumers, argb);
				matrices.pop();
				float pulse = 0.55f + 0.45f * MathHelper.sin((entity.age + tickDelta) * 0.45f);
				matrices.push();
				matrices.scale(s * 2.1f * pulse, s * 0.35f, s * 0.35f);
				drawBox(matrices, consumers, brighten(argb));
				matrices.pop();
				matrices.push();
				matrices.scale(s * 0.35f, s * 2.1f * pulse, s * 0.35f);
				drawBox(matrices, consumers, brighten(argb));
				matrices.pop();
				matrices.push();
				matrices.scale(s * 0.35f, s * 0.35f, s * 2.1f * pulse);
				drawBox(matrices, consumers, brighten(argb));
				matrices.pop();
			}
			default -> {
				// Bolt: a lance body with a brighter leading tip so travel direction reads.
				matrices.push();
				matrices.scale(s * 0.45f, s * 0.45f, s * 1.6f);
				drawBox(matrices, consumers, argb);
				matrices.pop();
				matrices.push();
				matrices.translate(0, 0, -s * 0.9f);
				matrices.scale(s * 0.62f, s * 0.62f, s * 0.5f);
				drawBox(matrices, consumers, brighten(argb));
				matrices.pop();
			}
		}
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	private static int brighten(int argb) {
		int a = (argb >> 24) & 255;
		int r = Math.min(255, ((argb >> 16) & 255) + 40);
		int g = Math.min(255, ((argb >> 8) & 255) + 40);
		int b = Math.min(255, (argb & 255) + 40);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers, int argb) {
		var entry = matrices.peek();
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getDebugFilledBox());
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

	private static void quad(VertexConsumer vc, MatrixStack.Entry entry,
							 float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3,
							 float r, float g, float b, float a) {
		vc.vertex(entry.getPositionMatrix(), x0, y0, z0).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, a);
		vc.vertex(entry.getPositionMatrix(), x3, y3, z3).color(r, g, b, a);
	}

	@Override
	public Identifier getTexture(ProjectileEntity entity) {
		return Identifier.of("minecraft", "textures/misc/white.png");
	}
}
