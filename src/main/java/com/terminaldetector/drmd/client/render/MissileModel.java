package com.terminaldetector.drmd.client.render;

import com.google.gson.JsonParser;
import com.terminaldetector.drmd.weapon.projectile.ProjectileVisual;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bakes the authored OBJ once per resource reload. The object origin is its sweep tip. */
final class MissileModel {
    private static final Identifier CATALOGUE = Identifier.of("drmd", "models/projectile/missile.json");
    private static final Identifier OBJ = Identifier.of("drmd", "models/projectile/missile.obj");
    private static final Identifier ATLAS = Identifier.of("drmd", "textures/entity/missile.png");

    private enum Material { SHELL, NOSE, BAND, FIN, ENGINE, EMISSIVE }

    record Vertex(float x, float y, float z, float u, float v) {}
    private record Face(Vertex[] corners, Vector3f normal, Material material) {}

    record Variant(Identifier icon, List<Face> faces, int[] colors, boolean pair) {
        int exhaust() { return colors[Material.EMISSIVE.ordinal()]; }

        void draw(MatrixStack.Entry matrix, VertexConsumer buffer, int light, int overlay, int tintRgb) {
            for (Face face : faces) {
                int color = tintRgb < 0 ? colors[face.material().ordinal()] : 0xFF000000 | tintRgb;
                int lighting = tintRgb >= 0 || face.material() == Material.EMISSIVE
                        ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
                for (Vertex vertex : face.corners()) {
                    buffer.vertex(matrix, vertex.x(), vertex.y(), vertex.z()).color(color)
                            .texture(vertex.u(), vertex.v()).overlay(overlay).light(lighting)
                            .normal(matrix, face.normal().x, face.normal().y, face.normal().z);
                }
            }
        }
    }

    record Art(Identifier texture, RenderLayer layer, float scale, float nozzleZ, Map<ProjectileVisual, Variant> variants) {
        Variant variant(ProjectileVisual visual) {
            return variants.getOrDefault(visual, variants.get(ProjectileVisual.ROCKET));
        }
    }

    private static Art art = new Art(ATLAS, RenderLayer.getEntitySolid(ATLAS), .22f, -4, Map.of());

    private MissileModel() {}

    static Art art() { return art; }

    static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.of("drmd", "missile_models"); }

            @Override public void reload(ResourceManager manager) {
                try {
                    art = bake(manager);
                } catch (IOException | RuntimeException ex) {
                    LoggerFactory.getLogger(MissileModel.class).error("Could not bake Ungravity missile geometry", ex);
                }
            }
        });
    }

    private static Art bake(ResourceManager manager) throws IOException {
        Map<String, List<Face>> meshes = readMeshes(manager);
        try (var reader = manager.getResourceOrThrow(CATALOGUE).getReader()) {
            var definition = JsonParser.parseReader(reader).getAsJsonObject();
            float scale = definition.get("scale").getAsFloat();
            float nozzleZ = definition.get("nozzleZ").getAsFloat();
            if (!(scale > 0 && scale <= 1) || !(nozzleZ >= -8 && nozzleZ < 0))
                throw new IOException("Invalid missile scale or nozzle pivot");
            Identifier texture = Identifier.of(definition.get("texture").getAsString());
            manager.getResourceOrThrow(texture);
            var variants = new EnumMap<ProjectileVisual, Variant>(ProjectileVisual.class);
            var catalog = definition.getAsJsonObject("variants");
            for (ProjectileVisual id : ProjectileVisual.values()) {
                if (id.family() != ProjectileVisual.Family.ROCKET || id == ProjectileVisual.GENERIC_ROCKET) continue;
                var entry = catalog.getAsJsonObject(id.name());
                String mesh = entry.get("mesh").getAsString();
                List<Face> faces = meshes.get(mesh);
                if (faces == null || faces.isEmpty()) throw new IOException("Missing missile mesh " + mesh);
                var materialColors = entry.getAsJsonObject("materials");
                int[] colors = new int[Material.values().length];
                for (Material material : Material.values()) {
                    String key = material.name().toLowerCase(Locale.ROOT);
                    colors[material.ordinal()] = 0xFF000000 | Integer.parseInt(materialColors.get(key).getAsString(), 16);
                }
                boolean pair = entry.has("pair") && entry.get("pair").getAsBoolean();
                Identifier icon = Identifier.of("drmd", "textures/item/missile/" + entry.get("item").getAsString() + ".png");
                var variant = new Variant(icon, List.copyOf(faces), colors, pair);
                manager.getResourceOrThrow(variant.icon());
                variants.put(id, variant);
            }
            return new Art(texture, RenderLayer.getEntitySolid(texture), scale, nozzleZ, Map.copyOf(variants));
        }
    }

    private static Map<String, List<Face>> readMeshes(ResourceManager manager) throws IOException {
        var meshes = new HashMap<String, List<Face>>();
        var positions = new ArrayList<Vector3f>();
        var uvs = new ArrayList<Vector2f>();
        try (var reader = manager.getResourceOrThrow(OBJ).getReader()) {
            List<Face> current = null;
            Material material = Material.SHELL;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.strip().split("\\s+");
                switch (fields[0]) {
                    case "o" -> {
                        current = new ArrayList<>();
                        if (meshes.put(fields[1], current) != null || meshes.size() > 32)
                            throw new IOException("Duplicate or excess missile objects");
                    }
                    case "v" -> positions.add(new Vector3f(
                            Float.parseFloat(fields[1]), Float.parseFloat(fields[2]), Float.parseFloat(fields[3])));
                    case "vt" -> uvs.add(new Vector2f(Float.parseFloat(fields[1]), 1f - Float.parseFloat(fields[2])));
                    case "usemtl" -> material = Material.valueOf(fields[1].toUpperCase(Locale.ROOT));
                    case "f" -> {
                        if (current == null || current.size() >= 256 || fields.length < 4 || fields.length > 5)
                            throw new IOException("Missile faces require one object and three or four vertices");
                        Vertex[] vertices = new Vertex[4];
                        Vector3f[] points = new Vector3f[4];
                        for (int i = 1; i < fields.length; i++) {
                            String[] coordinates = fields[i].split("/");
                            var point = positions.get(Integer.parseInt(coordinates[0]) - 1);
                            var uv = uvs.get(Integer.parseInt(coordinates[1]) - 1);
                            if (!point.isFinite() || !uv.isFinite() || point.z > 0 || point.z < -8
                                    || Math.abs(point.x) > 3 || Math.abs(point.y) > 3
                                    || uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1)
                                throw new IOException("Missile vertex is outside its local bounds");
                            points[i - 1] = point;
                            vertices[i - 1] = new Vertex(point.x, point.y, point.z, uv.x, uv.y);
                        }
                        if (fields.length == 4) vertices[3] = vertices[2];
                        Vector3f normal = new Vector3f(points[1]).sub(points[0])
                                .cross(new Vector3f(points[2]).sub(points[0]));
                        if (normal.lengthSquared() < 1e-8f) throw new IOException("Degenerate missile face");
                        current.add(new Face(vertices, normal.normalize(), material));
                    }
                    default -> { }
                }
                if (positions.size() > 8192 || uvs.size() > 8192) throw new IOException("Missile mesh budget exceeded");
            }
        }
        return meshes;
    }
}
