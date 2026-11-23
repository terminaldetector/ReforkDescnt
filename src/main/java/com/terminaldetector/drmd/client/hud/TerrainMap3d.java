package com.terminaldetector.drmd.client.hud;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Isometric scanner of nearby geometry, with independent XYZ samples and selectable depth. */
public final class TerrainMap3d {
    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 154;
    private static final int MAX_BODY_MARKERS = 32;
    private static final int NATURAL = 0xFF667F83;
    private static final int ENGINEERED = 0xFFC08951;
    private static final int LIQUID = 0xFF497C9E;
    private static final int MOVING = 0xFFAFF5C4;
    private static final int LABEL = 0xFFD1DAE1;
    private static final List<TerrainMapVolume.Cell> sortedSurface = new ArrayList<>();
    private static KeyBinding higher;
    private static KeyBinding lower;
    private static boolean open;
    private static int altitudeOffset;
    private static float yaw;
    private static World world;
    private static TerrainMapVolume scan;
    private static TerrainMapVolume.Snapshot snapshot;

    private TerrainMap3d() {}

    public static void register() {
        higher = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.map_up", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP, "key.category.drmd"));
        lower = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.map_down", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN, "key.category.drmd"));
    }

    public static boolean isOpen() { return open; }

    public static void toggle() {
        if (open) close();
        else open = true;
    }

    public static void close() {
        open = false;
        altitudeOffset = 0;
        world = null;
        clearScan();
    }

    private static void clearScan() {
        scan = null;
        snapshot = null;
        sortedSurface.clear();
    }

    public static boolean canUse(ClientPlayerEntity player) {
        return player != null && (player.getVehicle() instanceof PyroShipEntity || DescentClientState.enabled);
    }

    public static void tick(MinecraftClient mc) {
        int depth = altitudeOffset;
        while (higher.wasPressed()) depth += 16;
        while (lower.wasPressed()) depth -= 16;
        if (!open) return;
        if (!canUse(mc.player) || mc.world == null) {
            close();
            return;
        }

        depth = MathHelper.clamp(depth, -512, 512);
        if (world != mc.world || depth != altitudeOffset) {
            world = mc.world;
            altitudeOffset = depth;
            clearScan();
        }

        yaw = DescentClientState.attitudeValid ? ShipAttitudeClient.get().yawDegrees() : mc.player.getYaw();
        if (scan == null) scan = new TerrainMapVolume(world, mc.player.getBlockPos(), altitudeOffset);
        if (scan.advance(world)) {
            snapshot = scan.finish();
            sortedSurface.clear();
            sortedSurface.addAll(snapshot.surface());
            scan = null;
        }

        float angle = yaw * MathHelper.RADIANS_PER_DEGREE;
        float cosine = MathHelper.cos(angle);
        float sine = MathHelper.sin(angle);
        sortedSurface.sort(Comparator.comparingDouble(cell ->
                (cosine + sine) * cell.x() + (cosine - sine) * cell.z() + 0.45 * cell.y()));
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        if (!open || mc.player == null || mc.world != world) return;
        int left = mc.getWindow().getScaledWidth() - PANEL_WIDTH - 8;
        int top = Math.max(18, (mc.getWindow().getScaledHeight() - PANEL_HEIGHT) / 2);
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        ctx.fill(left, top, right, bottom, 0xEB11181E);
        ctx.fill(left, top, right, top + 1, 0xFF7A9C9E);
        ctx.drawText(mc.textRenderer, Text.translatable("hud.drmd.volume_map"), left + 6, top + 5, LABEL, false);
        String depthKeys = higher.getBoundKeyLocalizedText().getString() + "/" + lower.getBoundKeyLocalizedText().getString();
        ctx.drawText(mc.textRenderer, depthKeys, right - 6 - mc.textRenderer.getWidth(depthKeys), top + 5, LABEL, false);
        if (snapshot == null) {
            ctx.drawText(mc.textRenderer, Text.translatable("hud.drmd.volume_map.scanning"),
                    left + 6, top + 19, LABEL, false);
            return;
        }

        var region = snapshot.region();
        var scope = region.getCenter();
        float angle = yaw * MathHelper.RADIANS_PER_DEGREE;
        var view = new Projection(mc.player.getX(), scope.y, mc.player.getZ(),
                left + PANEL_WIDTH / 2f, top + 74, MathHelper.cos(angle), MathHelper.sin(angle));

        ctx.enableScissor(left + 2, top + 16, right - 2, bottom - 26);
        VertexConsumer buffer = ctx.getVertexConsumers().getBuffer(RenderLayer.getGui());
        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        for (var cell : sortedSurface) drawCell(buffer, matrix, view, cell);
        ctx.draw();
        int markers = 0;
        for (var body : BodySpatialIndex.query(world, region)) {
            Vec3d point = body.getPos();
            if (!region.contains(point)) continue;
            if (markers++ == MAX_BODY_MARKERS) break;
            marker(ctx, view, point, MOVING);
        }
        if (region.contains(mc.player.getPos())) marker(ctx, view, mc.player.getPos(), 0xFFFFFFFF);
        ctx.disableScissor();

        ctx.drawText(mc.textRenderer, Text.translatable("hud.drmd.volume_map.range", (int) region.minY,
                (int) region.maxY - 1, snapshot.freePercent(), snapshot.unknownPercent()), left + 6, bottom - 23, LABEL, false);
        legend(ctx, mc, left + 6, bottom - 11, NATURAL, "hud.drmd.volume_map.natural");
        legend(ctx, mc, left + 68, bottom - 11, ENGINEERED, "hud.drmd.volume_map.engineered");
        legend(ctx, mc, left + 146, bottom - 11, MOVING, "hud.drmd.volume_map.body");
    }

