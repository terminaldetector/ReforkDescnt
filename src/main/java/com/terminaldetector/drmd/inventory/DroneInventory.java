package com.terminaldetector.drmd.inventory;
import com.terminaldetector.drmd.entity.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
/** Adapter over the existing hull's persisted hardpoints, with no shadow inventory or free copies. */
public final class DroneInventory implements Inventory {
 public static final ShipWeaponSlot[] ORDER={ShipWeaponSlot.SIDE_LEFT,ShipWeaponSlot.LOWER,ShipWeaponSlot.UPPER,ShipWeaponSlot.SIDE_RIGHT};
 private final PyroShipEntity ship;
 public DroneInventory(PyroShipEntity ship){this.ship=ship;}
 public int size(){return 5;}
 public boolean isEmpty(){for(int i=0;i<size();i++)if(!getStack(i).isEmpty())return false;return true;}
 public ItemStack getStack(int i){return i==4?ship.getPropulsionModule():ship.getWeaponSlot(ORDER[i]);}
 public void setStack(int i,ItemStack s){if(i==4)ship.setPropulsionModule(s);else ship.setWeaponSlot(ORDER[i],s);}
 public ItemStack removeStack(int i,int count){var s=getStack(i).split(count);markDirty();return s;}
 public ItemStack removeStack(int i){var s=getStack(i);setStack(i,ItemStack.EMPTY);return s;}
 public void markDirty(){ship.syncLoadout();}
 public boolean canPlayerUse(PlayerEntity p){return p.isAlive() && !ship.isRemoved() && p.getVehicle()==ship;}
 public void clear(){for(int i=0;i<size();i++)setStack(i,ItemStack.EMPTY);}
}
