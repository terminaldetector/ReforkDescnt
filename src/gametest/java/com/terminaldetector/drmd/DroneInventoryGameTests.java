package com.terminaldetector.drmd;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.inventory.*;
import com.terminaldetector.drmd.weapon.items.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
public final class DroneInventoryGameTests implements FabricGameTest {
 private record Fixture(ServerPlayerEntity p,PyroShipEntity ship) implements AutoCloseable {
  public void close(){DroneArsenal.clear(p.getUuid());p.stopRiding();ship.discard();p.discard();}
 }
 private Fixture fixture(TestContext c){var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(2,170,2));
  var p=c.createMockCreativeServerPlayerInWorld();p.getInventory().clear();p.setPosition(Vec3d.ofCenter(at));var ship=ModEntities.PYRO_SHIP.create(w);ship.setPosition(p.getPos());ship.setOwnerUuid(p.getUuid());w.spawnEntity(ship);
  DescentPlayerData.get(p).setEnabled(true);DescentPlayerData.get(p).setEnergy(100);p.startRiding(ship,true);return new Fixture(p,ship);
 }
 private long shots(TestContext c,ServerPlayerEntity p){return c.getWorld().getEntitiesByClass(ProjectileEntity.class,p.getBoundingBox().expand(30),e->e.getOwnerLiving()==p).size();}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-held-fire")
 public void heldWeaponFiresOnBareHullWithoutHiddenHardpointCopy(TestContext c){try(var f=fixture(c)){
  var s=new ItemStack(ModItems.LASER);f.p.setStackInHand(Hand.MAIN_HAND,s);long n=shots(c,f.p);
  DroneArsenal.receive(f.p,1);DroneArsenal.tick(f.p);
  c.assertTrue(shots(c,f.p)>n,"LMB produces real projectiles on a bare piloted hull");
  c.assertTrue(f.p.getMainHandStack()==s,"original inventory stack is preserved");
 }c.complete();}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-dual-fire")
 public void primaryAndSecondaryFireFromPersistedSocketsWithoutReplacingHeldBuildingItem(TestContext c){try(var f=fixture(c)){
  f.ship.setWeaponSlot(ShipWeaponSlot.SIDE_LEFT,new ItemStack(ModItems.LASER));f.ship.setWeaponSlot(ShipWeaponSlot.LOWER,new ItemStack(ModItems.CONCUSSION));
  var bricks=new ItemStack(Items.BRICKS,32);f.p.setStackInHand(Hand.MAIN_HAND,bricks);long n=shots(c,f.p);
  DroneArsenal.receive(f.p,3);DroneArsenal.tick(f.p);
  c.assertTrue(shots(c,f.p)>=n+3,"independent primary bank and secondary missile actually fire");
  c.assertTrue(f.p.getMainHandStack()==bricks && bricks.getCount()==32,"temporary item context never consumes/replaces building inventory");
 }c.complete();}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-charge-fire")
 public void releasingAChargedSocketFiresFusionAndClearsUseState(TestContext c){try(var f=fixture(c)){
  f.ship.setWeaponSlot(ShipWeaponSlot.SIDE_LEFT,new ItemStack(ModItems.FUSION));long n=shots(c,f.p);
  DroneArsenal.receive(f.p,1);for(int i=0;i<12;i++)DroneArsenal.tick(f.p);
  c.assertTrue(shots(c,f.p)==n,"charged gun waits for release");DroneArsenal.receive(f.p,0);DroneArsenal.tick(f.p);
  c.assertTrue(shots(c,f.p)>n,"release fires the real Fusion implementation");c.assertTrue(!f.p.isUsingItem(),"no stuck vanilla using-item state");
 }c.complete();}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-inventory-persistence")
 public void shiftClickMovesExactlyOneWeaponIntoHullAndNbtRestoresIt(TestContext c){try(var f=fixture(c)){
  f.p.getInventory().setStack(9,new ItemStack(ModItems.LASER));var h=new DroneInventoryHandler(7,f.p.getInventory(),new DroneInventory(f.ship));
  h.quickMove(f.p,5);c.assertTrue(f.p.getInventory().getStack(9).isEmpty(),"source slot consumed exactly once");
  c.assertTrue(f.ship.getWeaponSlot(ShipWeaponSlot.SIDE_LEFT).isOf(ModItems.LASER),"primary socket holds the actual item");
  var nbt=new NbtCompound();f.ship.writeCustomDataToNbt(nbt);var restored=ModEntities.PYRO_SHIP.create(c.getWorld());restored.readCustomDataFromNbt(nbt);
  c.assertTrue(restored.getWeaponSlot(ShipWeaponSlot.SIDE_LEFT).isOf(ModItems.LASER),"saved hull restores socket");
  h.quickMove(f.p,0);c.assertTrue(f.ship.getWeaponSlot(ShipWeaponSlot.SIDE_LEFT).isEmpty(),"unequip empties socket");
  c.assertTrue(f.p.getInventory().count(ModItems.LASER)==1,"equip/unequip never duplicates an item");restored.discard();
 }c.complete();}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-menu-safety")
 public void openingInventoryStopsFiringEvenWhenButtonsAreHeld(TestContext c){try(var f=fixture(c)){
  f.ship.setWeaponSlot(ShipWeaponSlot.SIDE_LEFT,new ItemStack(ModItems.LASER));f.p.currentScreenHandler=new DroneInventoryHandler(8,f.p.getInventory(),new DroneInventory(f.ship));
  long n=shots(c,f.p);DroneArsenal.receive(f.p,7);DroneArsenal.tick(f.p);c.assertTrue(shots(c,f.p)==n,"GUI owns mouse and rejects firing");
  f.p.currentScreenHandler=f.p.playerScreenHandler;
 }c.complete();}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-free-inventory")
 public void freeFlightHasPersistentEquipmentWithoutSpawningAHull(TestContext c){try(var f=fixture(c)){
  f.p.stopRiding();DescentPlayerData.get(f.p).setEnabled(true);var inv=DroneLoadout.of(f.p);inv.setStack(0,new ItemStack(ModItems.LASER));
  var saved=DroneLoadout.save(f.p);inv.clear();DroneLoadout.load(f.p,saved);
  c.assertTrue(!f.p.hasVehicle() && DroneLoadout.of(f.p).getStack(0).isOf(ModItems.LASER),"free-flight equipment restores without a hull");
  long n=shots(c,f.p);DroneArsenal.receive(f.p,1);DroneArsenal.tick(f.p);c.assertTrue(shots(c,f.p)>n,"free-flight primary socket fires");
  DescentPlayerData.get(f.p).droneLoadout().clear();
 }c.complete();}

 @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-charge-menu-cancel")
 public void openingAMenuCancelsFusionChargeWithoutAnAccidentalShot(TestContext c){try(var f=fixture(c)){
  f.ship.setWeaponSlot(ShipWeaponSlot.SIDE_LEFT,new ItemStack(ModItems.FUSION));long n=shots(c,f.p);
  DroneArsenal.receive(f.p,1);for(int i=0;i<12;i++)DroneArsenal.tick(f.p);
  f.p.currentScreenHandler=new DroneInventoryHandler(9,f.p.getInventory(),new DroneInventory(f.ship));DroneArsenal.receive(f.p,0);DroneArsenal.tick(f.p);
  c.assertTrue(shots(c,f.p)==n,"opening a GUI cancels charge instead of releasing a shot");
  f.p.currentScreenHandler=f.p.playerScreenHandler;DroneArsenal.tick(f.p);
  c.assertTrue(shots(c,f.p)==n,"closing GUI cannot revive the old trigger");
 }c.complete();}

}
