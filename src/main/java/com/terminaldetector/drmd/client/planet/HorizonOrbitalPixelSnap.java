package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** PS1 sample-grid stability for tiny solid FAR asteroids. Never changes source geometry or depth. */
public final class HorizonOrbitalPixelSnap {
    public static final int MAX_OBJECTS=HorizonOrbitalHandoff.MAX_OBJECTS,COMPONENTS=MAX_OBJECTS*2;
    private HorizonOrbitalPixelSnap(){}
    public static List<HorizonLandmark> objects(List<HorizonOrbitalHandoff.Key> keys){
        var result=new ArrayList<HorizonLandmark>();
        for(var key:keys)if(!result.contains(key.mark())&&result.size()<MAX_OBJECTS)result.add(key.mark());
        return List.copyOf(result);
    }
    /** Upload-time stable object index, shared by all cube pieces of that object. Extra objects stay neutral. */
    public static int[] slots(List<HorizonOrbitalHandoff.Key> keys){
        var objects=objects(keys);var slots=new int[keys.size()];Arrays.fill(slots,-1);
        for(int i=0;i<keys.size();i++)slots[i]=objects.indexOf(keys.get(i).mark());return slots;
    }
    /** NDC translation: a subpixel axis, at most two pixels on the other, at most half a pixel per axis. */
    static float[] offset(double centreX,double centreY,double widthPx,double heightPx,int width,int height){
        if(width<=0||height<=0||!Double.isFinite(centreX)||!Double.isFinite(centreY)
            ||!Double.isFinite(widthPx)||!Double.isFinite(heightPx)||widthPx<=0||heightPx<=0
            ||Math.min(widthPx,heightPx)>=1||Math.max(widthPx,heightPx)>2
            ||centreX<0||centreX>=width||centreY<0||centreY>=height)return new float[2];
        return new float[]{(float)((Math.floor(centreX)+.5-centreX)*2/width),
            (float)((Math.floor(centreY)+.5-centreY)*2/height)};
    }
    private static Vector4f clip(Matrix4f transform,double x,double y,double z,float inner,float reach){
        double scale=HorizonProjection.factor(x,y,z,inner,reach);
        return new Vector4f((float)(x*scale),(float)(y*scale),(float)(z*scale),1).mul(transform);
    }
    /** Actual current scene dimensions/projection; false covers Native, Debug, overlays and offscreen views. */
    public static float[] shifts(List<HorizonOrbitalHandoff.Key> keys,double eyeX,double eyeY,double eyeZ,
                                  Matrix4f view,Matrix4f projection,int width,int height,float inner,float reach,boolean lowResolutionWorld){
        var result=new float[COMPONENTS];if(!lowResolutionWorld||width<=0||height<=0||!Double.isFinite(eyeX)||!Double.isFinite(eyeY)||!Double.isFinite(eyeZ)
            ||!Float.isFinite(inner)||!Float.isFinite(reach)||reach<=0)return result;
        var transform=new Matrix4f(projection).mul(view);var objects=objects(keys);
        for(int i=0;i<objects.size();i++){
            var mark=objects.get(i);
            if(mark.kind()!=MacroEntry.Kind.ASTEROID||HorizonOrbitalProxy.tierAt(mark,eyeX,eyeY,eyeZ)
                !=com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry.Tier.FAR)continue;
            double x=mark.x()-eyeX,y=mark.y()-eyeY,z=mark.z()-eyeZ;
            var centre=clip(transform,x,y,z,inner,reach);if(!Float.isFinite(centre.w)||centre.w<=0)continue;
            double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;boolean valid=true;
            for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1})for(int sz:new int[]{-1,1}){
                var p=clip(transform,x+sx*mark.sizeX()*.5,y+sy*mark.sizeY()*.5,z+sz*mark.sizeZ()*.5,inner,reach);
                if(!Float.isFinite(p.w)||p.w<=0){valid=false;continue;}
                double px=(p.x/p.w*.5+.5)*width,py=(p.y/p.w*.5+.5)*height;
                x0=Math.min(x0,px);x1=Math.max(x1,px);y0=Math.min(y0,py);y1=Math.max(y1,py);
            }
            if(!valid)continue;
            var shift=offset((centre.x/centre.w*.5+.5)*width,(centre.y/centre.w*.5+.5)*height,x1-x0,y1-y0,width,height);
            result[i*2]=shift[0];result[i*2+1]=shift[1];
        }
        return result;
    }
}
