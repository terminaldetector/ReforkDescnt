package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.joml.Vector4f;
import java.nio.file.*;
import java.util.*;

/** Real client keys power separate weapon/lever/drive blocks on an ordinary physical mechanism. */
public final class NativeMountedWeaponCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.mountedMechanisms");
    private static final BlockPos BASE=new BlockPos(8,2200,8),DRIVE=new BlockPos(-1,0,0),LASER=new BlockPos(1,0,0),ROCKET=new BlockPos(2,0,0);
    private static volatile BlockBodyEntity root,rotor;
    private static volatile ServerPlayerEntity player;
    private static volatile boolean located,ready,done,linked,moving,reversed,detached;
    private static volatile int phase,lasers,rockets,stoppedTicks;
    private static volatile double maxAngle,maxError,muzzleError,velocityError;
    private static long offTick=Long.MIN_VALUE,lastLaser=Long.MIN_VALUE,lastRocket=Long.MIN_VALUE;
    private static int shotsAtOff;
    private static boolean requested,spawnRequested,capture,rootDrawn,rotorDrawn;
    private static int ticks,age,frames,draws,pixelCount;
    private static String scene;
    private static final List<String> SCENES=new ArrayList<>();
    private static final Set<UUID> SHOTS=Collections.synchronizedSet(new HashSet<>());
    private static final List<double[]> PROJECTED=new ArrayList<>();
    private NativeMountedWeaponCheck(){}
    private static Entity e(Entity entity){return entity;}
    private static void require(boolean condition,String why){if(!condition){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("MOUNTED_NATIVE "+why);}}
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(NativeMountedWeaponCheck::tick);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!ready||done)return;
            require(CubicAvailability.backend(player.getServerWorld())==CubicAvailability.Backend.NATIVE_CUBIC,"native cubic backend");
            var links=BodyJointRegistry.get(player.getServerWorld()).forBody(e(root).getUuid());
            if(!links.isEmpty()){
                require(links.size()==1&&links.getFirst().blockDriven()&&links.getFirst().collideConnected(),"one material bearing with connected collisions");var link=links.getFirst();linked=true;
                maxError=Math.max(maxError,root.localToWorld(new Vec3d(1,.5,.5)).distanceTo(rotor.localToWorld(new Vec3d(0,.5,.5))));
                require(maxError<.03&&Math.abs(link.speed())<=.600001&&link.motorForce()<=40,"bounded physical motor");
                maxAngle=Math.max(maxAngle,2*Math.acos(Math.min(1,Math.abs(ImmPtlQuaternions.dot(root.rotation(),rotor.rotation())))));
                var axis=root.rotation().rotate(new Vec3(1,0,0));double relative=rotor.physics().angularVelocity().minus(root.physics().angularVelocity()).dot(axis);
                moving|=link.speed()<0&&relative<-.1&&maxAngle>.08;reversed|=phase>=7&&link.speed()>0&&relative>.1;
            }else if(linked&&phase>=8){detached=true;require(rotor.physics().angularVelocity().length()>.01,"detach preserves spin");}
            if(phase>=7&&!rotor.getLocalBlock(LASER.up()).get(Properties.POWERED)&&!rotor.getLocalBlock(ROCKET.up()).get(Properties.POWERED)){
                if(offTick==Long.MIN_VALUE){offTick=server.getTicks();shotsAtOff=lasers+rockets;}
                require(lasers+rockets==shotsAtOff,"off levers cannot keep emitting");stoppedTicks=(int)(server.getTicks()-offTick);
            }
        });
    }
    private static boolean loaded(MinecraftClient c){
        var access=WorldAccess.of(c.world);for(int x=-1;x<=1;x++)for(int y=(BASE.getY()-8)>>4;y<=(BASE.getY()+8)>>4;y++)for(int z=-1;z<=1;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;return true;
    }
    private static void spawn(){
        var world=player.getServerWorld();var at=Vec3d.of(BASE);var q=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),25);
        var cells=new ArrayList<BlockBodyEntity.Cell>();cells.add(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.EAST)));
        cells.add(new BlockBodyEntity.Cell(DRIVE,ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(Properties.FACING,Direction.EAST).with(Properties.INVERTED,true)));
        for(int y=-5;y<=-3;y++)for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
        cells.add(new BlockBodyEntity.Cell(new BlockPos(0,-1,0),Blocks.IRON_BLOCK.getDefaultState()));cells.add(new BlockBodyEntity.Cell(new BlockPos(0,-2,0),Blocks.IRON_BLOCK.getDefaultState()));
        root=BlockBodyEntity.createLocal(world,cells,at,q,Vec3d.ZERO,Vec3d.ZERO);var offset=q.rotate(new Vec3(1,0,0));
        var lever=Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR);
        rotor=BlockBodyEntity.createLocal(world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.WEST)),
            new BlockBodyEntity.Cell(LASER,ModWorldBlocks.SHIP_LASER.getDefaultState()),new BlockBodyEntity.Cell(ROCKET,ModWorldBlocks.SHIP_ROCKET.getDefaultState()),
            new BlockBodyEntity.Cell(LASER.up(),lever),new BlockBodyEntity.Cell(ROCKET.up(),lever)),at.add(offset.x(),offset.y(),offset.z()),q,Vec3d.ZERO,Vec3d.ZERO);
        root.setPhysicsGravity(false);rotor.setPhysicsGravity(false);var feet=root.localToWorld(new Vec3d(.5,-.6,-3.5));
        player.teleport(world,feet.x,feet.y,feet.z,Set.of(),0,0);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
        require(BodyJointRegistry.get(world).forBody(e(root).getUuid()).isEmpty(),"fixture supplies no ready-made joint");ready=true;
    }
    private static void tick(MinecraftClient c){
        if(done)return;require(++ticks<1800,"timeout phase="+phase+" age="+age+" shots="+lasers+"/"+rockets+" moving="+moving+" reversed="+reversed);
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!requested){
            require(c.runDirectory.getName().equals("cubic-client-automation"),"disposable runtime only");requested=true;c.setScreen(null);c.options.hudHidden=true;c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);c.inGameHud.getChatHud().clear(false);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;
            c.getServer().execute(()->{player=c.getServer().getPlayerManager().getPlayer(e(c.player).getUuid());FlightSystem.disable(player,DescentPlayerData.get(player));player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(player.getServerWorld(),BASE.getX()+.5,BASE.getY()+1,BASE.getZ()-4,Set.of(),0,0);player.getServerWorld().setTimeOfDay(6000);located=true;});return;
        }
        if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(NativeMountedWeaponCheck::spawn);}
        if(!ready||!(c.world.getEntityById(e(root).getId()) instanceof BlockBodyEntity a)||!(c.world.getEntityById(e(rotor).getId()) instanceof BlockBodyEntity b)||!c.player.getMainHandStack().isOf(ModWorldBlocks.MECHANISM_WRENCH))return;
        c.options.useKey.setPressed(false);c.options.sneakKey.setPressed(false);if(capture){aim(c,b,new Vec3(1.5,.5,.5));return;}age++;
        switch(phase){
            case 0->{aim(c,a,new Vec3(.5,.5,.5));c.options.useKey.setPressed(age==12);if(age>20){phase=1;age=0;}}
            case 1->{aim(c,b,new Vec3(.5,.5,.5));c.options.useKey.setPressed(age==12);if(linked&&age>15){phase=2;age=0;request("connected-off");}}
            case 2->{aim(c,a,new Vec3(-.5,.5,.5));c.options.useKey.setPressed(age==12);if(moving){phase=3;age=0;}}
            case 3->{aim(c,b,new Vec3(1.5,1.2,.5));c.options.useKey.setPressed(age==12);if(lasers>=3){phase=4;age=0;request("laser-fire");}}
            case 4->{aim(c,b,new Vec3(2.5,1.2,.5));c.options.useKey.setPressed(age==12);if(rockets>=1){phase=5;age=0;request("rocket-fire");}}
            case 5->{aim(c,b,new Vec3(1.5,1.2,.5));c.options.useKey.setPressed(age==12);if(age>15&&!rotor.getLocalBlock(LASER.up()).get(Properties.POWERED)){phase=6;age=0;}}
            case 6->{aim(c,b,new Vec3(2.5,1.2,.5));c.options.useKey.setPressed(age==12);if(age>15&&!rotor.getLocalBlock(ROCKET.up()).get(Properties.POWERED)){phase=7;age=0;}}
            case 7->{aim(c,a,new Vec3(-.5,.5,.5));c.options.sneakKey.setPressed(age>=8&&age<=14);c.options.useKey.setPressed(age==12);if(reversed&&stoppedTicks>=16){phase=8;age=0;request("fire-off-reversed");}}
            case 8->{aim(c,a,new Vec3(.5,.5,.5));c.options.sneakKey.setPressed(age>=8&&age<=14);c.options.useKey.setPressed(age==12);if(detached){phase=9;request("bearing-detached");}}
        }
    }
    private static void aim(MinecraftClient c,BlockBodyEntity body,Vec3 local){
        var view=BoardingClient.bodyView(body,1);var p=view==null?body.massPosition():view.position();var q=view==null?body.rotation():view.rotation();var eye=c.player.getEyePos();
        var look=p.plus(q.rotate(local.minus(body.localCentre()))).minus(new Vec3(eye.x,eye.y,eye.z));var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(look);c.player.setYaw((float)angles.yawDegrees());c.player.setPitch((float)angles.pitchDegrees());
    }
    public static void fired(WeaponCore.FireConfig cfg,ProjectileEntity shot){
        if(!ENABLED||!ready||done||shot==null||cfg.owner!=rotor)return;require(offTick==Long.MIN_VALUE,"no delayed emission after off");
        var slot=cfg.meshKind==1?ROCKET:LASER;var muzzle=MountedWeaponEmitter.muzzle(rotor,slot);muzzleError=Math.max(muzzleError,muzzle.distanceTo(cfg.pos));
        var axis=rotor.rotation().rotate(new Vec3(0,0,1));var expected=new Vec3d(axis.x(),axis.y(),axis.z()).multiply(cfg.speed).add(rotor.surfaceVelocityAt(cfg.pos).multiply(.05));velocityError=Math.max(velocityError,e(shot).getVelocity().distanceTo(expected));
        require(muzzleError<1e-8&&velocityError<1e-8&&shot.getOwnerEntity()==rotor&&shot.getOwnerLiving()==null,"actual physical barrel/point velocity/owner");
        long tick=player.getServer().getTicks();if(cfg.meshKind==1){require(lastRocket==Long.MIN_VALUE||tick-lastRocket>=24,"rocket cooldown");lastRocket=tick;rockets++;}else{require(lastLaser==Long.MIN_VALUE||tick-lastLaser>=8,"laser cooldown");lastLaser=tick;lasers++;}SHOTS.add(e(shot).getUuid());
    }
    private static void request(String name){scene=name;capture=true;frames=0;}
    public static void beforeFrame(){if(ENABLED){rootDrawn=false;rotorDrawn=false;PROJECTED.clear();}}
    public static void drew(BlockBodyEntity body){if(!ENABLED||!ready||done)return;if(e(body).getId()==e(root).getId())rootDrawn=true;if(e(body).getId()==e(rotor).getId())rotorDrawn=true;}
    public static void drewShot(ProjectileEntity shot,MatrixStack stack){
        if(!ENABLED||!ready||!SHOTS.contains(e(shot).getUuid()))return;
        var clip=new Vector4f(0,0,0,1).mul(stack.peek().getPositionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        if(clip.w>0&&Math.abs(clip.x/clip.w)<.9&&Math.abs(clip.y/clip.w)<.9){PROJECTED.add(new double[]{clip.x/clip.w,clip.y/clip.w,shot.getMeshKind()});draws++;}
    }
    public static void rendered(){
        if(!ENABLED||!capture||done||!rootDrawn||!rotorDrawn)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            int wanted=scene.equals("laser-fire")?0:scene.equals("rocket-fire")?1:-1;
            if(wanted>=0){int pixels=0;for(var p:PROJECTED){if((int)p[2]!=wanted)continue;int sx=(int)((p[0]+1)*.5*image.getWidth()),sy=(int)((1-p[1])*.5*image.getHeight());
                for(int y=Math.max(0,sy-12);y<Math.min(image.getHeight(),sy+13);y++)for(int x=Math.max(0,sx-12);x<Math.min(image.getWidth(),sx+13);x++){
                    int value=image.getColor(x,y),r=value&255,g=(value>>>8)&255,b=(value>>>16)&255;
                    if(wanted==0?g>130&&b>g*1.02&&r<g*.65:r>190&&g>70&&g<210&&b<g*.7)pixels++;
                }}if(pixels<3){frames=0;return;}pixelCount=Math.max(pixelCount,pixels);
            }
            if(++frames<3)return;var folder=c.runDirectory.toPath().resolve("mounted-mechanism-frames");Files.createDirectories(folder);image.writeTo(folder.resolve(scene+".png"));SCENES.add(scene);capture=false;
            if(phase==9){var result=new LinkedHashMap<String,Object>();result.put("actualUseAndSneakKeys",true);result.put("fixtureSuppliedJointOrShots",false);result.put("ordinaryPhysicalGunOwner",true);result.put("lasers",lasers);result.put("rockets",rockets);result.put("maxMuzzleError",muzzleError);result.put("maxLaunchVelocityError",velocityError);result.put("maxAnchorError",maxError);result.put("maxRelativeAngle",maxAngle);result.put("stoppedTicks",stoppedTicks);result.put("preservedSpinAfterRelease",detached);result.put("projectileDrawCalls",draws);result.put("projectedShotPixels",pixelCount);result.put("frames",SCENES);Files.writeString(c.runDirectory.toPath().resolve("mounted-mechanisms-ok.json"),new com.google.gson.Gson().toJson(result));done=true;c.scheduleStop();}
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
