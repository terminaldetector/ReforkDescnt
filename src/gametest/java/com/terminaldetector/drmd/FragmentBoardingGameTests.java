package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class FragmentBoardingGameTests implements FabricGameTest {
    private BlockBodyEntity line(TestContext c) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int i=0;i<7;i++)cells.add(new BlockBodyEntity.Cell(new BlockPos(i,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        for(int x=1874;x<=1876;x++)for(int z=1874;z<=1876;z++)c.getWorld().getChunk(x,z);
        return BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(30000,180,30000));
    }
    private Set<Integer> ids(TestContext c){var result=new HashSet<Integer>();for(var body:BodySpatialIndex.loaded(c.getWorld()))result.add(body.getId());return result;}
    private List<BlockBodyEntity> added(TestContext c,Set<Integer> before){return BodySpatialIndex.loaded(c.getWorld()).stream().filter(b->!before.contains(b.getId())).toList();}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="fragment-contact-phase")
    public void aMidTickFragmentExposesTheOriginalMaterialTrajectoryToContactQueries(TestContext c) {
        var root=line(c);var before=ids(c);root.physics().withRotation(new Quat(.2,.3,.4,.7).normalized()).withLinearVelocity(new Vec3(3,1,2)).withAngularVelocity(new Vec3(.1,.2,.3));root.tick();
        var motion=root.boardingMotion();var local=new Vec3(5.5,.5,.5);var from=motion.position(0).plus(motion.rotation(0).rotate(local.minus(root.localCentre())));
        var point=root.localToWorld(new Vec3d(local.x(),local.y(),local.z()));var speed=root.surfaceVelocityAt(point);
        try {
            c.assertTrue(root.setLocalBlock(new BlockPos(3,0,0),Blocks.AIR.getDefaultState()),"real current-tick hull fractures");
            var parts=added(c,before);c.assertTrue(parts.size()==1,"one detached half");var child=parts.getFirst();var next=child.boardingMotion();
            c.assertTrue(next.surfaceVelocity(new Vec3(point.x,point.y,point.z),1).minus(new Vec3(speed.x,speed.y,speed.z)).length()<1e-8,"contact queries must not replace a fresh moving fragment with a paused infinite-mass surface for its first tick");
            var oldPoint=next.position(0).plus(next.rotation(0).rotate(local.minus(child.localCentre())));
            c.assertTrue(oldPoint.minus(from).length()<1e-8,"the child's collision trajectory starts at the same earlier material pose as its original hull");
        }finally{added(c,before).forEach(BlockBodyEntity::discard);root.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="fragment-player-handoff")
    public void aPlayerStandingOnTheDetachedDeckKeepsContactWorldSpeedAndInputEpoch(TestContext c) {
        var root=line(c);var before=ids(c);root.physics().withLinearVelocity(new Vec3(3,0,0));root.tick();
        var player=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(player).setEnabled(false);player.getAbilities().flying=false;
        player.setPosition(root.localToWorld(new Vec3d(5.5,1,.5)));player.setVelocity(.15,0,0);
        com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(player.getUuid());BoardingSystem.ready(player,BoardingNetworking.PROTOCOL);
        try {
            BoardingSystem.tick(c.getWorld());c.assertTrue(BoardingSystem.drives(player),"real deck acquires the player before fracture");int epoch=BoardingSystem.epoch(player);var position=player.getPos();
            root.setLocalBlock(new BlockPos(3,0,0),Blocks.AIR.getDefaultState());var parts=added(c,before);c.assertTrue(parts.size()==1,"physical deck becomes a child body");
            BoardingSystem.tick(c.getWorld());var state=BoardingSystem.state(player);
            c.assertTrue(state!=null&&state.grounded()&&state.support()==parts.getFirst().getId(),"support follows actual detached deck geometry without teleport-follow or a new lease");
            c.assertTrue(BoardingSystem.epoch(player)==epoch,"unacknowledged input epoch survives a physical support transition");
            c.assertTrue(Math.abs(state.velocity().x()-3)<.1,"new fragment cannot erase inherited tangential speed by acting like a paused static floor");
            c.assertTrue(player.getPos().subtract(position).length()<.25&&player.getX()>position.x+.1,"ordinary collision-checked movement advances the actor continuously");
            c.assertTrue(BoardingSystem.receive(player,new BoardingNetworking.Input(epoch,0,0,0,true,0,0,1)),"jump input remains valid after support changes");
            parts.getFirst().tick();root.tick();BoardingSystem.tick(c.getWorld());
            c.assertTrue(!BoardingSystem.drives(player)&&!player.isOnGround(),"a real jump releases the separated deck");
            c.assertTrue(Math.abs(player.getVelocity().x-.15)<.01&&player.getVelocity().y>.1,"jump retains the fragment/carrier world speed exactly once");
        }finally{added(c,before).forEach(BlockBodyEntity::discard);root.discard();player.discard();BoardingSystem.tick(c.getWorld());}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="fragment-stale-parent")
    public void anUnsteppedParentDoesNotInventALiveContactTrajectoryForItsFragments(TestContext c) {
        var root=line(c);var before=ids(c);root.physics().withLinearVelocity(new Vec3(3,1,2));
        try {
            c.assertTrue(root.boardingMotionPaused(),"fixture has not advanced a physical tick");root.setLocalBlock(new BlockPos(3,0,0),Blocks.AIR.getDefaultState());
            var child=added(c,before).getFirst();var motion=child.boardingMotion();
            c.assertTrue(child.boardingMotionPaused()&&motion.position(0).equals(motion.position(1)),"freshness inheritance must not animate stale or loading-paused material");
            c.assertTrue(motion.surfaceVelocity(child.massPosition(),1).length()==0,"unavailable trajectory still behaves as a paused contact until physics resumes");
            c.assertTrue(child.physics().linearVelocity().minus(new Vec3(3,1,2)).length()<1e-9,"paused queries retain real physical momentum for resume");
        }finally{added(c,before).forEach(BlockBodyEntity::discard);root.discard();}c.complete();
    }

}
