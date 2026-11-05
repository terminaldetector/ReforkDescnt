package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.ai.AiRole;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

/** Independent registry identities; role loadouts remain on the shared drone combat/physics system. */
public enum FlightEnemyKind {
    ORB("orb",AiRole.ASSAULT,.9f,0x6de8cc,0x174d49),
    ROCKET("rocket",AiRole.ARTILLERY,1.8f,0xe5a35a,0x5a3020),
    JET("jet",AiRole.INTERCEPTOR,2.4f,0x8ab9e6,0x24395a);
    public final String id;
    public final AiRole defaultRole;
    /** Conservative cube contains the complete low-poly hull through full pitch/roll. */
    public final float diameter;
    public final int primaryColour,secondaryColour;
    FlightEnemyKind(String id,AiRole role,float diameter,int primary,int secondary){this.id=id;defaultRole=role;this.diameter=diameter;primaryColour=primary;secondaryColour=secondary;}
    public EntityType<? extends FlightEnemyEntity> entityType(){return switch(this){case ORB->ModEntities.ORB;case ROCKET->ModEntities.ROCKET;case JET->ModEntities.JET;};}
    public FlightEnemyEntity create(World world){return entityType().create(world);}
}
