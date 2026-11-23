package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.entity.model.ShapesWreckRig;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Authored Enderman rig with its real held arsenal item, not a generic drone or a hidden proxy. */
public final class ShapesInfantryRenderer extends MobEntityRenderer<ShapesInfantryEntity,EndermanEntityModel<ShapesInfantryEntity>> {
    static final Identifier TEXTURE=Identifier.ofVanilla("textures/entity/enderman/enderman.png");
    public ShapesInfantryRenderer(EntityRendererFactory.Context context){
        super(context,new ArmedModel(context.getPart(EntityModelLayers.ENDERMAN)),.5f);
        addFeature(new HeldItemFeatureRenderer<>(this,context.getHeldItemRenderer()));
        addFeature(new EyesFeatureRenderer<ShapesInfantryEntity,EndermanEntityModel<ShapesInfantryEntity>>(this){
            @Override public RenderLayer getEyesTexture(){return RenderLayer.getEyes(Identifier.ofVanilla("textures/entity/enderman/enderman_eyes.png"));}
        });
    }
    @Override public Identifier getTexture(ShapesInfantryEntity entity){return TEXTURE;}

    @Override public void render(ShapesInfantryEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertices, int light) {
        if (entity.isAlive() || !entity.isInvisible()) super.render(entity, yaw, tickDelta, matrices, vertices, light);
    }

    private static final class ArmedModel extends EndermanEntityModel<ShapesInfantryEntity> {
        private ArmedModel(ModelPart root){super(root);}
        @Override public void setAngles(ShapesInfantryEntity entity,float limbAngle,float limbDistance,float animationProgress,float headYaw,float headPitch){
            carryingBlock=false;angry=entity.isAlert();
            super.setAngles(entity,limbAngle,limbDistance,animationProgress,headYaw,headPitch);
            if(entity.hasInfantryGun()) {
                rightArm.pitch=ShapesWreckRig.gunPitch(head.pitch);
                leftArm.pitch=rightArm.pitch;
                rightArm.yaw=ShapesWreckRig.gunYaw(head.yaw, false);
                leftArm.yaw=ShapesWreckRig.gunYaw(head.yaw, true);
            }
            body.roll=entity.hitTilt(ShapesWreckRig.Part.TORSO, animationProgress);
            head.roll=entity.hitTilt(ShapesWreckRig.Part.HEAD, animationProgress);
            hat.copyTransform(head);
            rightArm.roll+=entity.hitTilt(ShapesWreckRig.Part.RIGHT_ARM, animationProgress);
            leftArm.roll+=entity.hitTilt(ShapesWreckRig.Part.LEFT_ARM, animationProgress);
            rightLeg.roll+=entity.hitTilt(ShapesWreckRig.Part.RIGHT_LEG, animationProgress);
            leftLeg.roll+=entity.hitTilt(ShapesWreckRig.Part.LEFT_LEG, animationProgress);
        }
    }
}
