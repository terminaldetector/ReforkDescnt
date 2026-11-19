package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Real ordinary walking and jumping over an exposed terrace, with a floor torch below its lip. */
public final class NativeOpenTorchCheck {
    public static final boolean ENABLED = Boolean.getBoolean("drmd.gate.torchOpen");
    private static final int FLOOR = 2148;
    private static final Vec3d START = new Vec3d(15.5, 2152, 28.5);
    private static final BlockPos TORCH = new BlockPos(14, FLOOR, 15);
    private static volatile UUID viewer;
    private static volatile boolean authored, observing, done;
    private static volatile Sample latest, captured;
    private static volatile String serverFailure;
    private static final List<Sample> SAMPLES = new ArrayList<>();
    private static boolean staged;
    private static int ticks, phase, jumpHeld;
    private static double top = FLOOR;
    private static Vec3d previousClient;
    private static double maxClientStep;
    private static String result;
    private static long boot;

    private record Sample(long tick, List<Double> position, boolean active, boolean grounded,
                          boolean clear, double displacement) {}

    static void register() {
        boot = System.nanoTime();
        ServerTickEvents.END_SERVER_TICK.register(NativeOpenTorchCheck::server);
        ClientTickEvents.END_CLIENT_TICK.register(NativeOpenTorchCheck::client);
    }

