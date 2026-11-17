package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real BCC availability boundary and production ragdoll ticks. No metadata writes or load tickets. */
public final class NativeRagdollVolumeCheck {
    public static final boolean ENABLED = Boolean.getBoolean("drmd.gate.ragdollVolumes");
    private static volatile boolean prepared, done;
    private static volatile ServerPlayerEntity observer;
    private static volatile Map<String, Object> observations = Map.of();
    private static boolean requested;
    private static int clientTicks, serverTicks;

    private NativeRagdollVolumeCheck() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NativeRagdollVolumeCheck::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!prepared || done) return;
            Map<String, Object> report = null;
            try {
                var world = observer.getServerWorld();
                require(server.isOnThread(), "native metadata and ragdoll observations require the server thread");
                require(CubicAvailability.backend(world) == CubicAvailability.Backend.NATIVE_CUBIC,
                        "real BCC backend required");
                // Keep the disposable spectator at its original position; no teleport or cube request.
                observer.setVelocity(Vec3d.ZERO);
                if (++serverTicks > 1000) throw new IllegalStateException("no natural FULL/unavailable air boundary became ready");
                if (serverTicks % 10 != 0) return;
                var boundary = findBoundary(world, observer.getBlockPos());
                if (boundary != null) report = exercise(world, boundary);
            } catch (Exception | LinkageError failure) {
                report = new LinkedHashMap<>();
                report.put("pass", false);
                report.put("failureKind", "OBSERVER_PRECONDITION");
                report.put("reason", failure.toString());
                report.put("observations", observations);
                report.put("serverTicks", serverTicks);
                report.put("observerPosition", vector(observer.getPos()));
            }
            if (report != null) finish(report);
        });
    }

    private static void clientTick(MinecraftClient client) {
        if (done) return;
        if (++clientTicks > 2400) {
            finish(new LinkedHashMap<>(Map.of("pass", false, "failureKind", "OBSERVER_PRECONDITION",
                    "reason", "native ragdoll boundary observer timed out before completion")));
            return;
        }
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for (var child : prompt.children())
                if (child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                        && button.getMessage().getString().equals("I know what I'm doing!")) button.onPress();
        if (client.player == null || client.world == null || client.getServer() == null) return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if (requested) return;
        require(client.runDirectory.getName().equals("cubic-client-automation"), "disposable staged runtime only");
        requested = true;
        client.setScreen(null);
        client.options.hudHidden = true;
        client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
        UUID playerId = client.player.getUuid();
        client.getServer().execute(() -> {
            observer = client.getServer().getPlayerManager().getPlayer(playerId);
            require(observer != null, "real integrated-server observer exists");
            observer.stopRiding();
            FlightSystem.disable(observer, DescentPlayerData.get(observer));
            observer.changeGameMode(GameMode.CREATIVE);
            observer.getAbilities().allowFlying = true;
            observer.getAbilities().flying = true;
            observer.sendAbilitiesUpdate();
            prepared = true;
        });
    }

    private record Boundary(D6ChunkPos known, D6ChunkPos unknown, Vec3d start) {}

    private static Boundary findBoundary(ServerWorld world, BlockPos viewer) {
        var access = WorldAccess.of(world);
        int x = viewer.getX() >> 4, z = viewer.getZ() >> 4;
        // A native-only parent can exist outside the vanilla column cache; this reproduction
        // specifically requires the same 2D predicate currently used by RagdollEntity to pass.
        if (CubicAvailability.loadedColumn(world, x, z) == null || !world.isChunkLoaded(x, z)) return null;
        int first = Math.max((world.getBottomY() >> 4) + 1, viewer.getY() >> 4);
        int last = Math.min((world.getTopY() >> 4) - 2, first + 128);
        for (int y = first; y <= last; y++) {
            var known = new D6ChunkPos(x, y, z);
            var unknown = known.offset(0, 1, 0);
            if (!access.isVolumeAvailable(known) || access.isVolumeAvailable(unknown)) continue;
            var start = new Vec3d(x * 16 + 8.5, unknown.minBlockY() - .5, z * 16 + 8.5);
            boolean clear = true;
            for (var pos : BlockPos.iterate(BlockPos.ofFloored(start).add(-1, -5, -1),
                    BlockPos.ofFloored(start).add(1, 0, 1))) {
                var state = access.readIfLoaded(pos);
                if (state.isEmpty() || !state.get().isAir()) { clear = false; break; }
            }
            if (clear && GravityFields.sample(world, start) == null
                    && GravityFields.sample(world, start.add(0, -4, 0)) == null)
                return new Boundary(known, unknown, start);
        }
        return null;
    }

    private static Map<String, Object> exercise(ServerWorld world, Boundary boundary) throws Exception {
        var access = WorldAccess.of(world);
        var report = new LinkedHashMap<String, Object>();
        observations = report;
        report.put("knownCube", List.of(boundary.known.x(), boundary.known.y(), boundary.known.z()));
        report.put("unknownCube", List.of(boundary.unknown.x(), boundary.unknown.y(), boundary.unknown.z()));
        var unknownProbe = new BlockPos(boundary.unknown.minBlockX() + 8,
                boundary.unknown.minBlockY(), boundary.unknown.minBlockZ() + 8);
        var beforeQuery = nativeSnapshot(world, boundary.unknown);
        report.put("nativeBeforeQuery", beforeQuery);
        require(!access.isVolumeAvailable(boundary.unknown) && access.readIfLoaded(unknownProbe).isEmpty(),
                "unknown Y cube must be observed before any production tick");
        var afterQuery = nativeSnapshot(world, boundary.unknown);
        report.put("nativeAfterQuery", afterQuery);
        require(beforeQuery.equals(afterQuery), "availability/read queries changed native admission or metadata");

        report.put("failureKind", "GAMEPLAY");
        report.put("realNativeCubic", true);
        report.put("nativeVersion", FabricLoader.getInstance().getModContainer("bettercubicchunks")
                .orElseThrow().getMetadata().getVersion().getFriendlyString());
        report.put("parentColumnLoaded", world.isChunkLoaded(boundary.known.x(), boundary.known.z()));
        report.put("unknownReadEmptyBeforeTick", true);
        report.put("queriesLeaveNativeStateUnchanged", true);
        report.put("observerTickets", 0);
        report.put("observerMetadataWrites", 0);
        report.put("observerBlockWrites", 0);
        report.put("serverOwnerThread", world.getServer().isOnThread());

        // Positive control prevents a guard that freezes every wreck from passing this test.
        RagdollEntity control = RagdollEntity.spawnScout(world, boundary.start.add(0, -4, 0), new Vec3d(0, .1, 0));
        require(control != null, "real loaded-air control registered");
        try {
            double before = entity(control).getY();
            require(allAvailable(access, control.partBounds()), "control starts entirely inside FULL air");
            entity(control).tick();
            double moved = entity(control).getY() - before;
            require(moved > .01 && allAvailable(access, control.partBounds()), "loaded-air control did not move within known space");
            report.put("loadedControlMovedY", moved);
        } finally { entity(control).discard(); }

        RagdollEntity ragdoll = RagdollEntity.spawnScout(world, boundary.start, new Vec3d(0, 2, 0));
        require(ragdoll != null, "real boundary scout registered");
        try {
            UUID id = entity(ragdoll).getUuid();
            int networkId = entity(ragdoll).getId();
            var simulation = ragdoll.simulation();
            require(allAvailable(access, ragdoll.partBounds()), "all initial articulated parts must be entirely inside FULL air");
            require(world.getEntityById(networkId) == ragdoll, "spawned rig is the real world-owned entity");
            report.put("entityUuid", id.toString());
            report.put("initialPosition", vector(entity(ragdoll).getPos()));
            report.put("initialVelocityBlocksPerSecond", vector(ragdoll.physicsVelocity()));
            report.put("initialParts", bounds(ragdoll.partBounds()));
            report.put("predictedFreeSweep", bounds(ragdoll.partBounds().stream()
                    .map(box -> box.union(box.offset(0, 1.75, 0))).toList()));
            var beforeTick = nativeSnapshot(world, boundary.unknown);
            require(beforeTick.equals(beforeQuery), "spawn/control unexpectedly changed native volume admission");
            // Exactly one real production tick, with no next vanilla world tick before cleanup.
            entity(ragdoll).tick();
            var afterTick = nativeSnapshot(world, boundary.unknown);
            boolean unknownStillUnavailable = !access.isVolumeAvailable(boundary.unknown)
                    && access.readIfLoaded(unknownProbe).isEmpty();
            var afterTickQuery = nativeSnapshot(world, boundary.unknown);
            boolean nativeUnchanged = beforeTick.equals(afterTick) && afterTick.equals(afterTickQuery);
            boolean retainedOwnership = id.equals(entity(ragdoll).getUuid()) && world.getEntityById(networkId) == ragdoll
                    && simulation == ragdoll.simulation() && entity(ragdoll).isAlive() && ragdoll.simulation().parts().size() == 5;
            boolean enteredUnknown = !allAvailable(access, ragdoll.partBounds());
            report.put("finalPosition", vector(entity(ragdoll).getPos()));
            report.put("finalVelocityBlocksPerSecond", vector(ragdoll.physicsVelocity()));
            report.put("finalParts", bounds(ragdoll.partBounds()));
            report.put("sameEntityAndSimulationOwner", retainedOwnership);
            report.put("initialPhysicalParts", 5);
            report.put("nativeBeforeQuery", beforeQuery);
            report.put("nativeBeforeTick", beforeTick);
            report.put("nativeAfterTick", afterTick);
            report.put("unknownStillUnavailableAfterTick", unknownStillUnavailable);
            report.put("nativeMetadataAndAdmissionUnchanged", nativeUnchanged);
            report.put("occupiedUnavailableCubeAfterTick", enteredUnknown);
            var violations = new ArrayList<String>();
            if (!retainedOwnership) violations.add("ragdoll entity/simulation ownership changed");
            if (!unknownStillUnavailable) violations.add("unknown cube was materialized or admitted");
            if (!nativeUnchanged) violations.add("production tick/query changed native cube metadata or admissions");
            if (enteredUnknown) violations.add("physical ragdoll parts crossed into an unavailable Y cube while parent X/Z remained loaded");
            report.put("violations", violations);
            report.put("pass", violations.isEmpty());
        } finally {
            int networkId = entity(ragdoll).getId();
            entity(ragdoll).discard();
            report.put("fixtureRagdollDiscarded", world.getEntityById(networkId) != ragdoll || entity(ragdoll).isRemoved());
        }
        return report;
    }

    private static boolean allAvailable(WorldAccess access, List<Box> boxes) {
        for (var box : boxes)
            for (int x = MathHelper.floor(box.minX + 1e-7) >> 4; x <= MathHelper.floor(box.maxX - 1e-7) >> 4; x++)
                for (int y = MathHelper.floor(box.minY + 1e-7) >> 4; y <= MathHelper.floor(box.maxY - 1e-7) >> 4; y++)
                    for (int z = MathHelper.floor(box.minZ + 1e-7) >> 4; z <= MathHelper.floor(box.maxZ - 1e-7) >> 4; z++)
                        if (!access.isVolumeAvailable(new D6ChunkPos(x, y, z))) return false;
        return true;
    }

    /** All reflective native access below is read-only and executes on the world owner thread. */
    private static Map<String, Object> nativeSnapshot(ServerWorld world, D6ChunkPos cube) throws Exception {
        var column = CubicAvailability.loadedColumn(world, cube.x(), cube.z());
        require(column != null, "native parent column remains available");
        var extension = Class.forName("dev.fallingcloud.bettercubicchunks.chunk.ISkyLightSourcesExt");
        Object state = extension.getMethod("bcc$columnState").invoke(column.getChunkSkyLight());
        require(state != null, "real native column metadata exists");
        int index = world.getSectionIndex(cube.minBlockY());
        var type = state.getClass();
        var result = new LinkedHashMap<String, Object>();
        result.put("cubeStatus", String.valueOf(type.getMethod("status", int.class).invoke(state, index)));
        for (String method : List.of("loadedCubeCount", "materializedCount"))
            result.put(method, type.getMethod(method).invoke(state));
        for (String method : List.of("wasFull", "wasGenerated", "isMaterialized", "hasData"))
            result.put(method, type.getMethod(method, int.class).invoke(state, index));
        byte[] statuses = (byte[]) type.getMethod("statusSnapshot").invoke(state);
        result.put("columnStatusSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(statuses)));
        var api = Class.forName("dev.fallingcloud.bettercubicchunks.server.ICubicServerLevel");
        Object nativeWorld = api.getMethod("bcc$cubic").invoke(world);
        for (String field : List.of("pending", "inFlight", "temporaryViewers", "temporaryRegions")) {
            var member = nativeWorld.getClass().getDeclaredField(field);
            member.setAccessible(true);
            Object value = member.get(nativeWorld);
            int size = value instanceof Map<?, ?> map ? map.size() : ((Collection<?>) value).size();
            result.put(field + "Count", size);
        }
        return result;
    }

    private static List<Double> vector(Vec3d value) { return List.of(value.x, value.y, value.z); }
    private static Entity entity(Entity entity) { return entity; }
    private static List<List<Double>> bounds(List<Box> boxes) {
        return boxes.stream().map(box -> List.of(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)).toList();
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
    private static void finish(Map<String, Object> report) {
        if (done) return;
        done = true;
        boolean pass = Boolean.TRUE.equals(report.get("pass"));
        report.put("nativeVisualAcceptance", false);
        report.put("scope", "server-side ragdoll admission at an actual native Y availability boundary");
        try {
            var file = FabricLoader.getInstance().getGameDir().resolve(pass
                    ? "ragdoll-volumes-ok.json" : "ragdoll-volumes-failure.json");
            Files.writeString(file, new com.google.gson.Gson().toJson(report) + "\n");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        var client = MinecraftClient.getInstance();
        client.execute(client::scheduleStop);
        System.out.println("RAGDOLL_VOLUMES_" + (pass ? "PASS " : "FAIL ") + report);
        if (!pass) throw new IllegalStateException("Native ragdoll volume gate: " + report);
    }
}
