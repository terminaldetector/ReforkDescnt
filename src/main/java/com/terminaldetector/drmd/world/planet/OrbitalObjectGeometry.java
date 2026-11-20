package com.terminaldetector.drmd.world.planet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** One original authored voxel definition shared by real near blocks and bounded mid/far proxies. */
public final class OrbitalObjectGeometry {
    public static final int VERSION=1,MAX_VOXELS=8192,MAX_BOXES=1024;
    public enum Shape { STATION,ASTEROID }
    public enum Tier { NEAR,MID,FAR }
    public record Voxel(int x,int y,int z,String state){}
    /** Local half-open cell coordinates. World origin is catalogue centre minus half its exact extent. */
    public record Box(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,int colorRgb){
        public Box{if(minX>=maxX||minY>=maxY||minZ>=maxZ)throw new IllegalArgumentException("empty orbital box");}
        public int volume(){return (maxX-minX)*(maxY-minY)*(maxZ-minZ);}
    }
    private static final String ROCK="minecraft:stone",METAL="minecraft:iron_block";
    private OrbitalObjectGeometry(){}
    public static boolean supports(Shape shape,int sx,int sy,int sz){
        if(shape==null||sx<8||sy<8||sz<8||(sx&1)!=0||(sy&1)!=0||(sz&1)!=0)return false;
        return shape==Shape.ASTEROID?sx<=24&&sy<=24&&sz<=24:sx>=16&&sy>=12&&sz>=16&&sx<=32&&sy<=32&&sz<=32;
    }
    /** A real sample just outside the station's open central shaft, on the lower deck. */
    public static Voxel stationDeckSample(int sx,int sy,int sz){
        if(!supports(Shape.STATION,sx,sy,sz))throw new IllegalArgumentException("unsupported original station dimensions");
        int x=sx/2+2,y=sy/4,z=sz/2;
        return new Voxel(x,y,z,Objects.requireNonNull(material(Shape.STATION,x,y,z,sx,sy,sz)));
    }
    public static List<Voxel> cells(Shape shape,int sx,int sy,int sz){
        if(!supports(shape,sx,sy,sz))throw new IllegalArgumentException("unsupported original orbital object dimensions");
        var out=new ArrayList<Voxel>();
        for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)for(int x=0;x<sx;x++){
            String material=material(shape,x,y,z,sx,sy,sz);if(material!=null){if(out.size()>=MAX_VOXELS)throw new IllegalArgumentException("orbital voxel cap8192");out.add(new Voxel(x,y,z,material));}
        }
        return List.copyOf(out);
    }
    private static String material(Shape shape,int x,int y,int z,int sx,int sy,int sz){
        if(shape==Shape.ASTEROID){
            long a=sx*sx,b=sy*sy,c=sz*sz,dx=2L*x+1-sx,dy=2L*y+1-sy,dz=2L*z+1-sz;
            return dx*dx*b*c+dy*dy*a*c+dz*dz*a*b<=a*b*c?ROCK:null;
        }
        int low=sy/4,high=sy*3/4;
        boolean shaft=Math.abs(2*x+1-sx)<=4&&Math.abs(2*z+1-sz)<=4;
        boolean floor=(y==low||y==high)&&x>=1&&x<sx-1&&z>=1&&z<sz-1&&!shaft;
        boolean corner=(x==1||x==sx-2)&&(z==1||z==sz-2)&&y>=low&&y<sy-1;
        boolean rim=(x==1||x==sx-2||z==1||z==sz-2)&&(y==low+3||y==sy-2)&&x>=1&&x<sx-1&&z>=1&&z<sz-1;
        boolean dock=y==low&&x>=sx/2-2&&x<sx/2+2&&!shaft;
        boolean door=Math.abs(2*x+1-sx)<=4&&y>low&&y<low+4;
        return (floor||corner||rim||dock)&&!door?METAL:null;
    }
    private static int color(String material){return ROCK.equals(material)?0x70777d:0xa8b6bc;}
    public static List<Box> boxes(Shape shape,int sx,int sy,int sz,Tier tier){
        Objects.requireNonNull(tier);var voxels=cells(shape,sx,sy,sz);
        if(tier==Tier.FAR)return far(voxels);
        var data=new String[sx][sy][sz];var used=new boolean[sx][sy][sz];for(var v:voxels)data[v.x()][v.y()][v.z()]=v.state();
        var out=new ArrayList<Box>();
        for(var v:voxels){int x=v.x(),y=v.y(),z=v.z();if(used[x][y][z])continue;String state=data[x][y][z];int ex=x+1,ez=z+1,ey=y+1;
            if(tier==Tier.MID){
                while(ex<sx&&!used[ex][y][z]&&state.equals(data[ex][y][z]))ex++;
                while(ez<sz&&same(data,used,state,x,ex,y,y+1,ez,ez+1))ez++;
                while(ey<sy&&same(data,used,state,x,ex,ey,ey+1,z,ez))ey++;
            }
            for(int a=x;a<ex;a++)for(int b=y;b<ey;b++)for(int c=z;c<ez;c++)used[a][b][c]=true;
            out.add(new Box(x,y,z,ex,ey,ez,color(state)));
        }
        if(tier!=Tier.NEAR&&out.size()>MAX_BOXES)throw new IllegalArgumentException("orbital proxy box cap1024");return List.copyOf(out);
    }
    private static boolean same(String[][][] data,boolean[][][] used,String state,int x0,int x1,int y0,int y1,int z0,int z1){
        for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)if(used[x][y][z]||!state.equals(data[x][y][z]))return false;return true;
    }
    private static List<Box> far(List<Voxel> voxels){
        var buckets=new LinkedHashMap<String,int[]>();
        for(var v:voxels){var key=v.x()/4+":"+v.y()/4+":"+v.z()/4;var a=buckets.computeIfAbsent(key,ignored->new int[]{v.x(),v.y(),v.z(),v.x()+1,v.y()+1,v.z()+1,color(v.state())});
            a[0]=Math.min(a[0],v.x());a[1]=Math.min(a[1],v.y());a[2]=Math.min(a[2],v.z());a[3]=Math.max(a[3],v.x()+1);a[4]=Math.max(a[4],v.y()+1);a[5]=Math.max(a[5],v.z()+1);
        }
        if(buckets.size()>MAX_BOXES)throw new IllegalArgumentException("orbital far box cap1024");
        return buckets.values().stream().map(a->new Box(a[0],a[1],a[2],a[3],a[4],a[5],a[6])).toList();
    }
}
