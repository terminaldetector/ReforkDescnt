package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.mob.ScannerEntity;
import com.terminaldetector.drmd.entity.model.ScannerModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class ScannerRenderer extends MobEntityRenderer<ScannerEntity, ScannerModel> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "scanner"), "main");
	private static final Identifier TEXTURE =
			Identifier.of(DescentMod.MOD_ID, "textures/entity/scanner.png");

	public ScannerRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new ScannerModel(ctx.getPart(LAYER)), 0.35f);
	}

	@Override
	public void render(ScannerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		// Bank the whole hull the way the flight rules say it is leaning.
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getFlightRoll()));
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		matrices.pop();
	}

	@Override
	public Identifier getTexture(ScannerEntity entity) {
		return TEXTURE;
	}
}
