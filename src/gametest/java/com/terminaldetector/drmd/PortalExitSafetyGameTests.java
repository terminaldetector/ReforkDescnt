package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.portal.PortalTravel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.block.entity.BlockEntity;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity;
import com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity;

public final class PortalExitSafetyGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void panelLinkSurvivesNbtReloadAndExplicitUnlink(TestContext context) {
        var at=context.getAbsolutePos(new BlockPos(3,3,3));
        var state=ModWorldBlocks.PORTAL_PANEL.getDefaultState();
        var original=new PortalPanelBlockEntity(at,state);
        var partner=at.east(8);var attached=java.util.UUID.randomUUID();
        original.setLink(attached,partner,net.minecraft.world.World.NETHER);
        var registries=context.getWorld().getRegistryManager();
        var restored=(PortalPanelBlockEntity)BlockEntity.createFromNbt(at,state,original.createNbtWithId(registries),registries);
        context.assertTrue(restored!=null && restored.isLinked() && partner.equals(restored.getLinkPartnerPos())
                && net.minecraft.world.World.NETHER.equals(restored.getLinkPartnerDim())
                && attached.equals(restored.getAttachedEntityId()),"panel link identity and destination survive serialization");
        restored.clearLink();
        var unlinked=(PortalPanelBlockEntity)BlockEntity.createFromNbt(at,state,restored.createNbtWithId(registries),registries);
        context.assertTrue(!unlinked.isLinked() && unlinked.getLinkPartnerPos()==null
                && unlinked.getLinkPartnerDim()==null && unlinked.getAttachedEntityId()==null,"cleared panel link stays cleared after reload");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void chargedMirrorLinkSurvivesNbtReloadAndExplicitUnlink(TestContext context) {
        var at=context.getAbsolutePos(new BlockPos(3,3,3));
        var state=ModWorldBlocks.CHARGED_MIRROR.getDefaultState();
        var original=new ChargedMirrorBlockEntity(at,state);
        var partner=at.east(8);var attached=java.util.UUID.randomUUID();
        original.setLink(attached,partner,net.minecraft.world.World.NETHER);
        var registries=context.getWorld().getRegistryManager();
        var restored=(ChargedMirrorBlockEntity)BlockEntity.createFromNbt(at,state,original.createNbtWithId(registries),registries);
        context.assertTrue(restored!=null && restored.isLinked() && partner.equals(restored.getLinkPartnerPos())
                && net.minecraft.world.World.NETHER.equals(restored.getLinkPartnerDim())
                && attached.equals(restored.getAttachedEntityId()),"mirror link identity and destination survive serialization");
        restored.clearLink();
        var unlinked=(ChargedMirrorBlockEntity)BlockEntity.createFromNbt(at,state,restored.createNbtWithId(registries),registries);
        context.assertTrue(!unlinked.isLinked() && unlinked.getLinkPartnerPos()==null
                && unlinked.getLinkPartnerDim()==null && unlinked.getAttachedEntityId()==null,"cleared mirror link stays cleared after reload");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rotatedNativeCrossingPreservesEntityAndVelocityAndPreventsBounce(TestContext context) {
        var world=context.getWorld();var source=context.getAbsolutePos(new BlockPos(3,5,3));var destination=source.east(8);
        for(var p:BlockPos.iterate(destination.add(-2,-1,-2),destination.add(3,3,2)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        var cow=net.minecraft.entity.EntityType.COW.create(world);
        cow.setAiDisabled(true);cow.setNoGravity(true);
        var face=Vec3d.ofCenter(source).add(0,0,-.5);
        cow.setPosition(face.add(0,0,.2));cow.prevX=face.x;cow.prevY=face.y;cow.prevZ=face.z-.2;
        cow.setVelocity(0,0,.2);world.spawnEntity(cow);var id=cow.getUuid();
        PortalTravel.carry(world,source,Direction.NORTH,destination,Direction.EAST,.75);
        context.assertTrue(cow.getX()>destination.getX()+1 && cow.getX()<destination.getX()+2,"crossing exits on the outward destination side");
        context.assertTrue(cow.getVelocity().squaredDistanceTo(new Vec3d(.2,0,0))<1e-10,"velocity rotates with the portal frame");
        context.assertTrue(world.getEntity(id)==cow,"native crossing preserves the original entity and UUID");
        var exitFace=Vec3d.ofCenter(destination).add(.5,0,0);
        cow.setPosition(exitFace.add(-.2,0,0));cow.prevX=exitFace.x+.2;cow.prevY=exitFace.y;cow.prevZ=exitFace.z;
        var before=cow.getPos();PortalTravel.carry(world,destination,Direction.EAST,source,Direction.NORTH,.75);
        context.assertTrue(cow.getPos().equals(before),"arrival cooldown rejects immediate reverse crossing");
        cow.discard();PortalTravel.clear();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void standingOnPortalPlaneDoesNotTeleportWithoutACrossing(TestContext context) {
        var world=context.getWorld();var source=context.getAbsolutePos(new BlockPos(3,5,3));
        var cow=net.minecraft.entity.EntityType.COW.create(world);cow.setAiDisabled(true);cow.setNoGravity(true);
        var at=Vec3d.ofCenter(source).add(0,0,-.5);cow.setPosition(at);cow.prevX=at.x;cow.prevY=at.y;cow.prevZ=at.z;
        cow.setVelocity(Vec3d.ZERO);world.spawnEntity(cow);
        PortalTravel.carry(world,source,Direction.NORTH,source.east(8),Direction.SOUTH,.75);
        context.assertTrue(cow.getPos().equals(at) && cow.getVelocity().equals(Vec3d.ZERO),"touching or looking at the face is not a crossing");
        cow.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void panelTickDoesNotLoadAnUnavailablePartner(TestContext context) {
        var world=context.getWorld();
        var at=context.getAbsolutePos(new BlockPos(3,3,3));
        var far=new BlockPos(2_000_000,100,2_000_000);
        var state=com.terminaldetector.drmd.entity.ModWorldBlocks.PORTAL_PANEL.getDefaultState();
        var panel=new com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity(at,state);
        panel.setLink(null,far,world.getRegistryKey());
        context.assertTrue(!world.isChunkLoaded(far),"partner starts unavailable");
        com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity.tick(world,at,state,panel);
        context.assertTrue(!world.isChunkLoaded(far),"panel tick must not request the partner chunk");
        context.assertTrue(panel.isLinked() && far.equals(panel.getLinkPartnerPos()),"temporary unavailability preserves the saved link");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void chargedMirrorTickDoesNotLoadAnUnavailablePartner(TestContext context) {
        var world=context.getWorld();
        var at=context.getAbsolutePos(new BlockPos(3,3,3));
        var far=new BlockPos(-2_000_000,100,-2_000_000);
        var state=com.terminaldetector.drmd.entity.ModWorldBlocks.CHARGED_MIRROR.getDefaultState();
        var mirror=new com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity(at,state);
        mirror.setLink(null,far,world.getRegistryKey());
        context.assertTrue(!world.isChunkLoaded(far),"partner starts unavailable");
        com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity.tick(world,at,state,mirror);
        context.assertTrue(!world.isChunkLoaded(far),"mirror tick must not request the partner chunk");
        context.assertTrue(mirror.isLinked() && far.equals(mirror.getLinkPartnerPos()),"temporary unavailability preserves the saved link");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clearFeetDoNotPermitAnExitWithTheHeadInsideAStoneBlock(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        var at=context.getAbsolutePos(new BlockPos(5,5,5));var origin=Vec3d.ofCenter(at.west(3));player.setPosition(origin);
        world.setBlockState(at,Blocks.AIR.getDefaultState(),2);world.setBlockState(at.up(),Blocks.STONE.getDefaultState(),2);
        context.assertTrue(!PortalTravel.exitIsClear(world,player,Vec3d.ofCenter(at)),"whole body is tested, not only the destination cell");
        context.assertTrue(PortalTravel.exitIsClear(world,player,Vec3d.ofCenter(at.east(2))),"clear destination is allowed");
        context.assertTrue(player.getPos().equals(origin),"preflight validation never moves the traveller");player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void heightAndUnloadedExitRejectionsPreserveTheTraveller(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        var origin=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,5,5)));player.setPosition(origin);
        context.assertTrue(!PortalTravel.exitIsClear(world,player,new Vec3d(origin.x,world.getTopY()-1,origin.z)),"head cannot exit above the runtime height limit");
        context.assertTrue(!PortalTravel.exitIsClear(world,player,new Vec3d(500000,80,500000)),"unloaded exit is declined");
        context.assertTrue(!world.isChunkLoaded(new BlockPos(500000,80,500000)) && player.getPos().equals(origin),"no chunk generation or partial teleport");
        player.discard();context.complete();
    }
}
