package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.compat.DistantHorizonsCompat;
import com.terminaldetector.drmd.world.contraption.BodyVolumeLeases;
import com.terminaldetector.drmd.world.contraption.BodyVolumePlan;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Isolated upstream LOD proof using only ordinary writes to real cubic-world blocks. */
public final class NativeBdhCubicCheck {
    public static final boolean ENABLED = Boolean.getBoolean("drmd.gate.bdhCubic");
    private static final boolean COLD_READER = Boolean.getBoolean("drmd.gate.bdhColdReader");
    private static final boolean VISIT_FIXTURE = Boolean.getBoolean("drmd.gate.bdhVisitFixture");
    private static final long FIXTURE_DAY_TIME = 6000;
    private static final BlockPos LOWER = new BlockPos(8, 2150, 120);
    private static final BlockPos UPPER = new BlockPos(8, 2229, 120);
    private static final BodyVolumePlan.Column TEST_COLUMN = new BodyVolumePlan.Column(0, 7, 132, 139);
    private static final Object INTEREST = new Object();
    private static final List<Map<String, Object>> frames = new ArrayList<>();
    private static boolean started, done;
    private static volatile boolean committed, writing, loading, released;
    private static volatile int fullPieceSections;
    private static boolean releaseQueued;
    private static boolean fixtureReturnQueued;
    private static volatile boolean fixtureReturned;
    private static java.util.UUID fixturePlayer;
    private static volatile int setupTeleports;
    private static volatile Map<String,Object> handoffAdmission = Map.of();
    private static volatile String serverStatus = "awaiting world";
    private static Method describe;
    private static NativeBdhCubicMaterial materialProbe;
    private static CompletableFuture<List<String>> materialRead;
    private static List<String> admittedMaterials = List.of();
    private static int ticks, stable, phase, lowerMax, upperMax;
    private static long deadline, nextReadback;
    private static String lower = "", upper = "";
    private static Map<String, Object> finalView = Map.of();

    private NativeBdhCubicCheck() {}

    public static void register() {
        deadline = System.nanoTime() + 260_000_000_000L;
        ClientTickEvents.END_CLIENT_TICK.register(NativeBdhCubicCheck::tick);
    }

