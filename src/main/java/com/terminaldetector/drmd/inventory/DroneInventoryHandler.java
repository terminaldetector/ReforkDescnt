package com.terminaldetector.drmd.inventory;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.registry.*;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Identifier;
public final class DroneInventoryHandler extends ScreenHandler {
 public static final ScreenHandlerType<DroneInventoryHandler> TYPE=new ScreenHandlerType<>(DroneInventoryHandler::new,FeatureFlags.VANILLA_FEATURES);
 private final Inventory hull;
 private final PlayerEntity owner;
 public static void register(){Registry.register(Registries.SCREEN_HANDLER,Identifier.of("drmd","drone_inventory"),TYPE);}
 public DroneInventoryHandler(int id,PlayerInventory inventory){this(id,inventory,new SimpleInventory(5));}
 public DroneInventoryHandler(int id,PlayerInventory inventory,Inventory hull){super(TYPE,id);this.hull=hull;this.owner=inventory.player;checkSize(hull,5);
  for(int i=0;i<5;i++){final int index=i;addSlot(new Slot(hull,i,16+i*36,91){
   public int getMaxItemCount(){return 1;}
   public boolean canInsert(ItemStack s){return index==4?s.getItem() instanceof com.terminaldetector.drmd.flight.AcceleratorModuleItem:index==3||DroneArsenal.supported(s);}
  });}
  for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,x+y*9+9,8+x*18,126+y*18));
  for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,8+x*18,184));
 }
 public boolean canUse(PlayerEntity p){return p==owner && p.isAlive() && hull.canPlayerUse(p);}
 public ItemStack quickMove(PlayerEntity p,int i){var slot=slots.get(i);if(!slot.hasStack())return ItemStack.EMPTY;var s=slot.getStack();var copy=s.copy();
  if(i<5){if(!insertItem(s,5,slots.size(),true))return ItemStack.EMPTY;}
  else {boolean moved=false;for(int j=0;j<5&&!moved;j++)if(slots.get(j).canInsert(s))moved=insertItem(s,j,j+1,false);if(!moved)return ItemStack.EMPTY;}
  if(s.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();return copy;
 }
 public static void open(net.minecraft.server.network.ServerPlayerEntity p){
  p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,player)->new DroneInventoryHandler(id,inv,DroneLoadout.of(p)),net.minecraft.text.Text.literal("Дрон / оснащение")));
 }
}
