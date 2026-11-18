package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.*;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.input.*;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.inventory.DroneLoadout;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.bombardment.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** Two actual socket/button transactions; no item/receiver/tick/cooldown invocation. */
public final class NativeClusterInputCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.clusterInput");
    private static final Vec3d START=new Vec3d(96.5,2158,96.5);
    private static final long WALL=160_000_000_000L;
    private record Bomb(UUID uuid,int id,boolean child,int age,int stage,boolean armed,List<Double> position,List<Double> velocity,List<Double> forward){}
    private record View(int tick,boolean nativeWorld,boolean flight,boolean emptyHand,boolean survival,int primary,int secondary,
                        List<Double> position,List<Double> up,List<Double> forward,boolean muzzleLoaded,List<Bomb> bombs,
                        int carriersSeen,int childrenSeen,boolean unarmedSeen,boolean armedSeen,Set<Integer> stages,int maxChildAge){}
    private static final class Counts {
        final Set<UUID> carriers=new HashSet<>(),children=new HashSet<>();final Set<Integer> stages=new TreeSet<>();
        boolean unarmed,armed;int childAge;
    }
    private static final Counts[] COUNTS={new Counts(),new Counts()};
    private static final List<View> ROWS=Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String,Object>> INPUTS=new ArrayList<>(),SCENES=new ArrayList<>();
    private static volatile UUID viewer;private static volatile View live;private static volatile String serverFailure;
    private static volatile boolean setup,finished,measuring;private static volatile int phase;
    private static long boot;private static int clientTicks,entered,frames,podCalls,stable;
    private static boolean framePod,captured;private static KeyBinding pressed;private static int pressTick;
    private static String step="ADMIT";private static final Set<UUID> clientCarriers=new HashSet<>(),clientChildren=new HashSet<>();
    private NativeClusterInputCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeClusterInputCheck::server);ClientTickEvents.END_CLIENT_TICK.register(NativeClusterInputCheck::tick);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static Vec3d vec(List<Double> v){return new Vec3d(v.get(0),v.get(1),v.get(2));}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static ItemStack stack(boolean heavy){
        var s=new ItemStack(heavy?ModItems.BOMB_HEAVY_CLUSTER:ModItems.BOMB_CLUSTER,3);var n=new NbtCompound();
        n.put(ClusterFlight.ITEM_KEY,ClusterFlight.writeProgram(ClusterProgram.preset(heavy?ClusterProgram.Pattern.STAGGER:ClusterProgram.Pattern.FAN)));
        s.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));return s;
    }
    private static void server(MinecraftServer server){
        if(finished||viewer==null||serverFailure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(viewer);if(p==null)return;var w=p.getServerWorld();var d=DescentPlayerData.get(p);
            require(WorldAccess.of(w).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual native BCC world required");
            if(!setup){
                require(!p.hasVehicle(),"free actual player, no substitute hull");p.changeGameMode(GameMode.SURVIVAL);
                p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);p.setVelocity(Vec3d.ZERO);
                p.getInventory().clear();p.getInventory().selectedSlot=8;var slots=DroneLoadout.of(p);slots.clear();slots.setStack(0,stack(false));slots.setStack(1,stack(true));slots.markDirty();
                FlightSystem.enableWithoutMount(p,d);d.setFlightVelocity(Vec3d.ZERO);p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();ModNetworking.syncPlayer(p,d);setup=true;return;
            }
            var slots=DroneLoadout.of(p);require(p.getMainHandStack().isEmpty()&&d.isEnabled()&&!p.hasVehicle()&&!p.isCreative(),"actual empty-hand Survival free6DoF authority persists");
            var bombs=new ArrayList<Bomb>();int scanned=0;var counts=COUNTS[Math.min(phase,1)];
            for(var e:w.iterateEntities()){
                require(++scanned<=4096,"bounded real entity observation; no fabricated empty result");
                if(!(e instanceof AerialBombEntity bomb)||e.isRemoved())continue;
                var n=e.writeNbt(new NbtCompound());if(!n.containsUuid("owner")||!viewer.equals(n.getUuid("owner")))continue;
                require(bombs.size()<32,"bounded actual owner ordnance");boolean child=n.getBoolean("sub");
                var f=n.getCompound(ClusterFlight.KEY);var forward=f.getCompound("forward");
                var row=new Bomb(e.getUuid(),e.getId(),child,n.getInt("FlightAge"),f.getInt("stage"),f.getBoolean("armed"),xyz(e.getPos()),xyz(e.getVelocity()),List.of(forward.getDouble("x"),forward.getDouble("y"),forward.getDouble("z")));bombs.add(row);
                if(measuring){if(child){counts.children.add(e.getUuid());counts.childAge=Math.max(counts.childAge,n.getInt("FlightAge"));}else{
                    if(counts.carriers.add(e.getUuid())){
                        var expected=phase==0?d.shipForward(p):d.shipUp(p).negate();
                        require(bomb.getOrdnance()==(phase==0?OrdnanceType.CLUSTER:OrdnanceType.HEAVY_CLUSTER),"actual bank emitted its own real ordnance type");
                        require(f.getCompound("program").getString("pattern").equals(phase==0?"FAN":"STAGGER"),"actual carrier retained the fixture item's saved program");
                        require(vec(row.forward).dotProduct(expected)>.94&&e.getVelocity().subtract(d.getFlightVelocity().multiply(.05)).dotProduct(expected)>.25,"actual carrier frame and velocity follow forward or rolled local-down launch");
                    }
                    counts.stages.add(row.stage);counts.unarmed|=!row.armed;counts.armed|=row.armed;
                }}
            }
            var up=d.shipUp(p);var fwd=d.shipForward(p);var muzzle=com.terminaldetector.drmd.weapon.core.WeaponCore.muzzle(p,phase==0?.9f:2.4f,0,phase==0?-.22f:-1.65f);
            boolean loaded=WorldEditSafety.loadedSegment(w,p.getEyePos(),muzzle)&&WorldEditSafety.loadedSegment(w,muzzle,muzzle.add((phase==0?fwd:up.negate()).multiply(80)));
            var value=new View(server.getTicks(),true,d.isEnabled(),p.getMainHandStack().isEmpty(),!p.isCreative()&&!p.isSpectator(),slots.getStack(0).getCount(),slots.getStack(1).getCount(),xyz(p.getPos()),xyz(up),xyz(fwd),loaded,List.copyOf(bombs),counts.carriers.size(),counts.children.size(),counts.unarmed,counts.armed,Set.copyOf(counts.stages),counts.childAge);
            if(measuring){synchronized(ROWS){require(ROWS.size()<300,"finite300 actual server rows");ROWS.add(value);}}
            live=value;
        }catch(Throwable error){serverFailure=error.toString();}
    }
    private static void key(KeyBinding key,boolean down){
        key.setPressed(down);if(down)KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(key));
        INPUTS.add(Map.of("phase",phase,"step",step,"clientTick",clientTicks,"serverTick",live==null?-1:live.tick,"binding",key.getTranslationKey(),"down",down));
    }
    private static void enter(String next){step=next;entered=live.tick;stable=0;}
    private static boolean clientReady(MinecraftClient c){
        if(c.currentScreen!=null||c.getOverlay()!=null||!c.isWindowFocused()||!c.mouse.isCursorLocked()||!DescentClientState.enabled||!ShipAttitudeClient.isPrimed())return false;
        var slots=DroneLoadout.of(c.player);
        return c.player.getMainHandStack().isEmpty()&&slots.getStack(0).getItem()==ModItems.BOMB_CLUSTER&&slots.getStack(1).getItem()==ModItems.BOMB_HEAVY_CLUSTER
            &&slots.getStack(0).getCount()==live.primary&&slots.getStack(1).getCount()==live.secondary&&DroneCombatInput.ownsMouse(c)&&DroneCombatInput.primary(c).getItem()==ModItems.BOMB_CLUSTER;
    }
    private static void tick(MinecraftClient c){
        if(finished)return;
        if(++clientTicks>3200||System.nanoTime()-boot>WALL){finish("FAIL","finite160s/3200client tick deadline: "+step);return;}
        if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen prompt&&prompt.getTitle().getString().equals("Warning! Low disk space!"))for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Continue"))b.onPress();
        if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        try{
            if(serverFailure!=null)throw new IllegalStateException(serverFailure);
            if(c.player==null||c.world==null||c.getServer()==null)return;DescentClient.markUserFlightChoice();
            if(viewer==null){viewer=c.player.getUuid();c.options.hudHidden=false;DescentConfig.weaponView=true;return;}
            if(live==null)return;
            if(pressed!=null&&clientTicks>pressTick){key(pressed,false);pressed=null;}
            if(!step.equals("ADMIT"))require(live.tick-entered<180,"finite ordinary phase deadline: "+step);
            if(step.equals("ADMIT")){
                if(!clientReady(c)||!live.muzzleLoaded||vec(live.position).distanceTo(START)>1||frames<4)return;
                require(live.primary==3&&live.secondary==3&&live.bombs.isEmpty()&&vec(live.up).y>.8,"initial real socket counts/clear owner lifecycle/ordinary frame");
                measuring=true;enter("PRIMARY");return;
            }
            require(c.currentScreen==null&&c.getOverlay()==null&&c.isWindowFocused()&&c.mouse.isCursorLocked()&&DescentClientState.enabled&&c.player.getMainHandStack().isEmpty(),"normal no-menu cursor/flight/empty-hand admission");
            if(step.equals("PRIMARY")||step.equals("SECONDARY")){
                if(!clientReady(c)||!live.muzzleLoaded||stable++<2)return;var key=phase==0?c.options.attackKey:c.options.useKey;
                key(key,true);pressed=key;pressTick=clientTicks;captured=false;clientCarriers.clear();clientChildren.clear();enter(phase==0?"PRIMARY_WAIT":"SECONDARY_WAIT");return;
            }
            if(step.equals("ROLL")){
                if(Math.abs(vec(live.up).y)<.45){key(DescentKeybinds.rollRight,false);enter("SECONDARY");}
                return;
            }
            boolean waitingForEdge=live.carriersSeen==0&&live.primary==(phase==0?3:2)&&live.secondary==3;
            require(waitingForEdge||live.primary==2&&live.secondary==(phase==0?3:2),"one Survival debit per actual button edge; no held repeat");
            require(live.carriersSeen<=1&&live.childrenSeen<=(phase==0?7:14),"one bounded real carrier/bomblet roster");
            if(live.bombs.isEmpty()&&live.carriersSeen==1){
                require(live.childrenSeen==(phase==0?7:14)&&live.unarmedSeen&&live.armedSeen&&live.maxChildAge>2,"natural arm/stage/child lifecycle reached its finite retirement");
                require(clientReady(c)&&captured&&clientCarriers.size()==1&&!clientChildren.isEmpty(),"actual carrier+children tracked and completed viewmodel PNG captured");
                require(phase==0||live.stages.containsAll(Set.of(0,1,2)),"three stagger stages observed before final retirement");
                SCENES.add(Map.of("phase",phase,"view",live,"actualClientCarrierUUIDs",Set.copyOf(clientCarriers),"actualClientChildUUIDs",Set.copyOf(clientChildren),"completedPodCalls",podCalls));
                if(phase==1){finish("PASS","two socket-only actual mouse edges, ammo debit, real7/14 lifecycles and completed viewmodel frames");return;}
                phase=1;key(DescentKeybinds.rollRight,true);enter("ROLL");
            }
        }catch(Throwable error){finish("FAIL",error.toString());}
    }
    public static void beforeFrame(){if(ENABLED)framePod=false;}
    /** Passive RETURN of the actual private viewmodel mesh emitter, not a synthetic render. */
    public static void pod(){if(ENABLED&&!finished&&!OffscreenWorldView.busy()){podCalls++;framePod=true;}}
    public static void rendered(){
        if(!ENABLED||finished)return;frames++;var c=MinecraftClient.getInstance();if(live==null||c.player==null||c.world==null)return;
        try{
            for(var b:live.bombs){Entity e=c.world.getEntityById(b.id);if(e!=null&&e.getUuid().equals(b.uuid)){if(b.child)clientChildren.add(b.uuid);else clientCarriers.add(b.uuid);}}
            if(captured||!measuring||!framePod||clientCarriers.isEmpty()||!step.endsWith("_WAIT"))return;
            require(c.currentScreen==null&&c.getOverlay()==null&&c.player.getMainHandStack().isEmpty(),"actual completed first-person empty-hand render");
            int before=GL11.glGetError();require(before==0,"pre-readback GL error");var dir=c.runDirectory.toPath().resolve("cluster-input-frames");Files.createDirectories(dir);
            try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(dir.resolve(phase==0?"primary-fan-forward.png":"secondary-stagger-rolled-down.png"));}
            require(GL11.glGetError()==0,"completed readback preserves GL success");captured=true;
        }catch(Throwable error){finish("FAIL",error.toString());}
    }
    private static void finish(String status,String reason){
        if(finished)return;finished=true;var c=MinecraftClient.getInstance();if(c.options!=null){c.options.attackKey.setPressed(false);c.options.useKey.setPressed(false);}if(DescentKeybinds.rollRight!=null)DescentKeybinds.rollRight.setPressed(false);
        try{
            List<View> rows;synchronized(ROWS){rows=List.copyOf(ROWS);}
            var result=new LinkedHashMap<String,Object>();result.put("status",status);result.put("live",live);result.put("admissionScreen",String.valueOf(c.currentScreen));result.put("admissionFocused",c.isWindowFocused());result.put("admissionCursorLocked",c.mouse.isCursorLocked());result.put("reason",reason);result.put("phase",phase);result.put("step",step);result.put("scenes",SCENES);result.put("inputs",INPUTS);result.put("rows",rows);result.put("initialTeleports",setup?1:0);result.put("authoredWorldBlocks",0);result.put("completedPodCalls",podCalls);result.put("glRenderer",GL11.glGetString(GL11.GL_RENDERER));result.put("sourceBinding",System.getProperty("drmd.gate.clusterSource","UNBOUND"));
            Files.writeString(c.runDirectory.toPath().resolve("cluster-input-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        }catch(Exception error){error.printStackTrace();}System.out.println("CLUSTER_INPUT_OUTCOME "+status+" "+reason);c.scheduleStop();
    }
}
