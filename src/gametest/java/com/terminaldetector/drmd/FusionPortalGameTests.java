package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class FusionPortalGameTests implements FabricGameTest {
    private record Pair(BlockPos near,BlockPos far,boolean panels) {}
    private Pair pair(TestContext c,boolean panels) {
        var w=c.getWorld();var near=c.getAbsolutePos(new BlockPos(4,6,10));var far=near.north(6);
        for(var p:BlockPos.iterate(far.add(-1,-1,-1),near.add(1,3,1)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        var block=panels?ModWorldBlocks.PORTAL_PANEL:ModWorldBlocks.CHARGED_MIRROR;
        w.setBlockState(near,block.getDefaultState().with(net.minecraft.state.property.Properties.FACING,Direction.NORTH),3);
        w.setBlockState(far,block.getDefaultState().with(net.minecraft.state.property.Properties.FACING,Direction.SOUTH),3);
        return new Pair(near,far,panels);
    }
    private boolean linked(TestContext c,Pair p) {
        if(p.panels())return c.getWorld().getBlockState(p.near()).get(PortalPanelBlock.LINKED)
                && c.getWorld().getBlockState(p.far()).get(PortalPanelBlock.LINKED);
        return c.getWorld().getBlockState(p.near()).get(ChargedMirrorBlock.LINKED)
                && c.getWorld().getBlockState(p.far()).get(ChargedMirrorBlock.LINKED);
    }
    private ServerPlayerEntity player(TestContext c,Pair p) {
        var player=c.createMockCreativeServerPlayerInWorld();
        var data=DescentPlayerData.get(player);FlightSystem.disable(player,data);
        data.setShipAttitude(0,0,1,0,1,0);
        player.setYaw(0);player.setPitch(0);player.setPosition(Vec3d.ofCenter(p.near().north(2)));
        return player;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void placementAloneDoesNotActivateAPortalPair(TestContext c) {
        var p=pair(c,true);c.assertTrue(!linked(c,p),"panels stay inactive without a Fusion impact");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oneMinimumFusionShotActivatesBothPanelsAfterSwitchingWeapons(TestContext c) {
        var p=pair(c,true);var player=player(c,p);
        var desired=Vec3d.ofCenter(p.near()).add(0,0,-2);
        var muzzle=WeaponCore.muzzleFor(player,"fusion",1);
        player.setPosition(player.getPos().add(desired.subtract(muzzle)));
        var stack=new ItemStack(ModItems.FUSION);player.setStackInHand(Hand.MAIN_HAND,stack);
        ModItems.FUSION.onStoppedUsing(stack,c.getWorld(),player,ModItems.FUSION.getMaxUseTime(stack,player));
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        c.waitAndRun(5,()->{c.assertTrue(linked(c,p),"one minimum-charge Fusion hit powers both ends even after a weapon switch");player.discard();c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fusionProjectileKeepsActivationAndOwnerAfterNbtReload(TestContext c) {
        var p=pair(c,false);var player=player(c,p);
        var original=new ProjectileEntity(ModEntities.PROJECTILE,c.getWorld());
        original.setOwner(player);original.setFusionPortalCharge(true);original.setLifeTicks(100);
        original.setPosition(Vec3d.ofCenter(p.near()).add(0,0,-2));original.setVelocity(0,0,3);
        var nbt=new NbtCompound();original.writeNbt(nbt);
        var restored=new ProjectileEntity(ModEntities.PROJECTILE,c.getWorld());restored.readNbt(nbt);
        c.getWorld().spawnEntity(restored);
        c.waitAndRun(5,()->{c.assertTrue(linked(c,p),"restored Fusion projectile powers charged mirrors without a key");player.discard();c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void holdingFusionDoesNotTurnAnotherProjectileIntoAnActivationShot(TestContext c) {
        var p=pair(c,true);var player=player(c,p);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FUSION));
        var shot=new ProjectileEntity(ModEntities.PROJECTILE,c.getWorld());shot.setOwner(player);shot.setLifeTicks(100);
        shot.setPosition(Vec3d.ofCenter(p.near()).add(0,0,-2));shot.setVelocity(0,0,3);c.getWorld().spawnEntity(shot);
        c.waitAndRun(5,()->{c.assertTrue(!linked(c,p),"activation comes from the fired weapon, not the current hand");player.discard();c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fusionDoesNotLinkThroughWallsOrWithoutModificationPermission(TestContext c) {
        var p=pair(c,true);var player=player(c,p);var w=c.getWorld();
        w.setBlockState(p.near().north(3),Blocks.STONE.getDefaultState(),2);
        c.assertTrue(!FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && !linked(c,p),"wall stops pairing");
        w.setBlockState(p.near().north(3),Blocks.AIR.getDefaultState(),2);player.getAbilities().allowModifyWorld=false;
        c.assertTrue(!FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && !linked(c,p),"adventure permissions are respected");
        player.getAbilities().allowModifyWorld=true;player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyKeyDoesNotActivateOrConsumeItself(TestContext c) {
        var p=pair(c,false);var player=player(c,p);var key=new ItemStack(ModWorldBlocks.MIRROR_LINKER_SAME_DIMENSION);
        player.setStackInHand(Hand.MAIN_HAND,key);
        var use=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p.near()),Direction.NORTH,p.near(),false));
        key.getItem().useOnBlock(use);
        c.assertTrue(!linked(c,p) && key.getCount()==1,"legacy keys remain inert and are not consumed");player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fusionRequiresTheFrontFaceAndRepeatedActivationIsIdempotent(TestContext c) {
        var p=pair(c,true);var player=player(c,p);var w=c.getWorld();
        c.assertTrue(!FusionPortalActivation.activate(w,p.near(),Direction.SOUTH,player) && !linked(c,p),"back face is not an activation target");
        c.assertTrue(FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && linked(c,p),"front face powers the pair");
        c.assertTrue(FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && linked(c,p),"second impact preserves the active pair");
        c.assertTrue(((PortalPanelBlockEntity)w.getBlockEntity(p.near())).getLinkPartnerPos().equals(p.far()),"partner stays unchanged");
        player.discard();c.complete();
    }
}
