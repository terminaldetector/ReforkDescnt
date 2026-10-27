package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.lang.ref.WeakReference;

/** Bounded interest subscriptions, never a second source of blocks or load tickets. */
public final class CubeLifecycle {
    public static final int MAX_CUBES=1024,MAX_LISTENERS=8192,PROBES_PER_TICK=64;
    public enum Change { AVAILABLE,UNAVAILABLE,CLOSED }
    public record Event(RegistryKey<World> dimension,long session,D6ChunkPos cube,Change change,boolean available) {}
    public interface Subscription extends AutoCloseable {@Override void close();}
    private static final Map<World,State> STATES=new WeakHashMap<>();
    private static final Set<World> CLOSED=Collections.newSetFromMap(new WeakHashMap<>());
    private static final AtomicLong SESSIONS=new AtomicLong();
    private static World clientWorld;
    private CubeLifecycle() {}
    private static final class State {
        final long session=SESSIONS.incrementAndGet();
        final Map<D6ChunkPos,Entry> entries=new HashMap<>();final ArrayDeque<Entry> queue=new ArrayDeque<>();
        int listeners;long nextId;boolean closed;
    }
    private static final class Entry {
        final D6ChunkPos cube;final Map<Long,Listener> listeners=new LinkedHashMap<>();
        Entry(D6ChunkPos cube){this.cube=cube;}
    }
    private static final class Listener {
        Consumer<Event> callback;boolean available;
        Listener(Consumer<Event> callback,boolean available){this.callback=callback;this.available=available;}
    }
    public static Optional<Subscription> watch(World world,D6ChunkPos cube,Consumer<Event> callback) {
        Objects.requireNonNull(cube);Objects.requireNonNull(callback);
        if(!CubicAvailability.onWorldThread(world))return Optional.empty();
        State state;
        synchronized(STATES) {
            if(CLOSED.contains(world))return Optional.empty();
            state=STATES.computeIfAbsent(world,ignored->new State());
        }
        if(state.closed || state.listeners>=MAX_LISTENERS || !state.entries.containsKey(cube) && state.entries.size()>=MAX_CUBES)return Optional.empty();
        Entry entry=state.entries.get(cube);
        if(entry==null){entry=new Entry(cube);state.entries.put(cube,entry);state.queue.addLast(entry);compact(state);}
        if(entry.listeners.size()>=64)return Optional.empty();
        long id=state.nextId++;var listener=new Listener(callback,CubicAvailability.available(world,cube));
        entry.listeners.put(id,listener);state.listeners++;
        Entry owned=entry;
        if(!emit(world,state,entry,listener,listener.available?Change.AVAILABLE:Change.UNAVAILABLE)) {
            remove(state,entry,id);listener.callback=null;return Optional.empty();
        }
        if(state.closed || entry.listeners.get(id)!=listener)return Optional.empty();
        var ownerReference=new WeakReference<>(world);
        return Optional.of(()->{
            if(state.closed)return;
            World owner=ownerReference.get();
            if(owner==null){remove(state,owned,id);listener.callback=null;return;}
            if(!CubicAvailability.onWorldThread(owner))throw new IllegalStateException("Cube subscription closes on its world thread");
            if(owned.listeners.get(id)==listener){remove(state,owned,id);emit(owner,state,owned,listener,Change.CLOSED);listener.callback=null;}
        });
    }
    public static void tick(MinecraftServer server){for(var world:server.getWorlds())tick(world);}
    public static void clientTick(World current) {
        if(clientWorld!=current){if(clientWorld!=null)close(clientWorld);clientWorld=current;}
        if(current!=null)tick(current);
    }
    private static void tick(World world) {
        State state;synchronized(STATES){state=STATES.get(world);}
        if(state==null || state.closed)return;
        int count=Math.min(PROBES_PER_TICK,state.queue.size());
        for(int i=0;i<count && !state.closed;i++) {
            var entry=state.queue.removeFirst();
            if(state.entries.get(entry.cube)!=entry)continue;
            boolean available=CubicAvailability.available(world,entry.cube);
            for(var item:List.copyOf(entry.listeners.entrySet())) {
                var listener=item.getValue();
                if(entry.listeners.get(item.getKey())!=listener || listener.available==available)continue;
                listener.available=available;
                if(!emit(world,state,entry,listener,available?Change.AVAILABLE:Change.UNAVAILABLE)){remove(state,entry,item.getKey());listener.callback=null;}
            }
            if(state.entries.get(entry.cube)==entry)state.queue.addLast(entry);
        }
    }
    public static void closeServer(MinecraftServer server) {
        List<World> worlds;synchronized(STATES){worlds=List.copyOf(STATES.keySet());}
        for(var world:worlds)if(world instanceof ServerWorld s && s.getServer()==server)close(world);
    }
    public static void close(World world) {
        if(!CubicAvailability.onWorldThread(world))throw new IllegalStateException("Cube observers close on their world thread");
        State state;synchronized(STATES){CLOSED.add(world);state=STATES.remove(world);}
        if(state==null)return;state.closed=true;
        for(var entry:List.copyOf(state.entries.values())) {
            for(var listener:List.copyOf(entry.listeners.values())){emit(world,state,entry,listener,Change.CLOSED);listener.callback=null;}
            entry.listeners.clear();
        }
        state.entries.clear();state.queue.clear();state.listeners=0;
    }
    private static void remove(State state,Entry entry,long id) {
        if(entry.listeners.remove(id)!=null)state.listeners--;
        if(entry.listeners.isEmpty())state.entries.remove(entry.cube,entry);
        compact(state);
    }
    private static void compact(State state) {
        if(state.queue.size()>2*MAX_CUBES){state.queue.clear();state.queue.addAll(state.entries.values());}
    }
    private static boolean emit(World world,State state,Entry entry,Listener listener,Change change) {
        if(listener.callback==null)return false;
        try {listener.callback.accept(new Event(world.getRegistryKey(),state.session,entry.cube,change,listener.available));return true;}
        catch(RuntimeException failure){DescentMod.LOGGER.error("[drmd world access] cube subscriber failed at {}",entry.cube,failure);return false;}
    }
}
