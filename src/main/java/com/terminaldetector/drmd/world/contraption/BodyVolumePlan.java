package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.physics.collision.BodyBounds;
import java.util.*;

/** Numeric full-volume loading plan: includes empty interior, not only occupied cells/pivot.
 * One bounded vertical range per XZ column; no Minecraft access or synchronous generation. */
public final class BodyVolumePlan {
    public record Column(int x,int z,int minY,int maxY) {
        public Column{if(minY>maxY)throw new IllegalArgumentException("empty column range");}
        public long key(){return ((long)x<<32)|(z&0xffffffffL);}
        public int centerY(){return minY+(maxY-minY)/2;}
        public int radiusY(){return (maxY-minY+1)/2;}
    }
    private BodyVolumePlan(){}
    public static List<Column> columns(BodyBounds bounds,int bottom,int top) {
        int x0=cube(bounds.min().x()-2),x1=cube(bounds.max().x()+2);
        int z0=cube(bounds.min().z()-2),z1=cube(bounds.max().z()+2);
        int y0=Math.max(cube(bounds.min().y()-2),Math.floorDiv(bottom,16));
        int y1=Math.min(cube(bounds.max().y()+2),Math.floorDiv(top-1,16));
        if(y0>y1)return List.of();
        // The current grid/speed envelope fits this comfortably. Reject arithmetic or
        // invalid geometry before any ticket mutation; never silently truncate a hull.
        if((long)(x1-x0+1)*(z1-z0+1)>4096||(long)y1-y0>128)throw new IllegalArgumentException("body volume plan exceeds geometry envelope");
        var result=new ArrayList<Column>();
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)result.add(new Column(x,z,y0,y1));
        double cx=(bounds.min().x()+bounds.max().x())/32,cz=(bounds.min().z()+bounds.max().z())/32;
        result.sort(Comparator.<Column>comparingDouble(c->Math.pow(c.x()+.5-cx,2)+Math.pow(c.z()+.5-cz,2)).thenComparingInt(Column::x).thenComparingInt(Column::z));
        return List.copyOf(result);
    }
    private static int cube(double coordinate) {
        if(!Double.isFinite(coordinate)||coordinate<-30000128||coordinate>30000128)throw new IllegalArgumentException("body loading coordinate");
        return Math.floorDiv((int)Math.floor(coordinate),16);
    }
}
