package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Reactor Keeper — procedural sentinel silhouette (placeholder mega-mob).
 */
public class ReactorKeeperRenderer extends EntityRenderer<ReactorKeeperEntity> {
	private static final Identifier TEXTURE = Identifier.of(com.terminaldetector.drmd.DescentMod.MOD_ID, "textures/entity/reactor_keeper.png");

	public ReactorKeeperRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(ReactorKeeperEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		Matrix4f mat = matrices.peek().getPositionMatrix();
		float s = 2.2f;
		cube(buf, mat, s, light, 60, 220, 255, 200);
		// Core
		matrices.push();
		matrices.translate(0, 1.2, 0);
		cube(buf, matrices.peek().getPositionMatrix(), 0.9f, light, 255, 255, 200, 230);
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
	public Identifier getTexture(ReactorKeeperEntity entity) {
		return TEXTURE;
	}
}
