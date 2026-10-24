package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.workshop.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Interactive orthographic mesh preview: same parts, module transforms and moving mechanisms as FP. */
public final class WorkshopMeshPreview {
    private WorkshopMeshPreview() {}
    private record Face(List<Vector3f> points,int colour,float depth) {}
    public static int draw(DrawContext context,List<ClusterModule> modules,int x,int y,int width,int height,
                           float yaw,float pitch,float recoil) {
        var orbit=new Quaternionf().rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
        List<Face> faces=new ArrayList<>();
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        for(var m:modules) for(var part:HybridWeaponModels.parts(m.model)) for(var face:part.faces()) {
            List<Vector3f> points=new ArrayList<>(); float depth=0;
            for(var p:face.points()) {
                var v=HybridWeaponModels.transform(m,p,recoil,part.moving());
                v.add(m.posRgt,m.posUp,-m.posFwd); orbit.transform(v);
                points.add(v); depth+=v.z;
                minX=Math.min(minX,v.x); maxX=Math.max(maxX,v.x);
                minY=Math.min(minY,v.y); maxY=Math.max(maxY,v.y);
            }
            var n=new Vector3f(points.get(1)).sub(points.get(0)).cross(new Vector3f(points.get(2)).sub(points.get(0)));
            if(n.lengthSquared()<1e-8f) continue;
            n.normalize();
            // Preview key light shows the chamfers even in a dark world.
            float shade=part.material().emissive?1f:.45f+.55f*Math.abs(n.dot(new Vector3f(-.4f,.7f,-.6f).normalize()));
            int c=HybridWeaponRenderer.tint(part.material().rgb,m);
            c=(c&0xff000000)|((int)(((c>>16)&255)*shade)<<16)|((int)(((c>>8)&255)*shade)<<8)|(int)((c&255)*shade);
            faces.add(new Face(points,c,depth/4));
        }
        if(faces.isEmpty()) return 0;
        float scale=Math.min((width-24)/Math.max(1,maxX-minX),(height-24)/Math.max(1,maxY-minY));
        float cx=(minX+maxX)/2,cy=(minY+maxY)/2;
        faces.sort(Comparator.comparingDouble(Face::depth).reversed());
        context.enableScissor(x,y,x+width,y+height);
        context.draw();
        var buffer=context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        var matrix=context.getMatrices().peek().getPositionMatrix();
        for(var face:faces) for(int index:new int[]{0,1,2,3,3,2,1,0}) {
            var p=face.points().get(index);
            buffer.vertex(matrix,x+width/2f+(p.x-cx)*scale,y+height/2f-(p.y-cy)*scale,0).color(face.colour());
        }
        context.draw(); context.disableScissor();
        return faces.size();
    }
}
