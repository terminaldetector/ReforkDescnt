package com.terminaldetector.drmd.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import java.util.HashMap;
import java.util.Map;

/** Cosmetic server-authoritative gravity load; never drives capture/launch physics. */
public final class WeaponLoadState {
    private static ClientWorld world;
    private static final Map<Integer,Float> LOADS=new HashMap<>();
    private WeaponLoadState() {}
    private static void checkWorld() {
        var current=MinecraftClient.getInstance().world;
        if(current!=world) { LOADS.clear(); world=current; }
    }
    public static void accept(int id,float load) {
        checkWorld(); if(!Float.isFinite(load)) return;
        if(load<=0) LOADS.remove(id); else LOADS.put(id,Math.clamp(load,0,1));
    }
    public static float get(int id) { checkWorld(); return LOADS.getOrDefault(id,0f); }
}
