package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Ordinary attached levers actuate physical barrel blocks after the contact phase.
 * Cooldowns belong to material cells, survive loading/cuts, and never queue missed shots. */
public final class BodyMountedWeapons {
    public static final int WORLD_CHECKS=64,WORLD_SHOTS=32,BODY_CHECKS=16,WORLD_BODIES=32;
    public record Stats(int bodies,int checks,int shots){}
    private static final class Schedule {long tick=Long.MIN_VALUE;int cursor;Stats last=new Stats(0,0,0);}
    private static final Map<ServerWorld,Schedule> SCHEDULES=new WeakHashMap<>();
    private final Map<BlockPos,Long> readyAt=new HashMap<>();
    private final MountedDefenseClaims defenseClaims=new MountedDefenseClaims();
    public MountedDefenseClaims defenseClaims(){return defenseClaims;}
    private final SortedSet<BlockPos> controlled=new TreeSet<>();
    private List<BlockPos> mounts=List.of();
    private boolean initialized,dirty;
    private int cursor;
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(BodyMountedWeapons::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->SCHEDULES.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SCHEDULES.clear());
    }
    public static boolean isWeapon(BlockState state){return state.isOf(ModWorldBlocks.SHIP_LASER)||state.isOf(ModWorldBlocks.SHIP_ROCKET);}
    /** Local lever semantics only: its actual mounted support must be the gun. No redstone-network emulation. */
    public static BlockPos support(BlockPos pos,BlockState lever){
        Direction side=switch(lever.get(Properties.BLOCK_FACE)){
            case FLOOR->Direction.DOWN;case CEILING->Direction.UP;case WALL->lever.get(Properties.HORIZONTAL_FACING).getOpposite();
        };return pos.offset(side);
    }
    private static boolean attached(BlockBodyEntity body,BlockPos gun,boolean powered){
        for(var side:Direction.values()){
            var cell=gun.offset(side);var state=body.getLocalBlock(cell);
            if(state.isOf(Blocks.LEVER)&&support(cell,state).equals(gun)&&(!powered||state.get(Properties.POWERED)))return true;
        }return false;
    }
    public static boolean locallyControlled(BlockBodyEntity body,BlockPos gun){return attached(body,gun,false);}
    public static boolean useLever(ServerPlayerEntity player,BlockBodyEntity body,BlockPos cell){
        var mode=player.interactionManager.getGameMode();var point=body.localToWorld(Vec3d.ofCenter(cell));
        if(!player.getServer().isOnThread()||!player.isAlive()||player.hasVehicle()||body.getWorld()!=player.getWorld()||body.isRemoved()
            ||!(mode==GameMode.CREATIVE||mode==GameMode.SURVIVAL)||BodySpatialIndex.find(body.getWorld(),body.getId()).orElse(null)!=body
            ||player.getEyePos().distanceTo(point)>Math.min(16,player.getBlockInteractionRange())+1
            ||!player.getServerWorld().canPlayerModifyAt(player,BlockPos.ofFloored(point)))return false;
        var state=body.getLocalBlock(cell);return state.isOf(Blocks.LEVER)&&body.setLocalBlock(cell,state.cycle(Properties.POWERED));
    }
    private void update(BlockBodyEntity body,BlockPos cell){
        boolean include=isWeapon(body.getLocalBlock(cell))&&locallyControlled(body,cell);
        if(include?controlled.add(cell.toImmutable()):controlled.remove(cell))dirty=true;
    }
    private List<BlockPos> mounts(BlockBodyEntity body){
        if(!initialized){initialized=true;for(var cell:body.cells())if(isWeapon(cell.state()))update(body,cell.pos());}
        if(dirty){mounts=List.copyOf(controlled);dirty=false;}return mounts;
    }
    /** Called at the material commit boundary, not during speculative fracture preparation. */
    public void materialChanged(BlockBodyEntity parent,List<BlockBodyEntity> fragments,List<BlockPos> changed){
        for(var cell:changed){
            if(!isWeapon(parent.getLocalBlock(cell))){var next=readyAt.remove(cell);if(next!=null)for(var fragment:fragments)if(isWeapon(fragment.getLocalBlock(cell))){fragment.mountedWeapons().readyAt.put(cell,next);break;}}
            if(initialized){update(parent,cell);for(var side:Direction.values())update(parent,cell.offset(side));}
        }
        defenseClaims.materialChanged(parent,fragments,changed,body->body.mountedWeapons().defenseClaims());
    }
    public static Stats stats(ServerWorld world){var schedule=SCHEDULES.get(world);return schedule==null?new Stats(0,0,0):schedule.last;}
    public static void tick(ServerWorld world){
        if(!world.getServer().isOnThread())throw new IllegalStateException("Mounted weapons are server-thread owned");
        var schedule=SCHEDULES.computeIfAbsent(world,key->new Schedule());long tick=world.getServer().getTicks();if(schedule.tick==tick)return;schedule.tick=tick;
        var bodies=BodySpatialIndex.loaded(world);
        int visited=0,checks=0,shots=0;long now=world.getTime();
        while(visited<Math.min(WORLD_BODIES,bodies.size())&&checks<WORLD_CHECKS&&shots<WORLD_SHOTS){
            var body=bodies.get(Math.floorMod(schedule.cursor++,bodies.size()));visited++;
            if(body.isRemoved()||body.boardingMotionPaused())continue;
            var state=body.mountedWeapons();var positions=state.mounts(body);boolean fired=false;int count=0;
            while(count<Math.min(BODY_CHECKS,positions.size())&&checks<WORLD_CHECKS&&shots<WORLD_SHOTS){
                var slot=positions.get(Math.floorMod(state.cursor++,positions.size()));count++;checks++;
                if(now<state.readyAt.getOrDefault(slot,Long.MIN_VALUE)||!attached(body,slot,true))continue;
                // An opaque ownership blob cannot safely authorize any cell.
                // Preserve it and the timers rather than infer ordinary mode.
                if(state.defenseClaims.readOnly())continue;
                var claim=state.defenseClaims.get(slot);
                if(claim.isPresent()){
                    // A paused/offline/invalid AA principal never falls through
                    // into unconditional ordinary fire and never queues a shot.
                    if(!com.terminaldetector.drmd.world.trap.PointDefenseInterception.tryFireMounted(body,slot,claim.get()))continue;
                }else if(MountedWeaponEmitter.emit(body,body,slot)==null)continue;
                state.readyAt.put(slot,now+(body.getLocalBlock(slot).isOf(ModWorldBlocks.SHIP_ROCKET)?24:8));shots++;fired=true;
            }
            if(fired)BodyJointSystem.reconcileImpulse(world,body);
        }
        schedule.last=new Stats(visited,checks,shots);
    }
    public NbtCompound write(BlockBodyEntity body){
        var n=new NbtCompound();n.putInt("schema",1);long now=body.getWorld().getTime();n.putLong("savedAt",now);var list=new NbtList();
        readyAt.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            int remaining=(int)Math.min(24,Math.max(0,entry.getValue()-now));if(remaining==0||!isWeapon(body.getLocalBlock(entry.getKey())))return;
            var cell=new NbtCompound();cell.putLong("cell",entry.getKey().asLong());cell.putInt("remaining",remaining);list.add(cell);
        });n.put("cooldowns",list);var claims=defenseClaims.write();if(claims!=null)n.put("aaDefense",claims);return n;
    }
    public void read(BlockBodyEntity body,NbtCompound n){
        if(n.getInt("schema")>1)throw new IllegalArgumentException("Unsupported mounted weapons schema");
        readyAt.clear();controlled.clear();mounts=List.of();initialized=dirty=false;cursor=0;
        defenseClaims.read(body,n.get("aaDefense"));
        var list=n.getList("cooldowns",NbtElement.COMPOUND_TYPE);if(list.size()>BlockBodyEntity.MAX_BLOCKS)throw new IllegalArgumentException("Too many mounted cooldowns");
        long now=body.getWorld().getTime(),saved=n.getLong("savedAt"),elapsed=saved>=0&&now>=saved?Math.min(24,now-saved):0;
        for(int i=0;i<list.size();i++){
            var entry=list.getCompound(i);var cell=BlockPos.fromLong(entry.getLong("cell"));int remaining=(int)Math.max(0,Math.min(24,entry.getInt("remaining"))-elapsed);
            if(BlockBodyEntity.validLocal(cell)&&isWeapon(body.getLocalBlock(cell))&&remaining>0)readyAt.put(cell,now+remaining);
        }
    }
}
