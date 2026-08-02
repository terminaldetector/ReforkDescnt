package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.mob.SpiderTurretEntity;
import com.terminaldetector.drmd.entity.model.SpiderTurretModel;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class SpiderTurretRenderer extends MobEntityRenderer<SpiderTurretEntity, SpiderTurretModel> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "spider_turret"), "main");
	private static final Identifier TEXTURE =
			Identifier.of(DescentMod.MOD_ID, "textures/entity/spider_turret.png");

	public SpiderTurretRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SpiderTurretModel(ctx.getPart(LAYER)), 0.6f);
	}

	@Override
	public Identifier getTexture(SpiderTurretEntity entity) {
		return TEXTURE;
	}
}
