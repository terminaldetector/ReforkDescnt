package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.mob.SpiderTurretEntity;
import com.terminaldetector.drmd.entity.model.SpiderTurretModel;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class SpiderTurretRenderer extends MobEntityRenderer<SpiderTurretEntity, SpiderTurretModel> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "spider_turret"), "main");
	private static final Identifier TEXTURE =
			Identifier.of(DescentMod.MOD_ID, "textures/entity/spider_turret.png");
	private final SpiderTurretModel nativeModel,coarseModel;

	public SpiderTurretRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SpiderTurretModel(ctx.getPart(LAYER)), 0.6f);
		nativeModel=model;
		coarseModel=new SpiderTurretModel(SpiderTurretModel.getLowPolyTexturedModelData().createModel());
	}

	@Override
	public void render(SpiderTurretEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light) {
		var previous=model;
		try {
			model=com.terminaldetector.drmd.client.presentation.Ps1WorldAssets.coarse()?coarseModel:nativeModel;
			super.render(entity,yaw,delta,matrices,consumers,light);
		} finally {model=previous;}
	}

	@Override
	public Identifier getTexture(SpiderTurretEntity entity) {
		return TEXTURE;
	}
}
