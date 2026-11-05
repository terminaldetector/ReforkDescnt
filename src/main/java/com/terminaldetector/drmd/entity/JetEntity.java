package com.terminaldetector.drmd.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public final class JetEntity extends FlightEnemyEntity {
    public JetEntity(EntityType<? extends JetEntity> type,World world){super(type,world,FlightEnemyKind.JET);}
    @Override public boolean continuousFlightSteering(){return true;}
    @Override public net.minecraft.util.math.Vec3d steerFlightVelocity(net.minecraft.util.math.Vec3d desired){
        return com.terminaldetector.drmd.ai.JetFlightMotor.steer(getVelocity(),desired);
    }
}
