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
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void munitionLifetimeContinuesAcrossEntitySaveAndReload(TestContext context) {
        var first=ModEntities.AERIAL_BOMB.create(context.getWorld());
        first.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7,9,7))));
        var saved=new net.minecraft.nbt.NbtCompound(); first.writeNbt(saved); saved.putInt("FlightAge",1234);
        var loaded=ModEntities.AERIAL_BOMB.create(context.getWorld()); loaded.readNbt(saved);
        var before=new net.minecraft.nbt.NbtCompound(); loaded.writeNbt(before);
        context.assertTrue(before.getInt("FlightAge")==1234,"chunk reload preserves the spent flight lifetime");
        loaded.tick(); var after=new net.minecraft.nbt.NbtCompound(); loaded.writeNbt(after);
        context.assertTrue(after.getInt("FlightAge")==1235,"lifetime advances instead of restarting after reload");
        first.discard(); loaded.discard(); context.complete();
    }

}
