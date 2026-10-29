package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class BodyScaleGameTests implements FabricGameTest {
    private List<BlockBodyEntity.Cell> cells(int sx,int sy,int sz) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();
        for(int x=0;x<sx;x++)for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)
            cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
        return cells;
    }
    private BlockBodyEntity create(TestContext c,List<BlockBodyEntity.Cell> cells) {
        var at=c.getAbsolutePos(new BlockPos(8,150,8));
        for(int x=-2;x<=8;x++)for(int z=-2;z<=4;z++)c.getWorld().getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        return BlockBodyEntity.createLocal(c.getWorld(),cells,Vec3d.of(at));
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-full")
    public void full32768BlockHullEditsOneSectionPreservingTheOtherSeven(TestContext c) {
        var body=create(c,cells(32,32,32));try {
            c.assertTrue(body.cells().size()==32768&&body.collider().sectionCount()==8,"full supported hull uses eight merged section colliders");
            var unchanged=body.colliderSection(new BlockPos(20,20,20)).orElseThrow();var snapshot=body.collider();
            var at=new BlockPos(8,8,8);var before=body.localToWorld(Vec3d.ofCenter(at));
            c.assertTrue(!body.setLocalBlock(new BlockPos(32,0,0),Blocks.GOLD_BLOCK.getDefaultState()),"cannot exceed 32768 actual occupied blocks");
            c.assertTrue(body.setLocalBlock(at,Blocks.AIR.getDefaultState()),"one full-hull damage operation succeeds");
            c.assertTrue(body.colliderSection(new BlockPos(20,20,20)).orElseThrow()==unchanged,"distant collider BVH reused by identity");
            var hole=new BodyBounds(new Vec3(8.1,8.1,8.1),new Vec3(8.9,8.9,8.9));
            c.assertTrue(body.collider().query(hole).isEmpty()&&!snapshot.query(hole).isEmpty(),"hole is real; in-flight physics snapshot remains immutable");
            c.assertTrue(body.setLocalBlock(new BlockPos(127,0,0),Blocks.GOLD_BLOCK.getDefaultState()),"released budget can extend to the far edge");
            c.assertTrue(before.distanceTo(body.localToWorld(Vec3d.ofCenter(at)))<1e-7,"COM edits retain the local frame");
        }finally{body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-legacy")
    public void oldCubicBodySavesRemainReadableAndNewSparseSnapshotsStayBelowPacketBudget(TestContext c) {
        var source=cells(32,32,32);var encoded=BodyShapeCodec.write(source);
        int bytes;
        try {var out=new java.io.ByteArrayOutputStream();NbtIo.write(encoded,new java.io.DataOutputStream(out));bytes=out.size();}
        catch(java.io.IOException e){throw new AssertionError(e);}
        c.assertTrue(bytes<300000&&BodyShapeCodec.read(encoded).equals(source),"32768 cells are registry-stable and below 300KB: "+bytes);
        var old=new com.terminaldetector.drmd.d6.D6Volume<BlockState>(Blocks.AIR.getDefaultState());old.set(3,0,0,Blocks.GOLD_BLOCK.getDefaultState());
        var n=new NbtCompound();n.put("shape",com.terminaldetector.drmd.world.cubic.CubicBlockCodec.writeVolume(old));n.put("body",new NbtCompound());
        var restored=ModEntities.BLOCK_BODY.create(c.getWorld());try {restored.readNbt(n);c.assertTrue(restored.getLocalBlock(new BlockPos(3,0,0)).isOf(Blocks.GOLD_BLOCK),"pre-scale volume save loads");}
        finally{restored.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-save")
    public void expandedNegativeAndFarCellsKeepSpinGravityAndWorldPoseAcrossSave(TestContext c) {
        var source=new ArrayList<>(cells(2,2,2));source.add(new BlockBodyEntity.Cell(new BlockPos(127,15,15),Blocks.GOLD_BLOCK.getDefaultState()));
        source.add(new BlockBodyEntity.Cell(new BlockPos(-32,0,0),Blocks.DIAMOND_BLOCK.getDefaultState()));
        var body=create(c,source);var restored=ModEntities.BLOCK_BODY.create(c.getWorld());try {
            body.physics().withRotation(ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),1.3)).withLinearVelocity(new Vec3(2,3,4)).withAngularVelocity(new Vec3(.2,.3,.4));
            body.setPassengerGravity(new Vec3(0,-32,0));body.setLocalBlock(new BlockPos(100,1,1),Blocks.OBSIDIAN.getDefaultState());
            restored.readNbt(body.writeNbt(new NbtCompound()));
            c.assertTrue(restored.cells().size()==11&&restored.shapeRevision()==1,"expanded sparse grid and revision survive");
            var point=new Vec3d(127.5,15.5,15.5);
            c.assertTrue(restored.localToWorld(point).distanceTo(body.localToWorld(point))<1e-7&&restored.surfaceVelocityAt(body.localToWorld(point)).distanceTo(body.surfaceVelocityAt(body.localToWorld(point)))<1e-7,"world material pose and angular point velocity survive");
            c.assertTrue(restored.passengerGravity().equals(body.passengerGravity()),"finite local gravity survives");
        }finally{body.discard();restored.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-sparse")
    public void farRotatedHullRayAndLocalNarrowphaseRetainEmptySpace(TestContext c) {
        var source=cells(2,1,2);source.add(new BlockBodyEntity.Cell(new BlockPos(127,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        var body=create(c,source);try {
            body.physics().withRotation(ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.7));body.publishPhysicsState();
            var access=new BlockBodyWorldAccess(body);
            var hit=access.raycastBlockBody(body.localToWorld(new Vec3d(127.5,.5,-3)),body.localToWorld(new Vec3d(127.5,.5,3))).orElseThrow();
            c.assertTrue(hit.localBlock().equals(new BlockPos(127,0,0)),"far local ray chooses exact cell after three-axis rotation");
            c.assertTrue(access.raycastBlockBody(body.localToWorld(new Vec3d(64.5,.5,-3)),body.localToWorld(new Vec3d(64.5,.5,3))).isEmpty(),"empty middle is not a solid giant box");
            c.assertTrue(body.localCollisionCandidates(new BodyBounds(new Vec3(127.1,.1,.1),new Vec3(127.9,.9,.9))).size()==1,"local cell index prunes distant material");
        }finally{body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-invalid")
    public void malformedSnapshotsCannotCreateDuplicateCellsOrExceedTheGeometryBudget(TestContext c) {
        var n=BodyShapeCodec.write(cells(2,1,1));n.putIntArray("positions",new int[]{BodyShapeCodec.pack(BlockPos.ORIGIN),BodyShapeCodec.pack(BlockPos.ORIGIN)});
        boolean duplicate=false;try{BodyShapeCodec.read(n);}catch(IllegalArgumentException expected){duplicate=true;}
        c.assertTrue(duplicate,"duplicate material cannot inflate mass");
        n=BodyShapeCodec.write(cells(1,1,1));n.putIntArray("states",new int[]{99});boolean palette=false;
        try{BodyShapeCodec.read(n);}catch(IllegalArgumentException expected){palette=true;}c.assertTrue(palette,"invalid palette index rejected");
        boolean overflow=false;try{BodyShapeCodec.write(cells(33,32,32));}catch(IllegalArgumentException expected){overflow=true;}
        c.assertTrue(overflow,"oversized blueprint rejected before spawning or removing terrain");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-unavailable")
    public void unloadedFarSectionPausesWithoutGeneratingTheEmptyEnvelopeOrErasingMomentum(TestContext c) {
        var body=create(c,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()),
            new BlockBodyEntity.Cell(new BlockPos(127,0,0),Blocks.IRON_BLOCK.getDefaultState())));
        try {
            var world=c.getWorld();int x=8192,z=8192;
            world.getChunk(x>>4,z>>4);body.setPosition(x+.5,body.getY(),z+.5);
            body.physics().withLinearVelocity(new Vec3(3,2,1)).withAngularVelocity(new Vec3(.1,.2,.3));
            var velocity=body.physics().linearVelocity();var momentum=body.physics().angularMomentum();var pos=body.getPos();
            c.assertTrue(world.getChunkManager().getWorldChunk((x>>4)-2,z>>4)==null,"a section inside the giant envelope starts absent");
            body.tick();
            c.assertTrue(body.getPos().distanceTo(pos)<1e-8&&body.boardingMotionPaused(),"unavailable hull volume pauses actual integration");
            c.assertTrue(body.physics().linearVelocity().minus(velocity).length()<1e-8&&body.physics().angularMomentum().minus(momentum).length()<1e-8,"unavailable data is not an inelastic impact");
            c.assertTrue(world.getChunkManager().getWorldChunk((x>>4)-2,z>>4)==null,"vanilla bookkeeping must not load empty hull-envelope columns");
        }finally{body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-scale-batch")
    public void damageBatchIsAtomicAndRebuildsAnUnchangedDistantSectionOnlyOnce(TestContext c) {
        var body=create(c,cells(32,2,16));try {
            var far=body.colliderSection(new BlockPos(20,0,0)).orElseThrow();
            var old=body.getLocalBlock(BlockPos.ORIGIN);long revision=body.shapeRevision();
            c.assertTrue(!body.setLocalBlocks(Map.of(BlockPos.ORIGIN,Blocks.AIR.getDefaultState(),new BlockPos(128,0,0),old)),"one invalid cell rejects the entire request");
            c.assertTrue(body.shapeRevision()==revision&&body.getLocalBlock(BlockPos.ORIGIN).equals(old),"failed batch cannot mutate mass/grid/revision");
            body.physics().withRotation(ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.5)).withLinearVelocity(new Vec3(1,2,3)).withAngularVelocity(new Vec3(.1,.2,.3));
            var point=new Vec3d(20.5,.5,.5);var worldPoint=body.localToWorld(point);var velocity=body.surfaceVelocityAt(worldPoint);
            var damage=new LinkedHashMap<BlockPos,BlockState>();
            for(int x=0;x<8;x++)for(int y=0;y<2;y++)for(int z=0;z<16;z++)damage.put(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
            c.assertTrue(body.setLocalBlocks(damage)&&body.shapeRevision()==256&&body.cells().size()==768,"256-cell damage batch publishes one rebased full snapshot");
            c.assertTrue(body.colliderSection(new BlockPos(20,0,0)).orElseThrow()==far,"unaffected section is retained");
            c.assertTrue(worldPoint.distanceTo(body.localToWorld(point))<1e-7&&velocity.distanceTo(body.surfaceVelocityAt(worldPoint))<1e-7,"batch preserves remaining world material and point velocity");
        }finally{body.discard();}c.complete();
    }
}
