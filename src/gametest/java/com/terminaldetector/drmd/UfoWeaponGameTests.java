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
    private void clean(TestContext c,SkyUfoEntity b,ServerPlayerEntity p){b.turretModule().flatMap(m->m.body(b)).ifPresent(net.minecraft.entity.Entity::discard);for(var s:shots(c,b,p))s.discard();UfoControlSystem.release(p);p.discard();b.discard();var lease=LEASES.remove(c);if(lease!=null)lease.close();}
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
            c.assertTrue(!UfoControlSystem.receiveFire(other,new UfoControlSystem.Fire(e,0,3))&&!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e+1,0,3))&&!UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,0,8)),"unknown owner, stale epoch and invalid button mask rejected");
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
            var module=b.turretModule().orElseThrow();var child=module.body(b).orElseThrow();var registry=BodyJointRegistry.get(c.getWorld());var link=registry.find(module.jointId()).orElseThrow();
            c.assertTrue(!link.collideConnected()&&!BodyJointLink.read(link.write()).collideConnected()&&!registry.collisionAllowed(b.getUuid(),child.getUuid())&&registry.collisionAllowed(child.getUuid(),UUID.randomUUID()),"bearing suppresses only the directly connected pair and persists that choice");
            var legacy=link.write();legacy.remove("collideConnected");c.assertTrue(BodyJointLink.read(legacy).collideConnected(),"old links keep their original collision behavior");
            var centre=child.localToWorld(new Vec3d(.5,.5,.5));var actor=new com.terminaldetector.drmd.physics.collision.OrientedBox(new Vec3(centre.x,centre.y,centre.z),new Vec3(.2,.2,.2),child.rotation());
            c.assertTrue(!child.collisionQuery().contacts(actor,0).isEmpty(),"bearing mask does not remove the actual module collider");
        }finally{clean(c,b,p);}c.complete();
    });}
    private SkyUfoTurret installTurret(TestContext c,SkyUfoEntity b,net.minecraft.server.network.ServerPlayerEntity p) {
        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));
        c.assertTrue(b.useLocalBlock(p,UfoControlSystem.CONSOLE),"real kit installation through local console use");return b.turretModule().orElseThrow();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-install-state",tickLimit=140)
    public void movingInstallConsumesOneKitAndInheritsEveryMaterialPoint(TestContext c){admitted(c,()->{
        var b=body(c);var q=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));b.physics().withRotation(q);b.publishPhysicsState();var p=pilot(c,b);p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        try {
            b.physics().withLinearVelocity(new Vec3(4,2,-3)).withAngularVelocity(new Vec3(.1,.2,.3));var old=b.physics().snapshot();var savedV=b.physics().linearVelocity();var savedL=b.physics().angularMomentum();var id=b.getUuid();
            var module=installTurret(c,b,p);var child=module.body(b).orElseThrow();
            c.assertTrue(p.getMainHandStack().isEmpty()&&child.cells().size()==5&&module.connected(b),"one kit becomes one five-cell physical module: empty="+p.getMainHandStack().isEmpty()+" cells="+child.cells().size()+" connected="+module.connected(b));
            c.assertTrue(id.equals(b.getUuid())&&b.massPosition().equals(old.position())&&b.physics().linearVelocity().equals(savedV)&&b.physics().angularMomentum().equals(savedL),"installation preserves the carrier's physical state");
            for(var cell:child.cells()){var at=child.localToWorld(Vec3d.ofCenter(cell.pos()));c.assertTrue(child.surfaceVelocityAt(at).distanceTo(b.surfaceVelocityAt(at))<1e-8,"new module inherits the full original material velocity field");}
            var tag=b.writeNbt(new NbtCompound());var copy=ModEntities.SKY_UFO.create(c.getWorld());try{copy.readNbt(tag);c.assertTrue(copy.turretModule().orElseThrow().bodyId().equals(child.getUuid())&&!copy.turretModule().orElseThrow().connected(copy),"saved identity is retained without granting authority to an unspawned clone");}finally{copy.discard();}
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));c.assertTrue(!b.useLocalBlock(p,UfoControlSystem.CONSOLE)&&p.getMainHandStack().getCount()==1,"a second kit cannot duplicate an existing joint/module");
        }finally{clean(c,b,p);}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-obstructed",tickLimit=140)
    public void blockedModuleInstallationPreservesTheKitAndWorld(TestContext c){admitted(c,()->{
        var b=body(c);var p=pilot(c,b);p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);var at=BlockPos.ofFloored(b.localToWorld(new Vec3d(.5,4.5,.5)));var before=c.getWorld().getBlockState(at);
        try{c.getWorld().setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),2);p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));c.assertTrue(!b.useLocalBlock(p,UfoControlSystem.CONSOLE)&&b.turretModule().isEmpty()&&p.getMainHandStack().getCount()==1,"occupied geometry cannot consume a kit or spawn a module");}
        finally{c.getWorld().setBlockState(at,before,2);clean(c,b,p);}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-authority",tickLimit=140)
    public void turretCommandsRejectWrongEpochReplayNonFiniteAndFlood(TestContext c){admitted(c,()->{
        var b=body(c);var p=pilot(c,b);try{var t=installTurret(c,b,p);int e=UfoControlSystem.epoch(p);
            c.assertTrue(!UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e+1,0,1))&&!UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,0,Float.NaN))&&!UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,0,2)),"wrong epoch and invalid motor command rejected");
            c.assertTrue(UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,0,1))&&!UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,0,1)),"duplicate motor snapshot rejected");
            for(int i=1;i<4;i++)c.assertTrue(UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,i,1)),"bounded accepted snapshot");c.assertTrue(!UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,4,1)),"motor packet budget");
            UfoControlSystem.drive(b);c.assertTrue(BodyJointRegistry.get(c.getWorld()).find(t.jointId()).orElseThrow().speed()==.6,"input configures a real bounded motor");UfoControlSystem.release(p);c.assertTrue(BodyJointRegistry.get(c.getWorld()).find(t.jointId()).orElseThrow().speed()==0,"release retires motor command through forces");
        }finally{clean(c,b,p);}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-detach",tickLimit=140)
    public void breakingTheActualRoofAnchorDetachesWithoutResetOrPhantomFire(TestContext c){admitted(c,()->{
        var b=body(c);var p=pilot(c,b);var t=installTurret(c,b,p);var child=t.body(b).orElseThrow();
        try{
            child.physics().withLinearVelocity(new Vec3(3,2,1)).withAngularVelocity(new Vec3(.1,.2,.3));var state=child.physics().snapshot();var savedV=child.physics().linearVelocity();var savedL=child.physics().angularMomentum();
            c.assertTrue(b.setLocalBlock(SkyUfoTurret.ROOF,Blocks.AIR.getDefaultState()),"actual roof anchor damage");t.tick(b);
            c.assertTrue(!t.connected(b)&&BodyJointRegistry.get(c.getWorld()).find(t.jointId()).isEmpty()&&child.physicsGravity(),"broken material releases the real joint and power");
            c.assertTrue(child.massPosition().equals(state.position())&&child.physics().linearVelocity().equals(savedV)&&child.physics().angularMomentum().equals(savedL),"free module retains pose, velocity and angular momentum at separation");
            b.fireMountedWeapons(p,4);c.assertTrue(shots(c,b,p).isEmpty(),"a disconnected module cannot fire from its old parent");
        }finally{clean(c,b,p);}c.complete();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-turret-live",tickLimit=160)
    public void realMotorAndFiringRemainAttachedDuringCarrierMotion(TestContext c){admitted(c,()->{
        var b=body(c);var p=pilot(c,b);var t=installTurret(c,b,p);var child=t.body(b).orElseThrow();var start=b.massPosition();
        p.getAbilities().flying=false;p.setPosition(b.localToWorld(new Vec3d(3.5,.001,-1)));com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());
        com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.ready(p,com.terminaldetector.drmd.network.BoardingNetworking.PROTOCOL);com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.tick(c.getWorld());
        var seen=new HashMap<java.util.UUID,ProjectileEntity>();int[] ticks={0};double[] error={0};Runnable[] step={null};
        step[0]=()->{try{
            int tick=ticks[0]++,e=UfoControlSystem.epoch(p);
            c.assertTrue(UfoControlSystem.receive(p,new UfoControlSystem.Input(e,tick,.2f,0,0,.1f,.1f,.3f))&&UfoControlSystem.receiveTurret(p,new UfoControlSystem.TurretInput(e,tick,1))&&UfoControlSystem.receiveFire(p,new UfoControlSystem.Fire(e,tick,4)),"actual controlled carrier and turret snapshots");
            for(var shot:shots(c,b,p))seen.put(shot.getUuid(),shot);
            error[0]=Math.max(error[0],b.localToWorld(new Vec3d(.5,4,.5)).distanceTo(child.localToWorld(new Vec3d(.5,0,.5))));
            // GameTestState removes a Runnable after invoking it. Re-enqueue a fresh wrapper,
            // otherwise reusing this same map key deletes the next scheduled invocation.
            if(ticks[0]<32){c.waitAndRun(1,()->step[0].run());return;}
            double dot=Math.abs(ImmPtlQuaternions.dot(b.rotation(),child.rotation()));
            c.assertTrue(t.connected(b)&&error[0]<.03&&2*Math.acos(Math.min(1,dot))>.25,"motor changes the real child orientation while the physical anchor stays attached: "+error[0]);
            c.assertTrue(b.massPosition().minus(start).length()>.1&&seen.values().stream().anyMatch(s->s.getMeshKind()==0)&&seen.values().stream().anyMatch(s->s.getMeshKind()==1),"moving carrier emits real laser and rocket entities from its module");
            for(var shot:seen.values())shot.discard();clean(c,b,p);c.complete();
        }catch(Throwable ex){System.out.println("TURRET_LIVE_FAILURE "+ex+" tick="+ticks[0]+" anchor="+error[0]+" root="+b.massPosition()+" child="+child.massPosition()+" connected="+t.connected(b));for(var shot:seen.values())shot.discard();clean(c,b,p);throw ex;}};
        c.waitAndRun(1,step[0]);
    });}
}
