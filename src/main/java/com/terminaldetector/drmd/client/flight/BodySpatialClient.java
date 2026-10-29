package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Enroll actual tracked client entities, separately from the integrated/dedicated server's trees. */
public final class BodySpatialClient {
    private BodySpatialClient(){}
    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((e,w)->{if(e instanceof BlockBodyEntity b)BodySpatialIndex.track(b);});
        ClientEntityEvents.ENTITY_UNLOAD.register((e,w)->{if(e instanceof BlockBodyEntity b)BodySpatialIndex.untrack(b);});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->BodySpatialIndex.closeClients());
    }
}
