package com.terminaldetector.drmd.inventory;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import java.util.*;
/** Server-owned button state and ordinary item firing, including charged/continuous item lifecycles. */
public final class DroneArsenal {
 private DroneArsenal(){}
 private static final Map<UUID,Controls> STATES=new HashMap<>();
 private static final class Controls {int mask;long received;final Hold[] holds=new Hold[3];}
 private static final class Hold {ItemStack stack;int age,max;Hold(ItemStack s,int max){stack=s;this.max=max;}}
 public static ItemStack weapon(net.minecraft.entity.player.PlayerEntity p,int channel){
  if(channel<0||channel>2)return ItemStack.EMPTY;
  if(DescentPlayerData.get(p).isEnabled()){
   var s=DroneLoadout.of(p).getStack(channel);if(s.getItem() instanceof DescentWeaponItem)return s;
  }
  return channel<2 && p.getMainHandStack().getItem() instanceof DescentWeaponItem?p.getMainHandStack():ItemStack.EMPTY;
 }
 public static void receive(ServerPlayerEntity p,int mask){
  var s=STATES.computeIfAbsent(p.getUuid(),id->new Controls());
  s.mask=(p.isAlive()&&!p.isSpectator()&&p.currentScreenHandler==p.playerScreenHandler)?mask&7:0;s.received=p.getWorld().getTime();
 }
 private static void withHand(ServerPlayerEntity p,ItemStack stack,Runnable action){
  var previous=p.getMainHandStack();p.setStackInHand(Hand.MAIN_HAND,stack);
  try{action.run();}finally{p.setStackInHand(Hand.MAIN_HAND,previous);}
 }
 private static void stop(ServerPlayerEntity p,Controls state,int i){var h=state.holds[i];if(h==null)return;
  withHand(p,h.stack,()->h.stack.getItem().onStoppedUsing(h.stack,p.getWorld(),p,Math.max(0,h.max-h.age)));
  state.holds[i]=null;
 }
 public static void tick(ServerPlayerEntity p){
  var state=STATES.get(p.getUuid());if(state==null)return;
  if(!p.isAlive()||p.isSpectator()||p.currentScreenHandler!=p.playerScreenHandler||p.getWorld().getTime()-state.received>6)state.mask=0;
  for(int i=0;i<3;i++){
   var selected=weapon(p,i);var hold=state.holds[i];boolean pressed=(state.mask&(1<<i))!=0;
   if(i>0 && (state.mask&1)!=0 && selected==weapon(p,0))pressed=false;
   if(hold!=null && (!pressed || hold.stack!=selected)){stop(p,state,i);hold=null;}
   if(!pressed || selected.isEmpty())continue;
   if(hold!=null){var h=hold;h.age++;withHand(p,h.stack,()->h.stack.getItem().usageTick(p.getWorld(),p,h.stack,Math.max(0,h.max-h.age)));if(h.age>=h.max)stop(p,state,i);continue;}
   if(p.getItemCooldownManager().isCoolingDown(selected.getItem()))continue;
   int max=selected.getMaxUseTime(p);
   withHand(p,selected,()->selected.getItem().use(p.getWorld(),p,Hand.MAIN_HAND));
   if(max>0 && p.isUsingItem()){state.holds[i]=new Hold(selected,max);p.clearActiveItem();}
  }
 }
 public static void clear(UUID id){STATES.remove(id);}
 public static void clear(){STATES.clear();}
}
