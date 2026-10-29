package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.*;

/** Actual common contact patches for OBBs. A face centre alone cannot constrain rotation:
 * another corner can close while that centre is already separating. Work relative to a
 * reference face to retain precision at large world coordinates. */
public final class BoxContactManifold {
    public record Contact(Vec3 point,Vec3 normal,double penetration){}
    private BoxContactManifold(){}
    public static List<Contact> contacts(OrientedBox a,OrientedBox b,double skin) {
        var sat=a.contact(b,skin);if(sat.isEmpty())return List.of();
        var hit=sat.get();Vec3 normal=hit.normal();
        int aa=faceAxis(a,normal),bb=faceAxis(b,normal);
        double alignA=Math.abs(a.basis(aa).dot(normal)),alignB=Math.abs(b.basis(bb).dot(normal));
        if(Math.max(alignA,alignB)<1-1e-7)return List.of(edgeContact(a,b,hit));
        boolean referenceA=alignA>=alignB;
        var reference=referenceA?a:b;var incident=referenceA?b:a;
        Vec3 outward=referenceA?normal.scaled(-1):normal;
        int face=referenceA?aa:bb;
        Vec3 origin=reference.centre().plus(reference.basis(face).scaled(Math.copySign(half(reference,face),reference.basis(face).dot(outward))));
        int tangent=(face+1)%3,bitangent=(face+2)%3;
        int inc=faceAxis(incident,outward);Vec3 faceNormal=incident.basis(inc);
        Vec3 incCentre=incident.centre().minus(origin).plus(faceNormal.scaled(Math.copySign(half(incident,inc),-faceNormal.dot(outward))));
        Vec3 u=incident.basis((inc+1)%3).scaled(half(incident,(inc+1)%3));
        Vec3 v=incident.basis((inc+2)%3).scaled(half(incident,(inc+2)%3));
        List<Vec3> polygon=List.of(incCentre.plus(u).plus(v),incCentre.minus(u).plus(v),incCentre.minus(u).minus(v),incCentre.plus(u).minus(v));
        for(int axis:new int[]{tangent,bitangent}) {
            polygon=clip(polygon,reference.basis(axis),half(reference,axis));
            polygon=clip(polygon,reference.basis(axis).scaled(-1),half(reference,axis));
        }
        var result=new ArrayList<Contact>();
        for(var p:polygon) {
            double distance=p.dot(outward);
            if(distance<=skin+1e-9)result.add(new Contact(origin.plus(p.minus(outward.scaled(distance*.5))),normal,Math.max(0,-distance)));
        }
        // Near-degenerate edge/face clipping may collapse to an edge. Keep the real support
        // pair, rather than inventing an occupied face at a broadphase corner.
        if(result.isEmpty())return List.of(edgeContact(a,b,hit));
        return List.copyOf(new LinkedHashSet<>(result));
    }
    private static int faceAxis(OrientedBox box,Vec3 normal) {
        int best=0;for(int i=1;i<3;i++)if(Math.abs(box.basis(i).dot(normal))>Math.abs(box.basis(best).dot(normal)))best=i;return best;
    }
    private static double half(OrientedBox box,int axis){return BodyBounds.component(box.half(),axis);}
    private static List<Vec3> clip(List<Vec3> polygon,Vec3 normal,double extent) {
        if(polygon.isEmpty())return polygon;
        var output=new ArrayList<Vec3>();Vec3 previous=polygon.getLast();double pd=previous.dot(normal)-extent;
        for(var current:polygon) {
            double cd=current.dot(normal)-extent;
            if((pd<=0)!=(cd<=0))output.add(previous.plus(current.minus(previous).scaled(pd/(pd-cd))));
            if(cd<=0)output.add(current);
            previous=current;pd=cd;
        }
        return output;
    }
    private static Contact edgeContact(OrientedBox a,OrientedBox b,OrientedBox.Contact hit) {
        int ai=0,bi=0;double best=-1;
        for(int i=0;i<3;i++)for(int j=0;j<3;j++) {
            var cross=a.basis(i).cross(b.basis(j));double length=cross.length();if(length<1e-10)continue;
            double aligned=Math.abs(cross.dot(hit.normal()))/length;if(aligned>best){best=aligned;ai=i;bi=j;}
        }
        Vec3 ac=edgeCentre(a,ai,hit.normal().scaled(-1)),bc=edgeCentre(b,bi,hit.normal());
        Vec3 u=a.basis(ai).scaled(2*half(a,ai)),v=b.basis(bi).scaled(2*half(b,bi));
        Vec3 p=ac.minus(u.scaled(.5)),q=bc.minus(v.scaled(.5)),r=p.minus(q);
        double uu=u.dot(u),vv=v.dot(v),uv=u.dot(v),ur=u.dot(r),vr=v.dot(r),denominator=uu*vv-uv*uv;
        double s=denominator>1e-12?clamp((uv*vr-ur*vv)/denominator):0,t=(uv*s+vr)/vv;
        if(t<0){t=0;s=clamp(-ur/uu);}else if(t>1){t=1;s=clamp((uv-ur)/uu);}
        return new Contact(p.plus(u.scaled(s)).plus(q.plus(v.scaled(t))).scaled(.5),hit.normal(),hit.penetration());
    }
    private static Vec3 edgeCentre(OrientedBox box,int edge,Vec3 direction) {
        Vec3 p=box.centre();for(int i=0;i<3;i++)if(i!=edge)p=p.plus(box.basis(i).scaled(Math.copySign(half(box,i),box.basis(i).dot(direction))));return p;
    }
    private static double clamp(double t){return Math.max(0,Math.min(1,t));}
}
