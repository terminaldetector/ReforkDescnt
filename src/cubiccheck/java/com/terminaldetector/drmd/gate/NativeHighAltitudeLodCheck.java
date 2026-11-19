package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.FlightBodyClient;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.planet.HorizonProjection;
import com.terminaldetector.drmd.client.planet.HorizonShader;
import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.BackupPromptScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A real nose-down, rolling flight through the far horizon and into resident surface meshes. */
public final class NativeHighAltitudeLodCheck {
    public static final boolean ENABLED = Boolean.getBoolean("drmd.gate.highAltitudeLod");
    private static final boolean TRACE = Boolean.getBoolean("drmd.gate.highAltitudeTrace");
    private static final Vec3d START = new Vec3d(40.5, 2272, 40.5);
    private static final Vec3d FORWARD = Vec3d.fromPolar(88f, 0);
    private static final Vec3d UP = new Vec3d(0, 1, 0)
        .subtract(FORWARD.multiply(FORWARD.y)).normalize();
    private static final int[] MARKS = {2150, 1640, 1020, 700, 450, 320, 155};
    private static final List<Map<String, Object>> FRAMES = new ArrayList<>();
    private static final List<Map<String, Object>> CAPTURES = new ArrayList<>();
    private static final List<Map<String, Object>> SERVER = new ArrayList<>();

    private static volatile UUID player;
    private static volatile boolean staged, armed, done;
    private static volatile String failure;
    private static volatile int serverSamples, teleports;
    private static int ticks, frames, mark, actualMoves, fastRollingFrames, fadedFrames, fullMeshFrames;
    private static int missingHorizonFrames, maximumMissingHorizonFrames, brakeTicks;
    private static int lastSampleFrame;
    private static long started;
    private static Vec3d actualMoveEnd, moveStart;
    private static float drawnOpacity, maximumSpeed;
    private static double maximumCameraError;
    private static int quads;
    private static String traceStage;
    private static final Set<String> TRACED = new java.util.HashSet<>();

    private NativeHighAltitudeLodCheck() {}

    public static void register() {
        started = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(NativeHighAltitudeLodCheck::client);
        ServerTickEvents.END_SERVER_TICK.register(NativeHighAltitudeLodCheck::server);
    }

    private static boolean full(World world, Vec3d at) {
        return WorldAccess.of(world).readIfLoaded(BlockPos.ofFloored(at)).isPresent();
    }

    private static List<Double> xyz(Vec3d position) {
        return List.of(position.x, position.y, position.z);
    }

    private static void server(MinecraftServer server) {
        if (done || player == null || failure != null) return;
        var p = server.getPlayerManager().getPlayer(player);
        if (p == null) return;
        var world = p.getServerWorld();
        var data = DescentPlayerData.get(p);
        if (!staged) {
            if (CubicAvailability.backend(world) != CubicAvailability.Backend.NATIVE_CUBIC
                || world.getBottomY() != -16384 || world.getHeight() != 32768) {
                failure = "the real signed native backend is required";
                return;
            }
            p.stopRiding();
            p.changeGameMode(GameMode.CREATIVE);
            p.teleport(world, START.x, START.y, START.z, Set.of(), 0, 88);
            teleports++;
            FlightSystem.enableWithoutMount(p, data);
            data.setShipAttitude((float) FORWARD.x, (float) FORWARD.y, (float) FORWARD.z,
                (float) UP.x, (float) UP.y, (float) UP.z);
            ModNetworking.syncPlayer(p, data);
            staged = true;
            return;
        }
        if (!armed) return;
        if (p.isSpectator() || p.noClip || !data.isEnabled() || !full(world, p.getPos())) {
            failure = "authoritative player left the loaded colliding flight path at " + p.getPos();
            return;
        }
        ++serverSamples;
        if (serverSamples % 12 == 0) synchronized (SERVER) {
            SERVER.add(Map.of("tick", server.getTicks(), "position", xyz(p.getPos()),
                "speed", data.getFlightVelocity().length(), "full", true));
        }
    }

