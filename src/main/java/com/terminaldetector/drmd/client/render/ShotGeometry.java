package com.terminaldetector.drmd.client.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** World-space glow primitives, with all forward edges at or behind the supplied tip. */
final class ShotGeometry {
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static final Vec3d EAST = new Vec3d(1, 0, 0);

    private ShotGeometry() {}

    static Vec3d transverse(Vec3d forward, Vec3d toEye) {
        Vec3d side = forward.crossProduct(toEye);
        if (side.lengthSquared() < 1e-10)
            side = forward.crossProduct(Math.abs(forward.y) < .9 ? UP : EAST);
        return side.normalize();
    }

    static void ribbon(VertexConsumer buffer, MatrixStack.Entry matrix, Vec3d forward, Vec3d side,
                       float front, float length, float rearWidth, float frontWidth, int rgb, float alpha) {
        int argb = (Math.round(alpha * 255) << 24) | (rgb & 0xFFFFFF);
        // U=.5 at the tip samples the middle of the glow kernel and U=0 fades the wake.
        vertex(buffer, matrix, forward, front - length, side, -rearWidth, argb, 0, 0);
        vertex(buffer, matrix, forward, front, side, -frontWidth, argb, .5f, 0);
        vertex(buffer, matrix, forward, front, side, frontWidth, argb, .5f, 1);
        vertex(buffer, matrix, forward, front - length, side, rearWidth, argb, 0, 1);
    }

    static void disc(VertexConsumer buffer, MatrixStack.Entry matrix, Vec3d right, Vec3d up,
                     float radius, int rgb, float alpha) {
        int argb = (Math.round(alpha * 255) << 24) | (rgb & 0xFFFFFF);
        vertex(buffer, matrix, right, -radius, up, -radius, argb, 0, 0);
        vertex(buffer, matrix, right, radius, up, -radius, argb, 1, 0);
        vertex(buffer, matrix, right, radius, up, radius, argb, 1, 1);
        vertex(buffer, matrix, right, -radius, up, radius, argb, 0, 1);
    }

    static int brightCore(int rgb) {
        int r = 255 - (255 - ((rgb >> 16) & 255)) / 4;
        int g = 255 - (255 - ((rgb >> 8) & 255)) / 4;
        int b = 255 - (255 - (rgb & 255)) / 4;
        return (r << 16) | (g << 8) | b;
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry matrix,
                               Vec3d axisA, float a, Vec3d axisB, float b, int argb, float u, float v) {
        float x = (float) (axisA.x * a + axisB.x * b);
        float y = (float) (axisA.y * a + axisB.y * b);
        float z = (float) (axisA.z * a + axisB.z * b);
        buffer.vertex(matrix, x, y, z).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(matrix, 0, 0, 1);
    }
}
