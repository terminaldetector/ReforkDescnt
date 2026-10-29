package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.nbt.NbtCompound;

public final class ShipGravityGameTests implements FabricGameTest {
    private static final Vec3 UP=new Vec3(0,1,0),ZERO=new Vec3(0,0,0);
    private record Fixture(TestContext c,ServerPlayerEntity p,BlockBodyEntity body) implements AutoCloseable {
        void step(){body.tick();BoardingSystem.tick(c.getWorld());}
        void land(){for(int i=0;i<45;i++)step();c.assertTrue(BoardingSystem.drives(p)&&BoardingSystem.state(p).grounded(),"real character lands in the rotating ship field: state="+BoardingSystem.state(p)+" player="+p.getPos()+" local="+body.worldToLocal(p.getPos())+" body="+body.getPos()+" q="+body.rotation()+" velocity="+p.getVelocity()+" indexed="+BodySpatialIndex.query(c.getWorld(),body.getBoundingBox()).contains(body)+" vanilla="+c.getWorld().getEntitiesByClass(BlockBodyEntity.class,body.getBoundingBox(),b->true).contains(body)+" loaded="+(c.getWorld().getEntityById(body.getId())==body));}
        public void close(){body.discard();p.discard();BoardingSystem.tick(c.getWorld());}
    }
    private static boolean canSwim(ServerPlayerEntity p) {
        try {
            var method=net.minecraft.entity.player.PlayerEntity.class.getDeclaredMethod("canChangeIntoPose",net.minecraft.entity.EntityPose.class);
            method.setAccessible(true);return (boolean)method.invoke(p,net.minecraft.entity.EntityPose.SWIMMING);
        }catch(ReflectiveOperationException e){throw new AssertionError("Cannot invoke the actual dev-runtime pose check",e);}
    }
    private Fixture fixture(TestContext c,Quat frame) {
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,140,8));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        for(var pos:BlockPos.iterate(at.add(-10,-10,-10),at.add(16,16,16)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
        for(var pos:BlockPos.iterate(at,at.add(7,0,7)))w.setBlockState(pos,Blocks.IRON_BLOCK.getDefaultState(),2);
        var body=BlockBodyEntity.assemble(w,at,at.add(7,0,7));body.physics().withRotation(frame);body.tick();body.setPassengerGravity(new Vec3(0,-32,0));
        var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.getAbilities().flying=false;
        var centre=body.getPos();var up=frame.rotate(UP);p.setPosition(centre.add(BoardingSystem.mc(up.scaled(3))));p.setVelocity(Vec3d.ZERO);
        com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());BoardingSystem.ready(p,BoardingNetworking.PROTOCOL);
        return new Fixture(c,p,body);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-six-axes")
    public void realPlayerCollisionAndEyesLandOnAllSixShipAxes(TestContext c) {
        for(var up:new Vec3[]{UP,new Vec3(0,-1,0),new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1)}) {
            var frame=com.terminaldetector.drmd.client.portal.PortalTransform.rotationBetween(UP,up);
            try(var f=fixture(c,frame)) {
                f.land();var state=BoardingSystem.state(f.p);
                c.assertTrue(PassengerFrame.up(state.frame()).dot(f.body.rotation().rotate(UP))>.999,"physical up follows the actual free hull, initial axis="+up+" frame="+state.frame()+" body="+f.body.rotation());
                var bounds=PassengerFrame.box(state.feet(),f.p.getWidth(),f.p.getHeight(),state.frame()).bounds();var actual=f.p.getBoundingBox();
                c.assertTrue(Math.abs(actual.minX-bounds.min().x())+Math.abs(actual.minY-bounds.min().y())+Math.abs(actual.minZ-bounds.min().z())<1e-6,"entity broadphase encloses the physical oriented shape");
                Vec3d expected=f.p.getPos().add(BoardingSystem.mc(PassengerFrame.up(state.frame())).multiply(f.p.getEyeHeight(f.p.getPose())));
                c.assertTrue(f.p.getEyePos().distanceTo(expected)<1e-6,"authoritative interaction eyes share the physical up");
            }
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-jump")
    public void aSidewaysJumpKeepsItsCarrierSpeedAndRemainsInsideTheShipField(TestContext c) {
        try(var f=fixture(c,ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),Math.PI/2))) {
            f.land();var before=BoardingSystem.state(f.p);var up=PassengerFrame.up(before.frame());
            f.p.addVelocity(0,0,.15);f.step();before=BoardingSystem.state(f.p);
            c.assertTrue(BoardingSystem.receive(f.p,new BoardingNetworking.Input(BoardingSystem.epoch(f.p),0,0,0,true,0,0,1)),"ship-local jump command accepted");
            f.step();var after=BoardingSystem.state(f.p);
            c.assertTrue(BoardingSystem.drives(f.p)&&!after.grounded()&&after.gravityOwner()==f.body.getId(),"airborne player keeps field authority, rather than reverting to vanilla world-Y");
            c.assertTrue(Math.abs(after.velocity().z()-before.velocity().z())<.01,"tangential carrier/knockback speed is preserved");
            c.assertTrue(Math.abs(after.velocity().minus(before.velocity()).dot(up)-6.8)<.05,"jump and acceleration use ship UP in world units");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-save")
    public void aRestoredHullKeepsItsGravityWithoutManufacturingAPassengerLease(TestContext c) {
        try(var f=fixture(c,Quat.IDENTITY)) {
            var tag=f.body.writeNbt(new NbtCompound());var restored=ModEntities.BLOCK_BODY.create(c.getWorld());
            try {
                restored.readNbt(tag);
                c.assertTrue(restored.passengerGravity().minus(new Vec3(0,-32,0)).length()<1e-10,"local gravity is part of the saved physical hull");
                c.assertTrue(!BoardingSystem.drives(f.p),"restoring a body alone cannot create a movement lease");
            } finally {restored.discard();}
            c.assertTrue(!BoardingSystem.ready(f.p,1)&&!BoardingSystem.ready(f.p,2),"old state codecs cannot opt into geometry revision protocol3");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-off")
    public void leavingADisabledFieldReturnsAnUprightPlayerWithoutZeroingItsWorldSpeed(TestContext c) {
        try(var f=fixture(c,ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),Math.PI/2))) {
            f.land();var away=PassengerFrame.up(BoardingSystem.state(f.p).frame());f.body.setPassengerGravity(ZERO);
            f.p.addVelocity(BoardingSystem.mc(away.scaled(.3)));
            for(int i=0;i<25;i++)f.step();
            c.assertTrue(!BoardingSystem.drives(f.p)&&!BoardingPose.active(f.p),"clear world-UP shape hands back to vanilla");
            c.assertTrue(Math.abs(BoardingSystem.pure(f.p.getVelocity()).dot(away))>.1,"release cannot erase departure speed");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-world-shape")
    public void realWorldNarrowphaseDistinguishesAnEmptyBroadphaseCornerFromAHeadObstacle(TestContext c) {
        try(var f=fixture(c,Quat.IDENTITY)) {
            f.body.discard();var feet=c.getAbsolutePos(new BlockPos(8,145,8));f.p.setPosition(Vec3d.of(feet));
            var frame=ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),Math.PI/4);
            var state=new DynamicBodyAttachment.State(BoardingSystem.pure(f.p.getPos()),ZERO,false,-1,frame,-1);
            c.getWorld().setBlockState(feet.up(),Blocks.IRON_BLOCK.getDefaultState(),2);
            c.assertTrue(BoardingWorldMotion.capture(f.p,state,true).canOrient(state.feet(),frame),"empty corner of a rotated character's AABB remains free");
            BoardingPose.publish(f.p,frame);
            c.assertTrue(!canSwim(f.p),"empty broadphase corners must not force an actually standing character into crawl");
            c.getWorld().setBlockState(feet.up().west(),Blocks.IRON_BLOCK.getDefaultState(),2);
            c.assertTrue(!BoardingWorldMotion.capture(f.p,state,true).canOrient(state.feet(),frame),"actual rotated head volume stops at solid terrain: pose="+f.p.getPose()+" width="+f.p.getWidth()+" height="+f.p.getHeight()+" block="+c.getWorld().getBlockState(feet.up().west())+" shape="+c.getWorld().getBlockState(feet.up().west()).getCollisionShape(c.getWorld(),feet.up().west()).getBoundingBoxes());
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-unknown")
    public void orientedWorldQueriesCannotLoadAnUnknownColumn(TestContext c) {
        try(var f=fixture(c,Quat.IDENTITY)) {
            int x=100000,z=100000;var chunks=c.getWorld().getChunkManager();
            c.assertTrue(chunks.getWorldChunk(x>>4,z>>4)==null,"remote fixture begins unloaded");
            var state=new DynamicBodyAttachment.State(new Vec3(x,140,z),ZERO,false,-1,Quat.IDENTITY,-1);
            var query=BoardingWorldMotion.capture(f.p,state,true);
            c.assertTrue(!query.canOrient(state.feet(),state.frame()),"unknown geometry is unavailable, never silently empty");
            c.assertTrue(chunks.getWorldChunk(x>>4,z>>4)==null,"oriented queries may not generate or join a chunk future");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ship-gravity-paused")
    public void anUntickedHullKeepsItsSavedMomentumWithoutMovingItsContactSurface(TestContext c) {
        try(var f=fixture(c,Quat.IDENTITY)) {
            var restored=ModEntities.BLOCK_BODY.create(c.getWorld());
            try {
                restored.readNbt(f.body.writeNbt(new NbtCompound()));
                Vec3 retained=new Vec3(2,.4,.3);restored.physics().withLinearVelocity(retained);
                c.assertTrue(restored.boardingMotionPaused(),"a restored hull has not simulated this world tick");
                c.assertTrue(restored.boardingMotion().surfaceVelocity(BoardingSystem.pure(restored.getPos()),1).length()<1e-10,"unticked geometry cannot push passengers with fictional motion");
                c.assertTrue(restored.physics().linearVelocity().equals(retained),"stored momentum remains available for resume");
                restored.tick();
                c.assertTrue(!restored.boardingMotionPaused(),"a real hull tick resumes contact motion");
                c.assertTrue(restored.boardingMotion().surfaceVelocity(BoardingSystem.pure(restored.getPos()),1).length()>1,"resumed hull exposes its actual motion");
            }finally{restored.discard();}
        }c.complete();
    }

}
