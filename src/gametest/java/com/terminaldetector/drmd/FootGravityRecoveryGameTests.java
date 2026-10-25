package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

public final class FootGravityRecoveryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clientMirrorsCannotEraseOrReplaceServerGravity(TestContext context) {
        UUID id=UUID.randomUUID();
        FootGravitySystem.adoptServer(id,new Vec3d(1,0,0),"server wall");
        FootGravitySystem.adoptClient(id,new Vec3d(0,-1,0));
        context.assertTrue(FootGravitySystem.get(id).up().x>.99,"client ceiling did not replace server wall");
        FootGravitySystem.clearClient(id);
        context.assertTrue(FootGravitySystem.isActive(id),"late client off packet did not erase authoritative field");
        FootGravitySystem.forget(id); context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bodyAndInteractionEyeUseTheSameWallFrameAndReturnUpright(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,6,5))));
        FootGravitySystem.adoptServer(player.getUuid(),new Vec3d(1,0,0),"wall");
        player.calculateDimensions();
        var wallBox=player.getBoundingBox();
        var eye=player.getEyePos().subtract(player.getPos());
        context.assertTrue(wallBox.getLengthX()>1.7 && wallBox.getLengthY()<.7,"actual collision body lies along local up");
        context.assertTrue(eye.x>1.5 && Math.abs(eye.y)<1e-6,"interaction eye is above the feet along the same wall");
        FootGravitySystem.clear(player.getUuid()); player.calculateDimensions();
        context.assertTrue(player.getBoundingBox().getLengthY()>1.7,"ordinary standing collision returned");
        context.assertTrue(player.getEyePos().y-player.getY()>1.5,"ordinary eye returned");
        FootGravitySystem.forget(player.getUuid()); player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void leavingEveryRoomFaceRestoresAClearStandingBody(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        var world=context.getWorld();
        var centre=context.getAbsolutePos(new BlockPos(5,6,5));
        for(var pos:BlockPos.iterate(centre.add(-3,-3,-3),centre.add(3,3,3))) {
            boolean shell=Math.abs(pos.getX()-centre.getX())==3 || Math.abs(pos.getY()-centre.getY())==3 || Math.abs(pos.getZ()-centre.getZ())==3;
            world.setBlockState(pos,shell?net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState():net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        }
        Vec3d middle=Vec3d.ofCenter(centre);
        for(Vec3d up:new Vec3d[]{new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1)}) {
            player.setPosition(middle.subtract(up.multiply(2.44)));
            FootGravitySystem.adoptServer(player.getUuid(),up,"room face");player.calculateDimensions();
            context.assertTrue(world.isSpaceEmpty(player,player.getBoundingBox()),"local standing body fits on room face "+up);
            com.terminaldetector.drmd.flight.FlightSystem.enableWithoutMount(player,DescentPlayerData.get(player));
            context.assertTrue(!FootGravitySystem.isActive(player) && world.isSpaceEmpty(player,player.getBoundingBox()),"H release gives upright clearance for "+up);
            context.assertTrue(player.getBoundingBox().getLengthY()>1.7,"standing height restored for "+up);
            com.terminaldetector.drmd.flight.FlightSystem.disable(player,DescentPlayerData.get(player));
        }
        FootGravitySystem.forget(player.getUuid());player.discard();context.complete();
    }
}
