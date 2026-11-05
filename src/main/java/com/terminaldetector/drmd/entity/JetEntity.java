package com.terminaldetector.drmd.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public final class JetEntity extends FlightEnemyEntity {
    public JetEntity(EntityType<? extends JetEntity> type,World world){super(type,world,FlightEnemyKind.JET);}
}
