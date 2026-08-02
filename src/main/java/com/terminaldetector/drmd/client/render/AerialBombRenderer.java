package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.bombardment.AerialBombEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Bomb trail / explosion particles carry the look; renderer keeps shadow-less tracking. */
public class AerialBombRenderer extends EntityRenderer<AerialBombEntity> {
	public AerialBombRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(AerialBombEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(AerialBombEntity entity) {
		return Identifier.of("minecraft", "textures/misc/white.png");
	}
}
