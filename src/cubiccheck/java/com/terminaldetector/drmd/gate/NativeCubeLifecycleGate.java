package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

/** One explicit 60s test ticket expires; the subscription itself must not keep the cube alive. */
public final class NativeCubeLifecycleGate {
    private static final D6ChunkPos CUBE=new D6ChunkPos(0,143,0);
    public static void register() {
        var events=new ArrayList<CubeLifecycle.Event>();
        CubeLifecycle.Subscription[] subscription=new CubeLifecycle.Subscription[1];int[] ticks={0};boolean[] loaded={false},done={false};
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            subscription[0]=WorldAccess.of(server.getOverworld()).watchCube(CUBE,events::add).orElseThrow();
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"bcc load 8 2300 8 0");
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(done[0])return;
            if(++ticks[0]>2600)throw new IllegalStateException("Native watched cube did not load then unload after its one test ticket: "+events);
            boolean available=WorldAccess.of(server.getOverworld()).isVolumeAvailable(CUBE);
            if(available)loaded[0]=true;
            if(!loaded[0] || available)return;
            boolean entered=events.stream().anyMatch(e->e.change()==CubeLifecycle.Change.AVAILABLE);
            boolean exited=false;
            for(int i=1;i<events.size();i++)if(events.get(i).change()==CubeLifecycle.Change.UNAVAILABLE
                && events.subList(0,i).stream().anyMatch(e->e.change()==CubeLifecycle.Change.AVAILABLE))exited=true;
            if(!entered || !exited)return;
            if(WorldAccess.of(server.getOverworld()).readIfLoaded(new net.minecraft.util.math.BlockPos(8,2150,8)).isEmpty())
                throw new IllegalStateException("Vertical unload test lost the retained parent column's near marker");
            subscription[0].close();done[0]=true;
            try {
                Files.writeString(FabricLoader.getInstance().getGameDir().resolve("native-cube-lifecycle-ok.json"),
                    "{\"status\":\"OBSERVED_NATIVE_LOAD_UNLOAD_PASS\",\"cube\":[0,143,0],\"parentColumnRetained\":true,\"singleExternalTicket\":true,\"observerTickets\":0,\"ticks\":"+ticks[0]+",\"events\":"+events.size()+"}\n");
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
            System.out.println("CUBIC_LIFECYCLE_PASS ticks="+ticks[0]+" events="+events);
        });
    }
}
