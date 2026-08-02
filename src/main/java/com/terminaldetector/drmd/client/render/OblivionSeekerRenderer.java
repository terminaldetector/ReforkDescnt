package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity;
import com.terminaldetector.drmd.entity.model.OblivionSeekerModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class OblivionSeekerRenderer extends MobEntityRenderer<OblivionSeekerEntity, OblivionSeekerModel> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of(DescentMod.MOD_ID, "oblivion_seeker"), "main");
	private static final Identifier TEXTURE =
			Identifier.of(DescentMod.MOD_ID, "textures/entity/oblivion_seeker.png");

	public OblivionSeekerRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new OblivionSeekerModel(ctx.getPart(LAYER)), 0.55f);
	}

	@Override
	public void render(OblivionSeekerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		matrices.push();
		// Full 3D bank from FlightAttitude (GMod-style enemy roll).
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getFlightRoll()));
		float s = 1.15f;
		matrices.scale(s, s, s);
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		matrices.pop();
	}

	@Override
	public Identifier getTexture(OblivionSeekerEntity entity) {
		return TEXTURE;
	}
}
