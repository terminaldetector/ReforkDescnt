package com.terminaldetector.drmd.world.geometry;

import com.terminaldetector.drmd.world.micro.MicroGrid;
import java.util.ArrayList;
import java.util.List;

/** Shared surface-net corner field, interpolated onto the quarter-cell damage lattice. */
public final class HybridMesh {
    public record Block(long mask, boolean organic, boolean anchor, boolean occludes) {
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
    private final Point[] vertices = new Point[125];

    public HybridMesh(Neighbourhood neighbours) {
        this.neighbours = neighbours;
        self = neighbours.at(0,0,0);
        Point[] corners = new Point[8];
        for (int y=0;y<2;y++) for (int z=0;z<2;z++) for (int x=0;x<2;x++)
            corners[(y*2+z)*2+x] = self.organic ? cornerOffset(x,y,z) : ZERO;
        for (int y=0;y<=4;y++) for (int z=0;z<=4;z++) for (int x=0;x<=4;x++) {
            double px=x*.25, py=y*.25, pz=z*.25;
            Point offset=ZERO;
            for (int cy=0;cy<2;cy++) for (int cz=0;cz<2;cz++) for (int cx=0;cx<2;cx++) {
                double weight=(cx==0?1-px:px)*(cy==0?1-py:py)*(cz==0?1-pz:pz);
                offset=offset.plus(corners[(cy*2+cz)*2+cx].times(weight));
            }
            vertices[index(x,y,z)]=new Point(px,py,pz).plus(offset);
        }
    }

    /** Same eight world blocks choose the offset on either side of any section boundary. */
    private Point cornerOffset(int x, int y, int z) {
        boolean[] solid=new boolean[8];
        for (int dy=0;dy<2;dy++) for (int dz=0;dz<2;dz++) for (int dx=0;dx<2;dx++) {
            Block b=neighbours.at(x+dx-1,y+dy-1,z+dz-1);
            if (b.anchor) return ZERO;
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
                if (!self.organic) {
                    while (u+width<4 && visible[v][u+width]) width++;
                    outer: while (v+height<4) {
                        for (int i=0;i<width;i++) if (!visible[v+height][u+i]) break outer;
                        height++;
                    }
                }
                for (int j=0;j<height;j++) for (int i=0;i<width;i++) visible[v+j][u+i]=false;
                int plane=slice+(d%2==1?1:0);
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
