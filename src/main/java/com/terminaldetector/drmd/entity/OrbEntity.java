package com.terminaldetector.drmd.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public final class OrbEntity extends FlightEnemyEntity {
    public OrbEntity(EntityType<? extends OrbEntity> type,World world){super(type,world,FlightEnemyKind.ORB);}
}
