package com.terminaldetector.drmd.world.portal.gun;

import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import java.util.*;

/** One native pair per player and dimension; no entity cache or chunk ticket. */
public final class PortalGunState extends PersistentState {
    private static final Map<ServerWorld,PortalGunState> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    public static final int MAX_PAIRS=1024;
    public record Pair(BlockPos blue,BlockPos orange) {
        public Pair {blue=blue==null?null:blue.toImmutable();orange=orange==null?null:orange.toImmutable();}
        public BlockPos at(int slot){return slot==1?blue:orange;}
        public Pair with(int slot,BlockPos pos){return slot==1?new Pair(pos,orange):new Pair(blue,pos);}
    }
    private final Map<UUID,Pair> pairs=new HashMap<>();
    public static PortalGunState get(ServerWorld world) {
        return CACHE.computeIfAbsent(world,w->w.getPersistentStateManager().getOrCreate(new Type<>(PortalGunState::new,PortalGunState::read,null),"drmd_portal_projector"));
    }
    public static boolean hasActivePairs(ServerWorld world){var state=CACHE.get(world);return state!=null && state.hasPairs();}
    public Pair pair(UUID owner){return pairs.getOrDefault(owner,new Pair(null,null));}
    public boolean hasCapacity(UUID owner){return pairs.containsKey(owner) || pairs.size()<MAX_PAIRS;}
    public boolean hasPairs(){return !pairs.isEmpty();}
    public List<Pair> pairs(){return List.copyOf(pairs.values());}
    public void put(UUID owner,Pair pair) {
        if(pair.blue()==null && pair.orange()==null)pairs.remove(owner);
        else {
            if(!hasCapacity(owner))throw new IllegalStateException("Portal pair storage full");
            pairs.put(owner,pair);
        }
        markDirty();
    }
    public void removed(UUID owner,int slot,BlockPos pos) {
        Pair pair=pair(owner);if(pos.equals(pair.at(slot)))put(owner,pair.with(slot,null));
    }
    private static BlockPos position(NbtCompound nbt,String key) {
        if(!nbt.contains(key,NbtElement.COMPOUND_TYPE))return null;
        var p=nbt.getCompound(key);return new BlockPos(p.getInt("x"),p.getInt("y"),p.getInt("z"));
    }
    private static void position(NbtCompound nbt,String key,BlockPos pos) {
        if(pos==null)return;var p=new NbtCompound();p.putInt("x",pos.getX());p.putInt("y",pos.getY());p.putInt("z",pos.getZ());nbt.put(key,p);
    }
    public static PortalGunState read(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        var state=new PortalGunState();var entries=nbt.getList("pairs",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(entries.size(),MAX_PAIRS);i++) {
            var entry=entries.getCompound(i);if(!entry.containsUuid("owner"))continue;
            var pair=new Pair(position(entry,"blue"),position(entry,"orange"));
            if(pair.blue()!=null || pair.orange()!=null)state.pairs.put(entry.getUuid("owner"),pair);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        var entries=new NbtList();
        pairs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            var p=new NbtCompound();p.putUuid("owner",entry.getKey());position(p,"blue",entry.getValue().blue());position(p,"orange",entry.getValue().orange());entries.add(p);
        });
        nbt.put("pairs",entries);return nbt;
    }
}
