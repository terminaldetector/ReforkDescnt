package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.render.ShotFxRenderer;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.items.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.BackupPromptScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Real client/packet/render tests, isolated from production entrypoints and player saves. */
public final class WeaponRenderCheck implements ClientModInitializer {
    private static final String[] SCENES = {"bolt-dark-ranges", "bolt-light-ranges",
            "orb-dark-ranges", "orb-light-ranges", "rocket-dark-ranges", "rocket-light-ranges",
            "banked-fire", "vertical-fire", "muzzle-impact", "fast-motion"};
    private static final Vec3d POSITION = new Vec3d(12, 205, 30);
    private volatile boolean ready;
    private volatile List<Integer> ids = List.of();
    private boolean queued, hybridStarted;
    private int ticks, scene = -1, sceneTick;
    private int muzzleBefore, impactBefore;
    private Vec3d previousFast;
    private double fastTravel;
    private int fastFrames;
    private CaptureRequest pendingCapture;
    private record CaptureRequest(String name, boolean colours, boolean mounts, Runnable after) {}
    private volatile net.minecraft.server.MinecraftServer fixtureServer;
    private volatile java.util.UUID fixturePlayer;
    private volatile boolean rangeServerTicked;
    private static final Map<Integer, RenderProbe> RENDERED = new HashMap<>();
    private static final Map<Integer, String> VISIBILITY = new HashMap<>();
    private static volatile String fastServerState = "fast volley not observed";
    private static long renderFrame;
    private record RenderProbe(float x, float y, float fogStart, float fogEnd, long recorded, long frame) {}

    public static void recordVisibility(int id, String state) { VISIBILITY.put(id, state); }

    public static void recordRender(ProjectileEntity entity, net.minecraft.client.util.math.MatrixStack matrices) {
        var client = MinecraftClient.getInstance();
        var clip = new org.joml.Vector4f(0, 0, 0, 1).mul(matrices.peek().getPositionMatrix())
                .mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix())
                .mul(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        RENDERED.put(entity.getId(), new RenderProbe(
                (clip.x / clip.w * .5f + .5f) * client.getFramebuffer().textureWidth,
                (.5f - clip.y / clip.w * .5f) * client.getFramebuffer().textureHeight,
                com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart(),
                com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd(), System.nanoTime(), renderFrame));
    }

