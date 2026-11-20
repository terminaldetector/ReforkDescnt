package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Actual native signed heights, with a separate cold reader after normal save/close. */
public final class NativeDeferredSpawnerCheck {
    private static final boolean WRITE = Boolean.getBoolean("drmd.gate.deferredSpawnerWrite");
    private static final boolean READ = Boolean.getBoolean("drmd.gate.deferredSpawnerRead");
    public static final boolean ENABLED = WRITE || READ;
    private static final int[] HEIGHTS = {-512, 2150};
    private static final int X = 72, Z = 72;
    private static final List<Map<String, Object>> ROWS = new ArrayList<>();
    private static volatile UUID playerId;
    private static volatile Map<String, Object> result;
    private static boolean moved, written;
    private static int ticks, site, attempts, fixtureWrites;

    private NativeDeferredSpawnerCheck() {}

    public static void register() {
        if (READ == WRITE) throw new IllegalStateException("Select exactly one signed spawner process");
        ClientTickEvents.END_CLIENT_TICK.register(NativeDeferredSpawnerCheck::client);
        ServerTickEvents.END_SERVER_TICK.register(NativeDeferredSpawnerCheck::server);
    }

    private static void client(MinecraftClient client) {
        CubicClientGate.confirmDisposableCubicSave(client);
        if (client.getServer() == null || client.player == null) return;
        if (!client.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation")) {
            throw new IllegalStateException("Disposable cubic runtime required");
        }
        if (playerId == null) playerId = client.player.getUuid();
        var outcome = result;
        if (outcome == null || written) return;
        written = true;
        try {
            Files.writeString(client.runDirectory.toPath().resolve(READ
                    ? "native-deferred-spawner-reader.json" : "native-deferred-spawner-writer-ready.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(outcome));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not record deferred spawner outcome", failure);
        } finally {
            client.scheduleStop();
        }
    }

    private static void server(MinecraftServer server) {
        if (result != null || playerId == null) return;
        try {
            var player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) return;
            var world = player.getServerWorld();
            var access = WorldAccess.of(world);
            require(access.backend() == CubicAvailability.Backend.NATIVE_CUBIC, "actual BCC required");
            if (++ticks > 2000) throw new IllegalStateException("ordinary residence failed admission");
            int y = HEIGHTS[site];
            var marker = new BlockPos(X, y, Z);
            var typed = marker.east();
            var control = marker.south();
            if (!moved) {
                FlightSystem.disable(player, DescentPlayerData.get(player));
                player.changeGameMode(GameMode.SPECTATOR);
                player.teleport(world, X + 4.5, y + 1, Z + 4.5, Set.of(), 0, 0);
                attempts++;
                moved = true;
                return;
            }
            // These three positions lie in one actual cube. The read path never asks for an unavailable native column.
            if (access.readIfLoaded(marker).isEmpty() || access.readIfLoaded(typed).isEmpty()
                    || access.readIfLoaded(control).isEmpty()) return;
            var column = CubicAvailability.loadedColumn(world, X >> 4, Z >> 4);
            require(column != null && marker.equals(BlockPos.fromLong(marker.asLong())), "actual signed native identity");
            short spawnCount = (short) (13 + site * 5);
            if (WRITE) {
                createAndReplacePending(world, marker, true);
                createAndReplacePending(world, typed, false);
                var surviving = placeSpawner(world, control);
                var nbt = surviving.createNbtWithIdentifyingData(world.getRegistryManager());
                nbt.putShort("SpawnCount", spawnCount);
                surviving.read(nbt, world.getRegistryManager());
                surviving.markDirty();
            }
            require(access.readIfLoaded(marker).orElseThrow().isAir() && access.readIfLoaded(typed).orElseThrow().isAir(),
                    "accepted replacements did not retain native AIR at signed Y");
            require(column.getBlockEntityNbt(marker) == null && column.getBlockEntityNbt(typed) == null
                    && !column.getBlockEntityPositions().contains(marker) && !column.getBlockEntityPositions().contains(typed),
                    "replacement retained pending or live owner");
            require(access.readIfLoaded(control).orElseThrow().isOf(Blocks.SPAWNER), "adjacent native control vanished");
            require(column.getBlockEntity(control) instanceof MobSpawnerBlockEntity, "adjacent retained owner is not a native spawner");
            var state = column.getBlockEntity(control).createNbtWithIdentifyingData(world.getRegistryManager());
            require("minecraft:mob_spawner".equals(state.getString("id"))
                    && state.getInt("x") == control.getX() && state.getInt("y") == y && state.getInt("z") == control.getZ()
                    && state.getShort("SpawnCount") == spawnCount, "unrelated typed state disagrees after replacement/save");
            ROWS.add(Map.of("signedY", y, "missingOwnerCount", 2, "retainedType", state.getString("id"),
                    "retainedSpawnCount", spawnCount, "actualFullCube", true));
            site++;
            moved = false;
            if (site != HEIGHTS.length) return;
            var evidence = new LinkedHashMap<String, Object>();
            evidence.put("status", READ ? "OBSERVED_NATIVE_SIGNED_SPAWNER_COLD_PASS" : "READY_FOR_NATIVE_SPAWNER_SAVE");
            evidence.put("stage", READ ? "reader" : "writer");
            evidence.put("cases", List.copyOf(ROWS));
            evidence.put("fixtureStateWrites", fixtureWrites);
            evidence.put("ordinaryResidencySites", attempts);
            evidence.put("backend", access.backend());
            evidence.put("worldSeed", world.getSeed());
            evidence.put("observerTickets", 0);
            evidence.put("initialPendingInputsValid", WRITE);
            result = evidence;
        } catch (Exception failure) {
            result = Map.of("status", "FAIL", "stage", READ ? "reader" : "writer", "ticks", ticks, "reason", failure.toString());
        }
    }

    private static MobSpawnerBlockEntity placeSpawner(ServerWorld world, BlockPos pos) {
        require(world.setBlockState(pos, Blocks.SPAWNER.getDefaultState(), 2), "fixture expected a fresh spawner position");
        fixtureWrites++;
        if (world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity entity) return entity;
        throw new IllegalStateException("native spawner factory did not attach a live owner");
    }

    private static void createAndReplacePending(ServerWorld world, BlockPos at, boolean dummy) {
        var original = placeSpawner(world, at);
        var chunk = CubicAvailability.loadedColumn(world, at.getX() >> 4, at.getZ() >> 4);
        var data = original.createNbtWithIdentifyingData(world.getRegistryManager());
        if (dummy) {
            data = new NbtCompound();
            data.putString("id", "DUMMY");
            data.putInt("x", at.getX());
            data.putInt("y", at.getY());
            data.putInt("z", at.getZ());
        }
        chunk.removeBlockEntity(at);
        chunk.addPendingBlockEntityNbt(data);
        require(data.equals(chunk.getBlockEntityNbt(at)) && chunk.getBlockState(at).isOf(Blocks.SPAWNER),
                "fixture did not start with valid deferred state/position pair");
        require(world.setBlockState(at, Blocks.AIR.getDefaultState(), 2), "actual managed spawner replacement refused");
        fixtureWrites++;
    }

    private static void require(boolean accepted, String detail) {
        if (!accepted) throw new IllegalStateException(detail);
    }
}