    private static boolean available(World world) {
        var access = WorldAccess.of(world);
        if (access.backend() != CubicAvailability.Backend.NATIVE_CUBIC) return false;
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 2; z++) for (int y = 134; y <= 135; y++)
            if (!access.isVolumeAvailable(new D6ChunkPos(x, y, z))) return false;
        return true;
    }

    private static void server(MinecraftServer server) {
        if (done || viewer == null || serverFailure != null) return;
        var p = server.getPlayerManager().getPlayer(viewer);
        if (p == null) return;
        var world = p.getServerWorld();
        try {
            if (!staged) {
                staged = true;
                p.changeGameMode(GameMode.CREATIVE);
                FlightSystem.disable(p, DescentPlayerData.get(p));
                p.getAbilities().allowFlying = true;
                p.getAbilities().flying = true;
                p.sendAbilitiesUpdate();
                p.teleport(world, START.x, START.y, START.z, Set.of(), 180, 0);
                return;
            }
            if (!authored) {
                if (!available(world)) return;
                for (var pos : BlockPos.iterate(new BlockPos(10, FLOOR - 1, 12), new BlockPos(21, 2156, 34))) {
                    int height = pos.getZ() >= 24 ? 2151 : FLOOR - 1;
                    var state = pos.getY() <= height ? Blocks.STONE : Blocks.AIR;
                    world.setBlockState(pos, state.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                world.setBlockState(TORCH, ModWorldBlocks.GRAVITY_TORCH.getDefaultState(), Block.NOTIFY_ALL);
                p.changeGameMode(GameMode.SURVIVAL);
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
                authored = true;
                return;
            }
            if (!observing) return;
            Vec3d at = p.getPos();
            var previous = latest;
            double distance = previous == null ? at.distanceTo(START) : at.distanceTo(vector(previous.position));
            var state = FootGravitySystem.get(p);
            boolean active = state != null && state.active();
            boolean grounded = active ? state.grounded() : p.isOnGround();
            boolean clear = world.isSpaceEmpty(p, p.getBoundingBox().contract(1e-6));
            var row = new Sample(server.getTicks(), xyz(at), active, grounded, clear, distance);
            if (active && captured == null) captured = row;
            latest = row;
            synchronized (SAMPLES) {
                if (SAMPLES.size() < 550) SAMPLES.add(row);
            }
            if (at.y < FLOOR - 1e-5 || !clear)
                serverFailure = "Server movement left the exposed walkable volume: " + row;
        } catch (RuntimeException error) {
            serverFailure = error.toString();
        }
    }

    private static void client(MinecraftClient c) {
        if (done || result != null) return;
        if (++ticks > 2400 || System.nanoTime() - boot > 180_000_000_000L) {
            finish(c, "Open terrain fixture did not complete its ordinary movement");
            return;
        }
        if (c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for (var node : prompt.children())
                if (node instanceof net.minecraft.client.gui.widget.ButtonWidget button
                    && button.getMessage().getString().equals("I know what I'm doing!")) button.onPress();
        if (c.player == null || c.world == null || c.getServer() == null) return;
        DescentClient.markUserFlightChoice();
        if (viewer == null) {
            if (!c.runDirectory.getName().equals("cubic-client-automation")) throw new IllegalStateException("Disposable native fixture required");
            viewer = c.player.getUuid();
            c.setScreen(null);
            c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
        }
        if (serverFailure != null) {
            finish(c, serverFailure);
            return;
        }
        if (!authored || c.player.isCreative() || DescentClientState.enabled || !available(c.world)) return;
        if (!observing) {
            if (c.player.getPos().distanceTo(START) > .15 || !c.player.isOnGround() || c.currentScreen != null) return;
            c.player.setYaw(180);
            c.player.setPitch(10);
            previousClient = c.player.getPos();
            observing = true;
        }
        var at = c.player.getPos();
        // Several normal client movement packets can arrive in one server tick.
        // The individual client simulation ticks show whether the camera/body actually jumped.
        maxClientStep = Math.max(maxClientStep, previousClient.distanceTo(at));
        previousClient = at;
        if (maxClientStep > 1.1) {
            finish(c, "Player displaced " + maxClientStep + " blocks in one actual movement tick");
            return;
        }
        if (at.y < FLOOR - 1e-5 || !c.world.isSpaceEmpty(c.player, c.player.getBoundingBox().contract(1e-6))) {
            finish(c, "Client feet or body intersect open terrain at " + at);
            return;
        }
        if (phase == 0) {
            c.options.forwardKey.setPressed(at.z > 19);
            if (at.z > 19 || latest == null || !latest.active || !latest.grounded || !c.player.isOnGround()) return;
            if (captured == null || vector(captured.position).y < FLOOR + 1) {
                finish(c, "The torch must first acquire the walking player while still over the drop");
                return;
            }
            phase = 1;
        }
        if (phase == 1) {
            c.options.jumpKey.setPressed(++jumpHeld <= 2);
            top = Math.max(top, at.y);
            if (top >= FLOOR + .5 && at.y < top - .12) phase = 2;
        }
        if (phase == 2 && latest != null && latest.grounded && c.player.isOnGround()
            && at.y < FLOOR + .10 && latest.active) finish(c, null);
    }

    public static void rendered() {
        if (result == null || done) return;
        var c = MinecraftClient.getInstance();
        var dir = c.runDirectory.toPath();
        try (var image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(dir.resolve("torch-open-terrain.png"));
            var proof = new LinkedHashMap<String, Object>();
            proof.put("status", result);
            proof.put("capture", captured);
            proof.put("jumpTop", top);
            proof.put("maximumClientStep", maxClientStep);
            synchronized (SAMPLES) { proof.put("server", List.copyOf(SAMPLES)); }
            proof.put("sourceBinding", System.getProperty("drmd.gate.torchOpenSource", "UNBOUND"));
            Files.writeString(dir.resolve("torch-open-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            done = true;
            c.scheduleStop();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void finish(MinecraftClient c, String failure) {
        c.options.forwardKey.setPressed(false);
        c.options.jumpKey.setPressed(false);
        result = failure == null ? "PASS" : "FAIL " + failure;
        System.out.println("TORCH_OPEN_TERRAIN " + result);
    }

    private static Vec3d vector(List<Double> p) { return new Vec3d(p.get(0), p.get(1), p.get(2)); }
    private static List<Double> xyz(Vec3d v) { return List.of(v.x, v.y, v.z); }
}
