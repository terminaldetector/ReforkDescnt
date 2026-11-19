package com.terminaldetector.drmd.world.geometry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** A bounded waterlogged volume complements the SAME conservative collider, never the retained solid. */
public final class MicroWaterVolume {
    public static final int SIZE=16;
    private static final Map<HybridMesh.Neighbourhood,MicroWaterVolume> CACHE=new ConcurrentHashMap<>();
    private final BitSet empty;
    private final boolean fillable;
    public record Quad(int direction,List<HybridMesh.Point> points){public Quad{points=List.copyOf(points);}}
    public interface Outside{boolean filled(int x,int y,int z);}
    private MicroWaterVolume(BitSet occupied){
        if(occupied.length()>4096)throw new IllegalArgumentException("unit water volume cap4096");
        empty=(BitSet)occupied.clone();empty.flip(0,4096);fillable=connectedToBoundary();
    }
    public static MicroWaterVolume fromOccupied(BitSet occupied){return new MicroWaterVolume(occupied);}
    public static MicroWaterVolume of(HybridMesh.Neighbourhood key){
        var cached=CACHE.get(key);if(cached!=null)return cached;
        var solid=HybridCellRaster.raster(new HybridMesh(key).collisionCells());var unit=new BitSet(4096);
        for(int y=0;y<SIZE;y++)for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++)if(solid.get(HybridCellRaster.index(x+8,y+8,z+8)))unit.set(index(x,y,z));
        var volume=new MicroWaterVolume(unit);if(CACHE.size()<512)CACHE.putIfAbsent(key,volume);return volume;
    }
    private static int index(int x,int y,int z){return (y*SIZE+z)*SIZE+x;}
    public boolean empty(int x,int y,int z){return x>=0&&y>=0&&z>=0&&x<SIZE&&y<SIZE&&z<SIZE&&empty.get(index(x,y,z));}
    public int emptyCells(){return empty.cardinality();}
    public boolean fillable(){return fillable;}
    private boolean connectedToBoundary(){
        int first=empty.nextSetBit(0);if(first<0)return false;
        var seen=new BitSet(4096);int[] queue=new int[4096];int read=0,write=1;queue[0]=first;seen.set(first);boolean boundary=false;
        while(read<write){int at=queue[read++],x=at%16,z=(at/16)%16,y=at/256;boundary|=x==0||y==0||z==0||x==15||y==15||z==15;
            for(int[] step:STEPS){int a=x+step[0],b=y+step[1],c=z+step[2];if(empty(a,b,c)){int next=index(a,b,c);if(!seen.get(next)){seen.set(next);queue[write++]=next;}}}
        }
        // One externally connected void only: a new fill cannot invent water in a separate sealed pocket.
        return boundary&&write==empty.cardinality();
    }
    private static final int[][] STEPS={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    private boolean water(int x,int y,int z,double height,Outside outside){
        if(x<0||y<0||z<0||x>=SIZE||y>=SIZE||z>=SIZE)return outside.filled(x,y,z);
        return y/16d<height&&empty(x,y,z);
    }
    /** Greedy boundary quads. Native fluid height clips the same volume; neighbouring real water culls joins. */
    public List<Quad> faces(double height,Outside outside){
        if(!Double.isFinite(height)||height<=0)return List.of();height=Math.min(1,height);var result=new ArrayList<Quad>();
        for(int d=0;d<6;d++)for(int slice=0;slice<SIZE;slice++){
            boolean[][] visible=new boolean[16][16];
            for(int v=0;v<16;v++)for(int u=0;u<16;u++){int x=d<4?u:slice,y=d<2?slice:v,z=d<2?v:d<4?slice:u;
                visible[v][u]=water(x,y,z,height,outside)&&(!water(x+STEPS[d][0],y+STEPS[d][1],z+STEPS[d][2],height,outside)||d==1&&(y+1)/16d>height);
            }
            for(int v=0;v<16;v++)for(int u=0;u<16;u++)if(visible[v][u]){
                int width=1,h=1;while(u+width<16&&visible[v][u+width])width++;
                outer:while(v+h<16){for(int i=0;i<width;i++)if(!visible[v+h][u+i])break outer;h++;}
                for(int j=0;j<h;j++)for(int i=0;i<width;i++)visible[v+j][u+i]=false;
                int plane=slice+(d%2==1?1:0);int[][] q=switch(d){
                    case 0->new int[][]{{u,plane,v+h},{u,plane,v},{u+width,plane,v},{u+width,plane,v+h}};
                    case 1->new int[][]{{u,plane,v},{u,plane,v+h},{u+width,plane,v+h},{u+width,plane,v}};
                    case 2->new int[][]{{u+width,v+h,plane},{u+width,v,plane},{u,v,plane},{u,v+h,plane}};
                    case 3->new int[][]{{u,v+h,plane},{u,v,plane},{u+width,v,plane},{u+width,v+h,plane}};
                    case 4->new int[][]{{plane,v+h,u},{plane,v,u},{plane,v,u+width},{plane,v+h,u+width}};
                    default->new int[][]{{plane,v+h,u+width},{plane,v,u+width},{plane,v,u},{plane,v+h,u}};};
                var points=new ArrayList<HybridMesh.Point>(4);for(var p:q)points.add(new HybridMesh.Point(p[0]/16d,Math.min(p[1]/16d,height),p[2]/16d));result.add(new Quad(d,points));
            }
        }
        return List.copyOf(result);
    }
}
