package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayDeque;

/** Bounded, short-lived muzzle/impact events; never alter damage or projectile simulation. */
public final class ShotFxRenderer {
    private static final int MAX_EFFECTS = 256;
    private static final ArrayDeque<Flash> FLASHES = new ArrayDeque<>();
    private static final ArrayDeque<Flash> RECOIL = new ArrayDeque<>();
    private static ClientWorld world;
    private static long ticks;
    private static int muzzleEvents, impactEvents;
    private record Flash(Vec3d position, int rgb, float scale, boolean impact, long born) {}
    private ShotFxRenderer() {}

    public static int muzzleEvents() { return muzzleEvents; }
    public static int impactEvents() { return impactEvents; }

    private static void checkWorld() {
        var current = MinecraftClient.getInstance().world;
        if (world != current) {
            FLASHES.clear(); RECOIL.clear(); world = current; ticks = 0; muzzleEvents = impactEvents = 0;
        }
    }

    public static void add(ModNetworking.ShotFxPayload event) {
        checkWorld();
        if (world == null || !Double.isFinite(event.x()) || !Double.isFinite(event.y()) ||
                !Double.isFinite(event.z()) || !Float.isFinite(event.scale())) return;
        while (FLASHES.size() >= MAX_EFFECTS) FLASHES.removeFirst();
        FLASHES.addLast(new Flash(new Vec3d(event.x(), event.y(), event.z()), event.rgb(),
                Math.clamp(event.scale(), .1f, 4f), event.impact(), ticks));
        if (event.impact()) impactEvents++; else {
            muzzleEvents++;
            while(RECOIL.size() >= 64) RECOIL.removeFirst();
            RECOIL.addLast(new Flash(new Vec3d(event.x(),event.y(),event.z()),event.rgb(),event.scale(),false,ticks));
        }
    }

    public static float recoilAt(Vec3d anchor, float delta) {
        float result=0;
        for(var event:RECOIL) if(event.position().squaredDistanceTo(anchor)<.16) {
            float age=ticks-event.born()+delta;
            if(age>=0 && age<8) result=Math.max(result,(float)Math.sin(Math.PI*age/8));
        }
        return result;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            checkWorld(); ticks++;
            RECOIL.removeIf(event -> ticks-event.born()>=8);
            FLASHES.removeIf(flash -> ticks - flash.born() >= (flash.impact() ? 6 : 3));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (world == null || FLASHES.isEmpty() || context.matrixStack() == null || context.consumers() == null) return;
            var matrices = context.matrixStack();
            var buffer = context.consumers().getBuffer(ShotRenderLayers.GLOW);
            for (var flash : FLASHES) {
                Vec3d offset = flash.position().subtract(context.camera().getPos());
                float life = flash.impact() ? 6f : 3f;
                float age = ticks - flash.born() + context.tickCounter().getTickDelta(false);
                float fade = Math.clamp(1 - age / life, 0f, 1f);
                float radius = flash.scale() * (flash.impact() ? .48f : .24f) * (1 + (1 - fade) * .6f);
                matrices.push(); matrices.translate(offset.x, offset.y, offset.z);
                matrices.multiply(context.camera().getRotation());
                ProjectileRenderer.billboard(buffer, matrices.peek(), radius * 2, radius * 2,
                        0xff000000 | flash.rgb(), fade * .55f);
                ProjectileRenderer.billboard(buffer, matrices.peek(), radius * .5f, radius * .5f,
                        0xffeeeecc, fade);
                matrices.pop();
            }
        });
    }
}
