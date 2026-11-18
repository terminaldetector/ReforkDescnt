package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.model.PyroShipModel;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.workshop.ClusterModule;
import com.terminaldetector.drmd.workshop.ConstructionRegistry;
import com.terminaldetector.drmd.workshop.ConstructionValidation;
import com.terminaldetector.drmd.workshop.HybridWeaponModels;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only menu inspection. No entity, dispatcher, world, player hand, FBO or physical mutation. */
public final class DroneLoadoutPreview {
    private static final Identifier HULL_TEXTURE = Identifier.of(DescentMod.MOD_ID, "textures/entity/pyro_ship.png");
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE, MAX_FACES = 32_768;
    private static final double YAW = 145, PITCH = 22;
    private record Vertex(DronePreviewProjection.Point point, float u, float v, int colour) {}
    private record Face(List<Vertex> vertices, boolean textured, double depth) {}
    private record Mesh(List<Face> faces, DronePreviewProjection.Bounds bounds) {}

    private final List<Face> bareHull;
    private ItemStack previous = ItemStack.EMPTY;
    private int previousBank = -1;
    private long previousLayout;
    private Mesh mesh;
    private boolean itemInspection;

    public DroneLoadoutPreview() {
        // This is private model data, never the renderer-owned parts or an actual world hull.
        ModelPart hull = PyroShipModel.getTexturedModelData().createModel();
        for (String wing : List.of("wing_l", "wing_r")) {
            ModelPart part = hull.getChild(wing);
            part.getChild(wing.equals("wing_l") ? "laser_l" : "laser_r").visible = false;
            part.getChild(wing.equals("wing_l") ? "missile_l" : "missile_r").visible = false;
        }
        for (String part : List.of("top_pod", "top_pod_tubes", "bottom_rig", "bottom_rig_emitter"))
            hull.getChild(part).visible = false;
        Collector collector = new Collector(true);
        hull.render(new MatrixStack(), collector, LIGHT, OverlayTexture.DEFAULT_UV, 0xffffffff);
        bareHull = collector.finish();
    }

    public void draw(DrawContext context, ItemStack selected, int bank, int x, int y, int width, int height) {
        if (width <= 8 || height <= 8) return;
        // Vanilla ScreenHandler owns these stacks; no code here sets/removes/uses an inventory item.
        List<ClusterModule> modules = selected.getItem() instanceof DescentWeaponItem weapon && bank >= 0 && bank < 3
                ? ConstructionRegistry.getModules(weapon.getDef().id) : List.of();
        if (!ConstructionValidation.valid(modules)) modules = List.of();
        long stamp = fingerprint(modules);
        if (mesh == null || previousBank != bank || previousLayout != stamp
                || !ItemStack.areItemsAndComponentsEqual(previous, selected)) {
            previous = selected.copy(); previousBank = bank; previousLayout = stamp;
            rebuild(selected, bank, modules);
        }
        var fit = DronePreviewProjection.fit(mesh.bounds, width, height, 4);
        context.draw();
        context.enableScissor(x, y, x + width, y + height);
        context.getMatrices().push();
        try {
            Boolean lastTextured = null;
            VertexConsumer buffer = null;
            var matrix = context.getMatrices().peek().getPositionMatrix();
            for (Face face : mesh.faces) {
                // Normal GUI buffers only. Flush switches to preserve the sorted face order.
                if (lastTextured == null || lastTextured != face.textured) {
                    context.draw();
                    buffer = context.getVertexConsumers().getBuffer(face.textured
                            ? RenderLayer.getText(HULL_TEXTURE) : RenderLayer.getGui());
                    lastTextured = face.textured;
                }
                for (Vertex vertex : face.vertices) {
                    var point = vertex.point;
                    var out = buffer.vertex(matrix, (float) (x + fit.x(point)), (float) (y + fit.y(point)), 100)
                            .color(vertex.colour);
                    if (face.textured) out.texture(vertex.u, vertex.v).light(LIGHT);
                }
            }
            context.draw();
            if (itemInspection) {
                // Real component-aware item model, not a substitute mesh or invented physical mount.
                context.getMatrices().push();
                try {
                    context.getMatrices().translate(x + width - 36, y + height - 36, 150);
                    context.getMatrices().scale(2, 2, 2);
                    context.drawItem(selected, 0, 0);
                    context.draw();
                } finally { context.getMatrices().pop(); }
            }
        } finally {
            try { context.draw(); }
            finally { context.getMatrices().pop(); context.disableScissor(); }
        }
    }

