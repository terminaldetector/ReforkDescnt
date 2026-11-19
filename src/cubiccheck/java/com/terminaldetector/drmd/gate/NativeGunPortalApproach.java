package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.portal.PortalSeeThroughRenderer;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Drives into a pair already placed by the real projector mouse and packet path. */
final class NativeGunPortalApproach {
    private static final Vec3d NORTH = new Vec3d(0, 0, -1);
    private static final Vec3d BANK = new Vec3d(Math.sin(Math.PI / 9), Math.cos(Math.PI / 9), 0);
    private final List<ServerSample> server = new ArrayList<>();
    private final List<Map<String, Object>> frames = new ArrayList<>();
    private final boolean flight = Boolean.getBoolean("drmd.gate.gunFlight");
    private volatile UUID actor;
    private volatile ServerSample baseline;
    private volatile ServerSample latest;
    private volatile ServerSample arrival;
    private volatile int crossings;
    private BlockPos source;
    private BlockPos target;
    private Quaternionf approachRotation;
    private boolean aligned;
    private boolean held;
    private int ticks;
    private int nearFrames;
    private int synchronizedTicks;
    private double maximumApproachAngle;

    private record ServerSample(int tick, List<Double> pos, List<Double> velocity, List<Double> forward,
                                List<Double> up, int epoch, boolean flight) {}

    void start(MinecraftClient client, BlockPos source, BlockPos target) {
        this.source = source;
        this.target = target;
        UUID playerId = client.player.getUuid();
        client.getServer().execute(() -> {
            var p = client.getServer().getPlayerManager().getPlayer(playerId);
            var data = DescentPlayerData.get(p);
            if (flight) {
                FlightSystem.enableWithoutMount(p, data);
                data.setFlightVelocity(Vec3d.ZERO);
                data.setShipAttitude(0, 0, -1, (float) BANK.x, (float) BANK.y, 0);
                ModNetworking.syncPlayer(p, data);
            }
            actor = playerId;
        });
    }

    void observe(MinecraftServer game) {
        if (actor == null) return;
        var p = game.getPlayerManager().getPlayer(actor);
        if (p == null) return;
        var data = DescentPlayerData.get(p);
        var sample = new ServerSample(game.getTicks(), xyz(p.getPos()),
                xyz(flight ? data.getFlightVelocity().multiply(.05) : p.getVelocity()),
                xyz(flight ? data.shipForward(p) : p.getRotationVec(1)), xyz(data.shipUp(p)),
                data.portalEpoch(), data.isEnabled());
        if (baseline == null) baseline = sample;
        if (sample.epoch != (latest == null ? baseline.epoch : latest.epoch)) {
            if (arrival == null) arrival = sample;
            crossings++;
        }
        latest = sample;
        synchronized (server) {
            if (server.size() < 320) server.add(sample);
        }
    }

    boolean tick(MinecraftClient client) {
        if (++ticks > 250) return finish(client, "Timed out approaching the linked gun panels");
        if (client.currentScreen != null) return finish(client, "A screen interrupted actual approach input");
        var initial = baseline;
        if (initial == null || DescentClientState.enabled != flight) return false;
        if (!aligned) {
            client.player.setYaw(180);
            client.player.setPitch(0);
            client.player.prevYaw = 180;
            client.player.prevPitch = 0;
            if (flight) ShipAttitudeClient.acceptPortalFrame(client.player, NORTH, BANK);
            aligned = true;
            return false;
        }
        if (approachRotation == null) return false;
        if (!held) {
            held = true;
            client.options.forwardKey.setPressed(true);
        }
        var reached = arrival;
        if (reached == null || ShipAttitudeClient.portalEpoch == initial.epoch
                || !atExit(client.player.getPos())) return false;
        client.options.forwardKey.setPressed(false);
        if (++synchronizedTicks < 6) return false;
        if (!atExit(vector(reached.pos)) || reached.epoch != initial.epoch + 1 || crossings != 1)
            return finish(client, "The server did not perform one paired exit");
        if (vector(reached.forward).z < .98)
            return finish(client, "The server did not map the incoming frame onto the exit");
        if (flight && (vector(reached.velocity).z <= 0 || vector(reached.up).distanceTo(new Vec3d(-BANK.x, BANK.y, 0)) > .04))
            return finish(client, "The server did not preserve bank and outgoing flight momentum");
        if (nearFrames == 0 || maximumApproachAngle > 1)
            return finish(client, "The actual outer world view rotated before arrival");
        if (latest == null || client.player.getPos().distanceTo(vector(latest.pos)) > 1)
            return finish(client, "The client and server did not agree after the crossing");
        saveFrame(client, "traversal-exit");
        return finish(client, null);
    }

    void rendered(MinecraftClient client) {
        var matrix = OffscreenWorldView.framePosition();
        if (!aligned || matrix == null || client.player == null || baseline == null
                || ShipAttitudeClient.portalEpoch != baseline.epoch) return;
        var q = matrix.getNormalizedRotation(new Quaternionf());
        if (approachRotation == null) {
            approachRotation = q;
            return;
        }
        double depth = client.player.getZ() - (source.getZ() + 1);
        if (!held || depth < .05 || depth > 2.5 || PortalSeeThroughRenderer.scannedCount() == 0) return;
        double angle = Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(approachRotation.dot(q)))));
        nearFrames++;
        maximumApproachAngle = Math.max(maximumApproachAngle, angle);
        if (frames.size() < 120) frames.add(Map.of("tick", ticks, "depth", depth, "angle", angle,
                "player", xyz(client.player.getPos()), "camera", xyz(client.gameRenderer.getCamera().getPos())));
        if (nearFrames == 1) saveFrame(client, "traversal-approach");
    }

    private boolean atExit(Vec3d position) {
        return Math.abs(position.x - (target.getX() + .5)) < 1.25 && position.z > target.getZ() + 1;
    }

    private boolean finish(MinecraftClient client, String failure) {
        client.options.forwardKey.setPressed(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null ? "PASS" : "FAIL");
        result.put("reason", failure);
        result.put("flight", flight);
        result.put("ordinaryForwardKey", held);
        result.put("nearFrames", nearFrames);
        result.put("maximumApproachAngle", maximumApproachAngle);
        result.put("crossings", crossings);
        result.put("initial", baseline);
        result.put("serverArrival", arrival);
        result.put("clientFinal", xyz(client.player.getPos()));
        result.put("frames", frames);
        synchronized (server) {
            result.put("server", List.copyOf(server));
        }
        try {
            Files.writeString(client.runDirectory.toPath().resolve("gun-traversal-outcome.json"),
                    new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        System.out.println("GUN_TRAVERSAL " + result.get("status") + " " + failure + " crossings=" + crossings
                + " nearFrames=" + nearFrames + " angle=" + maximumApproachAngle);
        client.scheduleStop();
        return true;
    }

    private static void saveFrame(MinecraftClient client, String name) {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(client.runDirectory.toPath().resolve("portal-gun-frames").resolve(name + ".png"));
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
    private static List<Double> xyz(Vec3d v) { return List.of(v.x, v.y, v.z); }
    private static Vec3d vector(List<Double> v) { return new Vec3d(v.get(0), v.get(1), v.get(2)); }
}
