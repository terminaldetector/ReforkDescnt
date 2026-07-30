package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
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
 * End giga-reactor core — procedural orb + ring silhouette.
 */
public class EndReactorBossRenderer extends EntityRenderer<EndReactorBossEntity> {
	private static final Identifier TEXTURE = Identifier.of(com.terminaldetector.drmd.DescentMod.MOD_ID, "textures/entity/end_reactor_boss.png");

	public EndReactorBossRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 2.4f;
	}

	@Override
	public void render(EndReactorBossEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		float pulse = 0.85f + 0.15f * MathHelper.sin((entity.age + tickDelta) * 0.15f);
		boolean shielded = entity.getHealth() >= entity.getMaxHealth() * 0.99f
				|| entity.age < 40; // visual only; shield state is server-driven via particles
		VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

		matrices.push();
		matrices.scale(pulse, pulse, pulse);
		Matrix4f mat = matrices.peek().getPositionMatrix();
		cube(buf, mat, 1.6f, light, 180, 60, 255, 210);
		cube(buf, mat, 1.05f, light, 255, 120, 255, 230);
		matrices.pop();

		// Outer ring
		matrices.push();
		matrices.translate(0, 0.2, 0);
		float spin = (entity.age + tickDelta) * 0.04f;
		for (int i = 0; i < 8; i++) {
			double a = spin + i * (Math.PI * 2 / 8);
			matrices.push();
			matrices.translate(Math.cos(a) * 2.4, Math.sin(a * 1.3) * 0.35, Math.sin(a) * 2.4);
			cube(buf, matrices.peek().getPositionMatrix(), 0.35f, light,
					shielded ? 80 : 255, shielded ? 160 : 80, 255, 200);
			matrices.pop();
		}
		matrices.pop();
	}

	private static void cube(VertexConsumer buf, Matrix4f mat, float s, int light, int r, int g, int b, int a) {
		quad(buf, mat, -s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s, light, r, g, b, a);
		quad(buf, mat, -s, -s, s, -s, s, s, s, s, s, s, -s, s, light, r, g, b, a);
		quad(buf, mat, -s, s, -s, s, s, -s, s, s, s, -s, s, s, light, r, g, b, a);
		quad(buf, mat, -s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s, light, r, g, b, a);
		quad(buf, mat, -s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s, light, r, g, b, a);
		quad(buf, mat, s, -s, -s, s, -s, s, s, s, s, s, s, -s, light, r, g, b, a);
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
	public Identifier getTexture(EndReactorBossEntity entity) {
		return TEXTURE;
	}
}