    private void rebuild(ItemStack selected, int bank, List<ClusterModule> modules) {
        List<Face> faces = new ArrayList<>(bareHull);
        boolean supported = !selected.isEmpty() && bank >= 0 && bank < 3 && !modules.isEmpty()
                && modules.stream().allMatch(module -> HybridWeaponModels.contains(module.model));
        itemInspection = !selected.isEmpty() && !supported;
        if (supported) {
            Collector collector = new Collector(false);
            MatrixStack matrices = new MatrixStack();
            try {
                for (ClusterModule module : modules) {
                    if (module.colorA <= 0) continue;
                    var anchor = DronePreviewProjection.moduleAnchor(module.posFwd, module.posRgt, module.posUp);
                    // Actual saved layout and shared module transform; no per-bank offset.
                    matrices.push(); matrices.translate(anchor.x(), anchor.y(), anchor.z());
                    HybridWeaponRenderer.draw(matrices, layer -> collector, module, 0, 0, LIGHT);
                    matrices.pop();
                    // Existing PyroShipRenderer brace/socket endpoints in the same physical frame.
                    float socketZ = module.model.equals(HybridWeaponModels.RAIL) ? 22
                            : module.model.equals(HybridWeaponModels.PLASMA) ? 19 : 20;
                    var socket = HybridWeaponModels.transform(module,
                            new HybridWeaponModels.Point(0, -4, socketZ), 0, false).div(16);
                    Vec3d to = new Vec3d(anchor.x() + socket.x, anchor.y() + socket.y, anchor.z() + socket.z);
                    Vec3d from = new Vec3d(Math.copySign(.4, module.posRgt), -.5, 0);
                    HybridWeaponRenderer.brace(matrices, layer -> collector, from, to, LIGHT);
                }
                faces.addAll(collector.finish());
            } catch (PreviewLimitException boundedFallback) {
                // Never pretend a partial layout is complete; inspect the actual item instead.
                itemInspection = true;
            }
        }
        List<Face> projected = new ArrayList<>(faces.size());
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (Face face : faces) {
            List<Vertex> vertices = new ArrayList<>(4); double depth = 0;
            for (Vertex vertex : face.vertices) {
                var p = DronePreviewProjection.orbit(vertex.point, YAW, PITCH);
                minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
                maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); depth += p.z();
                vertices.add(new Vertex(p, vertex.u, vertex.v, vertex.colour));
            }
            projected.add(new Face(List.copyOf(vertices), face.textured, depth / 4));
        }
        projected.sort(Comparator.comparingDouble(Face::depth).reversed());
        mesh = new Mesh(List.copyOf(projected), new DronePreviewProjection.Bounds(minX, minY, maxX, maxY));
    }

    /** Rebuild when geometry/tint changes even if the selected item's ID/components stay the same. */
    private static long fingerprint(List<ClusterModule> modules) {
        long stamp = 1;
        for (ClusterModule m : modules) {
            stamp = stamp * 31 + m.model.hashCode(); stamp = stamp * 31 + m.type.hashCode();
            for (float value : new float[]{m.posFwd, m.posRgt, m.posUp, m.pitch, m.yaw, m.roll, m.scaleX, m.scaleY, m.scaleZ})
                stamp = stamp * 31 + Float.floatToIntBits(value);
            for (int value : new int[]{m.colorR, m.colorG, m.colorB, m.colorA}) stamp = stamp * 31 + value;
        }
        return stamp;
    }

    private static final class PreviewLimitException extends RuntimeException {}

    /** Capture real private hull/shared weapon vertices without invoking a world/entity renderer. */
    private static final class Collector implements VertexConsumer {
        private final boolean textured;
        private final List<Face> faces = new ArrayList<>();
        private final List<Vertex> quad = new ArrayList<>(4);
        private boolean pending;
        private DronePreviewProjection.Point point;
        private float u, v;
        private int colour = 0xffffffff;
        Collector(boolean textured) { this.textured = textured; }

        @Override public VertexConsumer vertex(float x, float y, float z) {
            flushVertex();
            point = textured ? DronePreviewProjection.hullPoint(x, y, z)
                    : new DronePreviewProjection.Point(x, y, z);
            pending = true; return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            colour = a << 24 | r << 16 | g << 8 | b; return this;
        }
        @Override public VertexConsumer texture(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }

        private void flushVertex() {
            if (!pending) return;
            pending = false; quad.add(new Vertex(point, u, v, colour));
            if (quad.size() == 4) {
                List<Vertex> vertices = List.copyOf(quad); quad.clear();
                // World mesh emits both windings for culling; GUI has none. Avoid double alpha.
                if (!faces.isEmpty() && reversed(faces.get(faces.size() - 1).vertices, vertices)) return;
                if (faces.size() >= MAX_FACES) throw new PreviewLimitException();
                faces.add(new Face(vertices, textured, 0));
            }
        }
        private static boolean reversed(List<Vertex> a, List<Vertex> b) {
            for (int i = 0; i < 4; i++) if (!a.get(i).equals(b.get(3 - i))) return false;
            return true;
        }
        List<Face> finish() {
            flushVertex();
            if (!quad.isEmpty()) throw new IllegalStateException("Incomplete actual preview quad");
            return List.copyOf(faces);
        }
    }
}
