package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import java.util.*;

/**
 * Contact motor for an oriented walking actor. Owns world velocity, never a welded local position.
 * Finite ship-local fields and ordinary world gravity share the same compound contact solver.
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
    public record State(Vec3 feet,Vec3 velocity,boolean grounded,int support,Quat frame,int gravityOwner) {
        public State {
            BodyBounds.finite(feet);BodyBounds.finite(velocity);
            new OrientedBox(feet,new Vec3(1,1,1),frame);frame=frame.normalized();
        }
        public State(Vec3 feet,Vec3 velocity,boolean grounded,int support){this(feet,velocity,grounded,support,Quat.IDENTITY,-1);}
    }
    public record Move(Vec3 displacement,List<Vec3> blockingNormals) {
        public Move {BodyBounds.finite(displacement);blockingNormals=List.copyOf(blockingNormals);}
    }
    public interface Motion {
        Move move(Vec3 feet,Quat frame,Vec3 requested);
        default boolean canOrient(Vec3 feet,Quat frame){return true;}
    }
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
        return PassengerFrame.box(feet,width,height,Quat.IDENTITY);
    }
    public static State step(State state,Input input,List<BodyMotion> bodies,double width,double height,double seconds,WorldMove world) {
        return step(state,input,bodies,width,height,seconds,new Motion() {
            @Override public Move move(Vec3 feet,Quat frame,Vec3 requested) {
                Vec3 allowed=world.move(feet,requested);var normals=new ArrayList<Vec3>();
                if(requested.lengthSquared()>1e-7)for(int axis=0;axis<3;axis++) {
                    double want=BodyBounds.component(requested,axis),got=BodyBounds.component(allowed,axis);
                    if(Math.abs(want-got)>1e-7)normals.add(axis==0?new Vec3(-Math.signum(want),0,0):axis==1?new Vec3(0,-Math.signum(want),0):new Vec3(0,0,-Math.signum(want)));
                }
                return new Move(allowed,normals);
            }
        });
    }
    public static State step(State state,Input input,List<BodyMotion> bodies,double width,double height,double seconds,Motion world) {
        if(!Double.isFinite(seconds)||seconds<=0||seconds>.05)throw new IllegalArgumentException("one bounded tick required");
        BodyBounds.finite(state.feet);BodyBounds.finite(state.velocity);
        Quat frame=state.frame;
        new OrientedBox(state.feet,new Vec3(1,1,1),frame); // validate physical orientation
        BodyMotion owner=null;
        Vec3 centre=state.feet.plus(PassengerFrame.up(frame).scaled(height/2));
        for(var b:bodies)if(b.gravity(centre,0).isPresent()) {
            if(owner==null||b.id==state.gravityOwner||b.id==state.support)owner=b;
            if(b.id==state.gravityOwner)break;
        }
        Vec3 targetUp=owner==null?UP:owner.gravityUp(1);
        double turn=Math.acos(Math.max(-1,Math.min(1,PassengerFrame.up(frame).dot(targetUp))));
        int steps=Math.max(1,Math.min(32,(int)Math.ceil((state.velocity.length()*seconds+
            bodies.stream().mapToDouble(BodyMotion::travelBound).max().orElse(0)+Math.min(turn,6*seconds)*height)/.08)));
        double dt=seconds/steps;Vec3 feet=state.feet,velocity=state.velocity;
        boolean grounded=state.grounded;int support=state.support;
        boolean jumping=input.jump&&grounded;
        // World velocity already contains the contact point's speed. Adding it again doubles it.
        if(jumping){velocity=velocity.plus(PassengerFrame.up(frame).scaled(JUMP_SPEED));grounded=false;support=-1;}
        Vec3 lastSpin=ZERO;
        for(int s=0;s<steps;s++) {
            Quat frameBefore=frame;
            double t=(double)s/steps,end=(double)(s+1)/steps;
            Quat carried=frame;
            if(owner!=null) {
                var delta=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.hamiltonProduct(owner.rotation(end),owner.rotation(t).inverse());
                carried=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.hamiltonProduct(delta,frame).normalized();
            }
            Quat candidate=PassengerFrame.turn(carried,owner==null?UP:owner.gravityUp(end),6*dt);
            if(!candidate.equals(frame)) {
                if(canOrient(feet,candidate,bodies,t,width,height,world))frame=candidate;
                else {
                    Quat before=frame;double lo=0,hi=1;
                    for(int i=0;i<10;i++) {
                        double mid=(lo+hi)/2;
                        var q=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.interpolate(before,candidate,mid).normalized();
                        if(canOrient(feet,q,bodies,t,width,height,world))lo=mid;else hi=mid;
                    }
                    frame=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.interpolate(before,candidate,lo).normalized();
                }
            }
            Vec3 up=PassengerFrame.up(frame),wish=PassengerFrame.wish(input,state.frame,frame);
            Vec3 spin=PassengerFrame.angularVelocity(frameBefore,frame,dt),centreOffset=up.scaled(height/2);lastSpin=spin;
            // Feet drive through friction at the contact, with the opposite impulse on the hull.
            // Neutral air input does not erase the carrier speed inherited at departure.
            if(!grounded&&wish.lengthSquared()>1e-8)velocity=velocity.plus(wish.normalized().scaled(2*dt));
            velocity=velocity.plus((owner==null?new Vec3(0,-32,0):owner.acceleration((t+end)/2)).scaled(dt));
            grounded=false;support=-1;
            var actor=new D6PhysicsBody().withMass(1).withInertia(D6Mat3.diagonal(1e12,1e12,1e12))
                .withPosition(feet.plus(centreOffset)).withRotation(frame).withAngularVelocity(spin)
                .withLinearVelocity(velocity.plus(spin.cross(centreOffset)));
            // Resolve a small old-pose overlap (e.g. bounded angular substeps), never chase an anchor.
            for(var b:bodies)for(var c:b.query(t).contacts(PassengerFrame.box(feet,width,height,frame),SKIN)) {
                if(c.penetration()>.2)continue;
                if(c.penetration()>1e-7)feet=feet.plus(world.move(feet,frame,c.normal().scaled(c.penetration()+SEPARATION)).displacement());
                actor.withPosition(feet.plus(up.scaled(height/2)));
                contact(actor,b,t,c.point(),c.normal(),wish,up);
                if(c.normal().dot(up)>.6 && actor.velocityAtPoint(c.point().minus(actor.position())).minus(b.surfaceVelocity(c.point(),t)).dot(c.normal())<.05) {
                    grounded=true;support=b.id;
                }
            }
            double current=t;
            record Plane(Vec3 normal,Vec3 localPoint,Vec3 actorOffset,BodyBounds shape,BodyMotion body) {}
            var planes=new ArrayList<Plane>();
            for(int iteration=0;iteration<6 && current<end-1e-10;iteration++) {
                double remaining=(end-current)*seconds;
                Vec3 move=actor.linearVelocity().minus(spin.cross(centreOffset)).scaled(remaining);
                for(var plane:planes) {
                    Vec3 surfaceMove=plane.body.point(plane.localPoint,end).minus(plane.body.point(plane.localPoint,current));
                    Vec3 actorTurn=spin.cross(plane.actorOffset).scaled(remaining);
                    double closing=move.plus(actorTurn).minus(surfaceMove).dot(plane.normal);
                    if(closing<0)move=move.minus(plane.normal.scaled(closing));
                }
                BodyMotion hitBody=null;BodyCollisionQuery.Sweep hit=null;
                for(var b:bodies) {
                    var sweep=b.query(current).sweep(PassengerFrame.box(feet,width,height,frame),move,b.position(end).minus(b.position(current)),true,candidateHit->planes.stream().noneMatch(
                        plane->plane.body==b&&plane.shape.equals(candidateHit.localShape())&&plane.normal.dot(candidateHit.normal())>.99));
                    if(sweep.isPresent() && (hit==null||sweep.get().fraction()<hit.fraction())) {hit=sweep.get();hitBody=b;}
                }
                if(hit==null) {
                    Move allowed=world.move(feet,frame,move);feet=feet.plus(allowed.displacement());
                    actor.withLinearVelocity(blockedVelocity(actor.linearVelocity().minus(spin.cross(centreOffset)),allowed.blockingNormals()).plus(spin.cross(centreOffset)));current=end;break;
                }
                double fraction=hit.fraction(),impact=current+(end-current)*fraction;
                Vec3 partial=move.scaled(Math.max(0,fraction-1e-7));
                Move allowed=world.move(feet,frame,partial);feet=feet.plus(allowed.displacement());
                actor.withLinearVelocity(blockedVelocity(actor.linearVelocity().minus(spin.cross(centreOffset)),allowed.blockingNormals()).plus(spin.cross(centreOffset)));
                actor.withPosition(feet.plus(up.scaled(height/2)));
                var contacts=hitBody.query(impact).contacts(PassengerFrame.box(feet,width,height,frame),.02);
                final Vec3 sweptNormal=hit.normal();
                var actual=contacts.stream().max(Comparator.comparingDouble(c->c.normal().dot(sweptNormal))).orElse(null);
                Vec3 normal=actual==null?sweptNormal:actual.normal();
                if(actual!=null&&actual.penetration()>1e-7&&actual.penetration()<=.2) {
                    feet=feet.plus(world.move(feet,frame,normal.scaled(actual.penetration()+SEPARATION)).displacement());
                    actor.withPosition(feet.plus(up.scaled(height/2)));
                }
                Vec3 point=actual==null?PassengerFrame.box(feet,width,height,frame).supportCentre(normal.scaled(-1)):actual.point();
                double before=actor.velocityAtPoint(point.minus(actor.position())).minus(hitBody.surfaceVelocity(point,impact)).dot(normal);
                contact(actor,hitBody,impact,point,normal,wish,up);
                // The solved moving plane owns this normal for the rest of the substep. Recasting
                // its frozen old orientation would stop valid rotational surface movement.
                planes.add(new Plane(normal,hitBody.localOffset(point,impact),point.minus(feet),hit.localShape(),hitBody));
                if(normal.dot(up)>.6 && before<=.05) {grounded=true;support=hitBody.id;}
                // A solved touching contact should slide on the next sweep. Bound a degenerate
                // zero-progress corner instead of advancing through it or burning an unbounded loop.
                if(fraction<1e-10 && actor.velocityAtPoint(point.minus(actor.position())).minus(hitBody.surfaceVelocity(point,impact)).dot(normal)< -1e-6)break;
                current=impact;
            }
            velocity=actor.linearVelocity().minus(spin.cross(centreOffset));
        }
        // Grounding belongs to the final pose, not the start of the last angular substep. A small
        // resting margin preserves a genuine near contact while still letting an opening jump go.
        grounded=false;support=-1;
        Vec3 up=PassengerFrame.up(frame);
        for(var b:bodies)for(var c:b.query(1).contacts(PassengerFrame.box(feet,width,height,frame),.01))
            if(c.normal().dot(up)>.6&&c.penetration()<=.2&&velocity.plus(lastSpin.cross(c.point().minus(feet))).minus(b.surfaceVelocity(c.point(),1)).dot(c.normal())<.1) {
                grounded=true;support=b.id;
            }
        int gravityOwner=owner!=null&&owner.gravity(feet.plus(up.scaled(height/2)),1).isPresent()?owner.id:-1;
        if(gravityOwner<0&&!grounded&&up.dot(UP)>.99999&&world.canOrient(feet,Quat.IDENTITY))frame=Quat.IDENTITY;
        return new State(feet,velocity,grounded,support,frame,gravityOwner);
    }
    private static void contact(D6PhysicsBody actor,BodyMotion body,double t,Vec3 point,Vec3 normal,Vec3 wish,Vec3 up) {
        var proxy=body.proxy(t);Vec3 beforeV=proxy.linearVelocity();
        var constraint=new BodyContactConstraint(actor,proxy,point,normal,.65,normal.dot(up)>.6?wish:ZERO);
        for(int pass=0;pass<8;pass++)constraint.solve();
        Vec3 impulse=proxy.linearVelocity().minus(beforeV).scaled(proxy.mass());
        // The same contact point/offset generated this reaction on the pose-specific D6 proxy.
        body.react(impulse,point.minus(proxy.position()));
    }
    private static Vec3 blockedVelocity(Vec3 velocity,List<Vec3> normals) {
        // An oblique world contact removes only its closing normal speed, never all XYZ axes.
        for(var normal:normals){double closing=velocity.dot(normal);if(closing<0)velocity=velocity.minus(normal.scaled(closing));}
        return velocity;
    }
    private static boolean canOrient(Vec3 feet,Quat frame,List<BodyMotion> bodies,double t,double width,double height,Motion world) {
        if(!world.canOrient(feet,frame))return false;
        Vec3 up=PassengerFrame.up(frame);
        for(var b:bodies)for(var c:b.query(t).contacts(PassengerFrame.box(feet,width,height,frame),0))
            if(c.penetration()>(c.normal().dot(up)>.6?.02:1e-5))return false;
        return true;
    }
}
