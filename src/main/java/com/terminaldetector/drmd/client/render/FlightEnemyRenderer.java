package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.FlightEnemyEntity;
import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.entity.model.FlightEnemyMesh;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Centre-based real hulls; no vanilla standing-model offset or camera-facing impostor. */
public final class FlightEnemyRenderer extends EntityRenderer<FlightEnemyEntity> {
    private static final Identifier WHITE=Identifier.ofVanilla("textures/misc/white.png");
    private final FlightEnemyKind kind;
    public FlightEnemyRenderer(EntityRendererFactory.Context context,FlightEnemyKind kind){super(context);this.kind=kind;shadowRadius=kind.diameter*.35f;}
    @Override public void render(FlightEnemyEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light) {
        if(entity.isInvisible()){super.render(entity,yaw,delta,matrices,consumers,light);return;}
        float heading=MathHelper.lerpAngleDegrees(delta,entity.prevYaw,entity.getYaw()),pitch=MathHelper.lerp(delta,entity.prevPitch,entity.getPitch());
        var q=FlightEnemyPose.rotation(heading,pitch,entity.getFlightRoll());
        matrices.push();matrices.translate(0,entity.getHeight()*.5,0);matrices.multiply(q);float bodyScale=entity.getScale();matrices.scale(bodyScale,bodyScale,bodyScale);
        var vertices=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));var entry=matrices.peek();
        for(var face:FlightEnemyMesh.faces(kind))for(int i=0;i<4;i++) {
            var p=face.vertices().get(Math.min(i,face.vertices().size()-1));var n=face.normal();
            vertices.vertex(entry,p.x(),p.y(),p.z()).color(0xff000000|face.rgb()).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(face.emissive()?15728880:light).normal(entry,n.x(),n.y(),n.z());
        }
        matrices.pop();super.render(entity,yaw,delta,matrices,consumers,light);
    }
    @Override public Identifier getTexture(FlightEnemyEntity entity){return WHITE;}
}
