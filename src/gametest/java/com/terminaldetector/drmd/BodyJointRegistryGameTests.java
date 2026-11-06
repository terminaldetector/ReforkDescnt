package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class BodyJointRegistryGameTests implements FabricGameTest {
    private record Fixture(TestContext c,BlockBodyEntity a,BlockBodyEntity b,BodyJointLink link,BodyJointRegistry registry) implements AutoCloseable {
        public void close(){registry.remove(link.id());a.discard();b.discard();}
    }
    private Fixture fixture(TestContext c) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<5;x++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        var a=BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(40000,180,40000));
        var b=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),new Vec3d(40005,180,40000));
        var link=new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(a.getUuid(),new BlockPos(4,0,0),new Vec3(5,.5,.5),Quat.IDENTITY),new BodyJointLink.Endpoint(b.getUuid(),BlockPos.ORIGIN,new Vec3(0,.5,.5),Quat.IDENTITY),BodyJointConstraint.Kind.HINGE,-Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,0,0,100,200);
        var registry=BodyJointRegistry.get(c.getWorld());registry.add(c.getWorld(),a,b,link);return new Fixture(c,a,b,link,registry);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-registry-material")
    public void aJointFollowsTheActualDetachedMaterialAndBreaksWhenItsAnchorIsDestroyed(TestContext c) {
        try(var f=fixture(c)) {
            var before=new HashSet<UUID>();for(var b:BodySpatialIndex.loaded(c.getWorld()))before.add(b.getUuid());var point=f.a.localToWorld(new Vec3d(5,.5,.5));
            c.assertTrue(f.a.setLocalBlock(new BlockPos(2,0,0),Blocks.AIR.getDefaultState()),"jointed hull fractures through the real atomic edit");
            var parts=BodySpatialIndex.loaded(c.getWorld()).stream().filter(b->!before.contains(b.getUuid())).toList();c.assertTrue(parts.size()==1,"one independent fragment");var child=parts.getFirst();
            try {
                var link=f.registry.find(f.link.id()).orElseThrow();c.assertTrue(link.a().body().equals(child.getUuid())&&!f.registry.containsBody(f.a.getUuid()),"joint endpoint ownership moves with the block, without a second registry record");
                c.assertTrue(child.localToWorld(new Vec3d(link.a().point().x(),link.a().point().y(),link.a().point().z())).distanceTo(point)<1e-8,"material anchor world position survives COM rebasing");
                c.assertTrue(f.registry.forBody(child.getUuid()).size()==1&&f.registry.forBody(f.b.getUuid()).size()==1,"both endpoint indexes refer to the same stable joint identity");
                child.setLocalBlock(new BlockPos(4,0,0),Blocks.AIR.getDefaultState());
                c.assertTrue(f.registry.find(f.link.id()).isEmpty()&&!f.registry.containsBody(f.b.getUuid()),"destroying an anchor removes both indexes and cannot resurrect an orphan joint");
            }finally{child.discard();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-registry-save")
    public void independentRegistryNbtPreservesOneLinkAcrossLoadOrderAndComChanges(TestContext c) {
        try(var f=fixture(c)) {
            var old=f.link.bind(f.a,f.b).anchorA();var point=f.a.localToWorld(new Vec3d(5,.5,.5));
            f.a.setLocalBlock(BlockPos.ORIGIN,Blocks.AIR.getDefaultState());var saved=f.registry.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());
            var restored=BodyJointRegistry.fromNbt(saved,c.getWorld().getRegistryManager());var link=restored.find(f.link.id()).orElseThrow();
            c.assertTrue(link.equals(f.link)&&restored.forBody(f.a.getUuid()).size()==1&&restored.forBody(f.b.getUuid()).size()==1,"one authoritative identity restores independently of entity load order");
            c.assertTrue(!link.bind(f.a,f.b).anchorA().equals(old),"local material coordinates are adapted to the body's new COM each solver step");
            c.assertTrue(f.a.localToWorld(new Vec3d(5,.5,.5)).distanceTo(point)<1e-8,"unrelated mass edit cannot move the mechanical anchor");
            var clone=com.terminaldetector.drmd.entity.ModEntities.BLOCK_BODY.create(c.getWorld());
            try{clone.readNbt(f.a.writeNbt(new NbtCompound()));}finally{clone.discard();}
            c.assertTrue(f.registry.find(f.link.id()).isPresent(),"discarding an unspawned same-UUID NBT clone cannot delete a live joint");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-registry-break")
    public void breakingAConnectionDoesNotRecreateBodiesOrResetTheirPhysicalState(TestContext c) {
        try(var f=fixture(c)) {
            f.a.physics().withLinearVelocity(new Vec3(3,1,2)).withAngularVelocity(new Vec3(.1,.2,.3));f.b.physics().withLinearVelocity(new Vec3(-1,2,3)).withAngularVelocity(new Vec3(.3,.1,.2));
            var va=f.a.physics().linearVelocity();var vb=f.b.physics().linearVelocity();var la=f.a.physics().angularMomentum();var lb=f.b.physics().angularMomentum();
            c.assertTrue(f.registry.remove(f.link.id()),"server removes the one physical connection");
            c.assertTrue(f.a.physics().linearVelocity().equals(va)&&f.b.physics().linearVelocity().equals(vb)&&f.a.physics().angularMomentum().equals(la)&&f.b.physics().angularMomentum().equals(lb),"detached existing bodies keep their instantaneous world momentum");
            c.assertTrue(!f.a.isRemoved()&&!f.b.isRemoved()&&f.a.cells().size()==5&&f.b.cells().size()==1,"breaking a joint does not rebuild, replace or consume either hull");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-registry-location-save")
    public void lastRealVolumesSurviveAnEndpointUnloadAndLegacyDataNeedsNoInventedLocation(TestContext c) {
        try(var f=fixture(c)) {
            f.a.setLocalBlock(BlockPos.ORIGIN,Blocks.AIR.getDefaultState());
            c.assertTrue(f.registry.lastBounds(f.a.getUuid()).orElseThrow().equals(f.a.loadingBounds()),"a material edit refreshes the real volume even without endpoint migration");
            var saved=f.registry.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());var id=f.b.getUuid();var bounds=f.registry.lastBounds(id).orElseThrow();
            f.b.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored=BodyJointRegistry.fromNbt(saved,c.getWorld().getRegistryManager());
            c.assertTrue(restored.lastBounds(id).orElseThrow().equals(bounds)&&restored.find(f.link.id()).isPresent(),"saved numeric volume and stable joint survive independent entity unloading");
            var old=saved.copy();old.putInt("schema",1);old.remove("locations");var legacy=BodyJointRegistry.fromNbt(old,c.getWorld().getRegistryManager());
            c.assertTrue(legacy.find(f.link.id()).isPresent()&&legacy.lastBounds(id).isEmpty(),"legacy joints load without inventing a location for a missing hull");
            f.registry.remove(f.link.id());c.assertTrue(f.registry.lastBounds(id).isEmpty()&&f.registry.lastBounds(f.a.getUuid()).isEmpty(),"removing the last connection releases its location metadata");
        }c.complete();
    }
    private void rejectsLocation(TestContext c,NbtCompound data) {
        boolean rejected=false;try{BodyJointRegistry.fromNbt(data,c.getWorld().getRegistryManager());}catch(IllegalArgumentException expected){rejected=true;}
        c.assertTrue(rejected,"malformed or unrelated locations rejected before loading requests");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-registry-location-invalid")
    public void invalidLocationRecordsCannotRequestChunksOrCorruptTheLiveRegistry(TestContext c) {
        try(var f=fixture(c)) {
            var saved=f.registry.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());
            var bad=saved.copy();bad.getList("locations",net.minecraft.nbt.NbtElement.COMPOUND_TYPE).getCompound(0).putDouble("minx",Double.NaN);rejectsLocation(c,bad);
            bad=saved.copy();var list=bad.getList("locations",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);list.add(list.getCompound(0).copy());rejectsLocation(c,bad);
            bad=saved.copy();bad.getList("locations",net.minecraft.nbt.NbtElement.COMPOUND_TYPE).getCompound(0).putUuid("body",UUID.randomUUID());rejectsLocation(c,bad);
            c.assertTrue(f.registry.find(f.link.id()).isPresent()&&f.registry.lastBounds(f.a.getUuid()).isPresent(),"invalid serialized input cannot mutate the live world registry");
        }c.complete();
    }

}
