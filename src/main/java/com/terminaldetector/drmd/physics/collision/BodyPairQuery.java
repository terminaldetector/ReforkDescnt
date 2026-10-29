package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import java.util.*;

/** Two moving compound hulls, using the existing immutable local BVHs and OBB solver.
 * Translation uses continuous sweeps of BOTH participants. Rotation is subdivided by
 * corner travel; empty space in the overall hull bounds never becomes solid material. */
public final class BodyPairQuery {
    public record Motion(CompoundCollider shape,Vec3 centre,Vec3 from,Quat before,Vec3 to,Quat after) {
        public Motion {
            Objects.requireNonNull(shape);BodyBounds.finite(centre);BodyBounds.finite(from);BodyBounds.finite(to);
            new OrientedBox(from,new Vec3(1,1,1),before);new OrientedBox(to,new Vec3(1,1,1),after);
            before=before.normalized();after=after.normalized();
        }
        public Vec3 position(double t){return from.plus(to.minus(from).scaled(t));}
        public Quat rotation(double t){return ImmPtlQuaternions.interpolate(before,after,t).normalized();}
        public BodyCollisionQuery query(double t){return new BodyCollisionQuery(shape,position(t),centre,rotation(t));}
        public Vec3 point(Vec3 local,double t){return position(t).plus(rotation(t).rotate(local.minus(centre)));}
        public Vec3 velocity(Vec3 point,double t) {
            // Differentiate orientation without subtracting nearby world positions. At the
            // world border a finite-difference velocity manufactures closing contacts from
            // floating-point cancellation and can exhaust the zero-time contact budget.
            Quat a=before,b=after;if(ImmPtlQuaternions.dot(a,b)<0)b=ImmPtlQuaternions.scaled(b,-1);
            Quat rate;
            if(ImmPtlQuaternions.dot(a,b)>.9995) {
                var d=ImmPtlQuaternions.plus(b,ImmPtlQuaternions.scaled(a,-1));
                var raw=ImmPtlQuaternions.plus(a,ImmPtlQuaternions.scaled(d,t));
                double length=Math.sqrt(ImmPtlQuaternions.dot(raw,raw));var q=ImmPtlQuaternions.scaled(raw,1/length);
                rate=ImmPtlQuaternions.scaled(ImmPtlQuaternions.plus(d,ImmPtlQuaternions.scaled(q,-ImmPtlQuaternions.dot(q,d))),1/length);
                rate=ImmPtlQuaternions.hamiltonProduct(rate,q.inverse());
            } else {
                var delta=ImmPtlQuaternions.hamiltonProduct(b,a.inverse());var axis=new Vec3(delta.x(),delta.y(),delta.z());
                double length=axis.length(),factor=Math.atan2(length,delta.w())/length;
                rate=new Quat(axis.x()*factor,axis.y()*factor,axis.z()*factor,0);
            }
            return to.minus(from).plus(new Vec3(2*rate.x(),2*rate.y(),2*rate.z()).cross(point.minus(position(t))));
        }
        public double travelBound() {
            double radius=shape.bounds().map(b->b.half().length()+b.centre().minus(centre).length()).orElse(0.);
            double angle=2*Math.acos(Math.min(1,Math.abs(ImmPtlQuaternions.dot(before,after))));
            return to.minus(from).length()+angle*radius;
        }
        public Optional<BodyBounds> sweptBounds() {
            return shape.bounds().map(local->{
                double radius=local.half().length()+local.centre().minus(centre).length();
                double angle=2*Math.acos(Math.min(1,Math.abs(ImmPtlQuaternions.dot(before,after))));
                double sagitta=radius*(1-Math.cos(angle/2));
                return expand(OrientedBox.local(local,from,centre,before).bounds().union(OrientedBox.local(local,to,centre,after).bounds()),sagitta+1e-6);
            });
        }
        private BodyBounds local(BodyBounds world,double t){return OrientedBox.local(world,centre,position(t),rotation(t).inverse()).bounds();}
    }
    public record Contact(Vec3 point,Vec3 normal,double penetration){}
    public record Impact(double fraction,List<Contact> contacts){public Impact{contacts=List.copyOf(contacts);}}
    private BodyPairQuery(){}

