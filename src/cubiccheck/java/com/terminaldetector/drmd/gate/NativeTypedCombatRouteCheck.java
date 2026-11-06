package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.DroneSwarmAi;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import java.nio.file.*;
import java.util.*;

/** Default combat goals, actual projectiles and intact offset cover in a real native cubic world. */
public final class NativeTypedCombatRouteCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.typedCombatRoutes");
    private static final BlockPos BASE=new BlockPos(12,2156,20);
    private static final List<FlightEnemyKind> KINDS=List.of(FlightEnemyKind.ORB,FlightEnemyKind.ROCKET,FlightEnemyKind.JET,FlightEnemyKind.ROCKET);
    private static final Map<BlockPos,BlockState> WALL=new HashMap<>();
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static final Set<UUID> DRAWN=new HashSet<>();
    private static volatile boolean located,running,ready,done;
    private static boolean requested,spawnRequested,activated,lane,shot;
    private static int stage,ticks,age,frames;
    private static volatile UUID id;
    private static FlightEnemyEntity drone;
    private static LivingEntity target;
    private static FlightEnemyKind kind(){return KINDS.get(stage);}
    private static Entity entity(Entity e){return e;}
    private static Path file(String n){return MinecraftClient.getInstance().runDirectory.toPath().resolve(n);}
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    public static void register(){
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable combat-route fixture only");
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!running||ready||done)return;
            if(!activated){if(entity(drone).age<5||entity(target).age<5)return;((MobEntity)drone).setAiDisabled(false);DroneSwarmAi.clear();activated=true;return;}
            var w=s.getOverworld();require(CubicAvailability.backend(w)==CubicAvailability.Backend.NATIVE_CUBIC,"real cubic backend");
            require(++age<400,"typed native combat route timeout: kind="+kind()+" touching="+(stage==3)+" lane="+lane+" shot="+shot+" local="+entity(drone).getPos().subtract(Vec3d.of(BASE)));
            require(entity(drone).isAlive()&&entity(target).isAlive()&&drone.getRole()==kind().defaultRole,"actual default combat actors remain alive");
            int width=(int)Math.ceil(kind().diameter)+1;var from=entity(drone).getBoundingBox().getCenter();var to=entity(target).getBoundingBox().getCenter();double dx=to.x-from.x;
            if(Math.abs(dx)>1e-8){double t=(BASE.getX()+6.5-from.x)/dx;var at=from.lerp(to,t);
                if(t>=0&&t<=1&&at.y>=BASE.getY()+4&&at.y<=BASE.getY()+4+width&&at.z>=BASE.getZ()&&at.z<=BASE.getZ()+width&&w.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,drone)).getType()==HitResult.Type.MISS)lane=true;
            }
            for(var e:w.iterateEntities())if(e instanceof ProjectileEntity p&&p.getOwnerLiving()==drone&&e.getX()>BASE.getX()+7&&e.getPos().squaredDistanceTo(to)<9)shot=true;
            if(lane&&shot){require(WALL.keySet().stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK)),"cover remains intact");ready=true;frames=0;DRAWN.clear();}
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<4800,"native typed combat route global timeout");com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;c.player.getAbilities().flying=true;
            if(!requested){requested=true;c.setScreen(null);c.options.hudHidden=true;c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.getToastManager().clear();c.inGameHud.getChatHud().clear(false);
                com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
                c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));p.teleport(safeWorld(),BASE.getX()+6,BASE.getY()+18,BASE.getZ()+15,Set.of(),180,25);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();safeWorld().setTimeOfDay(6000);located=true;});return;
            }
            if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(()->spawn(safeWorld()));}
        });
    }
    private static ServerWorld safeWorld(){return MinecraftClient.getInstance().getServer().getOverworld();}
    private static boolean loaded(MinecraftClient c){
        var access=WorldAccess.of(c.world);
        for(int x=(BASE.getX()-14)>>4;x<=(BASE.getX()+26)>>4;x++)for(int y=(BASE.getY()-14)>>4;y<=(BASE.getY()+26)>>4;y++)for(int z=(BASE.getZ()-14)>>4;z<=(BASE.getZ()+24)>>4;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void spawn(ServerWorld w){
        int width=(int)Math.ceil(kind().diameter)+1;
        for(int y=-12;y<=12;y++)for(int z=-12;z<=12;z++){if(y>=4&&y<4+width&&z>=0&&z<width)continue;var p=BASE.add(6,y,z);WALL.put(p,w.getBlockState(p));w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);}
        var original=Vec3d.ofBottomCenter(BASE).add(.27,.37,.19);var start=stage==3?new Vec3d(BASE.getX()+6-kind().diameter*.5-.001,original.y,original.z):original;
        drone=kind().create(w);entity(drone).setPosition(start);((MobEntity)drone).setPersistent();((MobEntity)drone).setAiDisabled(true);entity(drone).setInvulnerable(true);
        target=EntityType.COW.create(w);entity(target).setPosition(original.add(18,0,0));((MobEntity)target).setAiDisabled(true);entity(target).setNoGravity(true);entity(target).setInvulnerable(true);
        require(w.spawnEntity(drone)&&w.spawnEntity(target),"real registered combat actors");((MobEntity)drone).setTarget(target);id=entity(drone).getUuid();DroneSwarmAi.clear();activated=lane=shot=false;age=frames=0;DRAWN.clear();running=true;
    }
    public static void drew(FlightEnemyEntity e){if(ENABLED&&id!=null&&entity(e).getUuid().equals(id))DRAWN.add(id);}
    private static Entity clientActor(MinecraftClient c){if(c.world==null||id==null)return null;for(var e:c.world.getEntities())if(e.getUuid().equals(id))return e;return null;}
    public static void beforeFrame(float delta){
        if(!ENABLED||!ready||done)return;var c=MinecraftClient.getInstance();if(c.player==null)return;var e=clientActor(c);if(e==null)return;
        var eye=entity(c.player).getLerpedPos(delta).add(0,entity(c.player).getEyeHeight(entity(c.player).getPose()),0);var aim=e.getLerpedPos(delta).add(0,e.getHeight()*.5,0).subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));c.player.setYaw(yaw);c.player.prevYaw=yaw;c.player.setPitch(pitch);c.player.prevPitch=pitch;
    }
    public static void rendered(){
        boolean drew=id!=null&&DRAWN.contains(id);DRAWN.clear();if(!ready||done)return;var c=MinecraftClient.getInstance();var e=clientActor(c);if(c.currentScreen!=null||!(e instanceof FlightEnemyEntity d)||d.kind()!=kind()||d.getRole()!=kind().defaultRole||!drew){frames=0;return;}
        if(++frames<4)return;
        int pixels=0;try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getColor(x,y),r=p&255,g=(p>>>8)&255,b=(p>>>16)&255;boolean match=switch(kind()){case ORB->r<g*.8&&g>b*1.05&&b>20;case ROCKET->r>g*1.2&&g>b*1.2&&r>30;case JET->b>g*1.1&&g>r*1.03&&b<180;};if(match)pixels++;}
            if(pixels<3)return;image.writeTo(file("typed-combat-route-"+stage+".png"));
        }catch(Exception failure){throw new IllegalStateException(failure);}
        var result=new LinkedHashMap<String,Object>();result.put("kind",kind().id);result.put("height",BASE.getY());result.put("touchingStart",stage==3);result.put("ticks",age);result.put("actualFiringLane",lane);result.put("actualProjectileBeyondCover",shot);result.put("consecutiveFreshFrames",frames);result.put("visibleHullPixels",pixels);RESULTS.add(result);ready=running=false;
        c.getServer().execute(()->{var w=safeWorld();for(var actor:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(actor instanceof ProjectileEntity p&&p.getOwnerLiving()==drone)actor.discard();entity(drone).discard();entity(target).discard();WALL.forEach((p,state)->w.setBlockState(p,state,3));WALL.clear();DroneSwarmAi.clear();
            c.execute(()->{if(++stage<KINDS.size()){requested=located=spawnRequested=false;return;}try{Files.writeString(file("typed-combat-routes-ok.json"),new com.google.gson.Gson().toJson(Map.of("cases",RESULTS,"initialTargetAssignedOnce",true,"manualEntityTicks",false,"manualVelocity",false,"noFollowTeleport",true,"intactCover",true))+"\n");done=true;c.scheduleStop();}catch(Exception failure){throw new IllegalStateException(failure);}});
        });
    }
}
