package com.terminaldetector.drmd.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public final class RocketDroneEntity extends FlightEnemyEntity {
    public RocketDroneEntity(EntityType<? extends RocketDroneEntity> type,World world){super(type,world,FlightEnemyKind.ROCKET);}
}
