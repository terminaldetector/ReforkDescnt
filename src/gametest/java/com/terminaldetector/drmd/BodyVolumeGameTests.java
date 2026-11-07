package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class BodyVolumeGameTests implements FabricGameTest {
    private BlockBodyEntity body(TestContext c,int x,int z) {
        var w=c.getWorld();w.getChunk(x>>4,z>>4);
        return BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()),
            new BlockBodyEntity.Cell(new BlockPos(127,0,0),Blocks.IRON_BLOCK.getDefaultState())),new Vec3d(x,150,z));
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-lifetime",tickLimit=200)
    public void playerAtFarEndHoldsTheWholeInteriorAndRemovalReleasesAllTickets(TestContext c) {
        var b=body(c,4096,4096);var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(4222,152,4096);
        c.waitAndRun(6,()->{try {
            var plan=BodyVolumeLeases.heldPlan(c.getWorld(),b.getUuid());
            c.assertTrue(plan.stream().anyMatch(x->x.x()==256)&&plan.stream().anyMatch(x->x.x()==260)&&plan.stream().anyMatch(x->x.x()==263),"far rider holds pivot, empty interior and opposite end");
            b.discard();c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())==0,"real entity removal releases all owned leases immediately");
        }finally{b.discard();p.discard();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-away",tickLimit=200)
    public void leavingTheHullDoesNotKeepAPlayerlessFleetLoadedForever(TestContext c) {
        var b=body(c,8192,8192);var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(8318,152,8192);
        c.waitAndRun(6,()->{
            c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())>0,"active rider creates volume leases");p.setPosition(0,150,0);
            c.waitAndRun(3,()->{try {c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())==0,"absent active players release the full volume");}
                finally{b.discard();p.discard();}c.complete();});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-move",tickLimit=200)
    public void movingVolumeDropsOldColumnsAndRetainsTheNewHullInsteadOfItsOldPivot(TestContext c) {
        var b=body(c,12288,12288);var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(12414,152,12288);
        c.waitAndRun(6,()->{
            c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())>0,"initial volume is leased");
            b.setPosition(b.getPos().add(512,0,0));p.setPosition(p.getPos().add(512,0,0));
            c.waitAndRun(6,()->{try {
                var plan=BodyVolumeLeases.heldPlan(c.getWorld(),b.getUuid());
                c.assertTrue(plan.stream().anyMatch(x->x.x()==800)&&plan.stream().noneMatch(x->x.x()<790),"new complete volume replaces old column tickets");
            }finally{b.discard();p.discard();}c.complete();});
        });
    }
    private record Fleet(TestContext c,List<BlockBodyEntity> bodies,List<UUID> joints,net.minecraft.server.network.ServerPlayerEntity player) implements AutoCloseable {
        public void close(){for(var id:joints)BodyJointRegistry.get(c.getWorld()).remove(id);
            var ids=bodies.stream().map(BlockBodyEntity::getUuid).toList();
            // A real demand-load may have already restored a different object with the same UUID.
            for(var b:BodySpatialIndex.loaded(c.getWorld()))if(ids.contains(b.getUuid()))b.discard();
            for(var b:bodies)b.discard();player.discard();}
    }
    private Fleet fleet(TestContext c,int x,int z) {
        var w=c.getWorld();for(int cx=(x-16)>>4;cx<=(x+544)>>4;cx++)for(int cz=(z-16)>>4;cz<=(z+16)>>4;cz++)w.getChunk(cx,cz);
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int n=0;n<128;n++)cells.add(new BlockBodyEntity.Cell(new BlockPos(n,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        var bodies=new ArrayList<BlockBodyEntity>();for(int n:new int[]{0,128,256,512})bodies.add(BlockBodyEntity.createLocal(w,cells,new Vec3d(x+n,180,z)));
        var links=new ArrayList<UUID>();var q=com.terminaldetector.drmd.client.portal.PortalTransform.Quat.IDENTITY;
        for(int n=0;n<2;n++){
            var a=bodies.get(n);var b=bodies.get(n+1);var link=new BodyJointLink(UUID.randomUUID(),
                new BodyJointLink.Endpoint(a.getUuid(),new BlockPos(127,0,0),new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(128,.5,.5),q),
                new BodyJointLink.Endpoint(b.getUuid(),BlockPos.ORIGIN,new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,.5,.5),q),
                com.terminaldetector.drmd.physics.collision.BodyJointConstraint.Kind.FIXED,0,0,0,0,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
            BodyJointRegistry.get(w).add(w,a,b,link);links.add(link.id());
        }
        var p=c.createMockCreativeServerPlayerInWorld();p.getAbilities().flying=true;p.setPosition(x+.5,182,z+.5);
        return new Fleet(c,List.copyOf(bodies),List.copyOf(links),p);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-joint-island",tickLimit=200)
    public void playerNearOneModuleHoldsTheFarEndOfItsConnectedAssembly(TestContext c) {
        var f=fleet(c,24000,24000);
        c.waitAndRun(8,()->{try {
            c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),f.bodies.get(2).getUuid())>0,"player near the first module must hold the complete connected assembly beyond the per-body128m halo");
            c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),f.bodies.get(3).getUuid())==0,"unconnected distant hull remains outside the active assembly");
        }finally{f.close();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-unloaded-partner",tickLimit=200)
    public void anUnloadedConnectedModuleRetainsARequestForItsActualLastVolume(TestContext c) {
        var f=fleet(c,26000,26000);var missing=f.bodies.get(2);
        c.waitAndRun(8,()->{
            missing.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            c.waitAndRun(5,()->{try {
                c.assertTrue(BodyJointRegistry.get(c.getWorld()).forBody(missing.getUuid()).size()==1,"ordinary unload preserves the connection to the missing physical identity");
                c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),missing.getUuid())>0,"occupied island must request the real stored volume of its unloaded partner");
                c.assertTrue(BodySpatialIndex.loaded(c.getWorld()).stream().noneMatch(b->b.getUuid().equals(missing.getUuid())),"loading request cannot manufacture a replacement body");
            }finally{f.close();}c.complete();});
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-damaged-pivot",tickLimit=200)
    public void aDamagedSparseHullStillRequestsItsRealEntityPivot(TestContext c) {
        var b=body(c,28000,28000);var p=c.createMockCreativeServerPlayerInWorld();p.getAbilities().flying=true;p.setPosition(28127,152,28000);
        var pivot=b.getPos();b.setLocalBlock(BlockPos.ORIGIN,Blocks.AIR.getDefaultState());
        c.waitAndRun(6,()->{try {
            c.assertTrue(b.cells().size()==1&&b.getPos().distanceTo(pivot)<1e-8,"material edit leaves the actual entity pivot at its saved world position");
            c.assertTrue(BodyVolumeLeases.heldPlan(c.getWorld(),b.getUuid()).stream().anyMatch(col->col.x()==Math.floorDiv((int)Math.floor(pivot.x),16)),"remaining material and the real saved entity section must both stay in the requested volume");
        }finally{b.discard();p.discard();}c.complete();});
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-cold-root",tickLimit=200)
    public void approachingACompletelyUnloadedAssemblyRequestsItsStoredRootVolume(TestContext c) {
        var f=fleet(c,30000,30000);var ids=f.bodies.subList(0,3).stream().map(BlockBodyEntity::getUuid).toList();
        c.waitAndRun(8,()->{
            // Establish actual absence before demand resumes. A player left at the root can
            // legitimately cause a fast reload before a fixed five-tick observation.
            f.player.setPosition(0,180,0);
            for(var b:f.bodies.subList(0,3))b.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            c.waitAndRun(5,()->{
                try {
                    c.assertTrue(BodySpatialIndex.loaded(c.getWorld()).stream().noneMatch(b->ids.contains(b.getUuid())),"fixture has no loaded root or module to seed the activity graph");
                    f.player.setPosition(30000.5,182,30000.5);
                }catch(Throwable e){f.close();throw e;}
                c.waitAndRun(2,()->{try {
                    c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),ids.getFirst())>0,"approaching the real saved root volume bootstraps an entirely unloaded assembly");
                    c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),ids.get(2))>0,"the missing far endpoint is requested through the same persisted island");
                    c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),f.bodies.get(3).getUuid())==0,"unconnected distant material is not loaded");
                }finally{f.close();}c.complete();});
            });
        });
    }

}
