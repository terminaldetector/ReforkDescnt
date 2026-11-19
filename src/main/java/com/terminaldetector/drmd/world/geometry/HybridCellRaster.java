package com.terminaldetector.drmd.world.geometry;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import com.terminaldetector.drmd.world.geometry.HybridMesh.Point;

/** Fixed-domain conservative voxelization of the SAME quarter-cell face triangles used by the mesh.
 * Twelve centre-to-face tetrahedra close each cell; diagonal/curved clearance is not filled by its AABB.
 * This remains a1/16 collider, not an exact triangle-contact engine. */
public final class HybridCellRaster {
    public static final int SIZE=32;
    public static final double MIN=-.5,PITCH=1d/16,MAX=1.5;
    private static final int[][] QUADS={{2,0,1,3},{4,6,7,5},{5,1,0,4},{6,2,3,7},{4,0,2,6},{7,3,1,5}};
    private HybridCellRaster(){}
    public static int index(int x,int y,int z){return (y*SIZE+z)*SIZE+x;}
    public static BitSet raster(List<HybridMesh.Cell> cells) {
        if(cells.size()>64)throw new IllegalArgumentException("quarter cell cap64");
        var result=new BitSet(SIZE*SIZE*SIZE);
        for(var cell:cells) {
            var points=cell.vertices();double x0=MAX,y0=MAX,z0=MAX,x1=MIN,y1=MIN,z1=MIN;Point centre=new Point(0,0,0);
            for(var p:points){check(p);x0=Math.min(x0,p.x());x1=Math.max(x1,p.x());y0=Math.min(y0,p.y());y1=Math.max(y1,p.y());z0=Math.min(z0,p.z());z1=Math.max(z1,p.z());centre=plus(centre,p);}
            centre=times(centre,.125);int ax=lower(x0),ay=lower(y0),az=lower(z0),bx=upper(x1),by=upper(y1),bz=upper(z1);
            boolean aligned=axisAligned(points,x0,y0,z0,x1,y1,z1);var tetrahedra=new ArrayList<Tetra>(12);
            if(!aligned)for(var q:QUADS){add(tetrahedra,centre,points.get(q[0]),points.get(q[1]),points.get(q[2]));add(tetrahedra,centre,points.get(q[2]),points.get(q[3]),points.get(q[0]));}
            for(int y=ay;y<by;y++)for(int z=az;z<bz;z++)for(int x=ax;x<bx;x++) {
                int at=index(x,y,z);if(result.get(at))continue;
                var mid=new Point(MIN+(x+.5)*PITCH,MIN+(y+.5)*PITCH,MIN+(z+.5)*PITCH);
                boolean hit=aligned;
                if(!hit)for(var tetra:tetrahedra)if(tetra.intersects(mid)){hit=true;break;}
                if(hit)result.set(at);
            }
        }
        return result;
    }
    private static boolean axisAligned(List<Point> p,double x0,double y0,double z0,double x1,double y1,double z1){
        for(int y=0;y<2;y++)for(int z=0;z<2;z++)for(int x=0;x<2;x++){
            var v=p.get((y*2+z)*2+x);if(v.x()!=(x==0?x0:x1)||v.y()!=(y==0?y0:y1)||v.z()!=(z==0?z0:z1))return false;
        }return true;
    }
    private static void add(List<Tetra> out,Point a,Point b,Point c,Point d){
        double volume=dot(minus(b,a),cross(minus(c,a),minus(d,a)));if(Math.abs(volume)>1e-15)out.add(new Tetra(new Point[]{a,b,c,d}));
    }
    private record Axis(Point normal,double low,double high,double radius){}
    private static final class Tetra {
        private final List<Axis> axes=new ArrayList<>(25);
        Tetra(Point[] p){
            axis(p,new Point(1,0,0));axis(p,new Point(0,1,0));axis(p,new Point(0,0,1));
            for(int a=0;a<4;a++)for(int b=a+1;b<4;b++){
                var edge=minus(p[b],p[a]);axis(p,new Point(0,edge.z(),-edge.y()));axis(p,new Point(-edge.z(),0,edge.x()));axis(p,new Point(edge.y(),-edge.x(),0));
            }
            for(int a=0;a<4;a++)for(int b=a+1;b<4;b++)for(int c=b+1;c<4;c++)axis(p,cross(minus(p[b],p[a]),minus(p[c],p[a])));
        }
        private void axis(Point[] points,Point normal){
            double scale=Math.max(Math.abs(normal.x()),Math.max(Math.abs(normal.y()),Math.abs(normal.z())));if(scale<1e-15)return;
            normal=times(normal,1/scale);double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(var p:points){double v=dot(normal,p);low=Math.min(low,v);high=Math.max(high,v);}
            axes.add(new Axis(normal,low,high,PITCH*.5*(Math.abs(normal.x())+Math.abs(normal.y())+Math.abs(normal.z()))));
        }
        boolean intersects(Point midpoint){
            for(var axis:axes){double centre=dot(axis.normal,midpoint);if(centre+axis.radius<=axis.low+1e-12||centre-axis.radius>=axis.high-1e-12)return false;}
            return true;
        }
    }
    private static int lower(double v){return (int)Math.floor((v-MIN)/PITCH);}
    private static int upper(double v){return (int)Math.ceil((v-MIN)/PITCH);}
    private static void check(Point p){if(!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||p.x()<MIN||p.y()<MIN||p.z()<MIN||p.x()>MAX||p.y()>MAX||p.z()>MAX)throw new IllegalArgumentException("hybrid vertex outside bounded collider");}
    private static Point plus(Point a,Point b){return new Point(a.x()+b.x(),a.y()+b.y(),a.z()+b.z());}
    private static Point minus(Point a,Point b){return new Point(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    private static Point times(Point a,double t){return new Point(a.x()*t,a.y()*t,a.z()*t);}
    private static Point cross(Point a,Point b){return new Point(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
    private static double dot(Point a,Point b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
}
