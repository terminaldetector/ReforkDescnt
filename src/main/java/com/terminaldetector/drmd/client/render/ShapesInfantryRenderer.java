package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.util.Identifier;

/** Authored Enderman rig with its real held arsenal item, not a generic drone or a hidden proxy. */
public final class ShapesInfantryRenderer extends MobEntityRenderer<ShapesInfantryEntity,EndermanEntityModel<ShapesInfantryEntity>> {
    private static final Identifier TEXTURE=Identifier.ofVanilla("textures/entity/enderman/enderman.png");
    public ShapesInfantryRenderer(EntityRendererFactory.Context context){
        super(context,new ArmedModel(context.getPart(EntityModelLayers.ENDERMAN)),.5f);
        addFeature(new HeldItemFeatureRenderer<>(this,context.getHeldItemRenderer()));
        addFeature(new EyesFeatureRenderer<ShapesInfantryEntity,EndermanEntityModel<ShapesInfantryEntity>>(this){
            @Override public RenderLayer getEyesTexture(){return RenderLayer.getEyes(Identifier.ofVanilla("textures/entity/enderman/enderman_eyes.png"));}
        });
    }
    @Override public Identifier getTexture(ShapesInfantryEntity entity){return TEXTURE;}
    private static final class ArmedModel extends EndermanEntityModel<ShapesInfantryEntity> {
        private ArmedModel(ModelPart root){super(root);}
        @Override public void setAngles(ShapesInfantryEntity entity,float limbAngle,float limbDistance,float animationProgress,float headYaw,float headPitch){
            carryingBlock=false;angry=entity.isAlert();
            super.setAngles(entity,limbAngle,limbDistance,animationProgress,headYaw,headPitch);
            if(!entity.hasInfantryGun())return;
            // Pose the actual long Enderman arms; the held item feature follows the authored hand transform.
            rightArm.pitch=-1.5707964f+head.pitch;rightArm.yaw=head.yaw-.1f;
            leftArm.pitch=rightArm.pitch;leftArm.yaw=head.yaw+.45f;
        }
    }
}
