package com.terminaldetector.drmd;
import com.terminaldetector.drmd.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.client.config.DescentConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
/** Real key state -> production C2S -> real weapon; fixed first-person tool and actual handled inventory. */
public final class SnapshotFixCheck {
 private static boolean started,preparing,finished;private static volatile boolean ready,verified;
 private static int ticks,age,stage,frames;private static int[] levelBox;
 private static final String[] NAMES={"primary-fire","secondary-fire","tool-level","tool-rolled","drone-inventory"};
 private static final List<String> RESULTS=new ArrayList<>();
 public static void register(){ClientTickEvents.END_CLIENT_TICK.register(SnapshotFixCheck::tick);}
 private static void require(boolean b,String why){if(!b){finished=true;throw new IllegalStateException(why);}}
 private static void tick(MinecraftClient c){
  if(finished)return;if(++ticks>2400)throw new IllegalStateException("snapshot timeout stage="+stage+" age="+age);
  if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
   for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
  if(c.player==null||c.world==null||c.getServer()==null)return;
  com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
  DescentConfig.cockpit=false;DescentConfig.hud=false;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
  DescentConfig.weaponView=true;c.options.hudHidden=false;
  if(!started){started=true;c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.getToastManager().clear();c.getServer().execute(()->{
   var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));
   p.teleport(p.getServerWorld(),8.5,220,28.5,Set.of(),180,0);
  });return;}
  if(!ready&&!preparing&&c.player.getY()>210){
   var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
   if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(0,13,1)))return;
   preparing=true;c.getServer().execute(()->setup(c));return;
  }
  if(!ready||!com.terminaldetector.drmd.client.DescentClientState.enabled)return;
  if(++age==1)begin(c);
  if(stage<2){
   c.options.attackKey.setPressed(stage==0&&age<=10);c.options.useKey.setPressed(stage==1&&age<=10);
   if(age==12)c.getServer().execute(()->{
    var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
    long count=p.getServerWorld().getEntitiesByClass(ProjectileEntity.class,p.getBoundingBox().expand(200),shot->shot.getOwnerLiving()==p && (stage==0||shot.getMeshKind()==ProjectileEntity.MESH_ROCKET)).size();
    require(count>0,"actual "+NAMES[stage]+" key did not fire a projectile");verified=true;
   });
  }else if(stage==4){
   require(c.currentScreen==null||c.currentScreen instanceof com.terminaldetector.drmd.client.screen.DroneInventoryScreen,"unexpected inventory screen");
   if(c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen){
    require(screen.getScreenHandler().getSlot(0).getStack().isOf(ModItems.LASER),"primary GUI socket not synchronized");
    require(screen.getScreenHandler().getSlot(1).getStack().isOf(ModItems.CONCUSSION),"secondary GUI socket not synchronized");verified=true;
   }
  }else verified=true;
 }
 private static void setup(MinecraftClient c){
  var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var w=p.getServerWorld();
  for(var b:BlockPos.iterate(new BlockPos(0,216,16),new BlockPos(16,230,32)))w.setBlockState(b,net.minecraft.block.Blocks.AIR.getDefaultState(),2);
  w.setTimeOfDay(6000);p.getInventory().clear();p.getInventory().selectedSlot=0;
  var data=DescentPlayerData.get(p);data.setEnergyMax(10000);data.setEnergy(10000);
  com.terminaldetector.drmd.flight.FlightSystem.enable(p,data);
  require(!p.hasVehicle(),"H must preserve free 6DoF without manufacturing a hull");
  var inventory=com.terminaldetector.drmd.inventory.DroneLoadout.of(p);inventory.setStack(0,new ItemStack(ModItems.LASER));inventory.setStack(1,new ItemStack(ModItems.CONCUSSION));
  data.setWepRecoil(0);com.terminaldetector.drmd.network.ModNetworking.syncPlayer(p,data);
  p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(ModItems.LASER));
  ready=true;
 }
 private static void begin(MinecraftClient c){
  verified=false;frames=0;c.options.attackKey.setPressed(false);c.options.useKey.setPressed(false);
  if(stage==2||stage==3){
   var up=stage==2?new Vec3d(0,1,0):new Vec3d(Math.sqrt(.5),Math.sqrt(.5),0);
   com.terminaldetector.drmd.client.flight.ShipAttitudeClient.acceptPortalFrame(c.player,new Vec3d(0,0,-1),up);
   c.getServer().execute(()->{
    var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.PORTAL_PROJECTOR));
    DescentPlayerData.get(p).setShipAttitude(0,0,-1,(float)up.x,(float)up.y,(float)up.z);
    if(stage==2)for(var at:BlockPos.iterate(new BlockPos(-12,200,8),new BlockPos(28,240,8)))p.getServerWorld().setBlockState(at,net.minecraft.block.Blocks.WHITE_CONCRETE.getDefaultState(),2);
   });
  }
  if(stage==4)ClientPlayNetworking.send(new com.terminaldetector.drmd.network.ModNetworking.ActionPayload("drone_inventory"));
 }
 public static void rendered(){
  if(!ready||finished||age<30||!verified)return;
  if(stage<4 && MinecraftClient.getInstance().currentScreen!=null)return;if(++frames<90)return;var c=MinecraftClient.getInstance();
  try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
   var path=c.runDirectory.toPath().resolve("snapshot-frames");Files.createDirectories(path);image.writeTo(path.resolve(NAMES[stage]+".png"));
   if(stage==2||stage==3){
    int x0=image.getWidth(),y0=image.getHeight(),x1=0,y1=0,pixels=0;
    for(int y=image.getHeight()/3;y<image.getHeight()*9/10;y++)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x++){
     int color=image.getColor(x,y);if((color>>>16&255)>(color&255)*1.5 && (color>>>8&255)>(color&255)*1.3 && (color>>>16&255)>(color>>>8&255)*1.15 && (color>>>8&255)<180 && (color>>>16&255)<220){pixels++;x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}
    }
    require(pixels>300,"fixed portal tool is missing from centre of the actual hand frame");
    int[] box={x0,y0,x1,y1};System.out.println("SNAPSHOT_TOOL_BOUNDS "+NAMES[stage]+" "+java.util.Arrays.toString(box)+" pixels="+pixels);if(stage==2)levelBox=box;else for(int i=0;i<4;i++)require(Math.abs(levelBox[i]-box[i])<=2,"hull-mounted tool moves when world bank changes: "+java.util.Arrays.toString(levelBox)+" -> "+java.util.Arrays.toString(box));
   }
   RESULTS.add("{\"scene\":\""+NAMES[stage]+"\",\"frames\":"+frames+"}");
   System.out.println("SNAPSHOT_CLIENT "+NAMES[stage]+" PASS");
   if(++stage==NAMES.length){finished=true;c.options.attackKey.setPressed(false);c.options.useKey.setPressed(false);Files.writeString(c.runDirectory.toPath().resolve("snapshot-ok.json"),"{\"actualWeaponInput\":true,\"stableToolRoll\":true,\"actualDroneInventory\":true,\"scenes\":["+String.join(",",RESULTS)+"]}\n");c.scheduleStop();}
   age=frames=0;verified=false;
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
}
