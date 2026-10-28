package com.terminaldetector.drmd.inventory;
import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
/** Free-flight equipment uses real persisted items; a mounted hull retains its own inventory. */
public final class DroneLoadout {
 private DroneLoadout(){}
 public static Inventory of(PlayerEntity p){return p.getVehicle() instanceof com.terminaldetector.drmd.entity.PyroShipEntity ship?new DroneInventory(ship):DescentPlayerData.get(p).droneLoadout();}
 public static NbtCompound save(PlayerEntity p){var list=DefaultedList.ofSize(5,ItemStack.EMPTY);var inv=DescentPlayerData.get(p).droneLoadout();for(int i=0;i<5;i++)list.set(i,inv.getStack(i));var n=new NbtCompound();Inventories.writeNbt(n,list,p.getRegistryManager());return n;}
 public static void load(PlayerEntity p,NbtCompound n){var list=DefaultedList.ofSize(5,ItemStack.EMPTY);Inventories.readNbt(n,list,p.getRegistryManager());var inv=DescentPlayerData.get(p).droneLoadout();for(int i=0;i<5;i++)inv.setStack(i,list.get(i));}
}
