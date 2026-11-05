package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual support routing through intact one-cell apertures; independent of the128-agent load stand. */
public final class NativeDroneRouteCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.droneRoutes");
    private static volatile boolean located,running,ready,done;
    private static boolean requested,spawnRequested,activated;
    private static int stage,ticks,age,blockedSamples,viewFrames;
    private static Vec3d previous;
    private static DroneEntity drone,ally;
    private static Entity threat;
    private static final Map<BlockPos,BlockState> WALL=new HashMap<>();
    private static final Set<UUID> DRAWN=new HashSet<>();
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static Entity entity(DroneEntity d){return d;}
    private static BlockPos base(){return new BlockPos(12,stage==0?207:2156,20);}
    private static boolean vertical(){return stage==1;}
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    public static void register() {
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable route scene only");
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!running||ready||done)return;
            if(!activated){if(entity(drone).age<5||entity(ally).age<5)return;require(s.getOverworld().getEntity(entity(drone).getUuid())==drone&&s.getOverworld().getEntity(entity(ally).getUuid())==ally,"real route actors admitted");DroneSwarmAi.clear();((MobEntity)drone).setAiDisabled(false);activated=true;return;}
            age++;var at=entity(drone).getPos();
            if(previous!=null&&at.squaredDistanceTo(previous)<1e-5&&Math.abs((vertical()?at.y-base().getY():at.x-base().getX())-(vertical()?2.1:2.55))<.02)blockedSamples++;
            previous=at;
            boolean crossed=vertical()?at.y>base().getY()+4.5:at.x>base().getX()+4.5;
            require(entity(drone).isAlive()&&entity(ally).isAlive(),"both actual route actors remain alive");
            require(age<400,"native support route timeout stage="+stage+" local="+at.subtract(Vec3d.of(base()))+" blocked="+blockedSamples);
            if(crossed) {
                require(blockedSamples>=3,"actual collision stall must precede the detour");
                require(WALL.keySet().stream().allMatch(p->s.getOverworld().getBlockState(p).isOf(Blocks.BEDROCK)),"route crosses an intact wall through its aperture");
                RESULTS.add(new LinkedHashMap<>(Map.of("height",base().getY(),"vertical",vertical(),"ticks",age,"blockedSamples",blockedSamples,"crossing",List.of(at.x-base().getX(),at.y-base().getY(),at.z-base().getZ()),"actualAi",true,"manualEntityTicks",false,"manualVelocity",false)));
                DRAWN.clear();ready=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeDroneRouteCheck::tick);
    }
    private static void tick(MinecraftClient c) {
        if(done)return;require(++ticks<4800,"native drone routes global timeout");com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())
            if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;c.player.getAbilities().flying=true;
        if(!requested) {
            requested=true;c.setScreen(null);c.options.hudHidden=true;
            com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());FlightSystem.disable(p,DescentPlayerData.get(p));
                double x=base().getX()+(vertical()?12:3.5),y=base().getY()+(vertical()?12:25),z=base().getZ()+12;
                p.teleport(safeWorld(c),x,y,z,Set.of(),0,0);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();located=true;});return;
        }
        if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(()->spawn(safeWorld(c)));}
    }
    private static ServerWorld safeWorld(MinecraftClient c){return c.getServer().getOverworld();}
    private static boolean loaded(MinecraftClient c) {
        var access=WorldAccess.of(c.world);var b=base();
        for(int x=(b.getX()-10)>>4;x<=(b.getX()+10)>>4;x++)for(int y=(b.getY()-10)>>4;y<=(b.getY()+10)>>4;y++)for(int z=(b.getZ()-10)>>4;z<=(b.getZ()+10)>>4;z++)
            if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void spawn(ServerWorld w) {
        WALL.clear();var b=base();
        for(int a=-8;a<=8;a++)for(int z=-8;z<=8;z++)if(a!=3||z!=0) {
            var p=b.add(vertical()?a:3,vertical()?3:a,z);WALL.put(p,w.getBlockState(p));w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);
        }
        var start=Vec3d.ofBottomCenter(b).add(0,.1,0);drone=ModEntities.DRONE.create(w);ally=ModEntities.DRONE.create(w);var cow=EntityType.COW.create(w);
        drone.applyRole(AiRole.SUPPORT);entity(drone).setPosition(start);((MobEntity)drone).setPersistent();entity(drone).setInvulnerable(true);((MobEntity)drone).setAiDisabled(true);
        ally.applyRole(AiRole.MG);entity(ally).setPosition(start.add(vertical()?0:6,vertical()?6:0,0));((LivingEntity)ally).setHealth(1);((MobEntity)ally).setAiDisabled(true);((MobEntity)ally).setPersistent();entity(ally).setInvulnerable(true);
        cow.setPosition(start.add(0,0,-6));cow.setAiDisabled(true);cow.setNoGravity(true);cow.setInvulnerable(true);threat=cow;
        require(w.spawnEntity(drone)&&w.spawnEntity(ally)&&w.spawnEntity(cow),"registered actors spawn");((MobEntity)drone).setTarget(cow);DroneSwarmAi.clear();previous=null;activated=false;age=blockedSamples=viewFrames=0;DRAWN.clear();running=true;
        System.out.println("DRONE_ROUTE stage="+stage+" height="+b.getY()+" initialThreatAssignedOnce=true supportGoalAutomatic=true manualTicks=false oneCellAperture=true");
    }
    public static void drew(DroneEntity d){if(ready&&drone!=null&&entity(d).getUuid().equals(entity(drone).getUuid()))DRAWN.add(entity(d).getUuid());}
    public static void rendered() {
        if(!ready||done)return;var c=MinecraftClient.getInstance();if(c.world==null||c.player==null||c.currentScreen!=null)return;
        Entity actor=null;for(var e:c.world.getEntities())if(e.getUuid().equals(entity(drone).getUuid()))actor=e;
        if(actor==null)return;var aim=actor.getBoundingBox().getCenter().subtract(c.player.getEyePos());
        float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));
        c.player.setYaw(yaw);c.player.prevYaw=yaw;c.player.setPitch(pitch);c.player.prevPitch=pitch;
        boolean clientCrossed=vertical()?actor.getY()>base().getY()+4.5:actor.getX()>base().getX()+4.5;
        if(++viewFrames<4||!clientCrossed||DRAWN.isEmpty())return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(file("drone-route-"+stage+".png"));}catch(Exception e){throw new IllegalStateException(e);}
        ready=running=false;c.getServer().execute(()->{var w=safeWorld(c);entity(drone).discard();entity(ally).discard();threat.discard();WALL.forEach((p,state)->w.setBlockState(p,state,3));WALL.clear();DroneSwarmAi.clear();
            c.execute(()->{if(++stage<2){located=requested=spawnRequested=false;return;}try{Files.writeString(file("drone-routes-ok.json"),new com.google.gson.Gson().toJson(Map.of("cases",RESULTS,"actualClient",true,"noTeleportFollowing",true,"intactOneCellApertures",true))+"\n");done=true;c.scheduleStop();}catch(Exception e){throw new IllegalStateException(e);}});
        });
    }
}
