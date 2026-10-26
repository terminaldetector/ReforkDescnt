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
    @GameTest(templateName="drmd:portal_lab")
    public void mirrorAndPortalFramesUseVisibleBlockModels(TestContext c) {
        for(var block:new net.minecraft.block.Block[]{ModWorldBlocks.MIRROR,ModWorldBlocks.CHARGED_MIRROR,ModWorldBlocks.PORTAL_PANEL})
            c.assertTrue(block.getDefaultState().getRenderType()==net.minecraft.block.BlockRenderType.MODEL,"placed frame has a visible model: "+block);
        c.complete();
    }
    @GameTest(templateName="drmd:portal_lab")
    public void adjacentMirrorFramesJoinAndRestoreAfterRemovalOnAllFaces(TestContext c) {
        var world=c.getWorld();var origin=c.getAbsolutePos(new BlockPos(2,7,2));
        for(var block:new net.minecraft.block.Block[]{ModWorldBlocks.MIRROR,ModWorldBlocks.CHARGED_MIRROR})
        for(var facing:Direction.values()) {
            var direction=MirrorEdges.directions(facing)[1];var neighbour=origin.offset(direction);
            var state=block.getDefaultState().with(net.minecraft.state.property.Properties.FACING,facing);
            world.setBlockState(origin,state,3);world.setBlockState(neighbour,state,3);
            c.assertTrue(!world.getBlockState(origin).get(MirrorEdges.FRAME[1]),"shared edge removed "+facing);
            c.assertTrue(!world.getBlockState(neighbour).get(MirrorEdges.FRAME[0]),"reverse edge removed "+facing);
            world.setBlockState(neighbour,Blocks.AIR.getDefaultState(),3);
            c.assertTrue(world.getBlockState(origin).get(MirrorEdges.FRAME[1]),"frame restored after break "+facing);
            world.setBlockState(origin,Blocks.AIR.getDefaultState(),3);
        }
        c.complete();
    }
    @GameTest(templateName="drmd:portal_lab")
    public void linkedAndDifferentMirrorTypesKeepTheirOwnFrames(TestContext c) {
        var world=c.getWorld();var a=c.getAbsolutePos(new BlockPos(2,7,2));var b=a.east();
        world.setBlockState(a,ModWorldBlocks.MIRROR.getDefaultState(),3);
        world.setBlockState(b,ModWorldBlocks.CHARGED_MIRROR.getDefaultState(),3);
        c.assertTrue(world.getBlockState(a).get(MirrorEdges.FRAME[1]),"different materials retain frame");
        world.setBlockState(a,ModWorldBlocks.CHARGED_MIRROR.getDefaultState(),3);
        c.assertTrue(!world.getBlockState(a).get(MirrorEdges.FRAME[1]),"same materials join");
        world.setBlockState(b,world.getBlockState(b).with(ChargedMirrorBlock.LINKED,true),3);
        c.assertTrue(world.getBlockState(a).get(MirrorEdges.FRAME[1]),"portal does not join mirror");
        c.complete();
    }
    @GameTest(templateName="drmd:portal_lab")
    public void legacyDisconnectedFramesReconcileWithoutRebuildingTheWall(TestContext c) {
        var world=c.getWorld();var a=c.getAbsolutePos(new BlockPos(2,7,2));var b=a.east();
        world.setBlockState(a,ModWorldBlocks.MIRROR.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
        world.setBlockState(b,ModWorldBlocks.MIRROR.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
        c.assertTrue(world.getBlockState(a).get(MirrorEdges.FRAME[1]),"legacy state initially has its full frame");
        c.waitAndRun(2,()->{
            c.assertTrue(!world.getBlockState(a).get(MirrorEdges.FRAME[1]),"loaded wall is joined by native ticker");
            c.assertTrue(!world.getBlockState(b).get(MirrorEdges.FRAME[0]),"both legacy edges reconciled");
            c.complete();
        });
    }
    @GameTest(templateName="drmd:portal_lab")
    public void legacyCleanupDoesNotDiscardAnUnrelatedEntityWithAStoredUuid(TestContext c) {
        var world=c.getWorld();var entity=new net.minecraft.entity.decoration.ArmorStandEntity(world,0,0,0);
        world.spawnEntity(entity);
        LegacyPortalEntities.detach(world,entity.getUuid());
        c.assertTrue(!entity.isRemoved(),"migration only retires the owned external entity type");
        entity.discard();c.complete();
    }
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
    @GameTest(templateName="drmd:portal_lab")
    public void placementAloneDoesNotActivateAPortalPair(TestContext c) {
        var p=pair(c,true);c.assertTrue(!linked(c,p),"panels stay inactive without a Fusion impact");
        c.complete();
    }
    @GameTest(templateName="drmd:portal_lab")
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
    @GameTest(templateName="drmd:portal_lab")
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
    @GameTest(templateName="drmd:portal_lab")
    public void holdingFusionDoesNotTurnAnotherProjectileIntoAnActivationShot(TestContext c) {
        var p=pair(c,true);var player=player(c,p);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FUSION));
        var shot=new ProjectileEntity(ModEntities.PROJECTILE,c.getWorld());shot.setOwner(player);shot.setLifeTicks(100);
        shot.setPosition(Vec3d.ofCenter(p.near()).add(0,0,-2));shot.setVelocity(0,0,3);c.getWorld().spawnEntity(shot);
        c.waitAndRun(5,()->{c.assertTrue(!linked(c,p),"activation comes from the fired weapon, not the current hand");player.discard();c.complete();});
    }
    @GameTest(templateName="drmd:portal_lab")
    public void fusionDoesNotLinkThroughWallsOrWithoutModificationPermission(TestContext c) {
        var p=pair(c,true);var player=player(c,p);var w=c.getWorld();
        w.setBlockState(p.near().north(3),Blocks.STONE.getDefaultState(),2);
        c.assertTrue(!FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && !linked(c,p),"wall stops pairing");
        w.setBlockState(p.near().north(3),Blocks.AIR.getDefaultState(),2);player.getAbilities().allowModifyWorld=false;
        c.assertTrue(!FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && !linked(c,p),"adventure permissions are respected");
        player.getAbilities().allowModifyWorld=true;player.discard();c.complete();
    }
    @GameTest(templateName="drmd:portal_lab")
    public void legacyKeyDoesNotActivateOrConsumeItself(TestContext c) {
        var p=pair(c,false);var player=player(c,p);var key=new ItemStack(ModWorldBlocks.MIRROR_LINKER_SAME_DIMENSION);
        player.setStackInHand(Hand.MAIN_HAND,key);
        var use=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p.near()),Direction.NORTH,p.near(),false));
        key.getItem().useOnBlock(use);
        c.assertTrue(!linked(c,p) && key.getCount()==1,"legacy keys remain inert and are not consumed");player.discard();c.complete();
    }
    @GameTest(templateName="drmd:portal_lab")
    public void fusionRequiresTheFrontFaceAndRepeatedActivationIsIdempotent(TestContext c) {
        var p=pair(c,true);var player=player(c,p);var w=c.getWorld();
        c.assertTrue(!FusionPortalActivation.activate(w,p.near(),Direction.SOUTH,player) && !linked(c,p),"back face is not an activation target");
        c.assertTrue(FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && linked(c,p),"front face powers the pair");
        c.assertTrue(FusionPortalActivation.activate(w,p.near(),Direction.NORTH,player) && linked(c,p),"second impact preserves the active pair");
        c.assertTrue(((PortalPanelBlockEntity)w.getBlockEntity(p.near())).getLinkPartnerPos().equals(p.far()),"partner stays unchanged");
        player.discard();c.complete();
    }
}