    private static void client(MinecraftClient client) {
        if (done) return;
        if (failure != null || ++ticks >= 3800 || System.nanoTime() - started >= 245_000_000_000L) {
            finish(client, false, failure != null ? failure : "native pressure deadline");
            return;
        }
        if (client.currentScreen instanceof BackupPromptScreen prompt) {
            for (var child : prompt.children()) if (child instanceof ButtonWidget button
                && button.getMessage().getString().equals("I know what I'm doing!")) button.onPress();
        }
        if (client.player == null || client.world == null || client.getServer() == null) return;
        if (player == null) {
            if (!client.runDirectory.getName().equals("cubic-client-automation")
                || !DescentConfig.hybridRender || !DescentConfig.levelSky || !DescentConfig.planetFloor) {
                finish(client, false, "disposable world with all ordinary terrain passes required");
                return;
            }
            if (Boolean.getBoolean("drmd.gate.highAltitudePs1")) {
                DescentConfig.presentationProfile = PresentationProfile.PS1_HYBRID;
            }
            client.setScreen(null);
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            client.options.setPerspective(Perspective.FIRST_PERSON);
            player = client.player.getUuid();
        }
        DescentClient.markUserFlightChoice();
        if (!staged || !DescentClientState.enabled || !FlightBodyClient.active()) return;
        if (!armed) {
            if (!full(client.world, client.player.getPos()) || client.player.getPos().distanceTo(START) > 1
                || PlanetClientState.INSTANCE.currentMesh() == null) return;
            ShipAttitudeClient.acceptPortalFrame(client.player, FORWARD, UP);
            actualMoveEnd = client.player.getPos();
            armed = true;
        }
        if (!full(client.world, client.player.getPos())
            || client.player.getPos().squaredDistanceTo(actualMoveEnd) > 1e-12) {
            finish(client, false, "native player left a FULL cube or bypassed its observed collision move");
            return;
        }
        double y = client.player.getY();
        boolean descending = y > MARKS[MARKS.length - 1];
        client.options.forwardKey.setPressed(descending);
        DescentKeybinds.alwaysRun.setPressed(y > 490);
        DescentKeybinds.rollLeft.setPressed(descending && ticks % 160 < 80);
        DescentKeybinds.rollRight.setPressed(descending && ticks % 160 >= 80);
        if (!descending && ++brakeTicks > 160) finish(client, false, "resident terrain or last capture never stabilized");
    }

    public static void beforeMove(Entity entity, MovementType type, Vec3d request) {
        if (!armed || done || entity != MinecraftClient.getInstance().player) return;
        if (!Double.isFinite(request.lengthSquared()) || moveStart != null
            || entity.getPos().squaredDistanceTo(actualMoveEnd) > 1e-12) {
            failure = "unobserved, nested, or nonfinite native movement";
        }
        moveStart = entity.getPos();
    }

    public static void afterMove(Entity entity) {
        if (!armed || done || entity != MinecraftClient.getInstance().player || moveStart == null) return;
        actualMoveEnd = entity.getPos();
        if (actualMoveEnd.squaredDistanceTo(moveStart) > 1e-12) actualMoves++;
        moveStart = null;
    }

    public static void beforeFrame() {
        drawnOpacity = -1;
        quads = 0;
        traceStage = null;
    }

    public static void beforeHorizon() {
        if (!TRACE || !armed || done || OffscreenWorldView.busy()) return;
        var client = MinecraftClient.getInstance();
        var eye = client.gameRenderer.getCamera().getPos();
        String stage = eye.y >= 300 && eye.y <= 322 ? "bridge" : eye.y >= 125 && eye.y < 158 ? "near" : null;
        if (stage == null || !TRACED.add(stage)) return;
        traceStage = stage;
        traceImage(client, stage + "-before");
    }

