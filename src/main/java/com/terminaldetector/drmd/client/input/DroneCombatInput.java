package com.terminaldetector.drmd.client.input;
import net.minecraft.client.MinecraftClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.world.portal.gun.PortalProjectorItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.terminaldetector.drmd.network.ModNetworking;
public final class DroneCombatInput {
 private static int last,age;
 public static boolean ownsMouse(MinecraftClient c){
  if(com.terminaldetector.drmd.client.flight.UfoControlClient.active()||c.player==null||c.player.isSpectator()||c.currentScreen!=null)return false;
  var stack=c.player.getMainHandStack();
  if(stack.getItem() instanceof PortalProjectorItem)return false;
  return stack.getItem() instanceof DescentWeaponItem || stack.getItem() instanceof com.terminaldetector.drmd.world.bombardment.BombardmentItems.BombBayItem bomb&&bomb.programmable() || DescentClientState.enabled && (stack.isEmpty() || com.terminaldetector.drmd.inventory.DroneArsenal.supported(stack));
 }
 /** Same supported socket/fallback selection used by the server; no inventory mutation. */
 public static net.minecraft.item.ItemStack weapon(MinecraftClient c,int channel){
  if(c.player==null||channel<0||channel>2)return net.minecraft.item.ItemStack.EMPTY;
  if(DescentClientState.enabled && ownsMouse(c)){
   var s=com.terminaldetector.drmd.inventory.DroneLoadout.of(c.player).getStack(channel);
   if(com.terminaldetector.drmd.inventory.DroneArsenal.supported(s))return s;
  }
  var held=c.player.getMainHandStack();
  return channel<2&&com.terminaldetector.drmd.inventory.DroneArsenal.supported(held)?held:net.minecraft.item.ItemStack.EMPTY;
 }
 public static net.minecraft.item.ItemStack primary(MinecraftClient c){return weapon(c,0);}
 public static boolean megaActive(MinecraftClient c){
  if(!ownsMouse(c))return false;
  var primary=weapon(c,0);var secondary=weapon(c,1);
  return c.options.attackKey.isPressed() && primary.getItem() instanceof DescentWeaponItem a && "mega_laser".equals(a.getDef().behavior)
   || c.options.useKey.isPressed() && secondary.getItem() instanceof DescentWeaponItem b && "mega_laser".equals(b.getDef().behavior);
 }
 public static void tick(MinecraftClient c){
  if(c.player==null){last=age=0;return;}
  int mask=ownsMouse(c)?(c.options.attackKey.isPressed()?1:0)|(c.options.useKey.isPressed()?2:0):0;
  if(c.currentScreen==null && DescentClientState.enabled && DescentKeybinds.auxiliary.isPressed())mask|=4;
  if(mask!=last||mask!=0 && ++age>=2){ClientPlayNetworking.send(new ModNetworking.DroneTriggerPayload(mask));last=mask;age=0;}
 }
}
