package com.terminaldetector.drmd.client.flight;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.input.DroneCombatInput;
import com.terminaldetector.drmd.world.contraption.BodyBlockInteraction;
import com.terminaldetector.drmd.world.portal.gun.PortalProjectorItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
public final class BodyEditClient {
 private static BodyBlockInteraction.Edit mining;
 private static net.minecraft.client.network.ClientPlayerEntity owner;
 private static int lastMine=-100,lastPlace=-100;
 private BodyEditClient(){}
 public static void register(){ClientTickEvents.END_CLIENT_TICK.register(c->{
  if(mining!=null&&(!eligible(c)||!c.options.attackKey.isPressed()))stop();
 });}
 private static boolean eligible(MinecraftClient c){return c.player!=null&&c.world!=null&&c.currentScreen==null&&!c.player.isSpectator()
  &&!DescentClientState.enabled&&!DroneCombatInput.ownsMouse(c)&&!(c.player.getMainHandStack().getItem() instanceof PortalProjectorItem)
  &&ClientPlayNetworking.canSend(BodyBlockInteraction.Edit.ID);}
 private static BodyBlockInteraction.Target target(MinecraftClient c){
  if(owner!=c.player){owner=c.player;lastMine=lastPlace=-100;mining=null;}
  return eligible(c)?BodyBlockInteraction.target(c.player,c.player.getEyePos(),c.player.getRotationVec(1),Math.min(16,c.player.getBlockInteractionRange()),b->{
   var predicted=BoardingClient.bodyView(b,1);return predicted==null?new BodyBlockInteraction.BodyPose(b.massPosition(),b.rotation()):new BodyBlockInteraction.BodyPose(predicted.position(),predicted.rotation());
  }).orElse(null):null;
 }
 public static boolean mine(MinecraftClient c,boolean held){
  if(!held){stop();return false;}var target=target(c);if(target==null){stop();return false;}
  var hit=target.hit();var packet=new BodyBlockInteraction.Edit(target.body().getUuid(),hit.revision(),hit.localBlock(),BodyBlockInteraction.MINE);
  if(c.player.age-lastMine<2)return true;lastMine=c.player.age;
  ClientPlayNetworking.send(packet);mining=packet;c.player.swingHand(Hand.MAIN_HAND);return true;
 }
 public static boolean place(MinecraftClient c){if(c.player==null||!(c.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem))return false;var target=target(c);if(target==null)return false;
  if(c.player.age-lastPlace<4)return true;lastPlace=c.player.age;
  var hit=target.hit();ClientPlayNetworking.send(new BodyBlockInteraction.Edit(target.body().getUuid(),hit.revision(),hit.localBlock(),BodyBlockInteraction.PLACE));
  c.player.swingHand(Hand.MAIN_HAND);return true;
 }
 private static void stop(){if(mining!=null){var c=MinecraftClient.getInstance();if(c.player!=null&&ClientPlayNetworking.canSend(BodyBlockInteraction.Edit.ID))ClientPlayNetworking.send(new BodyBlockInteraction.Edit(mining.body(),mining.revision(),mining.local(),BodyBlockInteraction.STOP));mining=null;}}
}
