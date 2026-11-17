package com.terminaldetector.drmd.world.compat;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Retain boundary work while the native world and normal fluid scheduler own simulation. */
public final class NativeDeferredFluids {
    public static final int MAX_ENTRIES=4096,ADMISSIONS_PER_TICK=128,ATTEMPTS_PER_TICK=16;
    public static final String STATE_ID="drmd_native_deferred_fluids";
    private static final PersistentState.Type<Pending> TYPE=new PersistentState.Type<>(Pending::new,Pending::read,null);
    private static final Map<ServerWorld,Binding> BINDINGS=new WeakHashMap<>();
    private static boolean registered;
    private NativeDeferredFluids() {}

    /** Full XYZ identity; block Y is never packed into twelve bits. */
    record Point(int x,int y,int z) {
        static Point of(BlockPos p){return new Point(p.getX(),p.getY(),p.getZ());}
        BlockPos block(){return new BlockPos(x,y,z);}
        D6ChunkPos cube(){return new D6ChunkPos(x>>4,y>>4,z>>4);}
        boolean representable(){var c=cube();return D6ChunkPos.inRange(c.x())&&D6ChunkPos.inRange(c.y())&&D6ChunkPos.inRange(c.z());}
    }
    record Edge(Point source,Point target,String family,long createdAt) {
        Edge {
            Objects.requireNonNull(source);Objects.requireNonNull(target);Objects.requireNonNull(family);
            long distance=Math.abs((long)source.x-target.x)+Math.abs((long)source.y-target.y)+Math.abs((long)source.z-target.z);
            if(distance!=1||!source.representable()||!target.representable()||source.cube().equals(target.cube())||Identifier.tryParse(family)==null)throw new IllegalArgumentException("invalid fluid boundary identity");
        }
        Key key(){return new Key(source,target,family);}
    }
    record Key(Point source,Point target,String family) {}
    enum Offer { ADDED,COALESCED,FULL,READ_ONLY }
    record Source(boolean available,String family) {}
    interface Access {
        Source source(Point at);
        default boolean targetValid(Point at){return true;}
        boolean targetAvailable(Point at);
        boolean queueFuture(Point at,String family);
    }
    enum Attempt { WAIT,STALE,HANDED_OFF }
    record Pass(int attempted,int resumed,int stale) {}
    static Attempt attempt(Edge edge,Access access) {
        if(!access.targetValid(edge.target))return Attempt.STALE;
        Source source=access.source(edge.source);
        if(!source.available)return Attempt.WAIT;
        if(!Objects.equals(source.family,edge.family))return Attempt.STALE;
        if(!access.targetAvailable(edge.target))return Attempt.WAIT;
        return access.queueFuture(edge.source,edge.family)?Attempt.HANDED_OFF:Attempt.WAIT;
    }
    static Pass process(Pending pending,Access access,int budget) {
        int attempted=0,resumed=0,stale=0;
        for(var edge:pending.rotate(budget)) {
            attempted++;var outcome=attempt(edge,access);
            if(outcome==Attempt.WAIT)continue;
            pending.remove(edge);
            if(outcome==Attempt.STALE)stale++;else resumed++;
        }
        return new Pass(attempted,resumed,stale);
    }

