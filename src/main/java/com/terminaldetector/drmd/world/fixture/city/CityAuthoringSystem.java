package com.terminaldetector.drmd.world.fixture.city;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;

/** Inert until an explicit operator command. No startup open/scan/world writes. */
public final class CityAuthoringSystem {
    private static final Map<MinecraftServer,Entry> SESSIONS=new IdentityHashMap<>();
    private static boolean registered;
    private static final class Entry{
        final CityRuntimeSession session;CityRuntimeSession.State notified=CityRuntimeSession.State.PREPARING;
        Entry(CityRuntimeSession session){this.session=session;}
    }
    private CityAuthoringSystem(){}
    /** Register manual commands/events only; never open a save or author at startup. */
    public static void register(){
        if(registered)return;registered=true;CityCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(CityAuthoringSystem::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var entry=SESSIONS.get(server);if(entry!=null)entry.session.ownerDisconnected(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var entry=SESSIONS.remove(server);if(entry!=null)entry.session.stop();});
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
    }
    private static Entry current(ServerPlayerEntity player){
        var entry=SESSIONS.get(player.getServerWorld().getServer());
        if(entry!=null&&!entry.session.ownerId().equals(player.getUuid()))throw new SecurityException("current city slot belongs to another operator");return entry;
    }
    public static CityRuntimeSession plan(ServerPlayerEntity player,long seed,CityBlueprint.Point anchor){
        return plan(player,seed,anchor,CityManifest.Kind.CITY);
    }
    public static CityRuntimeSession plan(ServerPlayerEntity player,long seed,CityBlueprint.Point anchor,CityManifest.Kind kind){
        var existing=current(player);if(existing!=null&&!existing.session.settled())throw new IllegalStateException("current city session is still active or closing");
        var session=CityRuntimeSession.plan(player,seed,anchor,kind);SESSIONS.put(player.getServerWorld().getServer(),new Entry(session));return session;
    }
    public static CityRuntimeSession inspect(ServerPlayerEntity player){
        var existing=current(player);if(existing!=null&&!existing.session.settled())return existing.session;
        var session=CityRuntimeSession.openExisting(player);SESSIONS.put(player.getServerWorld().getServer(),new Entry(session));return session;
    }
    public static Optional<CityRuntimeSession> active(ServerPlayerEntity player){var entry=current(player);return Optional.ofNullable(entry).map(e->e.session);}
    private static void tick(MinecraftServer server){
        var entry=SESSIONS.get(server);if(entry==null)return;entry.session.tick();var status=entry.session.inspect();
        if(status.state()!=entry.notified){
            entry.notified=status.state();
            if(status.state()!=CityRuntimeSession.State.RUNNING&&status.state()!=CityRuntimeSession.State.PREPARING){
                var player=server.getPlayerManager().getPlayer(entry.session.ownerId());
                if(player!=null)player.sendMessage(Text.literal("City: "+status.state()+"; "+status.detail()+"\n"+status.sterileLimit()),false);
            }
        }
    }
}
