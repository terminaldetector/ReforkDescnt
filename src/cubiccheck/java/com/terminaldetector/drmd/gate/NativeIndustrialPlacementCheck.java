package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.VolumetricWorldAccess.CubeState;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gen.ModWorldgen;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A disposable server-thread consumer of the real BCC and registered command. */
public final class NativeIndustrialPlacementCheck {
    public static final boolean ENABLED = Boolean.getBoolean("drmd.gate.industrialPlacement");
    private static final BlockPos PLAYER_START = new BlockPos(40, 1640, 40);
    private static final int MAX_SERVER_TICKS = 1200;
    private static volatile UUID playerId;
    private static volatile Map<String, Object> result;
    private static int ticks;
    private static boolean moved, written;

    private NativeIndustrialPlacementCheck() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NativeIndustrialPlacementCheck::client);
        ServerTickEvents.END_SERVER_TICK.register(NativeIndustrialPlacementCheck::server);
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
            Files.writeString(client.runDirectory.toPath().resolve("native-industrial-placement.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(outcome));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not record industrial placement outcome", failure);
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
            if (++ticks > MAX_SERVER_TICKS) throw new IllegalStateException("ordinary residence did not admit the full structure volume");
            if (!moved) {
                FlightSystem.disable(player, DescentPlayerData.get(player));
                player.changeGameMode(GameMode.CREATIVE);
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
                player.teleport(world, PLAYER_START.getX() + .5, PLAYER_START.getY(), PLAYER_START.getZ() + .5, Set.of(), 0, 0);
                moved = true;
                return;
            }
            require(player.getBlockPos().equals(PLAYER_START), "player left the staging cube");
            var center = player.getBlockPos().up(8);
            int cubes = fullFootprint(access, center);
            if (cubes == 0) return;

            // Keep native XZ column fixed while moving only the request's Y, without relocating the player.
            var unknown = center.up(256);
            var unknownCube = access.cubeAt(unknown);
            require(access.cubeState(unknownCube) == CubeState.UNAVAILABLE, "negative control must be representable but unavailable");
            require(CubicAvailability.loadedColumn(world, unknown.getX() >> 4, unknown.getZ() >> 4) != null,
                    "negative control without a retained column");
            int initialMacroCount = MacroWorld.size();
            require(!ModWorldgen.tryForceGenerate(world, unknown, WorldRules.ComplexStyle.ANCIENT_POWER),
                    "unknown Y unexpectedly admitted");
            require(access.cubeState(unknownCube) == CubeState.UNAVAILABLE && MacroWorld.size() == initialMacroCount,
                    "rejected placement loaded storage or committed a catalogue entry");

            var area = new Box(center).expand(23);
            int droneBefore = world.getEntitiesByClass(DroneEntity.class, area, DroneEntity::isAlive).size();
            int shipBefore = world.getEntitiesByClass(PyroShipEntity.class, area, PyroShipEntity::isAlive).size();
            int reactorBefore = world.getEntitiesByClass(ReactorDisplayEntity.class, area, ReactorDisplayEntity::isAlive).size();
            int command = server.getCommandManager().getDispatcher().execute("d6 start", player.getCommandSource());
            require(command == 1, "registered /d6 start did not report successful placement");
            require(access.readIfLoaded(center.down(8)).filter(s -> s.isOf(Blocks.IRON_BLOCK)).isPresent(), "landing pad was not written");
            require(access.readIfLoaded(center.up(8)).filter(s -> s.isOf(ModWorldBlocks.UNSTABLE_REACTOR)).isPresent(), "reactor accent was not written");
            require(world.getEntitiesByClass(DroneEntity.class, area, DroneEntity::isAlive).size() >= droneBefore + 5,
                    "starter did not spawn all five defenders");
            require(world.getEntitiesByClass(PyroShipEntity.class, area, PyroShipEntity::isAlive).size() == shipBefore + 1,
                    "starter did not spawn its transport");
            require(world.getEntitiesByClass(ReactorDisplayEntity.class, area, ReactorDisplayEntity::isAlive).size() == reactorBefore + 1,
                    "starter did not spawn its reactor");
            require(DescentPlayerData.get(player).isEnabled(), "starter did not enable player flight");
            var evidence = new LinkedHashMap<String, Object>();
            evidence.put("status", "OBSERVED_NATIVE_INDUSTRIAL_PLACEMENT_PASS");
            evidence.put("ordinaryAdmissionTicks", ticks);
            evidence.put("nativeFullCubes", cubes);
            evidence.put("registeredCommandResult", command);
            evidence.put("center", List.of(center.getX(), center.getY(), center.getZ()));
            evidence.put("rejectedSameColumnCube", List.of(unknownCube.x(), unknownCube.y(), unknownCube.z()));
            evidence.put("rejectedCubeStillUnavailable", true);
            evidence.put("observerWorldWrites", 0);
            evidence.put("observerTickets", 0);
            result = evidence;
        } catch (Exception failure) {
            result = Map.of("status", "FAIL", "ticks", ticks, "reason", failure.toString());
        }
    }

    private static int fullFootprint(WorldAccess access, BlockPos center) {
        var low = access.cubeAt(center.add(-37, -41, -37));
        var high = access.cubeAt(center.add(37, 41, 37));
        int count = 0;
        for (int x = low.x(); x <= high.x(); x++) {
            for (int y = low.y(); y <= high.y(); y++) {
                for (int z = low.z(); z <= high.z(); z++) {
                    if (access.cubeState(new D6ChunkPos(x, y, z)) != CubeState.FULL) return 0;
                    count++;
                }
            }
        }
        return count;
    }

    private static void require(boolean accepted, String detail) {
        if (!accepted) throw new IllegalStateException(detail);
    }
}
