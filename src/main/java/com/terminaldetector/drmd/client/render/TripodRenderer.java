package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.mob.TripodEntity;
import com.terminaldetector.drmd.entity.model.TripodModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class TripodRenderer extends MobEntityRenderer<TripodEntity, TripodModel> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "tripod"), "main");
	private static final Identifier TEXTURE =
			Identifier.of(DescentMod.MOD_ID, "textures/entity/tripod.png");

	public TripodRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new TripodModel(ctx.getPart(LAYER)), 1.85f);
	}

	@Override
	public void render(TripodEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		// Early WotW machines are big — presence scale beyond the collision box.
		matrices.scale(1.35f, 1.4f, 1.35f);
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		matrices.pop();
	}

	@Override
	public Identifier getTexture(TripodEntity entity) {
		return TEXTURE;
	}
}
