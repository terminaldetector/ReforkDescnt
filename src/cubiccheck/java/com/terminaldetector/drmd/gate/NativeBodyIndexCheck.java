package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Real player on a distant wing: actual mouse mining/placement beyond the pivot's entity section.
 * One physical body, ordinary world blocks stay empty, and no teleport-follow. */
public final class NativeBodyIndexCheck {
 private static final boolean SCALE=Boolean.getBoolean("drmd.gate.bodyScale");
 private static final int EDGE=SCALE?127:23;
 private static final BlockPos DECK=new BlockPos(8,Integer.getInteger("drmd.boarding.labY",2156),16),WALL=new BlockPos(EDGE,1,2);
 private static final Vec3 VELOCITY=new Vec3(2,0,0);
 private static boolean started,prepared,finished,capture,inventoryRequested;
 private static volatile boolean ready,serverLegacyMiss,charged;
 private static volatile long baseRevision;
 private static volatile double travel,actualFeetError;
 private static volatile boolean journalStarted,journalFinished;
 private static int journalEdits;
 private static long journalRevision;
 private static int bodyId=-1,phase,age,ticks,stable,frames,samples,mineInputs,placeInputs;
 private static double error;private static boolean clientLegacyMiss,clientEdgePick,clientHole;
 private static String scene;