    public static Optional<Impact> impact(Motion a,Motion b) {
        if(a.shape.bounds().isEmpty()||b.shape.bounds().isEmpty())return Optional.empty();
        if(!a.sweptBounds().orElseThrow().intersects(b.sweptBounds().orElseThrow()))return Optional.empty();
        var initial=contacts(a,b,0);if(!initial.isEmpty())return Optional.of(new Impact(0,initial));
        int steps=Math.max(1,(int)Math.ceil((a.travelBound()+b.travelBound())/.15));
        // COM rebasing after damage can add the old angular point velocity. The expanded
        // grid and both participants' trajectories fit1024. Invalid callers must split their
        // timestep rather than silently turning off rotational collision detection.
        if(steps>1024)throw new IllegalArgumentException("body pair timestep exceeds corner-travel budget");
        for(int step=0;step<steps;step++) {
            double start=(double)step/steps,end=(double)(step+1)/steps;
            var b0=OrientedBox.local(b.shape.bounds().orElseThrow(),b.position(start),b.centre,b.rotation(start)).bounds();
            var b1=OrientedBox.local(b.shape.bounds().orElseThrow(),b.position(end),b.centre,b.rotation(end)).bounds();
            var broad=expand(b0.union(b1),.16);
            double first=Double.POSITIVE_INFINITY;
            for(var primitive:a.shape.query(a.local(broad,start))) {
                var actor=OrientedBox.local(primitive,a.position(start),a.centre,a.rotation(start));
                Vec3 movement=a.point(primitive.centre(),end).minus(a.point(primitive.centre(),start));
                var swept=b.query(start).sweep(actor,movement,b.position(end).minus(b.position(start)),true);
                if(swept.isPresent())first=Math.min(first,start+(end-start)*swept.get().fraction());
            }
            if(Double.isFinite(first)) {
                var hit=contacts(a,b,first);
                if(!hit.isEmpty())return Optional.of(new Impact(first,hit));
            }
            // Angular SAT is checked with each actual quaternion; frozen-orientation
            // broadphase/sweep hits cannot manufacture a contact in a rotated hole.
            var atEnd=contacts(a,b,end);
            if(!atEnd.isEmpty()) {
                double lo=start,hi=end;
                for(int i=0;i<16;i++){double mid=(lo+hi)/2;if(contacts(a,b,mid).isEmpty())lo=mid;else hi=mid;}
                return Optional.of(new Impact(hi,contacts(a,b,hi)));
            }
        }
        return Optional.empty();
    }
    /** Evaluate every primitive. Keep each planar patch's projected convex hull and deepest
     * point; axis extrema alone can discard a corner with a closing angular velocity. */
    public static List<Contact> contacts(Motion a,Motion b,double t) {
        return contacts(a,b,t,false);
    }
    public static List<Contact> contacts(Motion a,Motion b,double t,boolean resting) {
        if(b.shape.bounds().isEmpty())return List.of();
        var bounds=OrientedBox.local(b.shape.bounds().orElseThrow(),b.position(t),b.centre,b.rotation(t)).bounds();
        var manifold=new Manifold();
        for(var primitive:a.shape.query(a.local(expand(bounds,1e-6),t))) {
            var actor=OrientedBox.local(primitive,a.position(t),a.centre,a.rotation(t));
            for(var shape:b.shape.query(b.local(expand(actor.bounds(),1e-6),t))) {
                var hull=OrientedBox.local(shape,b.position(t),b.centre,b.rotation(t));
                for(var hit:BoxContactManifold.contacts(actor,hull,1e-6)) {
                    double closing=a.velocity(hit.point(),t).minus(b.velocity(hit.point(),t)).dot(hit.normal());
                    if(resting||hit.penetration()>1e-7||closing<-1e-8)manifold.add(new Contact(hit.point(),hit.normal(),Math.max(0,hit.penetration())));
                }
            }
        }
        return manifold.values();
    }
    private static BodyBounds expand(BodyBounds b,double skin){var r=new Vec3(skin,skin,skin);return new BodyBounds(b.min().minus(r),b.max().plus(r));}
    private record Normal(long x,long y,long z,long plane){}
    private record Projected(Contact contact,double u,double v){}
    private static final class Manifold {
        final Map<Normal,List<Contact>> planes=new LinkedHashMap<>();
        void add(Contact c) {
            var key=new Normal(Math.round(c.normal.x()*1e6),Math.round(c.normal.y()*1e6),Math.round(c.normal.z()*1e6),Math.round(c.normal.dot(c.point)*1e5));
            planes.computeIfAbsent(key,k->new ArrayList<>()).add(c);
        }
        List<Contact> values() {
            var result=new LinkedHashSet<Contact>();
            for(var points:planes.values()) {
                if(points.size()<=8){result.addAll(points);continue;}
                var deepest=points.stream().max(Comparator.comparingDouble(Contact::penetration)).orElseThrow();result.add(deepest);
                Vec3 n=deepest.normal,origin=deepest.point;
                Vec3 axis=Math.abs(n.x())<.7?new Vec3(1,0,0):new Vec3(0,1,0),u=n.cross(axis).normalized(),v=n.cross(u);
                var projected=new ArrayList<Projected>();for(var c:points){var d=c.point.minus(origin);projected.add(new Projected(c,d.dot(u),d.dot(v)));}
                projected.sort(Comparator.comparingDouble(Projected::u).thenComparingDouble(Projected::v));
                var hull=new ArrayList<Projected>();
                for(var p:projected){while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.getLast(),p)<=1e-12)hull.removeLast();hull.add(p);}
                int lower=hull.size();
                for(int i=projected.size()-2;i>=0;i--){var p=projected.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.getLast(),p)<=1e-12)hull.removeLast();hull.add(p);}
                for(var p:hull)result.add(p.contact);
            }
            return List.copyOf(result);
        }
        private static double cross(Projected a,Projected b,Projected c){return (b.u-a.u)*(c.v-a.v)-(b.v-a.v)*(c.u-a.u);}
    }
}
