package com.terminaldetector.drmd.gate;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import java.nio.file.*;
import java.util.*;

/** Six real default combat actors sharing one intact aperture in native cubic space. */
public final class NativeSharedCombatCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.sharedCombat");
    private static final BlockPos BASE=new BlockPos(12,2156,20);
    private static final List<FlightEnemyEntity> DRONES=new ArrayList<>();
    private static final List<LivingEntity> TARGETS=new ArrayList<>();
    private static final Map<BlockPos,BlockState> WALL=new HashMap<>();
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static final Set<UUID> LANES=new HashSet<>(),HITS=new HashSet<>(),DRAWN=new HashSet<>();
    private static volatile Set<UUID> ids=Set.of();
    private static volatile boolean located,running,ready,done;
    private static boolean requested,spawnRequested,activated;
    private static int stage,ticks,age,frames,worstMerge,maxCandidates,minIndexed;
    private static int[][] merged;
    private static boolean vertical(){return stage==1;}
    private static Entity e(Entity actor){return actor;}
    private static Path file(String n){return MinecraftClient.getInstance().runDirectory.toPath().resolve(n);}
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static Vec3d point(double forward,double lateral,double z){return Vec3d.of(BASE).add(vertical()?lateral:forward,vertical()?forward:lateral,z);}
    public static void register(){
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable shared combat fixture only");
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!running||ready||done)return;
            if(!activated){if(DRONES.stream().anyMatch(d->e(d).age<5)||TARGETS.stream().anyMatch(t->e(t).age<5))return;DRONES.forEach(d->((MobEntity)d).setAiDisabled(false));DroneSwarmAi.clear();activated=true;return;}
            var w=server.getOverworld();require(CubicAvailability.backend(w)==CubicAvailability.Backend.NATIVE_CUBIC,"real cubic backend");age++;
            for(int i=0;i<6;i++){
                var d=DRONES.get(i);require(e(d).isAlive()&&d.getRole()==FlightEnemyKind.ORB.defaultRole,"live default role");var target=((MobEntity)d).getTarget();var from=e(d).getBoundingBox().getCenter();
                if(target!=null){var to=e(target).getBoundingBox().getCenter();double df=vertical()?to.y-from.y:to.x-from.x;
                    if(Math.abs(df)>1e-8){double t=((vertical()?BASE.getY():BASE.getX())+6.5-(vertical()?from.y:from.x))/df;var at=from.lerp(to,t);double lateral=vertical()?at.x-BASE.getX():at.y-BASE.getY();if(t>=0&&t<=1&&lateral>=4&&lateral<=6&&at.z>=BASE.getZ()&&at.z<=BASE.getZ()+2&&w.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,d)).getType()==HitResult.Type.MISS)LANES.add(e(d).getUuid());}
                }
                for(int j=0;j<i;j++){merged[i][j]=e(d).getPos().squaredDistanceTo(e(DRONES.get(j)).getPos())<.04?merged[i][j]+1:0;worstMerge=Math.max(worstMerge,merged[i][j]);}
                var stats=DroneSwarmAi.queryStats(d);maxCandidates=Math.max(maxCandidates,stats.examinedCandidates());minIndexed=Math.min(minIndexed,stats.indexedDrones());
            }
            for(var t:TARGETS){require(e(t).isAlive(),"training target alive");if(t.getHealth()<1000&&t.getAttacker()!=null&&ids.contains(e(t.getAttacker()).getUuid()))HITS.add(e(t.getAttacker()).getUuid());t.setHealth(t.getMaxHealth());}
            require(worstMerge<20,"persistent shared-route coincidence stage="+stage+" ticks="+worstMerge);require(age<600,"shared route timeout stage="+stage+" lanes="+LANES.size()+" damagingActors="+HITS.size());
            if(LANES.size()==6&&HITS.size()==6){require(maxCandidates<=DroneSwarmAi.MAX_BOID_CANDIDATES&&minIndexed>=6,"bounded neighbour index");require(WALL.keySet().stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK)),"intact common cover");ready=true;frames=0;DRAWN.clear();}
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<4800,"native shared combat global timeout");com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;c.player.getAbilities().flying=true;
            if(!requested){requested=true;c.setScreen(null);c.options.hudHidden=true;c.options.getFov().setValue(90);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.getToastManager().clear();c.inGameHud.getChatHud().clear(false);com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
                c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));p.teleport(world(),BASE.getX()+(vertical()?26:6),BASE.getY()+(vertical()?6:28),BASE.getZ()+30,Set.of(),180,25);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();world().setTimeOfDay(6000);located=true;});return;
            }
            if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;c.getServer().execute(()->spawn(world()));}
        });
    }
    private static ServerWorld world(){return MinecraftClient.getInstance().getServer().getOverworld();}
    private static boolean loaded(MinecraftClient c){var access=WorldAccess.of(c.world);for(int x=(BASE.getX()-22)>>4;x<=(BASE.getX()+28)>>4;x++)for(int y=(BASE.getY()-22)>>4;y<=(BASE.getY()+28)>>4;y++)for(int z=(BASE.getZ()-22)>>4;z<=(BASE.getZ()+24)>>4;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;return true;}
    private static void spawn(ServerWorld w){
        for(int a=-20;a<=20;a++)for(int z=-20;z<=20;z++){if(a>=4&&a<6&&z>=0&&z<2)continue;var p=BASE.add(vertical()?a:6,vertical()?6:a,z);WALL.put(p,w.getBlockState(p));w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);}
        var keys=new HashSet<UUID>();for(int i=0;i<6;i++){
            var d=FlightEnemyKind.ORB.create(w);e(d).setPosition(point(.77-(i%2)*1.25,.37+(i/2)*1.3,.69+(i%2)*1.4));((MobEntity)d).setAiDisabled(true);((MobEntity)d).setPersistent();e(d).setInvulnerable(true);
            LivingEntity t=EntityType.COW.create(w);e(t).setPosition(point(18.77,.37+(i/2)*2.5,.69+((i%2)-.5)*5));((MobEntity)t).setAiDisabled(true);e(t).setNoGravity(true);t.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);t.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);t.setHealth(1000);
            require(w.spawnEntity(d)&&w.spawnEntity(t),"real shared actors registered");((MobEntity)d).setTarget(t);DRONES.add(d);TARGETS.add(t);keys.add(e(d).getUuid());
        }
        ids=Set.copyOf(keys);merged=new int[6][6];LANES.clear();HITS.clear();DRAWN.clear();age=frames=worstMerge=maxCandidates=0;minIndexed=Integer.MAX_VALUE;activated=false;DroneSwarmAi.clear();running=true;
    }
    public static void drew(FlightEnemyEntity d){if(ENABLED&&ids.contains(e(d).getUuid()))DRAWN.add(e(d).getUuid());}
    public static void beforeFrame(float delta){
        if(!ENABLED||!ready||done)return;var c=MinecraftClient.getInstance();if(c.player==null||c.world==null)return;var centre=Vec3d.ZERO;int n=0;for(var a:c.world.getEntities())if(ids.contains(a.getUuid())){centre=centre.add(a.getLerpedPos(delta).add(0,a.getHeight()*.5,0));n++;}if(n!=6)return;
        var eye=e(c.player).getLerpedPos(delta).add(0,e(c.player).getEyeHeight(e(c.player).getPose()),0);var aim=centre.multiply(1.0/n).subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));c.player.setYaw(yaw);c.player.prevYaw=yaw;c.player.setPitch(pitch);c.player.prevPitch=pitch;
    }
    public static void rendered(){
        int rendered=DRAWN.size();DRAWN.clear();if(!ready||done)return;var c=MinecraftClient.getInstance();if(c.world==null||c.currentScreen!=null||rendered!=6){frames=0;return;}if(++frames<4)return;
        int green=0;try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getColor(x,y),r=p&255,g=(p>>>8)&255,b=(p>>>16)&255;if(r<g*.8&&g>b*1.05&&b>20)green++;}if(green<3)return;image.writeTo(file("shared-combat-"+stage+".png"));}catch(Exception failure){throw new IllegalStateException(failure);}
        var row=new LinkedHashMap<String,Object>();row.put("vertical",vertical());row.put("height",BASE.getY());row.put("actors",6);row.put("firingLanes",LANES.size());row.put("damagingActors",HITS.size());row.put("ticks",age);row.put("maxCoincidentTicks",worstMerge);row.put("maxCandidates",maxCandidates);row.put("minIndexed",minIndexed);row.put("consecutiveFrames",frames);row.put("rendered",rendered);row.put("greenPixels",green);RESULTS.add(row);ready=running=false;
        c.getServer().execute(()->{var w=world();for(var a:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(a instanceof ProjectileEntity p&&p.getOwnerLiving()!=null&&ids.contains(e(p.getOwnerLiving()).getUuid()))a.discard();DRONES.forEach(d->e(d).discard());TARGETS.forEach(t->e(t).discard());DRONES.clear();TARGETS.clear();WALL.forEach((p,state)->w.setBlockState(p,state,3));WALL.clear();DroneSwarmAi.clear();c.execute(()->{if(++stage<2){requested=located=spawnRequested=false;return;}try{Files.writeString(file("shared-combat-ok.json"),new com.google.gson.Gson().toJson(Map.of("cases",RESULTS,"initialTargetAssignedOnce",true,"naturalRetargetingAllowed",true,"manualEntityTicks",false,"manualVelocity",false,"noFollowTeleport",true,"intactCover",true))+"\n");done=true;c.scheduleStop();}catch(Exception failure){throw new IllegalStateException(failure);}});});
    }
}