    private static void traceImage(MinecraftClient client, String name) {
        int active = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        int[] names = {org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH,
            org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS, org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS};
        int[] saved = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            saved[i] = org.lwjgl.opengl.GL11.glGetInteger(names[i]);
            org.lwjgl.opengl.GL11.glPixelStorei(names[i], i == 0 ? 4 : 0);
        }
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var folder = client.runDirectory.toPath().resolve("high-altitude-passes");
            Files.createDirectories(folder);
            image.writeTo(folder.resolve(name + ".png"));
        } catch (Exception e) {
            failure = "native horizon draw readback: " + e;
        } finally {
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);
            RenderSystem.bindTexture(texture);
            for (int i = 0; i < names.length; i++) org.lwjgl.opengl.GL11.glPixelStorei(names[i], saved[i]);
        }
    }

    public static void horizon(Matrix4f view) {
        if (!armed || done || OffscreenWorldView.busy() || !view.isFinite()) return;
        var mesh = PlanetClientState.INSTANCE.currentMesh();
        if (mesh != null && RenderSystem.getShader() == HorizonShader.program()) {
            drawnOpacity = RenderSystem.getShaderColor()[3];
            quads = mesh.quads;
            if (traceStage != null) {
                var client = MinecraftClient.getInstance();
                var eye = client.gameRenderer.getCamera().getPos();
                var centre = mesh.originY;
                System.out.println("HIGH_ALTITUDE_DRAW " + traceStage + " eye=" + eye + " originY=" + centre
                    + " inner=" + mesh.innerRadius + " reach=" + mesh.reach + " opacity=" + drawnOpacity
                    + " quads=" + quads + " completed=" + client.worldRenderer.getCompletedChunkCount());
                traceImage(client, traceStage + "-after");
            }
        }
    }

    public static void rendered(float tickDelta) {
        var client = MinecraftClient.getInstance();
        if (!armed || done || client.player == null || client.world == null || client.currentScreen != null) return;
        frames++;
        var camera = client.gameRenderer.getCamera();
        var eye = camera.getPos();
        var basis = ShipAttitudeClient.get();
        double forward = basis.forward().dotProduct(FORWARD);
        Vector3f cameraForward = camera.getHorizontalPlane();
        double alignment = basis.forward().dotProduct(new Vec3d(cameraForward.x, cameraForward.y, cameraForward.z));
        double cameraError = eye.distanceTo(client.player.getLerpedPos(tickDelta));
        maximumCameraError = Math.max(maximumCameraError, cameraError);
        float speed = DescentClientState.speed * 20f;
        float rollSpeed = ShipAttitudeClient.rollSpeed();
        maximumSpeed = Math.max(maximumSpeed, speed);
        if (Math.abs(rollSpeed) >= 60 && speed >= 200 && eye.y > 1020) fastRollingFrames++;
        if (!Double.isFinite(cameraError) || cameraError > 3 || forward < .999 || alignment < .995) {
            finish(client, false, "camera left the interpolated player or actual downward attitude: " + cameraError
                + " forward=" + forward + " alignment=" + alignment);
            return;
        }
        float altitudeOpacity = HorizonProjection.groundOpacity(eye.y);
        int chunks = client.worldRenderer.getCompletedChunkCount();
        boolean needsBridge = eye.y >= 280 && eye.y <= 450 && chunks == 0;
        float minimum = needsBridge ? Math.max(.38f, altitudeOpacity) : altitudeOpacity;
        if (drawnOpacity >= 0 && (drawnOpacity + .0002f < minimum || drawnOpacity > 1 || quads <= 0)) {
            finish(client, false, "terrain opacity dropped below the altitude or missing-mesh minimum");
            return;
        }
        if (drawnOpacity > .025f && drawnOpacity < .975f) fadedFrames++;
        if (eye.y < 260 && chunks > 0) fullMeshFrames++;
        if (minimum > .08f && drawnOpacity < 0) {
            maximumMissingHorizonFrames = Math.max(maximumMissingHorizonFrames, ++missingHorizonFrames);
        } else missingHorizonFrames = 0;
        var row = Map.<String, Object>of("frame", frames, "eye", xyz(eye), "speed", speed,
            "rollSpeed", rollSpeed, "cameraDistance", cameraError, "alignment", alignment,
            "actualGroundAlpha", drawnOpacity, "minimumGroundAlpha", minimum, "lodQuads", quads,
            "residentCompletedMeshes", chunks);
        if (frames - lastSampleFrame >= 5) {
            FRAMES.add(row);
            lastSampleFrame = frames;
        }
        if (mark >= MARKS.length || eye.y > MARKS[mark] + 2 || (mark == MARKS.length - 1 && (chunks <= 0 || fullMeshFrames < 3))) return;
        try {
            var folder = client.runDirectory.toPath().resolve("high-altitude-lod-frames");
            Files.createDirectories(folder);
            try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(folder.resolve("y" + MARKS[mark] + ".png"));
            }
            CAPTURES.add(row);
            mark++;
        } catch (Exception e) {
            finish(client, false, e.toString());
        }
        if (mark == MARKS.length) {
            boolean pass = teleports == 1 && actualMoves >= 100 && serverSamples >= 100
                && fastRollingFrames >= 20 && fadedFrames >= 15 && fullMeshFrames >= 3
                && maximumMissingHorizonFrames <= 8;
            finish(client, pass, pass ? "real roll, horizon and resident mesh flight" : "unmet flight or draw predicates");
        }
    }

    private static void finish(MinecraftClient client, boolean pass, String description) {
        if (done) return;
        done = true;
        client.options.forwardKey.setPressed(false);
        DescentKeybinds.alwaysRun.setPressed(false);
        DescentKeybinds.rollLeft.setPressed(false);
        DescentKeybinds.rollRight.setPressed(false);
        try {
            var result = new LinkedHashMap<String, Object>();
            result.put("status", pass ? "PASS" : "FAIL");
            result.put("reason", description);
            result.put("profile", DescentConfig.presentationProfile);
            result.put("initialTeleports", teleports);
            result.put("nativeClientMoves", actualMoves);
            result.put("serverSamples", serverSamples);
            result.put("maximumSpeed", maximumSpeed);
            result.put("maximumCameraError", maximumCameraError);
            result.put("fastRollingFrames", fastRollingFrames);
            result.put("partialOpacityFrames", fadedFrames);
            result.put("fullMeshFrames", fullMeshFrames);
            result.put("maximumMissingHorizonFrames", maximumMissingHorizonFrames);
            result.put("captures", CAPTURES);
            result.put("frames", FRAMES);
            synchronized (SERVER) { result.put("server", List.copyOf(SERVER)); }
            Files.writeString(client.runDirectory.toPath().resolve("high-altitude-lod-outcome.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            System.out.println("HIGH_ALTITUDE_LOD " + result.get("status") + " " + description);
        } catch (Exception e) {
            e.printStackTrace();
        }
        client.scheduleStop();
    }
}
