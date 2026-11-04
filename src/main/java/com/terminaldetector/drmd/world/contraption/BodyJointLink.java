package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Persistent material anchors; COM rebasing must not move a mechanical attachment. */
public record BodyJointLink(UUID id,Endpoint a,Endpoint b,BodyJointConstraint.Kind kind,
                            double lower,double upper,double speed,double motorForce,double breakForce,double breakTorque) {
    public record Endpoint(UUID body,BlockPos cell,Vec3 point,Quat frame) {
        public Endpoint {
            Objects.requireNonNull(body);cell=Objects.requireNonNull(cell).toImmutable();BodyBounds.finite(point);Objects.requireNonNull(frame);
            double norm=frame.x()*frame.x()+frame.y()*frame.y()+frame.z()*frame.z()+frame.w()*frame.w();
            if(!Double.isFinite(norm)||norm<1e-16||point.x()<cell.getX()-1e-8||point.x()>cell.getX()+1+1e-8
                ||point.y()<cell.getY()-1e-8||point.y()>cell.getY()+1+1e-8||point.z()<cell.getZ()-1e-8||point.z()>cell.getZ()+1+1e-8)throw new IllegalArgumentException("invalid material anchor");
            frame=frame.normalized();
        }
        Endpoint on(UUID body){return new Endpoint(body,cell,point,frame);}
    }
    public BodyJointLink {
        Objects.requireNonNull(id);Objects.requireNonNull(a);Objects.requireNonNull(b);if(a.body.equals(b.body))throw new IllegalArgumentException("a joint connects distinct bodies");
        new BodyJointConstraint.Config(kind,a.point,b.point,a.frame,b.frame,lower,upper,speed,motorForce,breakForce,breakTorque);
    }
    public BodyJointConstraint.Config bind(BlockBodyEntity first,BlockBodyEntity second) {
        if(!first.getUuid().equals(a.body)||!second.getUuid().equals(b.body))throw new IllegalArgumentException("joint endpoints do not match bodies");
        return new BodyJointConstraint.Config(kind,a.point.minus(first.localCentre()),b.point.minus(second.localCentre()),a.frame,b.frame,lower,upper,speed,motorForce,breakForce,breakTorque);
    }
    public NbtCompound write() {
        var n=new NbtCompound();n.putUuid("id",id);n.put("a",write(a));n.put("b",write(b));n.putString("kind",kind.name());
        if(Double.isFinite(lower))n.putDouble("lower",lower);if(Double.isFinite(upper))n.putDouble("upper",upper);
        n.putDouble("speed",speed);n.putDouble("motor",motorForce);
        if(Double.isFinite(breakForce))n.putDouble("breakForce",breakForce);if(Double.isFinite(breakTorque))n.putDouble("breakTorque",breakTorque);return n;
    }
    public static BodyJointLink read(NbtCompound n) {
        if(!n.containsUuid("id"))throw new IllegalArgumentException("joint identity missing");
        return new BodyJointLink(n.getUuid("id"),readEndpoint(n.getCompound("a")),readEndpoint(n.getCompound("b")),BodyJointConstraint.Kind.valueOf(n.getString("kind")),
            n.contains("lower")?n.getDouble("lower"):Double.NEGATIVE_INFINITY,n.contains("upper")?n.getDouble("upper"):Double.POSITIVE_INFINITY,
            n.getDouble("speed"),n.getDouble("motor"),n.contains("breakForce")?n.getDouble("breakForce"):Double.POSITIVE_INFINITY,n.contains("breakTorque")?n.getDouble("breakTorque"):Double.POSITIVE_INFINITY);
    }
    private static NbtCompound write(Endpoint e) {
        var n=new NbtCompound();n.putUuid("body",e.body);n.putLong("cell",e.cell.asLong());
        n.putDouble("x",e.point.x());n.putDouble("y",e.point.y());n.putDouble("z",e.point.z());
        n.putDouble("qx",e.frame.x());n.putDouble("qy",e.frame.y());n.putDouble("qz",e.frame.z());n.putDouble("qw",e.frame.w());return n;
    }
    private static Endpoint readEndpoint(NbtCompound n) {
        if(!n.containsUuid("body"))throw new IllegalArgumentException("endpoint identity missing");
        return new Endpoint(n.getUuid("body"),BlockPos.fromLong(n.getLong("cell")),new Vec3(n.getDouble("x"),n.getDouble("y"),n.getDouble("z")),new Quat(n.getDouble("qx"),n.getDouble("qy"),n.getDouble("qz"),n.getDouble("qw")));
    }
}
