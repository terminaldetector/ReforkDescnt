package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.projectile.ProjectileVisual;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayDeque;

/** Launch and contact art emitted from accepted gameplay events, including first-tick collisions. */
public final class ShotFxRenderer {
    private static final int MAX_EFFECTS = 256;
    private static final int RECOIL_TICKS = 8;
    private static final int DEBRIS_BUDGET = 36;
    private static final Vec3d EAST = new Vec3d(1, 0, 0);
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static final ArrayDeque<Flash> FLASHES = new ArrayDeque<>();
    private static final ArrayDeque<Flash> RECOIL = new ArrayDeque<>();
    private static ClientWorld world;
    private static long ticks;
    private static int muzzleEvents, impactEvents;
    private static int debrisThisTick;

    private record Flash(Vec3d position, Vec3d axis, int rgb, float scale, boolean impact,
                         ShotVisualProfile.Flash profile, long born) {}

    private ShotFxRenderer() {}

    public static int muzzleEvents() { return muzzleEvents; }
    public static int impactEvents() { return impactEvents; }

    private static void checkWorld() {
        var current = MinecraftClient.getInstance().world;
        if (world == current) return;
        FLASHES.clear();
        RECOIL.clear();
        world = current;
        ticks = 0;
        muzzleEvents = impactEvents = debrisThisTick = 0;
    }

    public static void add(ModNetworking.ShotFxPayload event) {
        checkWorld();
        if (world == null || !Double.isFinite(event.x()) || !Double.isFinite(event.y())
                || !Double.isFinite(event.z()) || !Float.isFinite(event.scale())) return;
        while (FLASHES.size() >= MAX_EFFECTS) FLASHES.removeFirst();

        var profile = ShotVisualProfile.of(event.visual());
        Vec3d position = new Vec3d(event.x(), event.y(), event.z());
        Vec3d axis = event.direction();
        axis = Double.isFinite(axis.lengthSquared()) ? axis.normalize() : Vec3d.ZERO;
        var flash = new Flash(position, axis, event.rgb(), Math.clamp(event.scale(), .1f, 4f),
                event.impact(), event.impact() ? profile.impact() : profile.muzzle(), ticks);
        FLASHES.addLast(flash);
        if (event.impact()) {
            impactEvents++;
            emitDebris(flash, event.visual().family(), event.surfaceRgb());
        } else {
            muzzleEvents++;
            while (RECOIL.size() >= 64) RECOIL.removeFirst();
            RECOIL.addLast(flash);
        }
    }

    private static void emitDebris(Flash flash, ProjectileVisual.Family family, int surfaceRgb) {
        if (surfaceRgb < 0 || flash.axis().lengthSquared() < .5 || debrisThisTick >= DEBRIS_BUDGET) return;
        if (family != ProjectileVisual.Family.BALLISTIC
                && family != ProjectileVisual.Family.ROCKET && family != ProjectileVisual.Family.DRILL) return;
        var client = MinecraftClient.getInstance();
        if (flash.position().squaredDistanceTo(client.gameRenderer.getCamera().getPos()) > 36 * 36) return;

        var color = new Vector3f(((surfaceRgb >> 16) & 255) / 255f, ((surfaceRgb >> 8) & 255) / 255f,
                (surfaceRgb & 255) / 255f);
        var dust = new DustParticleEffect(color, Math.min(.85f, flash.scale() * .52f));
        Vec3d start = flash.position().add(flash.axis().multiply(.06));
        Vec3d side = ShotGeometry.transverse(flash.axis(), UP);
        for (int i = -1; i <= 1 && debrisThisTick < DEBRIS_BUDGET; i++, debrisThisTick++) {
            Vec3d motion = flash.axis().multiply(.10).add(side.multiply(i * .075));
            world.addParticle(dust, start.x, start.y, start.z, motion.x, motion.y, motion.z);
        }
    }

    public static float recoilAt(Vec3d anchor, float delta) {
        float result = 0;
        for (var event : RECOIL) {
            if (event.position().squaredDistanceTo(anchor) >= .16) continue;
            float age = ticks - event.born() + delta;
            if (age >= 0 && age < RECOIL_TICKS) {
                float recoil = event.profile().recoil() * (float) Math.sin(Math.PI * age / RECOIL_TICKS);
                result = Math.max(result, recoil);
            }
        }
        return result;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            checkWorld();
            ticks++;
            debrisThisTick = 0;
            RECOIL.removeIf(event -> ticks - event.born() >= RECOIL_TICKS);
            FLASHES.removeIf(flash -> ticks - flash.born() >= flash.profile().ticks());
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (world == null || FLASHES.isEmpty() || context.matrixStack() == null || context.consumers() == null)
                return;
            var buffer = context.consumers().getBuffer(ShotRenderLayers.GLOW);
            Vec3d cameraPos = context.camera().getPos();
            var rotation = context.camera().getRotation();
            float tickDelta = context.tickCounter().getTickDelta(false);
            for (var flash : FLASHES) {
                Vec3d offset = flash.position().subtract(cameraPos);
                float age = ticks - flash.born() + tickDelta;
                float fade = Math.clamp(1 - age / flash.profile().ticks(), 0f, 1f);
                float bloom = 1 + (1 - fade) * flash.profile().bloom();
                float radius = FxVisibility.radius(offset.length(), flash.scale() * flash.profile().radius() * bloom);
                if (radius <= 0 || fade <= 0) continue;
                var matrices = context.matrixStack();
                matrices.push();
                try {
                    matrices.translate(offset.x, offset.y, offset.z);
                    if (flash.axis().lengthSquared() > .5) {
                        renderDirected(flash, matrices, buffer, offset.negate(), radius, fade);
                    } else {
                        matrices.multiply(rotation);
                        var entry = matrices.peek();
                        ShotGeometry.disc(buffer, entry, EAST, UP, radius, flash.rgb(), fade * .62f);
                        ShotGeometry.disc(buffer, entry, EAST, UP, radius * .44f,
                                ShotGeometry.brightCore(flash.rgb()), fade);
                    }
                } finally {
                    matrices.pop();
                }
            }
        });
    }

    private static void renderDirected(Flash flash, MatrixStack matrices, VertexConsumer buffer,
                                       Vec3d toEye, float radius, float fade) {
        Vec3d normal = flash.axis();
        Vec3d side = ShotGeometry.transverse(normal, toEye);
        Vec3d up = normal.crossProduct(side);
        float length = flash.profile().length() * flash.scale();
        int core = ShotGeometry.brightCore(flash.rgb());

        if (flash.impact()) {
            // A short surface flash sits just above the authoritative contact. Spall emerges
            // one-sided along its normal rather than lighting things behind the wall.
            matrices.translate(normal.x * .016, normal.y * .016, normal.z * .016);
            var entry = matrices.peek();
            ShotGeometry.disc(buffer, entry, side, up, radius, flash.rgb(), fade * .72f);
            ShotGeometry.ribbon(buffer, entry, normal, side, length, length, radius * .52f, radius * .12f,
                    core, fade * .72f);
            ShotGeometry.disc(buffer, entry, side, up, radius * .4f, core, fade);
        } else {
            var entry = matrices.peek();
            ShotGeometry.ribbon(buffer, entry, normal, side, 0, length, radius * .56f, radius,
                    flash.rgb(), fade * .76f);
            ShotGeometry.disc(buffer, entry, side, up, radius * .57f, core, fade);
        }
    }
}
