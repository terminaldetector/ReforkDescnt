package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual server entities/physics; no fake availability, physics stepping or packet injection. */
public final class PortalTransferGameTests implements FabricGameTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private BlockPos origin(TestContext c){return c.getAbsolutePos(new BlockPos(8,130,8));}
    private void room(TestContext c,BlockPos at,int rx,int ry,int rz){
        var w=c.getWorld();for(int x=(at.getX()-rx-1)>>4;x<=(at.getX()+rx+1)>>4;x++)for(int z=(at.getZ()-rz-1)>>4;z<=(at.getZ()+rz+1)>>4;z++)w.getChunk(x,z);
        for(var p:BlockPos.iterate(at.add(-rx,-ry,-rz),at.add(rx,ry,rz)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
    }
    private static Vec3 p(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    private void near(TestContext c,Vec3 actual,Vec3 wanted,String why){c.assertTrue(actual.minus(wanted).lengthSquared()<1e-12,why+" actual="+actual+" expected="+wanted);}
    private BodyJointLink joint(BlockBodyEntity a,BlockBodyEntity b){return new BodyJointLink(UUID.randomUUID(),
        new BodyJointLink.Endpoint(a.getUuid(),BlockPos.ORIGIN,new Vec3(.5,.5,.5),Quat.IDENTITY),
        new BodyJointLink.Endpoint(b.getUuid(),BlockPos.ORIGIN,new Vec3(.5,.5,.5),Quat.IDENTITY),
        BodyJointConstraint.Kind.FIXED,0,0,0,0,10000,10000,false);}
    private D6PortalTransform mapping(Vec3 source,Vec3 dest){return D6PortalTransform.of(source,new Vec3(0,0,-1),dest,new Vec3(1,0,0));}
    private PortalTransfer.Attempt prepare(TestContext c,Entity root,D6PortalTransform t){return PortalTransfer.prepare(c.getWorld(),root,t,ZERO,t.sourcePoint(),8,false,c.getWorld().getServer().getTicks(),u->false,false);}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-group-live")
    public void jointGroupAndActualSupportedDropShareOneTransformWithoutDraggingAnEmptyGap(TestContext c){
        var w=c.getWorld();var at=origin(c);room(c,at,8,6,7);room(c,at.east(28),8,6,7);
        var a=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()),new BlockBodyEntity.Cell(new BlockPos(4,0,0),Blocks.IRON_BLOCK.getDefaultState())),Vec3d.of(at));
        var b=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),Vec3d.of(at.add(6,0,0)));
        var link=joint(a,b);var registry=BodyJointRegistry.get(w);registry.add(w,a,b,link);
        var carried=new ItemEntity(w,at.getX()+.5,at.getY()+1.01,at.getZ()+.5,new ItemStack(Items.IRON_INGOT));carried.setVelocity(Vec3d.ZERO);w.spawnEntity(carried);
        var gap=new ItemEntity(w,at.getX()+2.5,at.getY()+1.01,at.getZ()+.5,new ItemStack(Items.GOLD_INGOT));gap.setVelocity(Vec3d.ZERO);w.spawnEntity(gap);
        try{
            a.physics().withLinearVelocity(new Vec3(3,2,7)).withAngularVelocity(new Vec3(.2,.1,.3));b.physics().withLinearVelocity(new Vec3(-2,1,4));
            a.physics().applyForce(new Vec3(11,5,-9));a.physics().applyTorque(new Vec3(2,7,3));
            // A resting material contact uses its carrier's actual surface velocity, not a guessed broad AABB.
            carried.setVelocity(a.surfaceVelocityAt(carried.getPos()).multiply(.05));
            var oldA=a.massPosition();var oldB=b.massPosition();var oldItem=p(carried.getPos());var gapAt=gap.getPos();var oldV=a.physics().linearVelocity();var oldL=a.physics().angularMomentum();var oldF=a.physics().accumulatedForce();
            var proposed=prepare(c,a,mapping(oldA,p(Vec3d.ofCenter(at.east(28)))));var plan=proposed.accepted();
            c.assertTrue(plan!=null&&proposed.members().contains(b)&&proposed.members().contains(carried)&&!proposed.members().contains(gap),"real joint and supported item admitted, empty gap rejected");
            var t=plan.transform;c.assertTrue(plan.apply(),"whole prepared transaction commits");
            near(c,a.massPosition(),t.transformPoint(oldA),"root COM has one authority");near(c,b.massPosition(),t.transformPoint(oldB),"child keeps common rigid map");near(c,p(carried.getPos()),t.transformPoint(oldItem),"supported drop keeps same group translation");
            near(c,a.physics().linearVelocity(),t.transformVelocity(oldV),"physical velocity is not overwritten by entity memo");near(c,a.physics().angularMomentum(),t.transformAngularMomentum(oldL),"angular momentum rotated once");near(c,a.physics().accumulatedForce(),t.rotation().rotate(oldF),"pending force rotates without an extra integration");
            c.assertTrue(gap.getPos().equals(gapAt)&&registry.find(link.id()).orElseThrow().equals(link),"unowned drop and persistent joint identity retained");
            c.assertTrue(registry.lastBounds(a.getUuid()).orElseThrow().equals(a.loadingBounds())&&!plan.apply(),"registry points to moved hull and commit is single-use");
            var saved=a.writeNbt(new net.minecraft.nbt.NbtCompound()).getCompound("body");
            c.assertTrue(saved.getLong("portalEpoch")==1&&saved.getDouble("originx")==a.getX()&&saved.getDouble("originy")==a.getY()&&saved.getDouble("originz")==a.getZ(),"saved/tracked portal pose carries one epoch and the actual pivot, not a guessed COM");
        }finally{registry.remove(link.id());a.discard();b.discard();carried.discard();gap.discard();PortalTravel.clear();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-group-atomic")
    public void aBlockedChildAndAChangedDestinationRefuseBeforeAnyHullMutation(TestContext c){
        var w=c.getWorld();var at=origin(c);room(c,at,5,5,5);room(c,at.east(26),8,5,8);
        var cells=List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));
        var a=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at));var b=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at.east(3)));var link=joint(a,b);var registry=BodyJointRegistry.get(w);registry.add(w,a,b,link);
        try{
            a.physics().withLinearVelocity(new Vec3(1,2,3));b.physics().withLinearVelocity(new Vec3(-3,1,2));var sa=a.physics().snapshot();var sb=b.physics().snapshot();var pa=a.getPos();var pb=b.getPos();
            var base=mapping(a.massPosition(),p(Vec3d.ofCenter(at.east(26))));var first=prepare(c,a,base).accepted();c.assertTrue(first!=null,"destination genuinely clear before wall");
            var rider=EntityType.COW.create(w);rider.setPosition(a.getPos().add(0,1,0));w.spawnEntity(rider);
            try{c.assertTrue(rider.startRiding(a,true),"actual passenger can join after preparation");c.assertTrue(!first.apply()&&a.physics().matches(sa)&&b.physics().matches(sb),"new hull passenger invalidates the entire prepared membership before mutation");}
            finally{rider.stopRiding();rider.discard();}
            first=prepare(c,a,base).accepted();c.assertTrue(first!=null,"unchanged physical source can be prepared again after passenger departure");
            var childDest=first.transform.transformPoint(b.massPosition());var wall=BlockPos.ofFloored(childDest.x(),childDest.y(),childDest.z());w.setBlockState(wall,Blocks.STONE.getDefaultState(),2);
            c.assertTrue(!first.apply()&&prepare(c,a,base).accepted()==null,"late wall and initially blocked child both reject whole transaction");
            c.assertTrue(a.physics().matches(sa)&&b.physics().matches(sb)&&a.getPos().equals(pa)&&b.getPos().equals(pb)&&registry.find(link.id()).orElseThrow().equals(link),"every source pose, momentum, pending force and joint remains unchanged");
            w.setBlockState(wall,Blocks.AIR.getDefaultState(),2);c.assertTrue(prepare(c,a,base).accepted().apply(),"refusal did not consume either hull or damage source state");
        }finally{registry.remove(link.id());a.discard();b.discard();PortalTravel.clear();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-ceiling-exit")
    public void floorToCeilingEmergesTheEntireStandingPlayerOutsideBackingStone(TestContext c){
        var w=c.getWorld();var source=origin(c);var exit=source.east(24);room(c,source,4,5,4);room(c,exit,4,6,4);var player=c.createMockCreativeServerPlayerInWorld();
        try{
            DescentPlayerData.get(player).setEnabled(true);DescentPlayerData.get(player).setFlightVelocity(new Vec3d(0,-12,0));
            // Destination DOWN face lies at exit.Y; all stone is on its backing side.
            for(var q:BlockPos.iterate(exit.add(-2,0,-2),exit.add(2,2,2)))w.setBlockState(q,Blocks.STONE.getDefaultState(),2);
            double plane=source.getY()+1;player.setPosition(source.getX()+.5,plane-.1,source.getZ()+.5);player.prevX=player.getX();player.prevY=plane+.2;player.prevZ=player.getZ();player.setVelocity(0,-.6,0);
            PortalTravel.carry(w,source,Direction.UP,exit,Direction.DOWN,2);
            c.assertTrue(player.getX()>exit.getX()-1&&player.getBoundingBox().maxY<exit.getY()-.009,"common support clearance moves the WHOLE body outside the exit plane");
            c.assertTrue(player.getVelocity().distanceTo(new Vec3d(0,-.6,0))<1e-9&&DescentPlayerData.get(player).getFlightVelocity().distanceTo(new Vec3d(0,-12,0))<1e-9,"authoritative 6DoF velocity and per-tick velocity agree");
            c.assertTrue(w.isSpaceEmpty(player,player.getBoundingBox().contract(1e-6)),"residual sweep begins after emergence, clear of backing stone");
        }finally{player.discard();PortalTravel.clear();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-fast-real-pilot")
    public void onlinePilotCrossesBeyondBroadphaseUsingActualMoveAfterPrevCoordinatesReset(TestContext c){
        var w=c.getWorld();var source=origin(c);var exit=source.east(36);room(c,source,4,4,4);room(c,source.south(22),4,4,25);room(c,exit.east(23),26,4,4);var player=c.createMockCreativeServerPlayerInWorld();
        try{
            c.assertTrue(w.getServer().getPlayerManager().getPlayerList().contains(player),"fixture uses an actual online player");
            DescentPlayerData.get(player).setEnabled(true);DescentPlayerData.get(player).setFlightVelocity(new Vec3d(0,0,800));player.setPosition(source.getX()+.5,source.getY()-.4,source.getZ()-2);player.setVelocity(0,0,40);
            player.move(MovementType.SELF,new Vec3d(0,0,42));var moved=player.getPos();c.assertTrue(moved.z>source.getZ()+39,"real completed step leaves 32-block portal broadphase");
            player.prevX=player.getX();player.prevY=player.getY();player.prevZ=player.getZ();
            PortalTravel.carry(w,source,Direction.NORTH,exit,Direction.EAST,2);
            c.assertTrue(player.getX()>exit.getX()+30&&player.getPos().distanceTo(moved)>20,"genuine movement memo survives vanilla prev reset and online candidate is not lost");
            c.assertTrue(player.getVelocity().distanceTo(new Vec3d(40,0,0))<1e-7,"flight velocity turns once instead of using a clipped walking cache");
        }finally{player.discard();PortalTravel.clear();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-riding-tree")
    public void ridingTreePreservesExistingVehicleAndRelativePose(TestContext c){
        var w=c.getWorld();var at=origin(c);room(c,at,4,6,4);room(c,at.east(24),5,6,5);var pig=EntityType.PIG.create(w);var cow=EntityType.COW.create(w);
        try{
            pig.setNoGravity(true);pig.setAiDisabled(true);cow.setNoGravity(true);cow.setAiDisabled(true);pig.setPosition(Vec3d.ofCenter(at));cow.setPosition(pig.getPos().add(0,1.2,0));w.spawnEntity(pig);w.spawnEntity(cow);c.assertTrue(cow.startRiding(pig,true),"native riding tree exists");cow.setPosition(pig.getPos().add(0,1.2,0));
            var oldA=p(pig.getPos());var oldB=p(cow.getPos());var plan=prepare(c,pig,mapping(oldA,p(Vec3d.ofCenter(at.east(24))))).accepted();c.assertTrue(plan!=null&&plan.apply(),"complete riding tree admitted");
            c.assertTrue(cow.getVehicle()==pig&&pig.getPassengerList().equals(List.of(cow)),"vehicle identities retained");near(c,p(cow.getPos()).minus(p(pig.getPos())),plan.transform.rotation().rotate(oldB.minus(oldA)),"passenger preserves common map instead of recursive duplicate teleport");
        }finally{cow.stopRiding();cow.discard();pig.discard();PortalTravel.clear();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-final-phase")
    public void blockTickQueuesAndCompletedDropMovesOnlyWithItsCarrierAtFinalDrain(TestContext c){
        var w=c.getWorld();var source=origin(c);var exit=source.east(24);room(c,source,4,5,4);room(c,exit,5,5,5);
        var panel=com.terminaldetector.drmd.entity.ModWorldBlocks.PORTAL_PANEL;
        w.setBlockState(source,panel.getDefaultState().with(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.FACING,Direction.NORTH).with(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.LINKED,true),2);
        w.setBlockState(exit,panel.getDefaultState().with(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.FACING,Direction.EAST).with(com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock.LINKED,true),2);
        ((com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity)w.getBlockEntity(source)).setLink(null,exit,w.getRegistryKey());
        ((com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity)w.getBlockEntity(exit)).setLink(null,source,w.getRegistryKey());
        var body=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),new Vec3d(source.getX(),source.getY(),source.getZ()-.8));
        var item=new ItemEntity(w,source.getX()+.5,source.getY()+1.01,source.getZ()-.7,new ItemStack(Items.IRON_INGOT));item.setNoGravity(true);item.setVelocity(0,0,.4);w.spawnEntity(item);
        try{
            body.prevX=body.getX();body.prevY=body.getY();body.prevZ=body.getZ()-.4;var bodyBefore=body.getPos();
            PortalTravel.enqueue(w,source,Direction.NORTH,exit,Direction.EAST,2);c.assertTrue(body.getPos().equals(bodyBefore),"BE-time registration cannot warp a hull before deferred gravity/movement");
            var actualStart=item.getPos();item.move(MovementType.SELF,new Vec3d(0,0,.4));item.setVelocity(Vec3d.ZERO);var itemBefore=item.getPos();
            c.assertTrue(PortalTravel.afterDeferredMove(w,item,actualStart)&&item.getPos().equals(itemBefore),"completed item hook records its true segment without independently teleporting");
            var t=mapping(p(Vec3d.ofCenter(source).add(0,0,-.5)),p(Vec3d.ofCenter(exit).add(.5,0,0)));
            PortalTravel.drain(w);c.assertTrue(body.getX()>exit.getX()&&item.getX()>exit.getX(),"final phase transfers real hull and supported drop together");
            near(c,p(item.getPos()).minus(p(body.getPos())),t.rotation().rotate(p(itemBefore).minus(p(bodyBefore))),"one common group clearance keeps carried item relative pose");
            var after=item.getPos();PortalTravel.drain(w);c.assertTrue(item.getPos().equals(after),"completed segment is consumed once without double gravity or second warp");
        }finally{body.discard();item.discard();w.setBlockState(source,Blocks.AIR.getDefaultState(),2);w.setBlockState(exit,Blocks.AIR.getDefaultState(),2);PortalTravel.clear();}c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-foot-frame")
    public void freeWallWalkerCarriesActualGravityStateAndPhysicalStandingBox(TestContext c){
        var w=c.getWorld();var at=origin(c);room(c,at,5,5,5);room(c,at.east(24),5,5,5);var player=c.createMockCreativeServerPlayerInWorld();
        try{
            DescentPlayerData.get(player).setEnabled(false);player.setPosition(Vec3d.ofCenter(at));
            com.terminaldetector.drmd.world.gravity.FootGravitySystem.adoptServer(player.getUuid(),new Vec3d(1,0,0),"portal-fixture-wall");player.calculateDimensions();player.setVelocity(0,0,.2);
            var plan=prepare(c,player,mapping(p(player.getPos()),p(Vec3d.ofCenter(at.east(24))))).accepted();c.assertTrue(plan!=null,"actual old wall box admits a clear transformed wall box");var up=plan.transform.rotation().rotate(new Vec3(1,0,0));
            c.assertTrue(plan.apply(),"free foot frame commits independently");near(c,p(com.terminaldetector.drmd.world.gravity.FootGravitySystem.getUp(player)),up,"active State.up turns, not only LocalOrientation fallback");
            var expected=com.terminaldetector.drmd.world.gravity.GravityMount.standingBox(player.getPos(),PortalTransfer.mc(up),player.getWidth()/2.0,player.getHeight());
            c.assertTrue(player.getBoundingBox().equals(expected)&&w.isSpaceEmpty(player,expected.contract(1e-6)),"real active physical shape agrees with destination admission");
        }finally{com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(player.getUuid());player.discard();PortalTravel.clear();}c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-occupied-packet-order")
    public void actualBodySnapshotsPrecedeOccupantsNativeTeleportWithoutConsumingTrackerDirtyState(TestContext c){
        var w=c.getWorld();var at=origin(c);room(c,at,6,6,6);room(c,at.east(24),6,6,6);
        var client=net.minecraft.server.network.ConnectedClientData.createDefault(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"portal-wire"),false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        w.getServer().getPlayerManager().onPlayerConnect(connection,player,client);
        var body=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),Vec3d.of(at));
        try{
            DescentPlayerData.get(player).setEnabled(false);player.setNoGravity(true);body.setPassengerGravity(new Vec3(0,-16,0));player.setPosition(body.localToWorld(new Vec3d(.5,1.1,.5)));player.setVelocity(Vec3d.ZERO);
            for(int i=0;i<4096&&!channel.outboundMessages().isEmpty();i++)io.netty.util.ReferenceCountUtil.release(channel.readOutbound());c.assertTrue(channel.outboundMessages().isEmpty(),"initial actual connection backlog bounded");
            var attempt=prepare(c,body,mapping(body.massPosition(),p(Vec3d.ofCenter(at.east(24)))));c.assertTrue(attempt.accepted()!=null&&attempt.members().contains(player),"real connected user belongs to the finite hull field");
            c.assertTrue(attempt.accepted().apply(),"accepted physical group and occupant commit");channel.runPendingTasks();
            int index=0,position=-1,pose=-1,teleport=-1;boolean spawnSeen=false;
            for(Object raw:channel.outboundMessages()){
                if(raw instanceof net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket packet&&packet.getEntityId()==body.getId())position=index;
                if(raw instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket packet&&packet.id()==body.getId())for(var entry:packet.trackedValues())if(entry.value() instanceof net.minecraft.nbt.NbtCompound tag&&tag.getLong("portalEpoch")==1)pose=index;
                if(raw instanceof net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket packet&&packet.getEntityId()==body.getId()){spawnSeen=true;c.assertTrue(packet.getUuid().equals(body.getUuid())&&packet.getX()==body.getX(),"untracked occupant receives actual identity and destination, not a clone");}
                if(raw instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket)teleport=index;
                index++;
            }
            c.assertTrue(position>=0&&pose>=0&&teleport>position&&teleport>pose,"normal position and portal POSE arrive before native occupant teleport: position="+position+" pose="+pose+" teleport="+teleport+" initialSpawn="+spawnSeen);
            c.assertTrue(body.getDataTracker().isDirty(),"explicit publication does not consume the ordinary tracker dirty update");
        }finally{body.discard();w.getServer().getPlayerManager().remove(player);player.discard();channel.finishAndReleaseAll();PortalTravel.clear();}c.complete();
    }

}
