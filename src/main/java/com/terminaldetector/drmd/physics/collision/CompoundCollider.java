package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import java.util.*;

/** One rigid body's immutable local geometry. Greedy cuboids preserve holes; BVH is built on shape changes. */
public final class CompoundCollider {
    public static final int MAX_CELLS=32768;
    public record Cell(int x,int y,int z) {}
    public record RayHit(double fraction,Vec3 point,Vec3 normal,boolean inside) {}
    private record Node(BodyBounds bounds,Node left,Node right,int box) {}
    private final List<BodyBounds> boxes;
    private final Node root;
    public CompoundCollider(Collection<BodyBounds> bounds) {
        if(bounds.size()>MAX_CELLS)throw new IllegalArgumentException("collider geometry budget");
        boxes=List.copyOf(bounds);var ids=new ArrayList<Integer>();for(int i=0;i<boxes.size();i++)ids.add(i);
        root=build(ids);
    }
    public List<BodyBounds> boxes(){return boxes;}
    public Optional<BodyBounds> bounds(){return root==null?Optional.empty():Optional.of(root.bounds);}
    public static CompoundCollider cells(Collection<Cell> source) {
        if(source.size()>MAX_CELLS)throw new IllegalArgumentException("collider cell budget");
        var remaining=new TreeSet<Cell>(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z));
        for(Cell c:source) {
            if(Math.abs((long)c.x)>8192 || Math.abs((long)c.y)>8192 || Math.abs((long)c.z)>8192 || !remaining.add(c))
                throw new IllegalArgumentException("duplicate or out-of-bounds collider cell");
        }
        var boxes=new ArrayList<BodyBounds>();
        while(!remaining.isEmpty()) {
            Cell c=remaining.first();int x1=c.x+1,y1=c.y+1,z1=c.z+1;
            while(remaining.contains(new Cell(x1,c.y,c.z)))x1++;
            while(plane(remaining,c.x,x1,y1,y1+1,c.z))y1++;
            while(plane(remaining,c.x,x1,c.y,y1,z1))z1++;
            for(int x=c.x;x<x1;x++)for(int y=c.y;y<y1;y++)for(int z=c.z;z<z1;z++)remaining.remove(new Cell(x,y,z));
            boxes.add(new BodyBounds(new Vec3(c.x,c.y,c.z),new Vec3(x1,y1,z1)));
        }
        return new CompoundCollider(boxes);
    }
    private static boolean plane(Set<Cell> cells,int x0,int x1,int y0,int y1,int z) {
        for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)if(!cells.contains(new Cell(x,y,z)))return false;
        return true;
    }
    private Node build(List<Integer> ids) {
        if(ids.isEmpty())return null;
        BodyBounds bounds=boxes.get(ids.get(0));for(int i=1;i<ids.size();i++)bounds=bounds.union(boxes.get(ids.get(i)));
        if(ids.size()==1)return new Node(bounds,null,null,ids.get(0));
        Vec3 size=bounds.max().minus(bounds.min());int axis=size.x()>=size.y()&&size.x()>=size.z()?0:size.y()>=size.z()?1:2;
        ids.sort(Comparator.<Integer>comparingDouble(i->BodyBounds.component(boxes.get(i).centre(),axis)).thenComparingInt(i->i));
        int mid=ids.size()/2;
        return new Node(bounds,build(new ArrayList<>(ids.subList(0,mid))),build(new ArrayList<>(ids.subList(mid,ids.size()))),-1);
    }
    public List<BodyBounds> query(BodyBounds area) {
        var hits=new ArrayList<BodyBounds>();query(root,area,hits);return List.copyOf(hits);
    }
    private void query(Node node,BodyBounds area,List<BodyBounds> hits) {
        if(node==null || !node.bounds.intersects(area))return;
        if(node.box>=0)hits.add(boxes.get(node.box));else{query(node.left,area,hits);query(node.right,area,hits);}
    }
    public Optional<RayHit> raycast(Vec3 from,Vec3 to) {
        BodyBounds.finite(from);BodyBounds.finite(to);
        RayHit[] best={null};ray(root,from,to.minus(from),best);return Optional.ofNullable(best[0]);
    }
    private void ray(Node node,Vec3 from,Vec3 delta,RayHit[] best) {
        if(node==null)return;
        var boundsHit=rayBox(node.bounds,from,delta);
        if(boundsHit==null || best[0]!=null && boundsHit.fraction>=best[0].fraction)return;
        if(node.box>=0)best[0]=boundsHit;else{ray(node.left,from,delta,best);ray(node.right,from,delta,best);}
    }
    public static Optional<RayHit> raycastBox(BodyBounds bounds,Vec3 from,Vec3 to) {
        BodyBounds.finite(from);BodyBounds.finite(to);return Optional.ofNullable(rayBox(bounds,from,to.minus(from)));
    }
    private static RayHit rayBox(BodyBounds b,Vec3 from,Vec3 delta) {
        double enter=0,exit=1;Vec3 normal=new Vec3(0,0,0);boolean inside=true;
        for(int axis=0;axis<3;axis++) {
            double p=BodyBounds.component(from,axis),v=BodyBounds.component(delta,axis),lo=BodyBounds.component(b.min(),axis),hi=BodyBounds.component(b.max(),axis);
            inside &= p>=lo && p<=hi;
            if(Math.abs(v)<1e-12){if(p<lo || p>hi)return null;continue;}
            double near=(lo-p)/v,far=(hi-p)/v,sign=v>0?-1:1;
            if(near>far){double swap=near;near=far;far=swap;}
            if(near>enter){enter=near;normal=axis==0?new Vec3(sign,0,0):axis==1?new Vec3(0,sign,0):new Vec3(0,0,sign);}
            exit=Math.min(exit,far);if(enter>exit)return null;
        }
        return new RayHit(enter,from.plus(delta.scaled(enter)),inside?new Vec3(0,0,0):normal,inside);
    }
}