    @Override public void onInitializeClient() {
        if("1".equals(System.getenv("DRMD_MIRROR_JOIN_CHECK"))) { MirrorJoinCheck.register();return; }
        if("1".equals(System.getenv("DRMD_HORIZON_CHECK"))) { HorizonAnchorCheck.register();return; }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.START.register(context -> {
            if (com.terminaldetector.drmd.client.portal.OffscreenWorldView.depth() == 0) renderFrame++;
        });
        WorldRenderEvents.END.register(context -> {
            if (pendingCapture == null || com.terminaldetector.drmd.client.portal.OffscreenWorldView.depth() != 0) return;
            var request = pendingCapture;
            captureFrame(MinecraftClient.getInstance(), request.name(), request.colours(), request.mounts());
            pendingCapture = null;
            request.after().run();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server != fixtureServer || fixturePlayer == null) return;
            var player = server.getPlayerManager().getPlayer(fixturePlayer);
            if (player == null) return;
            if (scene == 9 && !ids.isEmpty()) {
                var shot = server.getOverworld().getEntityById(ids.getFirst());
                if (shot != null) {
                    var hit = server.getOverworld().raycast(new net.minecraft.world.RaycastContext(
                            shot.getPos(), shot.getPos().add(shot.getVelocity()),
                            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                            net.minecraft.world.RaycastContext.FluidHandling.NONE, shot));
                    fastServerState = "server age=" + shot.age + " pos=" + shot.getPos()
                            + " nextHit=" + hit.getType() + " at=" + hit.getPos();
                }
            }
            player.setPosition(POSITION); player.setVelocity(Vec3d.ZERO);
            var data = DescentPlayerData.get(player); data.setFlightVelocity(Vec3d.ZERO);
            Vec3d f = forward(), u = up();
            data.setShipAttitude((float) f.x, (float) f.y, (float) f.z, (float) u.x, (float) u.y, (float) u.z);
            if (ready && scene < 8 && ids.size() == 3) rangeServerTicked = ids.stream().allMatch(id -> {
                var entity = server.getOverworld().getEntityById(id);
                return entity instanceof ProjectileEntity && entity.age > 0;
            });
        });
    }

    private Vec3d forward() { return scene == 7 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, -1); }
    private Vec3d up() { return scene == 7 ? new Vec3d(0, 0, 1) : scene == 6 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0); }

    private void tick(MinecraftClient client) {
        if(hybridStarted) { if(HybridWeaponCheck.active()) HybridWeaponCheck.tick(client); else if(AuditRenderCheck.active()) AuditRenderCheck.tick(client); return; }
        ticks++;
        if (ticks % 200 == 0) System.out.println("DRMD shootcheck: scene=" + scene + " tick=" + sceneTick +
                " ready=" + ready + " screen=" + client.currentScreen);
        if (ticks > 4200) throw new IllegalStateException("Weapon check timed out: scene=" + scene + " tick=" + sceneTick);
        if (client.currentScreen instanceof BackupPromptScreen prompt) {
            if (!client.runDirectory.toPath().getFileName().toString().equals("shootcheck"))
                throw new IllegalStateException("Weapon check must use its disposable directory");
            for (var child : prompt.children()) if (child instanceof ButtonWidget button &&
                    button.getMessage().getString().equals("I know what I'm doing!")) { button.onPress(); return; }
            throw new IllegalStateException("Unexpected experimental-world prompt");
        }
        if (client.world == null || client.player == null || client.getServer() == null) return;
        DescentClient.markUserFlightChoice();
        DescentConfig.cockpit = false; DescentConfig.hud = false; DescentConfig.weaponView = true;
        DescentConfig.cameraShake = false;
        DescentConfig.planetFloor = false; DescentConfig.levelSky = false; DescentConfig.skyUfoVirtualHull = false;
        client.options.hudHidden = false; client.options.pauseOnLostFocus = false;
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.options.getParticles().setValue(ParticlesMode.MINIMAL);
        client.options.getSimulationDistance().setValue(8);
        // Keep the viewport deterministic without hiding the weapon renderer (which requires HUD).
        // Join chat and tutorial toasts otherwise cover the very pixels under colour verification.
        client.inGameHud.getChatHud().clear(false);
        client.getToastManager().clear();
        if (!queued) {
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            queued = true; begin(client, 0); return;
        }
        if (!ready || client.currentScreen != null) return;
        client.player.setPosition(POSITION); client.player.setVelocity(Vec3d.ZERO);
        DescentClientState.enabled = true; DescentClientState.attitudeValid = true;
        ShipAttitudeClient.acceptPortalFrame(client.player, forward(), up());
        if (pendingCapture != null) return;
        if (scene < 8 && sceneTick == 0 && (!rangeServerTicked || ids.stream().anyMatch(id ->
                !(client.world.getEntityById(id) instanceof ProjectileEntity)))) return;
        ++sceneTick;
        if (scene == 0) {
            if (sceneTick == 35) client.options.getFov().setValue(70);
            if (sceneTick == 40) capture(client, "mounts-fov-70", false, true);
            if (sceneTick == 45) client.options.getFov().setValue(80);
            if (sceneTick == 50) capture(client, "mounts-fov-80", false, true);
            if (sceneTick == 55) client.options.getFov().setValue(90);
            if (sceneTick == 60) capture(client, "mounts-fov-90", false, true);
            if (sceneTick == 65) client.options.getFov().setValue(80);
        }
        if (scene == 8 && sceneTick == 40) {
            muzzleBefore = ShotFxRenderer.muzzleEvents(); impactBefore = ShotFxRenderer.impactEvents();
            fireVolley(client, true, false);
        }
        if (scene == 8 && sceneTick >= 41 && sceneTick <= 50)
            capture(client, "muzzle-impact-" + sceneTick, false, false);
        if (scene == 9 && sceneTick == 40) fireVolley(client, false, true);
        if (scene == 9 && sceneTick >= 43 && sceneTick <= 49) {
            capture(client, "fast-motion-" + sceneTick, false, false);
        }
        if (scene < 8 && sceneTick == 75) {
            var checkedIds = List.copyOf(ids);
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                for (int id : checkedIds) {
                    var shot = world.getEntityById(id);
                    System.out.println("DRMD shootcheck: server id=" + id + " entity=" + shot +
                            (shot == null ? "" : " pos=" + shot.getPos() + " alive=" + shot.isAlive() + " age=" + shot.age));
                }
            });
        }
        if (sceneTick == 80) {
            if (scene < 8) {
                if (ids.size() != 3) throw new IllegalStateException("Server did not create all range shots");
                for (int id : ids) {
                    var shot = clientShot(client, id);
                    if (!shot.shouldRender(10000)) throw new IllegalStateException("Far projectile culled");
                }
            }
            if (scene == 8 && (ShotFxRenderer.muzzleEvents() <= muzzleBefore || ShotFxRenderer.impactEvents() <= impactBefore))
                throw new IllegalStateException("Short-lived shot lost muzzle or impact event");
            if (scene == 9 && fastTravel < 24)
                throw new IllegalStateException("Fast shot did not travel continuously on client: " + fastTravel);
            pendingCapture = new CaptureRequest(SCENES[scene], scene < 6, false,
                    () -> { if (scene == SCENES.length - 1) finish(client); else begin(client, scene + 1); });
        }
    }

    private void finish(MinecraftClient client) {
        try {
            Files.writeString(client.runDirectory.toPath().resolve("shootcheck-ok.json"),
                    "{\"scenes\":10,\"fovs\":[70,80,90],\"rangePixelsChecked\":true,\"fastSpeed\":8," +
                    "\"fastTravel\":" + fastTravel + ",\"fastFrames\":" + fastFrames + ",\"fastPixelsChecked\":true" +
                    ",\"muzzleEvents\":" + ShotFxRenderer.muzzleEvents() +
                    ",\"impactEvents\":" + ShotFxRenderer.impactEvents() + "}\n");
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        fixtureServer=null; hybridStarted=true; HybridWeaponCheck.start(client);
    }

    private static ProjectileEntity clientShot(MinecraftClient client, int id) {
        var entity = client.world.getEntityById(id);
        if (!(entity instanceof ProjectileEntity shot))
            throw new IllegalStateException("Projectile did not synchronize to client: " + id + "; " + fastServerState);
        return shot;
    }

    private void begin(MinecraftClient client, int next) {
        ready = false; rangeServerTicked = false; scene = next; sceneTick = 0;
        client.options.getFov().setValue(80);
        var server = client.getServer(); var uuid = client.player.getUuid();
        fixtureServer = server; fixturePlayer = uuid;
        server.execute(() -> {
            var world = server.getOverworld(); var player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) throw new IllegalStateException("Missing weapon-check player");
            server.getPlayerManager().setSimulationDistance(8);
            for (var entity : world.iterateEntities()) if (entity instanceof ProjectileEntity) entity.discard();
            player.stopRiding(); player.changeGameMode(GameMode.CREATIVE);
            for (var entity : world.iterateEntities())
                if (entity instanceof com.terminaldetector.drmd.entity.PyroShipEntity) entity.discard();
            if (scene == 0) {
                // Loaded does not imply ENTITY_TICKING. Keep every measured corridor chunk active
                // even while the integrated server is still updating player-distance tickets.
                for (int cx = 0; cx <= 1; cx++) for (int cz = -5; cz <= 1; cz++)
                    world.setChunkForced(cx, cz, true);
                // The copied GameTest save can contain authored/generated geometry. Clear only
                // the disposable shot corridor and vertical shaft, before creating any targets.
                for (var pos : BlockPos.iterate(new BlockPos(6, 202, -84), new BlockPos(21, 212, 30)))
                    if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                for (var pos : BlockPos.iterate(new BlockPos(8, 203, 26), new BlockPos(16, 310, 34)))
                    if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            }
            player.teleport(world, POSITION.x, POSITION.y, POSITION.z, 180, 0);
            var data = DescentPlayerData.get(player); data.setEnabled(true); data.setFlightVelocity(Vec3d.ZERO);
            Vec3d f = forward(), u = up();
            data.setShipAttitude((float) f.x, (float) f.y, (float) f.z, (float) u.x, (float) u.y, (float) u.z);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.LASER));
            // Only authored test blocks at an empty altitude; no user saves are loaded.
            boolean light = scene < 6 && (scene & 1) == 1;
            world.setTimeOfDay(light ? 6000 : 18000);
            // One backdrop; daylight/night changes contrast without thousands of block updates
            // competing with spawn packets at each transition.
            if (scene == 0) for (var pos : BlockPos.iterate(new BlockPos(-32, 177, -85), new BlockPos(56, 235, -85)))
                world.setBlockState(pos, Blocks.WHITE_CONCRETE.getDefaultState(), 2);
            if (scene == 8) {
                for (var pos : BlockPos.iterate(new BlockPos(8, 203, 24), new BlockPos(16, 209, 24)))
                    world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState(), 2);
            }
            if (scene == 9) {
                for (int z : new int[]{24, -85})
                    for (var pos : BlockPos.iterate(new BlockPos(8, 203, z), new BlockPos(16, 209, z)))
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            }
            List<Integer> created = new ArrayList<>();
            if (scene < 8) {
                Vec3d right = f.crossProduct(u).normalize();
                for (int index = 0; index < 3; index++) {
                    double distance = new double[]{12, 40, 90}[index];
                    Vec3d pos = scene < 6
                            ? player.getEyePos().add(f.multiply(distance)).add(right.multiply(new double[]{-3, 0, 5}[index]))
                            : WeaponCore.allMuzzles(player, "laser").get(index).add(f.multiply(40));
                    // Range images must remain at their authored distances even when
                    // software rendering takes longer than server simulation. Motion
                    // and full-speed synchronization are verified separately in scene 9.
                    var cfg = config(player, pos, f, 0,
                            scene < 2 ? 0 : scene < 4 ? 2 : scene < 6 ? 1 : 0);
                    cfg.life = 120;
                    var shot = WeaponCore.fireProjectile(cfg);
                    if (shot == null) throw new IllegalStateException("Range projectile spawn rejected");
                    created.add(shot.getId());
                }
            }
            ids = List.copyOf(created); ready = true;
        });
    }

    private void fireVolley(MinecraftClient client, boolean impact, boolean fast) {
        ready = false; var server = client.getServer(); var uuid = client.player.getUuid();
        server.execute(() -> {
            var player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) throw new IllegalStateException("Missing firing player");
            var positions = fast ? List.of(WeaponCore.muzzleFor(player, "laser", 1)) : WeaponCore.allMuzzles(player, "laser").subList(0, 2);
            List<Integer> created = new ArrayList<>();
            for (Vec3d position : positions) {
                var shot = WeaponCore.fireProjectile(config(player, position, forward(), fast ? 8 : 2, 0));
                if (shot == null) throw new IllegalStateException("Shot spawn rejected");
                created.add(shot.getId());
            }
            ids = List.copyOf(created); ready = true;
        });
    }

    private static WeaponCore.FireConfig config(ServerPlayerEntity player, Vec3d pos, Vec3d direction, float speed, int mesh) {
        var cfg = new WeaponCore.FireConfig();
        cfg.owner = player; cfg.pos = pos; cfg.dir = direction;
        cfg.speedIsSourceUnits = false; cfg.speed = speed; cfg.inherit = 0; cfg.recoil = 0;
        cfg.life = 30; cfg.directDamage = 0; cfg.splashRadius = 0;
        cfg.meshKind = mesh; cfg.visualScale = 1.5f;
        cfg.colorR = 40; cfg.colorG = 255; cfg.colorB = 100;
        return cfg;
    }

    private void capture(MinecraftClient client, String name, boolean colours, boolean mounts) {
        if (pendingCapture != null) throw new IllegalStateException("A frame capture is already pending");
        pendingCapture = new CaptureRequest(name, colours, mounts, () -> {});
    }

    private void captureFrame(MinecraftClient client, String name, boolean colours, boolean mounts) {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var directory = client.runDirectory.toPath().resolve("screenshots"); Files.createDirectories(directory);
            image.writeTo(directory.resolve(name + ".png"));
            boolean fastFrame = scene == 9 && name.startsWith("fast-motion-");
            boolean fastComplete = false;
            if (fastFrame) {
                var shot = clientShot(client, ids.getFirst());
                if (Math.abs(shot.getVisualVelocity().length() - 8) > .001)
                    throw new IllegalStateException("Client speed still clamped: " + shot.getVisualVelocity());
                if (previousFast != null) fastTravel += shot.getPos().distanceTo(previousFast);
                previousFast = shot.getPos(); fastFrames++;
                fastComplete = fastFrames >= 3 && fastTravel >= 24;
            }
            if (colours || fastFrame) {
                // A separate window per known range shot prevents a bright near bolt from hiding a
                // missing far bolt, and a green hue check catches the old red damage-overlay tint.
                double focal = image.getHeight() / (2 * Math.tan(Math.toRadians(40)));
                for (int id : ids) {
                    var shot = clientShot(client, id);
                    var probe = RENDERED.get(id);
                    System.out.println("DRMD shootcheck: id=" + id + " pos=" + shot.getPos() +
                            " rgb=" + Integer.toHexString(shot.getColorRgb()) +
                            " chunkLoaded=" + client.world.isChunkLoaded(shot.getBlockPos()) +
                            " distanceMultiplier=" + ProjectileEntity.getRenderDistanceMultiplier() + " render=" + probe +
                            " visibility=" + VISIBILITY.get(id));
                    if (probe == null || probe.frame() != renderFrame)
                        throw new IllegalStateException("Projectile did not reach the actual renderer: " + id);
                    Vec3d delta = shot.getPos().subtract(client.gameRenderer.getCamera().getPos());
                    double depth = delta.dotProduct(forward());
                    int x = Math.round(probe.x()), cy = Math.round(probe.y());
                    int green = 0;
                    for (int px = Math.max(0, x - 8); px < Math.min(image.getWidth(), x + 9); px++)
                        for (int y = Math.max(0, cy - 9); y < Math.min(image.getHeight(), cy + 10); y++) {
                            int color = image.getColor(px, y);
                            int r = color & 255, g = (color >> 8) & 255, b = (color >> 16) & 255;
                            if (g > 100 && g > r + 25 && g > b + 15) green++;
                        }
                    int minimum = shot.getMeshKind() == ProjectileEntity.MESH_ROCKET ? 1 : 4;
                    if (green < minimum) throw new IllegalStateException(name + ": green shot missing at depth=" + depth + " x=" + x + " greenPixels=" + green);
                }
            }
            if (fastComplete) {
                image.writeTo(directory.resolve("fast-motion.png"));
                finish(client);
            }
            if (mounts) {
                double focal = image.getHeight() / (2 * Math.tan(Math.toRadians(client.options.getFov().getValue() / 2.0)));
                for (var module : com.terminaldetector.drmd.workshop.DefaultLayouts.forWeapon("laser").subList(0, 2)) {
                    int cx = (int) Math.round(image.getWidth() / 2.0 + module.posRgt / module.posFwd * focal);
                    int cy = (int) Math.round(image.getHeight() / 2.0 - module.posUp / module.posFwd * focal);
                    int blue = 0;
                    for (int y = Math.max(0, cy - 35); y < Math.min(image.getHeight(), cy + 36); y++)
                        for (int x = Math.max(0, cx - 35); x < Math.min(image.getWidth(), cx + 36); x++) {
                            int color = image.getColor(x, y);
                            int r = color & 255, g = (color >> 8) & 255, b = (color >> 16) & 255;
                            if (b > 100 && b > r + 25 && b > g + 10) blue++;
                        }
                    if (blue < 20) throw new IllegalStateException(name + ": weapon mount missing at " + cx + "," + cy + ": " + blue + " pixels");
                }
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot save weapon evidence", error); }
    }
}
