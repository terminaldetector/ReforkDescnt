package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.entity.mob.SpiderTurretEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Spider turret — four legs under a head carrying an MG barrel and a laser emitter.
 *
 * <p>64×64 atlas; UV origins mirror {@code scripts/gen_textures.py} ({@code ent_spider_turret}).
 * Ground is at y = +24, as for any walking model.
 */
public class SpiderTurretModel extends EntityModel<SpiderTurretEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart[] legs = new ModelPart[4];

	public SpiderTurretModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		for (int i = 0; i < 4; i++) {
			this.legs[i] = root.getChild("leg" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		root.addChild("base", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-5f, 14f, -5f, 10f, 4f, 10f),
				ModelTransform.pivot(0, 0, 0));

		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
						.uv(0, 14).cuboid(-4f, -6f, -4f, 8f, 6f, 8f),
				ModelTransform.pivot(0, 14f, 0));
		head.addChild("mg", ModelPartBuilder.create()
						.uv(0, 28).cuboid(-1f, -1f, -12f, 2f, 2f, 8f),
				ModelTransform.pivot(-2.5f, -3f, 0));
		head.addChild("emitter", ModelPartBuilder.create()
						.uv(20, 28).cuboid(-1.5f, -1.5f, -8f, 3f, 3f, 4f),
				ModelTransform.pivot(2.5f, -3f, 0));

		// Four legs on the diagonals so the chassis silhouette stays square-on.
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + Math.PI * i / 2.0;
			root.addChild("leg" + i, ModelPartBuilder.create()
							.uv(0, 38).cuboid(-1f, 0f, -1f, 2f, 10f, 2f)
							.uv(8, 38).cuboid(-1.5f, 9f, -1.5f, 3f, 3f, 3f),
					ModelTransform.of(
							(float) (Math.sin(angle) * 5), 16f, (float) (Math.cos(angle) * 5),
							0f, (float) -angle, 0f));
		}

		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(SpiderTurretEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		// Head tracks its own synced aim, relative to whatever the chassis has managed to turn to.
		float relYaw = MathHelper.wrapDegrees(entity.getHeadYaw2() - entity.bodyYaw);
		this.head.yaw = relYaw * ((float) Math.PI / 180f);
		this.head.pitch = entity.getHeadPitch2() * ((float) Math.PI / 180f);

		boolean planted = entity.isDeployed();
		float amplitude = planted ? 0f : MathHelper.clamp(limbDistance, 0f, 1f) * 0.6f;
		for (int i = 0; i < legs.length; i++) {
			float phase = limbAngle * 0.7f + i * ((float) Math.PI / 2f);
			legs[i].pitch = MathHelper.cos(phase) * amplitude;
			// Deployed, the legs brace wide instead of walking.
			legs[i].roll = planted ? 0.28f : MathHelper.sin(phase) * amplitude * 0.3f;
		}
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(matrices, vertices, light, overlay, color);
	}
}
