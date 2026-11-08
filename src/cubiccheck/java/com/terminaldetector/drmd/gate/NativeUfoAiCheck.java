package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.joml.Vector4f;
import java.nio.file.*;
import java.util.*;

/** Disposable real-client autonomous fire/physical aim/inertia proof; no scripted shots or targets. */
public final class NativeUfoAiCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ufoCombat");
    private static final BlockPos BASE=new BlockPos(8,2156,8);
    private static volatile SkyUfoEntity body;private static volatile ServerPlayerEntity player;
    private static volatile boolean requested,located,spawnRequested,running,done,rootDrawn;private static volatile int phase,ticks,age,frames,lasers,rockets,hits,paused,shotsAtPause,shotsAtResume,renderedShots;private static int initialAge;
    private static volatile double initialDot,bestDot,muzzleError,velocityError,damageBefore,rootX,rootY,pauseTravel;private static Vec3 pauseAt;private static Quat initial;private static int shotPixels;
    private static final Set<UUID> SHOTS=Collections.synchronizedSet(new HashSet<>());private static final List<double[]> PROJECTED=new ArrayList<>();private static final List<String> SCENES=new ArrayList<>();
    private static net.minecraft.entity.Entity e(net.minecraft.entity.Entity e){return e;}
    private static Path file(String n){return MinecraftClient.getInstance().runDirectory.toPath().resolve(n);}
    private static void require(boolean ok,String message){if(!ok){MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("UFO autonomous: "+message);}}
    public static void register(){
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable native fixture required");
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!running||done||e(body).age<=initialAge)return;require(++age<650,"combat timeout phase="+phase+" shots="+lasers+"/"+rockets+" hits="+hits+" target="+body.autonomousTarget());
            require(player.isAlive()&&!UfoControlSystem.controls(player)&&e(body).isAlive(),"live Survival target and unpiloted real hull");
            require(CubicAvailability.backend(s.getOverworld())==CubicAvailability.Backend.NATIVE_CUBIC,"real cubic backend");
            require(!BodyContactSystem.stats(s.getOverworld()).budgetPaused(),"no exhausted contact phase");
            var forward=body.rotation().rotate(new Vec3(0,0,1));var goal=player.getBoundingBox().getCenter().subtract(body.physicsCentre()).normalize();bestDot=Math.max(bestDot,forward.x()*goal.x+forward.y()*goal.y+forward.z()*goal.z);
            if(age==1)require(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(initial,body.rotation()))>.999,"first engine tick cannot snap orientation");
            if(phase==0)require(body.autonomousTarget().orElse(null)==player,"normal AI acquires actual Survival player without a target setter");
            if(phase==2){paused++;require(lasers+rockets==shotsAtPause,"disabled autopilot cannot emit a stale shot");pauseTravel=Math.max(pauseTravel,body.massPosition().minus(pauseAt).length());}
            if(age%40==0)System.out.println("UFO_AI phase="+phase+" age="+age+" shots="+lasers+"/"+rockets+" hits="+hits+" aim="+bestDot+" distance="+body.physicsCentre().distanceTo(player.getPos()));
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<4800,"client timeout");com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;
            if(!requested){requested=true;c.setScreen(null);c.options.hudHidden=true;c.getToastManager().clear();c.inGameHud.getChatHud().clear(false);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
                c.getServer().execute(()->{player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());player.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(player,DescentPlayerData.get(player));player.getAbilities().flying=true;player.setNoGravity(true);player.sendAbilitiesUpdate();player.teleport(player.getServerWorld(),BASE.getX()+.5,BASE.getY()+2,BASE.getZ()-20,Set.of(),180,0);player.getServerWorld().setTimeOfDay(6000);located=true;});return;
            }
            if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(NativeUfoAiCheck::spawn);}
            if(running){c.player.setNoGravity(false);c.player.getAbilities().flying=false;}
        });
    }
    private static boolean loaded(MinecraftClient c){var a=WorldAccess.of(c.world);for(int x=-2;x<=2;x++)for(int y=(BASE.getY()-24)>>4;y<=(BASE.getY()+24)>>4;y++)for(int z=-4;z<=2;z++)if(!a.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;return true;}
    private static void spawn(){
        var w=player.getServerWorld();for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)w.setBlockState(BASE.add(x,-1,z),net.minecraft.block.Blocks.BEDROCK.getDefaultState(),3);
        player.teleport(w,BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5,Set.of(),180,0);player.changeGameMode(GameMode.SURVIVAL);player.getAbilities().flying=false;player.getAbilities().allowFlying=false;player.setNoGravity(false);player.sendAbilitiesUpdate();player.setVelocity(Vec3d.ZERO);
        player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);player.setHealth(200);player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        var data=DescentPlayerData.get(player);data.setShieldMax(400);data.setShield(400);data.setEnabled(false);
        body=ModEntities.SKY_UFO.create(w);body.setAutopilot(false);e(body).refreshPositionAndAngles(BASE.getX(),BASE.getY()-2,BASE.getZ()-42,0,0);require(w.spawnEntity(body),"spawn actual registered hull");e(body).tick();initialAge=e(body).age;
        initial=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.hamiltonProduct(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByDegrees(new Vec3(0,1,0),40),com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),25));
        require(body.resolveContactPose(body.massPosition(),initial),"initial fixture orientation must clear actual world geometry");body.publishPhysicsState();var f=initial.rotate(new Vec3(0,0,1));var to=player.getBoundingBox().getCenter().subtract(body.physicsCentre()).normalize();initialDot=f.x()*to.x+f.y()*to.y+f.z()*to.z;bestDot=initialDot;body.setAutopilot(true);running=true;
    }
    public static void fired(WeaponCore.FireConfig cfg,ProjectileEntity shot){
        if(!ENABLED||!running||shot==null||cfg.owner!=body)return;require(phase!=2,"no fire while disabled");
        var slots=cfg.meshKind==1?SkyUfoWeapons.ROCKETS:SkyUfoWeapons.LASERS;double error=slots.stream().mapToDouble(p->SkyUfoWeapons.muzzle(body,p).distanceTo(cfg.pos)).min().orElseThrow();muzzleError=Math.max(muzzleError,error);
        var dir=body.rotation().rotate(new Vec3(0,0,1));var expected=new Vec3d(dir.x(),dir.y(),dir.z()).multiply(cfg.speed).add(body.surfaceVelocityAt(cfg.pos).multiply(.05));velocityError=Math.max(velocityError,e(shot).getVelocity().distanceTo(expected));require(muzzleError<1e-8&&velocityError<1e-8,"actual barrel quaternion and material-point launch velocity");
        require(shot.getOwnerEntity()==body&&shot.getOwnerLiving()==null,"real nonliving physical owner, no fake pilot");var n=e(shot).writeNbt(new net.minecraft.nbt.NbtCompound());require(n.containsUuid("owner")&&n.getUuid("owner").equals(e(body).getUuid()),"actual persisted owner UUID");SHOTS.add(e(shot).getUuid());if(cfg.meshKind==1)rockets++;else lasers++;
    }
    public static void beforeDamage(net.minecraft.entity.Entity source,net.minecraft.entity.Entity target){if(ENABLED&&running&&source==body&&target==player)damageBefore=player.getHealth()+DescentPlayerData.get(player).getShield();}
    public static void afterDamage(net.minecraft.entity.Entity source,net.minecraft.entity.Entity target){if(ENABLED&&running&&source==body&&target==player&&player.getHealth()+DescentPlayerData.get(player).getShield()<damageBefore)hits++;}
    public static void beforeFrame(float delta){if(!ENABLED)return;PROJECTED.clear();rootDrawn=false;var c=MinecraftClient.getInstance();if(!running||c.player==null||c.world==null||done)return;var b=c.world.getEntityById(e(body).getId());if(!(b instanceof BlockBodyEntity hull))return;var centre=hull.renderMassPosition(delta);var from=e(c.player).getCameraPosVec(delta);var a=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(centre.x()-from.x,centre.y()-from.y,centre.z()-from.z));c.player.setYaw((float)a.yawDegrees());c.player.prevYaw=c.player.getYaw();c.player.setPitch((float)a.pitchDegrees());c.player.prevPitch=c.player.getPitch();}
    private static Vector4f project(MatrixStack stack,Vec3 local){return new Vector4f((float)local.x(),(float)local.y(),(float)local.z(),1).mul(stack.peek().getPositionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());}
    public static void drewRoot(BlockBodyEntity b,MatrixStack stack){if(!ENABLED||!running||body==null||!e(b).getUuid().equals(e(body).getUuid()))return;var clip=project(stack,b.localCentre().minus(b.localPivot()));if(clip.w<=0)return;rootX=clip.x/clip.w;rootY=clip.y/clip.w;rootDrawn=Math.abs(rootX)<.2&&Math.abs(rootY)<.2;}
    public static void drewShot(ProjectileEntity shot,MatrixStack stack){if(!ENABLED||!running||!SHOTS.contains(e(shot).getUuid()))return;var clip=project(stack,new Vec3(0,0,0));if(clip.w>0&&Math.abs(clip.x/clip.w)<.8&&Math.abs(clip.y/clip.w)<.8){PROJECTED.add(new double[]{clip.x/clip.w,clip.y/clip.w});renderedShots++;}}
    public static void rendered(){
        if(!ENABLED||!running||done||!rootDrawn)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null)return;
        boolean ready=phase==0?age>=20&&bestDot>initialDot+.1:phase==1?lasers>=3&&rockets>=1&&hits>0:phase==2?paused>=24&&pauseTravel>.01:lasers+rockets>=shotsAtResume+2;
        if(!ready){frames=0;return;}
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            int hull=0;for(int y=100;y<image.getHeight()-100;y++)for(int x=100;x<image.getWidth()-100;x++){int p=image.getColor(x,y),r=p&255,g=(p>>>8)&255,b=(p>>>16)&255;if(g>45&&g>r*1.15&&g>b*1.02)hull++;}if(hull<500){frames=0;return;}
            if(phase==1||phase==3){int pixels=0;for(var point:PROJECTED){int sx=(int)((point[0]+1)*.5*image.getWidth()),sy=(int)((1-point[1])*.5*image.getHeight());for(int y=Math.max(0,sy-16);y<Math.min(image.getHeight(),sy+17);y++)for(int x=Math.max(0,sx-16);x<Math.min(image.getWidth(),sx+17);x++){int p=image.getColor(x,y),r=p&255,g=(p>>>8)&255,b=(p>>>16)&255;if(g>130&&b>g*1.04&&r<g*.65||r>190&&g>80&&g<210&&b<g*.7)pixels++;}}if(pixels<3){frames=0;return;}shotPixels=Math.max(shotPixels,pixels);}
            if(++frames<3)return;String name=switch(phase){case 0->"autonomous-targeting";case 1->"autonomous-fire";case 2->"autopilot-disabled";default->"autopilot-resumed";};Files.createDirectories(file("ufo-ai-frames"));image.writeTo(file("ufo-ai-frames/"+name+".png"));SCENES.add(name);frames=0;
            if(phase==0){phase=1;return;}
            if(phase==1){c.getServer().execute(()->{var v=body.physics().linearVelocity();var l=body.physics().angularMomentum();body.setAutopilot(false);require(v.equals(body.physics().linearVelocity())&&l.equals(body.physics().angularMomentum()),"disable retains actual momenta");shotsAtPause=lasers+rockets;pauseAt=body.massPosition();phase=2;});return;}
            if(phase==2){c.getServer().execute(()->{var v=body.physics().linearVelocity();var l=body.physics().angularMomentum();body.setAutopilot(true);require(v.equals(body.physics().linearVelocity())&&l.equals(body.physics().angularMomentum()),"resume retains actual momenta");shotsAtResume=lasers+rockets;phase=3;});return;}
            var result=new LinkedHashMap<String,Object>();result.put("targetFixtureMaxHealth",200);result.put("targetFixtureKnockbackResistance",1);result.put("actualDefaultHull",true);result.put("naturalSurvivalTarget",true);result.put("noAssignedTarget",true);result.put("noScriptedShots",true);result.put("realPhysicalOwner",true);result.put("lasers",lasers);result.put("rockets",rockets);result.put("actualDamagingHits",hits);result.put("initialAimDot",initialDot);result.put("bestAimDot",bestDot);result.put("muzzleError",muzzleError);result.put("launchVelocityError",velocityError);result.put("disabledTicks",paused);result.put("disabledInertialTravel",pauseTravel);result.put("actualProjectedShotPixels",shotPixels);result.put("ownedShotRenderCalls",renderedShots);result.put("scenes",SCENES);result.put("height",BASE.getY());Files.writeString(file("ufo-ai-ok.json"),new com.google.gson.Gson().toJson(result));done=true;c.scheduleStop();
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
