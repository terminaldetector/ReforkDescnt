package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.inventory.DroneLoadout;
import com.terminaldetector.drmd.physics.GravyPhysics;
import com.terminaldetector.drmd.physics.PhysicsTarget;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.gravity.TransientGravityFields;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Bounded read-only observations of the real native-client connection and weapon calls. */
public final class GravityInputRouteTrace {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.gravityInputRoute");
    private static final int MAX_EVENTS=512;
    private static final ArrayDeque<Object> EVENTS=new ArrayDeque<>();
    private static final ThreadLocal<Before> PENDING=new ThreadLocal<>();
    private static volatile UUID playerId;
    private static volatile int scene=-1;
    private static volatile List<UUID> targets=List.of();
    private static long sequence;
    private GravityInputRouteTrace(){}
    public record Target(UUID id,int entityId,boolean present,double x,double y,double z,double vx,double vy,double vz,double mass,int fieldTicks){}
    public record Grab(String mode,int count,double mass){}
    public record Before(int scene,long tick,String hand,String item,float energy,float cooldown,long lastFire,double aimX,double aimY,double aimZ,Grab grab,List<Target> targets){}
    public record Use(long sequence,int scene,long tick,boolean accepted,Before before,Before after){}
    public record Trigger(long sequence,int scene,long tick,int mask,boolean eligible,int selectedSlot,String hand){}
    public record Selection(long sequence,int scene,long tick,String kind,int selectedSlot,String cursor,String primary,String secondary){}
    public record Pulse(long baseline,Trigger press,Trigger release,Trigger latest){}
    public static void start(UUID id){synchronized(EVENTS){EVENTS.clear();sequence=0;}playerId=id;scene=-1;targets=List.of();}
    public static void scene(int value){scene=value;targets=List.of();}
    public static void targets(List<UUID> ids){targets=List.copyOf(ids);}
    public static long sequence(){synchronized(EVENTS){return sequence;}}
    private static long next(){synchronized(EVENTS){return ++sequence;}}
    private static void add(Object value){synchronized(EVENTS){if(EVENTS.size()==MAX_EVENTS)EVENTS.removeFirst();EVENTS.addLast(value);}}
    public static List<Object> events(){synchronized(EVENTS){return List.copyOf(EVENTS);}}
    public static List<Use> uses(int phase,long since){return events().stream().filter(e->e instanceof Use u&&u.scene()==phase&&u.sequence()>since).map(e->(Use)e).toList();}
    public static boolean trigger(int phase,long since,int mask){return events().stream().anyMatch(e->e instanceof Trigger t&&t.scene()==phase&&t.sequence()>since&&t.mask()==mask&&t.eligible());}
    public static boolean pressedForUse(int phase,long baseline,int mask,Use use){
        return use!=null&&use.scene()==phase&&events().stream().anyMatch(e->e instanceof Trigger t&&t.scene()==phase&&t.eligible()&&t.sequence()>baseline&&t.sequence()<use.sequence()&&t.tick()<=use.tick()&&t.mask()==mask);
    }
    /** Release can precede a deferred weapon tick. Pair the physical pulse itself,
     * require its actual use to follow its press, and require latest server input zero. */
    public static Optional<Pulse> releasedForUse(int phase,long baseline,int mask,Use use){
        if(use==null||use.scene()!=phase)return Optional.empty();
        Trigger press=null,release=null,latest=null;
        for(var event:events())if(event instanceof Trigger t&&t.scene()==phase&&t.sequence()>baseline){
            latest=t;
            if(!t.eligible())continue;
            if(press==null&&t.mask()==mask&&t.sequence()<use.sequence()&&t.tick()<=use.tick())press=t;
            if(press!=null&&release==null&&t.sequence()>press.sequence()&&t.mask()==0)release=t;
        }
        return press!=null&&release!=null&&latest.eligible()&&latest.mask()==0?Optional.of(new Pulse(baseline,press,release,latest)):Optional.empty();
    }
    public static long selections(int phase,String kind){return events().stream().filter(e->e instanceof Selection s&&s.scene()==phase&&s.kind().equals(kind)).count();}
    public static String item(ItemStack stack){return Registries.ITEM.getId(stack.getItem()).toString();}
    private static boolean observed(ServerPlayerEntity player){return ENABLED&&scene>=0&&playerId!=null&&playerId.equals(player.getUuid());}
    public static Grab grab(ServerPlayerEntity player){return new Grab(GravyPhysics.activeMode(player).map(Enum::name).orElse("none"),GravyPhysics.heldCount(player),GravyPhysics.heldMass(player));}
    public static List<Target> targets(ServerWorld world){
        var result=new ArrayList<Target>();
        for(var id:targets){Entity e=world.getEntity(id);
            if(e==null){result.add(new Target(id,-1,false,0,0,0,0,0,0,0,0));continue;}
            var velocity=e instanceof PhysicsTarget p?p.physicsVelocity():e.getVelocity().multiply(20);
            var centre=e instanceof PhysicsTarget p?p.physicsCentre():e.getBoundingBox().getCenter();
            int ticks=TransientGravityFields.remainingTicks(UUID.nameUUIDFromBytes(("drmd:gravity-projector:"+id).getBytes(StandardCharsets.UTF_8)));
            result.add(new Target(id,e.getId(),e.isAlive()&&!e.isRemoved(),centre.x,centre.y,centre.z,velocity.x,velocity.y,velocity.z,e instanceof PhysicsTarget p?p.physicsMass():0,ticks));
        }return List.copyOf(result);
    }
    private static Before snapshot(ServerPlayerEntity player,Hand hand){
        var stack=player.getStackInHand(hand);var custom=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        var aim=WeaponCore.aimDir(player).normalize();
        return new Before(scene,player.getWorld().getTime(),hand.name(),item(stack),DescentPlayerData.get(player).getGravyEnergy(),player.getItemCooldownManager().getCooldownProgress(stack.getItem(),0),custom==null?0:custom.copyNbt().getLong("lastFire"),aim.x,aim.y,aim.z,grab(player),targets(player.getServerWorld()));
    }
    public static void beforeUse(World world,PlayerEntity user,Hand hand){
        if(world instanceof ServerWorld&&user instanceof ServerPlayerEntity player&&observed(player))PENDING.set(snapshot(player,hand));
    }
    public static void afterUse(World world,PlayerEntity user,Hand hand,boolean accepted){
        if(!(world instanceof ServerWorld)||!(user instanceof ServerPlayerEntity player)||!observed(player))return;
        var before=PENDING.get();PENDING.remove();if(before==null)return;
        add(new Use(next(),scene,world.getTime(),accepted,before,snapshot(player,hand)));
    }
    public static void trigger(ServerPlayerEntity player,int mask){
        if(!observed(player))return;
        boolean eligible=player.isAlive()&&!player.isSpectator()&&player.currentScreenHandler==player.playerScreenHandler&&!com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(player);
        add(new Trigger(next(),scene,player.getWorld().getTime(),mask,eligible,player.getInventory().selectedSlot,item(player.getMainHandStack())));
    }
    public static void selection(ServerPlayerEntity player,String kind){
        if(!observed(player))return;
        var slots=DescentPlayerData.get(player).isEnabled()?DroneLoadout.of(player):null;
        add(new Selection(next(),scene,player.getWorld().getTime(),kind,player.getInventory().selectedSlot,item(player.currentScreenHandler.getCursorStack()),slots==null?"unread":item(slots.getStack(0)),slots==null?"unread":item(slots.getStack(1))));
    }
}
