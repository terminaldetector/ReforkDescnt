package com.terminaldetector.drmd.client.world;

import com.terminaldetector.drmd.world.profile.WorldProfileNetworking;
import com.terminaldetector.drmd.world.profile.WorldProfile;
import com.terminaldetector.drmd.world.profile.WorldProfileNetworking.ProfilePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class WorldProfileClient {
    private WorldProfileClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ProfilePayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().getNetworkHandler() == context.player().networkHandler) {
                WorldProfileNetworking.acceptServerProfile(payload.profile());
                var world = context.client().world;
                if (world != null && WorldProfile.isUngravity(world)) {
                    context.client().worldRenderer.reload();
                }
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WorldProfileNetworking.acceptServerProfile(null));
    }
}
