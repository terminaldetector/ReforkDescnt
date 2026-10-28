package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Actual Minecraft mouse handlers, local edits and authoritative inventory on a moving tilted deck. */
public final class NativeBodyEditCheck {
 private static final BlockPos DECK=new BlockPos(8,Integer.getInteger("drmd.boarding.labY",2156),16),WALL=new BlockPos(4,1,6);
 private static final Vec3 UP=new Vec3(0,1,0),VELOCITY=new Vec3(2,.3,.2);
 private static boolean started,prepared,finished,capture,inventoryRequested,saveRequested;
 private static volatile boolean ready,materialCharged,saved,rebased;
 private static volatile int dropStacks,toolDamage;
 private static volatile double pivotError;
 private static int bodyId=-1,phase,age,ticks,stable,frames,samples,mineInputs,placeInputs;
 private static double error;private static String scene;
 private static final List<String> SCENES=new ArrayList<>();private static final Set<Integer> ACKS=new HashSet<>();
 private static Entity entity(BlockBodyEntity b){return b;}
 private static void require(boolean yes,String why){if(!yes){finished=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
 public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeBodyEditCheck::tick);}
 public static void input(boolean place,boolean handled){if(!finished&&handled){if(place)placeInputs++;else mineInputs++;}}
 public static void received(BoardingNetworking.State s){
  var c=MinecraftClient.getInstance();if(finished||!s.active()||c.world==null||s.body()==null)return;
  if(!(c.world.getEntityById(bodyId) instanceof BlockBodyEntity b)||b.shapeRevision()!=s.body().shapeRevision())return;
  var predicted=BoardingClient.predictedState(s.acknowledgment());
  if(predicted!=null&&ACKS.add(s.acknowledgment())){samples++;error=Math.max(error,predicted.feet().minus(new Vec3(s.x(),s.y(),s.z())).length());}
 }
 private static void tick(MinecraftClient c) {
  if(finished)return;if(++ticks>1600)require(false,"body edit timeout phase="+phase+" age="+age+" inputs="+mineInputs+"/"+placeInputs+" state="+BoardingClient.state());
  if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var x:p.children())if(x instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
  if(c.player==null||c.world==null||c.getServer()==null)return;
  com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
  if(!started) {
   var name=c.runDirectory.toPath().getFileName().toString();require(name.equals("bodyeditcheck")||name.equals("cubic-client-automation"),"disposable edit runtime required");
   started=true;c.setScreen(null);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);c.inGameHud.getChatHud().clear(false);
   c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
   com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;
   c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().flying=true;p.getAbilities().allowFlying=true;p.sendAbilitiesUpdate();p.setInvulnerable(true);p.teleport(p.getServerWorld(),12,DECK.getY()+5,19,Set.of(),0,0);});return;
  }
  if(!prepared&&c.player.getY()>DECK.getY()-20&&loaded(c)){prepared=true;c.getServer().execute(()->setup(c));return;}
  if(!ready||capture)return;
  if(!(c.world.getEntityById(bodyId) instanceof BlockBodyEntity body))return;age++;
  if(phase==0) {
   if(BoardingClient.drives(c.player)&&BoardingClient.state().grounded())stable++;else stable=0;
   if(stable>=10){request("hull-intact");phase=1;age=0;}
  }else if(phase==1) {
   aim(c,body,new Vec3(4.5,1.5,6.5));if(age>10)c.options.attackKey.setPressed(true);
   if(body.shapeRevision()>=1&&body.getLocalBlock(WALL).isAir()){c.options.attackKey.setPressed(false);request("hull-cut");phase=2;age=0;inventoryRequested=false;}
  }else if(phase==2) {
   if(!inventoryRequested){inventoryRequested=true;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.SURVIVAL);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Blocks.GOLD_BLOCK,3));});}
   aim(c,body,new Vec3(4.5,1.001,6.5));if(age>10&&c.player.getMainHandStack().isOf(Blocks.GOLD_BLOCK.asItem()))c.options.useKey.setPressed(true);
   if(body.shapeRevision()>=2&&body.getLocalBlock(WALL).isOf(Blocks.GOLD_BLOCK)){c.options.useKey.setPressed(false);request("hull-patched");phase=3;age=0;inventoryRequested=false;}
  }else if(phase==3) {
   if(!inventoryRequested){inventoryRequested=true;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));});}
   aim(c,body,new Vec3(4.5,1.5,6.5));if(age>10&&c.player.getMainHandStack().isOf(Items.DIAMOND_PICKAXE))c.options.attackKey.setPressed(true);
   if(body.shapeRevision()>=3&&body.getLocalBlock(WALL).isAir()){c.options.attackKey.setPressed(false);request("survival-cut");phase=4;age=0;}
  }else if(phase==4) {
   if(!saveRequested){saveRequested=true;c.getServer().execute(()->{
    var original=(BlockBodyEntity)c.getServer().getOverworld().getEntityById(bodyId);var copy=ModEntities.BLOCK_BODY.create(entity(original).getWorld());
    try{entity(copy).readNbt(entity(original).writeNbt(new net.minecraft.nbt.NbtCompound()));
     require(copy.localPivot().equals(original.localPivot())&&copy.shapeRevision()==3,"saved local pivot/revision changed");
     for(var cell:original.cells()){var p=Vec3d.ofCenter(cell.pos());require(copy.localToWorld(p).distanceTo(original.localToWorld(p))<1e-7,"restored material moved");}
     require(copy.physics().linearVelocity().minus(original.physics().linearVelocity()).length()<1e-8,"restored momentum changed");saved=true;
    }finally{entity(copy).discard();}
   });}
   if(saved&&age>10){require(materialCharged&&toolDamage==1&&dropStacks==1&&rebased,"authoritative inventory/loot/COM incomplete: "+materialCharged+"/"+toolDamage+"/"+dropStacks+"/"+rebased);
    require(mineInputs>0&&placeInputs>0&&samples>=40&&error<.8&&pivotError<.8,"actual input/prediction/COM proof incomplete: n="+samples+" error="+error+" pivot="+pivotError);request("saved-state");phase=5;}
  }
 }
 private static void aim(MinecraftClient c,BlockBodyEntity b,Vec3 target) {
  var predicted=BoardingClient.bodyView(b,1);Vec3 p=predicted==null?b.massPosition():predicted.position();Quat q=predicted==null?b.rotation():predicted.rotation();
  var look=p.plus(q.rotate(target.minus(b.localCentre()))).minus(BoardingSystem.pure(c.player.getEyePos())).normalized();
  var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(look);c.player.setYaw((float)angles.yawDegrees());c.player.setPitch((float)angles.pitchDegrees());
 }
 private static boolean loaded(MinecraftClient c) {
  var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
  for(int x=0;x<=1;x++)for(int y=(DECK.getY()>>4)-1;y<=(DECK.getY()>>4)+1;y++)for(int z=0;z<=2;z++)if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;return true;
 }
 private static void setup(MinecraftClient c) {
  var w=c.getServer().getOverworld();for(var p:BlockPos.iterate(DECK.add(-8,-10,-8),DECK.add(18,14,18)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
  for(var p:BlockPos.iterate(DECK,DECK.add(7,0,7)))w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);w.setBlockState(DECK.add(WALL),Blocks.STONE.getDefaultState(),2);
  var body=BlockBodyEntity.assemble(w,DECK,DECK.add(7,1,7));
  body.physics().withRotation(target(0)).withLinearVelocity(VELOCITY);body.setPassengerGravity(new Vec3(0,-32,0));entity(body).tick();bodyId=entity(body).getId();
  var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=false;p.getAbilities().allowFlying=false;p.sendAbilitiesUpdate();p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
  Vec3d feet=body.localToWorld(new Vec3d(4,2,3));p.teleport(w,feet.x,feet.y,feet.z,Set.of(),0,0);w.setTimeOfDay(6000);ready=true;long start=w.getTime();
  Vec3d[] before={entity(body).getPos(),body.surfaceVelocityAt(entity(body).getPos())};long[] revision={0};
  ServerTickEvents.START_WORLD_TICK.register(world->{if(world!=w||finished)return;
   before[0]=entity(body).getPos();before[1]=body.surfaceVelocityAt(before[0]);revision[0]=body.shapeRevision();
   Vec3 dv=VELOCITY.minus(body.physics().linearVelocity()).scaled(.3);if(dv.length()>.25)dv=dv.scaled(.25/dv.length());body.applyPhysicsImpulse(BoardingSystem.mc(body.massPosition()),BoardingSystem.mc(dv.scaled(body.physicsMass())));
   Quat error=ImmPtlQuaternions.hamiltonProduct(target((w.getTime()-start)*.005),body.rotation().inverse()).normalized();if(error.w()<0)error=new Quat(-error.x(),-error.y(),-error.z(),-error.w());
   Vec3 axis=new Vec3(error.x(),error.y(),error.z());Vec3 acceleration=axis.normalized().scaled(36*2*Math.acos(Math.min(1,error.w()))).minus(body.physics().angularVelocity().minus(UP.scaled(.1)).scaled(12));
   Vec3 torque=body.physics().worldInertia().transform(acceleration);double cap=body.physicsMass()*16;if(torque.length()>cap)torque=torque.scaled(cap/torque.length());body.applyPhysicsTorque(BoardingSystem.mc(torque));
  });
  ServerTickEvents.END_WORLD_TICK.register(world->{if(world!=w||finished)return;
   if(body.shapeRevision()!=revision[0]){rebased=true;pivotError=Math.max(pivotError,entity(body).getPos().subtract(before[0]).subtract(before[1].multiply(.05)).length());}
   if(p.getMainHandStack().isOf(Blocks.GOLD_BLOCK.asItem())&&p.getMainHandStack().getCount()==2)materialCharged=true;
   if(p.getMainHandStack().isOf(Items.DIAMOND_PICKAXE))toolDamage=p.getMainHandStack().getDamage();
   var drops=w.getEntitiesByClass(ItemEntity.class,entity(body).getBoundingBox().expand(16),e->e.getStack().isOf(Blocks.GOLD_BLOCK.asItem()));dropStacks=Math.max(dropStacks,drops.size());
   if(w.getTime()%40==0)System.out.println("BODY_EDIT_TRACE phase="+phase+" age="+age+" rev="+body.shapeRevision()+" wall="+body.getLocalBlock(WALL)+" mining="+mineInputs+"/"+placeInputs+" pose="+BoardingSystem.state(p));
  });
 }
 private static Quat target(double yaw){return ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByRadians(UP,yaw),ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByRadians(new Vec3(1,0,0),.2),ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),.2)));}
 private static void request(String name){scene=name;capture=true;frames=0;}
 public static void rendered(){if(!capture||finished)return;var c=MinecraftClient.getInstance();if(c.player==null||c.currentScreen!=null||++frames<3)return;
  try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
   var dir=c.runDirectory.toPath().resolve("body-edit-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(scene+".png"));SCENES.add("{\"scene\":\""+scene+"\",\"frames\":"+frames+"}");capture=false;System.out.println("BODY_EDIT_CLIENT "+scene+" PASS");
   if(phase==5){Files.writeString(c.runDirectory.toPath().resolve("body-edit-ok.json"),"{\"actualInputs\":true,\"survivalMining\":true,\"materialCharged\":"+materialCharged+",\"savedState\":"+saved+",\"comRebased\":"+rebased+",\"revisions\":3,\"predictionSamples\":"+samples+",\"maxPredictionError\":"+error+",\"maxPivotStep\":"+pivotError+",\"dropStacks\":"+dropStacks+",\"toolDamage\":"+toolDamage+",\"scenes\":["+String.join(",",SCENES)+"]}\n");finished=true;c.scheduleStop();}
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
}
