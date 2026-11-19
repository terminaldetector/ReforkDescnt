package com.terminaldetector.drmd.world.cubic;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.d6.D6Cube;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only compatibility for historical drmd/cubes snapshots. Live world authority is
 * WorldAccess/native cubes; no column capture, automatic cache write or terrain fallback exists. */
public final class CubicWorldSystem {
    private static final int MAX_SPACES=16,MAX_PENDING_READS=64;
    private static final Map<ServerWorld,Space> SPACES=new HashMap<>();
    private static final class Space {
        final Path root;
        final ExecutorService io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_PENDING_READS),r->{var t=new Thread(r,"drmd-old-cubes-read");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        Space(Path root){this.root=root;}
        Path path(D6ChunkPos pos){pos.asLong();return root.resolve(pos.x()+"_"+pos.y()+"_"+pos.z()+".nbt");}
    }
    private CubicWorldSystem(){}
    /** Compatibility callback only. Historical files never claim to follow current terrain. */
    @Deprecated public static void changed(ServerWorld world,BlockPos pos){}
    /** The old cubicSnapshots opt-in cannot reactivate obsolete column emulation. */
    @Deprecated public static void tick(MinecraftServer server){}
    /** Explicit historical read only. The caller is on the world owner thread; the worker
     * captures just a path and never reads/loads/tickets any world cube or retains a World. */
    public static CompletableFuture<D6Cube<BlockState>> readSaved(ServerWorld world,D6ChunkPos pos){
        if(!world.getServer().isOnThread())return CompletableFuture.failedFuture(new IllegalStateException("historical cube reads require owner-thread admission"));
        try{pos.asLong();}catch(IllegalArgumentException invalid){return CompletableFuture.failedFuture(invalid);}
        Space space=SPACES.get(world);
        if(space==null){
            if(SPACES.size()>=MAX_SPACES)return CompletableFuture.failedFuture(new RejectedExecutionException("historical cube space budget"));
            space=new Space(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("drmd/cubes")
                .resolve(world.getRegistryKey().getValue().getNamespace()).resolve(world.getRegistryKey().getValue().getPath()));
            SPACES.put(world,space);
        }
        final Path file;
        try{file=space.path(pos);}catch(IllegalArgumentException invalid){return CompletableFuture.failedFuture(invalid);}
        try{return CompletableFuture.supplyAsync(()->{
            try{return Files.isRegularFile(file)?CubicBlockCodec.read(NbtIo.readCompressed(file,NbtSizeTracker.of(2*1024*1024))):null;}
            catch(Exception failure){throw new CompletionException(failure);}
        },space.io);}catch(RejectedExecutionException full){return CompletableFuture.failedFuture(full);}
    }
    /** There are no emulated live terrain cubes; historical reads are never inserted into one. */
    public static int residentCount(ServerWorld world){return 0;}
    public static void close(){
        var spaces=List.copyOf(SPACES.values());SPACES.clear();
        for(var space:spaces)space.io.shutdown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        for(var space:spaces)try{
            long remaining=Math.max(0,deadline-System.nanoTime());
            if(!space.io.awaitTermination(remaining,TimeUnit.NANOSECONDS))DescentMod.LOGGER.error("Historical cube reads still completing at shared shutdown timeout");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
