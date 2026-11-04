package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Real registered UFO geometry and state; not a replacement decorative fixture. */
public final class PhysicalUfoGameTests implements FabricGameTest {
    private SkyUfoEntity create(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,190,8));var w=c.getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        var ufo=ModEntities.SKY_UFO.create(w);ufo.setAutopilot(false);ufo.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);
        c.assertTrue(w.spawnEntity(ufo),"spawn existing UFO registration");ufo.tick();return ufo;
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-single-hull",tickLimit=60)
    public void existingRegistrationCreatesOnePhysicalGridWithoutWorldBlocks(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,190,8));var w=c.getWorld();var sentinel=at.add(11,0,11);w.setBlockState(sentinel,Blocks.GOLD_BLOCK.getDefaultState(),2);
        var before=BodySpatialIndex.loaded(w).size();var ufo=create(c);
        try {
            c.assertTrue(ufo instanceof BlockBodyEntity&&ufo.getType()==ModEntities.SKY_UFO,"existing ID is the physical body itself");
            c.assertTrue(ufo.cells().size()>500&&ufo.physicsMass()>500&&ufo.collider().boxes().size()>1,"real hollow material grid has aggregate mass and compound geometry");
            c.assertTrue(BodySpatialIndex.loaded(w).size()==before+1,"no second proxy/body entity or per-block rigid bodies");
            c.assertTrue(w.getBlockState(at).isAir()&&w.getBlockState(sentinel).isOf(Blocks.GOLD_BLOCK),"initialization does not materialize or erase world blocks");
        } finally {ufo.discard();}
        c.assertTrue(w.getBlockState(sentinel).isOf(Blocks.GOLD_BLOCK),"removing UFO cannot clear unrelated terrain");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-rotated-interior",tickLimit=60)
    public void actualDoorAndDeckRemainHollowAfterThreeAxisRotation(TestContext c) {
        var ufo=create(c);try {
            var q=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));ufo.physics().withRotation(q);ufo.publishPhysicsState();
            var access=new BlockBodyWorldAccess(ufo);var door=access.raycastBlockBody(ufo.localToWorld(new Vec3d(0,1,-14)),ufo.localToWorld(new Vec3d(0,1,-2)));
            c.assertTrue(door.isEmpty(),"an actual entrance must not become a solid entity AABB");
            var floor=access.raycastBlockBody(ufo.localToWorld(new Vec3d(3,2,3)),ufo.localToWorld(new Vec3d(3,-2,3))).orElseThrow();
            c.assertTrue(floor.localBlock().getY()==-1&&floor.worldNormal().dot(q.rotate(new Vec3(0,1,0)))>.999999,"deck normal follows full quaternion");
            c.assertTrue(ufo.containsPos(ufo.localToWorld(new Vec3d(3,.1,3))),"interior membership uses inverse body transform");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-local-damage",tickLimit=60)
    public void miningRealHullMaterialKeepsIdentityAndChangesMass(TestContext c) {
        var ufo=create(c);try {
            var id=ufo.getUuid();double mass=ufo.physicsMass();var pos=new BlockPos(3,-1,3);
            c.assertTrue(!ufo.getLocalBlock(pos).isAir()&&ufo.setLocalBlock(pos,Blocks.AIR.getDefaultState()),"remove actual local deck material");
            c.assertTrue(ufo.getUuid().equals(id)&&ufo.physicsMass()<mass&&ufo.getLocalBlock(pos).isAir(),"normal BlockBody edits update the same UFO and its mass");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-state",tickLimit=60)
    public void physicalSaveRestoresDamagedGridGravityAndMomentum(TestContext c) {
        var ufo=create(c);SkyUfoEntity copy=null;
        try {
            ufo.setLocalBlock(new BlockPos(3,-1,3),Blocks.AIR.getDefaultState());ufo.physics().withLinearVelocity(new Vec3(12,-3,5)).withAngularVelocity(new Vec3(.3,-.2,.1));ufo.publishPhysicsState();
            var n=ufo.writeNbt(new NbtCompound());copy=ModEntities.SKY_UFO.create(c.getWorld());copy.readNbt(n);
            c.assertTrue(copy.cells().equals(ufo.cells())&&copy.shapeRevision()==ufo.shapeRevision(),"saved real damaged grid is not regenerated from the template");
            c.assertTrue(copy.physics().linearVelocity().equals(ufo.physics().linearVelocity())&&copy.physics().angularMomentum().equals(ufo.physics().angularMomentum())&&copy.passengerGravity().equals(ufo.passengerGravity()),"physical velocity/momentum/gravity survive entity NBT");
        } finally {if(copy!=null)copy.discard();ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-reactor",tickLimit=60)
    public void localReactorFailureChangesForcesWithoutResettingMotion(TestContext c) {
        var ufo=create(c);try {
            ufo.physics().withLinearVelocity(new Vec3(9,2,-3)).withAngularVelocity(new Vec3(.1,.2,-.15));var material=new Vec3d(3.5,-.5,3.5);var position=ufo.localToWorld(material);var rotation=ufo.rotation();var velocity=ufo.physics().velocityAtPoint(new Vec3(position.x-ufo.massPosition().x(),position.y-ufo.massPosition().y(),position.z-ufo.massPosition().z()));
            ufo.destroyFromCore(c.getWorld(),null,"test local core");
            c.assertTrue(ufo.isCrashing()&&ufo.physicsGravity()&&ufo.passengerGravity().lengthSquared()==0,"power failure disables artificial gravity and enables actual fall");
            var after=ufo.localToWorld(material);var afterVelocity=ufo.physics().velocityAtPoint(new Vec3(after.x-ufo.massPosition().x(),after.y-ufo.massPosition().y(),after.z-ufo.massPosition().z()));
            c.assertTrue(after.distanceTo(position)<1e-8&&ufo.rotation().equals(rotation)&&afterVelocity.minus(velocity).length()<1e-8,"core transition preserves actual material point pose and velocity across its COM rebase");
        } finally {ufo.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="physical-ufo-mined-reactor",tickLimit=60)
    public void ordinaryLocalCoreMiningImmediatelyPowersDownTheHullAndDetachedMaterial(TestContext c) {
        var before=BodySpatialIndex.loaded(c.getWorld()).stream().map(BlockBodyEntity::getUuid).collect(java.util.stream.Collectors.toSet());
        var ufo=create(c);
        try {
            c.assertTrue(new BlockBodyWorldAccess(ufo).breakLocalBlock(new BlockPos(0,1,0)),"actual local-grid mining removes reactor material");
            c.assertTrue(ufo.isCrashing()&&ufo.physicsGravity()&&ufo.passengerGravity().lengthSquared()==0,"material commit powers down the UFO without a later controller poll");
            var fragments=BodySpatialIndex.loaded(c.getWorld()).stream().filter(b->b!=ufo&&!before.contains(b.getUuid())).toList();
            c.assertTrue(!fragments.isEmpty()&&fragments.stream().allMatch(BlockBodyEntity::physicsGravity),"separated stabilizer material inherits the powered-down fall state");
        } finally {for(var b:BodySpatialIndex.loaded(c.getWorld()))if(!before.contains(b.getUuid()))b.discard();}c.complete();
    }
}
