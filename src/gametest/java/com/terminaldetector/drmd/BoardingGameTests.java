package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** Real server-owned player motor. Full rotated local gravity/UFO acceptance remains open. */
public final class BoardingGameTests implements FabricGameTest {
    private record Fixture(TestContext c,ServerPlayerEntity p,BlockBodyEntity body) implements AutoCloseable {
        public void close(){body.discard();p.discard();BoardingSystem.tick(c.getWorld());}
    }
    private Fixture fixture(TestContext c) {
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,140,8));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        for(var b:BlockPos.iterate(at.add(-4,-4,-4),at.add(9,8,9)))w.setBlockState(b,Blocks.AIR.getDefaultState(),2);
        for(var b:BlockPos.iterate(at,at.add(5,0,5)))w.setBlockState(b,Blocks.IRON_BLOCK.getDefaultState(),2);
        var body=BlockBodyEntity.assemble(w,at,at.add(5,0,5));body.tick();
        var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);
        p.getAbilities().flying=false;p.setPosition(at.getX()+3,at.getY()+1,at.getZ()+3);p.setVelocity(Vec3d.ZERO);p.setYaw(0);p.setPitch(0);
        com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());
        BoardingSystem.ready(p,1);
        return new Fixture(c,p,body);
    }
    private BoardingNetworking.Input input(Fixture f,int sequence,float strafe,boolean jump) {
        return new BoardingNetworking.Input(BoardingSystem.epoch(f.p),sequence,strafe,0,jump,0,0,1);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-server-walk")
    public void serverContactOwnsThePlayerAndWalkingPushesBackOnTheDeck(TestContext c) {
        try(var f=fixture(c)) {
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"a real compound floor acquires the server player");
            double before=f.p.getX();c.assertTrue(BoardingSystem.receive(f.p,input(f,0,1,false)),"valid bounded walking input accepted");
            f.body.tick();BoardingSystem.tick(c.getWorld());
            c.assertTrue(f.p.getX()>before+.02,"server integrates walking instead of accepting a client position");
            c.assertTrue(f.body.physics().linearVelocity().x()<0,"the actual physical deck receives the opposite foot impulse");
            c.assertTrue(BoardingSystem.state(f.p).grounded(),"actual player retains support contact");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-server-input")
    public void staleInvalidAndForgedPositionPacketsCannotMoveTheContactPlayer(TestContext c) {
        try(var f=fixture(c)) {
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"fixture acquired");
            c.assertTrue(BoardingSystem.receive(f.p,input(f,0,0,false)),"first input accepted");
            c.assertTrue(!BoardingSystem.receive(f.p,input(f,0,1,false)),"duplicate input rejected");
            c.assertTrue(!BoardingSystem.receive(f.p,input(f,9999,1,false)),"unbounded sequence advance rejected");
            c.assertTrue(!BoardingSystem.receive(f.p,input(f,1,Float.NaN,false)),"NaN input rejected");
            var before=f.p.getPos();
            f.p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(before.x+100,before.y+100,before.z,true));
            c.assertTrue(f.p.getPos().distanceTo(before)<1e-9,"client coordinates cannot overwrite server-owned contact movement");
            c.assertTrue(BoardingSystem.receive(f.p,input(f,1,0,false)),"rejected packet does not poison the next valid input");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-server-jump")
    public void jumpingReleasesThePlayerWithItsExistingCarrierWorldVelocity(TestContext c) {
        try(var f=fixture(c)) {
            f.body.physics().withLinearVelocity(new Vec3(3,0,0));f.p.setVelocity(.15,0,0);
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"moving deck contact acquired");
            c.assertTrue(BoardingSystem.receive(f.p,input(f,0,0,true)),"jump input accepted");
            f.body.tick();BoardingSystem.tick(c.getWorld());
            c.assertTrue(!BoardingSystem.drives(f.p),"jump releases the support lease");
            c.assertTrue(Math.abs(f.p.getVelocity().x-.15)<1e-7,"3m/s carrier speed is preserved once, in Minecraft tick units");
            c.assertTrue(f.p.getVelocity().y>.1&&!f.p.isOnGround(),"world-up jump and ordinary airborne handoff");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-flight-handoff")
    public void enablingSixDofReleasesBoardingWithoutRearmingOrSpawningAHull(TestContext c) {
        try(var f=fixture(c)) {
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"fixture acquired");
            DescentPlayerData.get(f.p).setEnabled(true);BoardingSystem.tick(c.getWorld());
            c.assertTrue(!BoardingSystem.drives(f.p)&&BoardingSystem.state(f.p)==null,"6DoF owns movement after release");
            c.assertTrue(DescentPlayerData.get(f.p).isEnabled()&&!f.p.hasVehicle(),"boarding cannot disable flight or manufacture a vehicle");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-body-removal")
    public void removingTheSupportingBodyCannotLeaveAnActivePassengerLease(TestContext c) {
        try(var f=fixture(c)) {
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"fixture acquired");
            f.body.discard();BoardingSystem.tick(c.getWorld());
            c.assertTrue(!BoardingSystem.drives(f.p)&&!f.p.isOnGround(),"removed geometry releases the player instead of holding a stale body ID");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-protocol-ready")
    public void aClientWithoutTheBoardingProtocolCannotBeCapturedByTheMotor(TestContext c) {
        try(var f=fixture(c)) {
            // A new player object has not sent Ready; its old/vanilla movement remains untouched.
            var other=c.createMockCreativeServerPlayerInWorld();
            try {
                DescentPlayerData.get(other).setEnabled(false);other.getAbilities().flying=false;other.setPosition(f.p.getPos().add(.5,0,0));
                BoardingSystem.tick(c.getWorld());
                c.assertTrue(!BoardingSystem.drives(other),"ordinary client without Ready is never movement-locked");
                c.assertTrue(!BoardingSystem.ready(other,999),"unsupported protocol version rejected");
                BoardingSystem.tick(c.getWorld());c.assertTrue(!BoardingSystem.drives(other),"invalid protocol cannot acquire a lease");
            } finally {other.discard();}
        }c.complete();
    }
}
