package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class UfoWeaponGameTests implements FabricGameTest {
    private static final Map<TestContext,AiFixtureChunks> LEASES=new IdentityHashMap<>();
    private void admitted(TestContext c,Runnable action) {
        var base=c.getAbsolutePos(new BlockPos(8,220,8));var chunks=new AiFixtureChunks(c.getWorld(),base,3);LEASES.put(c,chunks);
        var positions=new ArrayList<BlockPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)positions.add(base.add(x*16,0,z*16));
        boolean[] started={false};int[] ticks={0};
        c.runAtEveryTick(()->{
            if(started[0])return;
            if(!chunks.entityTicking(positions.toArray(BlockPos[]::new))){if(++ticks[0]<80)return;started[0]=true;LEASES.remove(c);chunks.close();c.assertTrue(false,"weapon fixture needs real entity-ticking columns before spawn: "+chunks.readiness(positions.toArray(BlockPos[]::new)));return;}
            started[0]=true;
            try{action.run();}catch(Throwable e){LEASES.remove(c);chunks.close();throw e;}
        });
    }
    private SkyUfoEntity body(TestContext c) {
        var at=c.getAbsolutePos(new BlockPos(8,220,8));var w=c.getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        var b=ModEntities.SKY_UFO.create(w);b.setAutopilot(false);b.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);c.assertTrue(w.spawnEntity(b),"real UFO");b.tick();return b;
    }
    private ServerPlayerEntity pilot(TestContext c,SkyUfoEntity b) {
        var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.setPosition(b.localToWorld(new Vec3d(3.5,.1,-1)));c.assertTrue(UfoControlSystem.claim(p,b),"actual console capture");return p;
    }
    private List<ProjectileEntity> shots(TestContext c,SkyUfoEntity b,ServerPlayerEntity p){return c.getWorld().getEntitiesByClass(ProjectileEntity.class,b.getBoundingBox().expand(256),e->e.getOwnerLiving()==p);}
    private void clean(TestContext c,SkyUfoEntity b,ServerPlayerEntity p){for(var s:shots(c,b,p))s.discard();UfoControlSystem.release(p);p.discard();b.discard();var lease=LEASES.remove(c);if(lease!=null)lease.close();}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-transform",tickLimit=140)
    public void rotatedMountedShotsInheritSurfaceVelocityAndRecoilTheHull(TestContext c){admitted(c,()->rotatedMountedShotsInheritSurfaceVelocityAndRecoilTheHullReady(c));}
    private void rotatedMountedShotsInheritSurfaceVelocityAndRecoilTheHullReady(TestContext c) {
        var b=body(c);var q=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));b.physics().withRotation(q);b.publishPhysicsState();var p=pilot(c,b);
        try {
            b.physics().withLinearVelocity(new Vec3(7,3,-5)).withAngularVelocity(new Vec3(.3,.4,-.2));DescentPlayerData.get(p).setFlightVelocity(new Vec3d(1000,1000,1000));
            var muzzle=SkyUfoWeapons.muzzle(b,SkyUfoWeapons.LASERS.getFirst());var dir=q.rotate(new Vec3(0,0,1));var expected=b.surfaceVelocityAt(muzzle).multiply(.05).add(new Vec3d(dir.x(),dir.y(),dir.z()).multiply(2.5));
            var velocity=b.physics().linearVelocity();var momentum=b.physics().angularMomentum();var pv=p.getVelocity();
            c.assertTrue(UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(UfoControlSystem.epoch(p),0,3))&&shots(c,b,p).isEmpty(),"receipt is input only, never queued simulation or shooting");
            UfoControlSystem.drive(b);UfoControlSystem.fire(b);var all=shots(c,b,p);c.assertTrue(all.size()==4,"both actual laser and rocket banks fire: count="+all.size()+" mesh="+all.stream().map(s->s.getMeshKind()).toList()+" tag="+b.writeNbt(new NbtCompound()).getInt("laserCooldown")+"/"+b.writeNbt(new NbtCompound()).getInt("rocketCooldown"));
            var first=all.stream().filter(s->s.getPos().distanceTo(muzzle)<1e-8).findFirst().orElseThrow();c.assertTrue(first.getVelocity().distanceTo(expected)<1e-8,"launch inherits v+omega cross r, not stale player flight velocity");
            Vec3 impulse=new Vec3(0,0,0),torque=new Vec3(0,0,0);
            for(var slot:SkyUfoWeapons.LASERS){var at=SkyUfoWeapons.muzzle(b,slot);var j=dir.scaled(-24);impulse=impulse.plus(j);torque=torque.plus(new Vec3(at.x-b.massPosition().x(),at.y-b.massPosition().y(),at.z-b.massPosition().z()).cross(j));}
            for(var slot:SkyUfoWeapons.ROCKETS){var at=SkyUfoWeapons.muzzle(b,slot);var j=dir.scaled(-160);impulse=impulse.plus(j);torque=torque.plus(new Vec3(at.x-b.massPosition().x(),at.y-b.massPosition().y(),at.z-b.massPosition().z()).cross(j));}
            c.assertTrue(b.physics().linearVelocity().minus(velocity).minus(impulse.scaled(1/b.physicsMass())).length()<1e-8&&b.physics().angularMomentum().minus(momentum).minus(torque).length()<1e-8,"all recoil applies at real mounts on the hull");
            c.assertTrue(pv.equals(p.getVelocity()),"hull recoil must not jerk the pilot's hands/body");
            UfoControlSystem.drive(b);UfoControlSystem.fire(b);c.assertTrue(shots(c,b,p).size()==4,"duplicate drive in a tick cannot multiply shots");
            var tag=b.writeNbt(new NbtCompound());c.assertTrue(tag.getInt("laserCooldown")==8&&tag.getInt("rocketCooldown")==24,"cooldowns belong to the hull");
            var restored=ModEntities.SKY_UFO.create(c.getWorld());try{restored.readNbt(tag);var saved=restored.writeNbt(new NbtCompound());c.assertTrue(saved.getInt("laserCooldown")==8&&saved.getInt("rocketCooldown")==24,"read retains both firing cooldowns");}finally{restored.discard();}
        } finally {clean(c,b,p);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-authority",tickLimit=140)
    public void fireSnapshotsRejectReplayWrongOwnerEpochAndFlood(TestContext c){admitted(c,()->fireSnapshotsRejectReplayWrongOwnerEpochAndFloodReady(c));}
    private void fireSnapshotsRejectReplayWrongOwnerEpochAndFloodReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);var other=c.createMockCreativeServerPlayerInWorld();try {
            int e=UfoControlSystem.epoch(p);
            c.assertTrue(!UfoControlSystem.receiveFire(other,new UfoControlSystem.Fire(e,0,3))&&!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e+1,0,3))&&!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,0,4)),"unknown owner, stale epoch and invalid button mask rejected");
            c.assertTrue(UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,0,3))&&!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,0,3)),"replayed command rejected");
            for(int i=1;i<4;i++)c.assertTrue(UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,i,0)),"bounded latest snapshot");
            c.assertTrue(!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,4,3)),"flood above four snapshots per tick rejected");
            UfoControlSystem.drive(b);UfoControlSystem.fire(b);c.assertTrue(shots(c,b,p).isEmpty(),"latest zero mask cancels pending fire before simulation");
            UfoControlSystem.release(p);c.assertTrue(UfoControlSystem.claim(p,b)&&!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,9999,3)),"reclaimed console rejects previous authority");
        } finally {other.discard();clean(c,b,p);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-repair",tickLimit=140)
    public void destroyedWeaponMaterialCannotShootAndRealRepairRestoresOneBarrel(TestContext c){admitted(c,()->destroyedWeaponMaterialCannotShootAndRealRepairRestoresOneBarrelReady(c));}
    private void destroyedWeaponMaterialCannotShootAndRealRepairRestoresOneBarrelReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);try {
            var cut=new HashMap<BlockPos,net.minecraft.block.BlockState>();for(var s:SkyUfoWeapons.LASERS)cut.put(s,Blocks.AIR.getDefaultState());c.assertTrue(b.setLocalBlocks(cut),"real local laser damage");
            b.fireMountedWeapons(p,1);c.assertTrue(shots(c,b,p).isEmpty(),"destroyed bank cannot emit phantom lasers");
            var point=new Vec3d(3.5,-.5,3.5);var at=b.localToWorld(point);var velocity=b.physics().linearVelocity();
            c.assertTrue(new BlockBodyWorldAccess(b).placeLocalBlock(SkyUfoWeapons.LASERS.getFirst(),ModWorldBlocks.SHIP_LASER.getDefaultState())&&b.localToWorld(point).distanceTo(at)<1e-8&&velocity.equals(b.physics().linearVelocity()),"real repair preserves material pose/motion");
            b.fireMountedWeapons(p,1);c.assertTrue(shots(c,b,p).size()==1,"one real repaired barrel supplies one shot");
            for(var shot:shots(c,b,p))shot.discard();b.setLocalBlock(new BlockPos(0,1,0),Blocks.AIR.getDefaultState());b.fireMountedWeapons(p,2);c.assertTrue(shots(c,b,p).isEmpty(),"reactor loss removes weapon authority");
        } finally {clean(c,b,p);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-obstruction",tickLimit=140)
    public void materialInFrontOfAMuzzleBlocksThatBarrel(TestContext c){admitted(c,()->materialInFrontOfAMuzzleBlocksThatBarrelReady(c));}
    private void materialInFrontOfAMuzzleBlocksThatBarrelReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);try {
            var at=SkyUfoWeapons.LASERS.getFirst().add(0,0,1);c.assertTrue(b.setLocalBlock(at,Blocks.IRON_BLOCK.getDefaultState()),"actual forward armor");
            b.fireMountedWeapons(p,1);c.assertTrue(shots(c,b,p).size()==1,"only the unobstructed laser may fire beyond hull material");
        } finally {clean(c,b,p);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-damage",tickLimit=140)
    public void realLasersAndRocketsDamageTargetsOutsideTheSourceHull(TestContext c){admitted(c,()->realLasersAndRocketsDamageTargetsOutsideTheSourceHullReady(c));}
    private void realLasersAndRocketsDamageTargetsOutsideTheSourceHullReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);var targets=new ArrayList<net.minecraft.entity.passive.CowEntity>();try {
            int cells=b.cells().size();
            for(boolean rocket:new boolean[]{false,true}) {
                var slot=(rocket?SkyUfoWeapons.ROCKETS:SkyUfoWeapons.LASERS).getFirst();var muzzle=SkyUfoWeapons.muzzle(b,slot);var cow=EntityType.COW.create(c.getWorld());cow.setAiDisabled(true);cow.setNoGravity(true);cow.setPosition(muzzle.add(0,-.5,5));c.assertTrue(c.getWorld().spawnEntity(cow),"real target");targets.add(cow);float health=cow.getHealth();
                b.fireMountedWeapons(p,rocket?2:1);var projectile=shots(c,b,p).stream().filter(s->s.getPos().distanceTo(muzzle)<1e-8).findFirst().orElseThrow();
                for(int i=0;i<5&&!projectile.isRemoved();i++)projectile.tick();
                c.assertTrue(cow.getHealth()<health,"real projectile collision damages target: "+rocket);
                c.assertTrue(b.cells().size()==cells,"outgoing shots cannot damage their own source hull during launch");
                for(var s:shots(c,b,p))s.discard();cow.discard();
            }
        } finally {for(var t:targets)t.discard();clean(c,b,p);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-input-expiry",tickLimit=140)
    public void missingFireSnapshotsCannotKeepAnAutomaticGunHeld(TestContext c){admitted(c,()->missingFireSnapshotsCannotKeepAnAutomaticGunHeldReady(c));}
    private void missingFireSnapshotsCannotKeepAnAutomaticGunHeldReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);p.setNoGravity(true);
        try {c.assertTrue(UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(UfoControlSystem.epoch(p),0,1)),"initial fire input");}
        catch(Throwable e){clean(c,b,p);throw e;}
        c.waitAndRun(14,()->{try{c.assertTrue(UfoControlSystem.controls(p,b)&&shots(c,b,p).size()==2,"one stale snapshot expires before the next eight-tick laser cycle");c.complete();}finally{clean(c,b,p);}});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-weapon-mouse-owner",tickLimit=140)
    public void pilotMouseCannotAlsoFireHeldItemsOrPortalProjector(TestContext c){admitted(c,()->pilotMouseCannotAlsoFireHeldItemsOrPortalProjectorReady(c));}
    private void pilotMouseCannotAlsoFireHeldItemsOrPortalProjectorReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);try {
            // Use a registered real weapon rather than assuming an old item alias survives.
            var item=net.minecraft.registry.Registries.ITEM.stream().filter(i->i instanceof com.terminaldetector.drmd.weapon.items.DescentWeaponItem).findFirst().orElseThrow();
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(item));
            com.terminaldetector.drmd.inventory.DroneArsenal.receive(p,3);com.terminaldetector.drmd.inventory.DroneArsenal.tick(p);
            c.assertTrue(shots(c,b,p).isEmpty()&&!p.isUsingItem(),"ordinary held weapons cannot consume a ship pilot's trigger");
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.PORTAL_PROJECTOR));
            com.terminaldetector.drmd.world.portal.gun.PortalProjectorItem.trigger(p,1,false);
            c.assertTrue(!p.getItemCooldownManager().isCoolingDown(ModWorldBlocks.PORTAL_PROJECTOR),"ship trigger does not activate the held portal projector");
        } finally {com.terminaldetector.drmd.inventory.DroneArsenal.clear(p.getUuid());clean(c,b,p);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-moving-launch",tickLimit=140)
    public void normalWorldTicksCannotExplodeAnOutgoingRocketInsideItsMovingLauncher(TestContext c){admitted(c,()->normalWorldTicksCannotExplodeAnOutgoingRocketInsideItsMovingLauncherReady(c));}
    private void normalWorldTicksCannotExplodeAnOutgoingRocketInsideItsMovingLauncherReady(TestContext c) {
        var b=body(c);var p=pilot(c,b);p.getAbilities().flying=false;p.setPosition(b.localToWorld(new Vec3d(3.5,.001,-1)));var original=b.cells();
        com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());
        com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.ready(p,com.terminaldetector.drmd.network.BoardingNetworking.PROTOCOL);
        com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.tick(c.getWorld());
        c.assertTrue(com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.drives(p),"the moving-launch fixture uses real physical boarding");
        try{
            b.physics().withLinearVelocity(new Vec3(12,4,8)).withAngularVelocity(new Vec3(.2,.7,.8));
            c.assertTrue(UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(UfoControlSystem.epoch(p),0,3)),"real laser/rocket snapshot");
        }catch(Throwable e){clean(c,b,p);throw e;}
        c.waitAndRun(8,()->{try{
            c.assertTrue(b.cells().equals(original)&&b.writeNbt(new NbtCompound()).getList("materialDamage",net.minecraft.nbt.NbtElement.COMPOUND_TYPE).isEmpty(),"outgoing rounds must not hit and splash their own moving source in the first tick");
            c.assertTrue(shots(c,b,p).size()==4,"both banks escaped the source through ordinary world ticks: count="+shots(c,b,p).size()+" mesh="+shots(c,b,p).stream().map(s->s.getMeshKind()).toList()+" controls="+UfoControlSystem.controls(p,b));c.complete();
        }finally{clean(c,b,p);}});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-kit",tickLimit=140)
    public void realTurretKitInstallsAnIndependentMotorJoint(TestContext c){admitted(c,()->{
        var b=body(c);var p=pilot(c,b);
        try{
            var id=net.minecraft.util.Identifier.of("drmd","ship_turret_kit");
            c.assertTrue(net.minecraft.registry.Registries.ITEM.containsId(id),"a real craftable turret kit is registered");
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.registry.Registries.ITEM.get(id)));
            c.assertTrue(b.useLocalBlock(p,UfoControlSystem.CONSOLE),"actual console use installs the held turret kit");
            c.assertTrue(BodyJointRegistry.get(c.getWorld()).forBody(b.getUuid()).stream().anyMatch(j->j.kind()==com.terminaldetector.drmd.physics.collision.BodyJointConstraint.Kind.MOTOR),"a real independent motor joint connects the turret to its carrier");
        }finally{clean(c,b,p);}c.complete();
    });}
}
