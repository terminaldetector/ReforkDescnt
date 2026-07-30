package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.mega.MegaWormEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Procedural multi-segment sky worm — landscape-scale body the player can fly along.
 */
public class MegaWormRenderer extends EntityRenderer<MegaWormEntity> {
	private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/block/magma.png");

	public MegaWormRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(MegaWormEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE));
		Vec3d origin = entity.getLerpedPos(tickDelta);
		for (int i = 0; i < MegaWormEntity.SEGMENTS; i++) {
			Vec3d seg = entity.segmentPos(i, tickDelta).subtract(origin);
			float scale = 1.6f - i * 0.03f;
			matrices.push();
			matrices.translate(seg.x, seg.y, seg.z);
			Matrix4f mat = matrices.peek().getPositionMatrix();
			cube(buf, mat, scale, light, 180, 90 + i * 2, 40);
			matrices.pop();
		}
	}

	private static void cube(VertexConsumer buf, Matrix4f mat, float s, int light, int r, int g, int b) {
		float h = s;
		quad(buf, mat, -s, -h, -s, s, -h, -s, s, h, -s, -s, h, -s, light, r, g, b);
		quad(buf, mat, -s, -h, s, -s, h, s, s, h, s, s, -h, s, light, r, g, b);
		quad(buf, mat, -s, h, -s, s, h, -s, s, h, s, -s, h, s, light, r, g, b);
		quad(buf, mat, -s, -h, -s, -s, -h, s, s, -h, s, s, -h, -s, light, r, g, b);
		quad(buf, mat, -s, -h, -s, -s, h, -s, -s, h, s, -s, -h, s, light, r, g, b);
		quad(buf, mat, s, -h, -s, s, -h, s, s, h, s, s, h, -s, light, r, g, b);
	}

	private static void quad(VertexConsumer buf, Matrix4f mat,
							 float x0, float y0, float z0,
							 float x1, float y1, float z1,
							 float x2, float y2, float z2,
							 float x3, float y3, float z3,
							 int light, int r, int g, int b) {
		buf.vertex(mat, x0, y0, z0).color(r, g, b, 255).texture(0, 0).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x1, y1, z1).color(r, g, b, 255).texture(1, 0).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x2, y2, z2).color(r, g, b, 255).texture(1, 1).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x3, y3, z3).color(r, g, b, 255).texture(0, 1).overlay(0).light(light).normal(0, 1, 0);
	}

	@Override
	public Identifier getTexture(MegaWormEntity entity) {
		return TEXTURE;
	}
}
