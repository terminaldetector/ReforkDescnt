package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.model.DescentDroneModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class DroneRenderer extends MobEntityRenderer<DroneEntity, DescentDroneModel<DroneEntity>> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "drone"), "main");
	private static final Identifier TEXTURE = Identifier.of(DescentMod.MOD_ID, "textures/entity/drone.png");

	public DroneRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new DescentDroneModel<>(ctx.getPart(LAYER)), 0.4f);
	}

	@Override
	public void render(DroneEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		matrices.scale(1.2f, 1.2f, 1.2f);
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		matrices.pop();
	}

	@Override
	public Identifier getTexture(DroneEntity entity) {
		return TEXTURE;
	}
}
