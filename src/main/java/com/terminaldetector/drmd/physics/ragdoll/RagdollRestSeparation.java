package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.*;

/** Read-only one-normal-tick interaction predictor; it is not a terrain/rotational CCD solver. */
public final class RagdollRestSeparation {
    private RagdollRestSeparation(){}
    public static final double SECONDS=.05,MARGIN=RagdollSleepController.MAX_POSE_DRIFT;
    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        boolean valid(){return Double.isFinite(minX)&&Double.isFinite(minY)&&Double.isFinite(minZ)&&Double.isFinite(maxX)&&Double.isFinite(maxY)&&Double.isFinite(maxZ)&&minX<maxX&&minY<maxY&&minZ<maxZ;}
        boolean overlaps(Bounds b){return minX<=b.maxX&&maxX>=b.minX&&minY<=b.maxY&&maxY>=b.minY&&minZ<=b.maxZ&&maxZ>=b.minZ;}
    }
    /** Includes the actual rotated hull, linear sweep, gravity and unconsumed world force/torque.
     * Angular excursion uses |L|/lambda_min, so anisotropic inertia/precession cannot reduce it.
     * Every ordinary entity tick rechecks this predictor; actual external impulses wake the group.
     */
    public static Optional<List<Bounds>> sweptParts(RagdollSimulation simulation,Vec3 gravity){
        if(simulation==null||!finite(gravity)||simulation.parts().isEmpty()||simulation.parts().size()>RagdollSleepController.MAX_PARTS)return Optional.empty();
        var result=new ArrayList<Bounds>();
        for(var part:simulation.parts()){
            var body=part.body();var p=body.position();var q=body.rotation();var h=part.segment().halfExtents();var inertia=body.inertia();
            if(!finite(p)||!finite(h)||!finite(body.linearVelocity())||!finite(body.angularMomentum())||!finite(body.accumulatedForce())||!finite(body.accumulatedTorque())||!Double.isFinite(body.mass())||body.mass()<=0||inertia==null)return Optional.empty();
            double norm=q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();if(!Double.isFinite(norm)||Math.abs(norm-1)>1e-8)return Optional.empty();
            var i=inertia.row0();var j=inertia.row1();var k=inertia.row2();
            if(!finite(i)||!finite(j)||!finite(k))return Optional.empty();
            double minimum=Math.min(i.x()-Math.abs(i.y())-Math.abs(i.z()),Math.min(j.y()-Math.abs(j.x())-Math.abs(j.z()),k.z()-Math.abs(k.x())-Math.abs(k.y())));
            if(!Double.isFinite(minimum)||minimum<=0)return Optional.empty();
            var x=q.rotate(new Vec3(h.x(),0,0));var y=q.rotate(new Vec3(0,h.y(),0));var z=q.rotate(new Vec3(0,0,h.z()));
            double ex=Math.abs(x.x())+Math.abs(y.x())+Math.abs(z.x()),ey=Math.abs(x.y())+Math.abs(y.y())+Math.abs(z.y()),ez=Math.abs(x.z())+Math.abs(y.z())+Math.abs(z.z());
            var velocity=body.linearVelocity().scaled(SECONDS);
            var acceleration=gravity.plus(body.accumulatedForce().scaled(1/body.mass()));
            // Semi-implicit substeps travel no farther than |a|*dt^2 on each world axis.
            double radius=h.length(),angular=radius*Math.min(2,SECONDS*(body.angularMomentum().length()+SECONDS*body.accumulatedTorque().length())/minimum);
            double px=MARGIN+angular+Math.abs(acceleration.x())*SECONDS*SECONDS,py=MARGIN+angular+Math.abs(acceleration.y())*SECONDS*SECONDS,pz=MARGIN+angular+Math.abs(acceleration.z())*SECONDS*SECONDS;
            var bounds=new Bounds(p.x()-ex+Math.min(0,velocity.x())-px,p.y()-ey+Math.min(0,velocity.y())-py,p.z()-ez+Math.min(0,velocity.z())-pz,p.x()+ex+Math.max(0,velocity.x())+px,p.y()+ey+Math.max(0,velocity.y())+py,p.z()+ez+Math.max(0,velocity.z())+pz);
            if(!bounds.valid())return Optional.empty();result.add(bounds);
        }
        return Optional.of(List.copyOf(result));
    }
    /** All part pairs, not just entity centres or a coarse combined centre radius. */
    public static boolean separated(List<Bounds> first,List<Bounds> second){
        if(first==null||second==null||first.isEmpty()||second.isEmpty()||first.size()>16||second.size()>16)return false;
        for(var a:first){if(a==null||!a.valid())return false;for(var b:second)if(b==null||!b.valid()||a.overlaps(b))return false;}return true;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z())&&Double.isFinite(v.lengthSquared());}
}
