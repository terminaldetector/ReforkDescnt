package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** UG-001/005/009/021: exercise the actual player/vehicle lifecycle and velocity assignment. */
public final class FlightRecoveryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explicitOffDismountsAndCannotBeRearmedByTheOldHull(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        Vec3d position=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,6,5)));
        player.setPosition(position);
        var ship=ModEntities.PYRO_SHIP.create(world); ship.setPosition(position); world.spawnEntity(ship);
        var data=DescentPlayerData.get(player); data.setEnabled(true); data.setRoll(117);
        data.setFlightVelocity(new Vec3d(12,4,-20)); data.setDashTimer(.15f); data.setHookActive(true);
        FlightSystem.input(player).afterburner=true;
        player.startRiding(ship,true);
        FlightSystem.disable(player,data);
        context.assertTrue(!player.hasVehicle() && !ship.hasPassengers(),"H-off severs both mount links");
        context.assertTrue(!data.isEnabled() && !player.hasNoGravity(),"ordinary control restored");
        context.assertTrue(data.getFlightVelocity().lengthSquared()==0 && data.getRoll()==0 && !data.isHookActive(),"no stale flight motion");
        context.assertTrue(!FlightSystem.input(player).afterburner,"held thrust cleared");
        ship.tick();
        context.assertTrue(!data.isEnabled() && !player.hasVehicle(),"old hull cannot rearm H-off");
        context.assertTrue(LocalOrientation.getUp(player.getUuid()).squaredDistanceTo(0,1,0)<1e-9,"world-up restored");
        ship.discard(); player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeFlightTogglesDoNotManufactureVehicles(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,7,5))));
        var data=DescentPlayerData.get(player); FlightSystem.disable(player,data);
        for(int i=0;i<8;i++) {
            FlightSystem.toggle(player);
            context.assertTrue(data.isEnabled() && !player.hasVehicle(),"native H-on is free 6DoF");
            FlightSystem.toggle(player);
            context.assertTrue(!data.isEnabled() && !player.hasVehicle(),"repeated H-off stays off");
        }
        context.assertTrue(world.getEntitiesByType(ModEntities.PYRO_SHIP,player.getBoundingBox().expand(3),e->true).isEmpty(),"no orphan auto-spawned hulls");
        player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void physicalHullConsumesBlocksPerTickNotBlocksPerSecond(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        Vec3d position=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,8,5)));
        player.setPosition(position);
        PyroShipEntity ship=ModEntities.PYRO_SHIP.create(world); ship.setPosition(position); world.spawnEntity(ship);
        var data=DescentPlayerData.get(player); data.setEnabled(true);
        Vec3d perSecond=new Vec3d(20,0,-10); data.setFlightVelocity(perSecond);
        player.startRiding(ship,true); ship.tick();
        context.assertTrue(ship.getVelocity().squaredDistanceTo(new Vec3d(1,0,-.5))<1e-9,"20 m/s becomes one block/tick");
        FlightSystem.disable(player,data); ship.discard(); player.discard(); context.complete();
    }
}
