package com.terminaldetector.drmd.client.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable cube ownership for cached authored geometry. No world, GL or worker-side loading. */
public final class HorizonOrbitalHandoff {
    public static final int MAX_OBJECTS=16, MAX_CUBES_PER_OBJECT=27, MAX_MASKS=432;
    /** 108 vec4s: 432 components, within GL3.2's minimum 1024 vertex-uniform components. */
    public static final int UNIFORM_VECTORS=108;
    public record Cube(int x,int y,int z) {}
    public record Key(HorizonLandmark mark,Cube cube) {
        public Key { Objects.requireNonNull(mark);Objects.requireNonNull(cube); }
    }
    public record Piece(HorizonOrbitalProxy.Box box,Cube cube) {}
    private HorizonOrbitalHandoff() {}

    /** Split half-open authored solids at real cube boundaries, including signed coordinates. */
    public static List<Piece> split(HorizonOrbitalProxy.Box box) {
        if(!(box.minX()<box.maxX()&&box.minY()<box.maxY()&&box.minZ()<box.maxZ())
            ||!finite(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())
            ||box.maxX()-box.minX()>32||box.maxY()-box.minY()>32||box.maxZ()-box.minZ()>32)
            throw new IllegalArgumentException("invalid authored proxy box");
        int x0=cube(box.minX()),y0=cube(box.minY()),z0=cube(box.minZ());
        int x1=upperCube(box.maxX()),y1=upperCube(box.maxY()),z1=upperCube(box.maxZ());
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>MAX_CUBES_PER_OBJECT)
            throw new IllegalArgumentException("authored proxy exceeds bounded cube footprint");
        List<Piece> result=new ArrayList<>();
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
            var clipped=new HorizonOrbitalProxy.Box(Math.max(box.minX(),x*16.0),Math.max(box.minY(),y*16.0),
                Math.max(box.minZ(),z*16.0),Math.min(box.maxX(),(x+1)*16.0),Math.min(box.maxY(),(y+1)*16.0),
                Math.min(box.maxZ(),(z+1)*16.0),box.colour());
            result.add(new Piece(clipped,new Cube(x,y,z)));
        }
        return List.copyOf(result);
    }

    /**
     * FULL cubes inside the current 3D view cube belong wholly to the real scene. No pristine
     * silhouette may fill a mined hole there. Outside that area a one-cube spatial ramp joins
     * the proxy. Unavailable cubes remain represented; FULL is not inferred from a column or Y.
     * This metadata policy does not claim per-section GPU-upload completion.
     */
    public static float opacity(Cube cell,double eyeX,double eyeY,double eyeZ,int viewRadius,boolean full) {
        if(viewRadius<1||!finite(eyeX,eyeY,eyeZ))throw new IllegalArgumentException("current orbital view");
        int cx=cube(eyeX),cy=cube(eyeY),cz=cube(eyeZ);
        long outside=Math.max(Math.max(Math.abs((long)cell.x-cx),Math.abs((long)cell.y-cy)),Math.abs((long)cell.z-cz))-viewRadius;
        if(!full)return 1;
        if(outside<=0)return 0;
        // Continuous camera-to-view-boundary distance, not distance to procedural ground.
        double dx=Math.max(0,Math.abs((cell.x+.5)*16-eyeX)-(viewRadius+.5)*16);
        double dy=Math.max(0,Math.abs((cell.y+.5)*16-eyeY)-(viewRadius+.5)*16);
        double dz=Math.max(0,Math.abs((cell.z+.5)*16-eyeZ)-(viewRadius+.5)*16);
        double t=Math.min(1,Math.max(dx,Math.max(dy,dz))/16);
        return (float)(t*t*(3-2*t));
    }

    /** Removed/replaced catalogue entries suppress even an older asynchronously built VBO now. */
    public static float[] mask(List<Key> keys,Set<HorizonLandmark> current,double x,double y,double z,
                               int viewRadius,Predicate<Cube> full) {
        if(keys.size()>MAX_MASKS)throw new IllegalArgumentException("orbital mask capacity");
        float[] result=new float[MAX_MASKS];
        for(int i=0;i<keys.size();i++) {
            Key key=keys.get(i);
            if(current.contains(key.mark()))result[i]=opacity(key.cube(),x,y,z,viewRadius,full.test(key.cube()));
        }
        return result;
    }
    private static int upperCube(double value){return (int)Math.ceil(value/16)-1;}
    private static int cube(double value){return (int)Math.floor(value/16);}
    private static boolean finite(double... values){for(double v:values)if(!Double.isFinite(v))return false;return true;}
}
