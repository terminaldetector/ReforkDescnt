package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.portal.PortalViewDiagnostics;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Drives into a pair already placed by the real projector mouse and packet path. */
final class NativeGunPortalApproach {
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private final List<ServerSample> server = new ArrayList<>();
    private final List<Map<String, Object>> frames = new ArrayList<>();
    private final boolean flight = Boolean.getBoolean("drmd.gate.gunFlight");
    private volatile UUID actor;
    private volatile ServerSample baseline;
    private volatile ServerSample latest;
    private volatile ServerSample arrival;
    private volatile int crossings;
    private Vec3d sourcePlane;
    private Vec3d exitPlane;
    private Vec3d outward;
    private Vec3d bank;
    private Quaternionf approachRotation;
    private Vec3d outerPosition;
    private float[] outerBasis;
    private boolean aligned;
    private boolean held;
    private int ticks;
    private int nearFrames;
    private int cameraStateChanges;
    private int synchronizedTicks;
    private double maximumApproachAngle;

    private record ServerSample(int tick, List<Double> pos, List<Double> velocity, List<Double> forward,
                                List<Double> up, int epoch, boolean flight) {}

    void start(MinecraftClient client, BlockPos source, BlockPos target, Direction facing) {
        outward = Vec3d.of(facing.getVector());
        sourcePlane = Vec3d.ofCenter(source).add(outward.multiply(.5));
        exitPlane = Vec3d.ofCenter(target).add(outward.multiply(.5));
        bank = outward.negate().crossProduct(UP).multiply(Math.sin(Math.PI / 9)).add(UP.multiply(Math.cos(Math.PI / 9)));
        UUID playerId = client.player.getUuid();
        client.getServer().execute(() -> {
            var p = client.getServer().getPlayerManager().getPlayer(playerId);
            var data = DescentPlayerData.get(p);
            if (flight) {
                FlightSystem.enableWithoutMount(p, data);
                data.setFlightVelocity(Vec3d.ZERO);
                data.setShipAttitude((float) -outward.x, 0, (float) -outward.z, (float) bank.x, (float) bank.y, (float) bank.z);
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
            float yaw = FlightAttitude.yawOf(outward.negate());
            client.player.setYaw(yaw);
            client.player.setPitch(0);
            client.player.prevYaw = yaw;
            client.player.prevPitch = 0;
            if (flight) ShipAttitudeClient.acceptPortalFrame(client.player, outward.negate(), bank);
            aligned = true;
            return false;
        }
        if (approachRotation == null) return false;
        var reached = arrival;
        if (reached == null && ShipAttitudeClient.portalEpoch == initial.epoch) {
            approach(client);
            return false;
        }
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        if (reached == null || ShipAttitudeClient.portalEpoch == initial.epoch
                || !atExit(client.player.getPos())) return false;
        if (++synchronizedTicks < 6) return false;
        if (!atExit(vector(reached.pos)) || reached.epoch != initial.epoch + 1 || crossings != 1)
            return finish(client, "The server did not perform one paired exit");
        if (vector(reached.forward).dotProduct(outward) < .98)
            return finish(client, "The server did not map the incoming frame onto the exit");
        if (flight && (vector(reached.velocity).dotProduct(outward) <= 0
                || vector(reached.up).distanceTo(bank.multiply(-1, 1, -1)) > .04))
            return finish(client, "The server did not preserve bank and outgoing flight momentum");
        if (nearFrames == 0) return finish(client, "No actual visible portal frame was sampled before arrival");
        if (maximumApproachAngle > 1 || cameraStateChanges != 0)
            return finish(client, "The actual outer world view rotated before arrival");
        if (latest == null || client.player.getPos().distanceTo(vector(latest.pos)) > 1) {
            if (synchronizedTicks < 80) return false;
            return finish(client, "The client and server did not agree after the crossing");
        }
        saveFrame(client, "traversal-exit");
        return finish(client, null);
    }

    private void approach(MinecraftClient client) {
        // Leave enough time for a real frame to finish on the software renderer before accelerating
        // through the plane. Both this maneuver and the crossing use the normal movement keys.
        boolean forward = true;
        boolean backward = false;
        if (flight && nearFrames == 0) {
            double depth = client.player.getPos().subtract(sourcePlane).dotProduct(outward);
            double inward = -client.player.getVelocity().dotProduct(outward);
            double desired = Math.max(-.28, Math.min(.38, (depth - 1.5) * .32));
            forward = inward < desired - .08;
            backward = inward > desired + .08;
        }
        held |= forward;
        client.options.forwardKey.setPressed(forward);
        client.options.backKey.setPressed(backward);
    }

    void beforeWorldRender(Camera camera) {
        outerPosition = camera.getPos();
        outerBasis = cameraBasis(camera);
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
        double depth = client.player.getPos().subtract(sourcePlane).dotProduct(outward);
        if (!held || depth < .05 || depth > 2.5
                || !String.valueOf(PortalViewDiagnostics.lastSummary("portal")).startsWith("drawing")) return;
        var camera = client.gameRenderer.getCamera();
        boolean sameCamera = camera.getPos().equals(outerPosition) && Arrays.equals(cameraBasis(camera), outerBasis);
        if (!sameCamera) cameraStateChanges++;
        double angle = Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(approachRotation.dot(q)))));
        nearFrames++;
        maximumApproachAngle = Math.max(maximumApproachAngle, angle);
        if (frames.size() < 120) frames.add(Map.of("tick", ticks, "depth", depth, "angle", angle,
                "player", xyz(client.player.getPos()), "camera", xyz(camera.getPos()), "sameCamera", sameCamera));
        if (nearFrames == 1) saveFrame(client, "traversal-approach");
    }

    private boolean atExit(Vec3d position) {
        Vec3d relative = position.subtract(exitPlane);
        double depth = relative.dotProduct(outward);
        return depth > 0 && relative.subtract(outward.multiply(depth)).horizontalLengthSquared() < 1.25 * 1.25;
    }

    private boolean finish(MinecraftClient client, String failure) {
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null ? "PASS" : "FAIL");
        result.put("reason", failure);
        result.put("flight", flight);
        result.put("outward", xyz(outward));
        result.put("ordinaryForwardKey", held);
        result.put("nearFrames", nearFrames);
        result.put("cameraStateChanges", cameraStateChanges);
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
    private static float[] cameraBasis(Camera camera) {
        var q = camera.getRotation();
        var f = camera.getHorizontalPlane();
        var up = camera.getVerticalPlane();
        var left = camera.getDiagonalPlane();
        return new float[]{camera.getYaw(), camera.getPitch(), q.x, q.y, q.z, q.w,
                f.x, f.y, f.z, up.x, up.y, up.z, left.x, left.y, left.z};
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
