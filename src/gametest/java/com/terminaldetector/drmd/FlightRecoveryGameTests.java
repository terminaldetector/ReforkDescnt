package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** UG-001/005/009/021: exercise the actual player/vehicle lifecycle and velocity assignment. */
public final class FlightRecoveryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explicitOffDismountsAndCannotBeRearmedByTheOldHull(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        // Leave standing clearance below the empty template's barrier ceiling.
        Vec3d position=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,4,5)));
        player.setPosition(position);
        var ship=ModEntities.PYRO_SHIP.create(world); ship.setPosition(position); world.spawnEntity(ship);
        context.assertTrue(com.terminaldetector.drmd.world.gravity.GravityMount.uprightDismountPosition(player,ship).isPresent(),
            "ordinary lifecycle case starts with a real loaded standing exit");
        var data=DescentPlayerData.get(player); data.setEnabled(true); data.setRoll(117);
        data.setFlightVelocity(new Vec3d(12,4,-20)); data.setDashTimer(.15f); data.setHookActive(true);
        FlightSystem.input(player).afterburner=true;
        player.startRiding(ship,true);
        FlightSystem.disable(player,data);
        context.assertTrue(!player.hasVehicle() && !ship.hasPassengers(),"H-off severs both mount links");
        context.assertTrue(!data.isEnabled() && !player.hasNoGravity(),"ordinary control restored");
        context.assertTrue(data.getFlightVelocity().lengthSquared()==0 && data.getRoll()==0 && !data.isHookActive(),"no stale flight motion");
        context.assertTrue(!FlightSystem.input(player).afterburner,"held thrust cleared");
        ship.tick();
        context.assertTrue(!data.isEnabled() && !player.hasVehicle(),"old hull cannot rearm H-off");
        context.assertTrue(LocalOrientation.getUp(player.getUuid()).squaredDistanceTo(0,1,0)<1e-9,"world-up restored");
        ship.discard(); player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeFlightTogglesDoNotManufactureVehicles(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,7,5))));
        var data=DescentPlayerData.get(player); FlightSystem.disable(player,data);
        for(int i=0;i<8;i++) {
            FlightSystem.toggle(player);
            context.assertTrue(data.isEnabled() && !player.hasVehicle(),"native H-on is free 6DoF");
            FlightSystem.toggle(player);
            context.assertTrue(!data.isEnabled() && !player.hasVehicle(),"repeated H-off stays off");
        }
        context.assertTrue(world.getEntitiesByType(ModEntities.PYRO_SHIP,player.getBoundingBox().expand(3),e->true).isEmpty(),"no orphan auto-spawned hulls");
        player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void physicalHullConsumesBlocksPerTickNotBlocksPerSecond(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        Vec3d position=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,8,5)));
        player.setPosition(position);
        PyroShipEntity ship=ModEntities.PYRO_SHIP.create(world); ship.setPosition(position); world.spawnEntity(ship);
        var data=DescentPlayerData.get(player); data.setEnabled(true);
        Vec3d perSecond=new Vec3d(20,0,-10); data.setFlightVelocity(perSecond);
        player.startRiding(ship,true); ship.tick();
        context.assertTrue(ship.getVelocity().squaredDistanceTo(new Vec3d(1,0,-.5))<1e-9,"20 m/s becomes one block/tick");
        FlightSystem.disable(player,data); ship.discard(); player.discard(); context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void delayedClientStateCannotDisableOrMutateTheIntegratedServer(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        var server=DescentPlayerData.get(player); server.setEnabled(true); server.setEnergy(42);
        var mirror=DescentPlayerData.client(player.getUuid()); mirror.setEnabled(false); mirror.setEnergy(99);
        context.assertTrue(server!=mirror && server.isEnabled() && server.getEnergy()==42,
                "client packet mirror cannot overwrite server flight authority or resources");
        DescentPlayerData.clearClient();
        context.assertTrue(server.isEnabled(),"client disconnect cleanup leaves live server state intact");
        server.setEnabled(false); player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-exit-alternate-clearance")
    public void normalOffUsesLoadedAlternateClearanceWithoutDestroyingTheHull(TestContext c) {
        var world=c.getWorld();var at=c.getAbsolutePos(new BlockPos(6,10,6));
        for(var p:BlockPos.iterate(at.add(-5,-5,-5),at.add(5,6,5)))world.setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        // A low ceiling blocks the old world-up-only release, while the adjacent side is open.
        for(var p:BlockPos.iterate(at.add(-1,1,-1),at.add(1,4,1)))world.setBlockState(p,net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState(),2);
        var player=c.createMockCreativeServerPlayerInWorld();var ship=ModEntities.PYRO_SHIP.create(world);
        var start=Vec3d.ofCenter(at);ship.setPosition(start);world.spawnEntity(ship);player.setPosition(start);
        try {
            var data=DescentPlayerData.get(player);data.setEnabled(true);player.startRiding(ship,true);
            FlightSystem.disable(player,data);
            c.assertTrue(!player.hasVehicle()&&!ship.hasPassengers()&&!ship.isRemoved(),"ordinary off removes both links, retains the explicit hull, and needs no damage/lava");
            c.assertTrue(!data.isEnabled()&&!player.hasNoGravity()&&player.getDimensions(net.minecraft.entity.EntityPose.STANDING).height()>1.7,
                "native ordinary standing authority is restored");
            c.assertTrue(world.isSpaceEmpty(player,player.getBoundingBox()),"release finds real clear standing space rather than leaving an upright body inside the ceiling");
            c.assertTrue(player.getPos().distanceTo(start)<4,"exit search is spatially bounded");
        } finally {ship.discard();player.discard();for(var p:BlockPos.iterate(at.add(-1,1,-1),at.add(1,4,1)))world.setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),2);}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-quaternion-boundary")
    public void actualPlayerMotionAndGravityBasisShareTheAcceptedQuaternion(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();
        try {
            var data=DescentPlayerData.get(player);data.setShipAttitude(0,1,0,1,0,0);
            var frame=data.shipOrientation();
            c.assertTrue(data.shipForward(player).y>.99999&&data.shipUp(player).x>.99999,"actual player stores a vertical quaternion without a yaw/pitch singularity");
            c.assertTrue(Math.abs(data.shipForward(player).dotProduct(data.shipUp(player)))<1e-10,"accepted basis stays orthogonal");
            data.setShipAttitude(Float.NaN,0,1,0,1,0);
            c.assertTrue(data.shipOrientation().equals(frame),"invalid later input retains the actual last accepted player orientation");
            var vector=new Vec3d(.2,3,-.7);var local=com.terminaldetector.drmd.physics.OrientationFrame.toLocal(frame,vector);
            c.assertTrue(com.terminaldetector.drmd.physics.OrientationFrame.toWorld(frame,local).squaredDistanceTo(vector)<1e-20,
                "local gravity vectors round-trip through the same canonical physical quaternion");
            FlightSystem.disable(player,data);
            c.assertTrue(!data.hasShipAttitude()&&data.shipOrientation().equals(com.terminaldetector.drmd.client.portal.PortalTransform.Quat.IDENTITY),
                "explicit exit resets the authoritative quaternion rather than retaining a hidden pose");
        } finally {player.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-exit-no-safe-clearance")
    public void normalOffWithNoLoadedClearUprightExitRetainsTheSameNativePilotAndHull(TestContext c){
        var world=c.getWorld();var at=c.getAbsolutePos(new BlockPos(6,10,6));var changed=new java.util.LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        // The2-block-high cabin fits the real2.2x1.2 hull. Its surrounding material leaves
        // no upright space above/beside it in the finite native exit envelope.
        for(var pos:BlockPos.iterate(at.add(-5,-3,-5),at.add(5,8,5))){
            var key=pos.toImmutable();changed.put(key,world.getBlockState(key));
            boolean cabin=Math.abs(pos.getX()-at.getX())<=1&&Math.abs(pos.getZ()-at.getZ())<=1&&pos.getY()>=at.getY()&&pos.getY()<=at.getY()+1;
            world.setBlockState(key,(cabin?net.minecraft.block.Blocks.AIR:net.minecraft.block.Blocks.IRON_BLOCK).getDefaultState(),2);
        }
        var player=c.createMockCreativeServerPlayerInWorld();var ship=ModEntities.PYRO_SHIP.create(world);var start=Vec3d.ofBottomCenter(at);
        try{
            ship.setPosition(start);c.assertTrue(world.spawnEntity(ship)&&world.isSpaceEmpty(ship,ship.getBoundingBox()),"actual original hull fits the loaded low cabin");
            player.setPosition(start);var data=DescentPlayerData.get(player);data.setEnabled(true);data.setRoll(42);data.setFlightVelocity(new Vec3d(2,3,4));player.setNoGravity(true);
            c.assertTrue(player.startRiding(ship,true),"actual native passenger links admitted");var before=player.getPos();
            c.assertTrue(com.terminaldetector.drmd.world.gravity.GravityMount.uprightDismountPosition(player,ship).isEmpty(),"actual loaded material has no safe bounded upright exit");
            FlightSystem.disable(player,data);
            c.assertTrue(player.getVehicle()==ship&&ship.hasPassenger(player)&&!ship.isRemoved()&&data.isEnabled(),"no-safe H-off preserves both exact links and existing authority");
            c.assertTrue(player.getPos().equals(before)&&player.hasNoGravity()&&data.getRoll()==42&&data.getFlightVelocity().equals(new Vec3d(2,3,4)),"refusal cannot teleport, reset attitude/momentum or erase original gravity ownership");
            FlightSystem.toggle(player);
            c.assertTrue(data.isEnabled()&&player.getVehicle()==ship&&!com.terminaldetector.drmd.world.gravity.FootGravitySystem.isActive(player),"refused H toggle cannot invoke foot adoption while flight still owns the pilot");
            FlightSystem.forceDisable(player,data);
            c.assertTrue(!player.hasVehicle()&&!ship.hasPassengers()&&!ship.isRemoved()&&!data.isEnabled()&&data.getFlightVelocity().lengthSquared()==0,
                "explicit lifecycle cleanup still severs/reset the same no-safe mount; disconnect or authoritative recovery must never be vetoed");
        }finally{
            DescentPlayerData.get(player).setEnabled(false);player.stopRiding();ship.discard();player.discard();changed.forEach((pos,state)->world.setBlockState(pos,state,2));
        }
        c.complete();
    }

}
