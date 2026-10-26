package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Reuses the detached body's cached surface; a falling tree keeps its smoothed silhouette. */
final class SmoothBodySurface {
    private static final int[][] TRIANGLES={{0,1,2,2},{0,2,3,3}};
    static void render(BlockBodyEntity body,BlockBodyEntity.Cell cell,MatrixStack matrices,
            VertexConsumerProvider consumers,int light) {
        var client=MinecraftClient.getInstance();
        var model=client.getBlockRenderManager().getModel(cell.state());
        var out=consumers.getBuffer(RenderLayers.getEntityBlockLayer(cell.state(),false));
        var entry=matrices.peek();
        for(var face:body.surface(cell).faces()) {
            var direction=Direction.values()[face.direction()];
            var templates=model.getQuads(cell.state(),direction,Random.create(0));
            var template=templates.isEmpty()?null:templates.getFirst();
            int colour=template!=null && template.hasColor()
                ? client.getBlockColors().getColor(cell.state(),client.world,body.getBlockPos(),template.getColorIndex()):0xffffff;
            for(var order:TRIANGLES) {
                var a=face.vertices().get(order[0]).position();var b=face.vertices().get(order[1]).position();var c=face.vertices().get(order[2]).position();
                var normal=new org.joml.Vector3f((float)(b.x()-a.x()),(float)(b.y()-a.y()),(float)(b.z()-a.z()))
                    .cross((float)(c.x()-a.x()),(float)(c.y()-a.y()),(float)(c.z()-a.z()));
                if(normal.lengthSquared()<1e-12)continue;normal.normalize();
                for(int i:order) {
                    var v=face.vertices().get(i);var p=v.position();var t=v.texturePosition();
                    double s=face.direction()<4?t.x():t.z(),tv=face.direction()<2?t.z():t.y();
                    double u=0,w=0;
                    if(template!=null) {
                        int[] packed=template.getVertexData();int stride=packed.length/4;
                        for(int j=0;j<4;j++) {
                            double js=Float.intBitsToFloat(packed[j*stride+(face.direction()<4?0:2)]);
                            double jv=Float.intBitsToFloat(packed[j*stride+(face.direction()<2?2:1)]);
                            double weight=(js>.5?s:1-s)*(jv>.5?tv:1-tv);
                            u+=weight*Float.intBitsToFloat(packed[j*stride+4]);w+=weight*Float.intBitsToFloat(packed[j*stride+5]);
                        }
                    } else {
                        var sprite=model.getParticleSprite();u=sprite.getMinU()+s*(sprite.getMaxU()-sprite.getMinU());
                        w=sprite.getMinV()+tv*(sprite.getMaxV()-sprite.getMinV());
                    }
                    out.vertex(entry,(float)p.x(),(float)p.y(),(float)p.z()).color(0xff000000|colour)
                        .texture((float)u,(float)w).overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(entry,normal.x,normal.y,normal.z);
                }
            }
        }
    }
}
