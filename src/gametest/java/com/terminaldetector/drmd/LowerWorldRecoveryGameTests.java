package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.level.LowerWorldRecovery;
import com.terminaldetector.drmd.world.layer.WorldLayer;
import com.terminaldetector.drmd.flight.FlightSystem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class LowerWorldRecoveryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void lowerBoundaryReturnsToClearSpaceWithControlBeforeVoidDamage(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        var world=context.getWorld();
        Vec3d safe=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,7,5)));
        player.setPosition(safe);
        context.assertTrue(!LowerWorldRecovery.recoverOrRemember(player),"ordinary in-bounds play is untouched");
        player.setPosition(safe.x,world.getBottomY()-80,safe.z);
        var data=DescentPlayerData.get(player); data.setFlightVelocity(new Vec3d(0,-200,0));
        context.assertTrue(LowerWorldRecovery.recoverOrRemember(player),"underflow recovers before ordinary void death");
        context.assertTrue(player.getPos().squaredDistanceTo(safe)<1e-6,"last clear position restored");
        context.assertTrue(player.isAlive() && data.isEnabled() && !player.hasVehicle(),"free control restored without manufacturing a hull");
        context.assertTrue(data.getFlightVelocity().lengthSquared()==0,"outward momentum does not immediately repeat the failure");
        context.assertTrue(!LowerWorldRecovery.recoverOrRemember(player),"recovery does not loop at its destination");
        context.assertTrue(!WorldLayer.display(world,world.getTopY()+20).insideWorld(),"HUD resolver marks actual out-of-bounds altitude");
        FlightSystem.disable(player,data); LowerWorldRecovery.clear(player.getUuid()); player.discard(); context.complete();
    }
}
