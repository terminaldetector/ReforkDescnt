package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Disposable native client: actual use/sneak keys and local-block packets build/control a joint.
 * The fixture provides two free ordinary bodies at rest in zero gravity, no UFO or joint setter. */
public final class NativeMechanismCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.mechanisms");
    private static final BlockPos BASE=new BlockPos(8,2156,8),DRIVE=new BlockPos(-1,0,0);
    private static volatile BlockBodyEntity root,rotor;
    private static volatile ServerPlayerEntity player;
    private static volatile boolean located,ready,done,linked,positive,reversed,detached;
    private static volatile double maxAngle,maxError;
    private static boolean requested,spawnRequested,capture,rootDrawn,rotorDrawn;
    private static volatile int phase;
    private static int ticks,age,frames;
    private static String scene;
    private static final List<String> SCENES=new ArrayList<>();
    private NativeMechanismCheck(){}
    private static void require(boolean condition,String why){if(!condition){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("MECHANISM_NATIVE "+why);}}
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(NativeMechanismCheck::tick);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!ready||done)return;
            require(CubicAvailability.backend(player.getServerWorld())==CubicAvailability.Backend.NATIVE_CUBIC,"native cubic backend required");
            var links=BodyJointRegistry.get(player.getServerWorld()).forBody(root.getUuid());
            if(!links.isEmpty()){
                require(links.size()==1&&links.getFirst().blockDriven(),"one actual block-driven joint");var link=links.getFirst();linked=true;
                maxError=Math.max(maxError,root.localToWorld(new Vec3d(1,.5,.5)).distanceTo(rotor.localToWorld(new Vec3d(0,.5,.5))));
                require(maxError<.03&&Math.abs(link.speed())<=.600001&&link.motorForce()<=40,"bounded physical bearing");
                maxAngle=Math.max(maxAngle,2*Math.acos(Math.min(1,Math.abs(ImmPtlQuaternions.dot(root.rotation(),rotor.rotation())))));
                var axis=root.rotation().rotate(new Vec3(1,0,0));double relative=rotor.physics().angularVelocity().minus(root.physics().angularVelocity()).dot(axis);
                positive|=link.speed()>0&&relative>.1&&maxAngle>.08;
                reversed|=link.speed()<0&&relative<-.1;
            }else if(linked&&phase>=4){detached=true;require(rotor.physics().angularVelocity().length()>.01,"detach preserves actual spin");}
        });
    }
    private static boolean loaded(MinecraftClient c){
        var access=WorldAccess.of(c.world);
        for(int x=-1;x<=1;x++)for(int y=(BASE.getY()-8)>>4;y<=(BASE.getY()+8)>>4;y++)for(int z=-1;z<=1;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void spawn(){
        var world=player.getServerWorld();var at=Vec3d.of(BASE);var q=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),25);
        root=BlockBodyEntity.createLocal(world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.EAST)),new BlockBodyEntity.Cell(DRIVE,ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.EAST))),at,q,Vec3d.ZERO,Vec3d.ZERO);
        var offset=q.rotate(new Vec3(1,0,0));
        rotor=BlockBodyEntity.createLocal(world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.WEST)),new BlockBodyEntity.Cell(new BlockPos(1,0,0),Blocks.IRON_BLOCK.getDefaultState())),at.add(offset.x(),offset.y(),offset.z()),q,Vec3d.ZERO,Vec3d.ZERO);
        root.setPhysicsGravity(false);rotor.setPhysicsGravity(false);
        var feet=root.localToWorld(new Vec3d(.5,-.6,3.5));player.teleport(world,feet.x,feet.y,feet.z,Set.of(),180,0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
        require(BodyJointRegistry.get(world).forBody(root.getUuid()).isEmpty(),"fixture cannot supply a ready-made joint");ready=true;
    }
    private static void tick(MinecraftClient c){
        if(done)return;require(++ticks<1600,"timeout phase="+phase+" age="+age+" linked="+linked+" positive="+positive+" reversed="+reversed+" detached="+detached);
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!requested){
            require(c.runDirectory.getName().equals("cubic-client-automation"),"disposable runtime only");requested=true;c.setScreen(null);c.options.hudHidden=true;c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            c.inGameHud.getChatHud().clear(false);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;
            c.getServer().execute(()->{player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());FlightSystem.disable(player,DescentPlayerData.get(player));player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(player.getServerWorld(),BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+4,Set.of(),180,0);player.getServerWorld().setTimeOfDay(6000);located=true;});return;
        }
        if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(NativeMechanismCheck::spawn);}
        if(!ready||!(c.world.getEntityById(root.getId()) instanceof BlockBodyEntity a)||!(c.world.getEntityById(rotor.getId()) instanceof BlockBodyEntity b)||!c.player.getMainHandStack().isOf(ModWorldBlocks.MECHANISM_WRENCH))return;
        c.options.useKey.setPressed(false);c.options.sneakKey.setPressed(false);if(capture){aim(c,a,new Vec3(.5,.5,.5));return;}age++;
        switch(phase){
            case 0->{aim(c,a,new Vec3(.5,.5,.5));c.options.useKey.setPressed(age==12);if(age>20){phase=1;age=0;}}
            case 1->{aim(c,b,new Vec3(.5,.5,.5));c.options.useKey.setPressed(age==12);if(linked&&age>15){phase=2;age=0;request("connected-idle");}}
            case 2->{aim(c,a,new Vec3(-.5,.5,.5));c.options.useKey.setPressed(age==12);if(positive){phase=3;age=0;request("drive-powered");}}
            case 3->{aim(c,a,new Vec3(-.5,.5,.5));c.options.sneakKey.setPressed(age>=8&&age<=14);c.options.useKey.setPressed(age==12);if(reversed){phase=4;age=0;request("drive-reversed");}}
            case 4->{aim(c,a,new Vec3(.5,.5,.5));c.options.sneakKey.setPressed(age>=8&&age<=14);c.options.useKey.setPressed(age==12);if(detached){phase=5;request("bearing-detached");}}
        }
    }
    private static void aim(MinecraftClient c,BlockBodyEntity body,Vec3 local){
        var view=BoardingClient.bodyView(body,1);var p=view==null?body.massPosition():view.position();var q=view==null?body.rotation():view.rotation();
        var eye=c.player.getEyePos();var look=p.plus(q.rotate(local.minus(body.localCentre()))).minus(new Vec3(eye.x,eye.y,eye.z));
        var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(look);c.player.setYaw((float)angles.yawDegrees());c.player.setPitch((float)angles.pitchDegrees());
    }
    private static void request(String name){scene=name;capture=true;frames=0;}
    public static void beforeFrame(){if(ENABLED){rootDrawn=false;rotorDrawn=false;}}
    public static void drew(BlockBodyEntity body){if(!ENABLED||!ready||done)return;if(body.getId()==root.getId())rootDrawn=true;if(body.getId()==rotor.getId())rotorDrawn=true;}
    public static void rendered(){
        if(!ENABLED||!capture||done||!rootDrawn||!rotorDrawn)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            var folder=c.runDirectory.toPath().resolve("mechanism-frames");Files.createDirectories(folder);image.writeTo(folder.resolve(scene+".png"));SCENES.add(scene);capture=false;
            if(phase==5){
                var result=new LinkedHashMap<String,Object>();result.put("actualClientUseAndSneakKeys",true);result.put("fixtureCreatedJoint",false);result.put("ordinaryBlockBodies",true);result.put("worldAssemblyCoveredByGameTestsOnly",true);
                result.put("physicalMotorPositiveAndReverse",positive&&reversed);result.put("detachPreservesSpin",detached);result.put("maxRelativeAngle",maxAngle);result.put("maxAnchorError",maxError);result.put("freshDrawsOfBothBodies",true);result.put("frames",SCENES);
                Files.writeString(c.runDirectory.toPath().resolve("mechanisms-ok.json"),new com.google.gson.Gson().toJson(result));done=true;c.scheduleStop();
            }
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
