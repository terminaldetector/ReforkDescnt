package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;

public final class DroneLoadoutGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-loadouts")
    public void everyRoleSpawnsAtItsFullAuthoredHullAndDefencePools(TestContext context) {
        var drone=ModEntities.DRONE.create(context.getWorld());
        try {
            for(AiRole role:AiRole.values()) {
                // Force every transition through the smallest hull, not just Assault's 200.
                drone.applyRole(AiRole.MG);drone.applyRole(role);
                context.assertTrue(drone.getHealth()==role.hullHp && drone.getMaxHealth()==role.hullHp,"full hull for "+role);
                context.assertTrue(drone.getDroneShield()==role.shield && drone.getDroneArmor()==role.armor,"full defence pools for "+role);
                context.assertTrue(drone.getRole()==role && !drone.isAiDisabled(),"correct default active AI role");
            }
        } finally {drone.discard();}
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-loadouts")
    public void roleReloadPreservesWoundsAndDoesNotInventMissingDefenceData(TestContext context) {
        var drone=ModEntities.DRONE.create(context.getWorld());var copy=ModEntities.DRONE.create(context.getWorld());
        try {
            for(AiRole role:AiRole.values()) {
                drone.applyRole(role);drone.setHealth(role.hullHp*.37f);
                var nbt=drone.writeNbt(new NbtCompound());nbt.putFloat("shield",7);nbt.putFloat("armor",3);
                copy.readNbt(nbt);
                context.assertTrue(copy.getRole()==role && copy.getMaxHealth()==role.hullHp,"saved role and maximum restored");
                context.assertTrue(Math.abs(copy.getHealth()-role.hullHp*.37f)<1e-4,"reload does not heal "+role);
                context.assertTrue(copy.getDroneShield()==7 && copy.getDroneArmor()==3,"saved defence state preserved");
                nbt.remove("shield");nbt.remove("armor");copy.readNbt(nbt);
                context.assertTrue(copy.getDroneShield()==role.shield && copy.getDroneArmor()==role.armor,"legacy missing defence keys use role defaults");
            }
        } finally {drone.discard();copy.discard();}
        context.complete();
    }
}
