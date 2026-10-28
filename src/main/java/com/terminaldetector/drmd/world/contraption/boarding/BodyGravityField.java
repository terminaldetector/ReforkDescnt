package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import java.util.Optional;

/** Optional local acceleration volume attached to one physical hull; zero means no artificial field. */
public record BodyGravityField(Vec3 localAcceleration,BodyBounds localVolume) {
    public static final BodyGravityField NONE=new BodyGravityField(new Vec3(0,0,0),
        new BodyBounds(new Vec3(-1,-1,-1),new Vec3(1,1,1)));
    public BodyGravityField {
        BodyBounds.finite(localAcceleration);
        if(localVolume==null||localAcceleration.length()>64)throw new IllegalArgumentException("invalid body gravity field");
    }
    public boolean enabled(){return localAcceleration.lengthSquared()>1e-8;}
    public Optional<Vec3> at(Vec3 world,Vec3 bodyPosition,Vec3 localCom,Quat rotation) {
        if(!enabled())return Optional.empty();
        Vec3 local=localCom.plus(rotation.inverse().rotate(world.minus(bodyPosition)));
        if(local.x()<localVolume.min().x()||local.y()<localVolume.min().y()||local.z()<localVolume.min().z()
            ||local.x()>localVolume.max().x()||local.y()>localVolume.max().y()||local.z()>localVolume.max().z())return Optional.empty();
        return Optional.of(rotation.rotate(localAcceleration));
    }
}
