package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.world.portal.mirror.MirrorEdges;
import net.minecraft.util.math.*;
import java.util.*;

/** Edge-connected, coplanar panes share one camera/render, including non-rectangular surfaces. */
public final class MirrorSurface {
    private final List<MirrorScanner.MirrorFace> panes;
    private final List<PortalTransform.Vec3[]> apertures;
    public MirrorSurface(List<MirrorScanner.MirrorFace> panes) {
        this.panes=List.copyOf(panes);this.apertures=buildApertures();
    }
    public List<MirrorScanner.MirrorFace> panes() {return panes;}
    public List<PortalTransform.Vec3[]> apertures() {return apertures;}
    public MirrorScanner.MirrorFace anchor() {return panes.getFirst();}
    public static List<MirrorSurface> join(List<MirrorScanner.MirrorFace> faces) {
        Map<BlockPos,MirrorScanner.MirrorFace> remaining=new HashMap<>();
        for(var face:faces) remaining.put(face.pos(),face);
        var result=new ArrayList<MirrorSurface>();
        // Stable across hash/chunk map iteration and after a split/reload.
        var ordered=new ArrayList<>(faces);
        ordered.sort(Comparator.comparingInt((MirrorScanner.MirrorFace f)->f.pos().getX())
                .thenComparingInt(f->f.pos().getY()).thenComparingInt(f->f.pos().getZ()));
        for(var seed:ordered) {
            if(remaining.remove(seed.pos())==null)continue;
            var panes=new ArrayList<MirrorScanner.MirrorFace>();
            var pending=new ArrayDeque<MirrorScanner.MirrorFace>();pending.add(seed);
            while(!pending.isEmpty()) {
                var face=pending.removeFirst();panes.add(face);
                for(var direction:MirrorEdges.directions(seed.facing())) {
                    var neighbour=remaining.get(face.pos().offset(direction));
                    if(neighbour!=null && neighbour.facing()==seed.facing() && neighbour.kind()==seed.kind()) {
                        remaining.remove(neighbour.pos());pending.addLast(neighbour);
                    }
                }
            }
            result.add(new MirrorSurface(panes));
        }
        return List.copyOf(result);
    }
    public double squaredDistance(Vec3d camera) {
        double distance=Double.POSITIVE_INFINITY;
        for(var pane:panes)distance=Math.min(distance,camera.squaredDistanceTo(pane.planePoint()));
        return distance;
    }
    /** Each pane reaches its joined neighbours; only boundary edges retain a 2/16 frame. */
    private List<PortalTransform.Vec3[]> buildApertures() {
        var positions=new HashSet<BlockPos>();for(var pane:panes)positions.add(pane.pos());
        var result=new ArrayList<PortalTransform.Vec3[]>();
        for(var pane:panes) {
            var d=MirrorEdges.directions(pane.facing());
            double loU=positions.contains(pane.pos().offset(d[0]))?-.5:-.375;
            double hiU=positions.contains(pane.pos().offset(d[1]))?.5:.375;
            double loV=positions.contains(pane.pos().offset(d[2]))?-.5:-.375;
            double hiV=positions.contains(pane.pos().offset(d[3]))?.5:.375;
            var u=Vec3d.of(d[1].getVector());var v=Vec3d.of(d[3].getVector());
            // Just in front of the backing face, behind the frame.
            var c=pane.planePoint().add(pane.normal().multiply(.002));
            result.add(new PortalTransform.Vec3[]{point(c,u,v,loU,loV),point(c,u,v,hiU,loV),
                    point(c,u,v,hiU,hiV),point(c,u,v,loU,hiV)});
        }
        return result;
    }
    private static PortalTransform.Vec3 point(Vec3d c,Vec3d u,Vec3d v,double a,double b) {
        var p=c.add(u.multiply(a)).add(v.multiply(b));return new PortalTransform.Vec3(p.x,p.y,p.z);
    }
}
