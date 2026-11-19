package com.terminaldetector.drmd.world.geometry;

import com.terminaldetector.drmd.world.micro.MicroGrid;
import java.util.ArrayList;
import java.util.List;

/** Shared surface-net corner field, interpolated onto the quarter-cell damage lattice. */
public final class HybridMesh {
    public enum LogAxis {NONE, X, Y, Z}
    public record Block(long mask,boolean organic,boolean anchor,boolean occludes,List<com.terminaldetector.drmd.world.micro.ThermalBore> bores,LogAxis logAxis) {
        public Block(long mask,boolean organic,boolean anchor,boolean occludes){this(mask,organic,anchor,occludes,List.of(),LogAxis.NONE);}
        public Block(long mask,boolean organic,boolean anchor,boolean occludes,List<com.terminaldetector.drmd.world.micro.ThermalBore> bores){this(mask,organic,anchor,occludes,bores,LogAxis.NONE);}
        public Block{bores=List.copyOf(bores);java.util.Objects.requireNonNull(logAxis);if(bores.size()>2)throw new IllegalArgumentException("thermal shape budget");}
        public static final Block AIR = new Block(0, false, false, false);
    }
    public record Neighbourhood(List<Block> blocks) {
        public Neighbourhood {
            if (blocks.size() != 27) throw new IllegalArgumentException("Expected 3x3x3 blocks");
            blocks = List.copyOf(blocks);
        }
        public Block at(int x, int y, int z) { return blocks.get(((y+1)*3+z+1)*3+x+1); }
    }
    public record Point(double x, double y, double z) {
        Point plus(Point b) { return new Point(x+b.x,y+b.y,z+b.z); }
        Point times(double a) { return new Point(x*a,y*a,z*a); }
    }
    public record Vertex(Point position, Point texturePosition) {}
    /** Direction order: DOWN, UP, NORTH, SOUTH, WEST, EAST. Outward winding. */
    public record Face(int direction, List<Vertex> vertices) {
        public Face { vertices = List.copyOf(vertices); }
    }
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    private static final Point ZERO = new Point(0,0,0);
    private static final int[][] STEPS = {{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    private final Neighbourhood neighbours;
    private final Block self;
    private final boolean thermal, axisAware;
    private final Point[] vertices = new Point[125];

    public HybridMesh(Neighbourhood neighbours) {
        this.neighbours = neighbours;
        self = neighbours.at(0,0,0);
        thermal=neighbours.blocks.stream().anyMatch(b->!b.bores.isEmpty());
        axisAware=self.organic&&neighbours.blocks.stream().anyMatch(b->b.logAxis!=LogAxis.NONE);
        Point[] corners = new Point[8];
        for (int y=0;y<2;y++) for (int z=0;z<2;z++) for (int x=0;x<2;x++)
            corners[(y*2+z)*2+x] = self.organic&&self.bores.isEmpty() ? cornerOffset(x,y,z) : ZERO;
        for (int y=0;y<=4;y++) for (int z=0;z<=4;z++) for (int x=0;x<=4;x++) {
            double px=x*.25, py=y*.25, pz=z*.25;
            Point offset=ZERO;
            for (int cy=0;cy<2;cy++) for (int cz=0;cz<2;cz++) for (int cx=0;cx<2;cx++) {
                double weight=(cx==0?1-px:px)*(cy==0?1-py:py)*(cz==0?1-pz:pz);
                offset=offset.plus(corners[(cy*2+cz)*2+cx].times(weight));
            }
            Point point=new Point(px,py,pz).plus(offset);
            if(axisAware) {
                Point rounded=logVertex(new Point(px,py,pz),x,y,z);
                if(rounded!=null)point=rounded;
            }
            vertices[index(x,y,z)]=thermal?thermalVertex(point,x,y,z):point;
        }
    }

    /** The same incident log axes choose a shared point on either side of a seam, including
     * perpendicular branches. Only cross-sections contract; a straight trunk keeps its length.
     * Engineered anchors and thermal cells pin their interface; bore projection stays authoritative. */
    private Point logVertex(Point p,int x,int y,int z) {
        Point best=null;double distance=Double.POSITIVE_INFINITY;boolean pinned=false;
        for(int dy=y==0?-1:0;dy<=(y==4?1:0);dy++)for(int dz=z==0?-1:0;dz<=(z==4?1:0);dz++)for(int dx=x==0?-1:0;dx<=(x==4?1:0);dx++) {
            var block=neighbours.at(dx,dy,dz);pinned|=block.anchor||!block.bores.isEmpty();
            if(block.logAxis==LogAxis.NONE)continue;
            double a,b;
            if(block.logAxis==LogAxis.X){a=p.y-dy-.5;b=p.z-dz-.5;}
            else if(block.logAxis==LogAxis.Y){a=p.x-dx-.5;b=p.z-dz-.5;}
            else {a=p.x-dx-.5;b=p.y-dy-.5;}
            // Square-to-disk cross-section on the existing quarter lattice: radius 3/8 m.
            double ca=.75*a*Math.sqrt(Math.max(0,1-2*b*b));
            double cb=.75*b*Math.sqrt(Math.max(0,1-2*a*a));
            Point candidate=block.logAxis==LogAxis.X?new Point(p.x,dy+.5+ca,dz+.5+cb)
                :block.logAxis==LogAxis.Y?new Point(dx+.5+ca,p.y,dz+.5+cb):new Point(dx+.5+ca,dy+.5+cb,p.z);
            double d=squared(candidate,p);
            if(best==null||d<distance-1e-12||Math.abs(d-distance)<=1e-12&&before(candidate,best)) {best=candidate;distance=d;}
        }
        return best==null?null:pinned?p:best;
    }
    private static double squared(Point a,Point b){double x=a.x-b.x,y=a.y-b.y,z=a.z-b.z;return x*x+y*y+z*z;}
    private static boolean before(Point a,Point b) {
        return a.x<b.x-1e-12||Math.abs(a.x-b.x)<=1e-12&&(a.y<b.y-1e-12||Math.abs(a.y-b.y)<=1e-12&&a.z<b.z);
    }
    /** Nonlinear cross-sections need their shared cell bounds, rather than one corner-only box. */
    public boolean axisAware(){return axisAware;}

    /** Both sides of a block seam use the same incident cells and union field. Untouched engineered anchors remain sharp. */
    private Point thermalVertex(Point p,int x,int y,int z){
        com.terminaldetector.drmd.world.micro.ThermalBore nearest=null;double signed=Double.POSITIVE_INFINITY;int ox=0,oy=0,oz=0;
        for(int dy=y==0?-1:0;dy<=(y==4?1:0);dy++)for(int dz=z==0?-1:0;dz<=(z==4?1:0);dz++)for(int dx=x==0?-1:0;dx<=(x==4?1:0);dx++){
            var block=neighbours.at(dx,dy,dz);if(block.anchor&&block.bores.isEmpty())return p;
            for(var bore:block.bores){double d=bore.distance(p.x-dx,p.y-dy,p.z-dz)-bore.radius();if(d<signed){signed=d;nearest=bore;ox=dx;oy=dy;oz=dz;}}
        }
        if(nearest==null||Math.abs(signed)>com.terminaldetector.drmd.world.micro.ThermalBore.SHELL)return p;
        var q=nearest.project(p.x-ox,p.y-oy,p.z-oz);return new Point(q.x()+ox,q.y()+oy,q.z()+oz);
    }

    /** Same eight world blocks choose the offset on either side of any section boundary. */
    private Point cornerOffset(int x, int y, int z) {
        boolean[] solid=new boolean[8];
        for (int dy=0;dy<2;dy++) for (int dz=0;dz<2;dz++) for (int dx=0;dx<2;dx++) {
            Block b=neighbours.at(x+dx-1,y+dy-1,z+dz-1);
            if (b.anchor||!b.bores.isEmpty()) return ZERO;
            solid[(dy*2+dz)*2+dx]=b.mask!=0;
        }
        Point sum=ZERO; int count=0;
        for (int dy=0;dy<2;dy++) for (int dz=0;dz<2;dz++) for (int dx=0;dx<2;dx++) {
            int i=(dy*2+dz)*2+dx;
            if (dx==0 && solid[i]!=solid[i+1]) {sum=sum.plus(new Point(0,dy-.5,dz-.5));count++;}
            if (dy==0 && solid[i]!=solid[i+4]) {sum=sum.plus(new Point(dx-.5,0,dz-.5));count++;}
            if (dz==0 && solid[i]!=solid[i+2]) {sum=sum.plus(new Point(dx-.5,dy-.5,0));count++;}
        }
        return count==0?ZERO:sum.times(.75/count);
    }
    private static int index(int x,int y,int z) { return (y*5+z)*5+x; }
    public Point vertex(int x,int y,int z) { return vertices[index(x,y,z)]; }

    private boolean occupied(int x,int y,int z) {
        if (MicroGrid.inRange(x,y,z)) return MicroGrid.get(self.mask,x,y,z);
        Block b=neighbours.at(Math.floorDiv(x,4),Math.floorDiv(y,4),Math.floorDiv(z,4));
        return b.occludes && MicroGrid.get(b.mask,Math.floorMod(x,4),Math.floorMod(y,4),Math.floorMod(z,4));
    }

    public List<Face> faces() {
        List<Face> out=new ArrayList<>();
        for (int d=0;d<6;d++) for (int slice=0;slice<4;slice++) {
            boolean[][] visible=new boolean[4][4];
            for (int v=0;v<4;v++) for (int u=0;u<4;u++) {
                int x=d<4?u:slice, y=d<2?slice:v, z=d<2?v:d<4?slice:u;
                visible[v][u]=MicroGrid.get(self.mask,x,y,z) && !occupied(x+STEPS[d][0],y+STEPS[d][1],z+STEPS[d][2]);
            }
            for (int v=0;v<4;v++) for (int u=0;u<4;u++) if (visible[v][u]) {
                int width=1,height=1;
                int plane=slice+(d%2==1?1:0);
                // Thermal projection is nonlinear even on engineered material: never replace its
                // actual rim/crease with a large quad whose interior skips the shared vertices.
                boolean featureAware=axisAware||thermal;
                if (!self.organic || planar(d,slice) || featureAware) {
                    while (u+width<4 && visible[v][u+width]
                        &&(!featureAware||mergePatch(d,plane,u,v,width+1,height))) width++;
                    outer: while (v+height<4) {
                        for (int i=0;i<width;i++) if (!visible[v+height][u+i]) break outer;
                        if(featureAware&&!mergePatch(d,plane,u,v,width,height+1))break;
                        height++;
                    }
                }
                for (int j=0;j<height;j++) for (int i=0;i<width;i++) visible[v+j][u+i]=false;
                int[][] p=switch(d) {
                    case 0 -> new int[][]{{u,plane,v+height},{u,plane,v},{u+width,plane,v},{u+width,plane,v+height}};
                    case 1 -> new int[][]{{u,plane,v},{u,plane,v+height},{u+width,plane,v+height},{u+width,plane,v}};
                    case 2 -> new int[][]{{u+width,v+height,plane},{u+width,v,plane},{u,v,plane},{u,v+height,plane}};
                    case 3 -> new int[][]{{u,v+height,plane},{u,v,plane},{u+width,v,plane},{u+width,v+height,plane}};
                    case 4 -> new int[][]{{plane,v+height,u},{plane,v,u},{plane,v,u+width},{plane,v+height,u+width}};
                    default -> new int[][]{{plane,v+height,u+width},{plane,v,u+width},{plane,v,u},{plane,v+height,u}};
                };
                List<Vertex> q=new ArrayList<>(4);
                for (int[] c:p) q.add(new Vertex(vertex(c[0],c[1],c[2]),new Point(c[0]*.25,c[1]*.25,c[2]*.25)));
                out.add(new Face(d,q));
            }
        }
        return List.copyOf(out);
    }

    /** Merge only a genuinely planar patch whose intermediate points lie on the same quad.
     * Coplanarity alone would replace a round end-cap boundary with its inscribed square. */
    private boolean mergePatch(int d,int plane,int u,int v,int width,int height) {
        Point a=facePoint(d,plane,u,v),b=facePoint(d,plane,u+width,v),c=facePoint(d,plane,u,v+height),e=facePoint(d,plane,u+width,v+height);
        double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        if(nx*nx+ny*ny+nz*nz<1e-16||Math.abs(nx*(e.x-a.x)+ny*(e.y-a.y)+nz*(e.z-a.z))>1e-10)return false;
        for(int j=0;j<=height;j++)for(int i=0;i<=width;i++) {
            double s=(double)i/width,t=(double)j/height;
            Point expected=a.times((1-s)*(1-t)).plus(b.times(s*(1-t))).plus(c.times((1-s)*t)).plus(e.times(s*t));
            if(squared(expected,facePoint(d,plane,u+i,v+j))>1e-20)return false;
        }
        return true;
    }

    /** Flat slopes and intact ground need one quad, not sixteen identical planar patches. */
    private boolean planar(int direction,int slice) {
        int plane=slice+(direction%2==1?1:0);
        Point a=facePoint(direction,plane,0,0),b=facePoint(direction,plane,4,0),c=facePoint(direction,plane,0,4);
        double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        if(nx*nx+ny*ny+nz*nz<1e-16)return false;
        for(int v=0;v<=4;v++)for(int u=0;u<=4;u++) {
            Point p=facePoint(direction,plane,u,v);
            if(Math.abs(nx*(p.x-a.x)+ny*(p.y-a.y)+nz*(p.z-a.z))>1e-10)return false;
        }
        return true;
    }
    private Point facePoint(int d,int plane,int u,int v) {
        return d<2?vertex(u,plane,v):d<4?vertex(u,v,plane):vertex(plane,v,u);
    }

    /** Conservative cell AABBs of the same warped volume; not exact triangle collision. */
    public List<Bounds> collisionBounds() {
        List<Bounds> out=new ArrayList<>();
        for (int y=0;y<4;y++) for (int z=0;z<4;z++) for (int x=0;x<4;x++) if (MicroGrid.get(self.mask,x,y,z)) {
            double minX=2,minY=2,minZ=2,maxX=-1,maxY=-1,maxZ=-1;
            for (int dy=0;dy<2;dy++) for (int dz=0;dz<2;dz++) for (int dx=0;dx<2;dx++) {
                Point p=vertex(x+dx,y+dy,z+dz);
                minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); minZ=Math.min(minZ,p.z);
                maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); maxZ=Math.max(maxZ,p.z);
            }
            if (maxX>minX && maxY>minY && maxZ>minZ) out.add(new Bounds(minX,minY,minZ,maxX,maxY,maxZ));
        }
        return List.copyOf(out);
    }
}
