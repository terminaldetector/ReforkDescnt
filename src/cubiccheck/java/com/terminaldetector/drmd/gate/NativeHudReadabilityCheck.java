package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import com.terminaldetector.drmd.client.screen.DroneInventoryScreen;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.inventory.DroneInventoryHandler;
import com.terminaldetector.drmd.inventory.DroneLoadout;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import com.terminaldetector.drmd.gate.mixin.HudMouseInputProbe;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Actual handled screen, HUD draw calls and final framebuffer at requested GUI scales. */
public final class NativeHudReadabilityCheck {
    public static final boolean ENABLED = Boolean.getBoolean("drmd.gate.hudReadability");
    private static final int HUD_FRAMES = 185;
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static final List<String> currentHints = new ArrayList<>();
    private static final List<String> BANK_LABELS = List.of("primary", "secondary", "auxiliary", "reserve", "engine");
    private static final java.util.HashMap<String, TextBounds> labelBounds = new java.util.HashMap<>();
    private record TextBounds(float left, float right) {}
    private static UUID playerId;
    private static int phase, frame, age, hudFrames, notices, debugPrefix, tooltipFrames;
    private static int x, y, desiredX = -1, desiredY = -1, bank;
    private static String tooltip;
    private static volatile boolean fixtureReady, menuRequested;
    private static boolean configured, done;
    private static long startedAt, hoverStartedAt;

    private NativeHudReadabilityCheck() {}

    public static void register() {
        startedAt = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(NativeHudReadabilityCheck::tick);
    }

    private static int scale() { return phase == 0 ? 1 : (phase - 1) % 3 + 1; }
    private static PresentationProfile profile() {
        return phase < 4 ? PresentationProfile.HYBRID_NATIVE : PresentationProfile.PS1_HYBRID;
    }

