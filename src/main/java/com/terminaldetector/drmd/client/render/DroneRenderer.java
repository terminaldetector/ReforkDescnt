package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SilverfishEntityModel;
import net.minecraft.util.Identifier;

public class DroneRenderer extends MobEntityRenderer<DroneEntity, SilverfishEntityModel<DroneEntity>> {
	private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/entity/silverfish.png");

	public DroneRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SilverfishEntityModel<>(ctx.getPart(EntityModelLayers.SILVERFISH)), 0.4f);
	}

	@Override
	public Identifier getTexture(DroneEntity entity) {
		return TEXTURE;
	}
}
