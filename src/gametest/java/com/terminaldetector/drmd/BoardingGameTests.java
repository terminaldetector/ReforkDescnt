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
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-restored-field-owner")
    public void savedCarrierWinsOverAnEarlierLoadedOverlappingModuleField(TestContext c){restoredFieldOwner(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-restored-field-wait")
    public void pendingSavedCarrierCannotBeReplacedByAnOverlappingModuleField(TestContext c){restoredFieldOwner(c,true);}
    private void restoredFieldOwner(TestContext c,boolean missing){
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,140,8));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        var q=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),140);
        var origin=Vec3d.of(at);var childOrigin=origin.add(com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.mc(q.rotate(new Vec3(0,3,0))));
        var child=BlockBodyEntity.createLocal(w,java.util.List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),childOrigin,q,Vec3d.ZERO,Vec3d.ZERO);
        var cells=new java.util.ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<4;x++)for(int z=0;z<4;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
        var root=BlockBodyEntity.createLocal(w,cells,origin,q,Vec3d.ZERO,Vec3d.ZERO);var p=c.createMockCreativeServerPlayerInWorld();
        try{
            child.setPassengerGravity(new Vec3(0,-16,0));root.setPassengerGravity(new Vec3(0,-32,0));child.tick();root.tick();
            c.assertTrue(child.getId()<root.getId(),"module must be encountered before the saved carrier");
            DescentPlayerData.get(p).setEnabled(false);p.getAbilities().flying=false;p.setPosition(root.localToWorld(new Vec3d(1.5,1.001,1.5)));p.setVelocity(Vec3d.ZERO);
            com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());
            var id=missing?java.util.UUID.randomUUID():root.getUuid();DescentPlayerData.get(p).rememberBoarding(id,w.getRegistryKey().getValue().toString(),q);
            var saved=new net.minecraft.nbt.NbtCompound();DescentPlayerData.get(p).writeNbt(saved);DescentPlayerData.get(p).readNbt(saved);
            c.assertTrue(DescentPlayerData.get(p).pendingBoardingResume()!=null,"actual persisted player data restores a pending carrier admission");
            com.terminaldetector.drmd.world.contraption.boarding.BoardingPose.publish(p,q);var feet=p.getPos();BoardingSystem.ready(p,BoardingNetworking.PROTOCOL);BoardingSystem.tick(w);
            if(missing)c.assertTrue(!BoardingSystem.drives(p)&&DescentPlayerData.get(p).pendingBoardingResume().body().equals(id),"saved missing carrier keeps admission pending instead of stealing the module's field");
            else{var state=BoardingSystem.state(p);c.assertTrue(state!=null&&state.gravityOwner()==root.getId(),"restore must prefer the saved carrier UUID over an earlier module");c.assertTrue(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(state.frame(),q))>1-1e-10,"saved rotated passenger frame is retained");}
            c.assertTrue(p.getPos().distanceTo(feet)<1e-8,"restored owner selection cannot teleport or reposition the passenger");
        }finally{root.discard();child.discard();p.discard();BoardingSystem.tick(w);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-untracked-pivot-snapshot")
    public void untrackedPivotStillSendsItsRealBodySnapshot(TestContext c) {
        var w=c.getWorld();w.getChunk(3125,3125);
        var body=BlockBodyEntity.createLocal(w,java.util.List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),new Vec3d(50000,160,50000));
        try {
            c.assertTrue(w.getEntityById(body.getId())==null,"fixture must exercise a loaded physical hull outside vanilla pivot tracking");
            body.physics().withLinearVelocity(new Vec3(17,3,-4));body.setPassengerGravity(new Vec3(0,-23,0));
            var snapshot=BoardingSystem.bodySnapshot(w,body.getId());
            c.assertTrue(snapshot!=null&&snapshot.vx()==17&&snapshot.vy()==3&&snapshot.vz()==-4&&snapshot.gy()==-23,"real outgoing snapshot cannot omit an untracked physical identity");
            c.assertTrue(snapshot.shapeRevision()==body.shapeRevision()&&snapshot.x()==body.massPosition().x(),"geometry and COM come from the same physical body");
            body.discard();c.assertTrue(BoardingSystem.bodySnapshot(w,body.getId())==null,"removed hull cannot supply phantom boarding geometry");
        } finally {body.discard();}c.complete();
    }
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
        BoardingSystem.ready(p,com.terminaldetector.drmd.network.BoardingNetworking.PROTOCOL);
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
    public void anExternalCollisionCheckedMoveCannotSeparateServerEyesFromPublishedFeet(TestContext c) {
        try(var f=fixture(c)) {
            f.body.setPassengerGravity(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,-32,0));
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"physical contact acquired");
            f.p.move(net.minecraft.entity.MovementType.SELF,new Vec3d(.25,.5,0));
            var before=f.p.getPos();f.body.tick();BoardingSystem.tick(c.getWorld());
            var feet=BoardingSystem.state(f.p).feet();
            c.assertTrue(f.p.getPos().distanceTo(new Vec3d(feet.x(),feet.y(),feet.z()))<1e-7,"packet feet and the actual actor must share one world position");
            c.assertTrue(f.p.getX()>before.x-.05,"external displacement is preserved through contact forces, not erased by teleport-follow");
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
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-connected-deck-transfer")
    public void walkingAcrossJoinedMovingDecksTransfersTheActualServerContact(TestContext c) {
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,140,8));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        for(var pos:BlockPos.iterate(at.add(-4,-4,-4),at.add(12,8,12)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
        var cells=new java.util.ArrayList<BlockBodyEntity.Cell>();
        for(int x=0;x<3;x++)for(int z=0;z<6;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
        var a=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at));var b=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at.add(3,0,0)));
        var q=com.terminaldetector.drmd.client.portal.PortalTransform.Quat.IDENTITY;
        var link=new com.terminaldetector.drmd.world.contraption.BodyJointLink(java.util.UUID.randomUUID(),
            new com.terminaldetector.drmd.world.contraption.BodyJointLink.Endpoint(a.getUuid(),new BlockPos(2,0,3),new Vec3(3,.5,3),q),
            new com.terminaldetector.drmd.world.contraption.BodyJointLink.Endpoint(b.getUuid(),new BlockPos(0,0,3),new Vec3(0,.5,3),q),
            com.terminaldetector.drmd.physics.collision.BodyJointConstraint.Kind.FIXED,0,0,0,0,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        var registry=com.terminaldetector.drmd.world.contraption.BodyJointRegistry.get(w);registry.add(w,a,b,link);
        var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.getAbilities().flying=false;
        p.setPosition(at.getX()+2.4,at.getY()+1,at.getZ()+3);p.setVelocity(0,0,.15);p.setYaw(0);p.setPitch(0);
        com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());BoardingSystem.ready(p,BoardingNetworking.PROTOCOL);
        a.physics().withLinearVelocity(new Vec3(0,0,3));b.physics().withLinearVelocity(new Vec3(0,0,3));
        BoardingSystem.tick(w);
        c.assertTrue(BoardingSystem.drives(p)&&BoardingSystem.state(p).support()==a.getId(),"initial contact acquired on the first real deck: "+BoardingSystem.state(p));
        c.waitAndRun(1,()->{try {
            a.tick();b.tick();com.terminaldetector.drmd.world.contraption.BodyContactSystem.solve(w);BoardingSystem.tick(w);
            c.assertTrue(BoardingSystem.drives(p)&&BoardingSystem.state(p).support()==a.getId(),"server retains first moving deck beside its joined edge: state="+BoardingSystem.state(p)+" feet="+p.getPos()+" a="+a.getPos()+" b="+b.getPos());
            int leftSamples=0,rightSamples=0;double zBefore=p.getZ();
            for(int i=0;i<16;i++) {
                c.assertTrue(BoardingSystem.receive(p,new BoardingNetworking.Input(BoardingSystem.epoch(p),i,1,0,false,0,0,1)),"bounded real walking command accepted");
                a.tick();b.tick();com.terminaldetector.drmd.world.contraption.BodyContactSystem.solve(w);BoardingSystem.tick(w);
                var state=BoardingSystem.state(p);c.assertTrue(state!=null&&state.grounded(),"joined seam cannot release a walking player's server contact");
                double localX=a.worldToLocal(p.getPos()).x;
                if(localX<2.65){leftSamples++;c.assertTrue(state.support()==a.getId(),"a neighbouring toe contact cannot steal support before the seam");}
                if(localX>3.35){rightSamples++;c.assertTrue(state.support()==b.getId(),"walking beyond the seam transfers support to the second physical component");}
                c.assertTrue(p.getPos().distanceTo(new Vec3d(state.feet().x(),state.feet().y(),state.feet().z()))<1e-7,"actual actor and outgoing contact state agree throughout transfer");
            }
            c.assertTrue(leftSamples>0&&rightSamples>0&&p.getZ()>zBefore+1,"walk crosses both real decks while the joined assembly continues translating");
            c.assertTrue(registry.find(link.id()).isPresent(),"contact transfer does not replace or sever the moving physical assembly");
        } finally {registry.remove(link.id());a.discard();b.discard();p.discard();BoardingSystem.tick(w);}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-external-teleport")
    public void anExternalTeleportRetiresOldWalkingCommandsBeforeSendingTheNewPosition(TestContext c) {
        try(var f=fixture(c)) {
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(f.p),"initial physical contact acquired");
            int epoch=BoardingSystem.epoch(f.p);var target=f.p.getPos().add(20,4,20);
            f.p.teleport(c.getWorld(),target.x,target.y,target.z,java.util.Set.of(),0,0);
            c.assertTrue(!BoardingSystem.drives(f.p)&&BoardingSystem.state(f.p)==null,"explicit vanilla transfer must retire old movement authority immediately");
            c.assertTrue(!BoardingSystem.receive(f.p,new BoardingNetworking.Input(epoch,0,1,0,false,0,0,1)),"old walking input cannot act during the teleport handoff");
            c.assertTrue(f.p.getPos().distanceTo(target)<1e-7,"old boarding state cannot overwrite the external target");
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
