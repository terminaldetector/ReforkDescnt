package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.bombardment.AerialBombEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bomb trail / explosion particles carry the look; renderer keeps shadow-less tracking. */
public class AerialBombRenderer extends EntityRenderer<AerialBombEntity> {
	public AerialBombRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(AerialBombEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider consumers, int light) {
        int phase=entity.clusterVisual();
        if(phase!=0){
            matrices.push();var velocity=entity.getVelocity();
            if(Double.isFinite(velocity.lengthSquared())&&velocity.lengthSquared()>1e-8){var f=velocity.normalize();matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)f.x,(float)f.y,(float)f.z)));}
            var vertices=consumers.getBuffer(RenderLayer.getEntitySolid(getTexture(entity)));float radius=phase==4?.075f:.16f,length=phase==4?.24f:.65f;int color=entity.clusterColor();
            prism(matrices,vertices,radius,length,color);prism(matrices,vertices,radius*.65f,length+(.08f),phase==5?0x6a6a6a:phase>=2&&phase<=3?0xfff0a0:0xb2d7e0);
            // Real staged carriers expose four separate casing petals while later waves leave.
            if(phase==3){for(int i=0;i<4;i++){matrices.push();matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(i*90));quad(matrices,vertices,.18f,-.08f,-.26f,.35f,-.08f,-.1f,.35f,.08f,.2f,.18f,.08f,.3f,color);matrices.pop();}}
            matrices.pop();
        }
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}
    private static void prism(MatrixStack matrices,VertexConsumer v,float r,float length,int color){
        for(int i=0;i<6;i++){double a=i*Math.PI/3,b=(i+1)*Math.PI/3;float x=(float)Math.cos(a)*r,y=(float)Math.sin(a)*r,xx=(float)Math.cos(b)*r,yy=(float)Math.sin(b)*r;
            quad(matrices,v,x,y,-length/2,xx,yy,-length/2,xx*.72f,yy*.72f,length/2,x*.72f,y*.72f,length/2,color);
            quad(matrices,v,0,0,length/2,x*.72f,y*.72f,length/2,xx*.72f,yy*.72f,length/2,0,0,length/2,color);
            quad(matrices,v,0,0,-length/2,xx,yy,-length/2,x,y,-length/2,0,0,-length/2,color);
        }
    }
    private static void quad(MatrixStack m,VertexConsumer v,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,int rgb){
        var normal=new Vector3f(bx-ax,by-ay,bz-az).cross(new Vector3f(cx-ax,cy-ay,cz-az));if(normal.lengthSquared()<1e-12)return;normal.normalize();var matrix=m.peek().getPositionMatrix();int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        v.vertex(matrix,ax,ay,az).color(r,g,b,255).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normal.x,normal.y,normal.z);
        v.vertex(matrix,bx,by,bz).color(r,g,b,255).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normal.x,normal.y,normal.z);
        v.vertex(matrix,cx,cy,cz).color(r,g,b,255).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normal.x,normal.y,normal.z);
        v.vertex(matrix,dx,dy,dz).color(r,g,b,255).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normal.x,normal.y,normal.z);
    }

	@Override
	public Identifier getTexture(AerialBombEntity entity) {
		return Identifier.of("minecraft", "textures/misc/white.png");
	}
}
