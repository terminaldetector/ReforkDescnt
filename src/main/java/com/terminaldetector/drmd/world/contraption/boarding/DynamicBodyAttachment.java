package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import java.util.*;

/**
 * Contact motor for an upright walking actor. Owns world velocity, never a welded local position.
 * Ship-local gravity and arbitrary actor UP are separate following stages; this uses world gravity.
 */
public final class DynamicBodyAttachment {
    public record Input(double strafe,double forward,boolean jump,Vec3 look) {
        public Input {
            BodyBounds.finite(look);
            if(!Double.isFinite(strafe)||!Double.isFinite(forward)||Math.abs(strafe)>1||Math.abs(forward)>1
                ||look.lengthSquared()<1e-8||look.lengthSquared()>4)throw new IllegalArgumentException("invalid boarding input");
        }
        public static Input idle(){return new Input(0,0,false,new Vec3(0,0,1));}
    }
    public record State(Vec3 feet,Vec3 velocity,boolean grounded,int support) {}
    /** Return the actual displacement permitted by ordinary world collisions. */
    @FunctionalInterface public interface WorldMove {Vec3 move(Vec3 feet,Vec3 requested);}
    private static final Vec3 UP=new Vec3(0,1,0),ZERO=new Vec3(0,0,0);
    private static final double SKIN=.002;
    // Entity.move ignores displacements whose squared length is <=1e-7. A smaller
    // depenetration silently leaves the real actor overlapping and wastes every CCD iteration.
    private static final double SEPARATION=.0005;
    private static final double JUMP_SPEED=.42*20; // Vanilla .42 blocks/tick, expressed in world units/second.
    private DynamicBodyAttachment() {}
    public static OrientedBox box(Vec3 feet,double width,double height) {
        return new OrientedBox(feet.plus(new Vec3(0,height/2,0)),new Vec3(width/2,height/2,width/2),Quat.IDENTITY);
    }
    public static State step(State state,Input input,List<BodyMotion> bodies,double width,double height,double seconds,WorldMove world) {
        if(!Double.isFinite(seconds)||seconds<=0||seconds>.05)throw new IllegalArgumentException("one bounded tick required");
        BodyBounds.finite(state.feet);BodyBounds.finite(state.velocity);
        int steps=Math.max(1,Math.min(32,(int)Math.ceil((state.velocity.length()*seconds+
            bodies.stream().mapToDouble(BodyMotion::travelBound).max().orElse(0))/.08)));
        double dt=seconds/steps;Vec3 feet=state.feet,velocity=state.velocity;
        boolean grounded=state.grounded;int support=state.support;
        boolean jumping=input.jump&&grounded;
        // World velocity already contains the contact point's speed. Adding it again doubles it.
        if(jumping){velocity=velocity.plus(UP.scaled(JUMP_SPEED));grounded=false;support=-1;}
        Vec3 forward=new Vec3(input.look.x(),0,input.look.z()).normalized();
        if(forward.lengthSquared()<1e-8)forward=new Vec3(0,0,1);
        Vec3 wish=UP.cross(forward).scaled(input.strafe).plus(forward.scaled(input.forward));
        if(wish.lengthSquared()>1)wish=wish.normalized();wish=wish.scaled(4.3);
        for(int s=0;s<steps;s++) {
            double t=(double)s/steps,end=(double)(s+1)/steps;
            Vec3 surface=ZERO;
            for(var b:bodies)if(b.id==support){surface=b.surfaceVelocity(feet,t);break;}
            // Feet drive through friction at the contact, with the opposite impulse on the hull.
            // Neutral air input does not erase the carrier speed inherited at departure.
            if(!grounded&&wish.lengthSquared()>1e-8)velocity=velocity.plus(wish.normalized().scaled(2*dt));
            velocity=velocity.plus(new Vec3(0,-32*dt,0));
            grounded=false;support=-1;
            var actor=new D6PhysicsBody().withMass(1).withInertia(D6Mat3.diagonal(1e12,1e12,1e12))
                .withPosition(feet.plus(new Vec3(0,height/2,0))).withLinearVelocity(velocity);
            // Resolve a small old-pose overlap (e.g. bounded angular substeps), never chase an anchor.
            for(var b:bodies)for(var c:b.query(t).contacts(box(feet,width,height),SKIN)) {
                if(c.penetration()>.2)continue;
                if(c.penetration()>1e-7)feet=feet.plus(world.move(feet,c.normal().scaled(c.penetration()+SEPARATION)));
                actor.withPosition(feet.plus(new Vec3(0,height/2,0)));
                contact(actor,b,t,c.point(),c.normal(),wish);
                if(c.normal().dot(UP)>.6 && actor.linearVelocity().minus(b.surfaceVelocity(c.point(),t)).dot(c.normal())<.05) {
                    grounded=true;support=b.id;
                }
            }
            double current=t;
            record Plane(Vec3 normal,BodyMotion body) {}
            var planes=new ArrayList<Plane>();
            for(int iteration=0;iteration<6 && current<end-1e-10;iteration++) {
                double remaining=(end-current)*seconds;
                Vec3 move=actor.linearVelocity().scaled(remaining);
                for(var plane:planes) {
                    Vec3 surfaceMove=plane.body.position(end).minus(plane.body.position(current));
                    double closing=move.minus(surfaceMove).dot(plane.normal);
                    if(closing<0)move=move.minus(plane.normal.scaled(closing));
                }
                BodyMotion hitBody=null;BodyCollisionQuery.Sweep hit=null;
                for(var b:bodies) {
                    var sweep=b.query(current).sweep(box(feet,width,height),move,b.position(end).minus(b.position(current)));
                    if(sweep.isPresent() && (hit==null||sweep.get().fraction()<hit.fraction())) {hit=sweep.get();hitBody=b;}
                }
                if(hit==null) {
                    Vec3 allowed=world.move(feet,move);feet=feet.plus(allowed);
                    actor.withLinearVelocity(blockedVelocity(actor.linearVelocity(),move,allowed));current=end;break;
                }
                double fraction=hit.fraction(),impact=current+(end-current)*fraction;
                Vec3 partial=move.scaled(Math.max(0,fraction-1e-7));
                Vec3 allowed=world.move(feet,partial);feet=feet.plus(allowed);
                actor.withLinearVelocity(blockedVelocity(actor.linearVelocity(),partial,allowed));
                actor.withPosition(feet.plus(new Vec3(0,height/2,0)));
                var contacts=hitBody.query(impact).contacts(box(feet,width,height),.02);
                final Vec3 sweptNormal=hit.normal();
                var actual=contacts.stream().max(Comparator.comparingDouble(c->c.normal().dot(sweptNormal))).orElse(null);
                Vec3 normal=actual==null?sweptNormal:actual.normal();
                if(actual!=null&&actual.penetration()>1e-7&&actual.penetration()<=.2) {
                    feet=feet.plus(world.move(feet,normal.scaled(actual.penetration()+SEPARATION)));
                    actor.withPosition(feet.plus(new Vec3(0,height/2,0)));
                }
                Vec3 point=actual==null?box(feet,width,height).supportCentre(normal.scaled(-1)):actual.point();
                double before=actor.linearVelocity().minus(hitBody.surfaceVelocity(point,impact)).dot(normal);
                contact(actor,hitBody,impact,point,normal,wish);
                planes.add(new Plane(normal,hitBody));
                if(normal.dot(UP)>.6 && before<=.05) {grounded=true;support=hitBody.id;}
                // A solved touching contact should slide on the next sweep. Bound a degenerate
                // zero-progress corner instead of advancing through it or burning an unbounded loop.
                if(fraction<1e-10 && actor.linearVelocity().minus(hitBody.surfaceVelocity(point,impact)).dot(normal)< -1e-6)break;
                current=impact;
            }
            velocity=actor.linearVelocity();
        }
        // Grounding belongs to the final pose, not the start of the last angular substep. A small
        // resting margin preserves a genuine near contact while still letting an opening jump go.
        grounded=false;support=-1;
        for(var b:bodies)for(var c:b.query(1).contacts(box(feet,width,height),.01))
            if(c.normal().y()>.6&&c.penetration()<=.2&&velocity.minus(b.surfaceVelocity(c.point(),1)).dot(c.normal())<.1) {
                grounded=true;support=b.id;
            }
        return new State(feet,velocity,grounded,support);
    }
    private static void contact(D6PhysicsBody actor,BodyMotion body,double t,Vec3 point,Vec3 normal,Vec3 wish) {
        var proxy=body.proxy(t);Vec3 beforeV=proxy.linearVelocity();
        var constraint=new BodyContactConstraint(actor,proxy,point,normal,.65,normal.y()>.6?wish:ZERO);
        for(int pass=0;pass<8;pass++)constraint.solve();
        Vec3 impulse=proxy.linearVelocity().minus(beforeV).scaled(proxy.mass());
        // The same contact point/offset generated this reaction on the pose-specific D6 proxy.
        body.react(impulse,point.minus(proxy.position()));
    }
    private static Vec3 blockedVelocity(Vec3 velocity,Vec3 requested,Vec3 allowed) {
        // A tiny time-of-impact displacement is ignored by Entity.move before it even queries
        // world collisions. That is not a wall and must not erase the actor's momentum.
        if(requested.lengthSquared()<=1e-7)return velocity;
        return new Vec3(Math.abs(requested.x()-allowed.x())>1e-7?0:velocity.x(),
            Math.abs(requested.y()-allowed.y())>1e-7?0:velocity.y(),Math.abs(requested.z()-allowed.z())>1e-7?0:velocity.z());
    }
}
