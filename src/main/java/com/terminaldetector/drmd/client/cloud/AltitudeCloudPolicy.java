package com.terminaldetector.drmd.client.cloud;

import java.util.Properties;

/** Client presentation only. The stock path through Y384 is invariant, including old config files. */
public record AltitudeCloudPolicy(boolean enabled,float startHeight,float fullHeight,float range) {
    /** Actual focused Entity.getY anchors transitions; the real eye remains independent for rays. */
    public record ViewAltitude(double referenceY,double eyeY){public boolean valid(){return Double.isFinite(referenceY)&&Double.isFinite(eyeY);}public float weight(AltitudeCloudPolicy policy){return valid()?policy.weight(referenceY):0;}}
    public static final float DEFAULT_START=384,DEFAULT_FULL=512,DEFAULT_RANGE=4096;
    public static final int MAX_PIXELS=8_388_608;
    public static final long MAX_TARGET_BYTES=96L*1024*1024;
    public static final float MAX_RAY_DISTANCE=32768;
    public AltitudeCloudPolicy {
        startHeight=finite(startHeight,DEFAULT_START,384,8192);
        fullHeight=finite(fullHeight,Math.max(DEFAULT_FULL,startHeight+128),startHeight+1,startHeight+4096);
        range=finite(range,DEFAULT_RANGE,512,16384);
    }
    private static float finite(float value,float fallback,float low,float high){return Math.max(low,Math.min(high,Float.isFinite(value)?value:fallback));}
    private static float number(Properties p,String name,float fallback){try{return Float.parseFloat(p.getProperty(name,Float.toString(fallback)));}catch(NumberFormatException ignored){return fallback;}}
    public static AltitudeCloudPolicy read(Properties p){return new AltitudeCloudPolicy(Boolean.parseBoolean(p.getProperty("altitudeClouds","true")),number(p,"cloudAltitudeStart",DEFAULT_START),number(p,"cloudAltitudeFull",DEFAULT_FULL),number(p,"cloudRange",DEFAULT_RANGE));}
    public void write(Properties p){p.setProperty("altitudeClouds",Boolean.toString(enabled));p.setProperty("cloudAltitudeStart",Float.toString(startHeight));p.setProperty("cloudAltitudeFull",Float.toString(fullHeight));p.setProperty("cloudRange",Float.toString(range));}
    public float weight(double cameraY){if(!enabled||!Double.isFinite(cameraY)||cameraY<=startHeight)return 0;double t=Math.max(0,Math.min(1,(cameraY-startHeight)/(fullHeight-startHeight)));return (float)(t*t*(3-2*t));}
    public static boolean acceptsTarget(int width,int height,int maxTexture){return width>0&&height>0&&width<=maxTexture&&height<=maxTexture&&(long)width*height<=MAX_PIXELS;}
    public float expandedFar(float original,float weight){return original+(Math.max(original,range)-original)*Math.max(0,Math.min(1,weight));}
    /** Strict LESS must stay below cleared depth1, including after24-bit quantization. */
    public static float farDepthCeiling(boolean strictLess){return strictLess?Math.nextDown(1.0f):1.0f;}
    public double radialAlpha(double distance){if(!Double.isFinite(distance)||distance<0)return 0;double t=Math.max(0,Math.min(1,(distance-range*.85)/(range*.15)));return 1-t*t*(3-2*t);}
    /** Same source-plane intersection as the semantic GPU pass; interpolation length is not a ray hit. */
    public static double planeRay(double nx,double ny,double nz,double px,double py,double pz,double rx,double ry,double rz){double length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(!Double.isFinite(length)||length<1e-12)return Double.NaN;nx/=length;ny/=length;nz/=length;double denominator=nx*rx+ny*ry+nz*rz;if(!Double.isFinite(denominator)||Math.abs(denominator)<1e-7)return Double.NaN;double t=(nx*px+ny*py+nz*pz)/denominator;return Double.isFinite(t)&&t>0?t:Double.NaN;}
    public static double originalDepth(double forwardDistance,double near,double far){if(!(forwardDistance>0&&near>0&&far>near))return Double.NaN;return Math.max(0,Math.min(1,far/(far-near)-far*near/((far-near)*forwardDistance)));}
}
