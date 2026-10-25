package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.weapon.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class BombBayVelocityGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void actualBombBayAddsPerTickInheritanceToItsEjectionVelocity(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,7,5))));
        var data=DescentPlayerData.get(player); data.setEnabled(true);
        data.setShipAttitude(0,0,1,0,1,0); data.setFlightVelocity(new Vec3d(20,0,0));
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.BOMB_CLUSTER));
        ModItems.BOMB_CLUSTER.use(context.getWorld(),player,Hand.MAIN_HAND);
        var bombs=context.getWorld().getEntitiesByType(ModEntities.AERIAL_BOMB,player.getBoundingBox().expand(6),e->true);
        context.assertTrue(bombs.size()==1,"real bomb-bay use created one munition");
        context.assertTrue(bombs.getFirst().getVelocity().squaredDistanceTo(new Vec3d(1,-.45,.65))<1e-9,
                "20 m/s inherited as one block/tick, not twenty");
        bombs.getFirst().discard(); data.setEnabled(false); player.discard(); context.complete();
    }
}
