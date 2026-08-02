package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.mega.DroneSwarmEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Swarm anchor — translucent cloud silhouette; actual drones are separate entities.
 */
public class DroneSwarmRenderer extends EntityRenderer<DroneSwarmEntity> {
	private static final Identifier TEXTURE = Identifier.of(com.terminaldetector.drmd.DescentMod.MOD_ID, "textures/entity/drone_swarm.png");

	public DroneSwarmRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(DroneSwarmEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		VertexConsumer buf = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		Matrix4f mat = matrices.peek().getPositionMatrix();
		float s = 8f;
		quad(buf, mat, -s, -s * 0.4f, -s, s, -s * 0.4f, -s, s, s * 0.4f, -s, -s, s * 0.4f, -s, light);
		quad(buf, mat, -s, -s * 0.4f, s, -s, s * 0.4f, s, s, s * 0.4f, s, s, -s * 0.4f, s, light);
		quad(buf, mat, -s, s * 0.4f, -s, s, s * 0.4f, -s, s, s * 0.4f, s, -s, s * 0.4f, s, light);
		quad(buf, mat, -s, -s * 0.4f, -s, -s, -s * 0.4f, s, s, -s * 0.4f, s, s, -s * 0.4f, -s, light);
	}

	private static void quad(VertexConsumer buf, Matrix4f mat,
							 float x0, float y0, float z0,
							 float x1, float y1, float z1,
							 float x2, float y2, float z2,
							 float x3, float y3, float z3,
							 int light) {
		buf.vertex(mat, x0, y0, z0).color(200, 40, 40, 90).texture(0, 0).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x1, y1, z1).color(200, 40, 40, 90).texture(1, 0).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x2, y2, z2).color(200, 40, 40, 90).texture(1, 1).overlay(0).light(light).normal(0, 1, 0);
		buf.vertex(mat, x3, y3, z3).color(200, 40, 40, 90).texture(0, 1).overlay(0).light(light).normal(0, 1, 0);
	}

	@Override
	public Identifier getTexture(DroneSwarmEntity entity) {
		return TEXTURE;
	}
}
