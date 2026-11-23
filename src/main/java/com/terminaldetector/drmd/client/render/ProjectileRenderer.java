package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.client.flight.GuidedMissileClient;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.projectile.ProjectileVisual;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** The collision sweep position is the visible nose. Glows and model vertices extend behind it. */
public final class ProjectileRenderer extends EntityRenderer<ProjectileEntity> {
    private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");

    public ProjectileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(ProjectileEntity entity, Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(entity, frustum, x, y, z)) return true;
        if (!entity.shouldRender(x, y, z)) return false;
        // A long flight streak can cross the frustum with the small hit box already outside it.
        Vec3d tail = entity.getVisualVelocity().normalize().multiply(-3.25);
        return frustum.isVisible(entity.getBoundingBox().stretch(tail).expand(.5));
    }

    @Override
    public void render(ProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        if (GuidedMissileClient.isCameraProjectile(entity.getId())) return;
        Vec3d velocity = entity.getVisualVelocity();
        double speed = velocity.length();
        Vec3d direction = speed > 1e-6 ? velocity.multiply(1 / speed) : new Vec3d(0, 0, 1);
        Vec3d toEye = dispatcher.camera.getPos().subtract(entity.getLerpedPos(tickDelta));
        Vec3d side = ShotGeometry.transverse(direction, toEye);
        float scale = Math.clamp(entity.getVisualScale(), .25f, 4f);
        float phase = (entity.age + tickDelta) * .61f + entity.getId();
        int rgb = entity.getColorRgb();
        int mesh = entity.getMeshKind();
        var profile = ShotVisualProfile.of(entity.getProjectileVisual());
        switch (mesh) {
            case ProjectileEntity.MESH_ROCKET ->
                    renderMissile(entity, direction, side, speed, scale, phase, profile, matrices, consumers, light);
            case ProjectileEntity.MESH_DRILL, ProjectileEntity.MESH_MINE ->
                    renderOtherModel(mesh, direction, scale, rgb, phase, matrices, consumers, light);
            default ->
                    renderStreak(direction, side, speed, toEye.length(), scale, rgb, phase, profile, matrices, consumers);
        }
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    private static void renderStreak(Vec3d direction, Vec3d side, double speed, double eyeDistance,
                                     float scale, int rgb, float phase, ShotVisualProfile profile,
                                     MatrixStack matrices, VertexConsumerProvider consumers) {
        float radius = FxVisibility.radius(eyeDistance,
                profile.radius() * scale * (1 + profile.pulse() * MathHelper.sin(phase)));
        if (radius <= 0) return;
        float length = profile.streakLength(speed, scale, eyeDistance);
        var matrix = matrices.peek();
        var glow = consumers.getBuffer(ShotRenderLayers.GLOW);
        ShotGeometry.ribbon(glow, matrix, direction, side, 0, length, radius * 1.3f, radius, rgb, .5f);
        ShotGeometry.ribbon(glow, matrix, direction, side, 0, length * .8f, radius * .46f, radius * .53f,
                ShotGeometry.brightCore(rgb), .96f);

        // This disk is perpendicular to the flight vector. At a head-on angle it provides a compact
        // cap at the sweep tip while the tail is naturally foreshortened by projection.
        Vec3d capUp = direction.crossProduct(side);
        ShotGeometry.disc(glow, matrix, side, capUp, radius, rgb, .64f);
        ShotGeometry.disc(glow, matrix, side, capUp, radius * .47f, ShotGeometry.brightCore(rgb), .96f);
    }

    private static void renderMissile(ProjectileEntity entity, Vec3d direction, Vec3d side, double speed,
                                      float scale, float phase, ShotVisualProfile profile, MatrixStack matrices,
                                      VertexConsumerProvider consumers, int light) {
        var art = MissileModel.art();
        var visual = entity.getProjectileVisual();
        var model = art.variant(visual);
        if (model == null) return;
        int tint = visual == ProjectileVisual.GENERIC_ROCKET ? entity.getColorRgb() : -1;
        float size = scale * art.scale();
        matrices.push();
        orientModel(matrices, direction);
        matrices.scale(size, size, size);
        model.draw(matrices.peek(), consumers.getBuffer(art.layer()), light, OverlayTexture.DEFAULT_UV, tint);
        matrices.pop();

        float pulse = 1 + profile.pulse() * MathHelper.sin(phase);
        float width = profile.radius() * scale * pulse;
        float length = profile.length(speed, scale) * pulse;
        float nozzle = art.nozzleZ() * size;
        int exhaust = tint < 0 ? model.exhaust() : tint;
        var glow = consumers.getBuffer(ShotRenderLayers.GLOW);
        var matrix = matrices.peek();
        ShotGeometry.ribbon(glow, matrix, direction, side, nozzle, length, width * .64f, width,
                exhaust, .68f);
        ShotGeometry.ribbon(glow, matrix, direction, side, nozzle, length * .63f, width * .28f, width * .46f,
                ShotGeometry.brightCore(exhaust), .94f);
    }

    private static void orientModel(MatrixStack matrices, Vec3d direction) {
        Vec3d referenceUp = Math.abs(direction.y) < .99 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
        ModelOrientation.applyBasis(matrices, 180f, direction.negate(), referenceUp);
    }

    private static void renderOtherModel(int mesh, Vec3d direction, float scale, int rgb, float phase,
                                         MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        orientModel(matrices, direction);
        var solid = consumers.getBuffer(RenderLayer.getEntitySolid(WHITE));
        var matrix = matrices.peek();
        if (mesh == ProjectileEntity.MESH_DRILL) {
            float width = .12f * scale;
            box(solid, matrix, width, width, -.79f * scale, -.1f * scale, rgb, light);
            box(solid, matrix, width * 1.42f, width * 1.42f, -.18f * scale, 0,
                    0xE09D52, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
        } else {
            float shell = .32f * scale;
            box(solid, matrix, shell, shell, -shell, shell, rgb, light);
            float pulse = .53f + .43f * MathHelper.sin(phase);
            float extent = shell * (1 + pulse * .48f);
            float band = shell * .21f;
            int glow = ShotGeometry.brightCore(rgb);
            int fullLight = net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;
            box(solid, matrix, extent, band, -band, band, glow, fullLight);
            box(solid, matrix, band, extent, -band, band, glow, fullLight);
            box(solid, matrix, band, band, -extent, extent, glow, fullLight);
        }
        matrices.pop();
    }

    private static void box(VertexConsumer buffer, MatrixStack.Entry matrix,
                            float x, float y, float z0, float z1, int rgb, int light) {
        int color = 0xff000000 | rgb;
        quad(buffer, matrix, -x,-y,z0, x,-y,z0, x,-y,z1, -x,-y,z1, color, light, 0,-1,0);
        quad(buffer, matrix, -x,y,z0, -x,y,z1, x,y,z1, x,y,z0, color, light, 0,1,0);
        quad(buffer, matrix, -x,-y,z0, -x,y,z0, x,y,z0, x,-y,z0, color, light, 0,0,-1);
        quad(buffer, matrix, -x,-y,z1, x,-y,z1, x,y,z1, -x,y,z1, color, light, 0,0,1);
        quad(buffer, matrix, -x,-y,z0, -x,-y,z1, -x,y,z1, -x,y,z0, color, light, -1,0,0);
        quad(buffer, matrix, x,-y,z0, x,y,z0, x,y,z1, x,-y,z1, color, light, 1,0,0);
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int color, int light, float nx, float ny, float nz) {
        buffer.vertex(matrix, x0, y0, z0).color(color).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrix, nx, ny, nz);
        buffer.vertex(matrix, x1, y1, z1).color(color).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrix, nx, ny, nz);
        buffer.vertex(matrix, x2, y2, z2).color(color).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrix, nx, ny, nz);
        buffer.vertex(matrix, x3, y3, z3).color(color).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrix, nx, ny, nz);
    }

    @Override
    public Identifier getTexture(ProjectileEntity entity) {
        return entity.getMeshKind() == ProjectileEntity.MESH_ROCKET ? MissileModel.art().texture() : WHITE;
    }
}
