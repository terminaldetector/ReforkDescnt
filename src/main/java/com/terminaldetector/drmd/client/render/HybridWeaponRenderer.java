package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.workshop.ClusterModule;
import com.terminaldetector.drmd.workshop.HybridWeaponModels;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/** World renderer for the exact mesh used by the construction preview. */
public final class HybridWeaponRenderer {
    private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
    private HybridWeaponRenderer() {}
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, ClusterModule module,
                            float recoil, float load, int light) {
        var entry = matrices.peek();
        var buffer = consumers.getBuffer(module.colorA<255 ? RenderLayer.getEntityTranslucent(WHITE) : RenderLayer.getEntitySolid(WHITE));
        for(var part : HybridWeaponModels.parts(module.model)) {
            int col = tint(part.material().rgb, module);
            if(part.material().emissive) {
                float power=module.model.equals(HybridWeaponModels.RAIL) ? .35f + (load>0 ? .2f+.45f*(float)Math.sqrt(load) : 0) : .65f+.35f*recoil;
                col=(col&0xff000000)|((int)(((col>>16)&255)*power)<<16)|((int)(((col>>8)&255)*power)<<8)|(int)((col&255)*power);
            }
            int partLight = part.material().emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            for(var face : part.faces()) {
                var points = face.points().stream().map(p -> HybridWeaponModels.transform(module,p,recoil,part.moving()).div(16)).toList();
                Vector3f n = new Vector3f(points.get(1)).sub(points.get(0))
                        .cross(new Vector3f(points.get(2)).sub(points.get(0)));
                if(n.lengthSquared()<1e-12f) continue;
                n.normalize();
                for(int i : new int[]{0,1,2,3,3,2,1,0}) {
                    Vector3f p=points.get(i);
                    buffer.vertex(entry.getPositionMatrix(),p.x,p.y,p.z).color(col).texture(0,0)
                            .overlay(OverlayTexture.DEFAULT_UV).light(partLight).normal(entry,n.x,n.y,n.z);
                }
            }
        }
    }
    /** Solid voxel brace between a hull hardpoint and the saved module's socket. */
    public static void brace(MatrixStack matrices,VertexConsumerProvider consumers,
                             net.minecraft.util.math.Vec3d from,net.minecraft.util.math.Vec3d to,int light) {
        var delta=to.subtract(from);
        if(delta.lengthSquared()<1e-6) return;
        var middle=from.add(to).multiply(.5);
        matrices.push(); matrices.translate(middle.x,middle.y,middle.z);
        matrices.multiply(new org.joml.Quaternionf().rotationTo(new Vector3f(0,0,1),
                new Vector3f((float)delta.x,(float)delta.y,(float)delta.z).normalize()));
        matrices.scale(.14f,.14f,(float)delta.length());
        var buffer=consumers.getBuffer(RenderLayer.getEntitySolid(WHITE));
        var entry=matrices.peek();
        float[][] vertices={{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f},
                {-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,1,5,4},{3,7,6,2},{0,4,7,3},{1,2,6,5}};
        for(var face:faces) {
            var a=vertices[face[0]];var b=vertices[face[1]];var c=vertices[face[2]];
            var normal=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2])
                    .cross(new Vector3f(c[0]-a[0],c[1]-a[1],c[2]-a[2])).normalize();
            for(int i:new int[]{0,1,2,3,3,2,1,0}) {
                var v=vertices[face[i]];
                buffer.vertex(entry.getPositionMatrix(),v[0],v[1],v[2]).color(0xff506579).texture(0,0)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,normal.x,normal.y,normal.z);
            }
        }
        matrices.pop();
    }
    public static int tint(int rgb, ClusterModule m) {
        return (Math.clamp(m.colorA,0,255)<<24) |
                (((rgb>>16)&255)*Math.clamp(m.colorR,0,255)/255<<16) |
                (((rgb>>8)&255)*Math.clamp(m.colorG,0,255)/255<<8) |
                ((rgb&255)*Math.clamp(m.colorB,0,255)/255);
    }
}
