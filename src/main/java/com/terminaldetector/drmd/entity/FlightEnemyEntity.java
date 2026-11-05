package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.ai.AiRole;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

/** Own type identity and authored defaults, sharing the existing6DoF combat, routes, state and damage. */
public abstract class FlightEnemyEntity extends DroneEntity {
    private final FlightEnemyKind kind;
    protected FlightEnemyEntity(EntityType<? extends FlightEnemyEntity> type,World world,FlightEnemyKind kind) {
        super(type,world);this.kind=kind;applyRole(kind.defaultRole);
    }
    public final FlightEnemyKind kind(){return kind;}
    @Override protected final AiRole defaultRole(){return kind.defaultRole;}
}