 private static final List<String> SCENES=new ArrayList<>();private static final Set<Integer> ACKS=new HashSet<>();
 private static Entity entity(BlockBodyEntity b){return b;}
 private static BlockBodyEntity bodyServer(MinecraftClient c){return (BlockBodyEntity)c.getServer().getOverworld().getEntityById(bodyId);}
 private static void require(boolean yes,String why){if(!yes){finished=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
 public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeBodyIndexCheck::tick);}
 public static void input(boolean place,boolean handled){if(!finished&&handled){if(place)placeInputs++;else mineInputs++;}}
 public static void received(BoardingNetworking.State s) {
  var c=MinecraftClient.getInstance();if(finished||!s.active()||c.world==null||s.body()==null)return;
  if(!(c.world.getEntityById(bodyId) instanceof BlockBodyEntity b)||b.shapeRevision()!=s.body().shapeRevision())return;
  var prediction=BoardingClient.predictedState(s.acknowledgment());
  if(prediction!=null&&ACKS.add(s.acknowledgment())){samples++;error=Math.max(error,prediction.feet().minus(new Vec3(s.x(),s.y(),s.z())).length());}
 }
 private static void tick(MinecraftClient c) {
  if(finished)return;if(++ticks>1600)require(false,"body index timeout phase="+phase+" age="+age+" input="+mineInputs+"/"+placeInputs+" state="+BoardingClient.state());
  if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var x:p.children())if(x instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
  if(c.player==null||c.world==null||c.getServer()==null)return;
  com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
  if(!started) {
   String name=c.runDirectory.toPath().getFileName().toString();require(name.equals("bodyindexcheck")||name.equals("bodyscalecheck")||name.equals("cubic-client-automation"),"disposable index runtime required");
   started=true;c.setScreen(null);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);c.inGameHud.getChatHud().clear(false);
   c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
   com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;
   c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().flying=true;p.getAbilities().allowFlying=true;p.sendAbilitiesUpdate();p.setInvulnerable(true);p.teleport(p.getServerWorld(),SCALE?DECK.getX()+EDGE:12,DECK.getY()+5,19,Set.of(),0,0);});return;
  }
  if(!prepared&&c.player.getY()>DECK.getY()-20&&loaded(c)){prepared=true;c.getServer().execute(()->setup(c));return;}
  if(!ready||capture)return;
  if(!(c.world.getEntityById(bodyId) instanceof BlockBodyEntity body))return;age++;
  if(phase==0) {
   aim(c,body,new Vec3(EDGE+.5,1.5,2.5));
   Vec3d from=c.player.getEyePos(),to=from.add(c.player.getRotationVec(1).multiply(5));var area=new Box(from,to).expand(1);
   if(!c.world.getEntitiesByClass(BlockBodyEntity.class,area,b->true).contains(body))clientLegacyMiss=true;
   var hit=ProjectileUtil.raycast(c.player,from,to,area,e->e.canHit(),25);
   clientEdgePick|=hit!=null&&hit.getEntity()==body;
   if(BoardingClient.drives(c.player)&&BoardingClient.state().grounded())stable++;else stable=0;
   if(stable>=10&&clientLegacyMiss&&serverLegacyMiss&&clientEdgePick){request("edge-intact");phase=1;age=0;}
  }else if(phase==1) {
   aim(c,body,new Vec3(EDGE+.5,1.5,2.5));if(age>10)c.options.attackKey.setPressed(true);
   if(body.shapeRevision()>baseRevision&&body.getLocalBlock(WALL).isAir()){c.options.attackKey.setPressed(false);request("edge-cut");phase=2;age=0;inventoryRequested=false;}
  }else if(phase==2) {
   if(!inventoryRequested){inventoryRequested=true;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.SURVIVAL);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Blocks.GOLD_BLOCK,3));});}
   aim(c,body,new Vec3(EDGE+.5,1.001,2.5));if(age>10&&c.player.getMainHandStack().isOf(Blocks.GOLD_BLOCK.asItem()))c.options.useKey.setPressed(true);
   if(body.shapeRevision()>baseRevision+1&&body.getLocalBlock(WALL).isOf(Blocks.GOLD_BLOCK)){c.options.useKey.setPressed(false);request("edge-patched");phase=3;age=0;inventoryRequested=false;}
  }else if(phase==3) {
   if(!inventoryRequested){inventoryRequested=true;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));});}
   aim(c,body,new Vec3(EDGE+.5,1.5,2.5));if(age>10&&c.player.getMainHandStack().isOf(Items.DIAMOND_PICKAXE))c.options.attackKey.setPressed(true);
   if(body.shapeRevision()>baseRevision+2&&body.getLocalBlock(WALL).isAir()) {
    c.options.attackKey.setPressed(false);var from=body.localToWorld(new Vec3d(EDGE+.5,1.5,-2));var to=body.localToWorld(new Vec3d(EDGE+.5,1.5,5));
    var hit=ProjectileUtil.raycast(c.player,from,to,new Box(from,to).expand(1),e->e==body,49);clientHole=hit==null;
    require(clientHole&&charged&&mineInputs>0&&placeInputs>0&&samples>=40&&error<.8&&travel>1,"far-wing input/revision/inventory/prediction proof incomplete: n="+samples+" err="+error+" charged="+charged+" travel="+travel+" hole="+clientHole+" input="+mineInputs+"/"+placeInputs);
    request("edge-hole");phase=SCALE?5:4;
   }
  }else if(phase==5) {
   if(!journalStarted){journalStarted=true;c.getServer().execute(()->journalRevision=bodyServer(c).shapeRevision());}
   if(journalFinished&&body.shapeRevision()==journalRevision) {
    require(body.cells().size()==32768&&body.getLocalBlock(new BlockPos(0,-15,0)).isOf(Blocks.GOLD_BLOCK),"rebased client lost full hull or final patch");
    require(samples>=40&&error<.8&&actualFeetError<1e-6,"large hull prediction/actual actor coherence after baseline rebase: "+actualFeetError);request("edge-rebased");phase=4;
   }
  }
 }
 private static void aim(MinecraftClient c,BlockBodyEntity b,Vec3 target) {
  var prediction=BoardingClient.bodyView(b,1);Vec3 p=prediction==null?b.massPosition():prediction.position();Quat q=prediction==null?b.rotation():prediction.rotation();
  var look=p.plus(q.rotate(target.minus(b.localCentre()))).minus(BoardingSystem.pure(c.player.getEyePos())).normalized();
  var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(look);c.player.setYaw((float)angles.yawDegrees());c.player.setPitch((float)angles.pitchDegrees());
 }
 private static boolean loaded(MinecraftClient c) {
  var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
  for(int x=SCALE?7:0;x<=(SCALE?9:2);x++)for(int y=(DECK.getY()>>4)-1;y<=(DECK.getY()>>4)+1;y++)for(int z=0;z<=2;z++)if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;return true;
 }
 private static void setup(MinecraftClient c) {
  var w=c.getServer().getOverworld();for(var p:BlockPos.iterate(DECK.add(-8,-10,-8),DECK.add(36,14,20)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
  if(!SCALE)for(var p:BlockPos.iterate(DECK,DECK.add(1,0,1)))w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);
  BlockBodyEntity body;
  if(SCALE) {
   var blueprint=new ArrayList<BlockBodyEntity.Cell>();
   for(int x=0;x<=EDGE;x++)for(int y=-15;y<=0;y++)for(int z=0;z<16;z++)
    if(!(x==0&&y==-15&&z==0))blueprint.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
   blueprint.add(new BlockBodyEntity.Cell(WALL,Blocks.STONE.getDefaultState()));
   body=BlockBodyEntity.createLocal(w,blueprint,Vec3d.of(DECK));
  }else {
   body=BlockBodyEntity.assemble(w,DECK,DECK.add(1,0,1));
   for(int x=2;x<=23;x++)for(int z=0;z<=3;z++)require(body.setLocalBlock(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()),"wing assembly rejected");
   require(body.setLocalBlock(WALL,Blocks.STONE.getDefaultState()),"wing wall rejected");
  }
  baseRevision=body.shapeRevision();
  body.physics().withLinearVelocity(VELOCITY);body.setPassengerGravity(new Vec3(0,-32,0));entity(body).tick();bodyId=entity(body).getId();
  var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=false;p.getAbilities().allowFlying=false;p.sendAbilitiesUpdate();p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
  var feet=body.localToWorld(new Vec3d(EDGE-.7,2,1.5));p.teleport(w,feet.x,feet.y,feet.z,Set.of(),0,0);w.setTimeOfDay(6000);ready=true;Vec3 launch=body.massPosition();
  ServerTickEvents.START_WORLD_TICK.register(world->{if(world!=w||finished)return;
   Vec3 dv=VELOCITY.minus(body.physics().linearVelocity()).scaled(.3);if(dv.length()>.25)dv=dv.scaled(.25/dv.length());body.applyPhysicsImpulse(BoardingSystem.mc(body.massPosition()),BoardingSystem.mc(dv.scaled(body.physicsMass())));
   var q=body.rotation();Vec3 axis=new Vec3(q.x(),q.y(),q.z());Vec3 acceleration=axis.normalized().scaled(-72*Math.acos(Math.min(1,Math.abs(q.w())))).minus(body.physics().angularVelocity().scaled(12));
   Vec3 torque=body.physics().worldInertia().transform(acceleration);double cap=body.physicsMass()*16;if(torque.length()>cap)torque=torque.scaled(cap/torque.length());body.applyPhysicsTorque(BoardingSystem.mc(torque));
  });
  ServerTickEvents.END_WORLD_TICK.register(world->{if(world!=w||finished)return;
   Vec3d from=p.getEyePos(),to=from.add(p.getRotationVec(1).multiply(5));var area=new Box(from,to).expand(1);
   travel=body.massPosition().minus(launch).length();
   var motor=BoardingSystem.state(p);
   if(motor!=null)actualFeetError=Math.max(actualFeetError,p.getPos().distanceTo(BoardingSystem.mc(motor.feet())));
   if(SCALE&&journalStarted&&!journalFinished) {
    var marker=new BlockPos(0,-15,0);
    for(int i=0;i<2&&journalEdits<300;i++) {
     var state=journalEdits%2==0?Blocks.DIAMOND_BLOCK.getDefaultState():Blocks.GOLD_BLOCK.getDefaultState();
     require(body.setLocalBlock(marker,state),"large hull journal mutation rejected");journalEdits++;
    }
    journalRevision=body.shapeRevision();if(journalEdits==300)journalFinished=true;
   }
   serverLegacyMiss|=!w.getEntitiesByClass(BlockBodyEntity.class,area,b->true).contains(body)&&BodySpatialIndex.query(w,area).contains(body);
   if(p.getMainHandStack().isOf(Blocks.GOLD_BLOCK.asItem())&&p.getMainHandStack().getCount()==2)charged=true;
   if(w.getTime()%40==0)System.out.println("BODY_INDEX_TRACE phase="+phase+" age="+age+" legacy="+clientLegacyMiss+"/"+serverLegacyMiss+" picked="+clientEdgePick+" rev="+body.shapeRevision()+" input="+mineInputs+"/"+placeInputs+" actualFeetError="+actualFeetError+" paused="+body.boardingMotionPaused()+" velocity="+body.physics().linearVelocity()+" travel="+travel+" state="+BoardingSystem.state(p));
  });
 }
 private static void request(String name){scene=name;capture=true;frames=0;}
 public static void rendered(){if(!capture||finished)return;var c=MinecraftClient.getInstance();if(c.player==null||c.currentScreen!=null||++frames<3)return;
  try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
   var dir=c.runDirectory.toPath().resolve(SCALE?"body-scale-frames":"body-index-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(scene+".png"));SCENES.add("{\"scene\":\""+scene+"\",\"frames\":"+frames+"}");capture=false;System.out.println("BODY_INDEX_CLIENT "+scene+" PASS");
   if(phase==4){Files.writeString(c.runDirectory.toPath().resolve(SCALE?"body-scale-ok.json":"body-index-ok.json"),"{\"actualFeetError\":"+actualFeetError+",\"bodyBlocks\":"+(SCALE?32768:93)+",\"localEdge\":"+(EDGE+1)+",\"journalEdits\":"+journalEdits+",\"actualInputs\":true,\"legacyMissClient\":"+clientLegacyMiss+",\"legacyMissServer\":"+serverLegacyMiss+",\"edgePicked\":"+clientEdgePick+",\"holeClear\":"+clientHole+",\"materialCharged\":"+charged+",\"revisionChanges\":3,\"travelDistance\":"+travel+",\"teleportFollow\":false,\"predictionSamples\":"+samples+",\"maxPredictionError\":"+error+",\"scenes\":["+String.join(",",SCENES)+"]}\n");finished=true;c.scheduleStop();}
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
}
