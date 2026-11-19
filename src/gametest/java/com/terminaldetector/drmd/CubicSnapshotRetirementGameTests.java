package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.world.DrmdServerConfig;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.cubic.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtIo;
import net.minecraft.test.*;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Retire obsolete capture only AFTER actual native parity. No old/user cache is removed by main. */
public final class CubicSnapshotRetirementGameTests implements FabricGameTest {
    private static Path root(TestContext c){var w=c.getWorld();return w.getServer().getSavePath(WorldSavePath.ROOT).resolve("drmd/cubes").resolve(w.getRegistryKey().getValue().getNamespace()).resolve(w.getRegistryKey().getValue().getPath());}
    private static Set<String> files(Path root,D6ChunkPos centre){
        try{if(!Files.exists(root))return Set.of();try(var stream=Files.walk(root)){return stream.filter(Files::isRegularFile).map(p->root.relativize(p).toString()).filter(name->wantedFile(name,centre)).collect(java.util.stream.Collectors.toSet());}}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static boolean wantedFile(String name,D6ChunkPos centre){
        String[] parts=name.replace(".nbt","").split("_");if(parts.length!=3||!name.endsWith(".nbt"))return false;
        try{return Math.abs((long)Integer.parseInt(parts[0])-centre.x())<=2&&Math.abs((long)Integer.parseInt(parts[1])-centre.y())<=2&&Math.abs((long)Integer.parseInt(parts[2])-centre.z())<=2;}
        catch(NumberFormatException unrelated){return false;}
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="retired-column-capture",tickLimit=80)
    public void oldOptInCannotRecreateTheRemovedColumnCaptureDuringRealServerTicks(TestContext c){
        var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(c.getAbsolutePos(new BlockPos(8,4,8)).toCenterPos());
        c.assertTrue(c.getWorld().getPlayers().contains(p),"actual world player list contains the fixture mock required by the old capture loop");
        var centre=D6ChunkPos.ofBlock(p.getBlockX(),p.getBlockY(),p.getBlockZ());
        boolean previous=DrmdServerConfig.cubicSnapshots;var before=files(root(c),centre);long clock=c.getWorld().getTime();DrmdServerConfig.cubicSnapshots=true;
        c.runAtTick(24,()->{
            try{
                c.assertTrue(c.getWorld().getTime()>=clock+20,"actual registered server lifecycle has advanced; no direct tick/worker invocation");
                c.assertTrue(CubicWorldSystem.residentCount(c.getWorld())==0,"there is no emulated resident terrain");
                c.assertTrue(files(root(c),centre).equals(before),"old properties key cannot author shadow snapshot files after one full flush period");
            }finally{DrmdServerConfig.cubicSnapshots=previous;p.discard();}
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="historical-cube-read",tickLimit=80)
    public void explicitSignedHistoricalFilesStayReadableAndByteIdenticalWithoutLoadingTerrain(TestContext c){
        var w=c.getWorld();var high=D6ChunkPos.ofBlock(2_000_000,2301,-2_000_000);var low=D6ChunkPos.ofBlock(2_000_000,-1795,-2_000_000);
        c.assertTrue(high.asLong()!=low.asLong(),"full signed cache addresses stay distinct");
        var access=WorldAccess.of(w);c.assertTrue(!access.isVolumeAvailable(high)&&!access.isVolumeAvailable(low),"no live volume may be substituted for historical data");
        var a=root(c).resolve(high.x()+"_"+high.y()+"_"+high.z()+".nbt");var b=root(c).resolve(low.x()+"_"+low.y()+"_"+low.z()+".nbt");
        final byte[] beforeA,beforeB;
        try{
            c.assertTrue(!Files.exists(a)&&!Files.exists(b),"disposable authored compatibility files never overwrite existing cache");Files.createDirectories(a.getParent());
            var ca=new D6Cube<>(Blocks.AIR.getDefaultState());ca.set(1,2,3,Blocks.GOLD_BLOCK.getDefaultState());
            var cb=new D6Cube<>(Blocks.AIR.getDefaultState());cb.set(1,2,3,Blocks.IRON_BLOCK.getDefaultState());
            NbtIo.writeCompressed(CubicBlockCodec.write(ca),a);NbtIo.writeCompressed(CubicBlockCodec.write(cb),b);beforeA=Files.readAllBytes(a);beforeB=Files.readAllBytes(b);
        }catch(Exception e){throw new IllegalStateException(e);}
        int loaded=w.getChunkManager().getLoadedChunkCount();var fa=CubicWorldSystem.readSaved(w,high);var fb=CubicWorldSystem.readSaved(w,low);
        c.assertTrue(w.getChunkManager().getLoadedChunkCount()==loaded,"read admission cannot synchronously load a column");
        await(c,()->fa.isDone()&&fb.isDone(),()->{
                try{
                    c.assertTrue(fa.join().get(1,2,3).isOf(Blocks.GOLD_BLOCK)&&fb.join().get(1,2,3).isOf(Blocks.IRON_BLOCK),"paired signed content survives the original palette codec");
                    c.assertTrue(Arrays.equals(beforeA,Files.readAllBytes(a))&&Arrays.equals(beforeB,Files.readAllBytes(b)),"main historical reads never migrate or rewrite stored files");
                    c.assertTrue(!access.isVolumeAvailable(high)&&!access.isVolumeAvailable(low)&&w.getChunkManager().getWorldChunk(high.x(),high.z())==null,"no cache→live world fallback or deferred terrain load");
                }catch(Exception e){throw new IllegalStateException(e);}finally{try{Files.deleteIfExists(a);Files.deleteIfExists(b);}catch(Exception e){throw new IllegalStateException(e);}}
            });
    }
    private static void await(TestContext c,java.util.function.BooleanSupplier ready,Runnable verify){
        c.runAtTick(c.getTick()+1,()->{
            if(!ready.getAsBoolean()){
                c.assertTrue(c.getTick()<60,"bounded actual historical IO completion");await(c,ready,verify);return;
            }
            verify.run();c.complete();
        });
    }
}
