package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.network.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import java.util.*;

public final class FlightBodyPlayerGameTests implements FabricGameTest {
    private record Fixture(TestContext c,ServerPlayerEntity p,BlockBodyEntity body,BlockPos at) implements AutoCloseable {
        public void close(){body.discard();p.discard();FlightBodySystem.tick(c.getWorld());BoardingSystem.tick(c.getWorld());}
        void phase(){body.tick();BodyContactSystem.solve(c.getWorld());FlightBodySystem.tick(c.getWorld());BoardingSystem.tick(c.getWorld());}
    }
    private Fixture fixture(TestContext c) {
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,180,8));
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        for(var pos:BlockPos.iterate(at.add(-4,-4,-4),at.add(12,12,12)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<8;x++)for(int z=0;z<8;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
        var b=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at));b.tick();
        var p=c.createMockCreativeServerPlayerInWorld();var d=DescentPlayerData.get(p);d.setEnabled(true);d.setFlightVelocity(new Vec3d(0,-80,0));
        p.getAbilities().flying=false;p.setPosition(at.getX()+4,at.getY()+3,at.getZ()+4);p.setVelocity(new Vec3d(0,-4,0));
        com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());BoardingSystem.ready(p,BoardingNetworking.PROTOCOL);
        return new Fixture(c,p,b,at);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-body-phase")
    public void actualFlightTravelDefersUntilHullPhysicsAndCannotCrossTheDeck(TestContext c) {
        try(var f=fixture(c)) {
            var before=f.p.getPos();f.p.travel(Vec3d.ZERO);
            c.assertTrue(f.p.getPos().equals(before),"travel must not move once before the chronological physical phase");
            f.phase();
            c.assertTrue(f.p.getY()<before.y&&f.p.getY()>=f.at.getY()+1-1e-5,"real suit sweep lands at material instead of crossing the moving hull");
            c.assertTrue(DescentPlayerData.get(f.p).getFlightVelocity().y> -2&&f.body.physics().linearVelocity().y()<0,"resolved velocity and reaction reach the actual player/body state");
            c.assertTrue(FlightBodySystem.epoch(f.p)>0&&!BoardingSystem.drives(f.p),"flight remains the single player movement owner");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-body-network-player-phase")
    public void realNetworkPlayerDoesNotNeedToInvokeTravelToEnterThePhysicalPhase(TestContext c) {
        try(var f=fixture(c)) {
            var before=f.p.getPos();f.phase();
            c.assertTrue(FlightBodySystem.epoch(f.p)>0&&f.p.getY()<before.y&&f.p.getY()>=f.at.getY()+1-1e-5,"world phase itself must enroll network-controlled pilots and resolve real deck contact");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-body-portal-clock")
    public void portalFrameDiscontinuityDropsOnlyTheOldClockAndRetainsPhysicalMomentum(TestContext c) {
        try(var f=fixture(c)) {
            f.phase();int old=FlightBodySystem.epoch(f.p);var position=f.p.getPos();var v=DescentPlayerData.get(f.p).getFlightVelocity();
            c.assertTrue(FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(old,0)),"old frame has a real pending command");
            FlightBodySystem.discontinuity(f.p);
            c.assertTrue(f.p.getPos().equals(position)&&DescentPlayerData.get(f.p).getFlightVelocity().equals(v),"clock reset must not move or stop the physical player");
            f.phase();int next=FlightBodySystem.epoch(f.p);
            c.assertTrue(next>old&&!FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(old,1))&&FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(next,0)),"old portal-frame commands cannot poison a new lease");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-body-network")
    public void vanillaPositionCannotOverwriteARealFlightContactAndClockIsBounded(TestContext c) {
        try(var f=fixture(c)) {
            f.p.travel(Vec3d.ZERO);f.phase();var before=f.p.getPos();
            f.p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(before.x,before.y-20,before.z,false));
            c.assertTrue(f.p.getPos().equals(before),"ordinary client position cannot drag the pilot through the deck");
            int epoch=FlightBodySystem.epoch(f.p);
            c.assertTrue(!FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(epoch+1,0)),"foreign epoch rejected");
            c.assertTrue(FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(epoch,0)),"bounded owner clock accepted");
            c.assertTrue(!FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(epoch,0))&&!FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(epoch,999)),"replay and large sequence jump rejected");
            for(int i=1;i<32;i++)c.assertTrue(FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(epoch,i)),"bounded pending clock");
            c.assertTrue(!FlightBodySystem.receive(f.p,new FlightBodyNetworking.Input(epoch,32)),"queue admission cannot grow without bound");
            f.p.travel(Vec3d.ZERO);f.phase();c.assertTrue(FlightBodySystem.acknowledgment(f.p)==0,"consume at most one prediction command per physical tick");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-body-handoff")
    public void disablingFlightReleasesAuthorityToTheExistingWalkingMotorWithoutVelocityReset(TestContext c) {
        try(var f=fixture(c)) {
            f.p.travel(Vec3d.ZERO);f.phase();var d=DescentPlayerData.get(f.p);
            var velocity=BoardingSystem.mc(f.body.boardingMotion().surfaceVelocity(BoardingSystem.pure(f.p.getPos()),1)).add(2,0,1);
            f.p.setVelocity(velocity.multiply(.05));d.setFlightVelocity(velocity);d.setEnabled(false);FlightBodySystem.tick(c.getWorld());
            c.assertTrue(!FlightBodySystem.owns(f.p)&&FlightBodySystem.epoch(f.p)<0,"flight lease expires on mode handoff");
            c.assertTrue(f.p.getVelocity().equals(velocity.multiply(.05)),"release does not zero or add carrier momentum");
            f.p.travel(Vec3d.ZERO);f.body.tick();BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"actual material contact enters the existing boarding motor");
        }c.complete();
    }
}
