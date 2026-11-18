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

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="fixed-boarding-snapshot-codec",tickLimit=140)
    public void actualFixedGraphSnapshotCodecAndUnloadedIdentityNeverFoldAnArticulatedNeighbour(TestContext c){
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,140,8));var tickets=new AiFixtureChunks(w,at,0);
        var cells=List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));var q=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.35);var velocity=new Vec3d(3,.2,-1);
        var a=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at),q,velocity,Vec3d.ZERO);
        var b=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at).add(com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.mc(q.rotate(new Vec3(1,0,0)))),q,velocity,Vec3d.ZERO);
        var child=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at).add(com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.mc(q.rotate(new Vec3(2,0,0)))),q,velocity,Vec3d.ZERO);
        var fixed=new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(a.getUuid(),BlockPos.ORIGIN,new Vec3(1,.5,.5),Quat.IDENTITY),new BodyJointLink.Endpoint(b.getUuid(),BlockPos.ORIGIN,new Vec3(0,.5,.5),Quat.IDENTITY),BodyJointConstraint.Kind.FIXED,0,0,0,0,10000,10000,false);
        var hinge=new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(b.getUuid(),BlockPos.ORIGIN,new Vec3(1,.5,.5),Quat.IDENTITY),new BodyJointLink.Endpoint(child.getUuid(),BlockPos.ORIGIN,new Vec3(0,.5,.5),Quat.IDENTITY),BodyJointConstraint.Kind.HINGE,-10,10,0,0,10000,10000,false);
        var registry=BodyJointRegistry.get(w);registry.add(w,a,b,fixed);registry.add(w,b,child,hinge);boolean[] done={false};long start=w.getTime();
        c.runAtEveryTick(()->{if(done[0])return;try{
            c.assertTrue(w.getTime()-start<100,"finite ordinary active-joint admission");if(a.age<1||b.age<1||child.age<1||!registry.active(fixed.id(),w.getTime()))return;
            var state=com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.bodySnapshot(w,a.getId());var before=a.physics().snapshot();var childV=child.physics().linearVelocity();
            c.assertTrue(state.fixed()!=null&&state.fixed().size()==2&&state.fixed().stream().noneMatch(m->m.uuid().equals(child.getUuid())),"actual FIXED component includes two identities and excludes the actual HINGE neighbour");
            for(var member:state.fixed()){
                var body=BodySpatialIndex.find(w,member.uuid()).orElseThrow();var origin=new Vec3(state.x(),state.y(),state.z());var turn=new Quat(state.qx(),state.qy(),state.qz(),state.qw());
                c.assertTrue(member.id()==body.getId()&&member.shapeRevision()==body.shapeRevision()&&origin.plus(turn.rotate(new Vec3(member.x(),member.y(),member.z()))).minus(body.massPosition()).length()<1e-10,"one same-time relative COM and material identity");
                c.assertTrue(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.hamiltonProduct(turn,new Quat(member.qx(),member.qy(),member.qz(),member.qw())).normalized(),body.rotation()))>1-1e-12,"one same-time relative quaternion");
            }
            var packet=new com.terminaldetector.drmd.network.BoardingNetworking.State(7,11,true,a.getId(),1,2,3,4,5,6,true,com.terminaldetector.drmd.network.BoardingNetworking.PassengerPose.WORLD,state,w.getRegistryKey().getValue());
            var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),w.getRegistryManager());try{
                com.terminaldetector.drmd.network.BoardingNetworking.State.CODEC.encode(buffer,packet);var decoded=com.terminaldetector.drmd.network.BoardingNetworking.State.CODEC.decode(buffer);
                c.assertTrue(decoded.equals(packet)&&buffer.readableBytes()==0,"real bounded boarding wire codec round-trips the complete FIXED component and consumes exactly its bytes");
            }finally{buffer.release();}
            c.assertTrue(a.physics().matches(before)&&child.physics().linearVelocity().equals(childV),"snapshot and real codec do not mutate physical or articulated velocities");
            b.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var unknown=com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.bodySnapshot(w,a.getId());
            c.assertTrue(BodySpatialIndex.find(w,b.getUuid()).isEmpty()&&unknown.fixed()==null&&unknown.paused(),"an actually unloaded fixed member is UNKNOWN, never an invented singleton or stale-UUID display");
            registry.remove(fixed.id());var detached=com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.bodySnapshot(w,a.getId());
            c.assertTrue(detached.fixed()!=null&&detached.fixed().size()==1&&detached.fixed().getFirst().uuid().equals(a.getUuid())&&a.physics().matches(before),"real joint removal retires old child membership without resetting the remaining hull");
            done[0]=true;registry.remove(hinge.id());a.discard();b.discard();child.discard();tickets.close();c.complete();
        }catch(RuntimeException|Error error){done[0]=true;registry.remove(fixed.id());registry.remove(hinge.id());a.discard();b.discard();child.discard();tickets.close();throw error;}});
    }

}