    private static void legend(DrawContext ctx, MinecraftClient mc, int x, int y, int color, String label) {
        ctx.fill(x, y + 2, x + 5, y + 7, color);
        ctx.drawText(mc.textRenderer, Text.translatable(label), x + 9, y, LABEL, false);
    }

    private static void marker(DrawContext ctx, Projection view, Vec3d point, int color) {
        int x = (int) view.screenX(point.x, point.z);
        int y = (int) view.screenY(point.x, point.y, point.z);
        ctx.fill(x - 2, y - 2, x + 3, y + 3, color);
    }

    private static void drawCell(VertexConsumer vertices, Matrix4f matrix, Projection view,
                                 TerrainMapVolume.Cell cell) {
        int x = cell.x();
        int y = cell.y();
        int z = cell.z();
        int size = TerrainMapVolume.CELL;
        float ax = view.screenX(x, z);
        float bx = view.screenX(x + size, z);
        float cx = view.screenX(x + size, z + size);
        float dx = view.screenX(x, z + size);
        float ay = view.screenY(x, y + size, z);
        float by = view.screenY(x + size, y + size, z);
        float cy = view.screenY(x + size, y + size, z + size);
        float dy = view.screenY(x, y + size, z + size);
        float height = size * Projection.VERTICAL_SCALE;
        int color = switch (cell.material()) {
            case ENGINEERED -> ENGINEERED;
            case LIQUID -> LIQUID;
            default -> NATURAL;
        };

        if (view.cosine + view.sine >= 0) {
            side(vertices, matrix, bx, by, cx, cy, height, shade(color, 4));
        } else {
            side(vertices, matrix, dx, dy, ax, ay, height, shade(color, 4));
        }
        if (view.cosine - view.sine >= 0) {
            side(vertices, matrix, cx, cy, dx, dy, height, shade(color, 3));
        } else {
            side(vertices, matrix, ax, ay, bx, by, height, shade(color, 3));
        }
        vertex(vertices, matrix, ax, ay, color);
        vertex(vertices, matrix, dx, dy, color);
        vertex(vertices, matrix, cx, cy, color);
        vertex(vertices, matrix, bx, by, color);
    }

    private static void side(VertexConsumer vertices, Matrix4f matrix, float ax, float ay,
                             float bx, float by, float height, int color) {
        vertex(vertices, matrix, ax, ay, color);
        vertex(vertices, matrix, bx, by, color);
        vertex(vertices, matrix, bx, by + height, color);
        vertex(vertices, matrix, ax, ay + height, color);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, int color) {
        vertices.vertex(matrix, x, y, 0).color(color);
    }

    private static int shade(int color, int fifths) {
        int red = ((color >>> 16) & 255) * fifths / 5;
        int green = ((color >>> 8) & 255) * fifths / 5;
        int blue = (color & 255) * fifths / 5;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private record Projection(double x, double y, double z, float ox, float oy, float cosine, float sine) {
        private static final float VERTICAL_SCALE = 0.91f;

        float screenX(double wx, double wz) {
            return ox + (float) ((wx - x) * (cosine - sine) - (wz - z) * (cosine + sine)) * 1.08f;
        }

        float screenY(double wx, double wy, double wz) {
            return oy + (float) ((wx - x) * (cosine + sine) + (wz - z) * (cosine - sine)) * 0.37f
                    - (float) (wy - y) * VERTICAL_SCALE;
        }
    }
}
