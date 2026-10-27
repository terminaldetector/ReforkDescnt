package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/** Loaded metadata queries only. A native API failure never falls back to a column or snapshot. */
public final class CubicAvailability {
    public enum Backend { STOCK, NATIVE_CUBIC, UNSUPPORTED }
    private static BooleanSupplier clientThread=()->false;
    private static boolean initialized,present;
    private static volatile boolean failed;
    private static Method isCubic,column,status,clientLoaded,serverCubic,nativeColumn;
    private static Class<?> dimension,skyExtension;
    private CubicAvailability() {}

    public static void bindClientThread(BooleanSupplier check){clientThread=check;}
    public static boolean onWorldThread(World world) {
        return world instanceof ServerWorld server ? server.getServer().isOnThread() : world.isClient && clientThread.getAsBoolean();
    }
    public static Backend backend(World world) {
        initialize();
        if(!present)return Backend.STOCK;
        if(failed)return Backend.UNSUPPORTED;
        try{return (Boolean)isCubic.invoke(null,world)?Backend.NATIVE_CUBIC:Backend.STOCK;}
        catch(ReflectiveOperationException | LinkageError failure){fail(failure);return Backend.UNSUPPORTED;}
    }
    public static boolean available(World world,D6ChunkPos cube) {
        if(!onWorldThread(world) || !withinHeight(world,cube))return false;
        long x=(long)cube.x()*16,z=(long)cube.z()*16,y=(long)cube.y()*16;
        if(Math.abs(x)>29_999_984L || Math.abs(z)>29_999_984L)return false;
        BlockPos min=new BlockPos((int)x,(int)y,(int)z),max=min.add(15,15,15);
        // Includes the backend's actual BlockPos packing, rather than assuming twelve Y bits.
        if(!min.equals(BlockPos.fromLong(min.asLong())) || !max.equals(BlockPos.fromLong(max.asLong())))return false;
        Backend mode=backend(world);
        if(mode==Backend.UNSUPPORTED)return false;
        var full=loadedColumn(world,cube.x(),cube.z());
        if(full==null)return false;
        if(mode==Backend.STOCK)return true;
        try {
            if(world.isClient) {
                if(clientLoaded==null)clientLoaded=Class.forName("dev.fallingcloud.bettercubicchunks.client.ClientCubes")
                    .getMethod("isCubeLoaded",World.class,BlockPos.class);
                return (Boolean)clientLoaded.invoke(null,world,min);
            }
            var sky=full.getChunkSkyLight();
            if(!skyExtension.isInstance(sky))return false;
            Object state=column.invoke(sky);
            return state!=null && "FULL".equals(String.valueOf(status.invoke(state,world.getSectionIndex((int)y))));
        }catch(ReflectiveOperationException | LinkageError failure){fail(failure);return false;}
    }
    public static boolean withinHeight(World world,D6ChunkPos cube) {
        long min=(long)cube.y()*16;
        return min>=world.getBottomY() && min+15<world.getTopY();
    }
    /** Existing ready data only: vanilla getWorldChunk and native columnAt do not join futures. */
    public static WorldChunk loadedColumn(World world,int x,int z) {
        if(!onWorldThread(world))return null;
        Backend mode=backend(world);if(mode==Backend.UNSUPPORTED)return null;
        if(world instanceof ServerWorld server) {
            if(mode==Backend.STOCK)return server.getChunkManager().getWorldChunk(x,z);
            try {
                Object cubic=serverCubic.invoke(server);
                Object loaded=cubic==null?null:nativeColumn.invoke(cubic,x,z);
                return loaded instanceof WorldChunk full?full:null;
            }catch(ReflectiveOperationException | LinkageError failure){fail(failure);return null;}
        }
        var loaded=world.getChunkManager().getChunk(x,z,ChunkStatus.FULL,false);
        return loaded instanceof WorldChunk full?full:null;
    }
    private static synchronized void initialize() {
        if(initialized)return;
        initialized=true;present=FabricLoader.getInstance().isModLoaded("bettercubicchunks");
        if(!present)return;
        try {
            dimension=Class.forName("dev.fallingcloud.bettercubicchunks.core.CubicDimensions");
            isCubic=dimension.getMethod("isCubic",World.class);
            skyExtension=Class.forName("dev.fallingcloud.bettercubicchunks.chunk.ISkyLightSourcesExt");
            column=skyExtension.getMethod("bcc$columnState");
            status=Class.forName("dev.fallingcloud.bettercubicchunks.chunk.CubicColumnState").getMethod("status",int.class);
            serverCubic=Class.forName("dev.fallingcloud.bettercubicchunks.server.ICubicServerLevel").getMethod("bcc$cubic");
            nativeColumn=Class.forName("dev.fallingcloud.bettercubicchunks.server.CubicServerLevel").getMethod("columnAt",int.class,int.class);
        }catch(ReflectiveOperationException | LinkageError failure){fail(failure);}
    }
    private static void fail(Throwable cause) {
        if(!failed)DescentMod.LOGGER.error("[drmd world access] unsupported native cube API; native queries remain unavailable",cause);
        failed=true;
    }
}
