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
        // The old low-Y point's0.55m bay AABB crossed the template's actual barrier wall.
        // This is an inheritance fixture, so admit a genuinely clear loaded muzzle first.
        var at=context.getAbsolutePos(new BlockPos(5,140,5));
        for(int x=(at.getX()>>4)-2;x<=(at.getX()>>4)+2;x++)for(int z=(at.getZ()>>4)-2;z<=(at.getZ()>>4)+2;z++)context.getWorld().getChunk(x,z);
        for(var p:BlockPos.iterate(at.add(-4,-4,-4),at.add(4,4,5)))context.getWorld().setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        player.setPosition(Vec3d.ofCenter(at));
        var data=DescentPlayerData.get(player); data.setEnabled(true);
        data.setShipAttitude(0,0,1,0,1,0); data.setFlightVelocity(new Vec3d(20,0,0));
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.BOMB_CLUSTER));
        var muzzle=com.terminaldetector.drmd.weapon.core.WeaponCore.muzzle(player,2.4f,0,-1.65f);
        context.assertTrue(com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(context.getWorld(),player.getEyePos(),muzzle)&&com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(context.getWorld(),BlockPos.ofFloored(muzzle),player),"actual loaded permitted bay before launch");
        var shape=new net.minecraft.util.math.Box(muzzle.x-.275,muzzle.y,muzzle.z-.275,muzzle.x+.275,muzzle.y+.55,muzzle.z+.275);
        context.assertTrue(context.getWorld().isSpaceEmpty(player,shape),"real bomb-size bay clearance, not unchecked barrier clipping");
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