    private static void tick(MinecraftClient client) {
        if (done) return;
        if (++age > 1600 || System.nanoTime() - startedAt > 140_000_000_000L) {
            finish(client, "FAIL", "GUI/HUD deadline in phase " + phase);
            return;
        }
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt) {
            for (var child : prompt.children()) {
                if (child instanceof net.minecraft.client.gui.widget.ButtonWidget button && button.getMessage().getString().equals("I know what I'm doing!")) button.onPress();
            }
        }
        if (client.player == null || client.world == null || client.getServer() == null) return;
        DescentClient.markUserFlightChoice();
        if (playerId == null) {
            if (!client.runDirectory.getName().equals("cubic-client-automation")) {
                finish(client, "FAIL", "Disposable client required");
                return;
            }
            playerId = client.player.getUuid();
            client.setScreen(null);
            client.options.hudHidden = false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            client.inGameHud.getChatHud().clear(false);
            DescentConfig.hud = true;
            client.getServer().execute(() -> {
                var player = client.getServer().getPlayerManager().getPlayer(playerId);
                player.changeGameMode(GameMode.CREATIVE);
                player.teleport(player.getServerWorld(), 8.5, 2156, 8.5, Set.of(), 0, 0);
                var data = DescentPlayerData.get(player);
                FlightSystem.enableWithoutMount(player, data);
                data.setShield(18);
                data.setShieldRegenDelay(1000);
                DroneLoadout.of(player).clear();
                ModNetworking.syncPlayer(player, data);
                fixtureReady = true;
            });
            return;
        }
        if (!fixtureReady || !DescentClientState.enabled) return;
        if (!configured) {
            client.options.getGuiScale().setValue(scale());
            client.onResolutionChanged();
            DescentConfig.presentationProfile = profile();
            configured = true;
        }
        if (phase == 0) return;
        if (!menuRequested) {
            menuRequested = true;
            client.getServer().execute(() -> DroneInventoryHandler.open(client.getServer().getPlayerManager().getPlayer(playerId)));
        }
        var menu = client.currentScreen;
        var handler = client.player.currentScreenHandler;
        if (menu instanceof DroneInventoryScreen && handler instanceof DroneInventoryHandler) {
            var slot = handler.getSlot(bank);
            desiredX = (menu.width - 176) / 2 + slot.x + 8;
            desiredY = (menu.height - 212) / 2 + slot.y + 8;
            if (hoverStartedAt == 0) hoverStartedAt = System.nanoTime();
            if (System.nanoTime() - hoverStartedAt > 12_000_000_000L) {
                finish(client, "FAIL", "Tooltip did not render for the selected GUI slot in phase " + phase + " bank " + bank);
                return;
            }
            ((HudMouseInputProbe) client.mouse).drmd$probeCursorPos(client.getWindow().getHandle(),
                (double) desiredX * client.getWindow().getWidth() / menu.width,
                (double) desiredY * client.getWindow().getHeight() / menu.height);
        }
    }

    public static void beforeFrame() {
        if (!ENABLED) return;
        notices = debugPrefix = 0;
        tooltip = null;
        labelBounds.clear();
    }

    public static void observeText(Text text) {
        if (!ENABLED || !configured || phase != 0 || done) return;
        String visible = text.getString();
        if (visible.equals(Text.translatable("hud.drmd.shield_critical").getString())) notices++;
        if (visible.startsWith("[SYS]")) debugPrefix++;
    }

    public static void observeLabel(net.minecraft.client.gui.DrawContext context,
            net.minecraft.client.font.TextRenderer renderer, Text text, int x, int y) {
        if (!ENABLED || !configured || phase == 0 || done
                || !(MinecraftClient.getInstance().currentScreen instanceof DroneInventoryScreen)) return;
        for (String key : BANK_LABELS) {
            if (!text.equals(Text.translatable("container.drmd.drone_inventory." + key))) continue;
            int width = renderer.getWidth(text);
            var matrix = context.getMatrices().peek().getPositionMatrix();
            float left = new org.joml.Vector4f(x - width / 2, y, 0, 1).mul(matrix).x;
            float right = new org.joml.Vector4f(x - width / 2 + width + 1, y, 0, 1).mul(matrix).x;
            labelBounds.put(key, new TextBounds(left, right));
            return;
        }
    }

    public static void observeTooltip(Text text, int mouseX, int mouseY) {
        if (!ENABLED || !configured || phase == 0 || done || ScenePresentation.stats().inWorldPass()) return;
        if (!(MinecraftClient.getInstance().currentScreen instanceof DroneInventoryScreen)) return;
        tooltip = text.getString();
        x = mouseX;
        y = mouseY;
    }

    public static void rendered() {
        if (!ENABLED || !configured || done) return;
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || ScenePresentation.stats().inWorldPass()) return;
        if (client.getWindow().getScaleFactor() != scale() || ScenePresentation.stats().profile() != profile()) return;
        if (phase == 0) {
            if (client.currentScreen != null || notices == 0) return;
            if (notices != 1 || debugPrefix != 0) {
                finish(client, "FAIL", "Duplicate or debug-styled battle notice");
                return;
            }
            if (++hudFrames < HUD_FRAMES) return;
        } else {
            var menu = client.currentScreen;
            if (!(menu instanceof DroneInventoryScreen) || desiredX < 0 || tooltip == null
                    || Math.abs(x - desiredX) > 2) return;
            int expectedAnchor = (menu.height - 212) / 2 + 79;
            if (y != expectedAnchor) {
                finish(client, "FAIL", "Equipment tooltip overlaps the caption row");
                return;
            }
            var options = client.options;
            Text expected = switch (bank) {
                case 0 -> Text.translatable("container.drmd.drone_inventory.primary.hint", options.attackKey.getBoundKeyLocalizedText());
                case 1 -> Text.translatable("container.drmd.drone_inventory.secondary.hint", options.useKey.getBoundKeyLocalizedText());
                case 2 -> Text.translatable("container.drmd.drone_inventory.auxiliary.hint",
                    com.terminaldetector.drmd.client.input.DescentKeybinds.auxiliary.getBoundKeyLocalizedText());
                case 3 -> Text.translatable("container.drmd.drone_inventory.reserve.hint");
                default -> Text.translatable("container.drmd.drone_inventory.engine.hint",
                    com.terminaldetector.drmd.client.input.DescentKeybinds.alwaysRun.getBoundKeyLocalizedText());
            };
            if (!tooltip.equals(expected.getString()) || x <= 0 || x >= menu.width || y <= 0 || y >= menu.height
                    || client.textRenderer.getWidth(tooltip) + 12 > menu.width) {
                finish(client, "FAIL", "Rendered tooltip failed text or viewport bound in phase " + phase + ": " + tooltip);
                return;
            }
            if (labelBounds.size() != BANK_LABELS.size()) {
                finish(client, "FAIL", "Incomplete rendered inventory labels in phase " + phase);
                return;
            }
            float previousRight = 0;
            for (String label : BANK_LABELS) {
                var bounds = labelBounds.get(label);
                if (bounds.left() <= previousRight || bounds.right() >= menu.width) {
                    finish(client, "FAIL", "Overlapping rendered label " + label + " in phase " + phase);
                    return;
                }
                previousRight = bounds.right();
            }
            tooltipFrames++;
            if (++frame < 3) return;
            currentHints.add(tooltip);
            if (bank < 4) {
                bank++;
                frame = 0;
                hoverStartedAt = 0;
                desiredX = -1;
                return;
            }
        }
        if (GL11.glGetError() != GL11.GL_NO_ERROR) {
            finish(client, "FAIL", "GL error at output frame");
            return;
        }
        String name = phase == 0 ? "battle-hud" : profile().name().toLowerCase(java.util.Locale.ROOT) + "-gui-" + scale();
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var folder = client.runDirectory.toPath().resolve("hud-readability-frames");
            Files.createDirectories(folder);
            image.writeTo(folder.resolve(name + ".png"));
            results.add(Map.of("name", name, "profile", profile().name(), "scale", scale(), "width", image.getWidth(),
                "height", image.getHeight(), "tooltips", List.copyOf(currentHints), "nativeOutput", true,
                "battleNoticeDraws", notices));
        } catch (Exception failure) {
            finish(client, "FAIL", failure.toString());
            return;
        }
        if (++phase == 7) finish(client, "PASS", "Native HUD and three actual equipment scales under each presentation profile");
        else {
            configured = false;
            frame = bank = 0;
            desiredX = -1;
            hoverStartedAt = 0;
            currentHints.clear();
        }
    }

    private static void finish(MinecraftClient client, String status, String reason) {
        if (done) return;
        done = true;
        try {
            var report = Map.of("status", status, "reason", reason, "results", List.copyOf(results), "battleFrames", hudFrames,
                "actualTooltipFrames", tooltipFrames);
            Files.writeString(client.runDirectory.toPath().resolve("hud-readability-outcome.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        client.scheduleStop();
    }
}
