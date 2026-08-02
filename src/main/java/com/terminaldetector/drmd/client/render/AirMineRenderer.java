package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.AirMineEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SlimeEntityModel;
import net.minecraft.util.Identifier;

public class AirMineRenderer extends MobEntityRenderer<AirMineEntity, SlimeEntityModel<AirMineEntity>> {
	private static final Identifier TEXTURE = Identifier.of(com.terminaldetector.drmd.DescentMod.MOD_ID, "textures/entity/air_mine.png");

	public AirMineRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SlimeEntityModel<>(ctx.getPart(EntityModelLayers.SLIME)), 0.35f);
	}

	@Override
	public Identifier getTexture(AirMineEntity entity) {
		return TEXTURE;
	}
}
