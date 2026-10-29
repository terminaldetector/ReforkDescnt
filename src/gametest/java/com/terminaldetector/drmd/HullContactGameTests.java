package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class HullContactGameTests implements FabricGameTest {
    private Vec3d arena(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,150,8));var w=c.getWorld();
        for(int x=-1;x<=8;x++)for(int z=-1;z<=1;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        for(var p:BlockPos.iterate(at.add(-4,-4,-4),at.add(14,8,10)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        return Vec3d.of(at);
    }
    private List<BlockBodyEntity.Cell> cube(){return List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));}
    private Vec3 momentum(BlockBodyEntity b){return b.physics().linearVelocity().scaled(b.physicsMass());}
    private Vec3 angular(BlockBodyEntity b,Vec3 origin){return b.physics().angularMomentum().plus(b.massPosition().minus(origin).cross(momentum(b)));}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-world-phase",tickLimit=100)
    public void normalServerTicksRunTheRegisteredContactPhaseWithoutAManualSolverCall(TestContext c) {
        var at=arena(c);var w=c.getWorld();var a=BlockBodyEntity.createLocal(w,cube(),at);var b=BlockBodyEntity.createLocal(w,cube(),at.add(3,0,0));
        var player=c.createMockCreativeServerPlayerInWorld();player.setPosition(at.add(0,3,5));
        a.physics().withLinearVelocity(new Vec3(20,0,0));b.physics().withLinearVelocity(new Vec3(-20,0,0));
        c.waitAndRun(12,()->{try {
            c.assertTrue(a.age>1&&b.age>1,"both hulls really tick in the server world");
            c.assertTrue(b.getX()-a.getX()>=.999&&a.getX()>at.x+.6,"registered world contact stops crossing after actual flight");
            c.assertTrue(a.physics().linearVelocity().length()<1e-6&&b.physics().linearVelocity().length()<1e-6,"END_WORLD_TICK actually solves both physical bodies");
        }finally{a.discard();b.discard();player.discard();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-remainder")
    public void collisionCompletesTheWholeTickInsteadOfLosingTheTimeAfterImpact(TestContext c) {
        var at=arena(c);var w=c.getWorld();var a=BlockBodyEntity.createLocal(w,cube(),at);var b=BlockBodyEntity.createLocal(w,cube(),at.add(3,0,0));
        try {
            b.physics().withLinearVelocity(new Vec3(-50,0,0));a.tick();b.tick();BodyContactSystem.solve(w);
            c.assertTrue(Math.abs(a.getX()-(at.x+.25))<1e-5&&Math.abs(b.getX()-(at.x+1.25))<1e-5,"both real hulls integrate the remaining10ms at their new25m/s contact speed: a="+a.getX()+" b="+b.getX()+" origin="+at.x);
        }finally{a.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-chain")
    public void sequentialImpactsRequeryTheChangedTrajectoryAndCompleteTheTick(TestContext c) {
        var at=arena(c);var w=c.getWorld();var bodies=new ArrayList<BlockBodyEntity>();
        try {
            for(int i=0;i<3;i++)bodies.add(BlockBodyEntity.createLocal(w,cube(),at.add(1.4*i,0,0)));
            bodies.getFirst().physics().withLinearVelocity(new Vec3(50,0,0));var p=momentum(bodies.getFirst());
            bodies.forEach(BlockBodyEntity::tick);BodyContactSystem.solve(w);
            c.assertTrue(!BodyContactSystem.stats(w).budgetPaused(),"two successive contacts settle within the physical budget");
            c.assertTrue(BodyContactSystem.stats(w).collisions()>=2,"changed first trajectory really reaches the third hull");
            Vec3 sum=new Vec3(0,0,0);
            for(int i=0;i<3;i++) {
                var b=bodies.get(i);sum=sum.plus(momentum(b));
                c.assertTrue(Math.abs(b.physics().linearVelocity().x()-50./3)<1e-4,"three hulls share the two-sided contact momentum");
                c.assertTrue(Math.abs(b.getX()-(at.x+1.7333333333333334+i))<1e-4,"the second collision also integrates its remaining26ms");
            }
            c.assertTrue(sum.minus(p).length()<1e-7,"sequential impacts conserve total linear momentum");
        }finally{bodies.forEach(BlockBodyEntity::discard);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-resting-island")
    public void anImpactPropagatesThroughAnAlreadyTouchingThirdHull(TestContext c) {
        var at=arena(c);var w=c.getWorld();var bodies=new ArrayList<BlockBodyEntity>();
        try {
            for(double x:new double[]{0,1.4,2.4})bodies.add(BlockBodyEntity.createLocal(w,cube(),at.add(x,0,0)));
            bodies.getFirst().physics().withLinearVelocity(new Vec3(50,0,0));bodies.forEach(BlockBodyEntity::tick);BodyContactSystem.solve(w);
            c.assertTrue(!BodyContactSystem.stats(w).budgetPaused(),"resting contact joins the same constraint island without zero-time ping-pong");
            for(int i=0;i<3;i++) {
                var b=bodies.get(i);
                c.assertTrue(Math.abs(b.physics().linearVelocity().x()-50./3)<1e-4,"impulse reaches the initially resting contact");
                c.assertTrue(Math.abs(b.getX()-(at.x+1.6+i))<1e-4,"all three hulls retain the full42ms after the shared impact");
            }
        }finally{bodies.forEach(BlockBodyEntity::discard);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-fast")
    public void twoActualFastHullsCannotSwapSidesAndTheirContactConservesMomentum(TestContext c) {
        var at=arena(c);var w=c.getWorld();var a=BlockBodyEntity.createLocal(w,cube(),at);var b=BlockBodyEntity.createLocal(w,cube(),at.add(3,0,0));
        try {
            a.physics().withLinearVelocity(new Vec3(50,0,0));b.physics().withLinearVelocity(new Vec3(-50,0,0));var initial=momentum(a).plus(momentum(b));
            a.tick();b.tick();c.assertTrue(a.getX()>b.getX(),"both clear end poses already crossed, so endpoint overlap cannot detect the hit");
            c.assertTrue(BodyContactSystem.solve(w)>0,"shared world phase finds the actual continuous hull contact");
            c.assertTrue(a.getX()<b.getX()&&b.getX()-a.getX()>=.999,"CCD leaves the real hulls on their original sides");
            c.assertTrue(momentum(a).plus(momentum(b)).minus(initial).length()<1e-7,"two-sided contact conserves linear momentum");
            c.assertTrue(Math.abs(a.physics().linearVelocity().x())<1e-6&&Math.abs(b.physics().linearVelocity().x())<1e-6,"inelastic equal-mass impact cancels relative closing velocity");
        }finally{a.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-hole")
    public void anotherFlyingBodyCanPassThroughARotatedHullsActualHole(TestContext c) {
        var at=arena(c);var w=c.getWorld();var frame=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()),new BlockBodyEntity.Cell(new BlockPos(3,0,0),Blocks.IRON_BLOCK.getDefaultState())),at);
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,1,0),.8);frame.physics().withRotation(q);frame.tick();
        var start=frame.localToWorld(new Vec3d(1.8,.5,-1.5));var mover=BlockBodyEntity.createLocal(w,cube(),start.subtract(.5,.5,.5));
        try {
            mover.physics().withRotation(q).withLinearVelocity(q.rotate(new Vec3(0,0,50)));mover.tick();
            c.assertTrue(BodyContactSystem.solve(w)==0,"composite broadphase empty corner cannot become a phantom wall");
            c.assertTrue(mover.physics().linearVelocity().minus(q.rotate(new Vec3(0,0,50))).length()<1e-7,"free motion through the opening is retained");
        }finally{frame.discard();mover.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-torque")
    public void anOffCentreRealHullStrikeTransfersTorqueWithoutLosingTotalAngularMomentum(TestContext c) {
        var at=arena(c);var w=c.getWorld();var a=BlockBodyEntity.createLocal(w,List.of(cube().getFirst(),new BlockBodyEntity.Cell(new BlockPos(0,1,0),Blocks.IRON_BLOCK.getDefaultState())),at);
        var b=BlockBodyEntity.createLocal(w,cube(),at.add(1.03,.6,0));
        try {
            b.physics().withLinearVelocity(new Vec3(-1,0,0));var p=momentum(a).plus(momentum(b));var origin=new Vec3(at.x,at.y,at.z);var l=angular(a,origin).plus(angular(b,origin));
            a.tick();b.tick();c.assertTrue(BodyContactSystem.solve(w)>0,"actual offset contact occurs");
            c.assertTrue(a.physics().angularVelocity().length()>1e-4&&b.physics().angularVelocity().length()>1e-4,"the same physical contact spins both inertial hulls: a="+a.physics().angularVelocity()+" b="+b.physics().angularVelocity());
            c.assertTrue(momentum(a).plus(momentum(b)).minus(p).length()<1e-7,"linear momentum is conserved");
            c.assertTrue(a.physics().angularVelocity().length()<1.99&&b.physics().angularVelocity().length()<1.99,"angular conservation fixture stays below the existing2rad/s dissipative cap");
            c.assertTrue(angular(a,origin).plus(angular(b,origin)).minus(l).length()<1e-6,"spin plus orbital world angular momentum is conserved: before="+l+" after="+angular(a,origin).plus(angular(b,origin))+" spinA="+a.physics().angularVelocity()+" spinB="+b.physics().angularVelocity()+" stats="+BodyContactSystem.stats(w));
        }finally{a.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-teleport")
    public void explicitPlacementIsNotAFlightThroughInterveningHulls(TestContext c) {
        var at=arena(c);var w=c.getWorld();var a=BlockBodyEntity.createLocal(w,cube(),at);var b=BlockBodyEntity.createLocal(w,cube(),at.add(50,0,0));
        try {
            a.tick();b.tick();var target=a.getPos().add(100,0,0);a.setPosition(target);
            c.assertTrue(BodyContactSystem.solve(w)==0&&a.getPos().distanceTo(target)<1e-7,"authoritative placement resets trajectory instead of colliding across a100-block teleport");
        }finally{a.discard();b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-pair-paused")
    public void anUnavailablePausedHullCannotAcquireSpuriousContactMomentum(TestContext c) {
        var at=arena(c);var w=c.getWorld();var a=BlockBodyEntity.createLocal(w,cube(),at);var b=BlockBodyEntity.createLocal(w,cube(),at.add(2,0,0));
        try {
            b.physics().withLinearVelocity(new Vec3(7,0,0));a.physics().withLinearVelocity(new Vec3(50,0,0));a.tick();
            c.assertTrue(b.boardingMotionPaused(),"second entity has no physical trajectory in this tick");
            c.assertTrue(BodyContactSystem.solve(w)>0&&Math.abs(a.physics().linearVelocity().x())<1e-7,"active hull collides with the still contact support");
            c.assertTrue(b.physics().linearVelocity().equals(new Vec3(7,0,0)),"paused hull retains its saved resume momentum");
        }finally{a.discard();b.discard();}c.complete();
    }
}
