package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.FlightEnemyEntity;
import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.entity.model.FlightEnemyMesh;
import net.minecraft.client.MinecraftClient;
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
        boolean detail = com.terminaldetector.drmd.ai.DroneFlightDetail.showSmallHullDetails(dispatcher.getSquaredDistanceToCamera(entity));
        double cameraX=Double.NaN,cameraY=Double.NaN,cameraZ=Double.NaN;
        var client=MinecraftClient.getInstance();
        if(!detail&&client.currentScreen==null&&Float.isFinite(bodyScale)&&bodyScale>0){
            // The active Camera is borrowed/restored by actual mirror/portal views too.
            // Subtract double world positions before the float quaternion transform.
            var camera=client.gameRenderer.getCamera().getPos();
            double x=(camera.x-MathHelper.lerp(delta,entity.prevX,entity.getX()))/bodyScale;
            double y=(camera.y-MathHelper.lerp(delta,entity.prevY,entity.getY())-entity.getHeight()*.5)/bodyScale;
            double z=(camera.z-MathHelper.lerp(delta,entity.prevZ,entity.getZ()))/bodyScale;
            var local=q.transformInverse(new org.joml.Vector3f((float)x,(float)y,(float)z));
            cameraX=local.x;cameraY=local.y;cameraZ=local.z;
        }
        for(var face:FlightEnemyMesh.faces(kind, detail)){
            if(!FlightEnemyMesh.emitFace(kind,face,detail,cameraX,cameraY,cameraZ))continue;
            var n=face.normal();
            for(int i=0;i<4;i++){
                var p=face.vertices().get(Math.min(i,face.vertices().size()-1));
                vertices.vertex(entry,p.x(),p.y(),p.z()).color(0xff000000|face.rgb()).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(face.emissive()?15728880:light).normal(entry,n.x(),n.y(),n.z());
            }
        }
        matrices.pop();
        if(entity instanceof com.terminaldetector.drmd.entity.TeleportFlyerEntity flyer
            &&(flyer.displayedPhase()==com.terminaldetector.drmd.ai.TeleportAbilityState.Phase.WARN
                ||flyer.displayedPhase()==com.terminaldetector.drmd.ai.TeleportAbilityState.Phase.COMMIT_PENDING)){
            // Marker is absolute int+fraction tracked data, independent of camera or body interpolation.
            var target=flyer.displayedDestination().add(0,flyer.getHeight()*.5,0);
            var interpolated=new Vec3d(MathHelper.lerp(delta,entity.prevX,entity.getX()),MathHelper.lerp(delta,entity.prevY,entity.getY()),MathHelper.lerp(delta,entity.prevZ,entity.getZ()));
            matrices.push();matrices.translate(target.x-interpolated.x,target.y-interpolated.y,target.z-interpolated.z);
            var line=consumers.getBuffer(RenderLayer.getLines());var marker=matrices.peek();float r=.55f;int color=0xffcbbdff;
            for(int plane=0;plane<3;plane++)for(int edge=0;edge<4;edge++){
                float a=edge<2?-r:r,b=edge==0||edge==3?-r:r;int next=(edge+1)%4;
                float c=next<2?-r:r,d=next==0||next==3?-r:r;
                if(plane==0)markerLine(line,marker,a,0,b,c,0,d,color);
                else if(plane==1)markerLine(line,marker,a,b,0,c,d,0,color);
                else markerLine(line,marker,0,a,b,0,c,d,color);
            }
            matrices.pop();
        }
        super.render(entity,yaw,delta,matrices,consumers,light);
    }
    private static void markerLine(VertexConsumer out,MatrixStack.Entry entry,float ax,float ay,float az,float bx,float by,float bz,int color){
        var direction=new Vec3d(bx-ax,by-ay,bz-az).normalize();
        out.vertex(entry,ax,ay,az).color(color).normal(entry,(float)direction.x,(float)direction.y,(float)direction.z);
        out.vertex(entry,bx,by,bz).color(color).normal(entry,(float)direction.x,(float)direction.y,(float)direction.z);
    }
    @Override public Identifier getTexture(FlightEnemyEntity entity){return WHITE;}
}
