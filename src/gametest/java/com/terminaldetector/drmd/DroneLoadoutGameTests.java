package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class DroneLoadoutGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-combat-roundtrip")
    public void savedCombatStateRetainsBankEnergyPhaseAndReloadDelay(TestContext c) {
        var w=c.getWorld();var at=Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(7,8,7)));var area=new Box(at.add(-16,-16,-16),at.add(16,16,16));
        var before=w.getOtherEntities(null,area).stream().map(net.minecraft.entity.Entity::getUuid).collect(java.util.stream.Collectors.toSet());
        var original=ModEntities.DRONE.create(w);var copy=ModEntities.DRONE.create(w);var target=net.minecraft.entity.EntityType.COW.create(w);
        try {
            original.applyRole(AiRole.HEAVY_ELITE);original.setPosition(at);target.setPosition(at.add(5,0,0));target.setInvulnerable(true);
            original.setHealth(500);original.damage(w.getDamageSources().magic(),50);original.setFlightRoll(32.5f);
            original.setPhase(com.terminaldetector.drmd.ai.DroneAi.Phase.RUN);original.addPhaseTimer(.75f);
            c.assertTrue(original.tryFireAt(target),"real weapon fire starts the authored cooldown and spends energy");
            float energy=original.getModEnergy(),shield=original.getDroneShield();var tag=original.writeNbt(new NbtCompound());copy.readNbt(tag);
            boolean unexpectedFire=copy.tryFireAt(target);
            c.assertTrue(copy.getRole()==AiRole.HEAVY_ELITE&&copy.getHealth()==500&&copy.getDroneShield()==shield&&copy.getDroneArmor()==original.getDroneArmor(),"preserve the already-fixed role, health and defence pools");
            c.assertTrue(copy.getFlightRoll()==32.5f&&copy.getModEnergy()==energy&&copy.getPhase()==original.getPhase()&&copy.getPhaseTimer()==.75f&&!unexpectedFire,
                "combat restore: bank="+copy.getFlightRoll()+" energy="+copy.getModEnergy()+" phase="+copy.getPhase()+" timer="+copy.getPhaseTimer()+" unexpectedFire="+unexpectedFire);
        } finally {original.discard();copy.discard();target.discard();for(var e:w.getOtherEntities(null,area))if(!before.contains(e.getUuid()))e.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-corpse-roundtrip")
    public void loadedDeadDroneDoesNotRedrawItsVanillaBodyBesideTheRagdoll(TestContext c) {
        var w=c.getWorld();var at=Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(7,8,7)));var area=new Box(at.add(-16,-16,-16),at.add(16,16,16));
        var before=w.getOtherEntities(null,area).stream().map(net.minecraft.entity.Entity::getUuid).collect(java.util.stream.Collectors.toSet());
        var original=ModEntities.DRONE.create(w);var copy=ModEntities.DRONE.create(w);
        try {
            original.setPosition(at);c.assertTrue(w.spawnEntity(original),"real dying drone registered");original.damage(w.getDamageSources().outOfWorld(),10000);
            c.assertTrue(original.getHealth()==0&&original.isInvisible(),"death hands visible ownership to the ragdoll path");
            long count=w.getEntitiesByClass(com.terminaldetector.drmd.entity.RagdollEntity.class,area,e->true).size();
            copy.readNbt(original.writeNbt(new NbtCompound()));
            c.assertTrue(copy.getHealth()==0&&copy.isInvisible(),"loaded corpse must retain hidden vanilla mesh ownership");
            c.assertTrue(count==w.getEntitiesByClass(com.terminaldetector.drmd.entity.RagdollEntity.class,area,e->true).size(),"NBT restore cannot manufacture another ragdoll");
        } finally {original.discard();copy.discard();for(var e:w.getOtherEntities(null,area))if(!before.contains(e.getUuid()))e.discard();}c.complete();
    }
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
