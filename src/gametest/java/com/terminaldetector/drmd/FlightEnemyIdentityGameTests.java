package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Entity registry and actual NBT factory round trip, independent of UFO or role aliases. */
public final class FlightEnemyIdentityGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-kind-orb")
    public void orbKeepsItsOwnClassAndSavedState(TestContext c){identity(c,"orb","OrbEntity",AiRole.ASSAULT);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-kind-rocket")
    public void rocketKeepsItsOwnClassAndSavedState(TestContext c){identity(c,"rocket","RocketDroneEntity",AiRole.ARTILLERY);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-kind-jet")
    public void jetKeepsItsOwnClassAndSavedState(TestContext c){identity(c,"jet","JetEntity",AiRole.INTERCEPTOR);}
    private static void identity(TestContext c,String id,String className,AiRole role) {
        var key=Identifier.of("drmd",id);var type=Registries.ENTITY_TYPE.getOrEmpty(key);
        c.assertTrue(type.isPresent(),"independent entity id must be registered: "+key);
        var entity=type.orElseThrow().create(c.getWorld());Entity restored=null,legacy=null;
        try {
            c.assertTrue(entity instanceof DroneEntity&&!(entity instanceof BlockBodyEntity)&&entity.getClass().getSimpleName().equals(className),"own flight enemy class, not generic drone or UFO alias: "+key);
            var d=(DroneEntity)entity;c.assertTrue(d.getRole()==role&&d.getHealth()==role.hullHp&&!d.isAiDisabled()&&d.hasNoGravity(),"authored full-health active default: "+key);
            d.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(7,8,7))));d.setVelocity(.1,.2,-.3);d.setHealth(role.hullHp*.37f);d.setFlightRoll(-42);d.setPhase(com.terminaldetector.drmd.ai.DroneAi.Phase.RUN);d.addPhaseTimer(.75f);
            var tag=new NbtCompound();c.assertTrue(d.saveSelfNbt(tag)&&tag.getString("id").equals(key.toString()),"save the actual independent type id");
            restored=EntityType.getEntityFromNbt(tag,c.getWorld()).orElseThrow();
            c.assertTrue(restored.getType()==type.get()&&restored.getClass()==entity.getClass(),"NBT factory preserves exact type and class");
            var copy=(DroneEntity)restored;c.assertTrue(copy.getRole()==role&&copy.getHealth()==d.getHealth()&&copy.getFlightRoll()==-42&&copy.getPhase()==d.getPhase()&&copy.getPhaseTimer()==.75f&&copy.getVelocity().equals(d.getVelocity()),"independent type retains actual combat/motion state");
            tag.remove("role");legacy=EntityType.getEntityFromNbt(tag,c.getWorld()).orElseThrow();
            c.assertTrue(((DroneEntity)legacy).getRole()==role&&legacy.getClass()==entity.getClass(),"missing optional role uses the type's own authored default");
            c.assertTrue(ModEntities.DRONE.create(c.getWorld()).getClass()==DroneEntity.class,"legacy generic registry identity stays intact");
        } finally {if(entity!=null)entity.discard();if(restored!=null)restored.discard();if(legacy!=null)legacy.discard();}
        c.complete();
    }
}
