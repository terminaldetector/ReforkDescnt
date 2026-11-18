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
 public static boolean supported(ItemStack stack){return stack.getItem() instanceof DescentWeaponItem
  || stack.getItem() instanceof com.terminaldetector.drmd.world.engineer.EngineerTools.MiningLaserItem
  || stack.getItem() instanceof com.terminaldetector.drmd.world.engineer.EngineerTools.TunnelLaserItem
  || stack.getItem() instanceof com.terminaldetector.drmd.world.engineer.EngineerTools.RepairLaserItem
  || stack.getItem() instanceof com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem bomb&&bomb.programmable();}

 private static final Map<UUID,Controls> STATES=new HashMap<>();
 private static final class Controls {
  int mask,edges;long received;final Hold[] holds=new Hold[3];
  final ItemStack[] edgeStacks=new ItemStack[3];
 }
 private static final class Hold {ItemStack stack;int age,max;Hold(ItemStack s,int max){stack=s;this.max=max;}}
 public static ItemStack weapon(net.minecraft.entity.player.PlayerEntity p,int channel){
  if(channel<0||channel>2)return ItemStack.EMPTY;
  if(DescentPlayerData.get(p).isEnabled()){
   var s=DroneLoadout.of(p).getStack(channel);if(supported(s))return s;
  }
  return channel<2 && supported(p.getMainHandStack())?p.getMainHandStack():ItemStack.EMPTY;
 }
 public static void receive(ServerPlayerEntity p,int mask){
  var s=STATES.computeIfAbsent(p.getUuid(),id->new Controls());
   s.received=p.getWorld().getTime();
   if(com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p)||!p.isAlive()||p.isSpectator()||p.currentScreenHandler!=p.playerScreenHandler){s.mask=0;clearEdges(s);return;}
   int next=mask&7,rising=next&~s.mask;
   for(int i=0;i<3;i++){
    int bit=1<<i;var selected=weapon(p,i);
    if((s.edges&bit)!=0 && s.edgeStacks[i]!=selected){s.edges&=~bit;s.edgeStacks[i]=null;}
    if((rising&bit)!=0 && !selected.isEmpty()){s.edges|=bit;s.edgeStacks[i]=selected;}
   }
   // A press and release may arrive before the next server simulation tick.
   // Retain at most one edge per bank, without changing the latest held mask.
   s.mask=next;
 }
 private static void clearEdges(Controls s){s.edges=0;Arrays.fill(s.edgeStacks,null);}
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
  if(com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p)||!p.isAlive()||p.isSpectator()||p.currentScreenHandler!=p.playerScreenHandler||p.getWorld().getTime()-state.received>6){
   if(p.isAlive() && java.util.Arrays.stream(state.holds).anyMatch(java.util.Objects::nonNull))com.terminaldetector.drmd.network.ModNetworking.weaponLoad(p,0);
    state.mask=0;clearEdges(state);java.util.Arrays.fill(state.holds,null);return;
  }
   int taps=state.edges;state.edges=0;
   for(int i=0;i<3;i++){
    if((taps&(1<<i))!=0 && state.edgeStacks[i]!=weapon(p,i))taps&=~(1<<i);
    state.edgeStacks[i]=null;
   }
   // Consume before cooldown/selection checks: rejected taps are never queued
   // for a later shot, even if the cooldown or selected item subsequently changes.
   int requested=state.mask|taps;
   // A physical stack already charging on a held bank owns that lifecycle.
   // A fallback click on another bank must not release or restart its charge.
   for(int i=0;i<3;i++){
    var hold=state.holds[i];int bit=1<<i;
    if(hold==null || (state.mask&requested&bit)==0 || hold.stack!=weapon(p,i))continue;
    for(int j=0;j<3;j++)if(j!=i && weapon(p,j)==hold.stack)requested&=~(1<<j);
   }
   for(int i=1;i<3;i++)if((requested&1)!=0 && weapon(p,i)==weapon(p,0))requested&=~(1<<i);
   int heldMask=state.mask&requested;
   // Finish genuinely released/replaced charges first, so their real stop
   // cooldown is visible before a new bank tries to start another use.
   for(int i=0;i<3;i++){
    var hold=state.holds[i];
    if(hold!=null && ((heldMask&(1<<i))==0 || hold.stack!=weapon(p,i)))stop(p,state,i);
   }
  for(int i=0;i<3;i++){
    var selected=weapon(p,i);var hold=state.holds[i];boolean held=(heldMask&(1<<i))!=0,pressed=(requested&(1<<i))!=0;
   if(!pressed || selected.isEmpty())continue;
   if(hold!=null){var h=hold;h.age++;withHand(p,h.stack,()->h.stack.getItem().usageTick(p.getWorld(),p,h.stack,Math.max(0,h.max-h.age)));if(h.age>=h.max)stop(p,state,i);continue;}
   if(p.getItemCooldownManager().isCoolingDown(selected.getItem()))continue;
   int max=selected.getMaxUseTime(p);
   final int bank=i;
   withHand(p,selected,()->{if(selected.getItem() instanceof com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem bomb&&bomb.programmable())bomb.useBank(p.getWorld(),p,Hand.MAIN_HAND,bank);else selected.getItem().use(p.getWorld(),p,Hand.MAIN_HAND);});
    if(max>0 && p.isUsingItem()){
     state.holds[i]=new Hold(selected,max);p.clearActiveItem();
     if(!held)stop(p,state,i); // Already released: one real age-zero charged tap.
    }
  }
 }
 public static void clear(UUID id){STATES.remove(id);}
 public static void clear(){STATES.clear();}
}