    private static void tick(MinecraftClient client) {
        if (done) return;
        if (System.nanoTime() > deadline) {
            finish(client, "FAIL", "deadline: " + serverStatus);
            return;
        }
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt) {
            for (var child : prompt.children()) {
                if (child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                        && button.getMessage().getString().equals("I know what I'm doing!")) button.onPress();
            }
        }
        if (client.world == null || client.player == null || client.getServer() == null) return;
        DescentClient.markUserFlightChoice();
        try {
            if (!started) {
                if (!client.runDirectory.getName().equals("cubic-client-automation")
                        || !DistantHorizonsCompat.isPresent()) {
                    finish(client, "FAIL", "isolated upstream client required");
                    return;
                }
                if (COLD_READER && VISIT_FIXTURE) {
                    finish(client, "FAIL", "cold reader and fresh visit are distinct cases");
                    return;
                }
                describe = Class.forName("com.seibel.distanthorizons.core.level.CubicDebugProbe")
                    .getMethod("describe", int.class, int.class, int.class);
                if (VISIT_FIXTURE) materialProbe = new NativeBdhCubicMaterial();
                started = true;
                client.options.hudHidden = true;
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.getToastManager().clear();
                client.options.getViewDistance().setValue(4);
                client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                DescentConfig.hud = false;
                DescentConfig.planetFloor = false; // External LOD visibility must stand on its own.
                var uuid = client.player.getUuid();
                fixturePlayer = uuid;
                client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(uuid);
                    FlightSystem.disable(player, DescentPlayerData.get(player));
                    player.changeGameMode(GameMode.CREATIVE);
                    player.getAbilities().flying = true;
                    player.sendAbilitiesUpdate();
                    // Ordinary player interest first admits the exact vanilla-column accessor used by BCC.
                    player.teleport(player.getServerWorld(), 8.5, 2183, VISIT_FIXTURE ? 120.5 : 8.5, Set.of(), 0, 0);
                    setupTeleports++;
                    var world = player.getServerWorld();
                    // Reference daylight makes the fixed colour thresholds independent of donor time.
                    world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
                    world.setTimeOfDay(FIXTURE_DAY_TIME);
                    world.setChunkForced(0, 7, true);
                    if (!BodyVolumeLeases.requestNativeInterest(world, INTEREST, TEST_COLUMN)) {
                        serverStatus = "native test interest refused";
                        return;
                    }
                    serverStatus = "awaiting eight FULL sections of both upstream 64-block pieces";
                    writing = true;
                });
                return;
            }
            ticks++;
            if (!committed && writing && !loading && ticks % 20 == 0) {
                loading = true;
                client.getServer().execute(() -> {
                    try {
                        var world = client.getServer().getOverworld();
                        var access = WorldAccess.of(world);
                        var player = client.getServer().getPlayerManager().getPlayer(fixturePlayer);
                        if (player == null) return;
                        var vanillaColumn = world.getChunkManager().getWorldChunk(0,7);
                        var nativeColumn = com.terminaldetector.drmd.world.access.CubicAvailability.loadedColumn(world,0,7);
                        boolean sameColumn = vanillaColumn != null && vanillaColumn == nativeColumn;
                        handoffAdmission = Map.of("vanillaPresent",vanillaColumn!=null,"nativePresent",nativeColumn!=null,
                            "sameIdentity",sameColumn,"vanillaIdentity",System.identityHashCode(vanillaColumn),
                            "nativeIdentity",System.identityHashCode(nativeColumn),
                            "serverPlayer",List.of(player.getX(),player.getY(),player.getZ()));
                        if (VISIT_FIXTURE && !sameColumn) {
                            serverStatus = "awaiting actual vanilla/native column identity at ordinary fixture visit";
                            return;
                        }
                        // BCC copies complete groups of four native sections, not individual edited cubes.
                        // The lower marker is CY134, but its upstream piece also requires CY132/133.
                        int ready = 0;
                        for (int cy = TEST_COLUMN.minY(); cy <= TEST_COLUMN.maxY(); cy++) {
                            if (access.readIfLoaded(new BlockPos(8, cy * 16 + 8, 120)).isPresent()) ready++;
                        }
                        fullPieceSections = ready;
                        if (ready != 8) return;
                        for (var pos : List.of(LOWER, UPPER)) {
                            var state = (pos.equals(LOWER) ? Blocks.RED_WOOL : Blocks.LIME_WOOL).getDefaultState();
                            for (int x = -5; x <= 5; x++) for (int y = -3; y <= 3; y++) for (int z = -1; z <= 1; z++) {
                                var cell = pos.add(x, y, z);
                                // A cold reader verifies the donor's real cells and never authors replacements.
                                if (!COLD_READER) world.setBlockState(cell, state, Block.NOTIFY_ALL);
                                if (!access.readIfLoaded(cell).orElseThrow().equals(state)) {
                                    serverStatus = "authored voxel did not persist in its native cube at " + cell;
                                    return;
                                }
                            }
                        }
                        // Keep only this test-owned range until the actual asynchronous upstream
                        // database/render upload acknowledges both pieces. No direct DH data injection.
                        serverStatus = (COLD_READER ? "462 existing donor cells verified with ZERO writes" : "462 real cells written and verified")
                            + "; eight FULL sections retained for upstream admission";
                        committed = true;
                    } finally {
                        loading = false;
                    }
                });
                return;
            }
            if (!committed || Math.abs(client.player.getY() - 2183) > 2) return;
            if (VISIT_FIXTURE && !fixtureReturned) {
                awaitFixtureMaterials(client);
                return;
            }
            client.player.setYaw(0);
            client.player.setPitch(phase == 0 ? 0 : (phase == 1 ? 12 : -12));
            if (ticks % 40 != 0) return;
            lower = describe(LOWER);
            upper = describe(UPPER);
            System.out.println("BDH_LOWER " + lower.replace('\n', ' '));
            System.out.println("BDH_UPPER " + upper.replace('\n', ' '));
            if (!releaseQueued && drawn(lower) && drawn(upper)) {
                releaseQueued = true;
                client.getServer().execute(() -> {
                    var world = client.getServer().getOverworld();
                    BodyVolumeLeases.releaseNativeInterest(world, INTEREST);
                    world.setChunkForced(0, 7, false);
                    serverStatus = "both real upstream pieces admitted; own interest released before distant pixel checks";
                    released = true;
                });
            }
        } catch (ReflectiveOperationException | CompletionException failure) {
            finish(client, "FAIL", failure.toString());
        }
    }

    private static void awaitFixtureMaterials(MinecraftClient client) throws ReflectiveOperationException {
        if (fixtureReturnQueued || ticks % 40 != 0) return;
        if (materialRead != null) {
            if (!materialRead.isDone()) return;
            admittedMaterials = materialRead.join();
            materialRead = null;
            serverStatus = "waiting at native fixture for actual upstream material: " + admittedMaterials;
            if (admittedMaterials.equals(List.of("minecraft:red_wool", "minecraft:lime_wool"))) {
                fixtureReturnQueued = true;
                client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(fixturePlayer);
                    if (player == null) return;
                    player.teleport(player.getServerWorld(), 8.5, 2183, 8.5, Set.of(), 0, 0);
                    setupTeleports++;
                    fixtureReturned = true;
                });
                return;
            }
        }
        materialRead = materialProbe.read(LOWER, UPPER);
    }

    private static String describe(BlockPos pos) throws ReflectiveOperationException {
        return (String) describe.invoke(null, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean drawn(String sample) {
        return sample.startsWith("cubic=true") && sample.lines().anyMatch(line ->
            line.contains("rendering=true") && line.contains("uploaded=true") && line.contains("noData=false"));
    }

    public static void rendered() {
        if (!ENABLED || done || !committed || !released || !drawn(lower) || !drawn(upper)) return;
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.world.getTimeOfDay() != FIXTURE_DAY_TIME) return;
        var access = WorldAccess.of(client.world);
        boolean lowerNative = access.readIfLoaded(LOWER).isPresent();
        boolean upperNative = access.readIfLoaded(UPPER).isPresent();
        var camera = client.gameRenderer.getCamera();
        var point = camera.getPos();
        if (finalView.isEmpty()) {
            try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(client.runDirectory.toPath().resolve("first-two-upstream-uploads.png"));
            } catch (Exception failure) { finish(client, "FAIL", failure.toString());return; }
        }
        finalView = Map.of("nativeDistance",client.options.getClampedViewDistance(),"screenOpen",client.currentScreen!=null,
            "lowerNative",lowerNative,"upperNative",upperNative,"camera",List.of(point.x,point.y,point.z),
            "yaw",camera.getYaw(),"pitch",camera.getPitch(),"phase",phase,"dayTime",client.world.getTimeOfDay());
        if (client.currentScreen != null || client.options.getClampedViewDistance() != 4 || lowerNative || upperNative) return;
        if (Math.abs(point.y - 2184.62) > 2 || Math.abs(point.z - 8.5) > 2) return;
        float pitch = phase == 0 ? 0 : (phase == 1 ? 12 : -12);
        if (Math.abs(camera.getPitch() - pitch) > 0.1 || Math.abs(camera.getYaw()) > 0.1) return;
        long now = System.nanoTime();
        if (now < nextReadback) return;
        nextReadback = now + 160_000_000;
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            int red = 0, green = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                    int color = image.getColor(x, y);
                    int r = color & 255, g = color >>> 8 & 255, b = color >>> 16 & 255;
                    if (y > image.getHeight() / 3 && r > 75 && r > g * 1.8 && r > b * 1.6) red++;
                    if (y < image.getHeight() * 2 / 3 && g > 64 && g > r * 1.3 && g > b * 1.35) green++;
                }
            }
            lowerMax = Math.max(lowerMax, red);
            upperMax = Math.max(upperMax, green);
            if ((phase != 2 && red < 25) || (phase != 1 && green < 25)) {
                stable = 0;
                return;
            }
            if (++stable < 3) return;
            String name = List.of("both-cubic-lods", "lower-cube", "upper-cube").get(phase);
            image.writeTo(client.runDirectory.toPath().resolve(name + ".png"));
            frames.add(Map.of("name", name, "lowerRed", red, "upperGreen", green,
                "yaw", camera.getYaw(), "pitch", camera.getPitch(), "nativeView", 4, "dayTime", client.world.getTimeOfDay()));
            if (++phase == 3) finish(client, "PASS", "separate native cube uploads and rendered markers beyond the vanilla view");
            else stable = 0;
        } catch (Exception failure) {
            finish(client, "FAIL", failure.toString());
        }
    }

    private static void finish(MinecraftClient client, String outcome, String reason) {
        if (done) return;
        done = true;
        try {
            var result = Map.of("status", outcome, "reason", reason, "server", serverStatus, "lower", lower,
                "upper", upper, "frames", List.copyOf(frames), "redMax", lowerMax, "greenMax", upperMax, "view", finalView,
                "interest", Map.of("minSectionY", 132, "maxSectionY", 139, "fullBeforeWrites", fullPieceSections,
                    "releasedBeforePixels", released, "coldReader", COLD_READER,
                    "visitFixture",VISIT_FIXTURE,"setupTeleports",setupTeleports,"handoffAdmission",handoffAdmission,
                    "admittedMaterials", admittedMaterials));
            Files.writeString(client.runDirectory.toPath().resolve("bdh-cubic-outcome.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        if (client.getServer() != null) client.getServer().execute(() -> {
            var world = client.getServer().getOverworld();
            BodyVolumeLeases.releaseNativeInterest(world, INTEREST);
            world.setChunkForced(0, 7, false);
        });
        client.scheduleStop();
    }
}
