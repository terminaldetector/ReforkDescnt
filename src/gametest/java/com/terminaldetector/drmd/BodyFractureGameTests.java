package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.stream.Collectors;

public final class BodyFractureGameTests implements FabricGameTest {
    private BlockBodyEntity line(TestContext c,int count) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int i=0;i<count;i++)cells.add(new BlockBodyEntity.Cell(new BlockPos(i,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        var w=c.getWorld();for(int x=1023;x<=1033;x++)for(int z=1023;z<=1025;z++)w.getChunk(x,z);
        return BlockBodyEntity.createLocal(w,cells,new Vec3d(16384,150,16384));
    }
    private Set<Integer> ids(TestContext c){return BodySpatialIndex.loaded(c.getWorld()).stream().map(BlockBodyEntity::getId).collect(Collectors.toSet());}
    private List<BlockBodyEntity> children(TestContext c,Set<Integer> before){return BodySpatialIndex.loaded(c.getWorld()).stream().filter(b->!before.contains(b.getId())).toList();}
    private Vec3 momentum(BlockBodyEntity b){return b.physics().linearVelocity().scaled(b.physicsMass());}
    private Vec3 angular(BlockBodyEntity b,Vec3 origin){return b.physics().angularMomentum().plus(b.massPosition().minus(origin).cross(momentum(b)));}
    private Vec3 pure(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-fracture-material")
    public void cuttingABridgeCreatesARealBodyWithContinuousMaterialMotionAndMomentum(TestContext c) {
        var body=line(c,5);var before=ids(c);var q=new Quat(.2,.3,.4,.7).normalized();Vec3 velocity=new Vec3(1.5,-2,.3),spin=new Vec3(.4,.2,-.1);
        body.physics().withRotation(q).withLinearVelocity(velocity).withAngularVelocity(spin);
        var positions=new HashMap<BlockPos,Vec3d>();var speeds=new HashMap<BlockPos,Vec3d>();
        for(var cell:body.cells()){var point=body.localToWorld(Vec3d.ofCenter(cell.pos()));positions.put(cell.pos(),point);speeds.put(cell.pos(),body.surfaceVelocityAt(point));}
        Vec3 origin=body.massPosition(),expectedP=velocity.scaled(4),expectedL=angular(body,origin).minus(spin.scaled(1./6));
        try {
            c.assertTrue(body.setLocalBlock(new BlockPos(2,0,0),Blocks.AIR.getDefaultState()),"bridge damage succeeds");
            var detached=children(c,before);c.assertTrue(detached.size()==1,"two remaining connected halves become exactly two physical bodies");
            var all=new ArrayList<>(detached);all.add(body);var owned=new HashSet<BlockPos>();Vec3 sumP=new Vec3(0,0,0),sumL=sumP;
            for(var part:all) {
                c.assertTrue(part.cells().size()==2,"one body per connected fragment, not per block");
                c.assertTrue(part.physics().angularVelocity().minus(spin).length()<1e-9,"fragment retains the original spin");
                for(var cell:part.cells()) {
                    c.assertTrue(owned.add(cell.pos()),"no local material can exist in both bodies");
                    var point=part.localToWorld(Vec3d.ofCenter(cell.pos()));
                    c.assertTrue(point.distanceTo(positions.get(cell.pos()))<1e-8,"fracture preserves the world pose of every remaining material point");
                    c.assertTrue(part.surfaceVelocityAt(point).distanceTo(speeds.get(cell.pos()))<1e-8,"fragment COM inherits v plus omega cross radius");
                }
                sumP=sumP.plus(momentum(part));sumL=sumL.plus(angular(part,origin));
            }
            c.assertTrue(owned.size()==4&&!owned.contains(new BlockPos(2,0,0)),"only the actual damaged block disappears");
            c.assertTrue(sumP.minus(expectedP).length()<1e-8,"all surviving linear momentum is conserved");
            c.assertTrue(sumL.minus(expectedL).length()<1e-8,"parallel-axis spin plus orbital angular momentum is conserved");
        }finally{children(c,before).forEach(BlockBodyEntity::discard);body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-fracture-batch")
    public void anAtomicTwoCutBatchDetachesEachComponentExactlyOnce(TestContext c) {
        var body=line(c,9);var before=ids(c);
        try {
            c.assertTrue(body.setLocalBlocks(Map.of(new BlockPos(2,0,0),Blocks.AIR.getDefaultState(),new BlockPos(5,0,0),Blocks.AIR.getDefaultState())),"atomic damage succeeds");
            var parts=children(c,before);c.assertTrue(parts.size()==2&&body.cells().size()==3,"largest component retains the original identity; two smaller bodies detach");
            c.assertTrue(parts.stream().allMatch(b->b.cells().size()==2),"each connected two-block remnant has one rigid body");
            c.assertTrue(!body.setLocalBlocks(Map.of(new BlockPos(2,0,0),Blocks.AIR.getDefaultState(),new BlockPos(5,0,0),Blocks.AIR.getDefaultState())),"replaying the same damage is a no-op");
            c.assertTrue(children(c,before).size()==2,"replayed edit cannot duplicate fragment entities");
        }finally{children(c,before).forEach(BlockBodyEntity::discard);body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-fracture-fast-inheritance")
    public void centrifugalFragmentVelocitySurvivesTheNextActualPhysicsTick(TestContext c) {
        var body=line(c,128);var before=ids(c);body.physics().withAngularVelocity(new Vec3(0,0,2));
        try {
            c.assertTrue(body.setLocalBlock(new BlockPos(64,0,0),Blocks.AIR.getDefaultState()),"long rotating hull fractures");
            var parts=new ArrayList<>(children(c,before));parts.add(body);c.assertTrue(parts.size()==2,"long hull separates into two coherent bodies");
            for(var part:parts) {
                var v=part.physics().linearVelocity();var p=part.massPosition();c.assertTrue(v.length()>60,"true inherited tangential COM speed exceeds the old50m/s limiter");
                part.tick();c.assertTrue(!part.boardingMotionPaused(),"test really integrates loaded terrain rather than restoring a paused velocity");
                c.assertTrue(part.physics().linearVelocity().minus(v).length()<1e-8,"COM rebasing and separation cannot silently clamp inherited momentum on the next tick");
                c.assertTrue(part.massPosition().minus(p.plus(v.scaled(.05))).length()<1e-7,"fragment completes a whole50ms with its inherited velocity");
            }
        }finally{children(c,before).forEach(BlockBodyEntity::discard);body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-fracture-budget")
    public void anExcessiveFragmentBatchCannotFloodEntitiesOrPartiallyConsumeTheHull(TestContext c) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();var edits=new HashMap<BlockPos,net.minecraft.block.BlockState>();
        for(int x=0;x<40;x++)for(int z=0;z<40;z++) {
            var pos=new BlockPos(x,0,z);cells.add(new BlockBodyEntity.Cell(pos,Blocks.IRON_BLOCK.getDefaultState()));edits.put(pos,Blocks.AIR.getDefaultState());
            if(x%2==0&&z%2==0)for(int y=1;y<=2;y++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
        }
        var body=BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(16384,150,16384));var before=ids(c);long revision=body.shapeRevision();
        try {
            c.assertTrue(!body.setLocalBlocks(edits),"399 new fragments require bounded destruction scheduling rather than one-tick entity allocation");
            c.assertTrue(body.cells().size()==2400&&body.shapeRevision()==revision,"refusal is atomic and keeps every block and revision");
            c.assertTrue(children(c,before).isEmpty(),"preflight refusal cannot leave a partial fleet of fragment entities");
        }finally{children(c,before).forEach(BlockBodyEntity::discard);body.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-fracture-nbt")
    public void eachSeparatedBodyStoresItsOwnGridQuaternionAndMaterialVelocity(TestContext c) {
        var body=line(c,5);var before=ids(c);body.physics().withRotation(new Quat(.2,.3,.4,.7).normalized()).withLinearVelocity(new Vec3(2,1,0)).withAngularVelocity(new Vec3(.1,.2,.3));
        try {
            c.assertTrue(body.setLocalBlock(new BlockPos(2,0,0),Blocks.AIR.getDefaultState()),"persistent fracture succeeds");
            var parts=new ArrayList<>(children(c,before));parts.add(body);c.assertTrue(parts.size()==2,"two independent saved bodies exist");
            var uuids=new HashSet<java.util.UUID>();
            for(var part:parts) {
                c.assertTrue(uuids.add(part.getUuid()),"fragments have distinct persistent identities");
                var clone=com.terminaldetector.drmd.entity.ModEntities.BLOCK_BODY.create(c.getWorld());
                try {
                    clone.readNbt(part.writeNbt(new net.minecraft.nbt.NbtCompound()));
                    c.assertTrue(clone.cells().equals(part.cells())&&clone.getUuid().equals(part.getUuid()),"local grid and identity round-trip without a parent dependency");
                    c.assertTrue(clone.rotation().equals(part.rotation())&&clone.physics().linearVelocity().equals(part.physics().linearVelocity())&&clone.physics().angularMomentum().equals(part.physics().angularMomentum()),"saved fragment retains its physical state");
                    c.assertTrue(clone.massPosition().minus(part.massPosition()).length()<1e-9,"saved fragment keeps its world COM");
                }finally{clone.discard();}
            }
            c.assertTrue(children(c,before).size()==1,"unspawned NBT inspection cannot duplicate the live fragment index");
        }finally{children(c,before).forEach(BlockBodyEntity::discard);body.discard();}c.complete();
    }


    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-fracture-speed-admission")
    public void anOutOfBudgetFragmentVelocityCannotBecomeASilentMomentumClamp(TestContext c) {
        var body=line(c,128);var before=ids(c);var velocity=new Vec3(0,510,0);body.physics().withLinearVelocity(velocity).withAngularVelocity(new Vec3(0,0,2));
        try {
            c.assertTrue(!body.setLocalBlock(new BlockPos(64,0,0),Blocks.AIR.getDefaultState()),"a575m/s child requires a larger collision budget; refuse before mutation instead of clipping its speed");
            c.assertTrue(body.cells().size()==128&&body.shapeRevision()==0&&body.physics().linearVelocity().equals(velocity),"unsupported fracture keeps all original material and momentum");
            c.assertTrue(children(c,before).isEmpty(),"no partial child fleet escapes a failed velocity preflight");
            body.physics().withLinearVelocity(new Vec3(0,512,0));
            c.assertTrue(!body.setLocalBlock(BlockPos.ORIGIN,Blocks.AIR.getDefaultState()),"a regular COM edit also checks the retained body's speed budget");
        }finally{children(c,before).forEach(BlockBodyEntity::discard);body.discard();}c.complete();
    }

}