    /** Bounded primitive metadata only; no World, section, callback or fluid-state reference. */
    static final class Pending extends PersistentState {
        private final LinkedHashMap<Key,Edge> entries=new LinkedHashMap<>();
        private NbtCompound unsupportedOriginal;
        synchronized Offer offer(Edge edge) {
            if(unsupportedOriginal!=null)return Offer.READ_ONLY;
            if(entries.containsKey(edge.key()))return Offer.COALESCED;
            if(entries.size()>=MAX_ENTRIES)return Offer.FULL;
            entries.put(edge.key(),edge);markDirty();return Offer.ADDED;
        }
        synchronized List<Edge> rotate(int budget) {
            var result=new ArrayList<Edge>();int count=Math.min(Math.max(0,budget),entries.size());
            for(int i=0;i<count;i++){var first=entries.pollFirstEntry();entries.put(first.getKey(),first.getValue());result.add(first.getValue());}
            return List.copyOf(result);
        }
        synchronized void remove(Edge edge){if(entries.remove(edge.key())!=null)markDirty();}
        synchronized int size(){return entries.size();}
        synchronized boolean contains(Edge edge){return entries.containsKey(edge.key());}
        synchronized boolean readOnly(){return unsupportedOriginal!=null;}
        static Pending read(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
            var result=new Pending();
            if(nbt.getInt("schema")!=1||!nbt.contains("entries",NbtElement.LIST_TYPE))return unsupported(nbt);
            var list=nbt.getList("entries",NbtElement.COMPOUND_TYPE);
            if(list.size()>MAX_ENTRIES||list.size()!=((NbtList)nbt.get("entries")).size())return unsupported(nbt);
            try {
                for(int i=0;i<list.size();i++) {
                    var p=list.getCompound(i);
                    for(String key:List.of("sx","sy","sz","tx","ty","tz"))if(!p.contains(key,NbtElement.INT_TYPE))return unsupported(nbt);
                    if(!p.contains("family",NbtElement.STRING_TYPE)||!p.contains("created",NbtElement.LONG_TYPE))return unsupported(nbt);
                    var edge=new Edge(new Point(p.getInt("sx"),p.getInt("sy"),p.getInt("sz")),new Point(p.getInt("tx"),p.getInt("ty"),p.getInt("tz")),p.getString("family"),p.getLong("created"));
                    result.entries.putIfAbsent(edge.key(),edge);
                }
            }catch(IllegalArgumentException invalid){return unsupported(nbt);}
            return result;
        }
        private static Pending unsupported(NbtCompound original){var result=new Pending();result.unsupportedOriginal=original.copy();return result;}
        @Override public synchronized NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
            if(unsupportedOriginal!=null)return unsupportedOriginal.copy();
            nbt.putInt("schema",1);var list=new NbtList();
            for(var e:entries.values()) {
                var p=new NbtCompound();p.putInt("sx",e.source.x);p.putInt("sy",e.source.y);p.putInt("sz",e.source.z);
                p.putInt("tx",e.target.x);p.putInt("ty",e.target.y);p.putInt("tz",e.target.z);p.putString("family",e.family);p.putLong("created",e.createdAt);list.add(p);
            }
            nbt.put("entries",list);return nbt;
        }
    }
    private static final class Binding {
        final Pending pending;
        boolean managed;
        int tick=Integer.MIN_VALUE,admissions;
        long added,coalesced,capRefused,budgetRefused,backpressureQueued,backpressureRefused,resumed,stale,unsupported;
        Binding(Pending existing){pending=existing==null?new Pending():existing;managed=existing!=null;}
        void begin(int currentTick){if(tick!=currentTick){tick=currentTick;admissions=0;}}
    }
    public record Snapshot(int pending,long added,long coalesced,long capRefused,long budgetRefused,long backpressureQueued,long backpressureRefused,long resumed,long stale,long unsupported) {}
    public static Snapshot snapshot(ServerWorld world) {
        if(!CubicAvailability.onWorldThread(world))return new Snapshot(0,0,0,0,0,0,0,0,0,0);
        var b=BINDINGS.get(world);return b==null?new Snapshot(0,0,0,0,0,0,0,0,0,0):new Snapshot(b.pending.size(),b.added,b.coalesced,b.capRefused,b.budgetRefused,b.backpressureQueued,b.backpressureRefused,b.resumed,b.stale,b.unsupported);
    }
    public static void register() {
        if(registered)return;registered=true;
        ServerTickEvents.END_SERVER_TICK.register(NativeDeferredFluids::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->BINDINGS.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->BINDINGS.keySet().removeIf(world->world.getServer()==server));
    }
    private static Binding binding(ServerWorld world) {
        return BINDINGS.computeIfAbsent(world,w->new Binding(w.getPersistentStateManager().get(TYPE,STATE_ID)));
    }
    private static boolean nativeOwner(ServerWorld world){return CubicAvailability.onWorldThread(world)&&CubicAvailability.backend(world)==CubicAvailability.Backend.NATIVE_CUBIC;}
    private static boolean boundary(BlockPos p){return (p.getX()&15)==0||(p.getX()&15)==15||(p.getY()&15)==0||(p.getY()&15)==15||(p.getZ()&15)==0||(p.getZ()&15)==15;}
    private static boolean validPosition(ServerWorld world,BlockPos p) {
        var cube=D6ChunkPos.ofBlock(p.getX(),p.getY(),p.getZ());
        return D6ChunkPos.inRange(cube.x())&&D6ChunkPos.inRange(cube.y())&&D6ChunkPos.inRange(cube.z())&&!world.isOutOfHeightLimit(p)&&world.getWorldBorder().contains(p);
    }
    static String family(FlowableFluid fluid){return Registries.FLUID.getId(fluid.getStill()).toString();}

    /** Observe an actual callback. Never directly simulates fluid or changes a block. */
    public static void observed(World w,BlockPos source,FluidState dispatched) {
        if(!(w instanceof ServerWorld world)||!boundary(source)||!nativeOwner(world))return;
        var access=WorldAccess.of(world);var loaded=access.readIfLoaded(source);if(loaded.isEmpty())return;
        Fluid current=loaded.get().getFluidState().getFluid();
        if(!(current instanceof FlowableFluid flowing)||!current.matchesType(dispatched.getFluid()))return;
        var b=binding(world);b.begin(world.getServer().getTicks());
        if(b.admissions>=ADMISSIONS_PER_TICK){b.budgetRefused++;backpressure(world,source,current,b);return;}
        b.admissions++;boolean refused=false;Point at=Point.of(source);String id=family(flowing);
        for(Direction direction:Direction.values()) {
            BlockPos target=source.offset(direction);Point to=Point.of(target);
            if(at.cube().equals(to.cube())||!validPosition(world,target)||access.isVolumeAvailable(to.cube()))continue;
            switch(b.pending.offer(new Edge(at,to,id,world.getTime()))) {
                case ADDED -> {
                    if(!b.managed){world.getPersistentStateManager().set(STATE_ID,b.pending);b.managed=true;}
                    b.added++;
                }
                case COALESCED -> b.coalesced++;
                case FULL -> {b.capRefused++;refused=true;}
                case READ_ONLY -> {b.unsupported++;refused=true;}
            }
        }
        if(refused)backpressure(world,source,current,b);
    }
    /** Already processed/current isTicking work never acknowledges a future handoff. */
    static boolean ensureFuture(BooleanSupplier queued,Runnable schedule) {
        if(queued.getAsBoolean())return true;
        schedule.run();return queued.getAsBoolean();
    }
    private static boolean scheduleFuture(ServerWorld world,BlockPos source,Fluid fluid,Binding binding) {
        int delay=fluid.getTickRate(world);if(delay<=0){binding.unsupported++;return false;}
        return ensureFuture(()->world.getFluidTickScheduler().isQueued(source,fluid),()->world.scheduleFluidTick(source,fluid,delay));
    }
    private static void backpressure(ServerWorld world,BlockPos source,Fluid current,Binding b) {
        // observed() has just validated the current loaded source/family on its owner thread.
        if(scheduleFuture(world,source,current,b))b.backpressureQueued++;else b.backpressureRefused++;
    }
    private static void tick(MinecraftServer server) {
        for(var world:server.getWorlds()) {
            if(!nativeOwner(world))continue;
            var b=binding(world);b.begin(server.getTicks());if(b.pending.readOnly())continue;
            var access=WorldAccess.of(world);
            var result=process(b.pending,new Access() {
                private Point checked;
                private Fluid current;
                public Source source(Point at) {
                    checked=at;current=null;BlockPos pos=at.block();
                    if(!validPosition(world,pos))return new Source(true,null);
                    var loaded=access.readIfLoaded(pos);if(loaded.isEmpty())return new Source(false,null);
                    current=loaded.get().getFluidState().getFluid();
                    return new Source(true,current instanceof FlowableFluid flowing?family(flowing):null);
                }
                public boolean targetValid(Point at){return validPosition(world,at.block());}
                public boolean targetAvailable(Point at){return access.isVolumeAvailable(at.cube());}
                public boolean queueFuture(Point at,String expected) {
                    // Only metadata queries run between this current-source check and handoff.
                    return at.equals(checked)&&current instanceof FlowableFluid flowing&&family(flowing).equals(expected)&&scheduleFuture(world,at.block(),current,b);
                }
            },ATTEMPTS_PER_TICK);
            b.resumed+=result.resumed;b.stale+=result.stale;
        }
    }
}
