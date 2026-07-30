package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Airborne XCOM-style saucer — procedural disc + dome for LLOD-scale presence.
 */
public class SkyUfoRenderer extends EntityRenderer<SkyUfoEntity> {
	private static final Identifier TEXTURE = Identifier.of(com.terminaldetector.drmd.DescentMod.MOD_ID, "textures/entity/sky_ufo.png");

	public SkyUfoRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 3.5f;
	}

	@Override
	public void render(SkyUfoEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		// Real copper hull is in the world — keep only a soft under-beam cue
		if (entity.isMaterialized()) {
			VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
			matrices.push();
			matrices.translate(0, -2.2, 0);
			cube(buf, matrices.peek().getPositionMatrix(), 0.35f, 1.4f, 0.35f, light, 255, 200, 80, 120);
			matrices.pop();
			return;
		}
		VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		float pulse = 0.92f + 0.08f * MathHelper.sin((entity.age + tickDelta) * 0.2f);

		matrices.push();
		matrices.scale(pulse, pulse, pulse);
		cube(buf, matrices.peek().getPositionMatrix(), 3.2f, 0.35f, 3.2f, light, 80, 220, 180, 210);
		matrices.translate(0, 0.7, 0);
		cube(buf, matrices.peek().getPositionMatrix(), 1.2f, 0.7f, 1.2f, light, 120, 255, 220, 230);
		matrices.translate(0, -1.6, 0);
		cube(buf, matrices.peek().getPositionMatrix(), 0.45f, 1.2f, 0.45f, light, 255, 200, 80, 160);
		matrices.pop();
	}

	private static void cube(VertexConsumer buf, Matrix4f mat, float hx, float hy, float hz,
							 int light, int r, int g, int b, int a) {
		quad(buf, mat, -hx, -hy, -hz, hx, -hy, -hz, hx, hy, -hz, -hx, hy, -hz, light, r, g, b, a);
		quad(buf, mat, -hx, -hy, hz, -hx, hy, hz, hx, hy, hz, hx, -hy, hz, light, r, g, b, a);
		quad(buf, mat, -hx, hy, -hz, hx, hy, -hz, hx, hy, hz, -hx, hy, hz, light, r, g, b, a);
		quad(buf, mat, -hx, -hy, -hz, -hx, -hy, hz, hx, -hy, hz, hx, -hy, -hz, light, r, g, b, a);
		quad(buf, mat, -hx, -hy, -hz, -hx, hy, -hz, -hx, hy, hz, -hx, -hy, hz, light, r, g, b, a);
		quad(buf, mat, hx, -hy, -hz, hx, -hy, hz, hx, hy, hz, hx, hy, -hz, light, r, g, b, a);
	}

	private static void quad(VertexConsumer buf, Matrix4f mat,
							 float x0, float y0, float z0,
							 float x1, float y1, float z1,
							 float x2, float y2, float z2,
							 float x3, float y3, float z3,
							 int light, int r, int g, int b, int a) {
		buf.vertex(mat, x0, y0, z0).color(r, g, b, a).texture(0, 0).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x1, y1, z1).color(r, g, b, a).texture(1, 0).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x2, y2, z2).color(r, g, b, a).texture(1, 1).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x3, y3, z3).color(r, g, b, a).texture(0, 1).overlay(0).light(light).normal(0, 1, 0);
	}

	@Override
	public Identifier getTexture(SkyUfoEntity entity) {
		return TEXTURE;
	}
}
