package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Real world-phase integration on two existing colliding hulls, not a replacement engine. */
public final class BodyJointRuntimeGameTests implements FabricGameTest {
    private record Fixture(TestContext c,BlockBodyEntity a,BlockBodyEntity b,BodyJointLink link,BodyJointRegistry registry) implements AutoCloseable {
        public void close(){registry.remove(link.id());a.discard();b.discard();}
    }
    private Fixture fixture(TestContext c,BodyJointConstraint.Kind kind,double speed,double force,double breakForce) {
        for(int x=2998;x<=3003;x++)for(int z=2998;z<=3002;z++)c.getWorld().getChunk(x,z);
        var cells=List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));
        var a=BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(48000,180,48000));var b=BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(48001,180,48000));
        var link=new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(a.getUuid(),BlockPos.ORIGIN,new Vec3(1,.5,.5),Quat.IDENTITY),new BodyJointLink.Endpoint(b.getUuid(),BlockPos.ORIGIN,new Vec3(0,.5,.5),Quat.IDENTITY),kind,0,1,speed,force,breakForce,Double.POSITIVE_INFINITY);
        var registry=BodyJointRegistry.get(c.getWorld());registry.add(c.getWorld(),a,b,link);return new Fixture(c,a,b,link,registry);
    }
    private void step(Fixture f){f.a.tick();f.b.tick();BodyContactSystem.solve(f.c.getWorld());}
    private Vec3d anchor(BlockBodyEntity b,BodyJointLink.Endpoint e){return b.localToWorld(new Vec3d(e.point().x(),e.point().y(),e.point().z()));}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-world-fixed")
    public void fixedHullComponentsShareOnePhysicalPhaseUnderTranslationAndSpin(TestContext c) {
        var f=fixture(c,BodyJointConstraint.Kind.FIXED,0,0,Double.POSITIVE_INFINITY);
        c.waitAndRun(1,()->{try {
            f.b.applyPhysicsImpulse(f.b.physicsCentre(),new Vec3d(4,1,.3));f.b.applyPhysicsTorque(new Vec3d(.3,.2,.4));
            for(int i=0;i<20;i++) {
                step(f);c.assertTrue(anchor(f.a,f.link.a()).distanceTo(anchor(f.b,f.link.b()))<.015,"fixed material anchors stay together through the actual terrain/hull world phase");
                c.assertTrue(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(f.a.rotation(),f.b.rotation()))>.999,"fixed relative orientations remain aligned");
            }
            c.assertTrue(f.a.getX()>48001&&BodyJointSystem.stats(c.getWorld()).substeps()>=16&&BodyJointSystem.stats(c.getWorld()).paused()==0,"the group really integrates its motion instead of freezing at a joint pose");
        }finally{f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-world-motor-slider")
    public void motorAndSliderDriveActualBodiesWithIndependentGeometryAndStops(TestContext c) {
        var f=fixture(c,BodyJointConstraint.Kind.MOTOR,1,10,Double.POSITIVE_INFINITY);
        c.waitAndRun(1,()->{try {
            for(int i=0;i<20;i++)step(f);
            c.assertTrue(Math.abs(f.b.physics().angularVelocity().x()-f.a.physics().angularVelocity().x()-1)<.02,"hinge motor reaches its relative angular speed through forces");
            c.assertTrue(anchor(f.a,f.link.a()).distanceTo(anchor(f.b,f.link.b()))<.015,"motor rotation retains its material anchor");
            c.assertTrue(f.a.collider()!=f.b.collider()&&f.a.cells().size()==1&&f.b.cells().size()==1,"each moving component retains a real independent collider");
        }finally{f.close();}
        var slider=fixture(c,BodyJointConstraint.Kind.SLIDER,1,10,Double.POSITIVE_INFINITY);
        c.waitAndRun(1,()->{try {
            for(int i=0;i<50;i++)step(slider);
            var delta=anchor(slider.b,slider.link.b()).subtract(anchor(slider.a,slider.link.a()));
            c.assertTrue(Math.abs(delta.x-1)<.01&&Math.abs(delta.y)<.001&&Math.abs(delta.z)<.001,"actual sliding platform reaches its stop without lateral drift");
            c.assertTrue(slider.a.physics().linearVelocity().minus(slider.b.physics().linearVelocity()).length()<.01,"stop removes only relative movement");
        }finally{slider.close();}c.complete();});});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-world-break")
    public void anOverloadedConnectionDetachesWithoutASecondImpulseOrVelocityReset(TestContext c) {
        var f=fixture(c,BodyJointConstraint.Kind.FIXED,0,0,1);
        c.waitAndRun(1,()->{try {
            f.b.physics().withLinearVelocity(new Vec3(12,0,0));step(f);
            c.assertTrue(f.registry.find(f.link.id()).isEmpty()&&BodyJointSystem.stats(c.getWorld()).broken()==1,"overload removes the authoritative connection");
            c.assertTrue(f.b.physics().linearVelocity().minus(new Vec3(12,0,0)).length()<1e-8&&f.a.physics().linearVelocity().length()<1e-8,"separated components retain the physical state at the break");
            c.assertTrue(f.b.getX()>48001.9,"freed component completes the remaining physical tick");
        }finally{f.close();}c.complete();});
    }
}
